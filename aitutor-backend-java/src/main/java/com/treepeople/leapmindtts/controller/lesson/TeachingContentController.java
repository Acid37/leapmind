package com.treepeople.leapmindtts.controller.lesson;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.treepeople.leapmindtts.pojo.dto.TeachingContentUpdateDTO;
import com.treepeople.leapmindtts.pojo.entity.TeachingContent;
import com.treepeople.leapmindtts.pojo.result.ApiResponse;
import com.treepeople.leapmindtts.pojo.vo.TeachingContentVO;
import com.treepeople.leapmindtts.service.PptxExportService;
import com.treepeople.leapmindtts.service.PythonApiClient;
import com.treepeople.leapmindtts.service.TeachingContentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;


import java.util.ArrayList;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * 备课内容管理控制器 — 对应 M5 文档 4.1.4 备课内容管理服务
 */
@Slf4j
@RestController
@RequestMapping("/api/lesson-prep/contents")
@RequiredArgsConstructor
@Tag(name = "备课内容", description = "备课内容管理相关接口")
public class TeachingContentController {

    private final TeachingContentService teachingContentService;
    private final PptxExportService pptxExportService;
    private final PythonApiClient pythonApiClient;
    private final ObjectMapper om;
    /**
     * SSE 流式任务线程池（Bean 名 = sseStreamExecutor，Spring 按构造参数名匹配注入）。
     */
    private final Executor sseStreamExecutor;

    /** SSE 流式端点超时：35 分钟（三段备课管线极端情况 30min + 5min 缓冲） */
    private static final long SSE_STREAM_TIMEOUT_MS = 35L * 60L * 1000L;

    /** 草稿 */
    private static final String STATUS_DRAFT = "draft";
    /** 已发布 */
    private static final String STATUS_PUBLISHED = "published";
    /** 已归档 */
    private static final String STATUS_ARCHIVED = "archived";

