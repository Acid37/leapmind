package com.treepeople.leapmindtts.service.lesson;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.treepeople.leapmindtts.config.EventCollectionProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 事件采集 HTTP 客户端
 * <p>
 * 封装对 M6 事件采集服务 {@code POST /api/events/collect} 的调用。
 * 上报失败仅记录日志，不抛异常，避免影响主业务流程。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventCollectionClient {

    private final WebClient webClient;
    private final EventCollectionProperties properties;
    private final ObjectMapper objectMapper;

    private static final DateTimeFormatter ISO_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    /**
     * 上报 weak_point_changed 事件
     *
     * @param userId         用户ID
     * @param knowledgePoint 知识点标识
     * @param oldScore       变化前薄弱度分数（0-1）
     * @param newScore       变化后薄弱度分数（0-1）
     * @param reason         变化原因枚举
     */
    public void reportWeakPointChanged(Long userId,
                                       String knowledgePoint,
                                       BigDecimal oldScore,
                                       BigDecimal newScore,
                                       String reason) {
        if (!properties.isEnabled()) {
            log.debug("事件上报已禁用，跳过: userId={}, kp={}", userId, knowledgePoint);
            return;
        }

        try {
            String eventDataJson = buildEventData(oldScore, newScore, reason, knowledgePoint);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("module", "M3");
            body.put("eventType", "weak_point_changed");
            body.put("userId", userId);
            body.put("eventData", eventDataJson);
            body.put("eventTime", ISO_FORMATTER.format(LocalDateTime.now().atZone(ZoneId.of("Asia/Shanghai"))));

            sendWithRetry(body, properties.getMaxRetries());
        } catch (Exception e) {
            log.error("事件上报失败: userId={}, kp={}, reason={}, error={}",
                    userId, knowledgePoint, reason, e.getMessage());
        }
    }

    // ==================== 私有辅助方法 ====================

    private String buildEventData(BigDecimal oldScore, BigDecimal newScore,
                                  String reason, String knowledgePoint)
            throws JsonProcessingException {
        Map<String, Object> eventData = new LinkedHashMap<>();
        eventData.put("oldScore", safeScale(oldScore));
        eventData.put("newScore", safeScale(newScore));
        eventData.put("reason", reason);
        eventData.put("kpId", knowledgePoint);
        eventData.put("sourceModule", "M3");
        return objectMapper.writeValueAsString(eventData);
    }

    private void sendWithRetry(Map<String, Object> body, int retriesLeft) {
        try {
            String response = webClient.post()
                    .uri(properties.getBaseUrl() + properties.getCollectEndpoint())
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(java.time.Duration.ofSeconds(properties.getTimeout()));

            log.info("事件上报成功: module=M3, eventType=weak_point_changed, response={}", response);
        } catch (Exception e) {
            if (retriesLeft > 0) {
                log.warn("事件上报失败，剩余重试 {} 次: {}", retriesLeft, e.getMessage());
                sendWithRetry(body, retriesLeft - 1);
            } else {
                log.error("事件上报最终失败，已用尽重试次数: {}", e.getMessage());
            }
        }
    }

    private BigDecimal safeScale(BigDecimal value) {
        if (value == null) return BigDecimal.ZERO;
        return value.setScale(4, RoundingMode.HALF_UP);
    }
}
