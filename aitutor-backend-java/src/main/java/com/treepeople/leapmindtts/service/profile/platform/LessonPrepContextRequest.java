package com.treepeople.leapmindtts.service.profile.platform;
import java.util.List;
public record LessonPrepContextRequest(List<KnowledgePointRef.Resolved> knowledgePoints) { public LessonPrepContextRequest { if (knowledgePoints == null || knowledgePoints.size() > 100 || knowledgePoints.stream().anyMatch(java.util.Objects::isNull)) throw new IllegalArgumentException("knowledgePoints must be resolved references"); knowledgePoints = List.copyOf(knowledgePoints); } }
