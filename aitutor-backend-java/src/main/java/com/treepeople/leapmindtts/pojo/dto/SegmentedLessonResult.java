package com.treepeople.leapmindtts.pojo.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 分段课程结果数据模型
 * 用于AI老师服务返回分段语音课程的创建结果
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SegmentedLessonResult {
    
    /**
     * 会话ID，用于后续播放控制
     */
    private String courseId;
    
    /**
     * 总片段数量
     */
    private int totalSegments;
    
    /**
     * 片段文本列表
     */
    private List<String> segmentTexts;
    
    /**
     * 第一个片段的音频数据（Base64编码）
     */
    private String firstSegmentAudio;
    
    /**
     * 播放进度信息
     */
    private PlaybackProgress progress;
    
    /**
     * 是否准备就绪（所有片段合成完成）
     */
    private boolean isReady;
    
    /**
     * 课程创建时间
     */
    private LocalDateTime createdAt;
    
    /**
     * 原始课程文本
     */
    private String originalText;
    
    /**
     * 润色后的文本
     */
    private String polishedText;
    
    /**
     * 预估总时长（毫秒）
     */
    private long estimatedDuration;
    
    /**
     * 课程状态（preparing, ready, error等）
     */
    private String status;
    
    /**
     * 错误信息（如果有）
     */
    private String errorMessage;
    
    /**
     * 已完成合成的片段数量
     */
    private int completedSegments;
    
    /**
     * 课程主题或标题
     */
    private String lessonTitle;
    
    /**
     * 获取课程准备进度百分比
     * @return 课程准备进度（0-100）
     */
    public double getPreparationProgress() {
        if (totalSegments == 0) {
            return 0.0;
        }
        return (double) completedSegments / totalSegments * 100.0;
    }
    
    /**
     * 检查是否可以开始播放
     * @return 如果至少有一个片段准备好则返回true
     */
    public boolean canStartPlayback() {
        return completedSegments > 0 && firstSegmentAudio != null && !firstSegmentAudio.isEmpty();
    }
    
    /**
     * 获取状态描述
     * @return 人类可读的状态描述
     */
    public String getStatusDescription() {
        switch (status != null ? status.toLowerCase() : "") {
            case "preparing":
                return "正在准备课程内容...";
            case "ready":
                return "课程准备完成，可以开始播放";
            case "error":
                return "课程准备失败：" + (errorMessage != null ? errorMessage : "未知错误");
            case "partial":
                return String.format("部分内容已准备完成 (%d/%d)", completedSegments, totalSegments);
            default:
                return "未知状态";
        }
    }
    
    /**
     * 获取预估的每个片段平均时长
     * @return 平均片段时长（毫秒）
     */
    public long getAverageSegmentDuration() {
        if (totalSegments == 0) {
            return 0;
        }
        return estimatedDuration / totalSegments;
    }
}