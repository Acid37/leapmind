package com.treepeople.leapmindtts.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * @ Package：com.treepeople.leapmindtts.pojo.dto
 * @ Project：leapmind-tts - 语音对话
 * @ Description: 语音合成请求
 * @ Date：2025/8/8  
 */

@Data
public class VoiceSynthesisRequest {
    @NotBlank(message = "会话ID不能为空")
    private String courseId;

    @NotBlank(message = "文本不能为空")
    private String text;
}