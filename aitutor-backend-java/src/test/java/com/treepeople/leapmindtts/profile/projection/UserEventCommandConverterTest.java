package com.treepeople.leapmindtts.profile.projection;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.treepeople.leapmindtts.pojo.entity.UserEvent;
import com.treepeople.leapmindtts.service.profile.impl.UserEventCommandConverter;
import com.treepeople.leapmindtts.service.profile.platform.KnowledgePointRef;
import com.treepeople.leapmindtts.service.profile.platform.LearningEventCommand;
import com.treepeople.leapmindtts.service.profile.platform.LearningEventPayload;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import static java.util.Map.entry;
import org.junit.jupiter.api.Test;

class UserEventCommandConverterTest {
    private final UserEventCommandConverter converter = new UserEventCommandConverter(new ObjectMapper().findAndRegisterModules());

    private UserEvent event(String eventType, String dataJson) {
        return UserEvent.builder().id(7L).eventId("evt-7").userId(23L).eventType(eventType).sourceModule("M1")
                .sessionId("sess-1").kpId(42L).eventDataJson(dataJson).schemaVersion("1.0")
                .occurredAt(LocalDateTime.of(2026, 8, 8, 10, 30, 0)).receivedAt(LocalDateTime.now())
                .processStatus("PENDING").traceId("trace-9").build();
    }

    @Test void convertsAnswerQuestionWithResolvedKp() {
        LearningEventCommand command = converter.convert(event("answer_question",
                "{\"isCorrect\":true,\"difficulty\":3,\"timeSpentSec\":20,\"hintCount\":1,\"confusionTag\":\"step_unclear\"}"));
        assertEquals("evt-7", command.eventId());
        assertEquals(23L, command.subjectUserId());
        assertEquals(Instant.parse("2026-08-08T10:30:00Z"), command.occurredAt());
        assertEquals("sess-1", command.sessionId());
        assertEquals("trace-9", command.traceId());
        assertEquals(new KnowledgePointRef.Resolved(42L), command.knowledgePoint());
        assertEquals("answer_question", command.eventType());
        assertEquals("M1", command.sourceModule());
        assertEquals("1.0", command.schemaVersion());
        LearningEventPayload.AnswerQuestion payload = (LearningEventPayload.AnswerQuestion) command.payload();
        assertTrue(payload.isCorrect());
        assertEquals(3, payload.difficulty());
        assertEquals("step_unclear", payload.confusionTag());
    }

    @Test void traceIdFallsBackToEventIdWhenNull() {
        UserEvent raw = event("lesson_material_used", "{\"contentId\":\"c1\",\"materialType\":\"text\",\"result\":\"completed\"}");
        raw.setTraceId(null);
        assertEquals("evt-7", converter.convert(raw).traceId());
    }

    @Test void nullKpOnAnswerQuestionIsRejected() {
        UserEvent raw = event("answer_question", "{\"isCorrect\":false,\"difficulty\":2,\"timeSpentSec\":5,\"hintCount\":0}");
        raw.setKpId(null);
        assertThrows(IllegalArgumentException.class, () -> converter.convert(raw));
    }

    @Test void nullKpOnOtherEventBecomesNone() {
        UserEvent raw = event("finish_practice", "{\"questionCount\":5,\"accuracy\":0.8,\"durationSec\":120}");
        raw.setKpId(null);
        assertEquals(KnowledgePointRef.none(), converter.convert(raw).knowledgePoint());
    }

    @Test void unknownEventTypeIsRejected() {
        UserEvent raw = event("not_an_event", "{}");
        assertThrows(IllegalArgumentException.class, () -> converter.convert(raw));
    }

    @Test void malformedPayloadJsonIsRejected() {
        UserEvent raw = event("mark_reviewed", "{not-json");
        assertThrows(IllegalArgumentException.class, () -> converter.convert(raw));
    }

    @Test void nullEventIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> converter.convert(null));
    }

    @Test void allPayloadTypesMapToTheirRecord() {
        Map<String, String> samples = Map.ofEntries(
                entry("answer_question", "{\"isCorrect\":true,\"difficulty\":2,\"timeSpentSec\":10,\"hintCount\":0}"),
                entry("finish_practice", "{\"questionCount\":5,\"accuracy\":0.8,\"durationSec\":120}"),
                entry("wrong_question_changed", "{\"questionId\":99,\"status\":\"UNRESOLVED\",\"wrongCount\":2}"),
                entry("request_explanation", "{\"explainId\":\"e1\",\"reasonTag\":\"WRONG_ANSWER\"}"),
                entry("explanation_feedback", "{\"explainId\":\"e1\",\"feedback\":\"understood\",\"repeatCount\":1}"),
                entry("weak_point_changed", "{\"oldScore\":0.5,\"newScore\":0.3,\"reason\":\"ACCURACY_DROP\"}"),
                entry("lecture_interact", "{\"lectureId\":\"l1\",\"chapterId\":\"c1\",\"action\":\"pause\"}"),
                entry("lesson_material_used", "{\"contentId\":\"ct1\",\"materialType\":\"text\",\"result\":\"completed\"}"),
                entry("ask_doubt", "{\"topic\":\"topic-a\",\"confusionTag\":\"concept_unclear\",\"isFollowUp\":false}"),
                entry("mark_reviewed", "{\"result\":\"correct_without_hint\",\"timeSpentSec\":30,\"hintCount\":0}"),
                entry("preference_changed", "{\"preferenceKey\":\"content_mode\",\"preferenceValue\":\"video\"}"));
        for (Map.Entry<String, String> sample : samples.entrySet()) {
            LearningEventPayload payload = converter.convert(event(sample.getKey(), sample.getValue())).payload();
            assertEquals(sample.getKey(), payload.eventType());
            assertEquals("1.0", payload.schemaVersion());
            assertTrue(LearningEventPayload.class.isInstance(payload));
        }
    }

    @Test void convertsWrongQuestionChangedEvent() {
        LearningEventCommand command = converter.convert(event("wrong_question_changed",
                "{\"questionId\":1001,\"status\":\"UNRESOLVED\",\"wrongCount\":2}"));

        LearningEventPayload.WrongQuestionChanged payload =
                (LearningEventPayload.WrongQuestionChanged) command.payload();
        assertEquals("wrong_question_changed", command.eventType());
        assertEquals("M1", command.sourceModule());
        assertEquals(1001L, payload.questionId());
        assertEquals("UNRESOLVED", payload.status());
        assertEquals(2, payload.wrongCount());
    }

    @Test void occurredAtIsInterpretedAsUtc() {
        UserEvent raw = event("finish_practice", "{\"questionCount\":1,\"accuracy\":1.0,\"durationSec\":0}");
        raw.setOccurredAt(LocalDateTime.of(2026, 1, 1, 0, 0, 0));
        assertEquals(Instant.parse("2026-01-01T00:00:00Z"), converter.convert(raw).occurredAt());
        assertEquals(LocalDateTime.of(2026, 1, 1, 0, 0, 0).toInstant(ZoneOffset.UTC), converter.convert(raw).occurredAt());
    }
}
