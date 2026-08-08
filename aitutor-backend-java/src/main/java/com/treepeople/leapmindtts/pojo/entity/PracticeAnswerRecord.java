package com.treepeople.leapmindtts.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("practice_answer_records")
public class PracticeAnswerRecord {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    @TableField("user_id")
    private Long userId;
    @TableField("question_id")
    private Long questionId;
    @TableField("answered_at")
    private LocalDateTime answeredAt;
    @TableField("duration_seconds")
    private Integer durationSeconds;
    @TableField("user_answer")
    private String userAnswer;
    @TableField("correct_answer")
    private String correctAnswer;
    private Boolean correct;
    private Integer points;
    private String chapter;
    @TableField("knowledge_point")
    private String knowledgePoint;
    private String difficulty;
    private String track;
    @TableField("question_type")
    private String questionType;
    @TableField("judge_score")
    private Double judgeScore;
    @TableField("judge_feedback")
    private String judgeFeedback;
    @TableField("attempt_number")
    private Integer attemptNumber;
    @TableField("source_mode")
    private String sourceMode;
    private Boolean doubtful;
    @TableField("review_note")
    private String reviewNote;
}
