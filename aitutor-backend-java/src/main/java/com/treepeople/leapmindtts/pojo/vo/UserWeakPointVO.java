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
    /** Python 引擎计算的薄弱度分数 0~1 */
    private BigDecimal weaknessScore;
    private Integer errorCount;
    private Integer totalCount;
    private BigDecimal accuracyRate;
    /** Python 引擎计算的错误率 0~1 */
    private BigDecimal errorRate;
    /** Python 引擎计算的最近10次正确率 */
    private BigDecimal recentCorrectRate;
    /** Python 引擎计算的困惑次数 */
    private Integer confusionCount;
    /** Python 引擎计算的趋势：improving/stable/declining */
    private String trend;
    private LocalDateTime lastErrorTime;
    private String status;
    private String aiAnalysis;
    private String aiSuggestion;
    private LocalDateTime analyzedAt;
    private LocalDateTime createdAt;
}
