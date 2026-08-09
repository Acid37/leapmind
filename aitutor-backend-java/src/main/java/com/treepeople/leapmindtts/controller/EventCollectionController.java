package com.treepeople.leapmindtts.controller;

import com.treepeople.leapmindtts.pojo.entity.EventCollection;
import com.treepeople.leapmindtts.pojo.result.ApiResponse;
import com.treepeople.leapmindtts.service.EventCollectionService;
import com.treepeople.leapmindtts.service.profile.security.ProfileActorResolver;
import com.treepeople.leapmindtts.exception.M6ApiException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataAccessException;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 事件采集控制器
 * <p>
 * 为 M1/M2/M4/M7 四个模块提供统一的事件采集 HTTP 接口。
 * 各模块在用户完成学习行为后，调用对应接口将事件数据写入 event_collections 表，
 * 供后续定时任务汇总分析并生成复习计划。
 * <p>
 * API 一览：
 * <pre>
 * POST   /api/events/collect                    — 采集单条事件（各模块主要使用）
 * POST   /api/events/collect/batch              — 批量采集事件（模块同步历史数据时使用）
 * GET    /api/events/unprocessed/{module}        — 查询模块未处理事件
 * GET    /api/events/user/{userId}               — 查询用户事件数据
 * PUT    /api/events/{eventId}/processed         — 标记事件已处理
 * </pre>
 * <p>
 * 调用方约定：
 * <ul>
 *   <li>M1/M2/M4/M7 模块只需关注 POST /collect 接口</li>
 *   <li>GET 和 PUT 接口主要由定时任务和管理后台使用</li>
 *   <li>eventData 为自由格式 JSON 字符串，本服务不做结构校验</li>
 * </ul>
 *
 * @author wuminxi
 * @date 2026-07-21
 */
@Slf4j
@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
@Deprecated(since = "2026-07", forRemoval = false)
@Tag(name = "Event Collection - 事件采集", description = "为 M1/M2/M4/M7 提供统一的事件采集上报接口")
public class EventCollectionController {

    private final EventCollectionService eventCollectionService;
    private final ProfileActorResolver profileActorResolver;

