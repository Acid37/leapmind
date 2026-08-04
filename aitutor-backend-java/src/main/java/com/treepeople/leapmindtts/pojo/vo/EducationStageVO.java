package com.treepeople.leapmindtts.pojo.vo;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * 教育阶段视图对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EducationStageVO {
    
    private String stageName;
    
    private String stageCode;
    
    private String description;
}