package com.treepeople.leapmindtts.pojo.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * 练习结果记录请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExerciseRecordRequest {

    /**
     * 用户ID
     */
    @NotNull(message = "用户ID不能为空")
    private Long userId;

    /**
     * 练习题ID（外部题库唯一标识）
     */
    @NotNull(message = "练习题ID不能为空")
    private String exerciseId;

    /**
     * 知识点名称
     */
    private String knowledgePoint;

    /**
     * 学科
     */
    private String subject;

    /**
     * 是否正确：1-正确 0-错误
     */
    @NotNull(message = "练习结果不能为空")
    private Integer isCorrect;
}
