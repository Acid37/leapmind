package com.treepeople.leapmindtts.pojo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @ Package：com.treepeople.leapmindtts.pojo.dto
 * @ Project：leapmind-tts
 * @ Description: 文本润色响应模型，包含润色结果和元数据
 * @ Date：2025/7/29  
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TextPolishingResponse {
    
    /**
     * 润色后文本
     */
    private String polishedText;
    
    /**
     * 原始文本
     */
    private String originalText;
    
    /**
     * 是否成功润色
     */
    private boolean isPolished;
    
    /**
     * 错误信息（如果有）
     */
    private String errorMessage;
    
    /**
     * 处理耗时（毫秒）
     */
    private long processingTimeMs;
}