package com.treepeople.leapmindtts.pojo.dto.profile;

import com.treepeople.leapmindtts.pojo.entity.UserKnowledgeMastery;
import com.treepeople.leapmindtts.pojo.entity.UserProfile;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class M6ProfileDtos {
    private M6ProfileDtos() { }
    public record ProfileSnapshot(UserProfile profile, List<UserKnowledgeMastery> knowledge) { }
    public sealed interface ProfileView permits FullProfile, NotReadyProfile { }
    public sealed interface SummaryView permits ExplainingSummary, LecturingSummary, ConversationSummary, NotReadyProfile { }
    public record NotReadyProfile(Long userId, String profileStatus, String statusReason, Long profileVersion)
            implements ProfileView, SummaryView { }
    public record RecentFocus(Long kpId, BigDecimal weight) { }
    public record RecentConfusion(Long kpId, String detail, Long evidenceCount, BigDecimal confidence, Instant lastOccurredAt) { }
    public record KnowledgeContext(Long kpId, String status, BigDecimal masteryScore, String masteryStatus,
                                   BigDecimal confidence, String trend, Long evidenceCount) { }
    public record FullProfile(Long userId, String profileStatus, String statusReason, Long profileVersion,
                              String grade, List<String> preferredContentModes, String preferredExplanationStyle,
                              String learningPace, List<RecentFocus> recentFocus, List<RecentConfusion> recentConfusions,
                              String summaryProfile, BigDecimal confidence, String algorithmVersion,
                              Instant lastEventAt, Instant computedAt, List<KnowledgeContext> knowledge) implements ProfileView { }
    public record ExplainingSummary(Long userId, String sceneType, String profileStatus, String statusReason,
                                    Long profileVersion, Instant lastUpdated, String grade,
                                    KnowledgeContext knowledgeContext, List<RecentConfusion> recentConfusions,
                                    List<String> preferredContentModes, String preferredExplanationStyle,
                                    String learningPace) implements SummaryView { }
    public record LecturingSummary(Long userId, String sceneType, String profileStatus, String statusReason,
                                   Long profileVersion, Instant lastUpdated, String grade,
                                   KnowledgeContext knowledgeContext, List<KnowledgeContext> weakKnowledgePoints,
                                   List<RecentFocus> recentFocus, List<String> preferredContentModes,
                                   String learningPace) implements SummaryView { }
    public record ConversationSummary(Long userId, String sceneType, String profileStatus, String statusReason,
                                      Long profileVersion, Instant lastUpdated, KnowledgeContext knowledgeContext,
                                      List<RecentConfusion> recentConfusions, List<String> preferredContentModes,
                                      String preferredExplanationStyle) implements SummaryView { }
    public record KnowledgeStatusResponse(Long userId, String profileStatus, String statusReason,
                                          Long profileVersion, List<KnowledgeContext> knowledge) { }
}
