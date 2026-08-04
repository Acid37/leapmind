package com.treepeople.leapmindtts.pojo.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * PPT音频片段信息对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PPTAudioSegment {
    
    private Long id;
    
    private String courseId;
    
    private Integer slidePageNumber;
    
    private String slideTitle;
    
    private Integer contentPointIndex;
    
    private Integer segmentIndex;
    
    private String slideType;
    
    private String slideDescription;
    
    private String originalText;
    
    private String polishedText;
    
    private String textContent;
    
    private byte[] audioData;
    
    private Long audioSize;
    
    private Long duration;
    
    private String audioFormat;
    
    private Integer sampleRate;
    
    private String checksum;
    
    private LocalDateTime createdAt;
}