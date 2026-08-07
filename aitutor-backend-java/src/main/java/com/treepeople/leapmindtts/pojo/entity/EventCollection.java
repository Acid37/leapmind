package com.treepeople.leapmindtts.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
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
    @Schema(description = "事件主键ID（自增，不需要传）", example = "1", accessMode = Schema.AccessMode.READ_ONLY)
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 模块标识：M1 / M2 / M4 / M7
     */
    @Schema(description = "模块标识，必填。取值：M1(课程学习) / M2(练习答题) / M4(知识图谱) / M7(学习分析)", example = "M1", requiredMode = Schema.RequiredMode.REQUIRED)
    @TableField("module")
    private String module;

    /**
     * 事件类型，由各模块自定义
     * <p>示例：COURSE_COMPLETED / EXERCISE_SUBMITTED / KNOWLEDGE_MASTERED / STUDY_SESSION_END</p>
     */
    @Schema(description = "事件类型，必填。各模块自定义命名，建议格式：大写下划线。"
            + " 如 COURSE_COMPLETED(课程完成) / EXERCISE_SUBMITTED(练习提交) / KNOWLEDGE_MASTERED(知识点掌握) / STUDY_SESSION_END(学习会话结束)",
            example = "COURSE_COMPLETED", requiredMode = Schema.RequiredMode.REQUIRED)
    @TableField("event_type")
    private String eventType;

    /**
     * 关联用户ID，可为空（模块级事件不需要关联用户）
     */
    @Schema(description = "关联用户ID，必填。标识该事件属于哪个用户", example = "10086", requiredMode = Schema.RequiredMode.REQUIRED)
    @TableField("user_id")
    private Long userId;

    /**
     * 事件数据，以 JSON 格式存储
     * <p>各模块自行定义数据结构，服务层不做格式校验</p>
     */
    @Schema(description = "事件详细数据，JSON 字符串格式。各模块自由定义内部结构，推荐包含的关键字段："
            + " courseId(课程ID) / duration(时长秒) / score(得分) / knowledgePointIds(知识点ID列表) / pageNumber(当前页码) / extraInfo(扩展信息对象)。"
            + " 示例：{\"courseId\":\"abc123\",\"duration\":1800,\"score\":85,\"knowledgePointIds\":[1,2,3]}",
            example = "{\"courseId\":\"abc123\",\"duration\":1800,\"score\":85,\"knowledgePointIds\":[1,2,3]}", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @TableField("event_data")
    private String eventData;

    /**
     * 事件发生的实际时间，由调用方传入
     */
    @Schema(description = "事件发生的实际时间，格式 ISO 8601 (yyyy-MM-ddTHH:mm:ss)。不传则默认使用当前时间",
            example = "2026-08-04T10:30:00", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @TableField("event_time")
    private LocalDateTime eventTime;

    /**
     * 是否已被定时任务处理：0-未处理，1-已处理
     * <p>定时任务处理完成后将其置为 1，避免重复处理</p>
     */
    @Schema(description = "是否已处理（不需要传，服务端自动维护）：0-未处理，1-已处理", example = "0", accessMode = Schema.AccessMode.READ_ONLY)
    @TableField("processed")
    private Integer processed;

    /**
     * 处理完成时间，由定时任务在处理后写入
     */
    @Schema(description = "处理完成时间（不需要传，服务端自动维护）", accessMode = Schema.AccessMode.READ_ONLY)
    @TableField("processed_at")
    private LocalDateTime processedAt;

    /**
     * 记录创建时间，由数据库自动填充
     */
    @Schema(description = "记录创建时间（不需要传，数据库自动生成）", accessMode = Schema.AccessMode.READ_ONLY)
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
