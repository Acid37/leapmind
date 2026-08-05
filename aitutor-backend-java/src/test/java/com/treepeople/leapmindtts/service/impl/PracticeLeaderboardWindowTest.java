package com.treepeople.leapmindtts.service.impl;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PracticeLeaderboardWindowTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 5);

    @Test
    void normalizesSupportedTypesAndKeepsMissingTypeBackwardCompatible() {
        assertEquals("daily", PracticeServiceImpl.normalizeLeaderboardType(" DAILY "));
        assertEquals("weekly", PracticeServiceImpl.normalizeLeaderboardType("weekly"));
        assertEquals("monthly", PracticeServiceImpl.normalizeLeaderboardType("monthly"));
        assertNull(PracticeServiceImpl.normalizeLeaderboardType(null));
        assertNull(PracticeServiceImpl.normalizeLeaderboardType(" "));
    }

    @Test
    void rejectsUnsupportedType() {
        assertThrows(IllegalArgumentException.class,
                () -> PracticeServiceImpl.normalizeLeaderboardType("yearly"));
    }

    @Test
    void calculatesInclusiveDailyWeeklyAndMonthlyWindows() {
        assertEquals(TODAY.atStartOfDay(),
                PracticeServiceImpl.leaderboardStart("daily", TODAY));
        assertEquals(TODAY.minusDays(6).atStartOfDay(),
                PracticeServiceImpl.leaderboardStart("weekly", TODAY));
        assertEquals(TODAY.minusDays(29).atStartOfDay(),
                PracticeServiceImpl.leaderboardStart("monthly", TODAY));
    }
}
