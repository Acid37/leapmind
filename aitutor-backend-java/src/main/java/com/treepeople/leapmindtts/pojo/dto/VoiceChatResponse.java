package com.treepeople.leapmindtts.pojo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * @ Package：com.treepeople.leapmindtts.pojo.dto
 * @ Project：leapmind-tts - 语音对话
 * @ Description: 语音对话响应
 * @ Date：2025/8/8  
 */

@AllArgsConstructor
@Data
public class VoiceChatResponse {
    private String answer;
    private String courseId;
    private String status; // SUCCESS, ERROR
}