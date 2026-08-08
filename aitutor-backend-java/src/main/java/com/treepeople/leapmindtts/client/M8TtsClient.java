package com.treepeople.leapmindtts.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class M8TtsClient {

    private final ObjectMapper om;
    private final HttpClient http;

    @Value("${m8.base-url:http://localhost:8080}")
    private String baseUrl;

    @Value("${m8.connect-timeout-ms:10000}")
    private int connectTimeoutMs;

    @Value("${m8.read-timeout-seconds:125}")
    private int readTimeoutSeconds;

    @Value("${m8.retry-count:3}")
    private int retryCount;

    @Value("${m8.default-voice:zhixiaoxia}")
    private String defaultVoice;

    @Value("${m8.default-speed:1.0}")
    private double defaultSpeed;

    @Deprecated
    @Value("${m8.internal-service-token:${M8_INTERNAL_SERVICE_TOKEN:}}")
    private String internalServiceToken;

    @Value("${m8.single-text-max:500}")
    private int singleTextMax;

    public M8TtsClient(ObjectMapper om) {
        this.om = om;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(10_000))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /** 对外唯一入口：任意长 text → 按标点切 ≤(singleTextMax-20) 段，逐段调 M8，按序返回 List<audioUrl>。 */
    public List<String> synthesizeChunkedUrls(String text, String courseId, String voiceType, Double speed, String userJwt) {
        if (text == null || text.isBlank()) return List.of();
        String v = (voiceType == null || voiceType.isBlank()) ? defaultVoice : voiceType;
        double s = (speed == null) ? defaultSpeed : speed;
        if (s < 0.5 || s > 2.0) throw new IllegalArgumentException("speed 必须在 [0.5, 2.0]，实际: " + s);

        List<String> parts = splitByPunctuation(text, singleTextMax - 20);
        List<String> urls = new ArrayList<>(parts.size());
        String cid = (courseId != null && courseId.length() > 64) ? courseId.substring(0, 64) : courseId;
        for (int i = 0; i < parts.size(); i++) {
            SynthesizeResponse r = callWithRetry(parts.get(i), cid, v, s, userJwt);
            if (r != null && r.audioUrl != null && !r.audioUrl.isBlank()) {
                urls.add(r.audioUrl);
            } else {
                log.warn("[M8] 第 {}/{} 段合成失败，textLen={}", i + 1, parts.size(), parts.get(i).length());
            }
        }
        return urls;
    }

    // ================================================================
    //  内部：HTTP + 重试 + 响应解析
    // ================================================================

    private SynthesizeResponse callWithRetry(String text, String courseId, String v, double s, String userJwt) {
        Exception lastErr = null;
        for (int i = 1; i <= Math.max(1, retryCount); i++) {
            try { return doPost(text, courseId, v, s, userJwt); }
            catch (Exception e) {
                lastErr = e;
                log.warn("[M8] 第 {}/{} 次调用失败, textLen={}, err={}", i, retryCount, text.length(), e.getMessage());
                if (i < retryCount) try { Thread.sleep(500L * i); } catch (InterruptedException ignore) {}
            }
        }
        throw new RuntimeException("M8 TTS 调用失败（已重试 " + retryCount + " 次）: "
                + (lastErr == null ? "未知错误" : lastErr.getMessage()), lastErr);
    }

    private SynthesizeResponse doPost(String text, String courseId, String v, double s, String userJwt) throws Exception {
        if (userJwt == null || userJwt.isBlank()) {
            throw new IllegalArgumentException(
                    "调用 M8 TTS 缺少用户登录态 JWT (userJwt 为空)。\n" +
                    "请在 Controller 加 @RequestHeader(\"Authorization\")，用 TtsBatchServiceImpl.extractBearer() 取纯 token 后透传。");
        }

        String endpoint = baseUrl.endsWith("/")
                ? baseUrl + "api/virtual-teacher/tts"
                : baseUrl + "/api/virtual-teacher/tts";

        java.util.LinkedHashMap<String, Object> body = new java.util.LinkedHashMap<>();
        if (courseId != null && !courseId.isBlank()) body.put("courseId", courseId);
        body.put("text", text);
        body.put("voiceType", v);
        body.put("speed", s);
        String json = om.writeValueAsString(body);

        String token = userJwt.toLowerCase().startsWith("bearer ") ? userJwt : "Bearer " + userJwt;
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofSeconds(readTimeoutSeconds))
                .header("Content-Type", "application/json")
                .header("Authorization", token)
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        int code = resp.statusCode();
        if (code == 401) throw new RuntimeException("M8 返回 401，JWT 无效或已过期");
        if (code == 429) throw new RuntimeException("M8 返回 429，触发限流");
        if (code == 400) throw new RuntimeException("M8 返回 400 参数非法: " + resp.body());
        if (code >= 500) throw new RuntimeException("M8 返回 5xx: code=" + code + " body=" + resp.body());
        if (code != 200) throw new RuntimeException("M8 返回非预期 HTTP code: " + code + " body=" + resp.body());

        JsonNode root = om.readTree(resp.body());
        JsonNode d = root.has("data") ? root.get("data") : root;
        SynthesizeResponse r = new SynthesizeResponse();
        r.httpStatus = code;
        r.audioUrl = textOrNull(d, "audioUrl");
        r.fileType = textOrNull(d, "fileType");
        if (r.fileType == null || r.fileType.isBlank()) r.fileType = "wav";
        r.fileSize = longOrZero(d, "fileSize");
        r.cacheHit = boolOrFalse(d, "cacheHit");
        r.cacheKey = textOrNull(d, "cacheKey");
        if (r.audioUrl == null || r.audioUrl.isBlank())
            throw new RuntimeException("M8 响应无 audioUrl 字段，body=" + resp.body());
        return r;
    }

    public static class SynthesizeResponse {
        public int httpStatus;
        public String audioUrl;
        public String fileType;
        public long fileSize;
        public boolean cacheHit;
        public String cacheKey;
    }

    /** public 方便单元测试复用：按标点切分，每段 ≤ maxChars。 */
    public static List<String> splitByPunctuation(String raw, int maxChars) {
        List<String> out = new ArrayList<>();
        String s = raw == null ? "" : raw;
        while (s.length() > maxChars) {
            int cut = -1;
            for (String pat : new String[]{"\n", "。", "！", "？", ".", "!", "?", "；", ";", "，", ",", " "}) {
                int idx = s.lastIndexOf(pat, maxChars);
                if (idx > 0) { cut = idx + pat.length(); break; }
            }
            if (cut <= 0) cut = maxChars;
            out.add(s.substring(0, cut).trim());
            s = s.substring(cut).trim();
        }
        if (!s.isEmpty()) out.add(s);
        return out;
    }

    private static String textOrNull(JsonNode n, String f) {
        if (n == null || !n.has(f) || n.get(f).isNull()) return null;
        return n.get(f).asText();
    }
    private static long longOrZero(JsonNode n, String f) {
        if (n == null || !n.has(f) || n.get(f).isNull()) return 0L;
        try { return n.get(f).asLong(); } catch (Exception e) { return 0L; }
    }
    private static boolean boolOrFalse(JsonNode n, String f) {
        if (n == null || !n.has(f) || n.get(f).isNull()) return false;
        try { return n.get(f).asBoolean(); } catch (Exception e) { return false; }
    }
}
