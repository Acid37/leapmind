package com.treepeople.leapmindtts.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class SseEmitterService {
    private final Map<Long, SseEmitter> emitterMap = new ConcurrentHashMap<>();
    private final TtsPreGenerateService ttsPreGenerateService;

    public SseEmitterService(@Lazy TtsPreGenerateService ttsPreGenerateService) {
        this.ttsPreGenerateService = ttsPreGenerateService;
    }

    public SseEmitter createEmitter(Long prepId) {
        // 5分钟超时，单位毫秒
        SseEmitter emitter = new SseEmitter(300000L);
        emitterMap.put(prepId, emitter);

        emitter.onCompletion(() -> {
            log.info("备课 {} SSE连接完成", prepId);
            emitterMap.remove(prepId);
        });
        emitter.onTimeout(() -> {
            log.warn("备课 {} SSE连接超时", prepId);
            emitter.complete();
            emitterMap.remove(prepId);
        });
        emitter.onError((e) -> {
            log.error("备课 {} SSE异常", prepId, e);
            emitter.completeWithError(e);
            emitterMap.remove(prepId);
        });
        return emitter;
    }

    /**
     * 推送消息给前端
     */
    public void sendEventMessage(Long prepId, String eventName, Object data) {
        SseEmitter emitter = emitterMap.get(prepId);
        if (emitter == null) {
            log.warn("备课{}无活跃SSE连接", prepId);
            return;
        }
        try {
            emitter.send(SseEmitter.event()
                    .name(eventName)
                    .data(data));
        } catch (IOException e) {
            log.error("推送消息失败，移除备课{}连接", prepId, e);
            emitterMap.remove(prepId);
        }
    }

    /**
     * AI生成全部完成后，自动触发批量TTS生成
     * 在Controller流式接收完Python完整JSON后调用此方法
     */
    public void triggerAutoTts(Long prepId, String fullPptJson) {
        ttsPreGenerateService.processBatchTtsAsync(prepId, fullPptJson);
        sendEventMessage(prepId, "GEN_DONE", "AI备课内容生成完毕，后台自动生成配音中...");
    }
}
