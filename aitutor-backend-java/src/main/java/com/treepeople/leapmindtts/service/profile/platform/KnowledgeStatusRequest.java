package com.treepeople.leapmindtts.service.profile.platform;

import java.util.List;

/** Bounded, resolved knowledge-point query for a single profile subject. */
public record KnowledgeStatusRequest(List<KnowledgePointRef.Resolved> knowledgePoints) {
    public KnowledgeStatusRequest {
        knowledgePoints = List.copyOf(knowledgePoints == null ? List.of() : knowledgePoints);
        if (knowledgePoints.isEmpty() || knowledgePoints.size() > 100
                || knowledgePoints.stream().anyMatch(value -> value == null)
                || knowledgePoints.stream().map(KnowledgePointRef.Resolved::kpId).distinct().count() != knowledgePoints.size()) {
            throw new IllegalArgumentException("knowledge points must be 1..100 distinct resolved values");
        }
    }
}
