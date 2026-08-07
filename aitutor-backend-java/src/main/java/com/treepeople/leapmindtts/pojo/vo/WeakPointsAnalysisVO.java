package com.treepeople.leapmindtts.pojo.vo;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

/**
 * AI 综合分析视图对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeakPointsAnalysisVO {

    /**
     * 综合薄弱点分析文本
     */
    private String comprehensiveAnalysis;

    /**
     * 个性化学习建议文本
     */
    private String learningSuggestions;

    /**
     * 每个薄弱点的详细分析
     */
    private List<DetailItem> detailAnalyses;

    /**
     * 推荐优先攻克的知识点列表
     */
    private List<String> recommendedPriority;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DetailItem {
        private String knowledgePoint;
        private String analysis;
        private String suggestion;
    }
}
