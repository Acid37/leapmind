package com.treepeople.leapmindtts.service.profile.impl;

import com.treepeople.leapmindtts.pojo.dto.profile.M6ProfileDtos.ProfileView;
import com.treepeople.leapmindtts.pojo.dto.profile.M6ProfileDtos.KnowledgeStatusResponse;
import com.treepeople.leapmindtts.pojo.dto.profile.M6ProfileDtos.SummaryView;
import java.util.List;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

/**
 * HTTP-free shared profile read seam.  The existing query implementation remains the sole owner
 * of snapshot, cache, strict decoding and scene assembly; this seam prevents internal adapters
 * from recreating any of those paths while the legacy controller continues to own self-auth.
 */
@Service
public class ProfileReadCore {
    private final UserProfileQueryServiceImpl queries;

    public ProfileReadCore(@Lazy UserProfileQueryServiceImpl queries) { this.queries = queries; }

    public ProfileView full(Long userId) { return queries.readFull(userId); }
    public SummaryView scene(Long userId, String scene, Long kpId) { return queries.readSummary(userId, scene, kpId); }
    public KnowledgeStatusResponse knowledge(Long userId, List<Long> kpIds) { return queries.readKnowledge(userId, kpIds); }
}
