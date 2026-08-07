package com.treepeople.leapmindtts.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@TableName("user_events")
public class UserEvent {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String eventId;
    private Long userId;
    private String eventType;
    private String sourceModule;
    private String sessionId;
    private Long kpId;
    private String eventDataJson;
    private String schemaVersion;
    private LocalDateTime occurredAt;
    private LocalDateTime receivedAt;
    private String processStatus;
    private String traceId;
    private String payloadHash;
    private Integer payloadHashVersion;
}
