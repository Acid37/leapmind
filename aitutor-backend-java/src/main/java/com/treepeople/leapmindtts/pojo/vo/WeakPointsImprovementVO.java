package com.treepeople.leapmindtts.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 薄弱点改善报告 VO（M3 薄弱点模块）
 * <p>
 * 对比本期与上期练习正确率，输出进步/退步知识点及整体改善度。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeakPointsImprovementVO {

    /** 统计周期，如 2026-07 */
    private String period;

    /** 整体改善度：本期与上期平均正确率的差值（百分点，正=进步） */
    private Double overallImprovement;

    /** 进步的知识点 */
    private List<KpChange> improvedKps;

    /** 退步的知识点 */
    private List<KpChange> worsenedKps;

    /** 当前薄弱点总数 */
    private Integer totalWeakPoints;

    /** 高薄弱（HIGH）数量 */
    private Integer highLevelCount;

    /** 已解决（RESOLVED）数量 */
    private Integer resolvedCount;

    /** 学习建议 */
    private String suggestion;

    /**
     * 单个知识点的改善变化
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KpChange {
        private String knowledgePoint;
        private Double before;   // 上期正确率(%)
        private Double after;    // 本期正确率(%)
    }
}
