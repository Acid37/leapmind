 package com.treepeople.leapmindtts.service.lesson;
 
 import com.treepeople.leapmindtts.service.optimize.ContextCompressService;
 import com.treepeople.leapmindtts.service.optimize.MetricsService;
 import com.treepeople.leapmindtts.service.optimize.RedisCacheService;
 import com.treepeople.leapmindtts.service.optimize.RequestMergeService;
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
      * 2. If miss, use Request Merging
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
 
         // 2. If miss, use Request Merging with standardized key
         String userId = getCurrentUserId();
         String dedupKey = CacheKeyBuilder.dedupKey(userId, question);
         RequestMergeService.RequestMergeResult mergeResult = requestMergeService.tryMerge(dedupKey);
 
         if (!mergeResult.isFirst()) {
             return Mono.fromFuture(mergeResult.getFuture())
                     .doOnSuccess(result -> {
                         log.debug("合并的请求 key={} 已成功获取结果", dedupKey);
                         metricsService.incrementQuestionProcessed("merged", "success");
                     })
                     .doOnError(error -> {
                         log.warn("合并的请求 key={} 获取结果失败", dedupKey, error);
                         metricsService.incrementQuestionProcessed("merged", "error");
                     });
         }
 
         // 3. If first request, compress context & call AI model
         log.debug("作为第一个请求 key={} 执行核心业务逻辑", dedupKey);
         return contextCompressService.compressContext(question)
                 .flatMap(compressedQuestion -> {
                     log.debug("Using question for AI (compressed: {}): {}", question.length() != compressedQuestion.length(), compressedQuestion);
                     return aiModelService.getAIResponse(compressedQuestion)
                             .transform(metricsService.recordMonoDuration("ai.model.response", "model", "default"));
                 })
                 .doOnSuccess(answer -> {
                     log.info("AI回答: {}", answer);
                     metricsService.incrementQuestionProcessed("full", "success");
                     // 4. Cache the result
                     if (StringUtils.hasText(answer)) {
                         redisCacheService.set(cacheKey, answer, Duration.ofHours(1));
                     } else {
                         redisCacheService.setNullPlaceholder(cacheKey);
                     }
                     requestMergeService.completeRequest(dedupKey, answer);
                 })
                 .doOnError(error -> {
                     log.error("处理语音对话失败，会话ID: {}, 问题: {}", courseId, question, error);
                     metricsService.incrementQuestionProcessed("full", "error");
                     // Also cache a null on error to prevent hammering a failing request
                     redisCacheService.setNullPlaceholder(cacheKey);
                     requestMergeService.failRequest(dedupKey, error);
                 });
     }
 
     private String getCurrentUserId() {
         Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
         if (authentication != null && authentication.isAuthenticated() && !"anonymousUser".equals(authentication.getName())) {
             return authentication.getName();
         }
         return "anonymous-user";
     }
 
     // Test method remains unchanged
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