package com.treepeople.leapmindtts.service.profile.platform;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

final class ProfileContextFields {
    private ProfileContextFields() { }
    static <T> List<T> list(List<T> values) { return list(values, Integer.MAX_VALUE, "values"); }
    static <T> List<T> list(List<T> values, int maximum, String name) {
        List<T> copy = values == null ? List.of() : List.copyOf(values);
        if (copy.size() > maximum || copy.stream().anyMatch(value -> value == null))
            throw new IllegalArgumentException(name + " is invalid");
        return copy;
    }
    static String summary(String value) {
        if (value != null && value.codePointCount(0, value.length()) > 1200)
            throw new IllegalArgumentException("summaryText is too long");
        return value;
    }
    static List<String> suggestions(List<String> values) {
        values = list(values, 20, "teachingSuggestions");
        if (values.stream().anyMatch(value -> value.codePointCount(0, value.length()) > 300))
            throw new IllegalArgumentException("teachingSuggestions are invalid");
        return values;
    }
    static void envelope(Long userId, String scene, String expectedScene, ProfileContextStatus status, String reason,
                         Long version, java.time.Instant computedAt, java.time.Instant lastUpdated,
                         BigDecimal confidence, ProfileAvailability availability, boolean hasFacts) {
        if (userId == null || userId <= 0 || !expectedScene.equals(scene) || status == null || reason != null && reason.isBlank() || availability == null
                || version != null && version < 0 || confidence != null && (confidence.compareTo(BigDecimal.ZERO) < 0 || confidence.compareTo(BigDecimal.ONE) > 0))
            throw new IllegalArgumentException("invalid profile scene context");
        if (reason != null && reason.codePointCount(0, reason.length()) > 120) throw new IllegalArgumentException("statusReason is too long");
        boolean materialized = status == ProfileContextStatus.READY || status == ProfileContextStatus.STALE;
        if (materialized && (availability == ProfileAvailability.UNAVAILABLE || version == null || version < 1 || computedAt == null || lastUpdated == null))
            throw new IllegalArgumentException("materialized context requires version, timestamps and availability");
        if (!materialized && (availability != ProfileAvailability.UNAVAILABLE || version != null || computedAt != null || lastUpdated != null || confidence != null || hasFacts))
            throw new IllegalArgumentException("unavailable context must not include profile facts");
    }
    static List<String> contentModes(List<String> values) {
        values = list(values, 5, "preferredContentModes");
        if (values.stream().anyMatch(v -> !Set.of("text", "image", "audio", "video", "exercise").contains(v)))
            throw new IllegalArgumentException("preferredContentModes are invalid");
        return values;
    }
    static String pace(String value) { if (value != null && !Set.of("slow", "moderate", "fast").contains(value)) throw new IllegalArgumentException("learningPace is invalid"); return value; }
    static String explanationStyle(String value) { if (value != null && !Set.of("step_by_step", "example_first", "concise", "detailed").contains(value)) throw new IllegalArgumentException("preferredExplanationStyle is invalid"); return value; }
    static boolean fraction(BigDecimal value) {
        return value.compareTo(BigDecimal.ZERO) >= 0 && value.compareTo(BigDecimal.ONE) <= 0;
    }
    static void readEnvelope(Long userId, ProfileContextStatus status, String reason,
                             ProfileAvailability availability, boolean hasData) {
        if (userId == null || userId <= 0 || status == null || reason != null && (reason.isBlank() || reason.codePointCount(0, reason.length()) > 120) || availability == null)
            throw new IllegalArgumentException("invalid profile read context");
        if (status == ProfileContextStatus.READY && (!hasData || availability == ProfileAvailability.UNAVAILABLE))
            throw new IllegalArgumentException("READY context requires available data");
    }
}
