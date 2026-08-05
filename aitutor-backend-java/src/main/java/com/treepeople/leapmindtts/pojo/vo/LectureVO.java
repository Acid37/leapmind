package com.treepeople.leapmindtts.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * M4 即时讲课视图对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LectureVO {
    private Long id;
    private String courseId;
    private String title;
    private String status;
    private String sourceFilePath;
    private String sourceFileName;
    private Long fileSize;
    private String fileType;
    private String generatedContent;
    private Integer currentPage;
    private Integer totalPages;
    private Long progressMs;
    private Long totalDurationMs;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
