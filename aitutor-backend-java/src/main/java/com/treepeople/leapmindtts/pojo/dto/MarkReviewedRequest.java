package com.treepeople.leapmindtts.pojo.dto;

import lombok.Data;
import jakarta.validation.constraints.NotNull;

/**
 * 标记已复习请求 DTO
 * <p>
 * 用户在前端点击"已复习"按钮时，前端将当前提醒的 ID
 * 通过此请求体发送到后端，后端校验后更新对应记录的状态。
 *
 * @author wuminxi
 * @date 2026-07-21
 */
@Data
public class MarkReviewedRequest {

    /**
     * 复习提醒ID，必填
     * <p>对应 review_reminders 表的主键 id</p>
     */
    @NotNull(message = "复习提醒ID不能为空")
    private Long reminderId;

    /**
     * 复习备注（可选）
     * <p>用户可在此记录复习心得、掌握程度等信息</p>
     */
    private String notes;
}
