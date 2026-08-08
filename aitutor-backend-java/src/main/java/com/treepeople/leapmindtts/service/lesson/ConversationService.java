package com.treepeople.leapmindtts.service.lesson;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.treepeople.leapmindtts.config.ConversationProperties;
import com.treepeople.leapmindtts.mapper.ConversationMessageMapper;
import com.treepeople.leapmindtts.mapper.ConversationSessionMapper;
import com.treepeople.leapmindtts.pojo.dto.ConversationRequest;
import com.treepeople.leapmindtts.pojo.dto.ConversationRequest.InputType;
import com.treepeople.leapmindtts.pojo.dto.ConversationRequest.SceneType;
import com.treepeople.leapmindtts.pojo.dto.ConversationSession;
import com.treepeople.leapmindtts.pojo.entity.ConversationMessageEntity;
import com.treepeople.leapmindtts.pojo.entity.ConversationSessionEntity;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ConversationService {

    private static final String REDIS_KEY_PREFIX = "user:session:";

    private final AIModelService aiModelService;
    private final AiTeacherBaiduAsrService aiTeacherBaiduAsrService;
    private final WebClient webClient;
    private final StringRedisTemplate stringRedisTemplate;
    private final ConversationSessionMapper sessionMapper;
    private final ConversationMessageMapper messageMapper;
    private final ConversationProperties properties;
    private final ObjectMapper objectMapper;

    private final ConcurrentHashMap<String, BaseSubscriber<AIModelService.AiChunk>> activeSubscribers = new ConcurrentHashMap<>();

    public ConversationService(AIModelService aiModelService,
                               AiTeacherBaiduAsrService aiTeacherBaiduAsrService,
                               WebClient.Builder webClientBuilder,
                               StringRedisTemplate stringRedisTemplate,
                               ConversationSessionMapper sessionMapper,
                               ConversationMessageMapper messageMapper,
                               ConversationProperties properties,
                               ObjectMapper objectMapper) {
        this.aiModelService = aiModelService;
        this.aiTeacherBaiduAsrService = aiTeacherBaiduAsrService;
        this.webClient = webClientBuilder.build();
        this.stringRedisTemplate = stringRedisTemplate;
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        log.info("ConversationService initialized (timeout={}, messageLimit={})",
                properties.getTimeout(), properties.getMessageLimit());
    }

    public String getOrCreateSessionId(ConversationRequest req) {
        if (req.getSessionId() != null) {
            ConversationSession existing = loadSessionFromRedis(req.getSessionId());
            if (existing != null) {
                extendRedisTtl(req.getSessionId());
                return req.getSessionId();
            }
            ConversationSessionEntity entity = sessionMapper.selectBySessionId(req.getSessionId());
            if (entity != null) {
                ConversationSession restored = toDto(entity);
                restored.setMessages(loadMessagesFromDb(req.getSessionId()));
                saveSessionToRedis(restored);
                return req.getSessionId();
            }
        }

        String sid = UUID.randomUUID().toString();
        var session = new ConversationSession();
        session.setSessionId(sid);
        session.setUserId(req.getUserId());
        session.setSceneType(req.getSceneType() != null ? req.getSceneType() : SceneType.general_qa);
        session.setContext(req.getContext());
        session.setCreatedAt(Instant.now().toEpochMilli());
        session.setUpdatedAt(Instant.now().toEpochMilli());

        saveSessionToRedis(session);
        saveSessionToDb(session);
        log.info("Created new session: {} for userId={}", sid, req.getUserId());
        return sid;
    }

    public ConversationSession getSession(String sessionId) {
        ConversationSession session = loadSessionFromRedis(sessionId);
        if (session != null) {
            return session;
        }
        ConversationSessionEntity entity = sessionMapper.selectBySessionId(sessionId);
        if (entity == null) {
            return null;
        }
        ConversationSession dto = toDto(entity);
        dto.setMessages(loadMessagesFromDb(sessionId));
        return dto;
    }

    public List<ConversationSession> listSessions(Long userId) {
        List<ConversationSessionEntity> entities = sessionMapper.selectByUserId(userId);
        return entities.stream().map(this::toDto).collect(Collectors.toList());
    }

    public void deleteSession(String sessionId) {
        cleanup(sessionId);
        stringRedisTemplate.delete(REDIS_KEY_PREFIX + sessionId);
        sessionMapper.logicDeleteBySessionId(sessionId);
        messageMapper.logicDeleteBySessionId(sessionId);
        log.info("Deleted session and its messages: {}", sessionId);
    }

    /**
     * 使用 Reactor Flux + ServerSentEvent（Spring WebFlux 原生类型）
     * 实现 SSE 流式对话，由 Spring MVC ReactiveTypeHandler 驱动异步响应。
     */
    public Flux<ServerSentEvent<?>> streamResponse(ConversationRequest req) {
        String sessionId = getOrCreateSessionId(req);
        final ConversationSession session;
        ConversationSession loaded = loadSessionFromRedis(sessionId);
        if (loaded != null) {
            session = loaded;
        } else {
            ConversationSessionEntity entity = sessionMapper.selectBySessionId(sessionId);
            if (entity == null) {
                return Flux.error(new IllegalStateException("Session not found: " + sessionId));
            }
            session = toDto(entity);
            session.setMessages(loadMessagesFromDb(sessionId));
            saveSessionToRedis(session);
        }

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
            Map<String, String> userMsg = Map.of("role", "user", "content", req.getQuestion());
            session.getMessages().add(userMsg);
            saveMessageToDb(sessionId, "user", req.getQuestion(), 0, 0, callId);
        }
        session.setUpdatedAt(Instant.now().toEpochMilli());

        StringBuilder fullAnswer = new StringBuilder();
        AtomicInteger index = new AtomicInteger(0);
        AtomicBoolean messageSaved = new AtomicBoolean(false);

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

                        saveMessageToDb(sessionId, "assistant", answer,
                                chunk.getInputTokens() != null ? chunk.getInputTokens() : 0,
                                chunk.getOutputTokens() != null ? chunk.getOutputTokens() : 0,
                                callId);

                        int msgCount = messageMapper.selectBySessionId(sessionId).size();
                        sessionMapper.updateMessageCount(sessionId, msgCount);
                        saveSessionToRedis(session);

                        fluxSink.next(sseEvent("message",
                            String.format("{\"type\":\"done\",\"callId\":\"%s\",\"sessionId\":\"%s\",\"tokenUsage\":{\"input\":%d,\"output\":%d}}",
                                callId, sessionId,
                                chunk.getInputTokens() != null ? chunk.getInputTokens() : 0,
                                chunk.getOutputTokens() != null ? chunk.getOutputTokens() : 0)));
                        messageSaved.set(true);
                        fluxSink.complete();
                        return;
                    }

                    fluxSink.next(sseEvent("message",
                        String.format("{\"type\":\"content\",\"chunk\":\"%s\",\"index\":%d}",
                            escapeJson(chunk.getChunk()), index.getAndIncrement())));
                }

                @Override
                protected void hookOnCancel() {
                    if (messageSaved.get()) return;
                    log.info("AI stream cancelled for session: {}", sessionId);
                    String answer = fullAnswer.toString();
                    if (!answer.isEmpty()) {
                        session.getMessages().add(Map.of("role", "assistant", "content", answer));
                        saveMessageToDb(sessionId, "assistant", answer, 0, 0, callId);
                        int msgCount = messageMapper.selectBySessionId(sessionId).size();
                        sessionMapper.updateMessageCount(sessionId, msgCount);
                        saveSessionToRedis(session);
                    }
                    fluxSink.next(sseEvent("interrupt",
                        "{\"type\":\"interrupted\",\"message\":\"用户已打断\"}"));
                    fluxSink.complete();
                }

                @Override
                protected void hookOnError(Throwable t) {
                    if (messageSaved.get()) return;
                    log.error("AI stream error for session {}: {}", sessionId, t.getMessage());
                    fluxSink.next(sseEvent("message",
                        String.format("{\"type\":\"error\",\"message\":\"%s\"}", escapeJson(t.getMessage()))));
                    fluxSink.complete();
                }

                @Override
                protected void hookOnComplete() {
                    if (messageSaved.get()) return;
                    log.warn("AI stream completed without last marker for session: {}", sessionId);
                    String answer = fullAnswer.toString();
                    if (!answer.isEmpty()) {
                        session.getMessages().add(Map.of("role", "assistant", "content", answer));
                        saveMessageToDb(sessionId, "assistant", answer, 0, 0, callId);
                        int msgCount = messageMapper.selectBySessionId(sessionId).size();
                        sessionMapper.updateMessageCount(sessionId, msgCount);
                        saveSessionToRedis(session);
                    }
                    fluxSink.next(sseEvent("message",
                        String.format("{\"type\":\"done\",\"callId\":\"%s\",\"sessionId\":\"%s\",\"tokenUsage\":{\"input\":0,\"output\":0}}",
                            callId, sessionId)));
                    fluxSink.complete();
                }
            };

            List<Map<String, String>> allHistory = session.getMessages();
            final List<Map<String, String>> history;
            if (allHistory.size() > properties.getMessageLimit()) {
                history = allHistory.subList(allHistory.size() - properties.getMessageLimit(), allHistory.size());
            } else {
                history = allHistory;
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

    private String redisKey(String sessionId) {
        return REDIS_KEY_PREFIX + sessionId;
    }

    private void saveSessionToRedis(ConversationSession session) {
        try {
            Map<String, Object> toStore = new LinkedHashMap<>();
            toStore.put("sessionId", session.getSessionId());
            toStore.put("userId", session.getUserId());
            toStore.put("sceneType", session.getSceneType() != null ? session.getSceneType().name() : null);
            toStore.put("context", session.getContext());
            toStore.put("createdAt", session.getCreatedAt());
            toStore.put("updatedAt", session.getUpdatedAt());

            List<Map<String, String>> messages = session.getMessages();
            if (messages.size() > properties.getMessageLimit()) {
                messages = messages.subList(messages.size() - properties.getMessageLimit(), messages.size());
            }
            toStore.put("messages", messages);

            String json = objectMapper.writeValueAsString(toStore);
            String key = redisKey(session.getSessionId());
            stringRedisTemplate.opsForValue().set(key, json,
                    properties.getTimeout().toSeconds(), TimeUnit.SECONDS);
            log.info("Saved session to Redis: key={}, TTL={}s", key, properties.getTimeout().toSeconds());
        } catch (Exception e) {
            log.error("Failed to save session to Redis: key={}, error={}", redisKey(session.getSessionId()), e.getMessage(), e);
        }
    }

    private ConversationSession loadSessionFromRedis(String sessionId) {
        String json = stringRedisTemplate.opsForValue().get(redisKey(sessionId));
        if (json == null) {
            return null;
        }
        try {
            Map<String, Object> map = objectMapper.readValue(json, LinkedHashMap.class);
            ConversationSession session = new ConversationSession();
            session.setSessionId((String) map.get("sessionId"));
            session.setUserId(map.get("userId") != null ? ((Number) map.get("userId")).longValue() : null);
            String sceneTypeStr = (String) map.get("sceneType");
            if (sceneTypeStr != null) {
                session.setSceneType(SceneType.valueOf(sceneTypeStr));
            }
            session.setContext((Map<String, Object>) map.get("context"));
            session.setCreatedAt(map.get("createdAt") != null ? ((Number) map.get("createdAt")).longValue() : 0);
            session.setUpdatedAt(map.get("updatedAt") != null ? ((Number) map.get("updatedAt")).longValue() : 0);
            List<Map<String, String>> messages = (List<Map<String, String>>) map.get("messages");
            if (messages != null) {
                session.setMessages(new ArrayList<>(messages));
            }
            return session;
        } catch (Exception e) {
            log.error("Failed to deserialize session from Redis: {}", sessionId, e);
            stringRedisTemplate.delete(redisKey(sessionId));
            return null;
        }
    }

    private void extendRedisTtl(String sessionId) {
        stringRedisTemplate.expire(redisKey(sessionId), properties.getTimeout().toSeconds(), TimeUnit.SECONDS);
    }

    private void saveSessionToDb(ConversationSession session) {
        ConversationSessionEntity entity = new ConversationSessionEntity();
        entity.setSessionId(session.getSessionId());
        entity.setUserId(session.getUserId());
        entity.setSceneType(session.getSceneType() != null ? session.getSceneType().name() : null);
        try {
            entity.setContextJson(objectMapper.writeValueAsString(session.getContext()));
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize context for session: {}", session.getSessionId());
        }
        entity.setMessageCount(session.getMessages() != null ? session.getMessages().size() : 0);
        sessionMapper.insert(entity);
    }

    private void saveMessageToDb(String sessionId, String role, String content, int inputTokens, int outputTokens, String callId) {
        ConversationMessageEntity entity = new ConversationMessageEntity();
        entity.setSessionId(sessionId);
        entity.setRole(role);
        entity.setContent(content);
        entity.setInputTokens(inputTokens);
        entity.setOutputTokens(outputTokens);
        entity.setCallId(callId);
        messageMapper.insert(entity);
    }

    private List<Map<String, String>> loadMessagesFromDb(String sessionId) {
        List<ConversationMessageEntity> entities = messageMapper.selectBySessionId(sessionId);
        return entities.stream()
                .map(e -> Map.of("role", e.getRole(), "content", e.getContent()))
                .collect(Collectors.toList());
    }

    private ConversationSession toDto(ConversationSessionEntity entity) {
        ConversationSession dto = new ConversationSession();
        dto.setSessionId(entity.getSessionId());
        dto.setUserId(entity.getUserId());
        if (entity.getSceneType() != null) {
            dto.setSceneType(SceneType.valueOf(entity.getSceneType()));
        }
        if (entity.getContextJson() != null) {
            try {
                dto.setContext(objectMapper.readValue(entity.getContextJson(), LinkedHashMap.class));
            } catch (JsonProcessingException e) {
                log.warn("Failed to parse context JSON for session: {}", entity.getSessionId());
            }
        }
        dto.setCreatedAt(entity.getCreatedAt() != null
                ? entity.getCreatedAt().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() : 0);
        dto.setUpdatedAt(entity.getUpdatedAt() != null
                ? entity.getUpdatedAt().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() : 0);
        return dto;
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
