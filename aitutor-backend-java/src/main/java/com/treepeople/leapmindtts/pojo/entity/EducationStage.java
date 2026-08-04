package com.treepeople.leapmindtts.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 教育阶段实体类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("education_stages")
public class EducationStage {
    
    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    
    /**
     * 阶段代码（如：PRIMARY, JUNIOR）
     */
    @TableField("stage_code")
    private String stageCode;
    
    /**
     * 阶段名称（如：小学, 初中）
     */
    @TableField("stage_name")
    private String stageName;

    @TableField("grade_code")
    private String gradeCode;

    @TableField("grade_name")
    private String gradeName;
    
    /**
     * 阶段描述
     */
    @TableField("description")
    private String description;
    
    /**
     * 排序顺序
     */
    @TableField("sort_order")
    private Integer sortOrder;

    
    /**
     * 创建时间
     */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    

}