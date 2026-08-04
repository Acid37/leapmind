package com.treepeople.leapmindtts.pojo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 教育阶段信息DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StageInfoDTO {
    
    /**
     * 阶段代码
     */
    private String stageCode;
    
    /**
     * 阶段名称
     */
    private String stageName;
    
    /**
     * 该阶段包含的年级列表
     */
    private List<GradeInfoDTO> grades;
    
    /**
     * 该阶段的用户数量
     */
    private Long userCount;
}