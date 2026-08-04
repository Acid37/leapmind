package com.treepeople.leapmindtts.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 学生提问记录表实体类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("student_questions")
public class StudentQuestion {
    
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    
    @TableField("course_id")
    private String courseId;
    
    @TableField("segment_index")
    private Integer segmentIndex;
    
    @TableField("question_text")
    private String questionText;
    
    @TableField("answer_text")
    private String answerText;
    
    @TableField("question_audio")
    private byte[] questionAudio;
    
    @TableField("answer_audio")
    private byte[] answerAudio;
    
    @TableField("question_type")
    private String questionType;
    
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}