package com.treepeople.leapmindtts.pojo.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 分段语音合成结果数据模型
 * 包含会话信息和语音片段列表
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SegmentedSpeechResult {
    
    /**
     * 会话ID，用于标识唯一的分段语音会话
     */
    private String courseId;
    
    /**
     * 总片段数量
     */
    private int totalSegments;
    
    /**
     * 各片段的文本内容列表
     */
    private List<String> segmentTexts;
    
    /**
     * 预估总时长（毫秒）
     */
    private long totalDuration;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
    
    /**
     * 是否完全合成完成
     */
    private boolean isComplete;
    
    /**
     * 状态信息（如：processing, completed, failed等）
     */
    private String status;
    
    /**
     * 已完成合成的片段数量
     */
    private int completedSegments;
    
    /**
     * 失败的片段数量
     */
    private int failedSegments;
    
    /**
     * 原始文本内容
     */
    private String originalText;
    
    /**
     * 错误信息（如果有）
     */
    private String errorMessage;
    
    /**
     * 合成开始时间
     */
    private LocalDateTime synthesisStartTime;
    
    /**
     * 合成完成时间
     */
    private LocalDateTime synthesisEndTime;
    
    /**
     * 获取合成进度百分比
     * @return 合成进度（0-100）
     */
    public double getProgressPercentage() {
        if (totalSegments == 0) {
            return 0.0;
        }
        return (double) completedSegments / totalSegments * 100.0;
    }
    
    /**
     * 获取成功率
     * @return 成功合成的片段比例（0-100）
     */
    public double getSuccessRate() {
        if (totalSegments == 0) {
            return 0.0;
        }
        return (double) completedSegments / totalSegments * 100.0;
    }
    
    /**
     * 检查是否有失败的片段
     * @return 如果有失败片段则返回true
     */
    public boolean hasFailedSegments() {
        return failedSegments > 0;
    }
    
    /**
     * 获取合成耗时（毫秒）
     * @return 从开始到完成的耗时，如果未完成则返回当前耗时
     */
    public long getSynthesisDuration() {
        if (synthesisStartTime == null) {
            return 0;
        }
        LocalDateTime endTime = synthesisEndTime != null ? synthesisEndTime : LocalDateTime.now();
        return java.time.Duration.between(synthesisStartTime, endTime).toMillis();
    }
}