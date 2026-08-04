package com.treepeople.leapmindtts.service.lesson;

import com.treepeople.leapmindtts.config.TextPolishingProperties;
import com.treepeople.leapmindtts.pojo.dto.PolishingMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @ Package：com.treepeople.leapmindtts.service
 * @ Project：leapmind-tts
 * @ Description: 文本润色服务，负责调用通义千问API对文本进行润色处理
 * @ Date：2025/7/29
 */
@Service
@Slf4j
public class TextPolishingService {

    private final AIModelService aiModelService;
    private final TextPolishingProperties textPolishingProperties;

    // 性能统计指标
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong successfulRequests = new AtomicLong(0);
    private final AtomicLong failedRequests = new AtomicLong(0);
    private final AtomicLong timeoutRequests = new AtomicLong(0);
    private final AtomicLong fallbackRequests = new AtomicLong(0);
    private final AtomicLong totalProcessingTime = new AtomicLong(0);
    private final AtomicInteger totalOriginalLength = new AtomicInteger(0);
    private final AtomicInteger totalPolishedLength = new AtomicInteger(0);

    // 日志格式化器
    private static final DateTimeFormatter LOG_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    @Autowired
    public TextPolishingService(AIModelService aiModelService, TextPolishingProperties textPolishingProperties) {
        this.aiModelService = aiModelService;
        this.textPolishingProperties = textPolishingProperties;
        log.info("TextPolishingService 初始化完成 - enabled: {}, timeout: {}s, maxRetries: {}",
                textPolishingProperties.isEnabled(),
                textPolishingProperties.getTimeout(),
                textPolishingProperties.getMaxRetries());
    }

    /**
     * 主要润色方法
     * 调用 AIModelService 进行文本润色
     *
     * @param originalText 原始文本
     * @return 润色后的文本
     */
    public Mono<String> polishText(String originalText) {
        String requestId = UUID.randomUUID().toString();
        long requestStartTime = System.currentTimeMillis();

        // 记录请求开始
        logPolishingStart(requestId, originalText, "polishText");
        totalRequests.incrementAndGet();

        if (!textPolishingProperties.isEnabled()) {
            log.debug("[POLISHING] requestId={} | action=DISABLED | status=SKIPPED", requestId);
            return Mono.just(originalText);
        }

        if (originalText == null || originalText.trim().isEmpty()) {
            log.warn("[POLISHING] requestId={} | action=VALIDATION | status=EMPTY_TEXT | originalText={}",
                    requestId, originalText);
            return Mono.just(originalText != null ? originalText : "");
        }

        return aiModelService.polishText(originalText)
                .doOnSuccess(polishedText -> {
                    long duration = System.currentTimeMillis() - requestStartTime;

                    // 记录成功事件和指标
                    logPolishingSuccess(requestId, originalText, polishedText, duration);
                    recordSuccessMetrics(originalText, polishedText, duration, false, requestId);
                })
                .doOnError(error -> {
                    long duration = System.currentTimeMillis() - requestStartTime;

                    // 记录失败事件
                    logPolishingError(requestId, originalText, error, duration);
                    recordFailureMetrics(originalText, duration, requestId);
                });
    }


