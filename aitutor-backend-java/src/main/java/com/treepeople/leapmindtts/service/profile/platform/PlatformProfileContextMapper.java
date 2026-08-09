package com.treepeople.leapmindtts.service.profile.platform;

import com.treepeople.leapmindtts.pojo.dto.profile.M6ProfileDtos.ConversationSummary;
import com.treepeople.leapmindtts.pojo.dto.profile.M6ProfileDtos.ExplainingSummary;
import com.treepeople.leapmindtts.pojo.dto.profile.M6ProfileDtos.FullProfile;
import com.treepeople.leapmindtts.pojo.dto.profile.M6ProfileDtos.KnowledgeContext;
import com.treepeople.leapmindtts.pojo.dto.profile.M6ProfileDtos.LecturingSummary;
import com.treepeople.leapmindtts.pojo.dto.profile.M6ProfileDtos.RecentConfusion;
import com.treepeople.leapmindtts.pojo.dto.profile.M6ProfileDtos.RecentFocus;
import java.util.Comparator;
import java.util.List;

/** Shared, deterministic M6 DTO-to-platform projection and bounded field clipping. */
final class PlatformProfileContextMapper {
    private PlatformProfileContextMapper() { }

    static ProfileContextStatus status(String value) {
        return "READY".equals(value) ? ProfileContextStatus.READY : "STALE".equals(value)
                ? ProfileContextStatus.STALE : ProfileContextStatus.NOT_READY;
    }
    static ProfileAvailability availability(ProfileContextStatus status) {
        return status == ProfileContextStatus.READY ? ProfileAvailability.AVAILABLE
                : status == ProfileContextStatus.STALE ? ProfileAvailability.PARTIAL : ProfileAvailability.UNAVAILABLE;
    }
    static List<ProfileKnowledgeContext> knowledge(List<KnowledgeContext> values) {
        return values == null ? List.of() : values.stream().map(value -> new ProfileKnowledgeContext(value.kpId(),
                ProfileKnowledgeContext.Availability.valueOf(value.status()), value.masteryScore(), value.masteryStatus(),
                value.confidence(), value.trend(), value.evidenceCount())).toList();
    }
    static List<ProfileRecentFocus> focus(List<RecentFocus> values, int max) {
        return values == null ? List.of() : values.stream().limit(max)
                .map(value -> new ProfileRecentFocus(value.kpId(), value.weight())).toList();
    }
    static List<ProfileRecentConfusion> confusions(List<RecentConfusion> values, int max) {
        return values == null ? List.of() : values.stream().limit(max).map(value -> new ProfileRecentConfusion(value.kpId(),
                value.detail(), value.evidenceCount(), value.confidence(), value.lastOccurredAt())).toList();
    }
    static List<ProfileKnowledgeContext> select(List<ProfileKnowledgeContext> values, List<Long> ids) {
        return ids.stream().map(id -> values.stream().filter(value -> id.equals(value.kpId())).findFirst()
                .orElse(new ProfileKnowledgeContext(id, ProfileKnowledgeContext.Availability.EMPTY, null, null, null, null, 0L))).toList();
    }
    static List<ProfileKnowledgeContext> weak(List<ProfileKnowledgeContext> values, int max) {
        return values.stream().filter(value -> "WEAK".equals(value.masteryStatus()) || "CONSOLIDATING".equals(value.masteryStatus()))
                .sorted(Comparator.comparing(ProfileKnowledgeContext::masteryScore, Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(max).toList();
    }
    static String style(String value) {
        return List.of("step_by_step", "example_first", "concise", "detailed").contains(value) ? value : null;
    }
    static FullProfileData fullData(FullProfile profile) {
        return new FullProfileData(profile.grade(), profile.preferredContentModes(), style(profile.preferredExplanationStyle()),
                profile.learningPace(), focus(profile.recentFocus(), 20), confusions(profile.recentConfusions(), 20),
                profile.summaryProfile(), profile.confidence(), profile.algorithmVersion(), profile.lastEventAt(),
                profile.computedAt(), knowledge(profile.knowledge()));
    }
    static ProfileKnowledgeContext knowledge(KnowledgeContext value) {
        return value == null ? null : knowledge(List.of(value)).get(0);
    }
}
