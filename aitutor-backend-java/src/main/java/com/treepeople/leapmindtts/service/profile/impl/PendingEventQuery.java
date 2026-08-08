package com.treepeople.leapmindtts.service.profile.impl;

import com.treepeople.leapmindtts.mapper.UserEventMapper;
import com.treepeople.leapmindtts.mapper.UserProfileMapper;
import com.treepeople.leapmindtts.pojo.entity.UserEvent;
import com.treepeople.leapmindtts.pojo.entity.UserProfile;
import java.util.List;
import org.springframework.stereotype.Service;

/** Read-only projection window over pending events and the user's profile stamp. */
@Service
public class PendingEventQuery {
    private static final int BATCH_LIMIT = 200;

    private final UserEventMapper eventMapper;
    private final UserProfileMapper profileMapper;

    public PendingEventQuery(UserEventMapper eventMapper, UserProfileMapper profileMapper) {
        this.eventMapper = eventMapper;
        this.profileMapper = profileMapper;
    }

    public List<Long> pendingUserIds() {
        return eventMapper.selectPendingUserIds();
    }

    public List<UserEvent> pendingSince(Long userId, Long afterId, int limit) {
        return eventMapper.selectPendingSince(userId, afterId, limit);
    }

    public UserProfile projectionStamp(Long userId) {
        return profileMapper.selectProjectionStamp(userId);
    }

    public int batchLimit() {
        return BATCH_LIMIT;
    }
}
