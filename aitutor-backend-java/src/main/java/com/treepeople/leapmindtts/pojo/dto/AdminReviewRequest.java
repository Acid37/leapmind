 package com.treepeople.leapmindtts.pojo.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 管理员审核请求DTO（支持修改润色文本）
 */
@Data
public class AdminReviewRequest {
    
    @NotBlank(message = "审核人ID不能为空")
    private String reviewerId;
    
    @NotNull(message = "审核结果不能为空")
    private Boolean approved;
    
    private String comments;
    
    /**
     * 修改后的润色文本（可选）
     * 如果提供，将更新会话的润色文本
     */
    private String updatedPolishedText;
    
    /**
     * 修改后的标题（可选）
     */
    private String updatedTitle;
    
    /**
     * 修改后的描述（可选）
     */
    private String updatedDescription;
    
    /**
     * 是否强制更新文本片段
     * 如果为true，将根据新的润色文本重新生成文本片段
     */
    private Boolean forceUpdateSegments = false;
}