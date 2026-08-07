package com.treepeople.leapmindtts.pojo.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * 练习推荐请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExerciseRecommendRequest {

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 学科（可选）
     */
    private String subject;

    /**
     * 知识点（可选）
     */
    private String knowledgePoint;

    /**
     * 推荐数量，默认5
     */
    private Integer count;
}
