package com.treepeople.leapmindtts.service.common;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class RequestMergeService {

    // 存储处理中的请求：dedupKey -> SSE Sinks 多播
    private final Cache<String, Sinks.Many<ServerSentEvent<?>>> pendingRequests;

    // 针对非流式 (Mono) 的请求缓存池
    private final Cache<String, reactor.core.publisher.Mono<String>> pendingMonos = Caffeine.newBuilder()
        .expireAfterWrite(200, java.util.concurrent.TimeUnit.MILLISECONDS)
        .maximumSize(5000)
        .build();

    public RequestMergeService() {
        this.pendingRequests = Caffeine.newBuilder()
            .expireAfterWrite(300, TimeUnit.MILLISECONDS) // 200ms-300ms 后自动过期清除，防长时间挂起
            .maximumSize(10000)
            .removalListener((String key, Sinks.Many<ServerSentEvent<?>> value, RemovalCause cause) -> {
                if (cause == RemovalCause.EXPIRED && value != null) {
                    // Sinks 会自动处理清理，这里主要做日志
                    log.debug("请求合并缓存过期清理：key={}", key);
                }
            })
            .build();
    }

    /**
     * 尝试合并请求
     * @param dedupKey 去重键 (如 sessionId + questionHash)
     * @param fluxSupplier 首个请求执行的逻辑（提供 Flux）
     * @return 共享的 Flux 数据流
     */
    public Flux<ServerSentEvent<?>> mergeStream(String dedupKey, java.util.function.Supplier<Flux<ServerSentEvent<?>>> fluxSupplier) {
        Sinks.Many<ServerSentEvent<?>> existingSink = pendingRequests.getIfPresent(dedupKey);
        
        if (existingSink != null) {
            log.info("SSE 请求合并：命中处理中的多播流，key={}", dedupKey);
            return existingSink.asFlux();
        }

        // 首个请求，创建多播 Sink (replay(1) 可以让后来的订阅者拿到至少最近的一个事件，比如 thinking)
        Sinks.Many<ServerSentEvent<?>> newSink = Sinks.many().replay().latest();
        pendingRequests.put(dedupKey, newSink);
        log.info("SSE 请求合并：创建新的多播流，key={}", dedupKey);

        Flux<ServerSentEvent<?>> sourceFlux = fluxSupplier.get();
        
        // 订阅原始流并将数据推送到 Sink
        sourceFlux.doOnNext(newSink::tryEmitNext)
                  .doOnError(error -> {
                      newSink.tryEmitError(error);
                      pendingRequests.invalidate(dedupKey);
                  })
                  .doOnComplete(() -> {
                      newSink.tryEmitComplete();
                      pendingRequests.invalidate(dedupKey);
                  })
                  .doOnCancel(() -> {
                      // 这里处理源头被取消的情况
                      pendingRequests.invalidate(dedupKey);
                  })
                  .subscribe();

        return newSink.asFlux();
    }

    /**
     * 针对 VoiceChatService (Mono返回) 的请求合并
     */
    public reactor.core.publisher.Mono<String> tryMergeMono(String dedupKey, java.util.function.Supplier<reactor.core.publisher.Mono<String>> aiCallSupplier) {
        reactor.core.publisher.Mono<String> existingMono = pendingMonos.getIfPresent(dedupKey);
        
        if (existingMono != null) {
            log.info("触发 Mono 请求合并，拦截到 200ms 内重复点击，直接订阅现有结果: {}", dedupKey);
            return existingMono;
        }

        reactor.core.publisher.Mono<String> newMono = aiCallSupplier.get()
            .doFinally(signalType -> pendingMonos.invalidate(dedupKey))
            // 核心：使用 cache() 操作符，让后续订阅者直接拿到第一次计算的缓存结果
            .cache();

        pendingMonos.put(dedupKey, newMono);
        return newMono;
    }
}
