package com.treepeople.leapmindtts.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

/**
 * 分段语音配置属性类
 * 用于管理分段语音合成和播放控制的所有配置参数
 */
@ConfigurationProperties(prefix = "segmented-speech")
@Component
@Data
public class SegmentedSpeechProperties {
    
    /**
     * 是否启用分段语音功能
     */
    private boolean enabled = true;
    
    /**
     * 文本分句配置
     */
    private TextSplitting textSplitting = new TextSplitting();
    
    /**
     * TTS合成配置
     */
    private Synthesis synthesis = new Synthesis();
    
    /**
     * 缓存配置
     */
    private Cache cache = new Cache();
    
    /**
     * 内存管理配置
     */
    private Memory memory = new Memory();
    
    /**
     * 播放控制配置
     */
    private Playback playback = new Playback();
    
    /**
     * 文本分句配置类
     */
    @Data
    public static class TextSplitting {
        /**
         * 句子分隔符列表
         */
        private List<String> sentenceSeparators = Arrays.asList("。", "！", "？", ".", "!", "?");
        
        /**
         * 最大片段长度（字符数）
         */
        private int maxSegmentLength = 200;
        
        /**
         * 最小片段长度（字符数）
         */
        private int minSegmentLength = 5;
        
        /**
         * 是否启用智能分句
         */
        private boolean smartSplitting = true;
    }
    
    /**
     * TTS合成配置类
     */
    @Data
    public static class Synthesis {
        /**
         * 最大并发TTS请求数
         */
        private int maxConcurrentRequests = 3;
        
        /**
         * 重试次数
         */
        private int retryAttempts = 2;
        
        /**
         * 请求超时时间
         */
        private Duration timeout = Duration.ofSeconds(10);
        
        /**
         * 批处理大小
         */
        private int batchSize = 5;
    }
    
    /**
     * 缓存配置类
     */
    @Data
    public static class Cache {
        /**
         * 语音片段缓存大小
         */
        private int segmentCacheSize = 50;
        
        /**
         * 会话超时时间
         */
        private Duration sessionTimeout = Duration.ofMinutes(30);
        
        /**
         * 是否启用预加载
         */
        private boolean enablePreload = true;
        
        /**
         * 预加载片段数量
         */
        private int preloadSegments = 2;
    }
    
    /**
     * 内存管理配置类
     */
    @Data
    public static class Memory {
        /**
         * 内存清理间隔
         */
        private Duration cleanupInterval = Duration.ofMinutes(5);
        
        /**
         * 最大内存使用量
         */
        private String maxMemoryUsage = "100MB";
        
        /**
         * 是否启用自动清理
         */
        private boolean autoCleanupEnabled = true;
    }
    
    /**
     * 播放控制配置类
     */
    @Data
    public static class Playback {
        /**
         * 是否启用跳转功能
         */
        private boolean enableSeek = true;
        
        /**
         * 是否启用暂停/恢复功能
         */
        private boolean enablePauseResume = true;
        
        /**
         * 播放进度更新间隔
         */
        private Duration progressUpdateInterval = Duration.ofSeconds(1);
    }
}