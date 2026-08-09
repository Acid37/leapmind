package com.treepeople.leapmindtts.profile.projection;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.treepeople.leapmindtts.mapper.UserEventMapper;
import com.treepeople.leapmindtts.mapper.UserKnowledgeMasteryMapper;
import com.treepeople.leapmindtts.mapper.UserProfileMapper;
import com.treepeople.leapmindtts.pojo.entity.UserEvent;
import com.treepeople.leapmindtts.pojo.entity.UserKnowledgeMastery;
import com.treepeople.leapmindtts.pojo.entity.UserProfile;
import com.treepeople.leapmindtts.service.profile.engine.ProfileEngineResponse;
import com.treepeople.leapmindtts.service.profile.impl.ProfileCasConflictException;
import com.treepeople.leapmindtts.service.profile.impl.ProfileCasWriter;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProfileCasWriterTest {
    private final UserProfileMapper profileMapper = mock(UserProfileMapper.class);
    private final UserKnowledgeMasteryMapper masteryMapper = mock(UserKnowledgeMasteryMapper.class);
    private final UserEventMapper eventMapper = mock(UserEventMapper.class);
    private final ProfileCasWriter writer = new ProfileCasWriter(profileMapper, masteryMapper, eventMapper,
            new ObjectMapper().findAndRegisterModules());

    private UserEvent event(long id) {
        return UserEvent.builder().id(id).eventId("evt-" + id).userId(23L).eventType("answer_question")
                .sourceModule("M1").eventDataJson("{}").schemaVersion("1.0").processStatus("PENDING").build();
    }

    private ProfileEngineResponse.EngineProfile profile() {
        return new ProfileEngineResponse.EngineProfile("S1", List.of("text"), "step_by_step", "moderate",
                List.of(new ProfileEngineResponse.RecentFocus(42L, new BigDecimal("0.80"))),
                List.of(new ProfileEngineResponse.RecentConfusion(42L, "step_unclear", 3L, new BigDecimal("0.60"), Instant.parse("2026-08-08T10:00:00Z"))),
                "摘要", new BigDecimal("0.900"));
    }

    private ProfileEngineResponse.Ready ready(ProfileEngineResponse.EngineProfile p) {
        return new ProfileEngineResponse.Ready("1.0", UUID.randomUUID(), 23L, 5L, 6L, 100L,
                ProfileEngineResponse.EngineStatus.READY, "m6-v1", Instant.parse("2026-08-08T11:00:00Z"), p, List.of());
    }

    @Test void readyCastsProfileAndMarksEventsProcessed() {
        UserProfile stamp = new UserProfile();
        stamp.setUserId(23L);
        stamp.setProfileVersion(5L);
        when(profileMapper.casProfile(any(), eq(100L), eq(5L))).thenReturn(1);
        List<UserEvent> events = List.of(event(98L), event(99L), event(100L));

        writer.apply(ready(profile()), stamp, events);

        verify(profileMapper).casProfile(argThat(target -> target.getProfileVersion() == 6L
                && "READY".equals(target.getProfileStatus()) && "S1".equals(target.getGrade())
                && "step_by_step".equals(target.getPreferredExplanationStyle())
                && "moderate".equals(target.getLearningPace())
                && "摘要".equals(target.getSummaryProfile())
                && new BigDecimal("0.900").compareTo(target.getConfidence()) == 0
                && target.getProfileDataJson() != null && target.getProfileDataJson().contains("recentConfusions")
                && target.getComputedAt() != null), eq(100L), eq(5L));
        for (UserEvent event : events) {
            assertEquals("PROCESSED", event.getProcessStatus());
            verify(eventMapper).updateById(event);
        }
        verifyNoInteractions(masteryMapper);
    }

    @Test void readyInsertsNewMasteryRow() {
        UserProfile stamp = new UserProfile();
        stamp.setUserId(23L);
        stamp.setProfileVersion(5L);
        when(profileMapper.casProfile(any(), any(), eq(5L))).thenReturn(1);
        when(masteryMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        ProfileEngineResponse.Ready response = new ProfileEngineResponse.Ready("1.0", UUID.randomUUID(), 23L, 5L, 6L, 100L,
                ProfileEngineResponse.EngineStatus.READY, "m6-v1", Instant.parse("2026-08-08T11:00:00Z"), profile(),
                List.of(new ProfileEngineResponse.KnowledgeMastery(42L, new BigDecimal("0.750"), "BASIC_MASTERY",
                        new BigDecimal("0.800"), 3L, "IMPROVING", "m6-v1", Instant.parse("2026-08-01T00:00:00Z"),
                        Instant.parse("2026-08-08T00:00:00Z"), Instant.parse("2026-08-08T11:00:00Z"))));

        writer.apply(response, stamp, List.of(event(100L)));

        verify(masteryMapper).insert(argThat(entity -> entity.getUserId() == 23L && entity.getKpId() == 42L
                && entity.getProfileVersion() == 6L && new BigDecimal("0.750").compareTo(entity.getMasteryScore()) == 0
                && "BASIC_MASTERY".equals(entity.getMasteryStatus()) && "IMPROVING".equals(entity.getTrend())
                && "m6-v1".equals(entity.getAlgorithmVersion()) && entity.getUpdatedAt() != null));
        verify(masteryMapper, never()).updateById(any());
    }

    @Test void readyUpdatesExistingMasteryRow() {
        UserProfile stamp = new UserProfile();
        stamp.setUserId(23L);
        stamp.setProfileVersion(5L);
        when(profileMapper.casProfile(any(), any(), eq(5L))).thenReturn(1);
        UserKnowledgeMastery existing = new UserKnowledgeMastery();
        existing.setId(77L);
        when(masteryMapper.selectOne(any(Wrapper.class))).thenReturn(existing);
        ProfileEngineResponse.Ready response = new ProfileEngineResponse.Ready("1.0", UUID.randomUUID(), 23L, 5L, 6L, 100L,
                ProfileEngineResponse.EngineStatus.READY, "m6-v1", Instant.parse("2026-08-08T11:00:00Z"), profile(),
                List.of(new ProfileEngineResponse.KnowledgeMastery(42L, new BigDecimal("0.750"), "BASIC_MASTERY",
                        new BigDecimal("0.800"), 3L, "IMPROVING", "m6-v1", Instant.parse("2026-08-01T00:00:00Z"),
                        Instant.parse("2026-08-08T00:00:00Z"), Instant.parse("2026-08-08T11:00:00Z"))));

        writer.apply(response, stamp, List.of(event(100L)));

        verify(masteryMapper, never()).insert(any());
        verify(masteryMapper).updateById(argThat(entity -> entity.getId() == 77L && entity.getProfileVersion() == 6L));
    }

    @Test void readyWithoutProfileRowInsertsVersionOne() {
        when(profileMapper.casProfile(any(), eq(100L), eq(0L))).thenReturn(0);
        writer.apply(ready(profile()), null, List.of(event(100L)));
        verify(profileMapper).insert(argThat(target -> target.getProfileVersion() == 6L
                && "READY".equals(target.getProfileStatus()) && "S1".equals(target.getGrade())));
        verify(profileMapper, never()).updateById(any());
    }

    @Test void casConflictWhenBaseVersionMoved() {
        UserProfile stamp = new UserProfile();
        stamp.setUserId(23L);
        stamp.setProfileVersion(5L);
        when(profileMapper.casProfile(any(), any(), eq(5L))).thenReturn(0);
        assertThrows(ProfileCasConflictException.class, () -> writer.apply(ready(profile()), stamp, List.of(event(100L))));
        verifyNoInteractions(masteryMapper);
    }

    @Test void insufficientDataWritesNotReadyWithoutDataOrComputedAt() {
        UserProfile stamp = new UserProfile();
        stamp.setUserId(23L);
        stamp.setProfileVersion(5L);
        when(profileMapper.casProfile(any(), eq(100L), eq(5L))).thenReturn(1);
        ProfileEngineResponse.InsufficientData response = new ProfileEngineResponse.InsufficientData("1.0",
                UUID.randomUUID(), 23L, 5L, 6L, 100L, ProfileEngineResponse.EngineStatus.INSUFFICIENT_DATA,
                "m6-v1", Instant.parse("2026-08-08T11:00:00Z"), List.of());

        writer.apply(response, stamp, List.of(event(100L)));

        verify(profileMapper).casProfile(argThat(target -> "NOT_READY".equals(target.getProfileStatus())
                && "INSUFFICIENT_DATA".equals(target.getStatusReason())
                && target.getGrade() == null && target.getProfileDataJson() == null
                && target.getComputedAt() == null), eq(100L), eq(5L));
        verifyNoInteractions(masteryMapper);
    }

    @Test void noChangeAdvancesWatermarkOnly() {
        UserProfile stamp = new UserProfile();
        stamp.setUserId(23L);
        stamp.setProfileVersion(5L);
        when(profileMapper.advanceWatermark(23L, 100L)).thenReturn(1);
        ProfileEngineResponse.NoChange response = new ProfileEngineResponse.NoChange("1.0", UUID.randomUUID(), 23L,
                5L, 5L, 100L, ProfileEngineResponse.EngineStatus.NO_CHANGE, "m6-v1", Instant.parse("2026-08-08T11:00:00Z"));

        writer.apply(response, stamp, List.of(event(100L)));

        verify(profileMapper).advanceWatermark(23L, 100L);
        verify(profileMapper, never()).casProfile(any(), any(), any());
        verify(profileMapper, never()).insert(any());
        verifyNoInteractions(masteryMapper);
    }

    @Test void noChangeWithoutProfileRowInsertsMinimalNotReady() {
        when(profileMapper.advanceWatermark(23L, 100L)).thenReturn(0);
        ProfileEngineResponse.NoChange response = new ProfileEngineResponse.NoChange("1.0", UUID.randomUUID(), 23L,
                0L, 0L, 100L, ProfileEngineResponse.EngineStatus.NO_CHANGE, "m6-v1", Instant.parse("2026-08-08T11:00:00Z"));

        writer.apply(response, null, List.of(event(100L)));

        verify(profileMapper).insert(argThat(target -> target.getProfileVersion() == 1L
                && "NOT_READY".equals(target.getProfileStatus())
                && "NO_CHANGE".equals(target.getStatusReason())
                && target.getLastProcessedEventId() == 100L && target.getComputedAt() == null));
    }

    @Test void responseUserMustMatchEventUser() {
        ProfileEngineResponse.Ready response = ready(profile());
        UserEvent otherUser = event(100L);
        otherUser.setUserId(99L);
        assertThrows(IllegalArgumentException.class, () -> writer.apply(response, null, List.of(otherUser)));
    }

    @Test void emptyEventsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> writer.apply(ready(profile()), null, List.of()));
    }

    @Test void markFailedFlipsSingleEventStatus() {
        writer.markFailed(55L);
        verify(eventMapper).updateById(argThat(event -> event.getId() == 55L && "FAILED".equals(event.getProcessStatus())));
    }
}
