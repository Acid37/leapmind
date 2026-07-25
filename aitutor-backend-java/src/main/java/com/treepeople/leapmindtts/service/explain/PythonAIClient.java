package com.treepeople.leapmindtts.service.explain;

import com.treepeople.leapmindtts.config.PythonAIServiceProperties;
import com.treepeople.leapmindtts.pojo.dto.AICallRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

/**
 * Python AI 微服务 HTTP 客户端
 * 负责 Java → Python 内部通信，支持流式和非流式调用
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PythonAIClient {

    private final WebClient webClient;
    private final PythonAIServiceProperties pythonProps;

    /**
     * 流式调用 Python AI 服务（SSE）
     * Python 返回的每一行 data 直接作为 Flux<String> 元素返回，
     * 由上层 Controller 包装成 SSE 推送给前端。
     *
     * @param request AI 调用请求
     * @return Flux<String> 流式响应行
     */
    public Flux<String> callStream(AICallRequest request) {
        String url = pythonProps.getBaseUrl() + pythonProps.getStreamPath();
        log.info("PythonAIClient 流式调用: url={}, module={}, scene={}", url, request.getModule(), request.getScene());

        return webClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .retrieve()
                .bodyToFlux(String.class)
                .doOnNext(chunk -> log.debug("Python SSE chunk: {}", chunk.substring(0, Math.min(100, chunk.length()))))
                .doOnError(error -> log.error("Python AI 流式调用失败: {}", error.getMessage(), error))
                .doOnComplete(() -> log.info("Python AI 流式调用完成: module={}, scene={}", request.getModule(), request.getScene()));
    }

    /**
     * 非流式调用 Python AI 服务
     *
     * @param request AI 调用请求
     * @return JSON 响应字符串
     */
    public reactor.core.publisher.Mono<String> call(AICallRequest request) {
        String url = pythonProps.getBaseUrl() + pythonProps.getGeneratePath();
        log.info("PythonAIClient 非流式调用: url={}, module={}, scene={}", url, request.getModule(), request.getScene());

        return webClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(String.class)
                .doOnSuccess(result -> log.info("Python AI 非流式调用完成: module={}", request.getModule()))
                .doOnError(error -> log.error("Python AI 非流式调用失败: {}", error.getMessage(), error));
    }
}
