package com.treepeople.leapmindtts.controller.explain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.treepeople.leapmindtts.pojo.dto.GenerateExplainRequest;
import com.treepeople.leapmindtts.pojo.dto.PhotoQARequest;
import com.treepeople.leapmindtts.pojo.result.ApiResponse;
import com.treepeople.leapmindtts.service.explain.ExplainService;
import com.treepeople.leapmindtts.service.explain.InterruptRegistry;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.Map;

/**
 * 答疑/讲题 SSE 流式控制器
 *
 * 核心链路：前端 ←─SSE── Java ←─HTTP Stream── Python AI
 * - 透传模式：Python SSE → Java 直接透传 → 前端
 * - 打断链路：前端 POST /interrupt → Java cancel 订阅 → Python 停止拉流
 */
@Slf4j
@RestController
@RequestMapping("/api/explain")
@RequiredArgsConstructor
public class ExplainController {

    private final ExplainService explainService;
    private final InterruptRegistry interruptRegistry;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 拍照答疑 SSE 流式接口
     *
     * 前端调用方式：
     * const eventSource = new EventSource('/api/explain/photo-qa?generateId=xxx');
     * 或 POST + fetch 手动解析 SSE
     */
    @PostMapping(value = "/photo-qa", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> photoQA(@RequestBody @Valid PhotoQARequest request) {
        String generateId = explainService.generateId();
        log.info("拍照答疑 SSE 开始: generateId={}, userId={}", generateId, request.getUserId());

        return createInterruptibleStream(
                generateId,
                explainService.photoQA(request)
        );
    }

    /**
     * 讲题生成 SSE 流式接口
     */
    @PostMapping(value = "/generate", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> generateExplain(@RequestBody @Valid GenerateExplainRequest request) {
        String generateId = explainService.generateId();
        log.info("讲题生成 SSE 开始: generateId={}, userId={}", generateId, request.getUserId());

        return createInterruptibleStream(
                generateId,
                explainService.generateExplain(request)
        );
    }

    /**
     * 打断当前生成
     */
    @PostMapping("/interrupt")
    public ResponseEntity<ApiResponse<String>> interrupt(@RequestParam String generateId) {
        boolean interrupted = interruptRegistry.interrupt(generateId);
        if (interrupted) {
            return ResponseEntity.ok(ApiResponse.success("interrupted", "已打断"));
        }
        return ResponseEntity.ok(ApiResponse.error(404, "会话不存在或已结束"));
    }

    /**
     * 创建可打断的流式管道
     *
     * 将 AI 流包装为可被 InterruptRegistry 管理的 Flux，
     * 在流开始/结束/异常时自动注册/注销。
     */
    private Flux<String> createInterruptibleStream(String generateId, Flux<String> aiStream) {
        return Flux.<String>create(sink -> {
            // 首事件：发送 generateId 供前端打断使用
            sink.next(toJson(Map.of("type", "start", "generateId", generateId)));

            // 订阅 AI 流
            var disposable = aiStream
                    .doOnNext(data -> {
                        // 透传：Python 返回什么就发什么（含 Python 自己发的 done 事件）
                        sink.next(data);
                    })
                    .doOnComplete(() -> {
                        // Python 流结束，Java 不再额外发 done（Python 已发）
                        sink.complete();
                        interruptRegistry.unregister(generateId);
                    })
                    .doOnError(error -> {
                        log.error("AI 流异常: generateId={}, error={}", generateId, error.getMessage());
                        sink.next(toJson(Map.of(
                                "type", "error",
                                "message", "生成失败：" + error.getMessage()
                        )));
                        sink.complete();
                        interruptRegistry.unregister(generateId);
                    })
                    .subscribe();

            // 注册到打断注册中心
            interruptRegistry.register(generateId, disposable, sink);

            // 超时保护：120 秒无数据自动结束
            sink.onDispose(() -> {
                disposable.dispose();
                interruptRegistry.unregister(generateId);
            });

        }).take(Duration.ofSeconds(120));  // 最大 120 秒超时
    }

    /** 安全 JSON 序列化，避免手拼注入 */
    private String toJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            log.error("JSON 序列化失败", e);
            return "{\"type\":\"error\",\"message\":\"内部错误\"}";
        }
    }
}
