package com.treepeople.leapmindtts.service.profile.platform;

import java.util.List;

/** Production default: no cache, database, or network collaborator is present. */
public final class DisabledProfileContextProvider implements ProfileContextProvider {
    private static final ProfileAvailability UNAVAILABLE = ProfileAvailability.UNAVAILABLE;
    private final PlatformCapabilityPolicy policy;

    public DisabledProfileContextProvider(PlatformCapabilityPolicy policy) {
        this.policy = policy == null ? new DefaultDenyPlatformCapabilityPolicy() : policy;
    }

    @Override public PracticeProfileContext practice(ProfileAccessContext access, PracticeContextRequest request) {
        Decision decision = decision(access, request, "practice");
        return new PracticeProfileContext(access.subjectUserId(), "practice", decision.status(), decision.reason(), null, null, null, null, UNAVAILABLE, List.of(), List.of(), null, List.of());
    }
    @Override public ExplainingProfileContext explaining(ProfileAccessContext access, ExplainingContextRequest request) {
        Decision decision = decision(access, request, "explaining");
        return new ExplainingProfileContext(access.subjectUserId(), "explaining", decision.status(), decision.reason(), null, null, null, null, UNAVAILABLE, null, null, List.of(), null, null, null);
    }
    @Override public LecturingProfileContext lecturing(ProfileAccessContext access, LecturingContextRequest request) {
        Decision decision = decision(access, request, "lecturing");
        return new LecturingProfileContext(access.subjectUserId(), "lecturing", decision.status(), decision.reason(), null, null, null, null, UNAVAILABLE, List.of(), List.of(), null, List.of(), null);
    }
    @Override public ConversationProfileContext conversation(ProfileAccessContext access, ConversationContextRequest request) {
        Decision decision = decision(access, request, "conversation");
        return new ConversationProfileContext(access.subjectUserId(), "conversation", decision.status(), decision.reason(), null, null, null, null, UNAVAILABLE, null, List.of(), null, null);
    }
    @Override public LessonPrepProfileContext lessonPrep(ProfileAccessContext access, LessonPrepContextRequest request) {
        Decision decision = decision(access, request, "lesson_prep");
        return new LessonPrepProfileContext(access.subjectUserId(), "lesson_prep", decision.status(), decision.reason(), null, null, null, null, UNAVAILABLE, List.of(), null, List.of(), null, List.of(), null);
    }
    @Override public FullProfileContext getFullProfile(ProfileAccessContext access) {
        Decision decision = decision(access, access, "full_profile");
        return new FullProfileContext(access.subjectUserId(), decision.status(), decision.reason(), UNAVAILABLE, null, null);
    }
    @Override public KnowledgeStatusContext getKnowledgeStatus(ProfileAccessContext access, KnowledgeStatusRequest request) {
        Decision decision = decision(access, request, "knowledge_status");
        return new KnowledgeStatusContext(access.subjectUserId(), decision.status(), decision.reason(), UNAVAILABLE, null, List.of());
    }
    private Decision decision(ProfileAccessContext access, Object request, String resource) {
        if (access == null || request == null || access.purpose() != Purpose.READ_SCENE_CONTEXT)
            throw new IllegalArgumentException("read-scene access and request are required");
        PlatformCapabilityPolicy.CapabilityDecision result = policy.evaluate(access, PlatformCapabilityPolicy.Capability.READ_PROFILE_CONTEXT, resource);
        if (result != null && result.allowed()) return new Decision(ProfileContextStatus.NOT_CONNECTED, "NOT_CONNECTED");
        if (result != null && "NOT_CONFIGURED".equals(result.reason()))
            return new Decision(ProfileContextStatus.NOT_CONFIGURED, result.reason());
        return new Decision(ProfileContextStatus.DENIED, result == null ? "ACCESS_DENIED" : result.reason());
    }
    private record Decision(ProfileContextStatus status, String reason) { }
}
