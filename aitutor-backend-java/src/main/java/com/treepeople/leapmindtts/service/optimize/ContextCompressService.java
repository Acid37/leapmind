package com.treepeople.leapmindtts.service.optimize;

import com.treepeople.leapmindtts.config.ContextCompressProperties;
import com.treepeople.leapmindtts.config.PythonApiProperties;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import java.time.Duration;

@Service("optimizeContextCompressService")
@Slf4j
public class ContextCompressService {

    @Autowired
    @Qualifier("contextCompressWebClient")
    private WebClient webClient;
    
    @Autowired
    private PythonApiProperties properties;

    @CircuitBreaker(name = "compressService", fallbackMethod = "fallbackCompress")
    public Mono<String> compressContext(String originalContext) {
        return webClient.post()
                .uri(properties.getCompressContextUri())
                .header("Content-Type", "application/json")
                .bodyValue("{\"text\": \"" + originalContext + "\"}")
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofMillis(3000))
                .onErrorResume(e -> fallbackCompress(originalContext, e));
    }

    public Mono<String> fallbackCompress(String originalContext, Throwable t) {
        log.warn(">>> 触发降级兜底！原因: {} <<<", t.getMessage());
        return Mono.just(originalContext);
    }
}
