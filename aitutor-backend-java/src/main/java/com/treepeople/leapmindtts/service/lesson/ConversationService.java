package com.treepeople.leapmindtts.service.lesson;

import com.treepeople.leapmindtts.pojo.dto.ConversationRequest;
import com.treepeople.leapmindtts.pojo.dto.ConversationSession;
import com.treepeople.leapmindtts.pojo.dto.ConversationRequest.InputType;
import com.treepeople.leapmindtts.pojo.dto.ConversationRequest.SceneType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.Disposable;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Flux;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Slf4j
public class ConversationService {

    private final AIModelService aiModelService;
    private final AiTeacherBaiduAsrService aiTeacherBaiduAsrService;
    private final WebClient webClient;

    private final ConcurrentHashMap<String, ConversationSession> sessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, BaseSubscriber<AIModelService.AiChunk>> activeSubscribers = new ConcurrentHashMap<>();

    public ConversationService(AIModelService aiModelService, AiTeacherBaiduAsrService aiTeacherBaiduAsrService, WebClient.Builder webClientBuilder) {
        this.aiModelService = aiModelService;
        this.aiTeacherBaiduAsrService = aiTeacherBaiduAsrService;
        this.webClient = webClientBuilder.build();
    }

    @PostConstruct
    public void init() {
        log.info("ConversationService initialized");
    }

    public String getOrCreateSessionId(ConversationRequest req) {
        if (req.getSessionId() != null && sessions.containsKey(req.getSessionId())) {
            return req.getSessionId();
        }
        String sid = UUID.randomUUID().toString();
        var session = new ConversationSession();
        session.setSessionId(sid);
        session.setUserId(req.getUserId());
        session.setSceneType(req.getSceneType() != null ? req.getSceneType() : SceneType.general_qa);
        session.setContext(req.getContext());
        session.setCreatedAt(Instant.now().toEpochMilli());
        session.setUpdatedAt(Instant.now().toEpochMilli());
        sessions.put(sid, session);
        log.info("Created new session: {}", sid);
        return sid;
    }

    public ConversationSession getSession(String sessionId) {
        return sessions.get(sessionId);
    }

    public List<ConversationSession> listSessions(Long userId) {
        var result = new ArrayList<ConversationSession>();
        for (var s : sessions.values()) {
            if (s.getUserId() != null && s.getUserId().equals(userId)) {
                result.add(s);
            }
        }
        result.sort((a, b) -> Long.compare(b.getUpdatedAt(), a.getUpdatedAt()));
        return result;
    }

    public void deleteSession(String sessionId) {
        cleanup(sessionId);
        sessions.remove(sessionId);
        log.info("Deleted session: {}", sessionId);
    }

