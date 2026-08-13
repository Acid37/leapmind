package com.treepeople.leapmindtts.service.practice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.treepeople.leapmindtts.pojo.dto.profile.M6Dtos.LearningEventRequest;
import com.treepeople.leapmindtts.pojo.entity.PracticeQuestion;
import com.treepeople.leapmindtts.service.profile.UserEventService;
import com.treepeople.leapmindtts.util.PracticeKnowledgePointIds;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** M1错题状态事件发布器，业务事务提交后统一写入M6事件流。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WrongQuestionEventPublisher {
    private static final String EVENT_TYPE = "wrong_question_changed";
    private static final String SOURCE_MODULE = "M1";
    private static final String SCHEMA_VERSION = "1.0";

    private final UserEventService userEventService;
    private final ObjectMapper objectMapper;

    /**
     * 尽力发布错题状态变化；事件失败不回滚练习主业务。
     *
     * @param userId 用户ID
     * @param question 题目信息
     * @param status 错题状态
     * @param wrongCount 错误次数
     * @param sessionId 练习会话ID
     */
    public void publishBestEffort(
            Long userId,
            PracticeQuestion question,
            String status,
            int wrongCount,
            String sessionId) {
        Runnable publish = () -> recordBestEffort(userId, question, status, wrongCount, sessionId);
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
                log.warn("M1错题事件注册事务回调失败，将直接尝试发送: userId={}, questionId={}, reason={}",
                        userId,
                        question == null ? null : question.getId(),
                        exception.getClass().getSimpleName());
            }
        }
        publish.run();
    }

    private void recordBestEffort(
            Long userId,
            PracticeQuestion question,
            String status,
            int wrongCount,
            String sessionId) {
        try {
            if (question == null || question.getId() == null) {
                throw new IllegalArgumentException("错题缺少题目信息，无法发送画像事件");
            }
            int safeWrongCount = Math.max(1, wrongCount);
            ObjectNode data = objectMapper.createObjectNode();
            data.put("questionId", question.getId());
            data.put("status", status);
            data.put("wrongCount", safeWrongCount);

            LearningEventRequest event = new LearningEventRequest(
                    buildEventId(userId, question.getId(), status, safeWrongCount),
                    userId,
                    EVENT_TYPE,
                    SOURCE_MODULE,
                    OffsetDateTime.now(ZoneOffset.UTC),
                    SCHEMA_VERSION,
                    sessionId,
                    PracticeKnowledgePointIds.from(question.getSubject(), question.getKnowledgePoint()),
                    null,
                    data);
            userEventService.recordInternal(event);
        } catch (RuntimeException exception) {
            log.warn("M1错题状态事件发送失败，不影响错题主流程: userId={}, questionId={}, status={}, reason={}",
                    userId,
                    question == null ? null : question.getId(),
                    status,
                    exception.getClass().getSimpleName());
        }
    }

    private String buildEventId(Long userId, Long questionId, String status, int wrongCount) {
        return "m1-wrong:" + userId + ":" + questionId + ":" + status + ":" + wrongCount;
    }
}
