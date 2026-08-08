package com.treepeople.leapmindtts.service.profile.platform;

import com.treepeople.leapmindtts.exception.M6ApiException;
import com.treepeople.leapmindtts.pojo.dto.profile.M6ProfileDtos.ConversationSummary;
import com.treepeople.leapmindtts.pojo.dto.profile.M6ProfileDtos.ExplainingSummary;
import com.treepeople.leapmindtts.pojo.dto.profile.M6ProfileDtos.FullProfile;
import com.treepeople.leapmindtts.pojo.dto.profile.M6ProfileDtos.NotReadyProfile;
import com.treepeople.leapmindtts.pojo.dto.profile.M6ProfileDtos.KnowledgeStatusResponse;
import com.treepeople.leapmindtts.pojo.dto.profile.M6ProfileDtos.LecturingSummary;
import com.treepeople.leapmindtts.service.profile.impl.ProfileReadCore;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Supplier;

/** Unregistered real reader adapter; policy and explicit readiness are both required for IO. */
public final class PolicyEnforcedProfileContextProvider implements ProfileContextProvider {
    /** Maximum deterministic UTF-8 response representation for an enabled platform read. */
    public static final int MAX_RESPONSE_BYTES = 64 * 1024;
    private static final ObjectMapper RESPONSE_JSON = new ObjectMapper().findAndRegisterModules()
            .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true);
    private final PlatformCapabilityPolicy policy;
    private final PlatformIntegrationReadiness readiness;
    private final ProfileReadCore core;
    private final int responseBudgetBytes;

    public PolicyEnforcedProfileContextProvider(PlatformCapabilityPolicy policy,
                                                PlatformIntegrationReadiness readiness, ProfileReadCore core) {
        this(policy, readiness, core, MAX_RESPONSE_BYTES);
    }

    /** Narrow test seam; production construction always uses {@link #MAX_RESPONSE_BYTES}. */
    public PolicyEnforcedProfileContextProvider(PlatformCapabilityPolicy policy,
                                                PlatformIntegrationReadiness readiness, ProfileReadCore core,
                                                int responseBudgetBytes) {
        this.policy = policy == null ? new DefaultDenyPlatformCapabilityPolicy() : policy;
        this.readiness = readiness == null ? new PlatformIntegrationReadiness() : readiness;
        this.core = java.util.Objects.requireNonNull(core, "core");
        if (responseBudgetBytes < 1 || responseBudgetBytes > MAX_RESPONSE_BYTES) throw new IllegalArgumentException("invalid response byte budget");
        this.responseBudgetBytes = responseBudgetBytes;
    }

    @Override public PracticeProfileContext practice(ProfileAccessContext access, PracticeContextRequest request) {
        Gate gate = gate(access, request, "practice");
        if (!gate.open()) return unavailablePractice(access, gate);
        try {
            var view = core.full(access.subjectUserId());
            if (!(view instanceof FullProfile full)) return unavailablePractice(access, new Gate(ProfileContextStatus.NOT_READY, ((NotReadyProfile) view).statusReason()));
            ProfileContextStatus status = PlatformProfileContextMapper.status(full.profileStatus());
            if (status == ProfileContextStatus.NOT_READY) return unavailablePractice(access, new Gate(status, full.statusReason()));
            List<ProfileKnowledgeContext> all = PlatformProfileContextMapper.knowledge(full.knowledge());
            List<Long> ids = request.knowledgePoints().stream().map(value -> ((KnowledgePointRef.Resolved) value).kpId()).toList();
            return bounded(new PracticeProfileContext(full.userId(), "practice", status, full.statusReason(), full.profileVersion(),
                    full.computedAt(), full.computedAt(), full.confidence(), PlatformProfileContextMapper.availability(status),
                    PlatformProfileContextMapper.select(all, ids), PlatformProfileContextMapper.weak(all, 100), full.learningPace(), full.preferredContentModes()),
                    () -> unavailablePractice(access, degraded()));
        } catch (RuntimeException failure) { return unavailablePractice(access, degraded()); }
    }

    @Override public ExplainingProfileContext explaining(ProfileAccessContext access, ExplainingContextRequest request) {
        Gate gate = gate(access, request, "explaining");
        if (!gate.open()) return unavailableExplaining(access, gate);
        try {
            var result = core.scene(access.subjectUserId(), "explaining", request.knowledgePoint().kpId());
            if (!(result instanceof ExplainingSummary value)) return unavailableExplaining(access, degraded());
            ProfileContextStatus status = PlatformProfileContextMapper.status(value.profileStatus());
            if (status == ProfileContextStatus.NOT_READY) return unavailableExplaining(access, new Gate(status, value.statusReason()));
            return bounded(new ExplainingProfileContext(value.userId(), "explaining", status, value.statusReason(), value.profileVersion(),
                    value.lastUpdated(), value.lastUpdated(), null, PlatformProfileContextMapper.availability(status), value.grade(),
                    PlatformProfileContextMapper.knowledge(value.knowledgeContext()), PlatformProfileContextMapper.confusions(value.recentConfusions(), 3),
                    PlatformProfileContextMapper.style(value.preferredExplanationStyle()), value.learningPace(), null),
                    () -> unavailableExplaining(access, degraded()));
        } catch (RuntimeException failure) { return unavailableExplaining(access, degraded()); }
    }

    @Override public LecturingProfileContext lecturing(ProfileAccessContext access, LecturingContextRequest request) {
        Gate gate = gate(access, request, "lecturing");
        if (!gate.open()) return unavailableLecturing(access, gate);
        try {
            Long kpId = request.knowledgePoint() instanceof KnowledgePointRef.Resolved resolved ? resolved.kpId() : null;
            var result = core.scene(access.subjectUserId(), "lecturing", kpId);
            if (!(result instanceof LecturingSummary value)) return unavailableLecturing(access, degraded());
            ProfileContextStatus status = PlatformProfileContextMapper.status(value.profileStatus());
            if (status == ProfileContextStatus.NOT_READY) return unavailableLecturing(access, new Gate(status, value.statusReason()));
            return bounded(new LecturingProfileContext(value.userId(), "lecturing", status, value.statusReason(), value.profileVersion(),
                    value.lastUpdated(), value.lastUpdated(), null, PlatformProfileContextMapper.availability(status),
                    PlatformProfileContextMapper.knowledge(value.weakKnowledgePoints()), PlatformProfileContextMapper.focus(value.recentFocus(), 3),
                    value.learningPace(), value.preferredContentModes(), null), () -> unavailableLecturing(access, degraded()));
        } catch (RuntimeException failure) { return unavailableLecturing(access, degraded()); }
    }

    @Override public ConversationProfileContext conversation(ProfileAccessContext access, ConversationContextRequest request) {
        Gate gate = gate(access, request, "conversation");
        if (!gate.open()) return unavailableConversation(access, gate);
        try {
            Long kpId = request.knowledgePoint() instanceof KnowledgePointRef.Resolved resolved ? resolved.kpId() : null;
            var result = core.scene(access.subjectUserId(), "conversation", kpId);
            if (!(result instanceof ConversationSummary value)) return unavailableConversation(access, degraded());
            ProfileContextStatus status = PlatformProfileContextMapper.status(value.profileStatus());
            if (status == ProfileContextStatus.NOT_READY) return unavailableConversation(access, new Gate(status, value.statusReason()));
            return bounded(new ConversationProfileContext(value.userId(), "conversation", status, value.statusReason(), value.profileVersion(),
                    value.lastUpdated(), value.lastUpdated(), null, PlatformProfileContextMapper.availability(status),
                    PlatformProfileContextMapper.knowledge(value.knowledgeContext()), PlatformProfileContextMapper.confusions(value.recentConfusions(), 3),
                    PlatformProfileContextMapper.style(value.preferredExplanationStyle()), null), () -> unavailableConversation(access, degraded()));
        } catch (RuntimeException failure) { return unavailableConversation(access, degraded()); }
    }

    @Override public LessonPrepProfileContext lessonPrep(ProfileAccessContext access, LessonPrepContextRequest request) {
        Gate gate = gate(access, request, "lesson_prep");
        if (!gate.open()) return unavailableLessonPrep(access, gate);
        try {
            var view = core.full(access.subjectUserId());
            if (!(view instanceof FullProfile full)) return unavailableLessonPrep(access, new Gate(ProfileContextStatus.NOT_READY, ((NotReadyProfile) view).statusReason()));
            ProfileContextStatus status = PlatformProfileContextMapper.status(full.profileStatus());
            if (status == ProfileContextStatus.NOT_READY) return unavailableLessonPrep(access, new Gate(status, full.statusReason()));
            List<ProfileKnowledgeContext> all = PlatformProfileContextMapper.knowledge(full.knowledge());
            List<ProfileKnowledgeContext> weak = request.knowledgePoints().isEmpty() ? PlatformProfileContextMapper.weak(all, 100)
                    : PlatformProfileContextMapper.select(all, request.knowledgePoints().stream().map(KnowledgePointRef.Resolved::kpId).toList());
            return bounded(new LessonPrepProfileContext(full.userId(), "lesson_prep", status, full.statusReason(), full.profileVersion(),
                    full.computedAt(), full.computedAt(), full.confidence(), PlatformProfileContextMapper.availability(status), weak,
                    full.grade(), full.preferredContentModes(), full.learningPace(), List.of(), null),
                    () -> unavailableLessonPrep(access, degraded()));
        } catch (RuntimeException failure) { return unavailableLessonPrep(access, degraded()); }
    }

    @Override public FullProfileContext getFullProfile(ProfileAccessContext access) {
        Gate gate = gate(access, access, "full_profile");
        if (!gate.open()) return new FullProfileContext(access.subjectUserId(), gate.status(), gate.reason(), ProfileAvailability.UNAVAILABLE, null, null);
        try {
            var view = core.full(access.subjectUserId());
            if (!(view instanceof FullProfile full)) {
                NotReadyProfile notReady = (NotReadyProfile) view;
                return new FullProfileContext(notReady.userId(), ProfileContextStatus.NOT_READY, notReady.statusReason(), ProfileAvailability.UNAVAILABLE, null, null);
            }
            ProfileContextStatus status = PlatformProfileContextMapper.status(full.profileStatus());
            return status == ProfileContextStatus.NOT_READY ? new FullProfileContext(full.userId(), status, full.statusReason(), ProfileAvailability.UNAVAILABLE, null, null)
                    : bounded(new FullProfileContext(full.userId(), status, full.statusReason(), PlatformProfileContextMapper.availability(status), full.profileVersion(), PlatformProfileContextMapper.fullData(full)),
                    () -> new FullProfileContext(access.subjectUserId(), ProfileContextStatus.DEGRADED, "PROFILE_RESPONSE_TOO_LARGE", ProfileAvailability.UNAVAILABLE, null, null));
        } catch (RuntimeException failure) { return new FullProfileContext(access.subjectUserId(), ProfileContextStatus.DEGRADED, "PROFILE_SERVICE_DEGRADED", ProfileAvailability.UNAVAILABLE, null, null); }
    }

    @Override public KnowledgeStatusContext getKnowledgeStatus(ProfileAccessContext access, KnowledgeStatusRequest request) {
        Gate gate = gate(access, request, "knowledge_status");
        if (!gate.open()) return new KnowledgeStatusContext(access.subjectUserId(), gate.status(), gate.reason(), ProfileAvailability.UNAVAILABLE, null, List.of());
        try {
            KnowledgeStatusResponse value = core.knowledge(access.subjectUserId(), request.knowledgePoints().stream().map(KnowledgePointRef.Resolved::kpId).toList());
            ProfileContextStatus status = PlatformProfileContextMapper.status(value.profileStatus());
            return status == ProfileContextStatus.NOT_READY ? new KnowledgeStatusContext(value.userId(), status, value.statusReason(), ProfileAvailability.UNAVAILABLE, null, List.of())
                    : bounded(new KnowledgeStatusContext(value.userId(), status, value.statusReason(), PlatformProfileContextMapper.availability(status), value.profileVersion(), PlatformProfileContextMapper.knowledge(value.knowledge())),
                    () -> new KnowledgeStatusContext(access.subjectUserId(), ProfileContextStatus.DEGRADED, "PROFILE_RESPONSE_TOO_LARGE", ProfileAvailability.UNAVAILABLE, null, List.of()));
        } catch (RuntimeException failure) { return new KnowledgeStatusContext(access.subjectUserId(), ProfileContextStatus.DEGRADED, "PROFILE_SERVICE_DEGRADED", ProfileAvailability.UNAVAILABLE, null, List.of()); }
    }

    private Gate gate(ProfileAccessContext access, Object request, String resource) {
        if (access == null || request == null || access.purpose() != Purpose.READ_SCENE_CONTEXT) throw new IllegalArgumentException("read-scene access and request are required");
        PlatformCapabilityPolicy.CapabilityDecision decision = policy.evaluate(access, PlatformCapabilityPolicy.Capability.READ_PROFILE_CONTEXT, resource);
        if (decision == null || !decision.allowed()) return new Gate(decision != null && "NOT_CONFIGURED".equals(decision.reason()) ? ProfileContextStatus.NOT_CONFIGURED : ProfileContextStatus.DENIED, decision == null ? "ACCESS_DENIED" : decision.reason());
        return readiness.ready() ? new Gate(ProfileContextStatus.READY, null) : new Gate(ProfileContextStatus.NOT_CONNECTED, "NOT_CONNECTED");
    }
    private Gate degraded() { return new Gate(ProfileContextStatus.DEGRADED, "PROFILE_SERVICE_DEGRADED"); }
    private <T> T bounded(T value, Supplier<T> fallback) {
        try {
            return RESPONSE_JSON.writeValueAsString(value).getBytes(StandardCharsets.UTF_8).length <= responseBudgetBytes
                    ? value : fallback.get();
        } catch (Exception ignored) { return fallback.get(); }
    }
    private PracticeProfileContext unavailablePractice(ProfileAccessContext a, Gate g) { return new PracticeProfileContext(a.subjectUserId(), "practice", g.status(), g.reason(), null, null, null, null, ProfileAvailability.UNAVAILABLE, List.of(), List.of(), null, List.of()); }
    private ExplainingProfileContext unavailableExplaining(ProfileAccessContext a, Gate g) { return new ExplainingProfileContext(a.subjectUserId(), "explaining", g.status(), g.reason(), null, null, null, null, ProfileAvailability.UNAVAILABLE, null, null, List.of(), null, null, null); }
    private LecturingProfileContext unavailableLecturing(ProfileAccessContext a, Gate g) { return new LecturingProfileContext(a.subjectUserId(), "lecturing", g.status(), g.reason(), null, null, null, null, ProfileAvailability.UNAVAILABLE, List.of(), List.of(), null, List.of(), null); }
    private ConversationProfileContext unavailableConversation(ProfileAccessContext a, Gate g) { return new ConversationProfileContext(a.subjectUserId(), "conversation", g.status(), g.reason(), null, null, null, null, ProfileAvailability.UNAVAILABLE, null, List.of(), null, null); }
    private LessonPrepProfileContext unavailableLessonPrep(ProfileAccessContext a, Gate g) { return new LessonPrepProfileContext(a.subjectUserId(), "lesson_prep", g.status(), g.reason(), null, null, null, null, ProfileAvailability.UNAVAILABLE, List.of(), null, List.of(), null, List.of(), null); }
    private record Gate(ProfileContextStatus status, String reason) { boolean open() { return status == ProfileContextStatus.READY && reason == null; } }
}
