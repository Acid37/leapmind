package com.treepeople.leapmindtts.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("user_teacher_preferences")
public class UserTeacherPreference {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long avatarId;
    private String voiceType;
    private BigDecimal speed;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
