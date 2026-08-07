package com.treepeople.leapmindtts.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("practice_mistakes")
public class PracticeMistake {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    @TableField("user_id")
    private Long userId;
    @TableField("question_id")
    private Long questionId;
    private String status;
    @TableField("wrong_count")
    private Integer wrongCount;
    @TableField("review_count")
    private Integer reviewCount;
    private Boolean doubtful;
    @TableField("review_note")
    private String reviewNote;
    @TableField("last_wrong_at")
    private LocalDateTime lastWrongAt;
    @TableField("last_review_at")
    private LocalDateTime lastReviewAt;
    @TableField("resolved_at")
    private LocalDateTime resolvedAt;
    @TableField("created_at")
    private LocalDateTime createdAt;
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
