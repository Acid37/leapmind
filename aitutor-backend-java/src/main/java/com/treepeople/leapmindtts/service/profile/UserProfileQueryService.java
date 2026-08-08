package com.treepeople.leapmindtts.service.profile;

import com.treepeople.leapmindtts.pojo.dto.profile.M6ProfileDtos.KnowledgeStatusResponse;
import com.treepeople.leapmindtts.pojo.dto.profile.M6ProfileDtos.ProfileView;
import com.treepeople.leapmindtts.pojo.dto.profile.M6ProfileDtos.SummaryView;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

public interface UserProfileQueryService {
    ProfileView profile(Long id, HttpServletRequest request);
    SummaryView summary(Long id, String scene, Long kpId, HttpServletRequest request);
    KnowledgeStatusResponse knowledge(Long id, List<Long> kpIds, HttpServletRequest request);
}
