package com.treepeople.leapmindtts.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("teacher_avatars")
public class TeacherAvatar {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String avatarCode;
    private String name;
    private String description;
    private String modelUrl;
    private String thumbnailUrl;
    private String voiceType;
    private String accent;
    private Boolean enabled;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
