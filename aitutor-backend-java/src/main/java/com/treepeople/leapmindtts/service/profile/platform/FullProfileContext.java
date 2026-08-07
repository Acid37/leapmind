package com.treepeople.leapmindtts.service.profile.platform;

/** Typed full-profile read result. Disabled implementations return no profile data. */
public record FullProfileContext(Long userId, ProfileContextStatus status, String reason,
                                 ProfileAvailability availability, Long profileVersion, FullProfileData profile) {
    public FullProfileContext {
        ProfileContextFields.readEnvelope(userId, status, reason, availability, profile != null);
        if (profileVersion != null && profileVersion < 0) throw new IllegalArgumentException("profileVersion is invalid");
        boolean materialized = status == ProfileContextStatus.READY || status == ProfileContextStatus.STALE;
        if (materialized && (profile == null || profileVersion == null || profileVersion < 1))
            throw new IllegalArgumentException("materialized full profile requires versioned data");
        if (materialized && availability == ProfileAvailability.UNAVAILABLE)
            throw new IllegalArgumentException("materialized full profile requires availability");
        if (!materialized && (profile != null || profileVersion != null || availability != ProfileAvailability.UNAVAILABLE))
            throw new IllegalArgumentException("unavailable full profile must not include facts");
    }
}
