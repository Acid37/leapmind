package com.treepeople.leapmindtts.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 题库相似题匹配结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionMatchResult {

    /** 是否匹配到 */
    private boolean matched;

    /** 匹配度 0-1 */
    private Double matchDegree;

    /** 匹配到的题目 ID */
    private Long questionId;

    /** 已有解析内容（匹配度高时直接复用） */
    private Object existingExplanation;

    /** 候选题目列表（匹配度中等时返回） */
    private java.util.List<CandidateQuestion> candidates;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CandidateQuestion {
        private Long questionId;
        private String stemSnippet;
        private Double similarity;
    }
}
