package com.treepeople.leapmindtts.pojo.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 项目大纲视图对象
 */
@Data
public class ProjectOutlineVO {
    
    /**
     * 主键ID
     */
    private Integer id;
    
    /**
     * 课程（会话）ID
     */
    private String courseId;
    
    /**
     * JSON格式的大纲
     */
    private String outlineJson;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
    
    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}