    /**
     * 带降级机制的润色方法
     * 实现完整的错误处理和降级策略
     *
     * @param originalText 原始文本
     * @return 润色后的文本，失败时返回原始文本
     */
    public Mono<String> polishTextWithFallback(String originalText) {
        if (!textPolishingProperties.isEnabled()) {
            log.debug("文本润色功能已禁用，返回原始文本");
            return Mono.just(originalText);
        }

        if (!textPolishingProperties.getFallback().isEnabled()) {
            // 如果降级机制被禁用，直接调用普通的润色方法
            return polishText(originalText);
        }

        if (originalText == null || originalText.trim().isEmpty()) {
            log.warn("原始文本为空，返回原文");
            return Mono.just(originalText != null ? originalText : "");
        }

        String requestId = UUID.randomUUID().toString();
        log.info("开始带降级的文本润色: requestId={}, originalLength={}", requestId, originalText.length());

        // 文本长度验证和截断逻辑
        String processedText = validateAndTruncateText(originalText, requestId);
        if (!processedText.equals(originalText)) {
            log.info("文本已截断: requestId={}, originalLength={}, truncatedLength={}",
                    requestId, originalText.length(), processedText.length());
        }

        long startTime = System.currentTimeMillis();

        // 直接执行润色，不进行额外的可用性检查以避免双重API调用
        return executePolishingWithRetry(processedText, requestId, startTime)
                .timeout(Duration.ofSeconds(textPolishingProperties.getTimeout()))
                .doOnError(error -> {
                    long duration = System.currentTimeMillis() - startTime;
                    if (error instanceof java.util.concurrent.TimeoutException) {
                        log.warn("文本润色超时，使用降级策略: requestId={}, timeout={}s, duration={}ms",
                                requestId, textPolishingProperties.getTimeout(), duration);
                    } else {
                        log.warn("文本润色失败，使用降级策略: requestId={}, error={}, duration={}ms",
                                requestId, error.getMessage(), duration);
                    }
                })
                .onErrorReturn(textPolishingProperties.getFallback().isUseOriginalOnError() ? originalText : "")
                .doOnSuccess(polishedText -> {
                    long duration = System.currentTimeMillis() - startTime;
                    boolean fallbackUsed = polishedText.equals(originalText);
                    if (fallbackUsed) {
                        log.info("文本润色使用了降级: requestId={}, duration={}ms", requestId, duration);
                    } else {
                        log.info("文本润色成功: requestId={}, originalLength={}, polishedLength={}, duration={}ms",
                                requestId, originalText.length(), polishedText.length(), duration);
                    }
                })
                .doOnError(error -> {
                    long duration = System.currentTimeMillis() - startTime;
                    log.error("带降级的文本润色完全失败: requestId={}, error={}, duration={}ms",
                            requestId, error.getMessage(), duration);
                })
                .onErrorReturn(textPolishingProperties.getFallback().isUseOriginalOnError() ? originalText : "");
    }

    /**
     * 执行带重试机制的文本润色
     *
     * @param text 要润色的文本
     * @param requestId 请求ID
     * @param startTime 开始时间
     * @return 润色后的文本
     */
    private Mono<String> executePolishingWithRetry(String text, String requestId, long startTime) {
        Mono<String> polishingMono = aiModelService.polishText(text);

        // 检查AIModelService是否返回null
        if (polishingMono == null) {
            log.warn("AIModelService.polishText返回null，使用原文: requestId={}", requestId);
            return Mono.just(text);
        }

        return polishingMono
                .retry(textPolishingProperties.getMaxRetries())
                .doOnSuccess(polishedText -> {
                    long duration = System.currentTimeMillis() - startTime;
                    log.debug("润色API调用成功: requestId={}, duration={}ms", requestId, duration);
                })
                .doOnError(error -> {
                    long duration = System.currentTimeMillis() - startTime;
                    log.warn("润色API调用失败（已重试{}次): requestId={}, error={}, duration={}ms",
                            textPolishingProperties.getMaxRetries(), requestId, error.getMessage(), duration);
                });
    }

    /**
     * 验证和截断文本长度
     *
     * @param originalText 原始文本
     * @param requestId 请求ID
     * @return 验证后的文本（可能被截断）
     */
    private String validateAndTruncateText(String originalText, String requestId) {
        if (originalText == null) {
            return "";
        }

        int maxLength = textPolishingProperties.getValidation().getMaxTextLength();
        int minLength = textPolishingProperties.getValidation().getMinTextLength();

        // 检查最小长度
        if (originalText.trim().length() < minLength) {
            log.warn("文本长度小于最小要求: requestId={}, actualLength={}, minLength={}",
                    requestId, originalText.length(), minLength);
            return originalText; // 返回原文，不进行润色
        }

        // 检查最大长度并截断
        if (originalText.length() > maxLength) {
            log.warn("文本长度超过最大限制，将进行截断: requestId={}, originalLength={}, maxLength={}",
                    requestId, originalText.length(), maxLength);

            // 智能截断：尽量在句号、感叹号、问号处截断
            String truncated = smartTruncate(originalText, maxLength);
            log.info("文本智能截断完成: requestId={}, truncatedLength={}", requestId, truncated.length());
            return truncated;
        }

        return originalText;
    }

    /**
     * 智能截断文本
     * 尽量在句子结束符处截断，保持文本的完整性
     *
     * @param text 原始文本
     * @param maxLength 最大长度
     * @return 截断后的文本
     */
    private String smartTruncate(String text, int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }

        // 在最大长度范围内查找最后一个句子结束符
        String truncated = text.substring(0, maxLength);
        int lastSentenceEnd = Math.max(
                Math.max(truncated.lastIndexOf('。'), truncated.lastIndexOf('！')),
                Math.max(truncated.lastIndexOf('？'), truncated.lastIndexOf('.')
        ));

