package com.treepeople.leapmindtts.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 薄弱点详情 VO
 * <p>
 * 包含薄弱点基础数据、近期错题列表、AI 分析和趋势数据。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeakPointDetailVO {

    // ========== 基本信息 ==========
    private Long id;
    private Long userId;
    private String knowledgePoint;
    private String subject;
    private String weaknessLevel;
    /** Python 引擎计算的薄弱度分数 0~1（权威值） */
    private BigDecimal weaknessScore;
    private Integer errorCount;
    private Integer totalCount;
    private BigDecimal accuracyRate;
    /** Python 引擎计算的错误率 0~1 */
    private BigDecimal errorRate;
    /** Python 引擎计算的最近正确率 */
    private BigDecimal recentCorrectRate;
    /** Python 引擎计算的困惑次数 */
    private Integer confusionCount;
    private LocalDateTime lastErrorTime;
    private String status;

    // ========== AI 分析 ==========
    private String aiAnalysis;
    private String aiSuggestion;
    private LocalDateTime analyzedAt;

    // ========== 趋势分析 ==========
    /** Python 引擎计算的趋势：improving/stable/declining */
    private String trend;
    /** 近7天错误率（0~100），Java 侧实时计算 */
    private BigDecimal recentErrorRate;
    /** 前7天错误率（0~100），Java 侧实时计算 */
    private BigDecimal previousErrorRate;

    // ========== 近期错题 ==========
    private List<ErrorExerciseItem> recentErrors;

    // ========== 时间 ==========
    private LocalDateTime createdAt;
    private LocalDateTime calculatedAt;

    /**
     * 错题条目
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ErrorExerciseItem {
        /** 练习记录ID */
        private Long id;
        /** 练习ID */
        private String exerciseId;
        /** 是否正确 */
        private Integer isCorrect;
        /** 完成时间 */
        private LocalDateTime completedAt;
    }
}
