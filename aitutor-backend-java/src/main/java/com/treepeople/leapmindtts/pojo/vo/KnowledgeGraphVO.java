package com.treepeople.leapmindtts.pojo.vo;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

/**
 * 知识图谱视图对象
 * 用于知识图谱页：展示知识点之间的关联关系
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeGraphVO {

    /**
     * 图谱节点列表（知识点）
     */
    private List<GraphNode> nodes;

    /**
     * 图谱边列表（知识点之间的关联）
     */
    private List<GraphEdge> edges;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GraphNode {

        /**
         * 节点ID（知识点名称）
         */
        private String id;

        /**
         * 知识点名称
         */
        private String name;

        /**
         * 学科
         */
        private String subject;

        /**
         * 薄弱程度: HIGH/MEDIUM/LOW/MASTERED
         */
        private String weaknessLevel;

        /**
         * 掌握率(%)
         */
        private Double masteryRate;

        /**
         * 节点分组（学科）
         */
        private String group;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GraphEdge {

        /**
         * 源知识点
         */
        private String source;

        /**
         * 目标知识点
         */
        private String target;

        /**
         * 关系类型: prerequisite(前置依赖) / related(相关) / extends(扩展)
         */
        private String relation;
    }
}