    /**
     * 使用 Reactor Flux + ServerSentEvent（Spring WebFlux 原生类型）
     * 实现 SSE 流式对话，由 Spring MVC ReactiveTypeHandler 驱动异步响应。
     */
    public Flux<ServerSentEvent<?>> streamResponse(ConversationRequest req) {
        String sessionId = getOrCreateSessionId(req);
        var session = sessions.get(sessionId);
        var callId = "call_" + UUID.randomUUID().toString().substring(0, 8);

        // 语音输入：下载音频 → ASR 转文字 → 替换 question
        if (req.getInputType() == InputType.voice && req.getAttachmentUrls() != null && !req.getAttachmentUrls().isEmpty()) {
            String audioUrl = req.getAttachmentUrls().get(0);
            log.info("Processing voice attachment: {}", audioUrl);
            try {
                byte[] audioData = webClient.get().uri(audioUrl).retrieve().bodyToMono(byte[].class)
                    .timeout(Duration.ofSeconds(15)).block();
                if (audioData != null && audioData.length > 0) {
                    String transcribed = aiTeacherBaiduAsrService.recognize(audioData)
                        .timeout(Duration.ofSeconds(15)).block();
                    if (transcribed != null && !transcribed.isEmpty()) {
                        req.setQuestion(transcribed);
                        log.info("Voice transcribed, final question: {}", req.getQuestion());
                    }
                }
            } catch (Exception e) {
                log.error("Voice transcription failed: {}", e.getMessage());
            }
        }

        if (req.getQuestion() != null && !req.getQuestion().isEmpty()) {
            session.getMessages().add(Map.of("role", "user", "content", req.getQuestion()));
        }
        session.setUpdatedAt(Instant.now().toEpochMilli());

        StringBuilder fullAnswer = new StringBuilder();
        AtomicInteger index = new AtomicInteger(0);

        return Flux.create(fluxSink -> {
            // 1. 先发 thinking 事件（带上 sessionId，打断用）
            fluxSink.next(sseEvent("message",
                String.format("{\"type\":\"thinking\",\"content\":\"\",\"sessionId\":\"%s\"}", sessionId)));

            // 2. 订阅 AI 流
            BaseSubscriber<AIModelService.AiChunk> subscriber = new BaseSubscriber<>() {
                @Override
                protected void hookOnNext(AIModelService.AiChunk chunk) {
                    fullAnswer.append(chunk.getChunk());

                    if (chunk.isLast()) {
                        String answer = fullAnswer.toString();
                        session.getMessages().add(Map.of("role", "assistant", "content", answer));

                        fluxSink.next(sseEvent("message",
                            String.format("{\"type\":\"done\",\"callId\":\"%s\",\"sessionId\":\"%s\",\"tokenUsage\":{\"input\":%d,\"output\":%d}}",
                                callId, sessionId,
                                chunk.getInputTokens() != null ? chunk.getInputTokens() : 0,
                                chunk.getOutputTokens() != null ? chunk.getOutputTokens() : 0)));
                        fluxSink.complete();
                        return;
                    }

                    fluxSink.next(sseEvent("message",
                        String.format("{\"type\":\"content\",\"chunk\":\"%s\",\"index\":%d}",
                            escapeJson(chunk.getChunk()), index.getAndIncrement())));
                }

                @Override
                protected void hookOnCancel() {
                    log.info("AI stream cancelled for session: {}", sessionId);
                    String answer = fullAnswer.toString();
                    if (!answer.isEmpty()) {
                        session.getMessages().add(Map.of("role", "assistant", "content", answer));
                    }
                    fluxSink.next(sseEvent("interrupt",
                        "{\"type\":\"interrupted\",\"message\":\"用户已打断\"}"));
                    fluxSink.complete();
                }

                @Override
                protected void hookOnError(Throwable t) {
                    log.error("AI stream error for session {}: {}", sessionId, t.getMessage());
                    fluxSink.next(sseEvent("message",
                        String.format("{\"type\":\"error\",\"message\":\"%s\"}", escapeJson(t.getMessage()))));
                    fluxSink.complete();
                }

                @Override
                protected void hookOnComplete() {
                    log.warn("AI stream completed without last marker for session: {}", sessionId);
                    String answer = fullAnswer.toString();
                    if (!answer.isEmpty()) {
                        session.getMessages().add(Map.of("role", "assistant", "content", answer));
                    }
                    fluxSink.next(sseEvent("message",
                        String.format("{\"type\":\"done\",\"callId\":\"%s\",\"sessionId\":\"%s\",\"tokenUsage\":{\"input\":0,\"output\":0}}",
                            callId, sessionId)));
                    fluxSink.complete();
                }
            };

            List<Map<String, String>> history = session.getMessages();
            if (history.size() > 20) {
                history = history.subList(history.size() - 20, history.size());
            }
            aiModelService.streamAIResponse(history, req.getInputType(), req.getAttachmentUrls())
                .subscribe(subscriber);
            activeSubscribers.put(sessionId, subscriber);

            fluxSink.onCancel(() -> {
                log.info("Flux cancelled for session: {}", sessionId);
                cleanup(sessionId);
            });
            fluxSink.onDispose(() -> {
                log.info("Flux disposed for session: {}", sessionId);
                cleanup(sessionId);
            });
        });
    }

    public void interrupt(String sessionId) {
        log.info("Interrupting session: {}", sessionId);

        // 先发打断事件（如果有 session 尚在连接中）
        // Flux.create 方式下无法直接向 fluxSink 发事件，
        // 通过取消 subscriber 触发 hookOnCancel → fluxSink.complete()

        BaseSubscriber<AIModelService.AiChunk> sub = activeSubscribers.get(sessionId);
        if (sub != null && !sub.isDisposed()) {
            sub.dispose(); // 触发 hookOnCancel，内部完成 fluxSink
        }
    }

    private void cleanup(String sessionId) {
        BaseSubscriber<AIModelService.AiChunk> sub = activeSubscribers.remove(sessionId);
        if (sub != null && !sub.isDisposed()) {
            sub.dispose();
        }
    }

    private static ServerSentEvent<?> sseEvent(String eventName, String data) {
        return ServerSentEvent.builder()
            .event(eventName)
            .data(data)
            .build();
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }
}
