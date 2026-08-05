package com.treepeople.leapmindtts.service.profile.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.type.LogicalType;
import com.treepeople.leapmindtts.exception.M6ApiException;
import com.treepeople.leapmindtts.mapper.UserProfileMapper;
import com.treepeople.leapmindtts.pojo.dto.profile.M6ProfileDtos.*;
import com.treepeople.leapmindtts.pojo.entity.UserKnowledgeMastery;
import com.treepeople.leapmindtts.pojo.entity.UserProfile;
import com.treepeople.leapmindtts.service.profile.UserProfileQueryService;
import com.treepeople.leapmindtts.service.profile.cache.M6ProfileCache;
import com.treepeople.leapmindtts.service.profile.security.ProfileActorResolver;
import com.treepeople.leapmindtts.service.profile.summary.SceneSummaryAssembler;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class UserProfileQueryServiceImpl implements UserProfileQueryService {
    private static final long MAX_UNSIGNED_INT = 4294967295L;
    private static final int MAX_SUMMARY_PROFILE_CODE_POINTS = 16383;
    private static final Set<String> CONTENT_MODES = Set.of("text", "image", "audio", "video", "exercise");
    private static final Set<String> LEARNING_PACES = Set.of("slow", "moderate", "fast");
    private static final Set<String> MASTERY_STATUSES = Set.of("WEAK", "CONSOLIDATING", "BASIC_MASTERY", "MASTERED", "INSUFFICIENT_EVIDENCE");
    private static final Set<String> MASTERY_TRENDS = Set.of("IMPROVING", "STABLE", "DECLINING");
    private final ProfileActorResolver actors;
    private final UserProfileMapper profiles;
    private final ProfileSnapshotReader snapshots;
    private final ObjectMapper json;
    private final ObjectMapper cacheJson;
    private final M6ProfileCache cache;
    private final SceneSummaryAssembler summaries;

    public UserProfileQueryServiceImpl(ProfileActorResolver actors, UserProfileMapper profiles,
                                       ProfileSnapshotReader snapshots, ObjectMapper json,
                                       M6ProfileCache cache, SceneSummaryAssembler summaries) {
        this.actors = actors;
        this.profiles = profiles;
        this.snapshots = snapshots;
        this.json = json;
        this.cacheJson = json.copy().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        this.cacheJson.coercionConfigFor(LogicalType.Integer)
                .setCoercion(CoercionInputShape.String, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
        this.cacheJson.coercionConfigFor(LogicalType.Boolean)
                .setCoercion(CoercionInputShape.String, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Float, CoercionAction.Fail);
        this.cacheJson.coercionConfigFor(LogicalType.Float)
                .setCoercion(CoercionInputShape.String, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
        this.cache = cache;
        this.summaries = summaries;
    }

    @Override
    public ProfileView profile(Long userId, HttpServletRequest request) {
        actors.authorizeSelf(request, userId);
        audit(request, userId, userId, "PROFILE_READ", "ALLOWED");
        UserProfile stamp = profiles.selectVersionStamp(userId);
        String key = M6ProfileCache.profileKey(userId);
        if (notReady(stamp)) {
            cache.delete(key);
            return notReadyResponse(userId, stamp);
        }
        validateVisibleStamp(stamp);
        JsonNode cached = cache.get(key, stamp.getProfileVersion(), stamp.getProfileStatus(), stamp.getStatusReason(), stamp(stamp));
        if (cached != null) {
            FullProfile hit = readCache(key, cached, FullProfile.class);
            if (validFullHit(hit, userId, stamp)) {
                return hit;
            }
            cache.delete(key);
        }
        ProfileSnapshot snapshot = requireVisibleSnapshot(userId);
        FullProfile response = full(userId, snapshot);
        UserProfile p = snapshot.profile();
        cache.put(key, p.getProfileVersion(), p.getProfileStatus(), p.getStatusReason(), stamp(p), json.valueToTree(response), false);
        return response;
    }

    @Override
    public SummaryView summary(Long userId, String requestedScene, Long kpId, HttpServletRequest request) {
        if ("lesson_prep".equals(requestedScene)) {
            Long actorId = actors.requireActor(request);
            audit(request, actorId, userId, "SUMMARY_LESSON_PREP", "DENIED");
            throw denied();
        }
        String scene = "photo_qa".equals(requestedScene) ? "explaining" : requestedScene;
        if (!Set.of("explaining", "lecturing", "conversation").contains(scene)
                || ("explaining".equals(scene) && (kpId == null || kpId < 1))
                || (kpId != null && kpId < 1)) {
            throw invalid();
        }
        actors.authorizeSelf(request, userId);
        audit(request, userId, userId, "SUMMARY_" + scene.toUpperCase(java.util.Locale.ROOT), "ALLOWED");
        UserProfile stamp = profiles.selectVersionStamp(userId);
        String key = M6ProfileCache.summaryKey(userId, scene, kpId);
        if (notReady(stamp)) {
            cache.delete(key);
            return notReadyResponse(userId, stamp);
        }
        validateVisibleStamp(stamp);
        JsonNode cached = cache.get(key, stamp.getProfileVersion(), stamp.getProfileStatus(), stamp.getStatusReason(), stamp(stamp));
        if (cached != null) {
            SummaryView hit = readCache(key, cached, summaryType(scene));
            if (validSummaryHit(hit, userId, scene, kpId, stamp)) {
                return hit;
            }
            cache.delete(key);
        }
        ProfileSnapshot snapshot = requireVisibleSnapshot(userId);
        ParsedProfile parsed = parse(snapshot);
        SummaryView response = summaries.assemble(userId, scene, kpId, snapshot, parsed.modes(), parsed.focus(),
                parsed.confusions(), knowledge(snapshot.knowledge()));
        UserProfile p = snapshot.profile();
        cache.put(key, p.getProfileVersion(), p.getProfileStatus(), p.getStatusReason(), stamp(p), json.valueToTree(response), true);
        return response;
    }

    @Override
    public KnowledgeStatusResponse knowledge(Long userId, List<Long> requested, HttpServletRequest request) {
        actors.authorizeSelf(request, userId);
        audit(request, userId, userId, "KNOWLEDGE_READ", "ALLOWED");
        if (requested == null || requested.isEmpty() || requested.size() > 100
                || requested.stream().anyMatch(k -> k == null || k < 1)) {
            throw invalid();
        }
        if (new HashSet<>(requested).size() != requested.size()) {
            throw invalid();
        }
        List<Long> ordered = List.copyOf(requested);
        ProfileSnapshot snapshot = snapshots.read(userId);
        if (snapshot == null || notReady(snapshot.profile())) {
            UserProfile p = snapshot == null ? null : snapshot.profile();
            List<KnowledgeContext> rows = ordered.stream().map(k -> new KnowledgeContext(k, "NOT_READY", null, null, null, null, null)).toList();
            return new KnowledgeStatusResponse(userId, "NOT_READY", p == null ? "NO_PROFILE" : p.getStatusReason(),
                    p == null ? 0L : p.getProfileVersion(), rows);
        }
        List<KnowledgeContext> existing = knowledge(snapshot.knowledge());
        List<KnowledgeContext> rows = ordered.stream().map(k -> existing.stream().filter(v -> k.equals(v.kpId())).findFirst()
                .orElse(new KnowledgeContext(k, "EMPTY", null, null, null, null, 0L))).toList();
        UserProfile p = snapshot.profile();
        return new KnowledgeStatusResponse(userId, p.getProfileStatus(), p.getStatusReason(), p.getProfileVersion(), rows);
    }

    private FullProfile full(Long userId, ProfileSnapshot snapshot) {
        ParsedProfile parsed = parse(snapshot);
        UserProfile p = snapshot.profile();
        FullProfile response = new FullProfile(userId, p.getProfileStatus(), p.getStatusReason(), p.getProfileVersion(), p.getGrade(),
                parsed.modes(), p.getPreferredExplanationStyle(), p.getLearningPace(), parsed.focus(), parsed.confusions(),
                p.getSummaryProfile(), p.getConfidence(), p.getAlgorithmVersion(), instant(p.getLastEventAt()),
                instant(p.getComputedAt()), knowledge(snapshot.knowledge()));
        validateTypedProfileContract(response.preferredContentModes(), response.recentFocus(), response.recentConfusions());
        return response;
    }

    private ParsedProfile parse(ProfileSnapshot snapshot) {
        UserProfile p = snapshot.profile();
        try {
            JsonNode profileData = json.readTree(p.getProfileDataJson());
            if (profileData == null || !profileData.isObject()) throw degraded();
            List<String> modes = parseModes(p.getPreferredContentModesJson());
            List<RecentFocus> focus = parseFocus(p.getRecentFocusJson());
            List<RecentConfusion> confusions = parseConfusions(profileData.get("recentConfusions"));
            validateShort(p.getGrade(), 30);
            validateShort(p.getPreferredExplanationStyle(), 50);
            validateShort(p.getLearningPace(), 20);
            validateLearningPace(p.getLearningPace());
            validateShort(p.getStatusReason(), 64);
            validateTypedOptionalText(p.getSummaryProfile(), MAX_SUMMARY_PROFILE_CODE_POINTS);
            return new ParsedProfile(modes, focus, confusions);
        } catch (M6ApiException e) {
            throw e;
        } catch (Exception e) {
            throw degraded();
        }
    }

    private List<String> parseModes(String raw) throws Exception {
        if (raw == null) {
            return List.of();
        }
        JsonNode node = json.readTree(raw);
        if (!node.isArray() || node.size() > 5) {
            throw degraded();
        }
        List<String> out = new ArrayList<>();
        for (JsonNode item : node) {
            if (!item.isTextual() || !CONTENT_MODES.contains(item.textValue())) {
                throw degraded();
            }
            out.add(item.textValue());
        }
        List<String> modes = List.copyOf(out);
        validateTypedContentModes(modes);
        return modes;
    }

    private List<RecentFocus> parseFocus(String raw) throws Exception {
        if (raw == null) {
            return List.of();
        }
        JsonNode node = json.readTree(raw);
        if (!node.isArray() || node.size() > 20) {
            throw degraded();
        }
        List<RecentFocus> out = new ArrayList<>();
        for (JsonNode item : node) {
            exactKeys(item, Set.of("kpId", "weight"));
            long kpId = positiveLong(item, "kpId");
            BigDecimal weight = decimal(item, "weight", true);
            out.add(new RecentFocus(kpId, weight));
        }
        List<RecentFocus> focus = List.copyOf(out);
        validateTypedRecentFocus(focus);
        return focus;
    }

    private List<RecentConfusion> parseConfusions(JsonNode node) {
        if (node == null || node.isNull()) {
            return List.of();
        }
        if (!node.isArray() || node.size() > 20) {
            throw degraded();
        }
        List<RecentConfusion> out = new ArrayList<>();
        for (JsonNode item : node) {
            exactKeys(item, Set.of("kpId", "detail", "evidenceCount", "confidence", "lastOccurredAt"));
            long kpId = positiveLong(item, "kpId");
            String detail = text(item, "detail", 120);
            long evidenceCount = nonNegativeUnsignedInt(item, "evidenceCount");
            BigDecimal confidence = decimal(item, "confidence", true);
            Instant lastOccurredAt = Instant.parse(text(item, "lastOccurredAt", 64));
            out.add(new RecentConfusion(kpId, detail, evidenceCount, confidence, lastOccurredAt));
        }
        List<RecentConfusion> confusions = List.copyOf(out);
        validateTypedRecentConfusions(confusions);
        return confusions;
    }

    private List<KnowledgeContext> knowledge(List<UserKnowledgeMastery> rows) {
        return rows.stream()
                .sorted(Comparator.comparing(UserKnowledgeMastery::getKpId))
                .map(row -> {
                    if (row.getKpId() == null || row.getKpId() < 1 || row.getProfileVersion() == null || row.getProfileVersion() < 1
                            || row.getMasteryScore() == null || row.getMasteryScore().compareTo(BigDecimal.ZERO) < 0 || row.getMasteryScore().compareTo(BigDecimal.ONE) > 0
                            || row.getConfidence() == null || row.getConfidence().compareTo(BigDecimal.ZERO) < 0 || row.getConfidence().compareTo(BigDecimal.ONE) > 0
                            || row.getEvidenceCount() == null || row.getEvidenceCount() < 0 || row.getEvidenceCount() > MAX_UNSIGNED_INT || row.getMasteryStatus() == null
                            || !MASTERY_STATUSES.contains(row.getMasteryStatus()) || row.getTrend() != null && !MASTERY_TRENDS.contains(row.getTrend())
                            || row.getAlgorithmVersion() == null || row.getAlgorithmVersion().isBlank()
                            || row.getAlgorithmVersion().codePointCount(0, row.getAlgorithmVersion().length()) > 30) {
                        throw degraded();
                    }
                    String availability = "INSUFFICIENT_EVIDENCE".equals(row.getMasteryStatus())
                            ? "INSUFFICIENT_EVIDENCE"
                            : "AVAILABLE";
                    return new KnowledgeContext(row.getKpId(), availability, row.getMasteryScore(),
                            row.getMasteryStatus(), row.getConfidence(), row.getTrend(), row.getEvidenceCount());
                })
                .toList();
    }

    private ProfileSnapshot requireVisibleSnapshot(Long userId) {
        ProfileSnapshot snapshot = snapshots.read(userId);
        if (snapshot == null || notReady(snapshot.profile())) {
            throw degraded();
        }
        return snapshot;
    }

    private boolean notReady(UserProfile profile) {
        return profile == null || "NOT_READY".equals(profile.getProfileStatus())
                || Long.valueOf(0).equals(profile.getProfileVersion());
    }

    private NotReadyProfile notReadyResponse(Long userId, UserProfile profile) {
        return new NotReadyProfile(userId, "NOT_READY", profile == null ? "NO_PROFILE" : profile.getStatusReason(),
                profile == null ? 0L : profile.getProfileVersion());
    }

    private void validateVisibleStamp(UserProfile profile) {
        if ((!"READY".equals(profile.getProfileStatus()) && !"STALE".equals(profile.getProfileStatus()))
                || profile.getProfileVersion() == null || profile.getProfileVersion() < 1
                || profile.getComputedAt() == null) {
            throw degraded();
        }
    }

    private String stamp(UserProfile profile) {
        return instant(profile.getComputedAt()).toString();
    }

    private Instant instant(java.time.LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    private <T> T readCache(String key, JsonNode node, Class<T> type) {
        try {
            return cacheJson.treeToValue(node, type);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean validFullHit(FullProfile hit, Long userId, UserProfile stamp) {
        try {
            return hit != null && userId.equals(hit.userId())
                    && stamp.getProfileVersion().equals(hit.profileVersion())
                    && stamp.getProfileStatus().equals(hit.profileStatus())
                    && java.util.Objects.equals(stamp.getStatusReason(), hit.statusReason())
                    && instant(stamp.getComputedAt()).equals(hit.computedAt())
                    && validTypedFullContract(hit);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private boolean validSummaryHit(SummaryView hit, Long userId, String scene, Long requestedKpId, UserProfile stamp) {
        if (hit instanceof ExplainingSummary summary) {
            return "explaining".equals(scene) && commonHit(summary.userId(), summary.profileVersion(),
                    summary.profileStatus(), summary.statusReason(), summary.lastUpdated(), userId, stamp)
                    && "explaining".equals(summary.sceneType()) && validTypedExplainingContract(summary, requestedKpId);
        }
        if (hit instanceof LecturingSummary summary) {
            return "lecturing".equals(scene) && commonHit(summary.userId(), summary.profileVersion(),
                    summary.profileStatus(), summary.statusReason(), summary.lastUpdated(), userId, stamp)
                    && "lecturing".equals(summary.sceneType()) && validTypedLecturingContract(summary, requestedKpId);
        }
        if (hit instanceof ConversationSummary summary) {
            return "conversation".equals(scene) && commonHit(summary.userId(), summary.profileVersion(),
                    summary.profileStatus(), summary.statusReason(), summary.lastUpdated(), userId, stamp)
                    && "conversation".equals(summary.sceneType()) && validTypedConversationContract(summary, requestedKpId);
        }
        return false;
    }

    private boolean validTypedProfileContract(List<String> modes, List<RecentFocus> focus,
                                              List<RecentConfusion> confusions) {
        try {
            validateTypedProfileContract(modes, focus, confusions);
            return true;
        } catch (M6ApiException ignored) {
            return false;
        }
    }

    private boolean validTypedFullContract(FullProfile hit) {
        try {
            validateTypedProfileContract(hit.preferredContentModes(), hit.recentFocus(), hit.recentConfusions());
            validateTypedOptionalFields(hit.statusReason(), 64, hit.grade(), 30, hit.preferredExplanationStyle(), 50,
                    hit.learningPace(), 20);
            validateTypedOptionalText(hit.summaryProfile(), MAX_SUMMARY_PROFILE_CODE_POINTS);
            if (hit.algorithmVersion() == null || hit.algorithmVersion().isBlank()
                    || hit.algorithmVersion().codePointCount(0, hit.algorithmVersion().length()) > 30 || hit.confidence() != null
                    && (hit.confidence().compareTo(BigDecimal.ZERO) < 0 || hit.confidence().compareTo(BigDecimal.ONE) > 0)) {
                throw degraded();
            }
            validateTypedKnowledge(hit.knowledge(), Integer.MAX_VALUE);
            return true;
        } catch (M6ApiException ignored) {
            return false;
        }
    }

    private boolean validTypedExplainingContract(ExplainingSummary summary, Long requestedKpId) {
        try {
            validateTypedOptionalFields(summary.statusReason(), 64, summary.grade(), 30,
                    summary.preferredExplanationStyle(), 50, summary.learningPace(), 20);
            validateTypedContentModes(summary.preferredContentModes());
            validateTypedRecentConfusions(summary.recentConfusions(), 3);
            validateSummaryKnowledgeContext("explaining", requestedKpId, summary.knowledgeContext());
            return true;
        } catch (M6ApiException ignored) {
            return false;
        }
    }

    private boolean validTypedLecturingContract(LecturingSummary summary, Long requestedKpId) {
        try {
            validateTypedOptionalFields(summary.statusReason(), 64, summary.grade(), 30, null, 0,
                    summary.learningPace(), 20);
            validateTypedContentModes(summary.preferredContentModes());
            validateTypedRecentFocus(summary.recentFocus(), 3);
            validateTypedKnowledge(summary.weakKnowledgePoints(), 5);
            validateSummaryKnowledgeContext("lecturing", requestedKpId, summary.knowledgeContext());
            return true;
        } catch (M6ApiException ignored) {
            return false;
        }
    }

    private boolean validTypedConversationContract(ConversationSummary summary, Long requestedKpId) {
        try {
            validateTypedOptionalFields(summary.statusReason(), 64, null, 0,
                    summary.preferredExplanationStyle(), 50, null, 0);
            validateTypedContentModes(summary.preferredContentModes());
            validateTypedRecentConfusions(summary.recentConfusions(), 3);
            validateSummaryKnowledgeContext("conversation", requestedKpId, summary.knowledgeContext());
            return true;
        } catch (M6ApiException ignored) {
            return false;
        }
    }

    private void validateTypedProfileContract(List<String> modes, List<RecentFocus> focus,
                                              List<RecentConfusion> confusions) {
        validateTypedContentModes(modes);
        validateTypedRecentFocus(focus);
        validateTypedRecentConfusions(confusions);
    }

    private void validateTypedContentModes(List<String> modes) {
        if (modes == null || modes.size() > 5 || modes.stream().anyMatch(mode -> mode == null || !CONTENT_MODES.contains(mode))) {
            throw degraded();
        }
    }

    private void validateTypedRecentFocus(List<RecentFocus> focus) {
        validateTypedRecentFocus(focus, 20);
    }

    private void validateTypedRecentFocus(List<RecentFocus> focus, int maxItems) {
        if (focus == null || focus.size() > maxItems) {
            throw degraded();
        }
        for (RecentFocus item : focus) {
            if (item == null || item.kpId() == null || item.kpId() < 1 || item.weight() == null
                    || item.weight().compareTo(BigDecimal.ZERO) < 0 || item.weight().compareTo(BigDecimal.ONE) > 0) {
                throw degraded();
            }
        }
    }

    private void validateTypedRecentConfusions(List<RecentConfusion> confusions) {
        validateTypedRecentConfusions(confusions, 20);
    }

    private void validateTypedRecentConfusions(List<RecentConfusion> confusions, int maxItems) {
        if (confusions == null || confusions.size() > maxItems) {
            throw degraded();
        }
        for (RecentConfusion item : confusions) {
            if (item == null || item.kpId() == null || item.kpId() < 1 || item.detail() == null
                    || item.detail().codePointCount(0, item.detail().length()) > 120 || item.evidenceCount() == null
                    || item.evidenceCount() < 0 || item.evidenceCount() > MAX_UNSIGNED_INT || item.confidence() == null || item.confidence().compareTo(BigDecimal.ZERO) < 0
                    || item.confidence().compareTo(BigDecimal.ONE) > 0 || item.lastOccurredAt() == null) {
                throw degraded();
            }
        }
    }

    private void validateTypedOptionalFields(String statusReason, int statusReasonMax, String grade, int gradeMax,
                                             String explanationStyle, int explanationStyleMax, String learningPace, int learningPaceMax) {
        validateTypedOptionalText(statusReason, statusReasonMax);
        validateTypedOptionalText(grade, gradeMax);
        validateTypedOptionalText(explanationStyle, explanationStyleMax);
        validateTypedOptionalText(learningPace, learningPaceMax);
        validateLearningPace(learningPace);
    }

    private void validateTypedOptionalText(String value, int maxCodePoints) {
        if (value != null && value.codePointCount(0, value.length()) > maxCodePoints) {
            throw degraded();
        }
    }

    private void validateLearningPace(String value) {
        if (value != null && !LEARNING_PACES.contains(value)) {
            throw degraded();
        }
    }

    private void validateTypedKnowledge(List<KnowledgeContext> knowledge, int maxItems) {
        if (knowledge == null || knowledge.size() > maxItems) {
            throw degraded();
        }
        for (KnowledgeContext item : knowledge) {
            validateTypedKnowledgeContext(item);
        }
    }

    private void validateTypedKnowledgeContext(KnowledgeContext item) {
        if (item == null) {
            throw degraded();
        }
        if (item.kpId() == null || item.status() == null || !Set.of("AVAILABLE", "INSUFFICIENT_EVIDENCE", "EMPTY", "NOT_READY").contains(item.status())
                || item.evidenceCount() == null || item.kpId() < 1
                || item.masteryScore() != null && (item.masteryScore().compareTo(BigDecimal.ZERO) < 0 || item.masteryScore().compareTo(BigDecimal.ONE) > 0)
                || item.confidence() != null && (item.confidence().compareTo(BigDecimal.ZERO) < 0 || item.confidence().compareTo(BigDecimal.ONE) > 0)
                || item.evidenceCount() < 0 || item.evidenceCount() > MAX_UNSIGNED_INT) {
            throw degraded();
        }
        if (item.masteryStatus() != null && !MASTERY_STATUSES.contains(item.masteryStatus())
                || item.trend() != null && !MASTERY_TRENDS.contains(item.trend())) {
            throw degraded();
        }
    }

    private void validateTypedOptionalKnowledgeContext(KnowledgeContext item) {
        if (item != null) {
            validateTypedKnowledgeContext(item);
        }
    }

    private void validateSummaryKnowledgeContext(String scene, Long requestedKpId, KnowledgeContext context) {
        if ("explaining".equals(scene) && (requestedKpId == null || context == null || !requestedKpId.equals(context.kpId()))) {
            throw degraded();
        }
        if (("lecturing".equals(scene) || "conversation".equals(scene)) && requestedKpId != null
                && (context == null || !requestedKpId.equals(context.kpId()))) {
            throw degraded();
        }
        validateTypedOptionalKnowledgeContext(context);
    }

    private boolean commonHit(Long cachedUserId, Long version, String status, String reason, Instant updated,
                              Long userId, UserProfile stamp) {
        return userId.equals(cachedUserId)
                && stamp.getProfileVersion().equals(version)
                && stamp.getProfileStatus().equals(status)
                && java.util.Objects.equals(stamp.getStatusReason(), reason)
                && instant(stamp.getComputedAt()).equals(updated);
    }

    private Class<? extends SummaryView> summaryType(String scene) {
        return switch (scene) {
            case "explaining" -> ExplainingSummary.class;
            case "lecturing" -> LecturingSummary.class;
            case "conversation" -> ConversationSummary.class;
            default -> throw invalid();
        };
    }

    private void exactKeys(JsonNode node, Set<String> allowed) {
        if (!node.isObject()) {
            throw degraded();
        }
        Set<String> names = new HashSet<>();
        node.fieldNames().forEachRemaining(names::add);
        if (!allowed.equals(names)) {
            throw degraded();
        }
    }

    private long positiveLong(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() < 1) {
            throw degraded();
        }
        return value.longValue();
    }

    private long nonNegativeUnsignedInt(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong()
                || value.longValue() < 0 || value.longValue() > MAX_UNSIGNED_INT) {
            throw degraded();
        }
        return value.longValue();
    }

    private BigDecimal decimal(JsonNode node, String field, boolean required) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            if (required) {
                throw degraded();
            }
            return null;
        }
        if (!value.isNumber()) {
            throw degraded();
        }
        BigDecimal decimal = value.decimalValue();
        if (decimal.compareTo(BigDecimal.ZERO) < 0 || decimal.compareTo(BigDecimal.ONE) > 0) {
            throw degraded();
        }
        return decimal;
    }

    private String text(JsonNode node, String field, int maxCodePoints) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual()
                || value.textValue().codePointCount(0, value.textValue().length()) > maxCodePoints) {
            throw degraded();
        }
        return value.textValue();
    }

    private void validateShort(String value, int maxCodePoints) {
        if (value != null && value.codePointCount(0, value.length()) > maxCodePoints) {
            throw degraded();
        }
    }

    private M6ApiException invalid() {
        return new M6ApiException(HttpStatus.BAD_REQUEST, "PROFILE_EVENT_INVALID", "请求参数无效");
    }

    private M6ApiException denied() {
        return new M6ApiException(HttpStatus.FORBIDDEN, "PROFILE_ACCESS_DENIED", "无权访问");
    }

    private M6ApiException degraded() {
        return new M6ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PROFILE_SERVICE_DEGRADED", "用户画像服务暂不可用");
    }

    private void audit(HttpServletRequest request, Long actorId, Long targetId, String operation, String result) {
        log.info("M6 profile access requestId={} actorId={} targetId={} operation={} status={}",
                com.treepeople.leapmindtts.util.M6RequestIds.resolveOrCreate(request), actorId, targetId,
                operation, result);
    }

    private record ParsedProfile(List<String> modes,List<RecentFocus> focus,List<RecentConfusion> confusions) { }
}
