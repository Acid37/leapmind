package com.treepeople.leapmindtts.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 复习提醒实体类
 * <p>
 * 用于存储基于艾宾浩斯遗忘曲线的复习提醒数据。
 * 每条记录代表一次需要用户在指定日期完成的复习任务，
 * 由定时任务调用 Python AI 服务计算生成。
 * <p>
 * 复习类型说明：
 * <ul>
 *   <li>REVIEW - 常规复习，基于遗忘曲线计算的时间节点</li>
 *   <li>RECALL - 主动回忆，鼓励用户在不看材料的情况下回忆内容</li>
 *   <li>SPACED_REPETITION - 间隔重复，基于间隔重复算法的最优复习点</li>
 * </ul>
 *
 * @author wuminxi
 * @date 2026-07-21
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("review_reminders")
public class ReviewReminder {

    /**
     * 主键ID，自增
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID，关联 users 表
     */
    @TableField("user_id")
    private Long userId;

    /**
     * 课程（会话）ID，关联 lesson_sessions 表的 course_id
     */
    @TableField("course_id")
    private String courseId;

    /**
     * 提醒类型
     * <p>可选值：REVIEW / RECALL / SPACED_REPETITION</p>
     */
    @TableField("reminder_type")
    private String reminderType;

    /**
     * 复习内容摘要，简略展示待复习的知识点
     */
    @TableField("content")
    private String content;

    /**
     * 计划复习日期，由 AI 算法计算得出
     */
    @TableField("scheduled_date")
    private LocalDate scheduledDate;

    /**
     * 优先级：0-普通，1-重要，2-紧急
     * <p>用于前端排序展示，数值越大越靠前</p>
     */
    @TableField("priority")
    private Integer priority;

    /**
     * 是否已复习：0-未复习，1-已复习
     */
    @TableField("is_reviewed")
    private Integer isReviewed;

    /**
     * 复习完成时间，用户点击"标记已复习"时写入
     */
    @TableField("reviewed_at")
    private LocalDateTime reviewedAt;

    /**
     * 记录创建时间，由数据库自动填充
     */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 记录更新时间，每次修改时由数据库自动更新
     */
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
