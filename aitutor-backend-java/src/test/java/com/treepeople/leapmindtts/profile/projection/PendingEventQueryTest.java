package com.treepeople.leapmindtts.profile.projection;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.treepeople.leapmindtts.mapper.UserEventMapper;
import com.treepeople.leapmindtts.mapper.UserProfileMapper;
import com.treepeople.leapmindtts.pojo.entity.UserEvent;
import com.treepeople.leapmindtts.pojo.entity.UserProfile;
import com.treepeople.leapmindtts.service.profile.impl.PendingEventQuery;
import java.util.List;
import org.junit.jupiter.api.Test;

class PendingEventQueryTest {
    private final UserEventMapper eventMapper = mock(UserEventMapper.class);
    private final UserProfileMapper profileMapper = mock(UserProfileMapper.class);
    private final PendingEventQuery query = new PendingEventQuery(eventMapper, profileMapper);

    @Test void delegatesPendingUserIds() {
        when(eventMapper.selectPendingUserIds()).thenReturn(List.of(1L, 23L, 88L));
        assertEquals(List.of(1L, 23L, 88L), query.pendingUserIds());
        verify(eventMapper).selectPendingUserIds();
    }

    @Test void delegatesPendingSinceWithExactParameters() {
        when(eventMapper.selectPendingSince(23L, 99L, 200)).thenReturn(List.of(new UserEvent()));
        assertEquals(1, query.pendingSince(23L, 99L, 200).size());
        verify(eventMapper).selectPendingSince(23L, 99L, 200);
    }

    @Test void delegatesProjectionStamp() {
        UserProfile stamp = new UserProfile();
        when(profileMapper.selectProjectionStamp(23L)).thenReturn(stamp);
        assertSame(stamp, query.projectionStamp(23L));
        verify(profileMapper).selectProjectionStamp(23L);
    }

    @Test void exposesBatchLimit() {
        assertEquals(200, query.batchLimit());
    }
}
