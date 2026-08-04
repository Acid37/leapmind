package com.treepeople.leapmindtts.pojo.vo;

import com.treepeople.leapmindtts.pojo.enums.SemesterEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 课程视图对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseVO {
    
    private Integer id;

    private String courseId;
    
    private String subject;
    
    private String stageName;
    
    private String gradeName;
    
    private String semester;
    
    private Integer chapterNumber;
    
    private String chapterTitle;
    
    private String sectionContent;
    
    private Float sectionNumber;
    
    private String sectionTitle;
}