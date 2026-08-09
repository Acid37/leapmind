package com.treepeople.leapmindtts.util;

import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class PracticeKnowledgePointIds {

    private PracticeKnowledgePointIds() {
    }

    /**
     * The practice question schema currently stores a knowledge-point name rather than a numeric kp_id.
     * Generate the same stable positive ID used by all M1 events until the schema owns that relation.
     */
    public static long from(String subject, String knowledgePoint) {
        String normalizedKnowledgePoint = knowledgePoint == null ? "" : knowledgePoint.trim();
        if (!StringUtils.hasText(normalizedKnowledgePoint)) {
            throw new IllegalArgumentException("题目缺少知识点，无法生成 M6 kpId");
        }
        String normalizedSubject = subject == null ? "" : subject.trim();
        long value = UUID.nameUUIDFromBytes(
                        (normalizedSubject + "\u0000" + normalizedKnowledgePoint).getBytes(StandardCharsets.UTF_8))
                .getMostSignificantBits() & Long.MAX_VALUE;
        return value == 0 ? 1L : value;
    }
}
