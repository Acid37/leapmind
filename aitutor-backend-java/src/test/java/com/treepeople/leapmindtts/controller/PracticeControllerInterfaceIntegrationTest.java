package com.treepeople.leapmindtts.controller;

import com.treepeople.leapmindtts.pojo.dto.ExerciseRecordRequest;
import com.treepeople.leapmindtts.pojo.dto.profile.M6Dtos.LearningEventRequest;
import com.treepeople.leapmindtts.pojo.vo.ExerciseVO;
import com.treepeople.leapmindtts.service.PracticeService;
import com.treepeople.leapmindtts.service.lesson.WeakPointsService;
import com.treepeople.leapmindtts.service.profile.UserEventService;
import com.treepeople.leapmindtts.service.profile.validation.LearningEventPolicy;
import com.treepeople.leapmindtts.service.user.ReviewReminderService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PracticeControllerInterfaceIntegrationTest {

    private final PracticeService practiceService = mock(PracticeService.class);
    private final ReviewReminderService reviewReminderService = mock(ReviewReminderService.class);
    private final WeakPointsService weakPointsService = mock(WeakPointsService.class);
    private final UserEventService userEventService = mock(UserEventService.class);
    private final PracticeController controller = new PracticeController(
            practiceService, reviewReminderService, weakPointsService, userEventService);

    @Test
    void submitBridgesAnswerToWeakPointsAndM6WithoutTrustingClientUserId() {
        MockHttpServletRequest request = authenticatedRequest(7L);
        PracticeController.SubmitAnswerRequest body = new PracticeController.SubmitAnswerRequest();
        body.setQuestionId(12L);
        body.setUserAnswer("A");
        body.setDurationSeconds(18);
        body.setMode("FREE_PRACTICE");
        body.setSessionId("sess_123");
        body.setHintCount(1);
        body.setConfusionTag("公式记忆混淆");
        Map<String, Object> result = Map.of(
                "record", Map.of("id", 91L),
                "question", Map.of(
                        "id", 12L,
                        "subject", "数学",
                        "knowledgePoint", "函数",
                        "difficulty", "ADVANCED"),
                "correct", true);
        when(practiceService.submitAnswer(7L, body)).thenReturn(result);

        controller.submit(request, body);

        ArgumentCaptor<ExerciseRecordRequest> weakPoint = ArgumentCaptor.forClass(ExerciseRecordRequest.class);
        verify(weakPointsService).recordExerciseResult(weakPoint.capture());
        assertEquals(7L, weakPoint.getValue().getUserId());
        assertEquals("12", weakPoint.getValue().getExerciseId());
        assertEquals(1, weakPoint.getValue().getIsCorrect());

        ArgumentCaptor<LearningEventRequest> event = ArgumentCaptor.forClass(LearningEventRequest.class);
        verify(userEventService).record(eq(7L), event.capture(), eq(request));
        assertEquals("answer_question", event.getValue().eventType());
        assertEquals("M1", event.getValue().sourceModule());
        assertEquals("sess_123", event.getValue().sessionId());
        assertNotNull(event.getValue().kpId());
        assertTrue(event.getValue().kpId() > 0);
        assertEquals(8 * 60 * 60, event.getValue().occurredAt().getOffset().getTotalSeconds());
        assertEquals(3, event.getValue().data().get("difficulty").intValue());
        assertTrue(event.getValue().data().get("isCorrect").booleanValue());
        assertEquals(18, event.getValue().data().get("timeSpentSec").intValue());
        assertEquals(1, event.getValue().data().get("hintCount").intValue());
        assertEquals("formula_confusion", event.getValue().data().get("confusionTag").textValue());
        LearningEventPolicy.validate(event.getValue());
    }

    @Test
    void submitRejectsMissingSessionIdBeforeSavingAnswer() {
        MockHttpServletRequest request = authenticatedRequest(7L);
        PracticeController.SubmitAnswerRequest body = new PracticeController.SubmitAnswerRequest();
        body.setQuestionId(12L);
        body.setUserAnswer("A");

        assertThrows(IllegalArgumentException.class, () -> controller.submit(request, body));
    }

    @Test
    void recommendationsAlwaysUseAuthenticatedUser() {
        MockHttpServletRequest request = authenticatedRequest(8L);
        when(weakPointsService.recommendExercises(8L, "数学", null, 5))
                .thenReturn(List.of(ExerciseVO.builder().exerciseId("q1").knowledgePoint("导数").build()));

        controller.recommendations(request, "数学", null, 5);

        verify(weakPointsService).recommendExercises(8L, "数学", null, 5);
    }

    @Test
    void completeSessionPublishesFinishPracticeEvent() {
        MockHttpServletRequest request = authenticatedRequest(9L);
        PracticeController.CompleteSessionRequest body = new PracticeController.CompleteSessionRequest();
        body.setSessionId("sess_complete_1");
        body.setQuestionCount(10);
        body.setCorrectCount(8);
        body.setDurationSeconds(300);

        controller.completeSession(request, body);

        ArgumentCaptor<LearningEventRequest> event = ArgumentCaptor.forClass(LearningEventRequest.class);
        verify(userEventService).record(eq(9L), event.capture(), eq(request));
        assertEquals("finish_practice", event.getValue().eventType());
        assertEquals("M1", event.getValue().sourceModule());
        assertEquals("sess_complete_1", event.getValue().sessionId());
        assertEquals(8 * 60 * 60, event.getValue().occurredAt().getOffset().getTotalSeconds());
        assertEquals(10, event.getValue().data().get("questionCount").intValue());
        assertEquals(0.8, event.getValue().data().get("accuracy").doubleValue());
        assertEquals(300, event.getValue().data().get("durationSec").intValue());
        LearningEventPolicy.validate(event.getValue());
    }

    private MockHttpServletRequest authenticatedRequest(Long userId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("userId", userId);
        return request;
    }
}
