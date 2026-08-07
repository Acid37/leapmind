package com.treepeople.leapmindtts.service.profile.platform;
import java.util.List;
public record PracticeContextRequest(List<KnowledgePointRef> knowledgePoints) {
    public PracticeContextRequest {
        if (knowledgePoints == null || knowledgePoints.isEmpty() || knowledgePoints.size() > 100) throw new IllegalArgumentException("knowledgePoints must contain 1 to 100 values");
        knowledgePoints = List.copyOf(knowledgePoints);
        if (knowledgePoints.stream().anyMatch(value -> !(value instanceof KnowledgePointRef.Resolved)) || new java.util.HashSet<>(knowledgePoints).size() != knowledgePoints.size()) throw new IllegalArgumentException("knowledgePoints must be distinct resolved references");
    }
}
