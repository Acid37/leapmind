package com.treepeople.leapmindtts.service.profile.platform;

/** Typed bulk knowledge-point status read result. */
public record KnowledgeStatusContext(Long userId, ProfileContextStatus status, String reason,
                                     ProfileAvailability availability, Long profileVersion,
                                     java.util.List<ProfileKnowledgeContext> knowledge) {
    public KnowledgeStatusContext {
        ProfileContextFields.readEnvelope(userId, status, reason, availability, knowledge != null && !knowledge.isEmpty());
        if (profileVersion != null && profileVersion < 0) throw new IllegalArgumentException("profileVersion is invalid");
        knowledge = ProfileContextFields.list(knowledge, 100, "knowledge");
        boolean materialized = status == ProfileContextStatus.READY || status == ProfileContextStatus.STALE;
        if (materialized && (profileVersion == null || profileVersion < 1 || knowledge.isEmpty() || availability == ProfileAvailability.UNAVAILABLE))
            throw new IllegalArgumentException("materialized knowledge status requires versioned data");
        if (!materialized && (!knowledge.isEmpty() || profileVersion != null || availability != ProfileAvailability.UNAVAILABLE))
            throw new IllegalArgumentException("unavailable knowledge status must not include facts");
    }
}
