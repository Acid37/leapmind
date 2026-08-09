package com.treepeople.leapmindtts.service.practice;

import com.treepeople.leapmindtts.pojo.entity.PracticeQuestion;
import com.treepeople.leapmindtts.util.PracticeKnowledgePointIds;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.RestOperations;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
public class WrongQuestionEventPublisher {

    private static final String EVENT_PATH = "/api/user-profile/{userId}/record-event";
    private static final ZoneOffset EVENT_OFFSET = ZoneOffset.ofHours(8);

    private final RestOperations restOperations;
    private final String endpoint;

    @Autowired
    public WrongQuestionEventPublisher(RestTemplateBuilder builder, Environment environment) {
        this(
                builder
                        .setConnectTimeout(Duration.ofSeconds(2))
                        .setReadTimeout(Duration.ofSeconds(3))
                        .build(),
                resolveBaseUrl(environment));
    }

    WrongQuestionEventPublisher(RestOperations restOperations, String baseUrl) {
        this.restOperations = restOperations;
        this.endpoint = stripTrailingSlash(baseUrl) + EVENT_PATH;
    }

    public void publishBestEffort(
            Long userId,
            PracticeQuestion question,
            String status,
            int wrongCount,
            String sessionId) {
        Runnable publish = () -> postBestEffort(userId, question, status, wrongCount, sessionId);
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            try {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        publish.run();
                    }
                });
                return;
            } catch (RuntimeException exception) {
                log.warn("M1 错题事件注册事务回调失败，将直接尝试发送: userId={}, questionId={}, reason={}",
                        userId,
                        question == null ? null : question.getId(),
                        exception.getClass().getSimpleName());
            }
        }
        publish.run();
    }

    private void postBestEffort(
            Long userId,
            PracticeQuestion question,
            String status,
            int wrongCount,
            String sessionId) {
        try {
            if (question == null || question.getId() == null) {
                throw new IllegalArgumentException("错题缺少题目信息，无法发送画像事件");
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("questionId", question.getId());
            data.put("status", status);
            data.put("wrongCount", Math.max(1, wrongCount));

            Map<String, Object> event = new LinkedHashMap<>();
            event.put("eventId", "m1-wrong:" + UUID.randomUUID());
            event.put("eventType", "wrong_question_changed");
            event.put("sourceModule", "M1");
            event.put("occurredAt", OffsetDateTime.now(EVENT_OFFSET));
            event.put("schemaVersion", "1.0");
            event.put("userId", userId);
            event.put("kpId", PracticeKnowledgePointIds.from(
                    question.getSubject(), question.getKnowledgePoint()));
            event.put("sessionId", sessionId);
            event.put("traceId", null);
            event.put("data", data);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            restOperations.postForEntity(
                    endpoint,
                    new HttpEntity<>(event, headers),
                    Void.class,
                    userId);
        } catch (RuntimeException exception) {
            log.warn("M1 错题状态事件发送失败，不影响错题主流程: userId={}, questionId={}, status={}, reason={}",
                    userId,
                    question == null ? null : question.getId(),
                    status,
                    exception.getClass().getSimpleName());
        }
    }

    private static String resolveBaseUrl(Environment environment) {
        String configured = environment.getProperty("m1.m6-event-base-url");
        if (configured != null && !configured.isBlank()) {
            return configured.trim();
        }
        return "http://127.0.0.1:" + environment.getProperty("server.port", "8080");
    }

    private static String stripTrailingSlash(String value) {
        String normalized = value == null ? "" : value.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("M1 画像事件地址不能为空");
        }
        return normalized;
    }
}
