package com.treepeople.leapmindtts.pojo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 *  
 * @ Package：com.treepeople.leapmindtts.pojo.dto
 * @ Project：leapmind-tts
 * @ Description: 润色指标模型，用于记录润色过程的性能数据
 * @ Date：2025/7/29  
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PolishingMetrics {
    
    /**
     * 原始文本长度
     */
    private int originalLength;
    
    /**
     * 润色后文本长度
     */
    private int polishedLength;
    
    /**
     * API响应时间（毫秒）
     */
    private long apiResponseTime;
    
    /**
     * 是否使用了降级
     */
    private boolean fallbackUsed;
    
    /**
     * 请求ID
     */
    private String requestId;
}