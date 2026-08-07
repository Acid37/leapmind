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
import com.treepeople.leapmindtts.pojo.entity.EventCollection;
// 【引入缺失的优化组件与工具类】
import com.treepeople.leapmindtts.service.EventCollectionService;
import com.treepeople.leapmindtts.service.common.RedisCacheService;
import com.treepeople.leapmindtts.service.common.MetricsService;
import com.treepeople.leapmindtts.service.common.ContextCompressService;
import com.treepeople.leapmindtts.util.CacheKeyBuilder;

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
import java.time.LocalDateTime;
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

    // 【新增】注入优化组件服务
    private final RedisCacheService redisCacheService;
    private final MetricsService metricsService;
    private final io.micrometer.core.instrument.MeterRegistry meterRegistry;
    private final com.treepeople.leapmindtts.service.common.RequestMergeService requestMergeService;
    private final ContextCompressService contextCompressService;

    private final ConcurrentHashMap<String, BaseSubscriber<AIModelService.AiChunk>> activeSubscribers = new ConcurrentHashMap<>();

    public static final String NULL_PLACEHOLDER = "__NULL__";

    // 【修改】在构造函数中补充 RedisCacheService 和 MetricsService 的注入
    public ConversationService(AIModelService aiModelService,
                               AiTeacherBaiduAsrService aiTeacherBaiduAsrService,
                               WebClient.Builder webClientBuilder,
                               StringRedisTemplate stringRedisTemplate,
                               ConversationSessionMapper sessionMapper,
                               ConversationMessageMapper messageMapper,
                               ConversationProperties properties,
                               ObjectMapper objectMapper,
                               RedisCacheService redisCacheService,
                               MetricsService metricsService,
                               io.micrometer.core.instrument.MeterRegistry meterRegistry,
                               com.treepeople.leapmindtts.service.common.RequestMergeService requestMergeService,
                               ContextCompressService contextCompressService) {
        this.aiModelService = aiModelService;
        this.aiTeacherBaiduAsrService = aiTeacherBaiduAsrService;
        this.webClient = webClientBuilder.build();
        this.stringRedisTemplate = stringRedisTemplate;
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.redisCacheService = redisCacheService;
        this.metricsService = metricsService;
        this.meterRegistry = meterRegistry;
        this.requestMergeService = requestMergeService;
        this.contextCompressService = contextCompressService;
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
    private Flux<ServerSentEvent<?>> tryServeOptimized(ConversationRequest req, String sessionId, ConversationSession session, String callId) {
        // 【修改】修复逻辑漏洞：坚决不加 sessionId，但必须带上 userId 防止跨用户串话！
        String cacheKey = CacheKeyBuilder.dedupKey(String.valueOf(req.getUserId()), req.getQuestion());
        String cachedAnswer = redisCacheService.get(cacheKey);

        if (cachedAnswer != null && !NULL_PLACEHOLDER.equals(cachedAnswer)) {
            log.info("Redis cache hit for optimized stream, sessionId: {}", sessionId);
            metricsService.incrementQuestionProcessed("cache", "success");
            
            // 组装静态数据为 SSE 流
            return Flux.concat(
                Flux.just(sseEvent("message", String.format("{\"type\":\"thinking\",\"content\":\"\",\"sessionId\":\"%s\"}", sessionId))),
                Flux.fromArray(cachedAnswer.split("(?<=[。！？!?])|(?=[。！？!?])"))
                    .filter(s -> !s.isEmpty())
                    .index()
                    .delayElements(Duration.ofMillis(50)) // 模拟打字机效果
                    .map(tuple -> sseEvent("message", String.format("{\"type\":\"content\",\"chunk\":\"%s\",\"index\":%d}", escapeJson(tuple.getT2()), tuple.getT1()))),
                Flux.just(sseEvent("message", String.format("{\"type\":\"done\",\"callId\":\"%s\",\"sessionId\":\"%s\",\"tokenUsage\":{\"input\":0,\"output\":0}}", callId, sessionId)))
            );
        }
        return null;
    }

    private void cacheAnswerIfNeeded(ConversationRequest req, String answer, boolean isFollowUp) {
        try {
            if (isFollowUp) {
                return;
            }
            String cacheKey = CacheKeyBuilder.dedupKey(String.valueOf(req.getUserId()), req.getQuestion());
            if (answer != null && !answer.isEmpty()) {
                redisCacheService.set(cacheKey, answer, Duration.ofHours(1));
                log.info("Cached AI answer to Redis: key={}, ttl=1h", cacheKey);
            } else {
                redisCacheService.setNullPlaceholder(cacheKey);
                log.info("Cached null placeholder to Redis: key={}", cacheKey);
            }
        } catch (Exception e) {
            log.warn("Failed to cache answer: {}", e.getMessage());
        }
    }

    private void publishAskDoubtEvent(String sessionId, ConversationRequest req, boolean isFollowUp) {
        try {
            if (req.getUserId() == null || req.getQuestion() == null || req.getQuestion().isEmpty()) {
                return;
            }
            String topic = truncate(req.getQuestion(), 120);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("topic", topic);
            data.put("confusionTag", "concept_unclear");
            data.put("isFollowUp", isFollowUp);
            if (sessionId != null) {
                data.put("sessionId", sessionId);
            }
            EventCollection event = EventCollection.builder()
                    .module("M7")
                    .eventType("ask_doubt")
                    .userId(req.getUserId())
                    .eventData(objectMapper.writeValueAsString(data))
                    .eventTime(LocalDateTime.now())
                    .processed(0)
                    .build();
            eventCollectionService.collectEvent(event);
            log.info("Published M7 ask_doubt event for userId={}, topicLength={}, isFollowUp={}",
                    req.getUserId(), topic.codePointCount(0, topic.length()), isFollowUp);
        } catch (Exception e) {
            log.warn("Failed to publish M7 ask_doubt event: {}", e.getMessage());
        }
    }

    private String truncate(String s, int max) {
        if (s.codePointCount(0, s.length()) <= max) return s;
        return s.substring(0, s.offsetByCodePoints(0, max));
    }

    private String buildScenePrompt(SceneType sceneType, Map<String, Object> context) {
        String scene = switch (sceneType == null ? SceneType.general_qa : sceneType) {
            case doing_exercise -> "你是一位耐心细致的答题老师。学生在做题时向你提问，请结合当前题目知识点讲解解题思路，步骤清晰，语气亲切。";
            case explaining -> "你是一位善于讲解的老师。学生在学习过程中向你追问，请结合正在讲解的内容深入浅出地解答，适当举例。";
            case teaching -> "你是一位亲切生动的授课老师。请用通俗易懂的语言向学生讲课，把知识点讲清楚，适当与学生互动。";
            case lesson_prep -> "你是一位备课助手。请帮助老师准备课程内容，提供结构化、实用的备课建议与教学设计。";
            default -> "你是一位聪明且体贴的老师，请用简洁友好的方式回答问题。";
        };
        if (context == null || context.isEmpty()) {
            return scene;
        }
        StringBuilder sb = new StringBuilder(scene);
        sb.append("\n【当前场景上下文】");
        context.forEach((k, v) -> {
            if (v != null) {
                sb.append("\n- ").append(k).append(": ").append(v);
            }
        });
        return sb.toString();
    }

    private void persistReplayedPair(String sessionId, ConversationSession session, ConversationRequest req, String callId) {
        try {
            if (req.getQuestion() == null || req.getQuestion().isEmpty() || session.getMessages() == null) {
                return;
            }
            String cachedAnswer = redisCacheService.get(CacheKeyBuilder.dedupKey(String.valueOf(req.getUserId()), req.getQuestion()));
            session.getMessages().add(Map.of("role", "user", "content", req.getQuestion()));
            saveMessageToDb(sessionId, "user", req.getQuestion(), 0, 0, callId);
            if (cachedAnswer != null && !NULL_PLACEHOLDER.equals(cachedAnswer)) {
                session.getMessages().add(Map.of("role", "assistant", "content", cachedAnswer));
                saveMessageToDb(sessionId, "assistant", cachedAnswer, 0, 0, callId);
            }
            int msgCount = messageMapper.selectBySessionId(sessionId).size();
            sessionMapper.updateMessageCount(sessionId, msgCount);
            saveSessionToRedis(session);
        } catch (Exception e) {
            log.warn("Failed to persist replayed conversation pair: {}", e.getMessage());
        }
    }

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
        final long streamStartNanos = System.nanoTime();
        final boolean isFollowUp = session.getMessages() != null
                && session.getMessages().stream().anyMatch(m -> "user".equals(m.get("role")));

        // 【M7 钩子切入点】尝试使用优化组件直接返回缓存（仅首问走缓存；追问依赖会话上下文，不能命中全局缓存）
        Flux<ServerSentEvent<?>> optimizedStream = isFollowUp ? null : tryServeOptimized(req, sessionId, session, callId);
        if (optimizedStream != null) {
            persistReplayedPair(sessionId, session, req, callId);
            publishAskDoubtEvent(sessionId, req, isFollowUp);
            return optimizedStream;
        }

        // 使用 RequestMergeService 包装实际的流式调用逻辑
        String userId = req.getUserId() != null ? String.valueOf(req.getUserId()) : "anonymous";
        String dedupKey = CacheKeyBuilder.dedupKey(userId, req.getQuestion());

        return requestMergeService.mergeStream(dedupKey, () -> {
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
        AtomicBoolean firstChunk = new AtomicBoolean(false);
        AtomicBoolean streamFinished = new AtomicBoolean(false);

        return Flux.create(fluxSink -> {
            // 1. 先发 thinking 事件（带上 sessionId，打断用）
            fluxSink.next(sseEvent("message",
                String.format("{\"type\":\"thinking\",\"content\":\"\",\"sessionId\":\"%s\"}", sessionId)));

            // 2. 订阅 AI 流
            BaseSubscriber<AIModelService.AiChunk> subscriber = new BaseSubscriber<>() {
                @Override
                protected void hookOnNext(AIModelService.AiChunk chunk) {
                    fullAnswer.append(chunk.getChunk());

                    if (firstChunk.compareAndSet(false, true)) {
                        meterRegistry.timer("conversation.ttft")
                                .record(Duration.ofNanos(System.nanoTime() - streamStartNanos));
                    }

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

                        // 【新增】将高频问答存入 Redis 缓存 (TTL 1h)
                        String cacheKey = CacheKeyBuilder.dedupKey(String.valueOf(session.getUserId()), req.getQuestion());
                        redisCacheService.set(cacheKey, answer, Duration.ofHours(1));

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
                    streamFinished.set(true);
                    meterRegistry.counter("conversation.stream.error").increment();
                    metricsService.incrementQuestionProcessed("full", "error");
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
                    cacheAnswerIfNeeded(req, answer, isFollowUp);
                    publishAskDoubtEvent(sessionId, req, isFollowUp);
                    metricsService.incrementQuestionProcessed("full", "success");
                    streamFinished.set(true);
                    fluxSink.next(sseEvent("message",
                        String.format("{\"type\":\"done\",\"callId\":\"%s\",\"sessionId\":\"%s\",\"tokenUsage\":{\"input\":0,\"output\":0}}",
                            callId, sessionId)));
                    fluxSink.complete();
                }
            };

            List<Map<String, String>> allHistory = session.getMessages();
            reactor.core.publisher.Mono<List<Map<String, String>>> historyMono;
            if (allHistory.size() > properties.getMessageLimit()) {
                try {
                    String contextText = objectMapper.writeValueAsString(allHistory);
                    historyMono = contextCompressService.compressContext(contextText)
                            .map(compressedContext -> {
                                List<Map<String, String>> newHistory = new ArrayList<>();
                                newHistory.add(Map.of("role", "system", "content", "前情提要：" + compressedContext));
                                // 保留最后一条用户消息，确保当前问题不丢失
                                newHistory.add(allHistory.get(allHistory.size() - 1));
                                return newHistory;
                            })
                            .onErrorResume(e -> {
                                log.error("Context compression failed, fallback to truncation: {}", e.getMessage());
                                // 降级策略：截断，但确保包含最后一条用户消息
                                List<Map<String, String>> fallbackHistory = new ArrayList<>(allHistory.subList(allHistory.size() - properties.getMessageLimit(), allHistory.size()));
                                if (!fallbackHistory.contains(allHistory.get(allHistory.size() - 1))) {
                                    fallbackHistory.add(allHistory.get(allHistory.size() - 1));
                                }
                                return reactor.core.publisher.Mono.just(fallbackHistory);
                            });
                } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                    log.error("Failed to serialize history for compression", e);
                    List<Map<String, String>> fallbackHistory = new ArrayList<>(allHistory.subList(allHistory.size() - properties.getMessageLimit(), allHistory.size()));
                    historyMono = reactor.core.publisher.Mono.just(fallbackHistory);
                }
            } else {
                historyMono = reactor.core.publisher.Mono.just(allHistory);
            }

            historyMono.subscribe(history -> {
                aiModelService.streamAIResponse(history, req.getInputType(), req.getAttachmentUrls())
                    .subscribe(subscriber);
                activeSubscribers.put(sessionId, subscriber);
            });

            fluxSink.onCancel(() -> {
                log.info("Flux cancelled for session: {}", sessionId);
                if (streamFinished.compareAndSet(false, true)) {
                    meterRegistry.counter("conversation.stream.disconnect").increment();
                }
                cleanup(sessionId);
            });
            fluxSink.onDispose(() -> {
                log.info("Flux disposed for session: {}", sessionId);
                if (streamFinished.compareAndSet(false, true)) {
                    meterRegistry.counter("conversation.stream.disconnect").increment();
                }
                cleanup(sessionId);
            });
        });
        });
    }

    public void interrupt(String sessionId) {
        log.info("Interrupting session: {}", sessionId);
        meterRegistry.counter("conversation.interrupt.count").increment();

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
