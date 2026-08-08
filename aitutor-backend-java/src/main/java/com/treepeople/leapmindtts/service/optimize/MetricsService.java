
 package com.treepeople.leapmindtts.service.optimize;
 
 import io.micrometer.core.instrument.MeterRegistry;
 import io.micrometer.core.instrument.Timer;
 import lombok.RequiredArgsConstructor;
 import org.springframework.stereotype.Service;
 import reactor.core.publisher.Mono;
 
 import java.util.Arrays;
 import java.util.function.Supplier;
 import java.util.function.UnaryOperator;
 
 @Service("optimizeMetricsService")
 @RequiredArgsConstructor
 public class MetricsService {
 
     private final MeterRegistry meterRegistry;

    // region Counters
    public void incrementQuestionProcessed(String type, String status) {
        meterRegistry.counter("questions.processed.total", "type", type, "status", status).increment();
    }

    public void recordCircuitBreakerState(String name, String from, String to) {
        meterRegistry.counter("circuitbreaker.state.change", "name", name, "from", from, "to", to).increment();
    }

    public void incrementCacheStatus(String cacheName, String status) {
        meterRegistry.counter("cache.status.total", "cache", cacheName, "status", status).increment();
    }

    public void incrementFallback(String fallbackName) {
        meterRegistry.counter("fallback.total", "name", fallbackName).increment();
    }
    // endregion

    // region Timers

    /**
     * Records the duration of a synchronous operation.
     */
    public <T> T recordDuration(String metricName, String[] tags, Supplier<T> operation) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            T result = operation.get();
            sample.stop(meterRegistry.timer(metricName, tags));
            return result;
        } catch (Exception e) {
            String[] errorTags = Arrays.copyOf(tags, tags.length + 2);
            errorTags[tags.length] = "status";
            errorTags[tags.length + 1] = "error";
            sample.stop(meterRegistry.timer(metricName, errorTags));
            throw e;
        }
    }

    /**
     * Returns a reactive operator to record the duration of a Mono pipeline.
     */
    public <T> UnaryOperator<Mono<T>> recordMonoDuration(String metricName, String... tags) {
        return mono -> {
            Timer.Sample sample = Timer.start(meterRegistry);
            return mono.doOnSuccess(v -> {
                String[] successTags = Arrays.copyOf(tags, tags.length + 2);
                successTags[tags.length] = "status";
                successTags[tags.length + 1] = "success";
                sample.stop(meterRegistry.timer(metricName, successTags));
            }).doOnError(e -> {
                String[] errorTags = Arrays.copyOf(tags, tags.length + 2);
                errorTags[tags.length] = "status";
                errorTags[tags.length + 1] = "error";
                sample.stop(meterRegistry.timer(metricName, errorTags));
            });
        };
    }
    // endregion
}
