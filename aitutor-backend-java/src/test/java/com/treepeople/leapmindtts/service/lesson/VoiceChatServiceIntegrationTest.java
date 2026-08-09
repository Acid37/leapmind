package com.treepeople.leapmindtts.service.lesson;

import com.treepeople.leapmindtts.service.common.ContextCompressService;
import com.treepeople.leapmindtts.util.CacheKeyBuilder;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@SpringBootTest
public class VoiceChatServiceIntegrationTest {

    @Autowired
    private VoiceChatService voiceChatService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @MockBean
    private AIModelService aiModelService;

    @MockBean
    private ContextCompressService contextCompressService;

    private CircuitBreaker contextCompressCircuitBreaker;

    @BeforeEach
    void setUp() {
        stringRedisTemplate.getConnectionFactory().getConnection().flushAll();
        contextCompressCircuitBreaker = circuitBreakerRegistry.circuitBreaker("contextCompress");
        contextCompressCircuitBreaker.reset();
        when(contextCompressService.compressContext(anyString())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
    }

    @Test
    void testNormalRequestFlow() {
        String question = "你好，世界";
        String expectedAnswer = "你好，探索者！";
        String userId = "user-normal";
        when(aiModelService.getAIResponse(question)).thenReturn(Mono.just(expectedAnswer));

        String answer = voiceChatService.processVoiceChat(userId, question).block();

        assertThat(answer).isEqualTo(expectedAnswer);
        String cacheKey = CacheKeyBuilder.QUESTION_CACHE_PREFIX + CacheKeyBuilder.questionHash(question);
        String cachedValue = stringRedisTemplate.opsForValue().get(cacheKey);
        assertThat(cachedValue).isEqualTo(expectedAnswer);
    }

    @Test
    void testConcurrentRequestMerging() throws InterruptedException {
        String question = "并发问题";
        String expectedAnswer = "只回答一次";
        when(aiModelService.getAIResponse(question)).thenReturn(Mono.delay(Duration.ofMillis(200)).thenReturn(expectedAnswer));

        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                String answer = voiceChatService.processVoiceChat("user-concurrent", question).block();
                assertThat(answer).isEqualTo(expectedAnswer);
                latch.countDown();
            });
        }

        latch.await(2, TimeUnit.SECONDS);
        executor.shutdown();

        Mockito.verify(aiModelService, times(1)).getAIResponse(question);
    }

    @Test
    void testCachePenetrationProtection() {
        String weirdQuestion = "一个导致异常的问题";
        String userId = "user-penetration";
        when(aiModelService.getAIResponse(weirdQuestion)).thenReturn(Mono.just(""));

        voiceChatService.processVoiceChat(userId, weirdQuestion).block();

        String cacheKey = CacheKeyBuilder.QUESTION_CACHE_PREFIX + CacheKeyBuilder.questionHash(weirdQuestion);
        String cachedValue = stringRedisTemplate.opsForValue().get(cacheKey);
        assertThat(cachedValue).isEqualTo("__NULL__");

        voiceChatService.processVoiceChat(userId, weirdQuestion).block();

        Mockito.verify(aiModelService, times(1)).getAIResponse(weirdQuestion);
    }

    @Test
    void testCompressionFallback() {
        String longQuestion = "这是一个非常非常长的问题，需要被压缩...".repeat(200);
        String expectedAnswer = "答案很简单";
        when(contextCompressService.compressContext(longQuestion)).thenReturn(Mono.error(new RuntimeException("Timeout!")));
        when(aiModelService.getAIResponse(longQuestion)).thenReturn(Mono.just(expectedAnswer));

        String answer = voiceChatService.processVoiceChat("user-fallback", longQuestion).block();

        assertThat(answer).isEqualTo(expectedAnswer);
        Mockito.verify(aiModelService, times(1)).getAIResponse(longQuestion);
    }

    @Test
    void testCircuitBreakerStateTransition() {
        String question = "测试熔断";
        when(contextCompressService.compressContext(question)).thenReturn(Mono.error(new RuntimeException("FAIL")));

        for (int i = 0; i < 10; i++) {
            try {
                voiceChatService.processVoiceChat("user-cb", question).block();
            } catch (Exception e) {
                // ignore
            }
        }

        assertThat(contextCompressCircuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        await().atMost(11, TimeUnit.SECONDS).until(() -> contextCompressCircuitBreaker.getState() == CircuitBreaker.State.HALF_OPEN);

        when(contextCompressService.compressContext(question)).thenReturn(Mono.just(question));
        when(aiModelService.getAIResponse(question)).thenReturn(Mono.just("SUCCESS"));

        voiceChatService.processVoiceChat("user-cb", question).block();
        assertThat(contextCompressCircuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }
}
