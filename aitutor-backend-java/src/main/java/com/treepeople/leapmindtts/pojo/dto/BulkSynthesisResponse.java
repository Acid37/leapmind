package com.treepeople.leapmindtts.pojo.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 批量语音合成响应对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkSynthesisResponse {
    
    private String courseId;
    
    private String status; // STARTED, PROCESSING, COMPLETED, FAILED
    
    private Integer totalSlides;
    
    private Integer totalContentPoints;
    
    private String message;
    
    private LocalDateTime startTime;
}