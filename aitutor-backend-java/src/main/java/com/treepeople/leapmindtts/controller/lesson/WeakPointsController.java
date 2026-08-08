package com.treepeople.leapmindtts.controller.lesson;

import com.treepeople.leapmindtts.pojo.dto.ExerciseRecordRequest;
import com.treepeople.leapmindtts.pojo.dto.PracticePlanRequest;
import com.treepeople.leapmindtts.pojo.result.ApiResponse;
import com.treepeople.leapmindtts.pojo.result.PageResult;
import com.treepeople.leapmindtts.pojo.vo.ExerciseVO;
import com.treepeople.leapmindtts.pojo.vo.KnowledgeGraphVO;
import com.treepeople.leapmindtts.pojo.vo.PracticePlanVO;
import com.treepeople.leapmindtts.pojo.vo.RecommendQuestionVO;
import com.treepeople.leapmindtts.pojo.vo.UserWeakPointVO;
import com.treepeople.leapmindtts.pojo.vo.WeakPointDetailVO;
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
     * 查询用户薄弱点列表（分页）
     *
     * @param userId  用户ID（必填）
     * @param topN    返回前N条（可选，与 page/size 互斥，取值 1-50）
     * @param subject 学科（可选）
     * @param status  状态过滤（可选）：ACTIVE/RESOLVED/IMPROVING
     * @param page    页码（默认1）
     * @param size    每页数量（默认20）
     * @return 薄弱点分页列表
     */
    @GetMapping("/weak-points")
    @Operation(summary = "查询用户薄弱点列表（分页/TOP-N）", description = "按用户ID查询薄弱点，可按学科和状态过滤，支持分页或 TopN")
    public ResponseEntity<ApiResponse<PageResult<UserWeakPointVO>>> getWeakPoints(
            @Parameter(description = "用户ID", required = true)
            @RequestParam Long userId,
            @Parameter(description = "返回前N条（与分页互斥，取值1-50）")
            @RequestParam(required = false) Integer topN,
            @Parameter(description = "学科（可选）")
            @RequestParam(required = false) String subject,
            @Parameter(description = "状态（可选）：ACTIVE/RESOLVED/IMPROVING")
            @RequestParam(required = false) String status,
            @Parameter(description = "页码（默认1）")
            @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页数量（默认20）")
            @RequestParam(defaultValue = "20") Integer size) {

        log.info("查询薄弱点: userId={}, topN={}, subject={}, status={}, page={}, size={}",
                userId, topN, subject, status, page, size);
        try {
            // topN 模式：前端需要"Top5 薄弱点"时，直接限制条数
            if (topN != null && topN > 0) {
                int n = Math.min(topN, 50); // 最大 50
                PageResult<UserWeakPointVO> result = weakPointsService
                        .getUserWeakPoints(userId, subject, status, 1, n);
                return ResponseEntity.ok(ApiResponse.success(result, "查询成功"));
            }
            PageResult<UserWeakPointVO> result = weakPointsService
                    .getUserWeakPoints(userId, subject, status, page, size);
            return ResponseEntity.ok(ApiResponse.success(result, "查询成功"));
        } catch (Exception e) {
            log.error("查询薄弱点失败: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, e.getMessage()));
        }
    }

    // ==================== 薄弱点详情 ====================

    /**
     * 查询单个薄弱点详情
     *
     * @param id 薄弱点记录ID
     * @return 薄弱点详情（含基本信息、近期错题、趋势、AI分析）
     */
    @GetMapping("/weak-points/{id}/detail")
    @Operation(summary = "查询薄弱点详情", description = "返回单个薄弱点的完整信息：基本数据、近期错题列表、趋势分析和AI评估")
    public ResponseEntity<ApiResponse<WeakPointDetailVO>> getWeakPointDetail(
            @Parameter(description = "薄弱点记录ID", required = true)
            @PathVariable Long id) {

        log.info("查询薄弱点详情: id={}", id);
        try {
            WeakPointDetailVO result = weakPointsService.getWeakPointDetail(id);
            return ResponseEntity.ok(ApiResponse.success(result, "查询成功"));
        } catch (Exception e) {
            log.error("查询薄弱点详情失败: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, e.getMessage()));
        }
    }

    /**
     * 根据薄弱点生成练习计划
     *
     * @param request 用户ID + 目标知识点列表
     * @return 练习计划（题目序列 + 预计时间）
     */
    @PostMapping("/weak-points/generate-practice-plan")
    @Operation(summary = "生成练习计划", description = "根据用户薄弱点列表生成针对性练习计划，按难度排序，含预计完成时间")
    public ResponseEntity<ApiResponse<PracticePlanVO>> generatePracticePlan(
            @RequestBody @Valid PracticePlanRequest request) {

        log.info("生成练习计划: userId={}, knowledgePoints={}", request.getUserId(), request.getKnowledgePoints());
        try {
            PracticePlanVO result = weakPointsService.generatePracticePlan(request);
            return ResponseEntity.ok(ApiResponse.success(result, "计划生成成功"));
        } catch (Exception e) {
            log.error("生成练习计划失败: {}", e.getMessage());
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
     * 根据知识点名称推荐具体题目（薄弱点详情页，内部调用 Python M3 引擎）
     * 根据知识点的薄弱程度调整推荐难度：HIGH→基础题, MEDIUM→中等题, LOW→提高题
     *
     * @param userId         用户ID（必填）
     * @param knowledgePoint 知识点名称（必填）
     * @param count          推荐数量，默认5
     * @return 推荐题目列表
     */
    @GetMapping("/weak-points/recommend-questions")
    @Operation(summary = "推荐题目(薄弱点详情页)", description = "根据具体知识点的薄弱程度推荐对应难度的练习题，内部调用Python M3引擎")
    public ResponseEntity<ApiResponse<List<RecommendQuestionVO>>> recommendQuestions(
            @Parameter(description = "用户ID", required = true)
            @RequestParam Long userId,
            @Parameter(description = "知识点名称", required = true)
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
