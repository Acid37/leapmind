package com.treepeople.leapmindtts.pojo.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;

/**
 * 音频片段请求DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AudioSegmentRequest {
    
    @NotBlank(message = "会话ID不能为空")
    private String courseId;
    
    @NotNull(message = "片段索引不能为空")
    @Min(value = 0, message = "片段索引不能小于0")
    private Integer segmentIndex;
    
    private String textContent;
    
    @NotNull(message = "音频数据不能为空")
    private byte[] audioData;
    
    @Builder.Default
    private String audioFormat = "wav";
    
    @Builder.Default
    private Integer sampleRate = 16000;
    
    private Long duration;
}