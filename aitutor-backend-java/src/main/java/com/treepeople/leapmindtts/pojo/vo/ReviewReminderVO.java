package com.treepeople.leapmindtts.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
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
    @Schema(description = "复习提醒ID（主键）", example = "100")
    private Long id;

    /**
     * 用户ID
     */
    @Schema(description = "用户ID", example = "10086")
    private Long userId;

    /**
     * 课程ID，前端可用于跳转到对应课件页面
     */
    @Schema(description = "课程ID，前端可据此跳转到对应课件页", example = "M1-MATH-001")
    private String courseId;

    /**
     * 提醒类型：REVIEW / RECALL / SPACED_REPETITION
     */
    @Schema(description = "提醒类型：REVIEW(常规复习) / RECALL(主动回忆) / SPACED_REPETITION(间隔重复)", example = "REVIEW")
    private String reminderType;

    /**
     * 复习内容摘要
     */
    @Schema(description = "复习内容摘要，简要描述需要复习的知识点", example = "二次函数的顶点式与一般式互化，重点练习配方法")
    private String content;

    /**
     * 计划复习日期
     */
    @Schema(description = "计划复习日期，由 AI 遗忘曲线算法计算得出", example = "2026-08-05")
    private LocalDate scheduledDate;

    /**
     * 优先级：0-普通，1-重要，2-紧急
     */
    @Schema(description = "优先级：0-普通，1-重要，2-紧急。数值越大排序越靠前", example = "1", allowableValues = {"0", "1", "2"})
    private Integer priority;

    /**
     * 是否已复习：0-未复习，1-已复习
     */
    @Schema(description = "是否已复习：0-未复习（显示在今日复习列表），1-已复习（显示在历史列表）", example = "0")
    private Integer isReviewed;

    /**
     * 复习完成时间，未复习时为 null
     */
    @Schema(description = "复习完成时间，未复习时为 null", example = "2026-08-05T14:30:00", nullable = true)
    private LocalDateTime reviewedAt;

    /**
     * 提醒创建时间
     */
    @Schema(description = "提醒创建时间", example = "2026-08-04T02:00:00")
    private LocalDateTime createdAt;
}
