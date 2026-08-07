package com.treepeople.leapmindtts.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * [跨端联桥] Java → Python HTTP 调用客户端。
 * 对应 Python 端 internal_ai.py:
 *   POST /api/internal/ai/generate-lesson-prep         — 非流式
 *   POST /api/internal/ai/generate/stream              — 流式（SSE 透传）
 *
 * <h3>数据格式说明</h3>
 * <ul>
 *   <li>请求：Jackson 序列化时 @JsonProperty 将 camelCase 字段转为 snake_case 发送给 Python</li>
 *   <li>响应：Python 返回 snake_case（如 prep_id, total_pages, page_num），
 *            本类在 parse 后不走 Jackson 绑定，手动提取并统一转为 camelCase，
 *            保证存入 DB 的 slides JSON 与 PptStructureDTO.parse() 兼容</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PythonApiClient {

    private final ObjectMapper om;
    private final WebClient.Builder webClientBuilder;

    /**
     * 流式专用 WebClient（Bean 名 = streamingWebClient，Spring 按构造参数名匹配注入）。
     */
    private final WebClient streamingWebClient;

    @Value("${python.service.base-url:http://localhost:8001}")
    private String pythonBaseUrl;

    /**
     * [跨端联桥] 调用 Python 非流式备课生成。
     *
     * @param request 备课参数（title/subject/grade/knowledgePointIds 等）
     * @return LessonPrepResponse，其中 slides 已转为 camelCase
     */
    public LessonPrepResponse generateLessonPrep(LessonPrepRequest request) {
        log.info("[联桥] 调用 Python 备课生成, title={}, subject={}, grade={}",
                request.getTitle(), request.getSubject(), request.getGrade());

        WebClient client = webClientBuilder
                .baseUrl(pythonBaseUrl)
                .build();

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> raw = client.post()
                    .uri("/api/internal/ai/generate-lesson-prep")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .onStatus(status -> status.isError(), resp ->
                            resp.bodyToMono(String.class)
                                    .flatMap(body -> Mono.error(
                                            new RuntimeException("Python API 返回错误: " + body))))
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(180))
                    .block();

            if (raw == null) {
                throw new RuntimeException("Python 返回为空");
            }
            if (raw.containsKey("error")) {
                throw new RuntimeException("Python 备课生成失败: " + raw.get("error"));
            }

            LessonPrepResponse response = new LessonPrepResponse();
            response.setPrepId(toInt(raw.get("prep_id")));
            response.setTotalPages(toInt(raw.get("total_pages")));

            // syllabus 原样保留（Java 不解析其内部结构，只透传存入 generated_content_json）
            @SuppressWarnings("unchecked")
            Map<String, Object> syllabus = (Map<String, Object>) raw.get("syllabus");
            response.setSyllabus(syllabus);

            // slides 需要把 key 从 snake_case → camelCase，否则 PptStructureDTO.parse() 不认
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rawSlides = (List<Map<String, Object>>) raw.get("slides");
            if (rawSlides != null) {
                List<Map<String, Object>> camelSlides = new ArrayList<>();
                for (Map<String, Object> s : rawSlides) {
                    camelSlides.add(convertKeysToCamel(s));
                }
                response.setSlides(camelSlides);
            } else {
                response.setSlides(List.of());
            }

            log.info("[联桥] Python 备课生成成功, prepId={}, totalPages={}",
                    response.getPrepId(), response.getTotalPages());
            return response;

        } catch (Exception e) {
            log.error("[联桥] 调用 Python 备课生成失败", e);
            throw new RuntimeException("Python AI 备课生成失败: " + e.getMessage(), e);
        }
    }

    // =========================================================================
    //  SSE 流式备课生成 —— 透传 Python SSE 到 Java 前端 SseEmitter
    // =========================================================================

    /**
     * [SSE流式] 调用 Python 备课生成，并将事件逐一写入传入的 SseEmitter。
     * <p>
     * Python 端 SSE 格式（见 lesson_prep_service._sse_event）：
     * <pre>
     *   data: {"type":"outline",  "title":"...", "sections":[...]}
     *   data: {"type":"section",  "hourIndex":1, ...}
     *   data: {"type":"slide",    "pageNum":1, "type":"cover", ...}
     *   data: {"type":"slidesDone", "totalPages":11}
     *   data: {"type":"narration","pageNum":3, "narrationText":"...", ...}
     *   data: {"type":"warn",     "message":"..."}
     *   data: {"type":"error",    "stage":"all", "message":"..."}
     *   data: {"type":"done",     "prepId":0, "totalPages":11, "totalDurationSeconds":664, "errors":[]}
     * </pre>
     * 每个 data 行末尾附带空行分隔，Spring ServerSentEvent 已自动聚合多行 data 为单个事件。
     * <p>
     * 本方法阻塞当前线程（调用方应提交到 sseStreamExecutor 异步线程池）。
     * 返回的 {@link StreamedLessonPrepResult} 包含流式累积的最终大纲 / PPT 结构 / 讲解词，
     * 供调用方写入 teaching_contents 表。
     */
    public StreamedLessonPrepResult streamLessonPrep(LessonPrepRequest request, SseEmitter emitter) {
        log.info("[联桥-SSE] 开始流式备课 title={}, subject={}", request.getTitle(), request.getSubject());

        // 1. 组装 Python internal_ai.py 期望的 AICallRequest 包装体
        Map<String, Object> extra = buildExtraSnakeMap(request);
        Map<String, Object> aiCall = new LinkedHashMap<>();
        aiCall.put("module_name", "prep");
        aiCall.put("scene_type", "generate_lesson");
        aiCall.put("prompt", "");
        aiCall.put("max_tokens", null);
        aiCall.put("temperature", null);
        aiCall.put("extra", extra);

        StreamAccumulator acc = new StreamAccumulator();

        try {
            // 2. 以 text/event-stream Accept 调用 Python，并订阅 Flux<ServerSentEvent>
            streamingWebClient
                    .mutate()
                    .baseUrl(pythonBaseUrl)
                    .build()
                    .post()
                    .uri("/api/internal/ai/generate/stream")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .header("X-Accel-Buffering", "no")
                    .header("Cache-Control", "no-cache")
                    .bodyValue(aiCall)
                    .retrieve()
                    .onStatus(status -> status.isError(),
                            resp -> resp.bodyToMono(String.class)
                                    .flatMap(body -> Mono.error(new RuntimeException(
                                            "Python SSE 接口 HTTP 错误: " + resp.statusCode() + " - " + body))))
                    .bodyToFlux(new org.springframework.core.ParameterizedTypeReference<ServerSentEvent<String>>() {})
                    .doOnNext(sse -> handlePythonSse(acc, emitter, sse))
                    .doOnError(err -> {
                        log.error("[联桥-SSE] Python 流异常", err);
                        try {
                            sendSseError(emitter, "STREAM_ERROR", "Python 流异常: " + err.getMessage());
                        } catch (Exception ignore) {}
                    })
                    .doFinally(sig -> log.info("[联桥-SSE] Python 上游终止信号: {}", sig))
                    .blockLast();
        } catch (Exception e) {
            log.error("[联桥-SSE] 流式备课整体异常", e);
            try {
                sendSseError(emitter, "STREAM_EXCEPTION", e.getMessage());
            } catch (Exception ignore) {}
        }

        // 3. 将累积的 slide 列表做 key 统一（虽然 Python 侧已 camel，但为稳妥再转换一次）
        List<Map<String, Object>> camelSlides = new ArrayList<>();
        for (Map<String, Object> s : acc.slides) {
            camelSlides.add(convertKeysToCamel(s));
        }

        StreamedLessonPrepResult result = new StreamedLessonPrepResult();
        result.setPrepId(acc.prepId);
        result.setTotalPages(acc.totalPages);
        result.setSyllabus(acc.syllabus != null ? convertKeysToCamel(acc.syllabus) : null);
        result.setSlides(camelSlides);
        result.setTotalDurationSeconds(acc.totalDurationSeconds);
        result.setErrors(acc.errors);
        log.info("[联桥-SSE] 流式备课结束，prepId={}, totalPages={}, errors={}",
                result.getPrepId(), result.getTotalPages(), result.getErrors().size());
        return result;
    }

    // =========================================================================
    //  SSE 流式辅助 —— 处理每条 Python SSE、写入 SseEmitter、累积大纲/PPT/讲解词
    // =========================================================================

    private static final class StreamAccumulator {
        int prepId;
        int totalPages;
        int totalDurationSeconds;
        Map<String, Object> syllabus;
        /** 按 pageNum 顺序保存的 slide JSON（已注入 narration→notes） */
        final List<Map<String, Object>> slides = new ArrayList<>();
        final List<String> errors = new ArrayList<>();

        /** 通过 pageNum 找到 slide map，找不到返回 null */
        Map<String, Object> findSlide(int pageNum) {
            // 以 pageNum 优先匹配；其次按顺序位置 i+1 == pageNum
            for (Map<String, Object> s : slides) {
                Object pn = s.get("pageNum");
                if (pn instanceof Number && ((Number) pn).intValue() == pageNum) return s;
            }
            if (pageNum >= 1 && pageNum <= slides.size()) {
                return slides.get(pageNum - 1);
            }
            return null;
        }
    }

    /**
     * 处理 Python 发来的单条 ServerSentEvent：
     *  - 解析 data JSON，取出 type 字段；
     *  - 用 type 作为 SseEmitter 的 event name，其余字段作为 data；
     *  - 同时按类型将内容累积到 acc（供最后写 DB）。
     */
    @SuppressWarnings("unchecked")
    private void handlePythonSse(StreamAccumulator acc, SseEmitter emitter,
                                 org.springframework.http.codec.ServerSentEvent<String> sse) {
        String raw = sse.data();
        if (raw == null || raw.isBlank()) return;
        try {
            JsonNode root = om.readTree(raw);
            JsonNode typeNode = root.path("type");
            String type = typeNode.isTextual() ? typeNode.asText() : "message";

            // 去掉 type 字段后，其余转为 Map 作为 event data
            Map<String, Object> payload = om.convertValue(root, Map.class);
            payload.remove("type");

            // === 分发：写 SseEmitter + 累计 ===
            switch (type) {
                case "outline":
                    acc.syllabus = new LinkedHashMap<>(payload);
                    sendSse(emitter, "outline", payload);
                    break;

                case "section":
                    sendSse(emitter, "section", payload);
                    break;

                case "slide":
                    // slide payload 中已有 pageNum
                    acc.slides.add(new LinkedHashMap<>(payload));
                    sendSse(emitter, "slide", payload);
                    break;

                case "slidesDone":
                    Object tp = payload.get("totalPages");
                    if (tp instanceof Number) acc.totalPages = ((Number) tp).intValue();
                    sendSse(emitter, "slidesDone", payload);
                    break;

                case "narration":
                    Object pn = payload.get("pageNum");
                    Object narrationText = payload.get("narrationText");
                    if (pn instanceof Number && narrationText != null) {
                        Map<String, Object> slide = acc.findSlide(((Number) pn).intValue());
                        if (slide != null) {
                            // 按项目惯例：讲解词写入 slide.notes（后续 TTS 再以 [AUDIO_URL:] 前缀回填）
                            slide.put("notes", String.valueOf(narrationText));
                        }
                    }
                    sendSse(emitter, "narration", payload);
                    break;

                case "warn":
                    sendSse(emitter, "warn", payload);
                    break;

                case "error":
                    Object msg = payload.get("message");
                    if (msg != null) acc.errors.add(String.valueOf(msg));
                    sendSse(emitter, "error", payload);
                    break;

                case "done":
                    Object pi = payload.get("prepId");
                    if (pi instanceof Number) acc.prepId = ((Number) pi).intValue();
                    Object tpp = payload.get("totalPages");
                    if (tpp instanceof Number && acc.totalPages == 0)
                        acc.totalPages = ((Number) tpp).intValue();
                    Object dur = payload.get("totalDurationSeconds");
                    if (dur instanceof Number)
                        acc.totalDurationSeconds = ((Number) dur).intValue();
                    sendSse(emitter, "done", payload);
                    // 所有事件处理完成，关闭 SseEmitter（前端收到 done 也会自己关，这里保险起见）
                    emitter.complete();
                    break;

                default:
                    // 未知事件原样透传，避免未来扩展时丢事件
                    sendSse(emitter, type, payload);
                    break;
            }
        } catch (Exception e) {
            log.warn("[联桥-SSE] 解析事件失败 raw={} err={}",
                    raw.length() > 120 ? raw.substring(0, 120) + "..." : raw, e.getMessage());
        }
    }

    private void sendSse(SseEmitter emitter, String eventName, Object data) {
        try {
            emitter.send(SseEmitter.event()
                    .name(eventName)
                    .data(data, MediaType.APPLICATION_JSON));
        } catch (IllegalStateException e) {
            // SseEmitter 已完成/超时：只打 warn，不打断上游流消费
            log.debug("[联桥-SSE] SseEmitter 已关闭，丢弃事件 {}: {}", eventName, e.getMessage());
        } catch (IOException e) {
            log.warn("[联桥-SSE] 写入 SseEmitter 失败 event={}", eventName, e);
            try {
                emitter.completeWithError(e);
            } catch (Exception ignore) {}
        }
    }

    private void sendSseError(SseEmitter emitter, String code, String message) {
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("stage", "all");
        err.put("errorCode", code);
        err.put("message", message != null ? message : "unknown error");
        err.put("timestamp", System.currentTimeMillis());
        try {
            emitter.send(SseEmitter.event().name("error").data(err, MediaType.APPLICATION_JSON));
        } catch (Exception ignore) {}
        emitter.complete();
    }

    /**
     * 构造 internal_ai.py 的 extra 字段：全部 snake_case 键。
     * Python 端以 extra.get("user_id") / extra.get("title") 等直接取 key。
     */
    private Map<String, Object> buildExtraSnakeMap(LessonPrepRequest r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("user_id", r.getUserId());
        m.put("title", r.getTitle());
        m.put("subject", r.getSubject());
        m.put("grade", r.getGrade());
        m.put("knowledge_point_ids", r.getKnowledgePointIds());
        m.put("teaching_goals", r.getTeachingGoals());
        m.put("total_hours", r.getTotalHours());
        m.put("style", r.getStyle());
        m.put("weak_point_ids", r.getWeakPointIds());
        m.put("user_profile_summary", r.getUserProfileSummary());
        return m;
    }

    // ============ 流式最终结果 DTO（返回给 Controller 层写 DB） ============
    @Data
    public static class StreamedLessonPrepResult {
        private int prepId;
        private int totalPages;
        private Map<String, Object> syllabus;
        private List<Map<String, Object>> slides;
        private int totalDurationSeconds;
        private List<String> errors;

        public StreamedLessonPrepResult() {
            this.errors = new ArrayList<>();
        }
    }

    /**
     * 将 map 的所有 key 从 snake_case 转为 camelCase（递归处理嵌套 map/list）。
     * 例如 page_num→pageNum, bullet_points→bulletPoints。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> convertKeysToCamel(Map<String, Object> snakeMap) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : snakeMap.entrySet()) {
            String camelKey = snakeToCamel(entry.getKey());
            Object value = entry.getValue();
            if (value instanceof Map) {
                value = convertKeysToCamel((Map<String, Object>) value);
            } else if (value instanceof List) {
                List<Object> list = (List<Object>) value;
                List<Object> converted = new ArrayList<>();
                for (Object item : list) {
                    if (item instanceof Map) {
                        converted.add(convertKeysToCamel((Map<String, Object>) item));
                    } else {
                        converted.add(item);
                    }
                }
                value = converted;
            }
            result.put(camelKey, value);
        }
        return result;
    }

    private String snakeToCamel(String snake) {
        StringBuilder sb = new StringBuilder();
        boolean upper = false;
        for (int i = 0; i < snake.length(); i++) {
            char c = snake.charAt(i);
            if (c == '_') {
                upper = true;
            } else if (upper) {
                sb.append(Character.toUpperCase(c));
                upper = false;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private int toInt(Object val) {
        if (val instanceof Number) return ((Number) val).intValue();
        if (val instanceof String) return Integer.parseInt((String) val);
        return 0;
    }

    // ========== 请求/响应 DTO ==========

    /**
     * 发给 Python POST /api/internal/ai/generate-lesson-prep 的请求体。
     * Python LessonPrepInternalRequest 用 snake_case，所以每个字段加 @JsonProperty。
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LessonPrepRequest {
        @JsonProperty("user_id")
        private int userId;

        @JsonProperty("title")
        private String title;

        @JsonProperty("subject")
        private String subject;

        @JsonProperty("grade")
        private String grade;

        @JsonProperty("knowledge_point_ids")
        private List<Integer> knowledgePointIds;

        @JsonProperty("teaching_goals")
        private List<String> teachingGoals;

        @JsonProperty("total_hours")
        private int totalHours = 1;

        @JsonProperty("style")
        private String style = "standard";

        @JsonProperty("weak_point_ids")
        private List<Integer> weakPointIds;

        @JsonProperty("user_profile_summary")
        private String userProfileSummary;
    }

    /**
     * Python generate_and_return() 返回的数据（经过本 client 已统一转为 camelCase）。
     */
    @Data
    public static class LessonPrepResponse {
        private int prepId;
        private int totalPages;
        private Map<String, Object> syllabus;
        private List<Map<String, Object>> slides;
    }
}
