package com.treepeople.leapmindtts.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户薄弱点表实体类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("user_weak_points")
public class UserWeakPoint {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("kp_id")
    private Long kpId;

    @TableField("knowledge_point")
    private String knowledgePoint;

    @TableField("subject")
    private String subject;

    @TableField("weakness_level")
    private String weaknessLevel;

    @TableField("weakness_score")
    private java.math.BigDecimal weaknessScore;

    @TableField("error_count")
    private Integer errorCount;

    @TableField("total_count")
    private Integer totalCount;

    @TableField("total_attempts")
    private Integer totalAttempts;

    @TableField("accuracy_rate")
    private java.math.BigDecimal accuracyRate;

    @TableField("error_rate")
    private java.math.BigDecimal errorRate;

    @TableField("recent_correct_rate")
    private java.math.BigDecimal recentCorrectRate;

    @TableField("confusion_count")
    private Integer confusionCount;

    @TableField("trend")
    private String trend;

    @TableField("last_error_time")
    private LocalDateTime lastErrorTime;

    @TableField("last_error_at")
    private LocalDateTime lastErrorAt;

    @TableField("status")
    private String status;

    @TableField("ai_analysis")
    private String aiAnalysis;

    @TableField("ai_suggestion")
    private String aiSuggestion;

    @TableField("analyzed_at")
    private LocalDateTime analyzedAt;

    @TableField("calculated_at")
    private LocalDateTime calculatedAt;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
