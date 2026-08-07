package com.treepeople.leapmindtts.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 备课内容实体类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("teaching_contents")
public class TeachingContent {

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 备课ID
     */
    @TableField("prep_id")
    private Long prepId;

    /**
     * 用户ID
     */
    @TableField("user_id")
    private Long userId;

    /**
     * 备课标题
     */
    @TableField("title")
    private String title;

    /**
     * 备课状态（draft-草稿, published-已发布, archived-已归档）
     */
    @TableField("status")
    private String status;

    /**
     * PPT结构JSON
     */
    @TableField("ppt_structure")
    private String pptStructure;

    /**
     * 应用的模板ID
     */
    @TableField("template_id")
    private Long templateId;

    /**
     * PPT导出下载链接
     */
    @TableField("ppt_download_url")
    private String pptDownloadUrl;

    /**
     * 完整备课生成内容（含大纲、PPT、讲稿），供 generate-ppt 接口读取 syllabus
     */
    @TableField("generated_content_json")
    private String generatedContentJson;

    /**
     * 创建时间
     */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
