package com.treepeople.leapmindtts.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("user_knowledge_mastery")
public class UserKnowledgeMastery {
    @TableId(type = IdType.AUTO) private Long id;
    @TableField("user_id") private Long userId;
    @TableField("kp_id") private Long kpId;
    @TableField("profile_version") private Long profileVersion;
    @TableField("mastery_score") private BigDecimal masteryScore;
    @TableField("mastery_status") private String masteryStatus;
    @TableField("confidence") private BigDecimal confidence;
    @TableField("evidence_count") private Long evidenceCount;
    @TableField("trend") private String trend;
    @TableField("algorithm_version") private String algorithmVersion;
    @TableField("window_start") private LocalDateTime windowStart;
    @TableField("window_end") private LocalDateTime windowEnd;
    @TableField("updated_at") private LocalDateTime updatedAt;
}
