package com.treepeople.leapmindtts.pojo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @ Package：com.treepeople.leapmindtts.pojo.dto
 * @ Project：leapmind-tts
 * @ Description: 文本润色请求模型，包含原始文本和润色参数
 * @ Date：2025/7/29  
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TextPolishingRequest {
    
    /**
     * 原始文本
     */
    private String originalText;
    
    /**
     * 润色提示词
     */
    private String polishingPrompt;
    
    /**
     * 最大长度限制
     */
    private Integer maxLength;
    
    /**
     * 请求ID（用于日志追踪）
     */
    private String requestId;
}