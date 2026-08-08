package com.treepeople.leapmindtts.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 标记已复习请求 DTO
 * <p>
 * 用户在前端点击"已复习"按钮时，前端将复习结果通过此请求体发送到后端。
 * 除基本标识外，还需上报复习结果、耗时和提示次数，
 * 用于 M6 画像系统生成 {@code mark_reviewed} 事件。
 * <p>
 * result 取值说明：
 * <ul>
 *   <li>{@code correct_without_hint} — 无需提示即正确作答</li>
 *   <li>{@code correct_with_hint} — 借助提示后正确作答</li>
 *   <li>{@code incorrect} — 作答错误</li>
 *   <li>{@code still_confused} — 仍然困惑，需要再次复习</li>
 *   <li>{@code postponed} — 暂时推迟复习</li>
 * </ul>
 *
 * @author wuminxi
 * @since 2026-07-21
 */
@Data
public class MarkReviewedRequest {

    /**
     * 复习提醒ID，必填
     * <p>对应 review_reminders 表的主键 id</p>
     */
    @Schema(description = "复习提醒ID，必填。前端从 review-reminders 列表的 id 字段获取", example = "100", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "复习提醒ID不能为空")
    private Long reminderId;

    /**
     * 复习备注（可选）
     * <p>用户可在此记录复习心得、掌握程度等信息，持久化到 review_reminders.notes</p>
     */
    @Schema(description = "复习备注（可选），可填写复习心得、掌握程度等，会持久化保存", example = "已完全掌握该知识点，练习正确率100%")
    private String notes;

    /**
     * 复习结果（必填，M6 事件所需）
     * <p>可选值：correct_without_hint / correct_with_hint / incorrect / still_confused / postponed</p>
     */
    @Schema(description = "复习结果，必填。可选值：correct_without_hint(无需提示正确) / correct_with_hint(提示后正确) / incorrect(错误) / still_confused(仍困惑) / postponed(推迟)",
            example = "correct_without_hint", requiredMode = Schema.RequiredMode.REQUIRED,
            allowableValues = {"correct_without_hint", "correct_with_hint", "incorrect", "still_confused", "postponed"})
    @NotNull(message = "复习结果不能为空")
    private String result;

    /**
     * 复习耗时（秒），必填
     * <p>范围 0-86400（24小时），0 表示瞬时完成</p>
     */
    @Schema(description = "复习耗时（秒），必填，范围 0-86400", example = "120", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "复习耗时不能为空")
    @Min(value = 0, message = "复习耗时不能小于0")
    @Max(value = 86400, message = "复习耗时不能超过86400秒")
    private Integer timeSpentSec;

    /**
     * 提示次数，必填
     * <p>范围 0-100，0 表示无需提示</p>
     */
    @Schema(description = "提示次数，必填，范围 0-100", example = "2", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "提示次数不能为空")
    @Min(value = 0, message = "提示次数不能小于0")
    @Max(value = 100, message = "提示次数不能超过100")
    private Integer hintCount;
}
