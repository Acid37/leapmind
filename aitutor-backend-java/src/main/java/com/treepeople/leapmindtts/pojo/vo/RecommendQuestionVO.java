package com.treepeople.leapmindtts.pojo.vo;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * 推荐题目视图对象
 * 用于薄弱点详情页：根据具体知识点推荐练习题
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendQuestionVO {

    /**
     * 题目ID
     */
    private String questionId;

    /**
     * 知识点
     */
    private String knowledgePoint;

    /**
     * 学科
     */
    private String subject;

    /**
     * 难度: EASY/MEDIUM/HARD
     */
    private String difficulty;

    /**
     * 题目类型: 选择题/填空题/解答题
     */
    private String questionType;

    /**
     * 题目标题/内容摘要
     */
    private String questionTitle;

    /**
     * 推荐原因
     */
    private String reason;
}
