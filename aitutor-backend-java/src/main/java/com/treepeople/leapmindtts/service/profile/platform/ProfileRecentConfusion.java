package com.treepeople.leapmindtts.service.profile.platform;

import java.math.BigDecimal;
import java.time.Instant;

public record ProfileRecentConfusion(Long kpId, String detail, Long evidenceCount,
                                     BigDecimal confidence, Instant lastOccurredAt) {
    public ProfileRecentConfusion {
        if (kpId == null || kpId <= 0 || detail == null || detail.isBlank()
                || detail.codePointCount(0, detail.length()) > 120 || evidenceCount == null
                || evidenceCount < 0 || evidenceCount > 4_294_967_295L || confidence == null
                || !ProfileContextFields.fraction(confidence) || lastOccurredAt == null) {
            throw new IllegalArgumentException("invalid recent confusion");
        }
    }
}
