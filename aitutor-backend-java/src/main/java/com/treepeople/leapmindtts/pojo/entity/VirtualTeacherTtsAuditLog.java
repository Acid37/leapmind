package com.treepeople.leapmindtts.pojo.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("virtual_teacher_tts_audit_logs")
public class VirtualTeacherTtsAuditLog {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("course_id")
    private String courseId;

    @TableField("voice_type")
    private String voiceType;

    @TableField("text_length")
    private Integer textLength;

    @TableField("cache_hit")
    private Boolean cacheHit;

    @TableField("audio_size")
    private Long audioSize;

    @TableField("latency_ms")
    private Long latencyMs;

    @TableField("status")
    private String status;

    @TableField("error_message")
    private String errorMessage;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
