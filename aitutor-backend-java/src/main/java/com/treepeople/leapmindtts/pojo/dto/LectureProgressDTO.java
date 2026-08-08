package com.treepeople.leapmindtts.pojo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * M4 讲课进度 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LectureProgressDTO {
    private String courseId;
    private Integer currentPage;
    private Long progressMs;
    private String status;
}
