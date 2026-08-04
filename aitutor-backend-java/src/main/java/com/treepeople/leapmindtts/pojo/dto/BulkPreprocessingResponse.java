package com.treepeople.leapmindtts.pojo.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 批量文本预处理响应
 */
@Data
@Builder
public class BulkPreprocessingResponse {
    
    private String courseId;
    private String status; // SUCCESS, FAILED
    private String message;
    private Integer totalSlides;
    private Integer totalTextSegments;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
}