    /**
     * 获取备课列表
     * <p>M5→M4 接口契约：返回 {total, items: [...]}，items 含 type/subject/grade/slideCount 等字段。</p>
     *
     * @param userId 用户ID
     * @param status 状态筛选（可选）
     * @param type   类型筛选（可选，目前固定 ppt）
     * @return 备课列表
     */
    @GetMapping
    @Operation(summary = "获取备课列表", description = "查询当前用户所有备课，按创建时间倒序，可选按状态和类型筛选")
    public ResponseEntity<ApiResponse<Map<String, Object>>> listContents(
            @Parameter(description = "用户ID", required = true)
            @RequestParam Long userId,
            @Parameter(description = "状态筛选（draft/published/archived）")
            @RequestParam(required = false) String status,
            @Parameter(description = "类型筛选（ppt）")
            @RequestParam(required = false) String type) {
        log.info("查询备课列表，用户ID: {}，状态: {}，类型: {}", userId, status, type);
        try {
            List<TeachingContent> list = teachingContentService.listByUserId(userId, status);
            List<TeachingContentVO> voList = list.stream()
                    .map(this::convertToVO)
                    .collect(Collectors.toList());
            Map<String, Object> data = new HashMap<>();
            data.put("total", voList.size());
            data.put("items", voList);
            return ResponseEntity.ok(ApiResponse.success(data, "查询备课列表成功"));
        } catch (Exception e) {
            log.error("查询备课列表失败: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, e.getMessage()));
        }
    }

    /**
     * 获取备课详情
     *
     * @param prepId 备课ID
     * @param userId 用户ID
     * @return 备课详情
     */
    @GetMapping("/{prepId}")
    @Operation(summary = "获取备课详情", description = "查询备课详情并校验是否属于当前用户")
    public ResponseEntity<ApiResponse<TeachingContentVO>> getContentDetail(
            @Parameter(description = "备课ID", required = true)
            @PathVariable Long prepId,
            @Parameter(description = "用户ID", required = true)
            @RequestParam Long userId) {
        log.info("查询备课详情，ID: {}，用户ID: {}", prepId, userId);
        try {
            TeachingContent content = teachingContentService.getByPrepId(prepId);
            if (content == null) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error(400, "备课内容不存在"));
            }
            if (!content.getUserId().equals(userId)) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error(400, "无权访问该备课"));
            }
            return ResponseEntity.ok(ApiResponse.success(convertToVO(content), "查询备课详情成功"));
        } catch (Exception e) {
            log.error("查询备课详情失败: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, e.getMessage()));
        }
    }

    /**
     * 更新备课
     *
     * @param prepId 备课ID
     * @param dto    更新参数
     * @param userId 用户ID
     * @return 操作结果
     */
    @PutMapping("/{prepId}")
    @Operation(summary = "更新备课", description = "更新备课内容或状态，含状态流转校验")
    public ResponseEntity<ApiResponse<Void>> updateContent(
            @Parameter(description = "备课ID", required = true)
            @PathVariable Long prepId,
            @Parameter(description = "更新参数", required = true)
            @RequestBody TeachingContentUpdateDTO dto,
            @Parameter(description = "用户ID", required = true)
            @RequestParam Long userId) {
        log.info("更新备课，ID: {}，用户ID: {}", prepId, userId);
        try {
            // 查询备课是否存在
            TeachingContent content = teachingContentService.getByPrepId(prepId);
            if (content == null) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error(400, "备课内容不存在"));
            }

            // 校验是否属于当前用户
            if (!content.getUserId().equals(userId)) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error(400, "只能修改自己的备课"));
            }

            String currentStatus = content.getStatus();
            String newStatus = dto.getStatus();
            boolean hasContentChange = dto.getTitle() != null || dto.getPptStructure() != null;

            // 状态流转校验
            if (STATUS_DRAFT.equals(currentStatus)) {
                // draft：可以修改内容，可以改为 published
                if (newStatus != null && !newStatus.isBlank()) {
                    if (!STATUS_PUBLISHED.equals(newStatus)) {
                        return ResponseEntity.badRequest()
                                .body(ApiResponse.error(400, "草稿状态只能发布为 published"));
                    }
                    content.setStatus(STATUS_PUBLISHED);
                }
                if (hasContentChange) {
                    if (dto.getTitle() != null) {
                        content.setTitle(dto.getTitle());
                    }
                    if (dto.getPptStructure() != null) {
                        content.setPptStructure(dto.getPptStructure());
                    }
                }
            } else if (STATUS_PUBLISHED.equals(currentStatus)) {
                // published：不能修改内容，只能改为 archived
                if (hasContentChange) {
                    return ResponseEntity.badRequest()
                            .body(ApiResponse.error(400, "已发布状态不能修改内容"));
                }
                if (newStatus != null && !newStatus.isBlank()) {
                    if (!STATUS_ARCHIVED.equals(newStatus)) {
                        return ResponseEntity.badRequest()
                                .body(ApiResponse.error(400, "已发布状态只能归档为 archived"));
                    }
                    content.setStatus(STATUS_ARCHIVED);
                } else {
                    return ResponseEntity.badRequest()
                            .body(ApiResponse.error(400, "已发布状态必须指定目标状态"));
                }
            } else if (STATUS_ARCHIVED.equals(currentStatus)) {
                // archived：不能做任何修改
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error(400, "已归档状态不能修改"));
            }

            teachingContentService.updateById(content);
            log.info("备课更新成功，ID: {}", prepId);
            return ResponseEntity.ok(ApiResponse.success(null, "更新备课成功"));
        } catch (Exception e) {
            log.error("更新备课失败: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, e.getMessage()));
        }
    }

    /**
     * 删除备课
     *
     * @param prepId 备课ID
     * @param userId 用户ID
     * @return 操作结果
     */
    @DeleteMapping("/{prepId}")
    @Operation(summary = "删除备课", description = "仅允许删除草稿状态的备课")
    public ResponseEntity<ApiResponse<Void>> deleteContent(
            @Parameter(description = "备课ID", required = true)
            @PathVariable Long prepId,
            @Parameter(description = "用户ID", required = true)
            @RequestParam Long userId) {
        log.info("删除备课，ID: {}，用户ID: {}", prepId, userId);
        try {
            // 查询备课是否存在
            TeachingContent content = teachingContentService.getByPrepId(prepId);
            if (content == null) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error(400, "备课内容不存在"));
            }

            // 校验是否属于当前用户
            if (!content.getUserId().equals(userId)) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error(400, "只能删除自己的备课"));
            }

            // 只有 draft 状态可以删除
            if (!STATUS_DRAFT.equals(content.getStatus())) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error(400, "仅草稿状态的备课可以删除"));
            }

            teachingContentService.removeByPrepId(prepId);
            log.info("备课删除成功，ID: {}", prepId);
            return ResponseEntity.ok(ApiResponse.success(null, "删除备课成功"));
        } catch (Exception e) {
            log.error("删除备课失败: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, e.getMessage()));
        }
    }

    /**
     * 导出PPT
     *
     * @param prepId 备课ID
     * @return 下载URL
     */
    @PostMapping("/{prepId}/export-ppt")
    @Operation(summary = "导出PPT", description = "导出备课内容为PPTX文件并返回下载URL")
    public ResponseEntity<ApiResponse<String>> exportPpt(
            @Parameter(description = "备课ID", required = true)
            @PathVariable Long prepId) {
        log.info("导出PPT，备课ID: {}", prepId);
        try {
            String downloadUrl = pptxExportService.export(prepId);
            log.info("PPT导出成功，备课ID: {}，下载URL: {}", prepId, downloadUrl);
            return ResponseEntity.ok(ApiResponse.success(downloadUrl, "导出PPT成功"));
        } catch (Exception e) {
            log.error("导出PPT失败: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, e.getMessage()));
        }
    }

    // ======================== VO 转换 ========================

    /**
     * 将 TeachingContent 实体转换为 TeachingContentVO。
     * <p>从 generated_content_json 解析 subject/grade/knowledgePointIds，
     * 从 ppt_structure 计算 slideCount，填充 M5→M4 接口契约字段。</p>
     */
    private TeachingContentVO convertToVO(TeachingContent content) {
        String subject = null;
        String grade = null;
        List<Map<String, Object>> knowledgePoints = null;

        // 从 generated_content_json 解析元数据
        String genJson = content.getGeneratedContentJson();
        if (genJson != null && !genJson.isBlank()) {
            try {
                JsonNode gen = om.readTree(genJson);
                if (gen.has("subject")) {
                    subject = gen.get("subject").asText();
                }
                if (gen.has("grade")) {
                    grade = gen.get("grade").asText();
                }
                if (gen.has("knowledgePointIds") && gen.get("knowledgePointIds").isArray()) {
                    knowledgePoints = new ArrayList<>();
                    for (JsonNode id : gen.get("knowledgePointIds")) {
                        Map<String, Object> kp = new HashMap<>();
                        kp.put("id", id.asInt());
                        knowledgePoints.add(kp);
                    }
                }
            } catch (Exception e) {
                // 旧格式 generated_content_json 可能是裸 syllabus 对象，忽略解析错误
            }
        }

        // 从 ppt_structure 计算 slideCount
        Integer slideCount = null;
        String pptJson = content.getPptStructure();
        if (pptJson != null && !pptJson.isBlank()) {
            try {
                JsonNode ppt = om.readTree(pptJson);
                if (ppt.isArray()) {
                    slideCount = ppt.size();
                } else if (ppt.has("slides") && ppt.get("slides").isArray()) {
                    slideCount = ppt.get("slides").size();
                }
            } catch (Exception e) {
                // ignore
            }
        }

        return TeachingContentVO.builder()
                .id(content.getId())
                .prepId(content.getPrepId())
                .userId(content.getUserId())
                .title(content.getTitle())
                .status(content.getStatus())
                .type("ppt")
                .subject(subject)
                .grade(grade)
                .slideCount(slideCount)
                .styleTemplate(content.getTemplateId() != null
                        ? String.valueOf(content.getTemplateId())
                        : "default")
                .knowledgePoints(knowledgePoints)
                .pptStructure(content.getPptStructure())
                .templateId(content.getTemplateId())
                .pptDownloadUrl(content.getPptDownloadUrl())
                .generatedContentJson(content.getGeneratedContentJson())
                .createdAt(content.getCreatedAt())
                .updatedAt(content.getUpdatedAt())
                .build();
    }

    // ======================== [跨端联桥] SSE 流式 ========================

    /**
     * [联桥-SSE] 流式生成备课 —— 三阶段管线（大纲→PPT→讲解词）实时推送到前端。
     *
     * <h3>事件流顺序（与 Python lesson_prep_service 对齐，event name = camelCase）</h3>
     * <pre>
     *   event: outline          { title, subject, grade, sections:[...] }          — Stage 1 完成：完整教学大纲
     *   event: section          { hourIndex, title, teachingGoals, ... } × N      — Stage 1：逐课时
     *   event: slide            { pageNum, type, title, bulletPoints, ... } × N   — Stage 2：逐页 PPT
     *   event: slidesDone       { totalPages }                                    — Stage 2 完成
     *   event: narration        { pageNum, narrationText, estimatedDurationSeconds } × N
     *                                                                              — Stage 3：逐页讲解词（同时自动回填 slide.notes）
     *   event: warn             { message, ... }                                  — 非致命警告
     *   event: error            { stage, message, errorCode }                     — 致命错误
     *   event: saved            { prepId, totalPages, slidesPreview }             — [Java 注入] Python done 之后 DB 持久化完成
     *   event: done             { prepId, totalPages, totalDurationSeconds, errors }  — Python done 事件
     * </pre>
     *
     * <p>注意：由于 SSE 标准前端浏览器 EventSource 仅支持 GET，前端需要用 fetch + ReadableStream 手动解析该端点。</p>
     */
    @PostMapping(value = "/generate/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "[联桥-SSE] 流式生成备课", description = "调用 Python 三阶段备课管线并 SSE 实时推送；完成后自动保存到 DB 返回 saved 事件")
    public SseEmitter generateLessonPrepStream(
            @RequestBody PythonApiClient.LessonPrepRequest request) {
        log.info("[联桥-SSE] 收到流式备课请求 title={}, subject={}", request.getTitle(), request.getSubject());

        // 1. 创建 SseEmitter 并设置超时（SseEmitter 超时从 Tomcat HTTP 线程释放角度控制）
        SseEmitter emitter = new SseEmitter(SSE_STREAM_TIMEOUT_MS);

        // 2. 发送"启动"事件，让前端知道流已接通（可选，避免前端长时间等待）
        try {
            Map<String, Object> startData = new HashMap<>();
            startData.put("status", "STARTED");
            startData.put("message", "AI 备课生成开始");
            startData.put("title", request.getTitle());
            startData.put("subject", request.getSubject());
            startData.put("timestamp", System.currentTimeMillis());
            emitter.send(SseEmitter.event().name("started").data(startData, MediaType.APPLICATION_JSON));
        } catch (IOException e) {
            log.warn("[联桥-SSE] 发送 started 事件失败（可能前端已断开）", e);
            emitter.completeWithError(e);
            return emitter;
        }

        // 3. 注册超时/错误回调：打日志 + emitter 完成（避免线程泄漏）
        emitter.onTimeout(() -> log.warn("[联桥-SSE] 端点超时 title={}", request.getTitle()));
        emitter.onError(t -> log.warn("[联桥-SSE] 端点错误 title={} err={}", request.getTitle(), t.toString()));
        emitter.onCompletion(() -> log.debug("[联桥-SSE] 端点完成 title={}", request.getTitle()));

        // 4. 提交流式任务到专用线程池（否则会占着 Tomcat HTTP 线程直到生成结束）
        sseStreamExecutor.execute(() -> {
            try {
                // 4a. 调用 PythonApiClient 流式读取并透传事件
                PythonApiClient.StreamedLessonPrepResult result =
                        pythonApiClient.streamLessonPrep(request, emitter);

                // 4b. DB 持久化：流式累积完毕后写入 teaching_contents
                TeachingContent saved = persistStreamResult(request, result);

                // 4c. 发送 saved 事件（表示 Java 侧已落盘，前端可以放心跳转编辑页）
                Map<String, Object> savedEvent = new HashMap<>();
                savedEvent.put("prepId", saved != null ? saved.getPrepId() : (long) result.getPrepId());
                savedEvent.put("totalPages", result.getTotalPages());
                savedEvent.put("slidesPreview", result.getSlides());
                savedEvent.put("errors", result.getErrors());
                savedEvent.put("savedAt", System.currentTimeMillis());
                try {
                    emitter.send(SseEmitter.event().name("saved").data(savedEvent, MediaType.APPLICATION_JSON));
                } catch (Exception ignore) {
                    // SseEmitter 可能已在 streamLessonPrep 的 done 事件中完成；忽略
                }
                emitter.complete();

            } catch (Exception e) {
                log.error("[联桥-SSE] 流式任务执行异常 title={}", request.getTitle(), e);
                try {
                    Map<String, Object> err = new HashMap<>();
                    err.put("stage", "all");
                    err.put("errorCode", "STREAM_TASK_EXCEPTION");
                    err.put("message", e.getMessage() != null ? e.getMessage() : "unknown exception");
                    err.put("timestamp", System.currentTimeMillis());
                    emitter.send(SseEmitter.event().name("error").data(err, MediaType.APPLICATION_JSON));
                } catch (Exception ignore) {}
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    /**
     * 将 Python 流式结果写入 teaching_contents 表。
     * 逻辑与非流式 generateLessonPrep 保持一致：slides → ppt_structure，syllabus → generated_content_json。
     * 如果 result.prepId == 0（Python 返回占位 0），则让 MyBatis 自增 id，并将 prepId 设置为等同 id 的值写入 DB 返回给前端。
     * 这里不做修改数据库约束的操作（遵循项目不修改 DB schema 约束）。
     */
    private TeachingContent persistStreamResult(PythonApiClient.LessonPrepRequest request,
                                                PythonApiClient.StreamedLessonPrepResult result) {
        try {
            long prepId = result.getPrepId() > 0 ? (long) result.getPrepId() : System.currentTimeMillis() / 1000L;

            TeachingContent content = TeachingContent.builder()
                    .prepId(prepId)
                    .userId((long) request.getUserId())
                    .title(request.getTitle())
                    .status(STATUS_DRAFT)
                    .pptStructure(om.writeValueAsString(result.getSlides()))
                    .generatedContentJson(result.getSyllabus() != null
                            ? om.writeValueAsString(result.getSyllabus())
                            : null)
                    .build();
            teachingContentService.save(content);

            log.info("[联桥-SSE] 备课已保存 prepId={}, slides={}", prepId, result.getSlides().size());
            return content;
        } catch (Exception e) {
            log.error("[联桥-SSE] 持久化失败 title={}", request.getTitle(), e);
            // 持久化失败不打流产出：前端会收到 done 但没 saved，用户可手动触发保存
            return null;
        }
    }

}
