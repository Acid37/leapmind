package com.treepeople.leapmindtts.pojo.dto;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;

/**
 * 审核请求
 */
@Data
public class ReviewRequest {
    
    @NotBlank(message = "审核人不能为空")
    private String reviewerId;
    
    private String comments;
    
    private Boolean approved; // true=通过, false=拒绝
}