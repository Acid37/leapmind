package com.treepeople.leapmindtts.controller.lesson;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.treepeople.leapmindtts.pojo.entity.TeachingContent;
import com.treepeople.leapmindtts.pojo.result.ApiResponse;
import com.treepeople.leapmindtts.service.PythonApiClient;
import com.treepeople.leapmindtts.service.TeachingContentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * AI 备课生成控制器 — 对应 M5 文档 4.1.1 接口 POST /api/lesson-prep/generate
 */
@Slf4j
@RestController
@RequestMapping("/api/lesson-prep")
@RequiredArgsConstructor
@Tag(name = "AI备课生成", description = "AI备课内容生成接口")
public class LessonPrepController {

    private final PythonApiClient pythonApiClient;
    private final TeachingContentService teachingContentService;
    private final ObjectMapper om;

    private static final String STATUS_DRAFT = "draft";

    /**
     * [联桥] 生成备课 — 调用 Python AI 服务生成大纲+PPT结构，保存到 DB。
     *
     * <h3>完整链路</h3>
     * <ol>
     *   <li>前端 POST /api/lesson-prep/generate（传 title/subject/grade/knowledgePointIds 等）</li>
     *   <li>Java → Python POST /api/internal/ai/generate-lesson-prep</li>
     *   <li>Python 生成 syllabus + slides（已写入 DB）并返回 JSON</li>
     *   <li>Java 将 slides（camelCase）写入 teaching_contents.ppt_structure，
     *       syllabus + 元数据写入 generated_content_json</li>
     *   <li>返回 prepId + slides 预览给前端</li>
     * </ol>
     */
    @PostMapping("/generate")
    @Operation(summary = "[联桥] 生成备课", description = "调用 Python AI 服务生成备课内容并保存到数据库")
    public ResponseEntity<ApiResponse<Map<String, Object>>> generateLessonPrep(
            @RequestBody PythonApiClient.LessonPrepRequest request) {
        log.info("[联桥] 收到备课生成请求: title={}, subject={}", request.getTitle(), request.getSubject());

        try {
            // Step 1: 调用 Python 生成备课大纲 + PPT 结构
            PythonApiClient.LessonPrepResponse pythonResult =
                    pythonApiClient.generateLessonPrep(request);

            // Step 2: 将 Python 返回的 slides（已转 camelCase）写入 ppt_structure，
            //         syllabus + 元数据（subject/grade/knowledgePointIds）写入 generated_content_json
            Map<String, Object> generatedContent = new HashMap<>();
            generatedContent.put("syllabus", pythonResult.getSyllabus());
            generatedContent.put("subject", request.getSubject());
            generatedContent.put("grade", request.getGrade());
            generatedContent.put("knowledgePointIds", request.getKnowledgePointIds());

            TeachingContent content = TeachingContent.builder()
                    .prepId((long) pythonResult.getPrepId())
                    .userId((long) request.getUserId())
                    .title(request.getTitle())
                    .status(STATUS_DRAFT)
                    .pptStructure(om.writeValueAsString(pythonResult.getSlides()))
                    .generatedContentJson(om.writeValueAsString(generatedContent))
                    .build();
            teachingContentService.save(content);

            Map<String, Object> data = new HashMap<>();
            data.put("prepId", pythonResult.getPrepId());
            data.put("totalPages", pythonResult.getTotalPages());
            data.put("slidesPreview", pythonResult.getSlides());

            log.info("[联桥] 备课生成完成, prepId={}, totalPages={}",
                    pythonResult.getPrepId(), pythonResult.getTotalPages());
            return ResponseEntity.ok(ApiResponse.success(data, "备课生成成功"));

        } catch (Exception e) {
            log.error("[联桥] 备课生成失败", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error(500, "备课生成失败: " + e.getMessage()));
        }
    }
}
