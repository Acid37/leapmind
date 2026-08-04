package com.treepeople.leapmindtts.controller.lesson;

import com.treepeople.leapmindtts.pojo.result.ApiResponse;
import com.treepeople.leapmindtts.pojo.vo.WeakPointsImprovementVO;
import com.treepeople.leapmindtts.service.lesson.WeakPointsImprovementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 薄弱点改善追踪控制器（M3 薄弱点模块）
 * <p>
 * 提供薄弱点改善报告查询接口：
 * <pre>
 * GET /api/weak-points/improvement-report?userId={userId}&period=month
 * </pre>
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "薄弱点改善追踪", description = "用户薄弱点改善报告接口")
public class WeakPointsImprovementController {

    private final WeakPointsImprovementService weakPointsImprovementService;

    /**
     * 查询薄弱点改善报告
     *
     * @param userId 用户ID（必填）
     * @param period 统计周期：week/month，默认 month
     * @return 改善报告
     */
    @GetMapping("/weak-points/improvement-report")
    @Operation(summary = "查询薄弱点改善报告", description = "对比本期与上期练习正确率，输出进步/退步知识点及整体改善度")
    public ResponseEntity<ApiResponse<WeakPointsImprovementVO>> getImprovementReport(
            @Parameter(description = "用户ID", required = true) @RequestParam Long userId,
            @Parameter(description = "统计周期：week/month，默认 month") @RequestParam(defaultValue = "month") String period) {
        log.info("查询改善报告: userId={}, period={}", userId, period);
        try {
            WeakPointsImprovementVO report = weakPointsImprovementService.getImprovementReport(userId, period);
            return ResponseEntity.ok(ApiResponse.success(report, "查询改善报告成功"));
        } catch (Exception e) {
            log.error("查询改善报告失败: userId={}, error={}", userId, e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        }
    }
}
