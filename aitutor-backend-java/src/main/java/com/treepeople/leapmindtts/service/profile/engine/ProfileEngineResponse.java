package com.treepeople.leapmindtts.service.profile.engine;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "status", visible = true)
@JsonSubTypes({
        @JsonSubTypes.Type(value = ProfileEngineResponse.Ready.class, name = "READY"),
        @JsonSubTypes.Type(value = ProfileEngineResponse.InsufficientData.class, name = "INSUFFICIENT_DATA"),
        @JsonSubTypes.Type(value = ProfileEngineResponse.NoChange.class, name = "NO_CHANGE")
})
public sealed interface ProfileEngineResponse permits ProfileEngineResponse.Ready,
        ProfileEngineResponse.InsufficientData, ProfileEngineResponse.NoChange {
    String contractVersion(); UUID requestId(); Long userId(); Long baseProfileVersion();
    Long targetProfileVersion(); Long eventWatermarkInclusive(); EngineStatus status();
    String algorithmVersion(); Instant evaluatedAt();

    record Ready(String contractVersion, UUID requestId, Long userId, Long baseProfileVersion,
                 Long targetProfileVersion, Long eventWatermarkInclusive, EngineStatus status,
                 String algorithmVersion, Instant evaluatedAt, EngineProfile profile,
                 List<KnowledgeMastery> knowledgeMastery) implements ProfileEngineResponse { }
    record InsufficientData(String contractVersion, UUID requestId, Long userId, Long baseProfileVersion,
                            Long targetProfileVersion, Long eventWatermarkInclusive, EngineStatus status,
                            String algorithmVersion, Instant evaluatedAt,
                            List<KnowledgeMastery> knowledgeMastery) implements ProfileEngineResponse { }
    record NoChange(String contractVersion, UUID requestId, Long userId, Long baseProfileVersion,
                    Long targetProfileVersion, Long eventWatermarkInclusive, EngineStatus status,
                    String algorithmVersion, Instant evaluatedAt) implements ProfileEngineResponse { }

    enum EngineStatus { READY, INSUFFICIENT_DATA, NO_CHANGE }
    record EngineProfile(String grade, List<String> preferredContentModes, String preferredExplanationStyle,
                         String learningPace, List<RecentFocus> recentFocus, List<RecentConfusion> recentConfusions,
                         String summaryProfile, BigDecimal confidence) { }
    record RecentFocus(Long kpId, BigDecimal weight) { }
    record RecentConfusion(Long kpId, String detail, Long evidenceCount, BigDecimal confidence, Instant lastOccurredAt) { }
    record KnowledgeMastery(Long kpId, BigDecimal masteryScore, String masteryStatus, BigDecimal confidence,
                            Long evidenceCount, String trend, String algorithmVersion, Instant windowStart,
                            Instant windowEnd, Instant updatedAt) { }
}