    /**
     * 采集单条事件
     * <p>
     * 这是 M1/M2/M4/M7 各模块日常使用的主接口。
     * 模块在用户完成一次学习行为后立即调用此接口上报事件。
     * <p>
     * 请求体核心字段：
     * <ul>
     *   <li>{@code module} — 必填，模块标识（M1/M2/M4/M7）</li>
     *   <li>{@code eventType} — 必填，事件类型（如 COURSE_COMPLETED）</li>
     *   <li>{@code userId} — 必填，关联用户ID</li>
     *   <li>{@code eventData} — 可选，JSON 格式的事件详细数据</li>
     *   <li>{@code eventTime} — 可选，默认取当前时间</li>
     * </ul>
     * <p>
     * 请求示例：
     * <pre>{@code
     * POST /api/events/collect
     * {
     *   "module": "M1",
     *   "eventType": "COURSE_COMPLETED",
     *   "userId": 1,
     *   "eventData": "{\"courseId\":\"abc123\",\"duration\":1800}",
     *   "eventTime": "2026-07-21T10:30:00"
     * }
     * }</pre>
     *
     * @param event 事件数据
     * @return HTTP 200 + 保存后的事件（含数据库生成的 ID）
     */
    @Operation(summary = "采集单条事件", description = "M1/M2/M4/M7 各模块在用户完成学习行为后调用，上报一条事件数据。"
            + " module、eventType、userId 为必填，eventData 为 JSON 字符串自由格式，eventTime 不填则默认当前时间。"
            + " 调用成功返回 HTTP 200 + 保存后的事件（含数据库自动生成的 id 和 createdAt）。")
    @PostMapping("/collect")
    public ResponseEntity<ApiResponse<EventCollection>> collectEvent(@RequestBody EventCollection event, HttpServletRequest servletRequest) {
        requireUserActor(servletRequest, event == null ? null : event.getUserId());
        log.info("采集事件，模块: {}, 类型: {}", event.getModule(), event.getEventType());

        try {
            EventCollection saved = eventCollectionService.collectEvent(event);
            return ResponseEntity.ok(ApiResponse.success(saved, "事件采集成功"));
        } catch (DataAccessException e) { throw e;
        } catch (Exception e) {
            log.error("事件采集失败: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, e.getMessage()));
        }
    }

    /**
     * 批量采集事件
     * <p>
     * 适用场景：模块初次接入时需要同步历史数据，或定时批量上报。
     * 请求体为事件对象的 JSON 数组。
     *
     * @param events 事件列表（JSON 数组）
     * @return HTTP 200 + 保存后的事件列表
     */
    @Operation(summary = "批量采集事件", description = "模块同步历史数据或定时批量上报，请求体为事件对象数组。"
            + " 单次最多 100 条，超出可能超时。每条事件的字段规则同单条采集接口。")
    @PostMapping("/collect/batch")
    public ResponseEntity<ApiResponse<List<EventCollection>>> collectEvents(@RequestBody List<EventCollection> events, HttpServletRequest servletRequest) {
        if (events == null || events.isEmpty() || events.size() > 100) throw new IllegalArgumentException("events batch size must be 1..100");
        EventCollection first = events.get(0);
        Long userId = first == null ? null : first.getUserId();
        requireUserActor(servletRequest, userId);
        if (events.stream().anyMatch(event -> event == null || !userId.equals(event.getUserId())))
            throw new IllegalArgumentException("all batch events must belong to the authenticated user");
        log.info("批量采集事件，共 {} 条", events.size());

        try {
            List<EventCollection> saved = eventCollectionService.collectEvents(events);
            return ResponseEntity.ok(ApiResponse.success(saved, "批量事件采集成功"));
        } catch (DataAccessException e) { throw e;
        } catch (Exception e) {
            log.error("批量事件采集失败: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, e.getMessage()));
        }
    }

    /**
     * 查询指定模块的未处理事件
     * <p>供定时任务/管理后台使用，返回该模块所有 processed=0 的事件</p>
     *
     * @param module 模块标识（M1/M2/M4/M7）
     * @return HTTP 200 + 未处理事件列表，按发生时间升序
     */
    @Operation(summary = "查询未处理事件", description = "按模块标识返回该模块所有 processed=0 的事件，按发生时间升序排列。供定时任务和管理后台使用。")
    @GetMapping("/unprocessed/{module}")
    public ResponseEntity<ApiResponse<List<EventCollection>>> getUnprocessedEvents(@PathVariable String module) {
        throw legacyServiceIdentityMissing();
    }

    /**
     * 查询用户的事件数据
     * <p>用于查看单个用户的学习行为时间线，按时间降序排列</p>
     *
     * @param userId 用户ID
     * @return HTTP 200 + 用户事件列表
     */
    @Operation(summary = "查询用户事件", description = "查询指定用户的所有学习行为事件，按时间降序排列。用于查看用户学习时间线。")
    @GetMapping("/user/{userId}")
    public ResponseEntity<ApiResponse<List<EventCollection>>> getUserEvents(
            @Parameter(description = "用户ID，必填", example = "10086", required = true) @PathVariable Long userId,
            HttpServletRequest servletRequest) {
        profileActorResolver.authorizeSelf(servletRequest, userId);
        log.info("查询用户 {} 的事件数据", userId);

        try {
            List<EventCollection> events = eventCollectionService.getUserEvents(userId);
            return ResponseEntity.ok(ApiResponse.success(events, "查询用户事件成功"));
        } catch (DataAccessException e) { throw e;
        } catch (Exception e) {
            log.error("查询用户 {} 事件失败: {}", userId, e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, e.getMessage()));
        }
    }

    /**
     * 标记事件为已处理
     * <p>定时任务处理完某事件后调用，设置 processed=1，processedAt=当前时间</p>
     *
     * @param eventId 事件ID
     * @return HTTP 200 + 成功消息
     */
    @Operation(summary = "标记事件已处理", description = "定时任务处理完某事件后调用，将 processed 置为 1，processedAt 设为当前时间。防止重复处理。")
    @PutMapping("/{eventId}/processed")
    public ResponseEntity<ApiResponse<String>> markAsProcessed(@PathVariable Long eventId) {
        throw legacyServiceIdentityMissing();
    }

    private void requireUserActor(HttpServletRequest request, Long userId) {
        if (userId == null || userId <= 0) throw new IllegalArgumentException("userId must be positive");
        profileActorResolver.authorizeSelf(request, userId);
    }
    private M6ApiException legacyServiceIdentityMissing() {
        return new M6ApiException(org.springframework.http.HttpStatus.FORBIDDEN, "PROFILE_ACCESS_DENIED", "Legacy service identity is not configured");
    }
}
