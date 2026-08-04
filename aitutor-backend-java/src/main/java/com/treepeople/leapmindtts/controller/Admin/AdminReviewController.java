package com.treepeople.leapmindtts.controller.admin;

import com.treepeople.leapmindtts.pojo.dto.AdminReviewRequest;
import com.treepeople.leapmindtts.pojo.dto.BulkSynthesisResponse;
import com.treepeople.leapmindtts.pojo.dto.ReviewResponse;
import com.treepeople.leapmindtts.pojo.entity.LessonSession;
import com.treepeople.leapmindtts.service.admin.BulkSpeechService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理后台 - 审核管理控制器
 */
@Controller
@RequestMapping("api/admin/review")
@RequiredArgsConstructor
@Slf4j
@Validated
public class AdminReviewController {

    private final BulkSpeechService bulkSpeechService;

    /**
     * 获取待审核的会话列表 (API)
     */
    @GetMapping("/api/pending-sessions")
    @ResponseBody
    public ResponseEntity<List<LessonSession>> getPendingSessions() {
        log.info("管理后台获取待审核会话列表");

        try {
            List<LessonSession> pendingSessions = bulkSpeechService.getPendingReviewSessions();
            return ResponseEntity.ok(pendingSessions);
        } catch (Exception e) {
            log.error("管理后台获取待审核会话列表失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 根据courId获取待审核的会话列表 (API)
     */
    @GetMapping("/api/pending-sessions/{courseId}")
    @ResponseBody
    public ResponseEntity<LessonSession> getPendingSessionsByCourseId(@PathVariable @NotBlank String courseId) {
        log.info("管理后台获取待审核会话:{}",courseId);

        try {
            LessonSession pendingSessions = bulkSpeechService.getPendingReviewSessionsByCourseId(courseId);
            return ResponseEntity.ok(pendingSessions);
        } catch (Exception e) {
            log.error("管理后台获取待审核会话列表失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }


    /*@PostMapping("/api/sessions/{courseId}/review")
    @ResponseBody
    public ResponseEntity<ReviewResponse> reviewSession(
            @PathVariable @NotBlank String courseId,
            @Valid @RequestBody ReviewRequest request) {

        log.info("管理后台审核会话，会话ID: {}, 审核人: {}, 结果: {}",
                courseId, request.getReviewerId(), request.getApproved());

        try {
            ReviewResponse response = bulkSpeechService.reviewSession(
                    courseId,
                    request.getReviewerId(),
                    request.getApproved(),
                    request.getComments()
            );

            log.info("管理后台会话审核完成，会话ID: {}, 状态: {}", courseId, response.getStatus());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("管理后台会话审核失败，会话ID: {}", courseId, e);
            return ResponseEntity.internalServerError()
                    .body(ReviewResponse.builder()
                            .courseId(courseId)
                            .status("FAILED")
                            .message("审核失败: " + e.getMessage())
                            .build());
        }
    }*/

    /**
     * 管理员高级审核会话 (API) - 支持修改润色文本和其他属性
     */
    @PostMapping("/api/sessions/{courseId}/admin-review")
    @ResponseBody
    public ResponseEntity<ReviewResponse> adminReviewSession(
            @PathVariable @NotBlank String courseId,
            @Valid @RequestBody AdminReviewRequest request) {

        log.info("管理员高级审核会话，会话ID: {}, 审核人: {}, 结果: {}, 修改文本: {}",
                courseId, request.getReviewerId(), request.getApproved(),
                request.getUpdatedPolishedText() != null);

        try {
            ReviewResponse response = bulkSpeechService.adminReviewSession(courseId, request);

            log.info("管理员高级审核完成，会话ID: {}, 状态: {}", courseId, response.getStatus());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("管理员高级审核失败，会话ID: {}", courseId, e);
            return ResponseEntity.internalServerError()
                    .body(ReviewResponse.builder()
                            .courseId(courseId)
                            .status("FAILED")
                            .message("审核失败: " + e.getMessage())
                            .build());
        }
    }

    /**
     * 执行批量语音合成 (API)
     */
    @PostMapping("/api/sessions/{courseId}/synthesize")
    @ResponseBody
    public ResponseEntity<BulkSynthesisResponse> executeSynthesis(@PathVariable @NotBlank String courseId) {
        log.info("管理后台执行批量语音合成，会话ID: {}", courseId);

        try {
            BulkSynthesisResponse response = bulkSpeechService.executeBulkSynthesis(courseId);
            log.info("管理后台批量语音合成完成，会话ID: {}, 状态: {}", courseId, response.getStatus());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("管理后台批量语音合成失败，会话ID: {}", courseId, e);
            return ResponseEntity.internalServerError()
                    .body(BulkSynthesisResponse.builder()
                            .courseId(courseId)
                            .status("FAILED")
                            .message("合成失败: " + e.getMessage())
                            .build());
        }
    }

    /**
     * 获取所有状态的会话列表 (API)
     */
    @GetMapping("/api/sessions")
    @ResponseBody
    public ResponseEntity<List<LessonSession>> getAllSessions(@RequestParam(required = false) String status) {
        log.info("管理后台获取会话列表，状态过滤: {}", status);

        try {
            List<LessonSession> sessions;
            if (status != null && !status.trim().isEmpty()) {
                sessions = bulkSpeechService.getSessionsByStatus(status);
            } else {
                sessions = bulkSpeechService.getSessionsByStatus(null);
            }
            return ResponseEntity.ok(sessions);
        } catch (Exception e) {
            log.error("管理后台获取会话列表失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
