package com.treepeople.leapmindtts.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 练习计划 VO
 * <p>
 * 包含目标知识点、推荐题目序列和预计完成时间。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PracticePlanVO {

    /** 用户ID */
    private Long userId;

    /** 目标知识点列表 */
    private List<String> targetKnowledgePoints;

    /** 总题目数 */
    private Integer totalQuestions;

    /** 预计完成时间（分钟） */
    private Integer estimatedMinutes;

    /** 推荐题目列表 */
    private List<PlanQuestionItem> questions;

    /**
     * 练习计划中的单道题目
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PlanQuestionItem {
        /** 题目ID */
        private String questionId;
        /** 所属知识点 */
        private String knowledgePoint;
        /** 学科 */
        private String subject;
        /** 难度：EASY/MEDIUM/HARD */
        private String difficulty;
        /** 题目类型 */
        private String questionType;
        /** 题目标题 */
        private String questionTitle;
        /** 排序序号 */
        private Integer order;
        /** 推荐理由 */
        private String reason;
    }
}
