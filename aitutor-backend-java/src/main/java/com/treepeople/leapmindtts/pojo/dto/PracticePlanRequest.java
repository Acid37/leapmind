package com.treepeople.leapmindtts.pojo.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 练习计划生成请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PracticePlanRequest {

    /** 用户ID */
    @NotNull(message = "用户ID不能为空")
    private Long userId;

    /** 需要练习的知识点列表 */
    @NotEmpty(message = "知识点列表不能为空")
    private List<String> knowledgePoints;
}
