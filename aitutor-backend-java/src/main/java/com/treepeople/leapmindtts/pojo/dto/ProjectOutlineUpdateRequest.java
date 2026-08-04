package com.treepeople.leapmindtts.pojo.dto;

import lombok.Data;

import jakarta.validation.constraints.NotNull;

/**
 * 更新项目大纲请求DTO
 */
@Data
public class ProjectOutlineUpdateRequest {
    
    /**
     * 大纲ID
     */
    @NotNull(message = "大纲ID不能为空")
    private Integer id;
    
    /**
     * JSON格式的大纲
     */
    @NotNull(message = "大纲内容不能为空")
    private String outlineJson;
}