package com.treepeople.leapmindtts.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 讲课会话表实体类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("lesson_sessions")
public class LessonSession {
    
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    
    @TableField("course_id")
    private String courseId;
    
    @TableField("title")
    private String title;
    
    @TableField("original_text")
    private String originalText;
    
    @TableField("polished_text")
    private String polishedText;
    
    @TableField("total_segments")
    private Integer totalSegments;
    
    @TableField("total_duration")
    private Long totalDuration;
    
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    
    // 新增：审核流程相关字段
    @TableField("processing_status")
    private String processingStatus; // DRAFT, PENDING_REVIEW, APPROVED, REJECTED, SYNTHESIZED
    
    @TableField("reviewed_by")
    private String reviewedBy;
    
    @TableField("reviewed_at")
    private LocalDateTime reviewedAt;
    
    @TableField("review_comments")
    private String reviewComments;
}