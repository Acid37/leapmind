package com.treepeople.leapmindtts.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * PPT课程大纲实体类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("project_outline")
public class ProjectOutline {
    
    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;
    
    /**
     * 课程（会话）ID
     */
    @TableField("course_id")
    private String courseId;
    
    /**
     * JSON格式的大纲
     */
    @TableField("outline_json")
    private String outlineJson;
    
    /**
     * 创建时间
     */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    
    /**
     * 更新时间
     */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}