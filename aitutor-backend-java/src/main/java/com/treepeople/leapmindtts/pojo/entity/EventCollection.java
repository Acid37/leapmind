package com.treepeople.leapmindtts.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 事件采集实体类
 * <p>
 * 统一承接 M1/M2/M4/M7 各模块上报的用户学习行为事件，
 * 作为复习计算的原始数据源。各模块通过
 * {@code POST /api/events/collect} 接口将事件写入此表，
 * 定时任务定期汇总后交由 Python AI 服务生成复习计划。
 * <p>
 * 模块分工：
 * <ul>
 *   <li><b>M1</b> - 课程学习模块：记录用户学习课件、观看视频等行为</li>
 *   <li><b>M2</b> - 练习答题模块：记录用户做题、答题正确率等行为</li>
 *   <li><b>M4</b> - 知识图谱模块：记录知识点掌握度变化事件</li>
 *   <li><b>M7</b> - 学习分析模块：记录学习时长、专注度等分析数据</li>
 * </ul>
 *
 * @author wuminxi
 * @date 2026-07-21
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("event_collections")
public class EventCollection {

    /**
     * 主键ID，自增
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 模块标识：M1 / M2 / M4 / M7
     */
    @TableField("module")
    private String module;

    /**
     * 事件类型，由各模块自定义
     * <p>示例：COURSE_COMPLETED / EXERCISE_SUBMITTED / KNOWLEDGE_MASTERED / STUDY_SESSION_END</p>
     */
    @TableField("event_type")
    private String eventType;

    /**
     * 关联用户ID，可为空（模块级事件不需要关联用户）
     */
    @TableField("user_id")
    private Long userId;

    /**
     * 事件数据，以 JSON 格式存储
     * <p>各模块自行定义数据结构，服务层不做格式校验</p>
     */
    @TableField("event_data")
    private String eventData;

    /**
     * 事件发生的实际时间，由调用方传入
     */
    @TableField("event_time")
    private LocalDateTime eventTime;

    /**
     * 是否已被定时任务处理：0-未处理，1-已处理
     * <p>定时任务处理完成后将其置为 1，避免重复处理</p>
     */
    @TableField("processed")
    private Integer processed;

    /**
     * 处理完成时间，由定时任务在处理后写入
     */
    @TableField("processed_at")
    private LocalDateTime processedAt;

    /**
     * 记录创建时间，由数据库自动填充
     */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
