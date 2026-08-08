package com.treepeople.leapmindtts.pojo.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * 薄弱点查询请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeakPointsQueryRequest {

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 学科（可选）
     */
    private String subject;

    /**
     * 状态过滤（可选）：ACTIVE/RESOLVED/IMPROVING
     */
    private String status;
}
