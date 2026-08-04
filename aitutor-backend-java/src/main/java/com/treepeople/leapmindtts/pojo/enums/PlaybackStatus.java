package com.treepeople.leapmindtts.pojo.enums;

/**
 * 播放状态枚举
 * 用于表示分段语音的播放状态
 */
public enum PlaybackStatus {
    /**
     * 未开始播放
     */
    NOT_STARTED,
    
    /**
     * 正在播放
     */
    PLAYING,
    
    /**
     * 已暂停
     */
    PAUSED,
    
    /**
     * 播放完成
     */
    COMPLETED,
    
    /**
     * 播放出错
     */
    ERROR,
    
    /**
     * 已停止
     */
    STOPPED
}