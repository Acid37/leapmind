package com.treepeople.leapmindtts.profile.platform;

import static org.junit.jupiter.api.Assertions.*;

import com.treepeople.leapmindtts.service.profile.engine.*;
import com.treepeople.leapmindtts.service.profile.platform.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlatformContractsTest {
    private static final Instant NOW = Instant.parse("2026-07-27T00:00:00Z");
    private static final UUID REQUEST_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test void commandEnvelopeDerivesV1MetadataForAllTenPayloads() {
        List<Expected> values = List.of(
                new Expected(payload(new LearningEventPayload.AnswerQuestion(true, 2, 10, 0, null)), "answer_question", "M1"),
                new Expected(payload(new LearningEventPayload.FinishPractice(1, BigDecimal.ONE, 0)), "finish_practice", "M1"),
                new Expected(payload(new LearningEventPayload.RequestExplanation("exp-1", "USER_REQUEST")), "request_explanation", "M2"),
                new Expected(payload(new LearningEventPayload.ExplanationFeedback("exp-1", "understood", 0)), "explanation_feedback", "M2"),
                new Expected(payload(new LearningEventPayload.WeakPointChanged(BigDecimal.ZERO, BigDecimal.ONE, "RECALCULATED")), "weak_point_changed", "M3"),
                new Expected(payload(new LearningEventPayload.LectureInteract("lec-1", "ch-1", "pause")), "lecture_interact", "M4"),
                new Expected(payload(new LearningEventPayload.LessonMaterialUsed("mat-1", "text", "completed")), "lesson_material_used", "M5"),
                new Expected(payload(new LearningEventPayload.AskDoubt("why", "concept_unclear", false)), "ask_doubt", "M7"),
                new Expected(payload(new LearningEventPayload.MarkReviewed("correct_without_hint", 0, 0)), "mark_reviewed", "M6"),
                new Expected(payload(new LearningEventPayload.PreferenceChanged("learning_pace", "slow")), "preference_changed", "M6"));
        values.forEach(value -> assertAll(() -> assertEquals(value.type, value.command.eventType()), () -> assertEquals(value.source, value.command.sourceModule()), () -> assertEquals("1.0", value.command.schemaVersion())));
    }

    @Test void publisherRejectsWrongSubjectPurposeAndSourceBeforeDisabledOutcome() {
        LearningEventCommand command = payload(new LearningEventPayload.AnswerQuestion(true, 2, 10, 0, null));
        DisabledLearningEventPublisher publisher = new DisabledLearningEventPublisher((access, capability, resource) -> new PlatformCapabilityPolicy.CapabilityDecision(true, "ALLOWED"));
        EventPublishContext good = new EventPublishContext(7L, ActorKind.USER, 7L, null, SourceModule.M1, Purpose.PUBLISH_LEARNING_EVENT, "req-1");
        assertAll(() -> assertEquals(EventPublishOutcome.Status.NOT_CONNECTED, publisher.publish(good, command).status()),
                () -> assertEquals(EventPublishOutcome.Status.REJECTED, publisher.publish(new EventPublishContext(8L, ActorKind.USER, 8L, null, SourceModule.M1, Purpose.PUBLISH_LEARNING_EVENT, "req-1"), command).status()),
                () -> assertEquals(EventPublishOutcome.Status.REJECTED, publisher.publish(new EventPublishContext(7L, ActorKind.USER, 7L, null, SourceModule.M1, Purpose.READ_SCENE_CONTEXT, "req-1"), command).status()),
                () -> assertEquals(EventPublishOutcome.Status.REJECTED, publisher.publish(new EventPublishContext(7L, ActorKind.USER, 7L, null, SourceModule.M2, Purpose.PUBLISH_LEARNING_EVENT, "req-1"), command).status()));
    }

    @Test void kpAndSceneBoundariesAreTypedAndDisabledResponsesAreEmpty() {
        ProfileAccessContext access = new ProfileAccessContext(7L, ActorKind.USER, 7L, null, SourceModule.M1, Purpose.READ_SCENE_CONTEXT, "req-1");
        DisabledProfileContextProvider provider = new DisabledProfileContextProvider((context, capability, resource) -> new PlatformCapabilityPolicy.CapabilityDecision(true, "ALLOWED"));
        assertAll(() -> assertEquals(new KnowledgePointRef.Unresolved("external-kp"), new NoMappingKnowledgePointResolver().resolve("external-kp")),
                () -> assertThrows(IllegalArgumentException.class, () -> new PracticeContextRequest(List.of(KnowledgePointRef.none()))),
                () -> assertThrows(IllegalArgumentException.class, () -> new ExplainingContextRequest(null)),
                () -> assertDoesNotThrow(() -> new LecturingContextRequest(KnowledgePointRef.none())),
                () -> assertThrows(IllegalArgumentException.class, () -> new LessonPrepContextRequest(java.util.Arrays.asList((KnowledgePointRef.Resolved) null))),
                () -> assertThrows(IllegalArgumentException.class, () -> new KnowledgeStatusRequest(List.of())),
                () -> assertThrows(IllegalArgumentException.class, () -> new KnowledgeStatusRequest(List.of(new KnowledgePointRef.Resolved(1L), new KnowledgePointRef.Resolved(1L)))),
                () -> assertTrue(provider.practice(access, new PracticeContextRequest(List.of(new KnowledgePointRef.Resolved(1L)))).knowledgeStatuses().isEmpty()),
                () -> assertTrue(provider.lessonPrep(access, new LessonPrepContextRequest(List.of())).teachingSuggestions().isEmpty()),
                () -> assertTrue(provider.getKnowledgeStatus(access, new KnowledgeStatusRequest(List.of(new KnowledgePointRef.Resolved(1L)))).knowledge().isEmpty()));
    }

    @Test void engineWireContainsEnvelopeAndExactAnswerPayloadOnly() throws Exception {
        LearningEventCommand command = payload(new LearningEventPayload.AnswerQuestion(true, 2, 10, 0, "concept_unclear"));
        ProfileEngineRequest request = new ProfileEngineRequest("1.0", REQUEST_ID, 7L, ProfileEngineRequest.Mode.INCREMENTAL, 3L, 0L, 1L, List.of(new ProfileEngineEvent(1L, command)));
        String wire = new String(new StrictProfileEngineJsonCodec().writeRequest(request), StandardCharsets.UTF_8);
        assertAll(() -> assertTrue(wire.contains("\"eventId\":\"evt-1\"")), () -> assertTrue(wire.contains("\"kpId\":9")),
                () -> assertTrue(wire.contains("\"data\":{\"isCorrect\":true,\"difficulty\":2,\"timeSpentSec\":10,\"hintCount\":0,\"confusionTag\":\"concept_unclear\"}")),
                () -> assertFalse(wire.contains("subjectUserId")));
    }

    @Test void publisherBatchValidatesAndKeepsPerItemOutcomes() {
        EventPublishContext context = new EventPublishContext(7L, ActorKind.USER, 7L, null, SourceModule.M7, Purpose.PUBLISH_LEARNING_EVENT, "req-1");
        DisabledLearningEventPublisher publisher = new DisabledLearningEventPublisher((a, c, r) -> new PlatformCapabilityPolicy.CapabilityDecision(true, "ALLOWED"));
        List<EventPublishOutcome> outcomes = publisher.publishBatch(context, List.of(payload(new LearningEventPayload.AskDoubt("topic", "concept_unclear", false)), payload(new LearningEventPayload.AskDoubt("12345678901234567X", "concept_unclear", false))));
        assertAll(() -> assertEquals(EventPublishOutcome.Status.NOT_CONNECTED, outcomes.get(0).status()),
                () -> assertEquals(EventPublishOutcome.Status.REJECTED, outcomes.get(1).status()),
                () -> assertThrows(IllegalArgumentException.class, () -> publisher.publishBatch(context, List.of())));
    }

    @Test void engineRejectsUnresolvedAndOutOfWindowEventsAndOmitsNullOptionals() throws Exception {
        LearningEventCommand unresolved = new LearningEventCommand("evt-2", 7L, NOW, null, new KnowledgePointRef.Unresolved("external"), "trace-2", new LearningEventPayload.AnswerQuestion(true, 2, 10, 0, null));
        LearningEventCommand noKpAnswer = new LearningEventCommand("evt-3", 7L, NOW, null, KnowledgePointRef.none(), "trace-3", new LearningEventPayload.AnswerQuestion(true, 2, 10, 0, null));
        assertAll(() -> assertThrows(IllegalArgumentException.class, () -> new ProfileEngineRequest("1.0", REQUEST_ID, 7L, ProfileEngineRequest.Mode.INCREMENTAL, 0L, 0L, 1L, List.of(new ProfileEngineEvent(1L, unresolved)))),
                () -> assertThrows(IllegalArgumentException.class, () -> new ProfileEngineRequest("1.0", REQUEST_ID, 7L, ProfileEngineRequest.Mode.INCREMENTAL, 0L, 0L, 1L, List.of(new ProfileEngineEvent(2L, payload(new LearningEventPayload.AnswerQuestion(true, 2, 10, 0, null)))))),
                () -> assertThrows(IllegalArgumentException.class, () -> new ProfileEngineRequest("1.0", REQUEST_ID, 7L,
                        ProfileEngineRequest.Mode.INCREMENTAL, 0L, 0L, 1L, List.of(new ProfileEngineEvent(1L, noKpAnswer)))));
    }

    @Test void docsDescribeAllTenEventsAndNoUntypedSceneFields() throws Exception {
        String docs = java.nio.file.Files.readString(java.nio.file.Path.of("docs/m6/learning-event-contract.md"));
        assertAll(() -> assertTrue(docs.contains("answer_question")), () -> assertTrue(docs.contains("preference_changed")),
                () -> assertTrue(java.util.Arrays.stream(PracticeProfileContext.class.getRecordComponents()).noneMatch(component -> component.getType().getName().contains("Map"))),
                () -> assertFalse(java.util.Arrays.stream(ExplainingProfileContext.class.getRecordComponents()).anyMatch(component -> component.getType().getSimpleName().equals("JsonNode"))));
    }

    @Test void defaultPolicyIsNotConfiguredAndStateEnvelopesFailClosed() {
        EventPublishContext publish = new EventPublishContext(7L, ActorKind.USER, 7L, null, SourceModule.M1, Purpose.PUBLISH_LEARNING_EVENT, "req");
        ProfileAccessContext read = new ProfileAccessContext(7L, ActorKind.USER, 7L, null, SourceModule.M1, Purpose.READ_SCENE_CONTEXT, "req");
        DisabledLearningEventPublisher publisher = new DisabledLearningEventPublisher(new DefaultDenyPlatformCapabilityPolicy());
        DisabledProfileContextProvider provider = new DisabledProfileContextProvider(new DefaultDenyPlatformCapabilityPolicy());
        assertAll(
                () -> assertEquals(EventPublishOutcome.Status.NOT_CONFIGURED, publisher.publish(publish, payload(new LearningEventPayload.AnswerQuestion(true, 1, 0, 0, null))).status()),
                () -> assertEquals(ProfileContextStatus.NOT_CONFIGURED, provider.practice(read, new PracticeContextRequest(List.of(new KnowledgePointRef.Resolved(1L)))).profileStatus()),
                () -> assertThrows(IllegalArgumentException.class, () -> new PracticeProfileContext(7L, "practice", ProfileContextStatus.DENIED, null, null, null, null, null, ProfileAvailability.UNAVAILABLE, List.of(new ProfileKnowledgeContext(1L, ProfileKnowledgeContext.Availability.AVAILABLE, null, null, null, null, null)), List.of(), null, List.of())),
                () -> assertThrows(IllegalArgumentException.class, () -> new FullProfileContext(7L, ProfileContextStatus.NOT_READY, null, ProfileAvailability.UNAVAILABLE, 1L, null)),
                () -> assertThrows(IllegalArgumentException.class, () -> new KnowledgeStatusContext(7L, ProfileContextStatus.STALE, null, ProfileAvailability.UNAVAILABLE, 1L, List.of(new ProfileKnowledgeContext(1L, ProfileKnowledgeContext.Availability.AVAILABLE, null, null, null, null, 4_294_967_295L)))),
                () -> assertDoesNotThrow(() -> new ProfileKnowledgeContext(1L, ProfileKnowledgeContext.Availability.AVAILABLE, null, null, null, null, 4_294_967_295L)),
                () -> assertThrows(IllegalArgumentException.class, () -> new ProfileKnowledgeContext(1L, ProfileKnowledgeContext.Availability.AVAILABLE, null, null, null, null, 4_294_967_296L)));
    }

    @Test void everySceneRejectsFactsWhenNotMaterializedAndSharesMaterializedGuards() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> new ExplainingProfileContext(7L, "explaining", ProfileContextStatus.DEGRADED, null, null, null, null, null, ProfileAvailability.UNAVAILABLE, "7", null, List.of(), null, null, null)),
                () -> assertThrows(IllegalArgumentException.class, () -> new LecturingProfileContext(7L, "lecturing", ProfileContextStatus.NOT_READY, null, null, null, null, null, ProfileAvailability.UNAVAILABLE, List.of(), List.of(), "slow", List.of(), null)),
                () -> assertThrows(IllegalArgumentException.class, () -> new ConversationProfileContext(7L, "conversation", ProfileContextStatus.NOT_CONNECTED, null, null, null, null, null, ProfileAvailability.UNAVAILABLE, null, List.of(), null, "x")),
                () -> assertThrows(IllegalArgumentException.class, () -> new LessonPrepProfileContext(7L, "lesson_prep", ProfileContextStatus.NOT_CONFIGURED, null, null, null, null, null, ProfileAvailability.UNAVAILABLE, List.of(), null, List.of(), null, List.of("x"), null)),
                () -> assertThrows(IllegalArgumentException.class, () -> new PracticeProfileContext(7L, "practice", ProfileContextStatus.READY, null, 0L, NOW, NOW, null, ProfileAvailability.AVAILABLE, List.of(), List.of(), null, List.of())),
                () -> assertThrows(IllegalArgumentException.class, () -> new ExplainingProfileContext(7L, "explaining", ProfileContextStatus.STALE, null, 1L, null, NOW, null, ProfileAvailability.PARTIAL, null, null, List.of(), null, null, null)),
                () -> assertThrows(IllegalArgumentException.class, () -> new LecturingProfileContext(7L, "lecturing", ProfileContextStatus.READY, null, 1L, NOW, NOW, null, ProfileAvailability.UNAVAILABLE, List.of(), List.of(), null, List.of(), null)));
    }

    @Test void materializedAndNullableStatusReasonBoundariesAreAccepted() {
        ProfileKnowledgeContext knowledge = new ProfileKnowledgeContext(1L, ProfileKnowledgeContext.Availability.AVAILABLE, null, null, null, null, 0L);
        assertAll(
                () -> assertDoesNotThrow(() -> new ConversationProfileContext(7L, "conversation", ProfileContextStatus.READY, null, 1L, NOW, NOW, null, ProfileAvailability.AVAILABLE, null, List.of(), null, null)),
                () -> assertDoesNotThrow(() -> new FullProfileContext(7L, ProfileContextStatus.NOT_READY, null, ProfileAvailability.UNAVAILABLE, null, null)),
                () -> assertDoesNotThrow(() -> new KnowledgeStatusContext(7L, ProfileContextStatus.STALE, null, ProfileAvailability.PARTIAL, 1L, List.of(knowledge))),
                () -> assertDoesNotThrow(() -> new FullProfileData(null, List.of(), null, null, List.of(), List.of(), "x".repeat(16_383), null, null, null, null, List.of())),
                () -> assertThrows(IllegalArgumentException.class, () -> new FullProfileData(null, List.of(), null, null, List.of(), List.of(), "x".repeat(16_384), null, null, null, null, List.of())));
    }

    private static LearningEventCommand payload(LearningEventPayload value) { return new LearningEventCommand("evt-1", 7L, NOW, "session-1", new KnowledgePointRef.Resolved(9L), "trace-1", value); }
    private record Expected(LearningEventCommand command, String type, String source) { }
}
