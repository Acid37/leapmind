package com.treepeople.leapmindtts.pojo.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

/**
 * Python AI 服务返回的分析响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiAnalysisResponse {

    /**
     * 综合薄弱点分析
     */
    private String comprehensiveAnalysis;

    /**
     * 个性化学习建议
     */
    private String learningSuggestions;

    /**
     * 详细分析列表
     */
    private List<DetailAnalysis> detailAnalyses;

    /**
     * 推荐优先攻克的知识点
     */
    private List<String> recommendedPriority;

    /**
     * 状态：success / error
     */
    private String status;

    /**
     * 错误信息
     */
    private String error;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DetailAnalysis {
        private String knowledgePoint;
        private String analysis;
        private String suggestion;
    }
}
