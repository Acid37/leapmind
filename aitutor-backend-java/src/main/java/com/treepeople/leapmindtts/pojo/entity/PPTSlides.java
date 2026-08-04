package com.treepeople.leapmindtts.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @ Package：com.treepeople.leapmindtts.pojo.entity
 * @ Project：leapMind-java
 * @ Description:
 * @ Date：2025/11/11  15:54
 */
@Data
@TableName("ppt_slides")
@AllArgsConstructor
@NoArgsConstructor
public class PPTSlides {

    @TableField("id")
    private Integer id;

    @TableField("course_id")
    private String courseId;

    @TableField("slide_index")
    private Integer slideIndex;

    @TableField("slide_id")
    private String slideId;

    @TableField("title")
    private String title;

    @TableField("content_type")
    private String contentType;

    @TableField("html_content")
    private String htmlContent;

    @TableField("create_at")
    private String createAt;

}
