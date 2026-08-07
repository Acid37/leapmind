package com.treepeople.leapmindtts.pojo.vo;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户薄弱点视图对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserWeakPointVO {

    private Long id;
    private Long userId;
    private String knowledgePoint;
    private String subject;
    private String weaknessLevel;
    private Integer errorCount;
    private Integer totalCount;
    private BigDecimal accuracyRate;
    private LocalDateTime lastErrorTime;
    private String status;
    private String aiAnalysis;
    private String aiSuggestion;
    private LocalDateTime analyzedAt;
    private LocalDateTime createdAt;
}
