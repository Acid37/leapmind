package com.treepeople.leapmindtts.pojo.vo;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * 年级视图对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GradeVO {
    
    private String gradeCode;
    
    private String gradeName;
    
    private String stageCode;
    
    private String stageName;
    
    private Integer sortOrder;
}