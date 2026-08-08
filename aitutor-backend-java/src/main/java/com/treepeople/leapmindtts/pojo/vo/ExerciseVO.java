package com.treepeople.leapmindtts.pojo.vo;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * 推荐练习视图对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExerciseVO {

    /**
     * 练习ID
     */
    private String exerciseId;

    /**
     * 知识点
     */
    private String knowledgePoint;

    /**
     * 学科
     */
    private String subject;

    /**
     * 来源类型：RESOLVED_WEAK_POINT / ACTIVE_WEAK_POINT
     */
    private String sourceType;

    /**
     * 优先级：1-最高（已解决错题复用）2-普通
     */
    private Integer priority;
}
