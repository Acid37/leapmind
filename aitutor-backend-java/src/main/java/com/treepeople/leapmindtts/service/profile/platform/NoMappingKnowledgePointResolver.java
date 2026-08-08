package com.treepeople.leapmindtts.service.profile.platform;

/** Default resolver: it never hashes, guesses, casts or persists a kp mapping. */
public final class NoMappingKnowledgePointResolver implements KnowledgePointResolver {
    @Override public KnowledgePointRef resolve(String stableKey) { return new KnowledgePointRef.Unresolved(stableKey); }
}
