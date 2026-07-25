package com.treepeople.leapmindtts.pojo.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 讲题生成请求
 */
@Data
public class GenerateExplainRequest {

    @NotNull(message = "用户ID不能为空")
    private Long userId;

    /** 错题本 ID（来自 M1） */
    private Long wrongQuestionId;

    /** 用户提交的答案 */
    private Map<String, Object> userAnswer;

    /** 正确答案 */
    private Map<String, Object> correctAnswer;

    /** 题干内容（如果没有 wrongQuestionId，直接传题） */
    private String questionStem;

    /** 错误原因标签 */
    private String wrongReasonTag;

    /** 关联知识点 */
    private List<Map<String, Object>> knowledgePoints;
}
