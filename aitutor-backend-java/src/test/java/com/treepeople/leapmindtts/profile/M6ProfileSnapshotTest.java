package com.treepeople.leapmindtts.profile;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.treepeople.leapmindtts.exception.M6ApiException;
import com.treepeople.leapmindtts.mapper.UserKnowledgeMasteryMapper;
import com.treepeople.leapmindtts.mapper.UserProfileMapper;
import com.treepeople.leapmindtts.pojo.entity.UserKnowledgeMastery;
import com.treepeople.leapmindtts.pojo.entity.UserProfile;
import com.treepeople.leapmindtts.service.profile.impl.ProfileSnapshotReader;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

class M6ProfileSnapshotTest {
    @Test void mixedVersionsAreSanitizedAsServiceDegraded() {
        UserProfileMapper profiles=mock(UserProfileMapper.class);UserKnowledgeMasteryMapper mastery=mock(UserKnowledgeMasteryMapper.class);
        UserProfile profile=visible();when(profiles.selectOne(any())).thenReturn(profile);UserKnowledgeMastery wrong=new UserKnowledgeMastery();wrong.setProfileVersion(2L);when(mastery.selectList(any())).thenReturn(List.of(wrong));
        M6ApiException error=assertThrows(M6ApiException.class,()->new ProfileSnapshotReader(profiles,mastery).read(1L));
        assertEquals("PROFILE_SERVICE_DEGRADED",error.getErrorCode());
    }

    @Test void notReadyDoesNotReadMasteryAndMethodDeclaresRepeatableRead() throws Exception {
        UserProfileMapper profiles=mock(UserProfileMapper.class);UserKnowledgeMasteryMapper mastery=mock(UserKnowledgeMasteryMapper.class);UserProfile p=new UserProfile();p.setProfileVersion(0L);p.setProfileStatus("NOT_READY");when(profiles.selectOne(any())).thenReturn(p);
        assertTrue(new ProfileSnapshotReader(profiles,mastery).read(1L).knowledge().isEmpty());verifyNoInteractions(mastery);
        Transactional tx=ProfileSnapshotReader.class.getMethod("read",Long.class).getAnnotation(Transactional.class);
        assertTrue(tx.readOnly());assertEquals(Isolation.REPEATABLE_READ,tx.isolation());
    }

    private UserProfile visible(){UserProfile p=new UserProfile();p.setProfileVersion(3L);p.setProfileStatus("READY");p.setProfileDataJson("{}");p.setAlgorithmVersion("a");p.setComputedAt(LocalDateTime.now());return p;}
}
