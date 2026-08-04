package com.treepeople.leapmindtts.pojo.dto;

import lombok.Data;

import jakarta.validation.constraints.NotNull;

/**
 * 创建项目大纲请求DTO
 */
@Data
public class ProjectOutlineCreateRequest {
    
    /**
     * 课程（会话）ID
     */
    @NotNull(message = "项目ID不能为空")
    private String courseId;
    
    /**
     * JSON格式的大纲
     */
    @NotNull(message = "大纲内容不能为空")
    private String outlineJson;
}