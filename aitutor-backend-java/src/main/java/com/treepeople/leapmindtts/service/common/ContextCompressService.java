package com.treepeople.leapmindtts.service.common;

import com.treepeople.leapmindtts.pojo.dto.python.CompressRequest;
import com.treepeople.leapmindtts.pojo.dto.python.CompressResponse;
import com.treepeople.leapmindtts.pojo.properties.ContextCompressProperties;
import com.treepeople.leapmindtts.pojo.properties.PythonApiProperties;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;

@Slf4j
@Service
public class ContextCompressService {

    private final WebClient webClient;
    private final PythonApiProperties properties;
    private final ContextCompressProperties compressProperties;
    private final MeterRegistry meterRegistry;

    public ContextCompressService(WebClient.Builder webClientBuilder, PythonApiProperties properties, ContextCompressProperties compressProperties, MeterRegistry meterRegistry) {
        this.properties = properties;
        this.compressProperties = compressProperties;
        this.meterRegistry = meterRegistry;

        ConnectionProvider provider = ConnectionProvider.builder("custom")
                .maxConnections(50)
                .pendingAcquireMaxCount(-1)
                .build();

        HttpClient httpClient = HttpClient.create(provider)
                .responseTimeout(Duration.ofSeconds(10))
                .option(io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS, 2000);

        this.webClient = webClientBuilder
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .baseUrl(properties.getBaseUrl())
                .build();
    }

    @CircuitBreaker(name = "contextCompress", fallbackMethod = "compressContextFallback")
    public Mono<String> compressContext(String contextText) {
        int estimatedTokens = (int) (contextText.length() * 0.5); // 估算 token
        if (estimatedTokens < compressProperties.getTokenThreshold()) {
            return Mono.just(contextText);
        }

        CompressRequest request = new CompressRequest(contextText, compressProperties.getMaxCompressedTokens());
        Timer.Sample sample = Timer.start(meterRegistry);

        return webClient.post()
                .uri(properties.getCompressContextUri())
                .bodyValue(request)
                .retrieve()
                .bodyToMono(CompressResponse.class)
                .map(CompressResponse::getCompressedText)
                .doOnSuccess(r -> sample.stop(meterRegistry.timer("external.api.duration", "api", "compress", "status", "success")))
                .doOnError(e -> sample.stop(meterRegistry.timer("external.api.duration", "api", "compress", "status", "error")))
                .timeout(Duration.ofSeconds(10));
    }

    /**
     * 上下文压缩降级逻辑：出现任何异常或熔断，直接抛给下游原文本，不阻碍正常提问
     */
    public Mono<String> compressContextFallback(String contextText, Throwable t) {
        log.warn("上下文压缩调用触发降级：使用原始上下文，原因：{}", t.getMessage());
        meterRegistry.counter("compress.fallback.count").increment();
        return Mono.just(contextText);
    }
}