        // 如果找到句子结束符，并且位置不太靠前（至少保留一半长度）
        if (lastSentenceEnd > maxLength / 2) {
            return text.substring(0, lastSentenceEnd + 1);
        }

        // 否则直接截断并添加省略号
        return truncated.trim() + "...";
    }

    /**
     * 创建润色指标对象
     *
     * @param originalText 原始文本
     * @param polishedText 润色后文本
     * @param apiResponseTime API响应时间
     * @param fallbackUsed 是否使用了降级
     * @param requestId 请求ID
     * @return 润色指标对象
     */
    public PolishingMetrics createMetrics(String originalText, String polishedText,
                                        long apiResponseTime, boolean fallbackUsed, String requestId) {
        return new PolishingMetrics(
                originalText != null ? originalText.length() : 0,
                polishedText != null ? polishedText.length() : 0,
                apiResponseTime,
                fallbackUsed,
                requestId
        );
    }


    /**
     * 记录润色请求开始事件
     *
     * @param requestId 请求ID
     * @param originalText 原始文本
     * @param methodName 调用的方法名
     */
    private void logPolishingStart(String requestId, String originalText, String methodName) {
        String timestamp = LocalDateTime.now().format(LOG_TIME_FORMATTER);
        int textLength = originalText != null ? originalText.length() : 0;

        log.info("[POLISHING] requestId={} | timestamp={} | action=START | method={} | originalLength={} | status=INITIATED",
                requestId, timestamp, methodName, textLength);

        // 记录文本内容的前50个字符用于调试（避免日志过长）
        if (log.isDebugEnabled() && originalText != null && !originalText.trim().isEmpty()) {
            String textPreview = originalText.length() > 50 ?
                    originalText.substring(0, 50) + "..." : originalText;
            log.debug("[POLISHING] requestId={} | textPreview={}", requestId, textPreview);
        }
    }

    /**
     * 记录润色成功事件
     *
     * @param requestId 请求ID
     * @param originalText 原始文本
     * @param polishedText 润色后文本
     * @param duration 处理时长
     */
    private void logPolishingSuccess(String requestId, String originalText, String polishedText, long duration) {
        String timestamp = LocalDateTime.now().format(LOG_TIME_FORMATTER);
        int originalLength = originalText != null ? originalText.length() : 0;
        int polishedLength = polishedText != null ? polishedText.length() : 0;
        double lengthChangeRatio = originalLength > 0 ? (double)(polishedLength - originalLength) / originalLength * 100 : 0;

        log.info("[POLISHING] requestId={} | timestamp={} | action=SUCCESS | originalLength={} | polishedLength={} | lengthChange={:.1f}% | duration={}ms | status=COMPLETED",
                requestId, timestamp, originalLength, polishedLength, lengthChangeRatio, duration);

        // 记录润色后文本的前50个字符用于调试
        if (log.isDebugEnabled() && polishedText != null && !polishedText.trim().isEmpty()) {
            String polishedPreview = polishedText.length() > 50 ?
                    polishedText.substring(0, 50) + "..." : polishedText;
            log.debug("[POLISHING] requestId={} | polishedPreview={}", requestId, polishedPreview);
        }
    }

    /**
     * 记录润色失败事件
     *
     * @param requestId 请求ID
     * @param originalText 原始文本
     * @param error 错误信息
     * @param duration 处理时长
     */
    private void logPolishingError(String requestId, String originalText, Throwable error, long duration) {
        String timestamp = LocalDateTime.now().format(LOG_TIME_FORMATTER);
        int originalLength = originalText != null ? originalText.length() : 0;
        String errorType = error.getClass().getSimpleName();
        String errorMessage = error.getMessage();

        // 判断错误类型
        boolean isTimeout = error instanceof java.util.concurrent.TimeoutException;
        String status = isTimeout ? "TIMEOUT" : "ERROR";

        log.error("[POLISHING] requestId={} | timestamp={} | action=FAILURE | originalLength={} | errorType={} | errorMessage={} | duration={}ms | status={}",
                requestId, timestamp, originalLength, errorType, errorMessage, duration, status);

        // 记录详细的错误堆栈信息（仅在DEBUG级别）
        if (log.isDebugEnabled()) {
            log.debug("[POLISHING] requestId={} | errorStackTrace:", requestId, error);
        }
    }

    /**
     * 记录成功的指标数据
     *
     * @param originalText 原始文本
     * @param polishedText 润色后文本
     * @param duration 处理时长
     * @param fallbackUsed 是否使用了降级
     * @param requestId 请求ID
     */
    private void recordSuccessMetrics(String originalText, String polishedText, long duration, boolean fallbackUsed, String requestId) {
        // 更新统计指标
        successfulRequests.incrementAndGet();
        totalProcessingTime.addAndGet(duration);

        int originalLength = originalText != null ? originalText.length() : 0;
        int polishedLength = polishedText != null ? polishedText.length() : 0;

        totalOriginalLength.addAndGet(originalLength);
        totalPolishedLength.addAndGet(polishedLength);

        if (fallbackUsed) {
            fallbackRequests.incrementAndGet();
        }

        // 创建并记录指标对象
        PolishingMetrics metrics = createMetrics(originalText, polishedText, duration, fallbackUsed, requestId);
        logMetrics(metrics, "SUCCESS");

        // 定期输出统计信息
        if (totalRequests.get() % 10 == 0) {
            logPerformanceStatistics();
        }
    }

    /**
     * 记录失败的指标数据
     *
     * @param originalText 原始文本
     * @param duration 处理时长
     * @param requestId 请求ID
     */
    private void recordFailureMetrics(String originalText, long duration, String requestId) {
        // 更新统计指标
        failedRequests.incrementAndGet();
        totalProcessingTime.addAndGet(duration);

        int originalLength = originalText != null ? originalText.length() : 0;
        totalOriginalLength.addAndGet(originalLength);

        // 创建并记录指标对象
        PolishingMetrics metrics = createMetrics(originalText, "", duration, true, requestId);
        logMetrics(metrics, "FAILURE");

        // 定期输出统计信息
        if (totalRequests.get() % 10 == 0) {
            logPerformanceStatistics();
        }
    }

    /**
     * 记录指标信息
     *
     * @param metrics 指标对象
     * @param eventType 事件类型
     */
    private void logMetrics(PolishingMetrics metrics, String eventType) {
        String timestamp = LocalDateTime.now().format(LOG_TIME_FORMATTER);

        log.info("[POLISHING_METRICS] requestId={} | timestamp={} | eventType={} | originalLength={} | polishedLength={} | apiResponseTime={}ms | fallbackUsed={}",
                metrics.getRequestId(), timestamp, eventType,
                metrics.getOriginalLength(), metrics.getPolishedLength(),
                metrics.getApiResponseTime(), metrics.isFallbackUsed());
    }

    /**
     * 输出性能统计信息
     */
    private void logPerformanceStatistics() {
        long total = totalRequests.get();
        long successful = successfulRequests.get();
        long failed = failedRequests.get();
        long timeout = timeoutRequests.get();
        long fallback = fallbackRequests.get();
        long totalTime = totalProcessingTime.get();
        int totalOriginal = totalOriginalLength.get();
        int totalPolished = totalPolishedLength.get();

        double successRate = total > 0 ? (double) successful / total * 100 : 0;
        double failureRate = total > 0 ? (double) failed / total * 100 : 0;
        double timeoutRate = total > 0 ? (double) timeout / total * 100 : 0;
        double fallbackRate = total > 0 ? (double) fallback / total * 100 : 0;
        double avgProcessingTime = total > 0 ? (double) totalTime / total : 0;
        double avgOriginalLength = total > 0 ? (double) totalOriginal / total : 0;
        double avgPolishedLength = successful > 0 ? (double) totalPolished / successful : 0;
        double avgLengthChange = avgOriginalLength > 0 ? (avgPolishedLength - avgOriginalLength) / avgOriginalLength * 100 : 0;

        String timestamp = LocalDateTime.now().format(LOG_TIME_FORMATTER);

        log.info("[POLISHING_STATISTICS] timestamp={} | totalRequests={} | successfulRequests={} | failedRequests={} | timeoutRequests={} | fallbackRequests={}",
                timestamp, total, successful, failed, timeout, fallback);

        log.info("[POLISHING_STATISTICS] timestamp={} | successRate={:.2f}% | failureRate={:.2f}% | timeoutRate={:.2f}% | fallbackRate={:.2f}%",
                timestamp, successRate, failureRate, timeoutRate, fallbackRate);

        log.info("[POLISHING_STATISTICS] timestamp={} | avgProcessingTime={:.2f}ms | avgOriginalLength={:.1f} | avgPolishedLength={:.1f} | avgLengthChange={:.1f}%",
                timestamp, avgProcessingTime, avgOriginalLength, avgPolishedLength, avgLengthChange);
    }

}
