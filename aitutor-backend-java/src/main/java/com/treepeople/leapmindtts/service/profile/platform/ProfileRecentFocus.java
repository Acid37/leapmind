package com.treepeople.leapmindtts.service.profile.platform;

import java.math.BigDecimal;

public record ProfileRecentFocus(Long kpId, BigDecimal weight) {
    public ProfileRecentFocus {
        if (kpId == null || kpId <= 0 || weight == null || !ProfileContextFields.fraction(weight))
            throw new IllegalArgumentException("invalid recent focus");
    }
}
