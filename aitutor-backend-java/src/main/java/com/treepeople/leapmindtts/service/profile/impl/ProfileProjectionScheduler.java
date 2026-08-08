package com.treepeople.leapmindtts.service.profile.impl;

import com.treepeople.leapmindtts.pojo.entity.UserEvent;
import com.treepeople.leapmindtts.pojo.entity.UserProfile;
import com.treepeople.leapmindtts.service.profile.engine.ProfileEngineEvent;
import com.treepeople.leapmindtts.service.profile.engine.ProfileEnginePort;
import com.treepeople.leapmindtts.service.profile.engine.ProfileEngineRequest;
import com.treepeople.leapmindtts.service.profile.engine.ProfileEngineResponse;
import com.treepeople.leapmindtts.service.profile.engine.ProfileEngineUnavailableException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Polls pending events per user and writes the engine response back via CAS.
 * Disabled by default; enable with m6.profile-engine.projection-enabled=true.
 */
@Component
@ConditionalOnProperty(prefix = "m6.profile-engine", name = "projection-enabled", havingValue = "true", matchIfMissing = false)
public class ProfileProjectionScheduler {
    private static final Logger log = LoggerFactory.getLogger(ProfileProjectionScheduler.class);
    private static final String CONTRACT_VERSION = "1.0";

    private final PendingEventQuery query;
    private final UserEventCommandConverter converter;
    private final ProfileEnginePort engine;
    private final ProfileCasWriter writer;

    public ProfileProjectionScheduler(PendingEventQuery query, UserEventCommandConverter converter,
                                      ProfileEnginePort engine, ProfileCasWriter writer) {
        this.query = query;
        this.converter = converter;
        this.engine = engine;
        this.writer = writer;
    }

    @Scheduled(fixedDelayString = "${m6.profile-engine.projection-interval-ms:30000}")
    public void scan() {
        for (Long userId : query.pendingUserIds()) {
            try {
                processUser(userId);
            } catch (ProfileEngineUnavailableException unavailable) {
                log.warn("profile engine unavailable for user {}; projection skipped", userId);
            } catch (ProfileCasConflictException conflict) {
                log.warn("{}; retry next scan", conflict.getMessage());
            } catch (RuntimeException unexpected) {
                log.error("profile projection failed for user {}", userId, unexpected);
            }
        }
    }

    private void processUser(Long userId) {
        UserProfile stamp = query.projectionStamp(userId);
        long base = stamp == null || stamp.getProfileVersion() == null ? 0L : stamp.getProfileVersion();
        long watermark = stamp == null || stamp.getLastProcessedEventId() == null ? 0L : stamp.getLastProcessedEventId();
        List<UserEvent> batch = query.pendingSince(userId, watermark, query.batchLimit());
        if (batch.isEmpty()) return;
        List<UserEvent> processable = new ArrayList<>();
        List<ProfileEngineEvent> engineEvents = new ArrayList<>();
        for (UserEvent event : batch) {
            try {
                engineEvents.add(new ProfileEngineEvent(event.getId(), converter.convert(event)));
                processable.add(event);
            } catch (IllegalArgumentException invalid) {
                writer.markFailed(event.getId());
                log.warn("event {} of user {} cannot be projected; marked FAILED", event.getId(), userId);
            }
        }
        if (engineEvents.isEmpty()) return;
        long watermarkInclusive = engineEvents.get(engineEvents.size() - 1).dbEventId();
        ProfileEngineRequest request = new ProfileEngineRequest(CONTRACT_VERSION, UUID.randomUUID(), userId,
                ProfileEngineRequest.Mode.INCREMENTAL, base, watermark, watermarkInclusive, engineEvents);
        ProfileEngineResponse response = engine.buildProfile(request);
        writer.apply(response, stamp, processable);
    }
}
