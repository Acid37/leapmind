package com.treepeople.leapmindtts.service.profile.platform;

/** A kp reference is either resolved, explicitly unresolved, or intentionally absent. */
public sealed interface KnowledgePointRef permits KnowledgePointRef.Resolved, KnowledgePointRef.Unresolved, KnowledgePointRef.None {
    record Resolved(Long kpId) implements KnowledgePointRef {
        public Resolved { if (kpId == null || kpId <= 0) throw new IllegalArgumentException("kpId must be positive"); }
    }
    record Unresolved(String stableKey) implements KnowledgePointRef {
        public Unresolved { if (stableKey == null || stableKey.isBlank() || stableKey.codePointCount(0, stableKey.length()) > 128) throw new IllegalArgumentException("stableKey is invalid"); }
    }
    record None() implements KnowledgePointRef { }
    static None none() { return new None(); }
}
