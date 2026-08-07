package com.treepeople.leapmindtts.service.profile.platform;

import java.math.BigDecimal;

public record ProfileKnowledgeContext(Long kpId, Availability status, BigDecimal masteryScore, String masteryStatus,
                                      BigDecimal confidence, String trend, Long evidenceCount) {
    public ProfileKnowledgeContext {
        if (kpId == null || kpId <= 0 || status == null
                || masteryScore != null && (masteryScore.compareTo(BigDecimal.ZERO) < 0 || masteryScore.compareTo(BigDecimal.ONE) > 0)
                || confidence != null && (confidence.compareTo(BigDecimal.ZERO) < 0 || confidence.compareTo(BigDecimal.ONE) > 0)
                || masteryStatus != null && !java.util.Set.of("WEAK", "CONSOLIDATING", "BASIC_MASTERY", "MASTERED", "INSUFFICIENT_EVIDENCE").contains(masteryStatus)
                || trend != null && !java.util.Set.of("IMPROVING", "STABLE", "DECLINING").contains(trend)
                || evidenceCount != null && (evidenceCount < 0 || evidenceCount > 4_294_967_295L)) throw new IllegalArgumentException("invalid knowledge context");
    }
    public enum Availability { AVAILABLE, INSUFFICIENT_EVIDENCE, EMPTY, NOT_READY }
}
