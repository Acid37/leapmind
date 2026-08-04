package com.treepeople.leapmindtts.pojo.dto;

import lombok.Data;

/**
 * PPT幻灯片更新请求DTO
 */
@Data
public class PptSlideUpdateRequest {
    
    /**
     * 项目ID
     */
    private String courseId;
    
    /**
     * 幻灯片索引
     */
    private Integer slideIndex;
    
    /**
     * 幻灯片ID
     */
    private String slideId;
    
    /**
     * 标题
     */
    private String title;
    
    /**
     * 内容类型
     */
    private String contentType;
    
    /**
     * HTML内容
     */
    private String htmlContent;
}