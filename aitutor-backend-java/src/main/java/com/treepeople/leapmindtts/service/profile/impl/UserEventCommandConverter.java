package com.treepeople.leapmindtts.service.profile.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.treepeople.leapmindtts.pojo.entity.UserEvent;
import com.treepeople.leapmindtts.service.profile.platform.KnowledgePointRef;
import com.treepeople.leapmindtts.service.profile.platform.LearningEventCommand;
import com.treepeople.leapmindtts.service.profile.platform.LearningEventPayload;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Converts a persisted user_event row into the engine command; any failure is an illegal event, never a partial conversion. */
@Component
public class UserEventCommandConverter {
    private static final Map<String, Class<? extends LearningEventPayload>> PAYLOAD_TYPES = Map.of(
            "answer_question", LearningEventPayload.AnswerQuestion.class,
            "finish_practice", LearningEventPayload.FinishPractice.class,
            "request_explanation", LearningEventPayload.RequestExplanation.class,
            "explanation_feedback", LearningEventPayload.ExplanationFeedback.class,
            "weak_point_changed", LearningEventPayload.WeakPointChanged.class,
            "lecture_interact", LearningEventPayload.LectureInteract.class,
            "lesson_material_used", LearningEventPayload.LessonMaterialUsed.class,
            "ask_doubt", LearningEventPayload.AskDoubt.class,
            "mark_reviewed", LearningEventPayload.MarkReviewed.class,
            "preference_changed", LearningEventPayload.PreferenceChanged.class);

    private final ObjectMapper mapper;

    public UserEventCommandConverter(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public LearningEventCommand convert(UserEvent event) {
        if (event == null || event.getEventId() == null || event.getUserId() == null || event.getUserId() <= 0
                || event.getEventType() == null || event.getOccurredAt() == null) throw invalid();
        Class<? extends LearningEventPayload> type = PAYLOAD_TYPES.get(event.getEventType());
        if (type == null) throw invalid();
        LearningEventPayload payload = readPayload(event, type);
        KnowledgePointRef knowledgePoint = event.getKpId() == null ? KnowledgePointRef.none()
                : new KnowledgePointRef.Resolved(event.getKpId());
        if (event.getKpId() == null && "answer_question".equals(event.getEventType())) throw invalid();
        String traceId = event.getTraceId() != null ? event.getTraceId() : event.getEventId();
        return new LearningEventCommand(event.getEventId(), event.getUserId(),
                event.getOccurredAt().toInstant(ZoneOffset.UTC), event.getSessionId(), knowledgePoint,
                traceId, payload);
    }

    private LearningEventPayload readPayload(UserEvent event, Class<? extends LearningEventPayload> type) {
        try {
            return mapper.readValue(event.getEventDataJson(), type);
        } catch (IOException | RuntimeException malformed) {
            throw invalid();
        }
    }

    private IllegalArgumentException invalid() { return new IllegalArgumentException("user event cannot be converted to a learning command"); }
}
