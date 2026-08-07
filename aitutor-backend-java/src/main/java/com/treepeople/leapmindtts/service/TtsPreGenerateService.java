package com.treepeople.leapmindtts.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Slf4j
@Service
@RequiredArgsConstructor
public class TtsPreGenerateService {
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final SseEmitterService sseEmitterService;

    @Qualifier("ttsTaskExecutor")
    private final Executor ttsTaskExecutor;

    @Value("${tts.api-url}")
    private String ttsApiUrl;

    /**
     * 异步批量生成幻灯片配音，绑定ttsTaskExecutor线程池，最大并发3
     * @param prepId 备课ID
     * @param pptJsonData 原始PPT页面JSON字符串
     */
    @Async("ttsTaskExecutor")
    public void processBatchTtsAsync(Long prepId, String pptJsonData) {
        try {
            JsonNode rootNode = objectMapper.readTree(pptJsonData);
            JsonNode pagesNode = rootNode.get("pages");
            ArrayNode pages = null;
            // 安全强转，防止pages不是数组抛出异常
            if (pagesNode instanceof ArrayNode) {
                pages = (ArrayNode) pagesNode;
            }
            if (pages == null || pages.isEmpty()) {
                log.warn("备课{}无幻灯片数据，跳过TTS生成", prepId);
                return;
            }

            List<CompletableFuture<Void>> futures = new ArrayList<>();
            for (int i = 0; i < pages.size(); i++) {
                final int pageIndex = i;
                ObjectNode pageNode = (ObjectNode) pages.get(i);
                String narrationText = pageNode.has("narrationText") ? pageNode.get("narrationText").asText() : "";
                if (narrationText.isBlank()) {
                    continue;
                }

                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    log.info("prepId:{} 第{}页开始生成TTS配音", prepId, pageIndex + 1);
                    // 调用M8 TTS语音接口
                    TtsRequest req = new TtsRequest(narrationText, "default", 1.0);
                    TtsResponse resp = restTemplate.postForObject(ttsApiUrl, req, TtsResponse.class);
                    if (resp != null && resp.getAudioUrl() != null) {
                        pageNode.put("audioUrl", resp.getAudioUrl());
                    }
                }, ttsTaskExecutor).exceptionally(ex -> {
                    log.error("备课{} 第{}页TTS生成失败", prepId, pageIndex + 1, ex);
                    return null;
                });
                futures.add(future);
            }

            // 阻塞等待所有页面配音生成完成
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            String updatedJson = rootNode.toString();
            // 更新teaching_contents表的ppt_structure字段，写入带audioUrl的完整JSON
            jdbcTemplate.update(
                    "UPDATE teaching_contents SET ppt_structure = ? WHERE prep_id = ?",
                    updatedJson, prepId
            );
            log.info("备课{}全部幻灯片语音生成完成，已更新数据库", prepId);
            // 通过SSE推送给前端配音完成通知
            sseEmitterService.sendEventMessage(prepId, "VOICE_READY", "所有幻灯片配音已生成完毕");
        } catch (Exception e) {
            log.error("备课{} 批量TTS全局任务异常", prepId, e);
        }
    }

    /**
     * TTS接口请求体
     * @param text 旁白文本
     * @param voiceType 音色
     * @param speed 语速
     */
    public record TtsRequest(String text, String voiceType, double speed) {}

    /**
     * TTS接口返回体
     */
    public static class TtsResponse {
        private String audioUrl;

        public String getAudioUrl() {
            return audioUrl;
        }

        public void setAudioUrl(String audioUrl) {
            this.audioUrl = audioUrl;
        }
    }
}
