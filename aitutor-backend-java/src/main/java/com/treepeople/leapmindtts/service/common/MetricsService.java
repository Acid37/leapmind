package com.treepeople.leapmindtts.service.common;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.function.Supplier;

@Component
public class MetricsService {

    private final MeterRegistry meterRegistry;

    public MetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void incrementQuestionProcessed(String type, String status) {
        meterRegistry.counter("questions.processed.total", "type", type, "status", status).increment();
    }

    // 记录打断次数
    public void incrementInterruptCount() {
        meterRegistry.counter("conversation.interrupt.count").increment();
    }

    // 记录首字延迟
    public void recordTtft(long durationMs) {
        meterRegistry.timer("conversation.ttft").record(java.time.Duration.ofMillis(durationMs));
    }

    // 记录AI流错误
    public void incrementStreamError() {
        meterRegistry.counter("conversation.stream.error").increment();
    }

    // 记录连接断开
    public void incrementStreamDisconnect() {
        meterRegistry.counter("conversation.stream.disconnect").increment();
    }

    public void recordCircuitBreakerState(String name, String from, String to) {
        meterRegistry.counter("circuitbreaker.state.change", "name", name, "from", from, "to", to).increment();
    }

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

    public void recordMergePoolSize(int size) {
        meterRegistry.gauge("request.merge.pool.size", size);
    }
}
