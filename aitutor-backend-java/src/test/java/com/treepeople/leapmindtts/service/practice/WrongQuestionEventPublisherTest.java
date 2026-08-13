package com.treepeople.leapmindtts.service.practice;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.treepeople.leapmindtts.pojo.dto.profile.M6Dtos.LearningEventRequest;
import com.treepeople.leapmindtts.pojo.entity.PracticeQuestion;
import com.treepeople.leapmindtts.service.profile.UserEventService;
import com.treepeople.leapmindtts.util.PracticeKnowledgePointIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class WrongQuestionEventPublisherTest {
    private UserEventService userEventService;
    private WrongQuestionEventPublisher publisher;
    private PracticeQuestion question;

    @BeforeEach
    void setUp() {
        userEventService = mock(UserEventService.class);
        publisher = new WrongQuestionEventPublisher(userEventService, new ObjectMapper());
        question = new PracticeQuestion();
        question.setId(5001L);
        question.setSubject("数学");
        question.setKnowledgePoint("二次函数");
    }

    @Test
    void recordsTheDocumentedWrongQuestionEventInternally() {
        publisher.publishBestEffort(1001L, question, "UNRESOLVED", 1, "session-abc");

        ArgumentCaptor<LearningEventRequest> captor = ArgumentCaptor.forClass(LearningEventRequest.class);
        verify(userEventService).recordInternal(captor.capture());
        LearningEventRequest event = captor.getValue();
        assertEquals("m1-wrong:1001:5001:UNRESOLVED:1", event.eventId());
        assertEquals("wrong_question_changed", event.eventType());
        assertEquals("M1", event.sourceModule());
        assertEquals("1.0", event.schemaVersion());
        assertEquals(1001L, event.userId());
        assertEquals(PracticeKnowledgePointIds.from("数学", "二次函数"), event.kpId());
        assertEquals("session-abc", event.sessionId());
        assertEquals(5001L, event.data().get("questionId").longValue());
        assertEquals("UNRESOLVED", event.data().get("status").textValue());
        assertEquals(1, event.data().get("wrongCount").intValue());
    }

    @Test
    void persistenceFailureDoesNotEscapeToTheMistakeFlow() {
        doThrow(new IllegalStateException("数据库不可用")).when(userEventService).recordInternal(any());

        assertDoesNotThrow(() -> publisher.publishBestEffort(
                1001L, question, "RESOLVED", 2, null));
    }
}
