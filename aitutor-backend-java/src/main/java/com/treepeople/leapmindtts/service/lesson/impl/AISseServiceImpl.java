package com.treepeople.leapmindtts.service.lesson.impl;

import com.treepeople.leapmindtts.service.lesson.AISseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.util.Map;

/**
 * AI SSE 流式讲课生成实现（许沣睿）
 * 通过 WebClient 代理转发到 AI 服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AISseServiceImpl implements AISseService {

    private final WebClient webClient;

    @Value("${ai.teaching.service-url:http://localhost:8000}")
    private String aiTeachingServiceUrl;

    @Override
    public String generateTeachingContent(String courseId, String sourceText, Map<String, Object> userProfile) {
        Map<String, Object> requestBody = Map.of(
                "course_id", courseId,
                "source_text", sourceText != null ? sourceText : "",
                "user_profile", userProfile != null ? userProfile : Map.of()
        );

        try {
            String result = webClient.post()
                    .uri(aiTeachingServiceUrl + "/api/ai/generate-teaching")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            log.info("AI讲课内容生成完成: courseId={}, contentLength={}",
                    courseId, result != null ? result.length() : 0);
            return result;
        } catch (Exception e) {
            log.error("AI讲课内容生成失败: courseId={}", courseId, e);
            throw new RuntimeException("AI生成失败: " + e.getMessage(), e);
        }
    }

    @Override
    public Flux<String> streamGenerateTeachingContent(String courseId, String sourceText,
                                                      Map<String, Object> userProfile) {
        Map<String, Object> requestBody = Map.of(
                "course_id", courseId,
                "source_text", sourceText != null ? sourceText : "",
                "user_profile", userProfile != null ? userProfile : Map.of()
        );

        return webClient.post()
                .uri(aiTeachingServiceUrl + "/api/ai/stream-generate-teaching")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .retrieve()
                .bodyToFlux(String.class)
                .doOnNext(chunk -> log.debug("SSE chunk received: {} chars", chunk.length()))
                .doOnError(error -> log.error("SSE流式传输出错: courseId={}", courseId, error))
                .doOnComplete(() -> log.info("SSE流式传输完成: courseId={}", courseId));
    }
}
