package com.treepeople.leapmindtts.pojo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 年级信息DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GradeInfoDTO {
    
    /**
     * 年级代码
     */
    private String code;
    
    /**
     * 年级描述
     */
    private String description;
    
    /**
     * 教育阶段
     */
    private String stage;
    
    /**
     * 教育阶段代码
     */
    private String stageCode;
}