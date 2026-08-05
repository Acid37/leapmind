package com.treepeople.leapmindtts.controller.user;

import com.treepeople.leapmindtts.pojo.dto.MarkReviewedRequest;
import com.treepeople.leapmindtts.pojo.result.ApiResponse;
import com.treepeople.leapmindtts.pojo.vo.ReviewReminderVO;
import com.treepeople.leapmindtts.service.profile.security.ProfileActorResolver;
import com.treepeople.leapmindtts.service.user.ReviewReminderService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户画像控制器
 * <p>
 * 负责用户个人学习数据相关的 API，包括复习提醒查询、标记已复习、复习历史等。
 * 路由前缀统一为 {@code /api/user-profile}，各接口通过 {@code {userId}} 路径参数区分用户。
 * <p>
 * API 一览：
 * <pre>
 * GET    /api/user-profile/{userId}/review-reminders   — 待复习提醒查询
 * POST   /api/user-profile/{userId}/mark-reviewed      — 标记已复习
 * GET    /api/user-profile/{userId}/review-history      — 复习历史查询
 * </pre>
 * <p>
 * 异常处理：Controller 层统一 try-catch，将业务异常转为 HTTP 400 + 错误消息。
 *
 * @author wuminxi
 * @date 2026-07-21
 */
@Slf4j
@RestController
@RequestMapping("/api/user-profile")
@RequiredArgsConstructor
public class UserProfileController {

    private final ReviewReminderService reviewReminderService;
    private final ProfileActorResolver profileActorResolver;

    /**
     * 待复习提醒查询
     * <p>
     * 返回用户当前所有待复习的提醒列表，按日期升序、优先级降序排列。
     * 包含逾期未复习的记录（scheduled_date 早于今天），前端应对逾期记录做视觉区分。
     * <p>
     * 请求示例：{@code GET /api/user-profile/1/review-reminders}
     *
     * @param userId 用户ID（路径参数）
     * @return HTTP 200 + 复习提醒列表；异常时返回 HTTP 400 + 错误消息
     */
    @GetMapping("/{userId}/review-reminders")
    public ResponseEntity<ApiResponse<List<ReviewReminderVO>>> getReviewReminders(
            @PathVariable Long userId,
            HttpServletRequest httpRequest) {
        log.info("查询用户 {} 的待复习提醒", userId);
        profileActorResolver.authorizeSelf(httpRequest, userId);

        try {
            List<ReviewReminderVO> reminders = reviewReminderService.getReviewReminders(userId);
            return ResponseEntity.ok(ApiResponse.success(reminders, "查询待复习提醒成功"));
        } catch (Exception e) {
            log.error("查询用户 {} 待复习提醒失败: {}", userId, e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, e.getMessage()));
        }
    }

    /**
     * 标记已复习
     * <p>
     * 将指定的复习提醒标记为已完成。请求体须包含提醒ID（reminderId）。
     * 服务层会校验提醒是否存在以及是否属于当前用户，防止越权操作。
     * <p>
     * 请求示例：{@code POST /api/user-profile/1/mark-reviewed}
     * <pre>{@code
     * {
     *   "reminderId": 100,
     *   "notes": "已掌握该知识点"
     * }
     * }</pre>
     *
     * @param userId  用户ID（路径参数，用于权限校验）
     * @param request 标记已复习请求体（@Valid 校验 reminderId 不为空）
     * @return HTTP 200 + 更新后的复习提醒；异常时返回 HTTP 400 + 错误消息
     */
    @PostMapping("/{userId}/mark-reviewed")
    public ResponseEntity<ApiResponse<ReviewReminderVO>> markReviewed(
            @PathVariable Long userId,
            HttpServletRequest httpRequest,
            @RequestBody @Valid MarkReviewedRequest request) {
        log.info("用户 {} 标记复习提醒 {} 为已复习", userId, request.getReminderId());
        profileActorResolver.authorizeSelf(httpRequest, userId);

        try {
            ReviewReminderVO result = reviewReminderService.markAsReviewed(userId, request);
            return ResponseEntity.ok(ApiResponse.success(result, "标记已复习成功"));
        } catch (Exception e) {
            log.error("用户 {} 标记已复习失败: {}", userId, e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, e.getMessage()));
        }
    }

    /**
     * 获取用户所有复习记录（包含已复习和未复习）
     * <p>未复习记录在前、已复习在后，前端可据此分段展示"待复习"和"已完成"两个区域</p>
     *
     * @param userId 用户ID（路径参数）
     * @return HTTP 200 + 复习记录完整列表
     */
    @GetMapping("/{userId}/review-history")
    public ResponseEntity<ApiResponse<List<ReviewReminderVO>>> getReviewHistory(
            @PathVariable Long userId,
            HttpServletRequest httpRequest) {
        log.info("查询用户 {} 的所有复习记录", userId);
        profileActorResolver.authorizeSelf(httpRequest, userId);

        try {
            List<ReviewReminderVO> reminders = reviewReminderService.getAllReminders(userId);
            return ResponseEntity.ok(ApiResponse.success(reminders, "查询复习记录成功"));
        } catch (Exception e) {
            log.error("查询用户 {} 复习记录失败: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, e.getMessage()));
        }
    }
}
