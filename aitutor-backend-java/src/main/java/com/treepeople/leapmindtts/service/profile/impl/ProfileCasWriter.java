package com.treepeople.leapmindtts.service.profile.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.treepeople.leapmindtts.mapper.UserEventMapper;
import com.treepeople.leapmindtts.mapper.UserKnowledgeMasteryMapper;
import com.treepeople.leapmindtts.mapper.UserProfileMapper;
import com.treepeople.leapmindtts.pojo.entity.UserEvent;
import com.treepeople.leapmindtts.pojo.entity.UserKnowledgeMastery;
import com.treepeople.leapmindtts.pojo.entity.UserProfile;
import com.treepeople.leapmindtts.service.profile.engine.ProfileEngineResponse;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists one engine response in a single transaction: CAS profile version, upsert mastery,
 * advance watermark, then flip events to PROCESSED. Any failure rolls everything back.
 */
@Service
public class ProfileCasWriter {
    private final UserProfileMapper profileMapper;
    private final UserKnowledgeMasteryMapper masteryMapper;
    private final UserEventMapper eventMapper;
    private final ObjectMapper mapper;

    public ProfileCasWriter(UserProfileMapper profileMapper, UserKnowledgeMasteryMapper masteryMapper,
                            UserEventMapper eventMapper, ObjectMapper mapper) {
        this.profileMapper = profileMapper;
        this.masteryMapper = masteryMapper;
        this.eventMapper = eventMapper;
        this.mapper = mapper;
    }

    @Transactional
    public void apply(ProfileEngineResponse response, UserProfile stamp, List<UserEvent> events) {
        if (response == null || response.userId() == null || response.status() == null
                || response.targetProfileVersion() == null || response.eventWatermarkInclusive() == null
                || events == null || events.isEmpty()) throw new IllegalArgumentException("profile projection requires a valid response and events");
        Long userId = response.userId();
        for (UserEvent event : events) {
            if (!userId.equals(event.getUserId())) throw new IllegalArgumentException("response user does not match event user");
        }
        long base = stamp == null || stamp.getProfileVersion() == null ? 0 : stamp.getProfileVersion();
        long watermark = response.eventWatermarkInclusive();
        if (response instanceof ProfileEngineResponse.Ready ready) {
            applyReady(ready, userId, base, watermark);
        } else if (response instanceof ProfileEngineResponse.InsufficientData insufficient) {
            applyInsufficient(insufficient, userId, base, watermark);
        } else {
            applyNoChange(userId, base, watermark);
        }
        for (UserEvent event : events) {
            event.setProcessStatus("PROCESSED");
            eventMapper.updateById(event);
        }
    }

    /** Marks a single event FAILED without touching the batch; used for conversion failures before any engine call. */
    public void markFailed(Long eventId) {
        UserEvent event = new UserEvent();
        event.setId(eventId);
        event.setProcessStatus("FAILED");
        eventMapper.updateById(event);
    }

    private void applyReady(ProfileEngineResponse.Ready response, Long userId, long base, long watermark) {
        ProfileEngineResponse.EngineProfile profile = response.profile();
        if (profile == null) throw new IllegalArgumentException("READY response must carry a profile");
        UserProfile target = target(response, userId, base);
        target.setProfileStatus("READY");
        target.setStatusReason(null);
        target.setGrade(profile.grade());
        target.setPreferredContentModesJson(writeJson(profile.preferredContentModes()));
        target.setPreferredExplanationStyle(profile.preferredExplanationStyle());
        target.setLearningPace(profile.learningPace());
        target.setRecentFocusJson(writeJson(profile.recentFocus()));
        target.setSummaryProfile(profile.summaryProfile());
        target.setProfileDataJson(writeJson(profile));
        target.setAlgorithmVersion(response.algorithmVersion());
        target.setConfidence(profile.confidence());
        target.setComputedAt(toLocalDateTime(response.evaluatedAt()));
        casOrInsert(target, userId, base, watermark);
        upsertMastery(userId, target.getProfileVersion(), response.knowledgeMastery());
    }

    private void applyInsufficient(ProfileEngineResponse.InsufficientData response, Long userId, long base, long watermark) {
        UserProfile target = target(response, userId, base);
        target.setProfileStatus("NOT_READY");
        target.setStatusReason("INSUFFICIENT_DATA");
        casOrInsert(target, userId, base, watermark);
    }

    private void applyNoChange(Long userId, long base, long watermark) {
        int rows = profileMapper.advanceWatermark(userId, watermark);
        if (rows == 0) {
            UserProfile minimal = new UserProfile();
            minimal.setUserId(userId);
            minimal.setProfileVersion(1L);
            minimal.setProfileStatus("NOT_READY");
            minimal.setStatusReason("NO_CHANGE");
            minimal.setLastProcessedEventId(watermark);
            try {
                profileMapper.insert(minimal);
            } catch (DuplicateKeyException concurrent) {
                throw new ProfileCasConflictException(userId, base);
            }
        }
    }

    private UserProfile target(ProfileEngineResponse response, Long userId, long base) {
        UserProfile target = new UserProfile();
        target.setUserId(userId);
        target.setProfileVersion(response.targetProfileVersion());
        return target;
    }

    private void casOrInsert(UserProfile target, Long userId, long base, long watermark) {
        int rows = profileMapper.casProfile(target, watermark, base);
        if (rows == 0) {
            if (base != 0) throw new ProfileCasConflictException(userId, base);
            try {
                profileMapper.insert(target);
            } catch (DuplicateKeyException concurrent) {
                throw new ProfileCasConflictException(userId, 0);
            }
        }
    }

    private void upsertMastery(Long userId, Long profileVersion, List<ProfileEngineResponse.KnowledgeMastery> rows) {
        if (rows == null) return;
        for (ProfileEngineResponse.KnowledgeMastery row : rows) {
            UserKnowledgeMastery existing = masteryMapper.selectOne(new LambdaQueryWrapper<UserKnowledgeMastery>()
                    .eq(UserKnowledgeMastery::getUserId, userId).eq(UserKnowledgeMastery::getKpId, row.kpId()));
            UserKnowledgeMastery entity = new UserKnowledgeMastery();
            entity.setUserId(userId);
            entity.setKpId(row.kpId());
            entity.setProfileVersion(profileVersion);
            entity.setMasteryScore(row.masteryScore());
            entity.setMasteryStatus(row.masteryStatus());
            entity.setConfidence(row.confidence());
            entity.setEvidenceCount(row.evidenceCount());
            entity.setTrend(row.trend());
            entity.setAlgorithmVersion(row.algorithmVersion());
            entity.setWindowStart(toLocalDateTime(row.windowStart()));
            entity.setWindowEnd(toLocalDateTime(row.windowEnd()));
            entity.setUpdatedAt(row.updatedAt() != null ? toLocalDateTime(row.updatedAt()) : LocalDateTime.now());
            if (existing == null) masteryMapper.insert(entity);
            else {
                entity.setId(existing.getId());
                masteryMapper.updateById(entity);
            }
        }
    }

    private String writeJson(Object value) {
        if (value == null) return null;
        try {
            return mapper.writeValueAsString(value);
        } catch (IOException e) {
            throw new IllegalArgumentException("profile data serialization failed", e);
        }
    }

    private LocalDateTime toLocalDateTime(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
