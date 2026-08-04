package com.treepeople.leapmindtts.pojo.vo;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 复习提醒视图对象
 * <p>
 * 用于向前端返回复习提醒数据，对 {@link com.treepeople.leapmindtts.pojo.entity.ReviewReminder}
 * 实体进行转换后返回。仅包含前端展示所需字段，不暴露内部实现细节。
 * <p>
 * 前端根据 {@code isReviewed} 字段区分：
 * <ul>
 *   <li>0 - 待复习（显示在"今日复习"列表中）</li>
 *   <li>1 - 已复习（显示在"复习历史"列表中）</li>
 * </ul>
 *
 * @author wuminxi
 * @date 2026-07-21
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewReminderVO {

    /**
     * 复习提醒ID
     */
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 课程ID，前端可用于跳转到对应课件页面
     */
    private String courseId;

    /**
     * 提醒类型：REVIEW / RECALL / SPACED_REPETITION
     */
    private String reminderType;

    /**
     * 复习内容摘要
     */
    private String content;

    /**
     * 计划复习日期
     */
    private LocalDate scheduledDate;

    /**
     * 优先级：0-普通，1-重要，2-紧急
     */
    private Integer priority;

    /**
     * 是否已复习：0-未复习，1-已复习
     */
    private Integer isReviewed;

    /**
     * 复习完成时间，未复习时为 null
     */
    private LocalDateTime reviewedAt;

    /**
     * 提醒创建时间
     */
    private LocalDateTime createdAt;
}
