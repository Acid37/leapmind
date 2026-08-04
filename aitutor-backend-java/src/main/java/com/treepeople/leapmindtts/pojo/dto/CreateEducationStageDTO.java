package com.treepeople.leapmindtts.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;



/**
 * 创建教育阶段DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateEducationStageDTO {
    
    /**
     * 阶段名称（如：小学, 初中）
     */
    @NotBlank(message = "阶段名称不能为空")
    private String stageName;
    
    /**
     * 年级名称（如：一年级, 二年级）
     */
    @NotBlank(message = "年级名称不能为空")
    private String gradeName;
    
    /**
     * 阶段描述
     */
    private String description;
}