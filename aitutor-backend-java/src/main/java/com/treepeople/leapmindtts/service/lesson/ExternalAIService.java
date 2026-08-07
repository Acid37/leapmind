package com.treepeople.leapmindtts.service.lesson;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ExternalAIService {

    private final FallbackAnswerProvider fallbackProvider;

    @CircuitBreaker(name = "aiService", fallbackMethod = "fallback")
    public String getAnswer(String question) {
        if ("trigger_error".equals(question)) {
            throw new RuntimeException("模拟 AI 接口超时或宕机");
        }
        return "AI Answer";
    }

    public String fallback(String question, Throwable t) {
        return fallbackProvider.getFallbackAnswer(FallbackScene.AI_ERROR);
    }
}
