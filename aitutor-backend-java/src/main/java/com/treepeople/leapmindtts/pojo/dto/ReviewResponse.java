package com.treepeople.leapmindtts.pojo.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 审核响应
 */
@Data
@Builder
public class ReviewResponse {
    
    private String courseId;
    private String status; // SUCCESS, FAILED
    private String message;
    private String newStatus; // 新的处理状态
}