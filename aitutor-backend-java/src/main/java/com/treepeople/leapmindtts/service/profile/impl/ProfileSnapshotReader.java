package com.treepeople.leapmindtts.service.profile.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.treepeople.leapmindtts.exception.M6ApiException;
import com.treepeople.leapmindtts.mapper.UserKnowledgeMasteryMapper;
import com.treepeople.leapmindtts.mapper.UserProfileMapper;
import com.treepeople.leapmindtts.pojo.dto.profile.M6ProfileDtos.ProfileSnapshot;
import com.treepeople.leapmindtts.pojo.entity.UserKnowledgeMastery;
import com.treepeople.leapmindtts.pojo.entity.UserProfile;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProfileSnapshotReader {
    private final UserProfileMapper profiles;
    private final UserKnowledgeMasteryMapper mastery;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ProfileSnapshot read(Long userId) {
        UserProfile profile = profiles.selectOne(new LambdaQueryWrapper<UserProfile>().eq(UserProfile::getUserId, userId));
        if (profile == null) return null;
        if ("NOT_READY".equals(profile.getProfileStatus()) || Long.valueOf(0).equals(profile.getProfileVersion())) {
            return new ProfileSnapshot(profile, List.of());
        }
        if (!"READY".equals(profile.getProfileStatus()) && !"STALE".equals(profile.getProfileStatus())) throw degraded();
        if (profile.getProfileVersion() == null || profile.getProfileVersion() < 1 || profile.getComputedAt() == null
                || profile.getProfileDataJson() == null || profile.getAlgorithmVersion() == null
                || profile.getAlgorithmVersion().isBlank()) throw degraded();
        List<UserKnowledgeMastery> rows = mastery.selectList(new LambdaQueryWrapper<UserKnowledgeMastery>()
                .eq(UserKnowledgeMastery::getUserId, userId)
                .eq(UserKnowledgeMastery::getProfileVersion, profile.getProfileVersion()));
        if (rows.stream().anyMatch(row -> !profile.getProfileVersion().equals(row.getProfileVersion()))) throw degraded();
        return new ProfileSnapshot(profile, List.copyOf(rows));
    }

    private M6ApiException degraded() {
        return new M6ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PROFILE_SERVICE_DEGRADED", "用户画像服务暂不可用");
    }
}
