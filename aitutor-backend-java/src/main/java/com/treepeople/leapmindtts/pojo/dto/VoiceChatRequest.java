package com.treepeople.leapmindtts.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * @ Package：com.treepeople.leapmindtts.pojo.dto
 * @ Project：leapmind-tts - 语音对话
 * @ Description: 语音对话请求
 * @ Date：2025/8/8  
 */

@Data
public class VoiceChatRequest {
    @NotBlank(message = "会话ID不能为空")
    private String courseId;

    @NotBlank(message = "问题不能为空")
    private String question;
}