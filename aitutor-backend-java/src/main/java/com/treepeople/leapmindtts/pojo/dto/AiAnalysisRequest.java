package com.treepeople.leapmindtts.pojo.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

/**
 * 调用 Python AI 服务的分析请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiAnalysisRequest {

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 薄弱点列表
     */
    private List<WeakPointItem> weakPoints;

    /**
     * 最近的练习记录（提供上下文）
     */
    private List<ExerciseRecordItem> recentExercises;

    /**
     * 语言，默认 zh
     */
    private String language;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WeakPointItem {
        private Long id;
        private String knowledgePoint;
        private String subject;
        private String weaknessLevel;
        private Integer errorCount;
        private Integer totalCount;
        private java.math.BigDecimal accuracyRate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExerciseRecordItem {
        private String exerciseId;
        private String knowledgePoint;
        private String subject;
        private Integer isCorrect;
        private String completedAt;
    }
}
