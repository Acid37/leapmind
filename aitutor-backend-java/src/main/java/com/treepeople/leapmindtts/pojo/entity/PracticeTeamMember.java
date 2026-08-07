package com.treepeople.leapmindtts.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("practice_team_members")
public class PracticeTeamMember {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    @TableField("team_id")
    private Long teamId;
    @TableField("user_id")
    private Long userId;
    @TableField("joined_at")
    private LocalDateTime joinedAt;
}
