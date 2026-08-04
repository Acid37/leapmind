package com.treepeople.leapmindtts.pojo.dto;

import com.treepeople.leapmindtts.pojo.enums.PlaybackStatus;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * 播放进度数据模型
 * 用于向客户端提供播放进度信息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlaybackProgress {
    
    /**
     * 当前播放片段索引
     */
    private int currentSegment;
    
    /**
     * 总片段数量
     */
    private int totalSegments;
    
    /**
     * 进度百分比（0-100）
     */
    private double progressPercentage;
    
    /**
     * 当前片段的文本内容
     */
    private String currentText;
    
    /**
     * 剩余播放时长（毫秒）
     */
    private long remainingDuration;
    
    /**
     * 播放状态
     */
    private PlaybackStatus status;
    
    /**
     * 已播放时长（毫秒）
     */
    private long playedDuration;
    
    /**
     * 总时长（毫秒）
     */
    private long totalDuration;
    
    /**
     * 当前片段时长（毫秒）
     */
    private long currentSegmentDuration;
    
    /**
     * 当前片段已播放时长（毫秒）
     */
    private long currentSegmentPlayedDuration;
    
    /**
     * 播放速度（1.0为正常速度）
     */
    private double playbackSpeed;
    
    /**
     * 会话ID
     */
    private String courseId;
    
    /**
     * 进度更新时间
     */
    private LocalDateTime updatedAt;
    
    /**
     * 下一个片段的文本预览
     */
    private String nextSegmentText;
    
    /**
     * 是否为最后一个片段
     */
    private boolean isLastSegment;
    
    /**
     * 获取剩余片段数量
     * @return 剩余未播放的片段数量
     */
    public int getRemainingSegments() {
        return Math.max(0, totalSegments - currentSegment - 1);
    }
    
    /**
     * 获取当前片段进度百分比
     * @return 当前片段的播放进度（0-100）
     */
    public double getCurrentSegmentProgress() {
        if (currentSegmentDuration == 0) {
            return 0.0;
        }
        return (double) currentSegmentPlayedDuration / currentSegmentDuration * 100.0;
    }
    
    /**
     * 获取预估剩余时间（考虑播放速度）
     * @return 根据当前播放速度计算的剩余时间（毫秒）
     */
    public long getEstimatedRemainingTime() {
        if (playbackSpeed <= 0) {
            return remainingDuration;
        }
        return (long) (remainingDuration / playbackSpeed);
    }
    
    /**
     * 检查是否可以跳转到下一个片段
     * @return 如果不是最后一个片段则返回true
     */
    public boolean canSkipToNext() {
        return currentSegment < totalSegments - 1;
    }
    
    /**
     * 检查是否可以跳转到上一个片段
     * @return 如果不是第一个片段则返回true
     */
    public boolean canSkipToPrevious() {
        return currentSegment > 0;
    }
    
    /**
     * 获取格式化的进度文本
     * @return 格式化的进度信息，如 "3/10 (30%)"
     */
    public String getFormattedProgress() {
        return String.format("%d/%d (%.1f%%)", currentSegment + 1, totalSegments, progressPercentage);
    }
    
    /**
     * 获取格式化的时间信息
     * @return 格式化的时间信息，如 "02:30 / 08:45"
     */
    public String getFormattedTime() {
        return String.format("%s / %s", 
            formatDuration(playedDuration), 
            formatDuration(totalDuration));
    }
    
    /**
     * 格式化时长为 MM:SS 格式
     * @param durationMs 时长（毫秒）
     * @return 格式化的时长字符串
     */
    private String formatDuration(long durationMs) {
        long seconds = durationMs / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }
}