package com.treepeople.leapmindtts.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("practice_teams")
public class PracticeTeam {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private String name;
    @TableField("owner_user_id")
    private Long ownerUserId;
    @TableField("invite_code")
    private String inviteCode;
    @TableField("created_at")
    private LocalDateTime createdAt;
}
