package com.treepeople.leapmindtts.service.profile.engine;

import java.math.BigDecimal;
import java.util.Set;

/** Cross-field validator for values a strict JSON parser cannot express. */
public final class ProfileEngineContractValidator {
    private ProfileEngineContractValidator() { }
    public static void validate(ProfileEngineRequest request, ProfileEngineResponse response) {
        if (request == null || response == null) throw invalid();
        if (!"1.0".equals(response.contractVersion()) || !request.requestId().equals(response.requestId())
                || !request.userId().equals(response.userId()) || !request.baseProfileVersion().equals(response.baseProfileVersion())
                || !request.eventWatermarkInclusive().equals(response.eventWatermarkInclusive())
                || !algorithmVersion(response.algorithmVersion()) || response.evaluatedAt() == null) throw invalid();
        if (response instanceof ProfileEngineResponse.Ready ready) {
            if (ready.status() != ProfileEngineResponse.EngineStatus.READY || ready.profile() == null || ready.knowledgeMastery() == null
                    || ready.profile().preferredContentModes() == null || ready.profile().recentFocus() == null
                    || ready.profile().recentConfusions() == null || !profile(ready.profile()) || !mastery(ready.knowledgeMastery())
                    || !incremented(request, ready)) throw invalid();
        } else if (response instanceof ProfileEngineResponse.InsufficientData insufficient) {
            if (insufficient.status() != ProfileEngineResponse.EngineStatus.INSUFFICIENT_DATA || insufficient.knowledgeMastery() == null
                    || !insufficient.knowledgeMastery().isEmpty() || !incremented(request, insufficient)) throw invalid();
        } else if (response instanceof ProfileEngineResponse.NoChange noChange) {
            if (noChange.status() != ProfileEngineResponse.EngineStatus.NO_CHANGE || !request.baseProfileVersion().equals(noChange.targetProfileVersion())) throw invalid();
        } else throw invalid();
    }
    private static boolean incremented(ProfileEngineRequest request, ProfileEngineResponse response) {
        return request.baseProfileVersion() != Long.MAX_VALUE && response.targetProfileVersion() != null
                && response.targetProfileVersion().equals(request.baseProfileVersion() + 1);
    }
    private static boolean profile(ProfileEngineResponse.EngineProfile profile) {
        if (profile.preferredContentModes().size() > 5 || profile.recentFocus().size() > 20 || profile.recentConfusions().size() > 20
                || !nullableLength(profile.grade(), 30) || !nullableLength(profile.preferredExplanationStyle(), 50)
                || !nullableLength(profile.summaryProfile(), 16383) || !nullablePrecision(profile.confidence(), 3)
                || (profile.learningPace() != null && !Set.of("slow", "moderate", "fast").contains(profile.learningPace()))
                || profile.preferredContentModes().stream().anyMatch(value -> !Set.of("text", "image", "audio", "video", "exercise").contains(value))) return false;
        return profile.recentFocus().stream().allMatch(ProfileEngineContractValidator::focus)
                && profile.recentConfusions().stream().allMatch(ProfileEngineContractValidator::confusion);
    }
    private static boolean focus(ProfileEngineResponse.RecentFocus value) { return value != null && positive(value.kpId()) && requiredRange(value.weight()); }
    private static boolean confusion(ProfileEngineResponse.RecentConfusion value) {
        return value != null && positive(value.kpId()) && value.detail() != null && value.detail().codePointCount(0, value.detail().length()) <= 120
                && nonNegative(value.evidenceCount()) && requiredRange(value.confidence()) && value.lastOccurredAt() != null;
    }
    private static boolean mastery(java.util.List<ProfileEngineResponse.KnowledgeMastery> values) {
        return values.stream().allMatch(value -> value != null && positive(value.kpId()) && requiredFraction(value.masteryScore())
                && Set.of("WEAK", "CONSOLIDATING", "BASIC_MASTERY", "MASTERED", "INSUFFICIENT_EVIDENCE").contains(value.masteryStatus())
                && requiredFraction(value.confidence()) && nonNegative(value.evidenceCount()) && algorithmVersion(value.algorithmVersion())
                && value.updatedAt() != null && validWindow(value)
                && (value.trend() == null || Set.of("IMPROVING", "STABLE", "DECLINING").contains(value.trend())))
                && values.stream().map(ProfileEngineResponse.KnowledgeMastery::kpId).distinct().count() == values.size();
    }
    private static boolean validWindow(ProfileEngineResponse.KnowledgeMastery value) {
        return value.windowStart() == null || value.windowEnd() == null || !value.windowStart().isAfter(value.windowEnd());
    }
    private static boolean positive(Long value) { return value != null && value > 0; }
    private static boolean nonNegative(Long value) { return value != null && value >= 0 && value <= 4_294_967_295L; }
    private static boolean fraction(BigDecimal value) { return value == null || (value.compareTo(BigDecimal.ZERO) >= 0 && value.compareTo(BigDecimal.ONE) <= 0); }
    private static boolean requiredFraction(BigDecimal value) { return value != null && precision(value, 4); }
    private static boolean requiredRange(BigDecimal value) { return value != null && fraction(value); }
    private static boolean precision(BigDecimal value, int digits) { return fraction(value) && value.stripTrailingZeros().scale() <= digits; }
    private static boolean nullablePrecision(BigDecimal value, int digits) { return value == null || precision(value, digits); }
    private static boolean nullableLength(String value, int maximum) { return value == null || value.codePointCount(0, value.length()) <= maximum; }
    private static boolean algorithmVersion(String value) { return value != null && !value.isBlank() && value.codePointCount(0, value.length()) <= 30; }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("PROFILE_ENGINE_CONTRACT_INVALID"); }
}
