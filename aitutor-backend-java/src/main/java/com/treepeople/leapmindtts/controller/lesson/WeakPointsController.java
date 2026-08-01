package com.treepeople.leapmindtts.controller.lesson;

import com.treepeople.leapmindtts.pojo.dto.ExerciseRecordRequest;
import com.treepeople.leapmindtts.pojo.result.ApiResponse;
import com.treepeople.leapmindtts.pojo.vo.ExerciseVO;
import com.treepeople.leapmindtts.pojo.vo.KnowledgeGraphVO;
import com.treepeople.leapmindtts.pojo.vo.RecommendQuestionVO;
import com.treepeople.leapmindtts.pojo.vo.UserWeakPointVO;
import com.treepeople.leapmindtts.pojo.vo.WeakPointsAnalysisVO;
import com.treepeople.leapmindtts.service.lesson.WeakPointsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 薄弱点分析与练习推荐控制器
 * <p>
 * 提供 REST 接口给 M4（课程）/ M5（测评）/ M1（首页）模块调用
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "薄弱点分析", description = "用户薄弱点查询、AI分析和练习推荐接口")
public class WeakPointsController {

    private final WeakPointsService weakPointsService;

    // ==================== 薄弱点查询 ====================

    /**
     * 查询用户薄弱点列表
     *
     * @param userId  用户ID（必填）
     * @param subject 学科（可选）
     * @param status  状态过滤（可选）：ACTIVE/RESOLVED/IMPROVING
     * @return 薄弱点列表
     */
    @GetMapping("/weak-points")
    @Operation(summary = "查询用户薄弱点列表", description = "按用户ID查询薄弱点，可按学科和状态过滤")
    public ResponseEntity<ApiResponse<List<UserWeakPointVO>>> getWeakPoints(
            @Parameter(description = "用户ID", required = true)
            @RequestParam Long userId,
            @Parameter(description = "学科（可选）")
            @RequestParam(required = false) String subject,
            @Parameter(description = "状态（可选）：ACTIVE/RESOLVED/IMPROVING")
            @RequestParam(required = false) String status) {

        log.info("查询薄弱点: userId={}, subject={}, status={}", userId, subject, status);
        try {
            List<UserWeakPointVO> result = weakPointsService.getUserWeakPoints(userId, subject, status);
            return ResponseEntity.ok(ApiResponse.success(result, "查询成功"));
        } catch (Exception e) {
            log.error("查询薄弱点失败: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, e.getMessage()));
        }
    }

    // ==================== AI 分析 ====================

    /**
     * 触发 AI 综合分析
     * 调用 Python AI 服务生成薄弱点综合分析 + 个性化学习建议
     *
     * @param userId 用户ID
     * @return AI 分析结果
     */
    @PostMapping("/weak-points/{userId}/analysis")
    @Operation(summary = "触发AI综合分析", description = "调用Python AI服务生成薄弱点综合分析+个性化学习建议")
    public ResponseEntity<ApiResponse<WeakPointsAnalysisVO>> analyzeWeakPoints(
            @Parameter(description = "用户ID", required = true)
            @PathVariable Long userId) {

        log.info("触发AI分析: userId={}", userId);
        try {
            WeakPointsAnalysisVO result = weakPointsService.getOrCreateAnalysis(userId);
            return ResponseEntity.ok(ApiResponse.success(result, "分析完成"));
        } catch (Exception e) {
            log.error("AI分析失败: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, e.getMessage()));
        }
    }

    // ==================== 练习推荐 ====================

    /**
     * 获取推荐练习
     * 自动排除7天内已做练习，优先推荐已解决错题对应的知识点
     *
     * @param userId         用户ID（必填）
     * @param subject        学科（可选）
     * @param knowledgePoint 知识点（可选）
     * @param count          推荐数量，默认5
     * @return 推荐练习列表
     */
    @GetMapping("/exercises/recommend")
    @Operation(summary = "获取推荐练习", description = "自动去重（排除7天内已做）+ 优先已解决错题")
    public ResponseEntity<ApiResponse<List<ExerciseVO>>> recommendExercises(
            @Parameter(description = "用户ID", required = true)
            @RequestParam Long userId,
            @Parameter(description = "学科（可选）")
            @RequestParam(required = false) String subject,
            @Parameter(description = "知识点（可选）")
            @RequestParam(required = false) String knowledgePoint,
            @Parameter(description = "推荐数量，默认5")
            @RequestParam(defaultValue = "5") Integer count) {

        log.info("推荐练习: userId={}, subject={}, knowledgePoint={}, count={}", userId, subject, knowledgePoint, count);
        try {
            List<ExerciseVO> result = weakPointsService.recommendExercises(userId, subject, knowledgePoint, count);
            return ResponseEntity.ok(ApiResponse.success(result, "推荐成功"));
        } catch (Exception e) {
            log.error("推荐练习失败: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, e.getMessage()));
        }
    }

    // ==================== 练习记录 ====================

    /**
     * 记录练习结果
     * 同时自动更新薄弱点数据（正确率、状态等）
     *
     * @param request 练习记录
     * @return 操作结果
     */
    @PostMapping("/exercises/record")
    @Operation(summary = "记录练习结果", description = "记录练习结果并自动更新薄弱点数据")
    public ResponseEntity<ApiResponse<String>> recordExercise(
            @RequestBody @Valid ExerciseRecordRequest request) {

        log.info("记录练习: userId={}, exerciseId={}, isCorrect={}", request.getUserId(), request.getExerciseId(), request.getIsCorrect());
        try {
            weakPointsService.recordExerciseResult(request);
            return ResponseEntity.ok(ApiResponse.success("ok", "记录成功"));
        } catch (Exception e) {
            log.error("记录练习失败: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, e.getMessage()));
        }
    }

    // ==================== 推荐题目（薄弱点详情页） ====================

    /**
     * 根据知识点推荐具体题目（薄弱点详情页）
     * 根据知识点的薄弱程度调整推荐难度：HIGH→基础题, MEDIUM→中等题, LOW→提高题
     *
     * @param userId         用户ID（必填）
     * @param knowledgePoint 知识点（必填）
     * @param count          推荐数量，默认5
     * @return 推荐题目列表
     */
    @GetMapping("/weak-points/recommend-questions")
    @Operation(summary = "推荐题目(薄弱点详情页)", description = "根据具体知识点的薄弱程度推荐对应难度的练习题")
    public ResponseEntity<ApiResponse<List<RecommendQuestionVO>>> recommendQuestions(
            @Parameter(description = "用户ID", required = true)
            @RequestParam Long userId,
            @Parameter(description = "知识点", required = true)
            @RequestParam String knowledgePoint,
            @Parameter(description = "推荐数量，默认5")
            @RequestParam(defaultValue = "5") Integer count) {

        log.info("推荐题目: userId={}, knowledgePoint={}, count={}", userId, knowledgePoint, count);
        try {
            List<RecommendQuestionVO> result = weakPointsService.recommendQuestions(userId, knowledgePoint, count);
            return ResponseEntity.ok(ApiResponse.success(result, "推荐成功"));
        } catch (Exception e) {
            log.error("推荐题目失败: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, e.getMessage()));
        }
    }

    // ==================== 知识图谱 ====================

    /**
     * 获取用户知识图谱（知识图谱页）
     * 返回知识点节点和关联关系边，用于前端可视化展示
     *
     * @param userId  用户ID（必填）
     * @param subject 学科（可选，为空则返回所有学科）
     * @return 知识图谱（节点+边）
     */
    @GetMapping("/weak-points/knowledge-graph")
    @Operation(summary = "知识图谱", description = "获取用户知识图谱，包含知识点节点和前置依赖关系边")
    public ResponseEntity<ApiResponse<KnowledgeGraphVO>> getKnowledgeGraph(
            @Parameter(description = "用户ID", required = true)
            @RequestParam Long userId,
            @Parameter(description = "学科（可选，为空则返回所有学科）")
            @RequestParam(required = false) String subject) {

        log.info("知识图谱: userId={}, subject={}", userId, subject);
        try {
            KnowledgeGraphVO result = weakPointsService.getKnowledgeGraph(userId, subject);
            return ResponseEntity.ok(ApiResponse.success(result, "查询成功"));
        } catch (Exception e) {
            log.error("知识图谱查询失败: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, e.getMessage()));
        }
    }
}
