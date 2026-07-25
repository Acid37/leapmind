package com.treepeople.leapmindtts.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 题库相似题匹配请求
 */
@Data
public class QuestionMatchRequest {

    /** 题目题干文本 */
    @NotBlank(message = "题目题干不能为空")
    private String stem;

    /** 科目 */
    private String subject;

    /** 题型 */
    private String type;
}
