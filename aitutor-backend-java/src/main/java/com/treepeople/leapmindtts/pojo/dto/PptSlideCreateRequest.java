package com.treepeople.leapmindtts.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;



/**
 * PPT幻灯片创建请求DTO
 */
@Data
public class PptSlideCreateRequest {
    
    /**
     * 项目ID
     */
    @NotBlank(message = "项目ID不能为空")
    private String courseId;
    
    /**
     * 幻灯片索引
     */
    @NotNull(message = "幻灯片索引不能为空")
    private Integer slideIndex;
    
    /**
     * 幻灯片ID
     */
    @NotBlank(message = "幻灯片ID不能为空")
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