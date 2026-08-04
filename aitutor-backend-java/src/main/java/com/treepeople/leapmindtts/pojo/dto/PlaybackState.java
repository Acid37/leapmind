package com.treepeople.leapmindtts.pojo.dto;

import com.treepeople.leapmindtts.pojo.enums.PlaybackStatus;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * 播放状态数据模型
 * 用于管理分段语音的播放状态信息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlaybackState {
    
    /**
     * 会话ID
     */
    private String courseId;
    
    /**
     * 当前播放的片段索引
     */
    private int currentSegmentIndex;
    
    /**
     * 总片段数量
     */
    private int totalSegments;
    
    /**
     * 播放状态
     */
    private PlaybackStatus status;
    
    /**
     * 最后更新时间
     */
    private LocalDateTime lastUpdated;
    
    /**
     * 已播放时长（毫秒）
     */
    private long playedDuration;
    
    /**
     * 总时长（毫秒）
     */
    private long totalDuration;
    
    /**
     * 播放开始时间
     */
    private LocalDateTime playbackStartTime;
    
    /**
     * 播放暂停时间
     */
    private LocalDateTime pausedTime;
    
    /**
     * 播放完成时间
     */
    private LocalDateTime completedTime;
    
    /**
     * 当前片段的播放位置（毫秒）
     */
    private long currentSegmentPosition;
    
    /**
     * 播放速度（1.0为正常速度）
     */
    private double playbackSpeed;
    
    /**
     * 是否循环播放
     */
    private boolean isLooping;
    
    /**
     * 获取播放进度百分比
     * @return 播放进度（0-100）
     */
    public double getProgressPercentage() {
        if (totalDuration == 0) {
            return 0.0;
        }
        return (double) playedDuration / totalDuration * 100.0;
    }
    
    /**
     * 获取剩余播放时长
     * @return 剩余时长（毫秒）
     */
    public long getRemainingDuration() {
        return Math.max(0, totalDuration - playedDuration);
    }
    
    /**
     * 检查是否正在播放
     * @return 如果状态为PLAYING则返回true
     */
    public boolean isPlaying() {
        return PlaybackStatus.PLAYING.equals(status);
    }
    
    /**
     * 检查是否已暂停
     * @return 如果状态为PAUSED则返回true
     */
    public boolean isPaused() {
        return PlaybackStatus.PAUSED.equals(status);
    }
    
    /**
     * 检查是否已完成
     * @return 如果状态为COMPLETED则返回true
     */
    public boolean isCompleted() {
        return PlaybackStatus.COMPLETED.equals(status);
    }
    
    /**
     * 获取当前片段进度百分比
     * @return 当前片段的播放进度（0-100）
     */
    public double getCurrentSegmentProgress() {
        if (totalSegments == 0) {
            return 0.0;
        }
        return (double) currentSegmentIndex / totalSegments * 100.0;
    }
    
    /**
     * 更新最后更新时间为当前时间
     */
    public void updateLastUpdated() {
        this.lastUpdated = LocalDateTime.now();
    }
}