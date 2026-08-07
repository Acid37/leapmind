package com.treepeople.leapmindtts.pojo.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * M4 即时讲课实体类（许沣睿）
 * 表名: lectures（避免与备课模块 teaching_contents 冲突）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("lectures")
public class Lecture {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("course_id")
    private String courseId;

    @TableField("title")
    private String title;

    @TableField("status")
    private String status;

    @TableField("source_file_path")
    private String sourceFilePath;

    @TableField("source_file_name")
    private String sourceFileName;

    @TableField("file_size")
    private Long fileSize;

    @TableField("file_type")
    private String fileType;

    @TableField("ppt_json_path")
    private String pptJsonPath;

    @TableField("generated_content")
    private String generatedContent;

    @TableField("current_page")
    private Integer currentPage;

    @TableField("total_pages")
    private Integer totalPages;

    @TableField("progress_ms")
    private Long progressMs;

    @TableField("total_duration_ms")
    private Long totalDurationMs;

    @TableField("playback_snapshot")
    private String playbackSnapshot;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
