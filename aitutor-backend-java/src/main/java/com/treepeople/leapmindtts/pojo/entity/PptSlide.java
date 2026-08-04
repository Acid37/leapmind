package com.treepeople.leapmindtts.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * PPT幻灯片实体类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("ppt_slides")
public class PptSlide {
    
    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;
    
    /**
     * 项目ID
     */
    @TableField("course_id")
    private String courseId;
    
    /**
     * 幻灯片索引
     */
    @TableField("slide_index")
    private Integer slideIndex;
    
    /**
     * 幻灯片ID
     */
    @TableField("slide_id")
    private String slideId;
    
    /**
     * 标题
     */
    @TableField("title")
    private String title;
    
    /**
     * 内容类型
     */
    @TableField("content_type")
    private String contentType;
    
    /**
     * HTML内容
     */
    @TableField("html_content")
    private String htmlContent;
    
    /**
     * 创建时间
     */
    @TableField(value = "create_at", fill = FieldFill.INSERT)
    private LocalDateTime createAt;
}