package com.treepeople.leapmindtts.service.profile.summary;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.treepeople.leapmindtts.exception.M6ApiException;
import com.treepeople.leapmindtts.pojo.dto.profile.M6ProfileDtos.*;
import com.treepeople.leapmindtts.pojo.entity.UserProfile;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class SceneSummaryAssembler {
    private final ObjectMapper json;
    public SceneSummaryAssembler(ObjectMapper json) { this.json = json; }

    public SummaryView assemble(Long userId, String scene, Long kpId, ProfileSnapshot snapshot,
                                List<String> modes, List<RecentFocus> focus, List<RecentConfusion> confusions,
                                List<KnowledgeContext> knowledge) {
        UserProfile profile = snapshot.profile();
        KnowledgeContext selected = kpId == null ? null : knowledge.stream()
                .filter(k -> kpId.equals(k.kpId())).findFirst()
                .orElse(new KnowledgeContext(kpId, "EMPTY", null, null, null, null, 0L));
        List<RecentConfusion> relevant = new ArrayList<>(confusions.stream()
                .filter(c -> kpId == null || kpId.equals(c.kpId())).limit(3).toList());
        return switch (scene) {
            case "explaining" -> trimExplaining(userId, profile, selected, relevant, modes);
            case "lecturing" -> trimLecturing(userId, profile, kpId, selected, focus, modes, knowledge);
            case "conversation" -> trimConversation(userId, profile, selected, relevant, modes);
            default -> throw new IllegalArgumentException("unsupported canonical scene");
        };
    }

    private ExplainingSummary trimExplaining(Long userId, UserProfile p, KnowledgeContext selected,
                                               List<RecentConfusion> confusions, List<String> modes) {
        ExplainingSummary result;
        do {
            result = new ExplainingSummary(userId, "explaining", p.getProfileStatus(), p.getStatusReason(),
                    p.getProfileVersion(), p.getComputedAt().toInstant(ZoneOffset.UTC), p.getGrade(), selected,
                    List.copyOf(confusions), modes, p.getPreferredExplanationStyle(), p.getLearningPace());
            if (fits(result) || confusions.isEmpty()) return requireFits(result);
            confusions.remove(confusions.size() - 1);
        } while (true);
    }

    private LecturingSummary trimLecturing(Long userId, UserProfile p, Long kpId, KnowledgeContext selected,
                                            List<RecentFocus> focusInput, List<String> modes,
                                            List<KnowledgeContext> knowledge) {
        List<KnowledgeContext> weak = new ArrayList<>(kpId == null ? knowledge.stream()
                .filter(k -> "WEAK".equals(k.masteryStatus()) || "CONSOLIDATING".equals(k.masteryStatus()))
                .sorted(Comparator.comparing(KnowledgeContext::masteryScore)).limit(5).toList() : List.of());
        List<RecentFocus> focus = new ArrayList<>(focusInput.stream()
                .filter(f -> kpId == null || kpId.equals(f.kpId())).limit(3).toList());
        while (true) {
            LecturingSummary result = new LecturingSummary(userId, "lecturing", p.getProfileStatus(), p.getStatusReason(),
                    p.getProfileVersion(), p.getComputedAt().toInstant(ZoneOffset.UTC), p.getGrade(), selected,
                    List.copyOf(weak), List.copyOf(focus), modes, p.getLearningPace());
            if (fits(result)) return result;
            if (!focus.isEmpty()) focus.remove(focus.size() - 1);
            else if (!weak.isEmpty()) weak.remove(weak.size() - 1);
            else return requireFits(result);
        }
    }

    private ConversationSummary trimConversation(Long userId, UserProfile p, KnowledgeContext selected,
                                                   List<RecentConfusion> confusions, List<String> modes) {
        while (true) {
            ConversationSummary result = new ConversationSummary(userId, "conversation", p.getProfileStatus(),
                    p.getStatusReason(), p.getProfileVersion(), p.getComputedAt().toInstant(ZoneOffset.UTC), selected,
                    List.copyOf(confusions), modes, p.getPreferredExplanationStyle());
            if (fits(result)) return result;
            if (!confusions.isEmpty()) confusions.remove(confusions.size() - 1); else return requireFits(result);
        }
    }

    private boolean fits(Object value) {
        try { String encoded=json.writeValueAsString(value);return encoded.codePointCount(0,encoded.length())<=1200; }
        catch (Exception e) { throw degraded(); }
    }
    private <T> T requireFits(T value) { if (!fits(value)) throw degraded();return value; }
    private M6ApiException degraded() { return new M6ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PROFILE_SERVICE_DEGRADED", "用户画像服务暂不可用"); }
}
