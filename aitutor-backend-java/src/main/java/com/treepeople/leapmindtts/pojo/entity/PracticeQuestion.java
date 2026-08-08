package com.treepeople.leapmindtts.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("practice_questions")
public class PracticeQuestion {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private String subject;
    @TableField("grade_level")
    private String gradeLevel;
    @TableField("question_type")
    private String questionType;
    private String title;
    private String content;
    @TableField("option_a")
    private String optionA;
    @TableField("option_b")
    private String optionB;
    @TableField("option_c")
    private String optionC;
    @TableField("option_d")
    private String optionD;
    @TableField("correct_answer")
    private String correctAnswer;
    @TableField("answer_keywords")
    private String answerKeywords;
    private String analysis;
    private String chapter;
    @TableField("knowledge_point")
    private String knowledgePoint;
    private String difficulty;
    private String track;
    @TableField("lesson_id")
    private String lessonId;
    private String status;
    @TableField("created_at")
    private LocalDateTime createdAt;
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
