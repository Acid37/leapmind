 package com.treepeople.leapmindtts.service.lesson;

import com.treepeople.leapmindtts.service.common.ContextCompressService;
import com.treepeople.leapmindtts.service.common.MetricsService;
import com.treepeople.leapmindtts.service.common.RedisCacheService;
import com.treepeople.leapmindtts.service.common.RequestMergeService;
import com.treepeople.leapmindtts.util.CacheKeyBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * @ Package：com.treepeople.leapmindtts.service
 * @ Project：leapmind-tts - 语音对话
 * @ Description: 语音对话服务
 * @ Date：2025/8/8
 */

@Service
@Slf4j
@RequiredArgsConstructor
public class VoiceChatService {

    private final AIModelService aiModelService;
    private final TextToSpeechService ttsService;
    private final RequestMergeService requestMergeService;
    private final RedisCacheService redisCacheService;
    private final ContextCompressService contextCompressService;
    private final MetricsService metricsService;

    /**
     * 处理语音对话
     * Pipeline:
     * 1. Check Redis Cache
     * 2. If miss, use Request Merging (Reactive Mono)
     * 3. If first request, compress context & call AI model
     * 4. Cache the result in Redis
     */
    public Mono<String> processVoiceChat(String courseId, String question) {
        log.info("语音对话服务：处理对话请求，会话ID: {}, 问题: {}", courseId, question);

        // 1. Check Redis Cache using standardized key builder
        String cacheKey = CacheKeyBuilder.QUESTION_CACHE_PREFIX + CacheKeyBuilder.questionHash(question);
        String cachedAnswer = redisCacheService.get(cacheKey);
        
        if (cachedAnswer != null) {
            log.info("Redis cache hit for question hash: {}", cacheKey);
            metricsService.incrementQuestionProcessed("cache", "success");
            return Mono.just(cachedAnswer);
        }

        // 2. Prepare Deduplication Key
        String userId = getCurrentUserId();
        String dedupKey = CacheKeyBuilder.dedupKey(userId, question);

        // 3. 使用新的响应式合并逻辑 (tryMergeMono) 包装核心业务
        return requestMergeService.tryMergeMono(dedupKey, () -> {
            log.debug("作为第一个请求 key={} 执行核心业务逻辑", dedupKey);
            
            return contextCompressService.compressContext(question)
                .flatMap(compressedQuestion -> {
                    log.debug("Using question for AI (compressed: {}): {}", question.length() != compressedQuestion.length(), compressedQuestion);
                    return aiModelService.getAIResponse(compressedQuestion);
                })
                .doOnSuccess(answer -> {
                    log.info("AI回答: {}", answer);
                    metricsService.incrementQuestionProcessed("full", "success");
                    
                    // 4. 回写 Redis 缓存
                    if (StringUtils.hasText(answer)) {
                        redisCacheService.set(cacheKey, answer, Duration.ofHours(1));
                    } else {
                        redisCacheService.setNullPlaceholder(cacheKey);
                    }
                })
                .doOnError(error -> {
                    log.error("处理语音对话失败，会话ID: {}, 问题: {}", courseId, question, error);
                    metricsService.incrementQuestionProcessed("full", "error");
                    // 出现异常时写入空值防止缓存击穿
                    redisCacheService.setNullPlaceholder(cacheKey);
                });
        });
    }

    private String getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() && !"anonymousUser".equals(authentication.getName())) {
            return authentication.getName();
        }
        return "anonymous-user";
    }

    public Mono<String> processVoiceChatNoPrompt(String courseId, String question) {
        log.info("语音对话服务：处理对话请求，会话ID: {}, 问题: {}", courseId, question);

        return aiModelService.getAIResponseNoPrompt(question)
                .doOnNext(answer -> log.info("AI回答: {}", answer))
                .doOnError(error -> log.error("处理语音对话失败，会话ID: {}, 问题: {}", courseId, question, error));
    }

    public Mono<byte[]> synthesizeVoiceAudio(String text) {
        log.info("语音对话服务：合成语音音频，文本长度: {} 字符", text.length());

        return ttsService.synthesizeSpeech(text)
                .doOnNext(audioData -> log.info("语音合成完成，音频大小: {} bytes", audioData.length))
                .doOnError(error -> log.error("语音合成失败，文本: {}", text, error));
    }
}
