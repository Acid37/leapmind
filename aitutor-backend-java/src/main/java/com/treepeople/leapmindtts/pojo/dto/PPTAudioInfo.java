package com.treepeople.leapmindtts.pojo.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * PPT音频概览信息对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PPTAudioInfo {
    
    private String courseId;
    
    private String title;
    
    private Integer totalPages;
    
    private Integer totalSegments;
    
    private Long totalDuration;
    
    private Long totalAudioSize;
    
    private List<PPTPageInfo> pages;
    
    private LocalDateTime createdAt;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PPTPageInfo {
        
        private Integer pageNumber;
        
        private String pageTitle;
        
        private String slideType;
        
        private Integer segmentCount;
        
        private Long pageDuration;
    }
}