package com.treepeople.leapmindtts.profile.projection;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.treepeople.leapmindtts.pojo.entity.UserEvent;
import com.treepeople.leapmindtts.pojo.entity.UserProfile;
import com.treepeople.leapmindtts.service.profile.engine.ProfileEnginePort;
import com.treepeople.leapmindtts.service.profile.engine.ProfileEngineRequest;
import com.treepeople.leapmindtts.service.profile.engine.ProfileEngineResponse;
import com.treepeople.leapmindtts.service.profile.engine.ProfileEngineUnavailableException;
import com.treepeople.leapmindtts.service.profile.impl.PendingEventQuery;
import com.treepeople.leapmindtts.service.profile.impl.ProfileCasConflictException;
import com.treepeople.leapmindtts.service.profile.impl.ProfileCasWriter;
import com.treepeople.leapmindtts.service.profile.impl.ProfileProjectionScheduler;
import com.treepeople.leapmindtts.service.profile.impl.UserEventCommandConverter;
import com.treepeople.leapmindtts.service.profile.platform.KnowledgePointRef;
import com.treepeople.leapmindtts.service.profile.platform.LearningEventCommand;
import com.treepeople.leapmindtts.service.profile.platform.LearningEventPayload;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ProfileProjectionSchedulerTest {
    private final PendingEventQuery query = mock(PendingEventQuery.class);
    private final UserEventCommandConverter converter = mock(UserEventCommandConverter.class);
    private final ProfileEnginePort engine = mock(ProfileEnginePort.class);
    private final ProfileCasWriter writer = mock(ProfileCasWriter.class);
    private final ProfileProjectionScheduler scheduler = new ProfileProjectionScheduler(query, converter, engine, writer);

    private UserEvent event(long id, long userId) {
        return UserEvent.builder().id(id).eventId("evt-" + id).userId(userId).eventType("answer_question")
                .sourceModule("M1").eventDataJson("{}").schemaVersion("1.0").processStatus("PENDING").build();
    }

    private LearningEventCommand command(long id, long userId) {
        return new LearningEventCommand("evt-" + id, userId, Instant.parse("2026-08-08T10:00:00Z"), null,
                new KnowledgePointRef.Resolved(42L), "evt-" + id,
                new LearningEventPayload.AnswerQuestion(true, 3, 20, 1, null));
    }

    private ProfileEngineResponse.Ready ready() {
        return new ProfileEngineResponse.Ready("1.0", UUID.randomUUID(), 23L, 5L, 6L, 101L,
                ProfileEngineResponse.EngineStatus.READY, "m6-v1", Instant.parse("2026-08-08T11:00:00Z"),
                new ProfileEngineResponse.EngineProfile("S1", List.of("text"), "step_by_step", "moderate",
                        List.of(), List.of(), "摘要", new BigDecimal("0.9")), List.of());
    }

    private UserProfile stamp(long version, long watermark) {
        UserProfile stamp = new UserProfile();
        stamp.setUserId(23L);
        stamp.setProfileVersion(version);
        stamp.setLastProcessedEventId(watermark);
        return stamp;
    }

    private void stubConversion() {
        when(query.batchLimit()).thenReturn(200);
        when(converter.convert(any())).thenAnswer(invocation -> {
            UserEvent event = invocation.getArgument(0);
            return command(event.getId(), event.getUserId());
        });
    }

    @Test void buildsIncrementalRequestAndAppliesResponse() {
        when(query.pendingUserIds()).thenReturn(List.of(23L));
        when(query.projectionStamp(23L)).thenReturn(stamp(5L, 99L));
        when(query.pendingSince(23L, 99L, 200)).thenReturn(List.of(event(100L, 23L), event(101L, 23L)));
        stubConversion();
        ProfileEngineResponse.Ready response = ready();
        when(engine.buildProfile(any())).thenReturn(response);

        scheduler.scan();

        verify(engine).buildProfile(argThat(request -> "1.0".equals(request.contractVersion())
                && request.mode() == ProfileEngineRequest.Mode.INCREMENTAL
                && request.userId() == 23L && request.baseProfileVersion() == 5L
                && request.fromEventIdExclusive() == 99L && request.eventWatermarkInclusive() == 101L
                && request.events().size() == 2
                && request.events().get(0).dbEventId() == 100L && request.events().get(1).dbEventId() == 101L));
        verify(writer).apply(response, stamp(5L, 99L), List.of(event(100L, 23L), event(101L, 23L)));
        verify(writer, never()).markFailed(any());
    }

    @Test void engineUnavailableSkipsUserButContinuesOthers() {
        when(query.pendingUserIds()).thenReturn(List.of(7L, 23L));
        when(query.projectionStamp(7L)).thenReturn(stamp(1L, 0L));
        when(query.projectionStamp(23L)).thenReturn(stamp(5L, 99L));
        when(query.pendingSince(7L, 0L, 200)).thenReturn(List.of(event(1L, 7L)));
        when(query.pendingSince(23L, 99L, 200)).thenReturn(List.of(event(100L, 23L)));
        stubConversion();
        when(engine.buildProfile(any())).thenAnswer(invocation -> {
            ProfileEngineRequest request = invocation.getArgument(0);
            if (request.userId() == 7L) throw new ProfileEngineUnavailableException("NOT_CONNECTED");
            return ready();
        });

        scheduler.scan();

        verify(writer).apply(any(), eq(stamp(5L, 99L)), anyList());
        verify(writer, never()).markFailed(any());
    }

    @Test void conversionFailureMarksFailedAndProcessesTheRest() {
        when(query.pendingUserIds()).thenReturn(List.of(23L));
        when(query.projectionStamp(23L)).thenReturn(stamp(5L, 99L));
        when(query.pendingSince(23L, 99L, 200)).thenReturn(List.of(event(100L, 23L), event(101L, 23L)));
        when(query.batchLimit()).thenReturn(200);
        when(converter.convert(any())).thenAnswer(invocation -> {
            UserEvent event = invocation.getArgument(0);
            if (event.getId() == 100L) throw new IllegalArgumentException("bad event");
            return command(event.getId(), event.getUserId());
        });
        when(engine.buildProfile(any())).thenReturn(ready());

        scheduler.scan();

        verify(writer).markFailed(100L);
        verify(writer).apply(any(), any(), argThat(events -> events.size() == 1 && events.get(0).getId() == 101L));
        verify(engine).buildProfile(argThat(request -> request.eventWatermarkInclusive() == 101L
                && request.events().size() == 1 && request.events().get(0).dbEventId() == 101L));
    }

    @Test void allEventsFailedSkipsEngineCall() {
        when(query.pendingUserIds()).thenReturn(List.of(23L));
        when(query.projectionStamp(23L)).thenReturn(stamp(5L, 99L));
        when(query.pendingSince(23L, 99L, 200)).thenReturn(List.of(event(100L, 23L)));
        when(query.batchLimit()).thenReturn(200);
        when(converter.convert(any())).thenThrow(new IllegalArgumentException("bad event"));

        scheduler.scan();

        verify(engine, never()).buildProfile(any());
        verify(writer).markFailed(100L);
        verify(writer, never()).apply(any(), any(), anyList());
    }

    @Test void emptyBatchSkipsEngineCall() {
        when(query.pendingUserIds()).thenReturn(List.of(23L));
        when(query.projectionStamp(23L)).thenReturn(stamp(5L, 99L));
        when(query.pendingSince(23L, 99L, 200)).thenReturn(List.of());

        scheduler.scan();

        verify(engine, never()).buildProfile(any());
        verify(writer, never()).apply(any(), any(), anyList());
    }

    @Test void noPendingUsersIsNoOp() {
        when(query.pendingUserIds()).thenReturn(List.of());
        scheduler.scan();
        verifyNoInteractions(engine, writer);
    }

    @Test void casConflictSkipsBatchAndRetriesLater() {
        when(query.pendingUserIds()).thenReturn(List.of(23L));
        when(query.projectionStamp(23L)).thenReturn(stamp(5L, 99L));
        when(query.pendingSince(23L, 99L, 200)).thenReturn(List.of(event(100L, 23L)));
        stubConversion();
        when(engine.buildProfile(any())).thenReturn(ready());
        doThrow(new ProfileCasConflictException(23L, 5L)).when(writer).apply(any(), any(), anyList());

        assertDoesNotThrow(scheduler::scan);
        verify(writer, never()).markFailed(any());
    }

    @Test void disabledByDefaultAndRegisteredWhenEnabled() {
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withBean(PendingEventQuery.class, () -> mock(PendingEventQuery.class))
                .withBean(UserEventCommandConverter.class, () -> mock(UserEventCommandConverter.class))
                .withBean(ProfileEnginePort.class, () -> mock(ProfileEnginePort.class))
                .withBean(ProfileCasWriter.class, () -> mock(ProfileCasWriter.class))
                .withUserConfiguration(ProfileProjectionScheduler.class);
        runner.run(context -> assertEquals(0, context.getBeansOfType(ProfileProjectionScheduler.class).size()));
        runner.withPropertyValues("m6.profile-engine.projection-enabled=true")
                .run(context -> assertEquals(1, context.getBeansOfType(ProfileProjectionScheduler.class).size()));
    }
}
