package com.treepeople.leapmindtts.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("user_profiles")
public class UserProfile {
    @TableId(type = IdType.AUTO) private Long id;
    @TableField("user_id") private Long userId;
    @TableField("profile_version") private Long profileVersion;
    @TableField("profile_status") private String profileStatus;
    @TableField("status_reason") private String statusReason;
    @TableField("grade") private String grade;
    @TableField("preferred_content_modes_json") private String preferredContentModesJson;
    @TableField("preferred_explanation_style") private String preferredExplanationStyle;
    @TableField("learning_pace") private String learningPace;
    @TableField("recent_focus_json") private String recentFocusJson;
    @TableField("summary_profile") private String summaryProfile;
    @TableField("profile_data_json") private String profileDataJson;
    @TableField("algorithm_version") private String algorithmVersion;
    @TableField("confidence") private BigDecimal confidence;
    @TableField("last_event_at") private LocalDateTime lastEventAt;
    @TableField("last_processed_event_id") private Long lastProcessedEventId;
    @TableField("computed_at") private LocalDateTime computedAt;
    @TableField("created_at") private LocalDateTime createdAt;
    @TableField("updated_at") private LocalDateTime updatedAt;
}
