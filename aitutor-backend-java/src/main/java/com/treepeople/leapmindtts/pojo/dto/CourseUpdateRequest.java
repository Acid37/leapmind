package com.treepeople.leapmindtts.pojo.dto;

import com.treepeople.leapmindtts.pojo.enums.SemesterEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 课程更新请求DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseUpdateRequest {
    
    private String subject;
    
    private String stageName;
    
    private String gradeName;
    
    private SemesterEnum semester;
    
    private Integer chapterNumber;
    
    private String chapterTitle;
    
    private String sectionContent;
    
    private Float sectionNumber;
    
    private String sectionTitle;
}