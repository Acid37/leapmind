package com.treepeople.leapmindtts.pojo.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * 语音片段数据模型
 * 表示分段语音合成中的单个语音片段
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SpeechSegment {
    
    /**
     * 片段索引（从0开始）
     */
    private int segmentIndex;
    
    /**
     * 原始文本内容
     */
    private String text;
    
    /**
     * 音频数据（字节数组）
     */
    private byte[] audioData;
    
    /**
     * 音频时长（毫秒）
     */
    private long duration;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
    
    /**
     * 是否为预加载片段
     */
    private boolean isPreloaded;
    
    /**
     * 音频数据校验和（用于验证数据完整性）
     */
    private String checksum;
    
    /**
     * 片段状态（合成中、已完成、失败等）
     */
    private String status;
    
    /**
     * 音频格式（如：wav, mp3等）
     */
    private String audioFormat;
    
    /**
     * 音频采样率
     */
    private int sampleRate;
    
    /**
     * 获取音频数据大小（字节）
     * @return 音频数据大小，如果audioData为null则返回0
     */
    public int getAudioSize() {
        return audioData != null ? audioData.length : 0;
    }
    
    /**
     * 检查片段是否有效（包含音频数据）
     * @return 如果片段包含有效音频数据则返回true
     */
    public boolean isValid() {
        return audioData != null && audioData.length > 0 && text != null && !text.trim().isEmpty();
    }
    
    /**
     * 获取文本长度
     * @return 文本字符数，如果text为null则返回0
     */
    public int getTextLength() {
        return text != null ? text.length() : 0;
    }
}