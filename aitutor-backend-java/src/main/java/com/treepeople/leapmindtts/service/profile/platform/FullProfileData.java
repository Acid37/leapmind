package com.treepeople.leapmindtts.service.profile.platform;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Immutable, typed full-profile data returned only by an enabled profile reader. */
public record FullProfileData(
        String grade,
        List<String> preferredContentModes,
        String preferredExplanationStyle,
        String learningPace,
        List<ProfileRecentFocus> recentFocus,
        List<ProfileRecentConfusion> recentConfusions,
        String summaryProfile,
        BigDecimal confidence,
        String algorithmVersion,
        Instant lastEventAt,
        Instant computedAt,
        List<ProfileKnowledgeContext> knowledge) {
    public FullProfileData {
        if (grade != null && grade.codePointCount(0, grade.length()) > 30
                || confidence != null && !ProfileContextFields.fraction(confidence)
                || algorithmVersion != null && (algorithmVersion.isBlank()
                    || algorithmVersion.codePointCount(0, algorithmVersion.length()) > 30)) {
            throw new IllegalArgumentException("invalid full profile data");
        }
        preferredContentModes = ProfileContextFields.contentModes(preferredContentModes);
        preferredExplanationStyle = ProfileContextFields.explanationStyle(preferredExplanationStyle);
        learningPace = ProfileContextFields.pace(learningPace);
        recentFocus = ProfileContextFields.list(recentFocus, 20, "recentFocus");
        recentConfusions = ProfileContextFields.list(recentConfusions, 20, "recentConfusions");
        knowledge = ProfileContextFields.list(knowledge, 100, "knowledge");
        if (summaryProfile != null && summaryProfile.codePointCount(0, summaryProfile.length()) > 16_383)
            throw new IllegalArgumentException("summaryProfile is too long");
    }
}
