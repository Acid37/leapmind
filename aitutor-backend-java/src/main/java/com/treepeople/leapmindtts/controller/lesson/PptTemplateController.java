package com.treepeople.leapmindtts.controller.lesson;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.treepeople.leapmindtts.pojo.dto.PptTemplateCreateDTO;
import com.treepeople.leapmindtts.pojo.dto.PptTemplateUpdateDTO;
import com.treepeople.leapmindtts.pojo.entity.PptTemplate;
import com.treepeople.leapmindtts.pojo.entity.TeachingContent;
import com.treepeople.leapmindtts.pojo.result.ApiResponse;
import com.treepeople.leapmindtts.pojo.vo.PptTemplateVO;
import com.treepeople.leapmindtts.service.PptTemplateService;
import com.treepeople.leapmindtts.service.TeachingContentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * PPT模板控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/lesson-prep/templates")
@RequiredArgsConstructor
@Tag(name = "PPT模板", description = "PPT模板管理相关接口")
public class PptTemplateController {

    private final PptTemplateService pptTemplateService;
    private final TeachingContentService teachingContentService;

    /**
     * 获取模板列表（系统模板 + 用户自己的模板）
     *
     * @param userId 用户ID
     * @return 模板列表
     */
    @GetMapping
    @Operation(summary = "获取模板列表", description = "查询系统模板和用户自己的模板列表")
    public ResponseEntity<ApiResponse<List<PptTemplateVO>>> listTemplates(
            @Parameter(description = "用户ID", required = true)
            @RequestParam Long userId) {
        log.info("查询模板列表，用户ID: {}", userId);
        try {
            List<PptTemplate> templates = pptTemplateService.listAll(userId);
            List<PptTemplateVO> voList = templates.stream()
                    .map(this::convertToVO)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(ApiResponse.success(voList, "查询模板列表成功"));
        } catch (Exception e) {
            log.error("查询模板列表失败: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, e.getMessage()));
        }
    }

    /**
     * 创建模板
     *
     * @param dto    模板创建参数
     * @param userId 用户ID
     * @return 创建的模板ID
     */
    @PostMapping
    @Operation(summary = "创建模板", description = "创建PPT模板，需校验配置JSON格式")
    public ResponseEntity<ApiResponse<Long>> createTemplate(
            @Parameter(description = "模板创建参数", required = true)
            @RequestBody PptTemplateCreateDTO dto,
            @Parameter(description = "用户ID", required = true)
            @RequestParam Long userId) {
        log.info("创建模板，用户ID: {}，模板名称: {}", userId, dto.getName());
        try {
            // 校验 configJson 格式
            String validationError = validateConfigJson(dto.getConfigJson());
            if (validationError != null) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error(400, validationError));
            }

            // 创建实体
            PptTemplate template = PptTemplate.builder()
                    .userId(userId)
                    .name(dto.getName())
                    .configJson(dto.getConfigJson())
                    .previewImageUrl(dto.getPreviewImageUrl())
                    .isSystem(0)
                    .createdAt(LocalDateTime.now())
                    .build();

            pptTemplateService.save(template);
            log.info("模板创建成功，ID: {}", template.getId());
            return ResponseEntity.ok(ApiResponse.success(template.getId(), "创建模板成功"));
        } catch (Exception e) {
            log.error("创建模板失败: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, e.getMessage()));
        }
    }

    /**
     * 更新模板
     *
     * @param templateId 模板ID
     * @param dto        模板更新参数
     * @param userId     当前用户ID
     * @return 操作结果
     */
    @PutMapping("/{templateId}")
    @Operation(summary = "更新模板", description = "更新PPT模板信息，仅允许修改自己的非系统模板")
    public ResponseEntity<ApiResponse<Void>> updateTemplate(
            @Parameter(description = "模板ID", required = true)
            @PathVariable Long templateId,
            @Parameter(description = "模板更新参数", required = true)
            @RequestBody PptTemplateUpdateDTO dto,
            @Parameter(description = "用户ID", required = true)
            @RequestParam Long userId) {
        log.info("更新模板，ID: {}，用户ID: {}", templateId, userId);
        try {
            // 查询模板是否存在
            PptTemplate template = pptTemplateService.getById(templateId);
            if (template == null) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error(400, "模板不存在"));
            }

            // 检查是否为系统模板，系统模板不允许修改
            if (template.getIsSystem() == 1) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error(400, "系统模板不允许修改"));
            }

            // 检查模板归属
            if (!template.getUserId().equals(userId)) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error(400, "只能修改自己的模板"));
            }

            // 校验新的 configJson 格式
            String validationError = validateConfigJson(dto.getConfigJson());
            if (validationError != null) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error(400, validationError));
            }

            // 更新字段
            template.setName(dto.getName());
            template.setConfigJson(dto.getConfigJson());
            template.setPreviewImageUrl(dto.getPreviewImageUrl());

            pptTemplateService.updateById(template);
            log.info("模板更新成功，ID: {}", templateId);
            return ResponseEntity.ok(ApiResponse.success(null, "更新模板成功"));
        } catch (Exception e) {
            log.error("更新模板失败: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, e.getMessage()));
        }
    }

    /**
     * 删除模板
     *
     * @param templateId 模板ID
     * @param userId     当前用户ID
     * @return 操作结果
     */
    @DeleteMapping("/{templateId}")
    @Operation(summary = "删除模板", description = "删除PPT模板，仅允许删除自己的非系统模板")
    public ResponseEntity<ApiResponse<Void>> deleteTemplate(
            @Parameter(description = "模板ID", required = true)
            @PathVariable Long templateId,
            @Parameter(description = "用户ID", required = true)
            @RequestParam Long userId) {
        log.info("删除模板，ID: {}，用户ID: {}", templateId, userId);
        try {
            // 查询模板是否存在
            PptTemplate template = pptTemplateService.getById(templateId);
            if (template == null) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error(400, "模板不存在"));
            }

            // 检查是否为系统模板，系统模板不允许删除
            if (template.getIsSystem() == 1) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error(400, "系统模板不允许删除"));
            }

            // 检查模板归属
            if (!template.getUserId().equals(userId)) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error(400, "只能删除自己的模板"));
            }

            pptTemplateService.removeById(templateId);
            log.info("模板删除成功，ID: {}", templateId);
            return ResponseEntity.ok(ApiResponse.success(null, "删除模板成功"));
        } catch (Exception e) {
            log.error("删除模板失败: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, e.getMessage()));
        }
    }

    /**
     * 应用模板到备课
     *
     * @param templateId 模板ID
     * @param prepId     备课ID
     * @return 操作结果
     */
    @PostMapping("/{templateId}/apply")
    @Operation(summary = "应用模板到备课", description = "将PPT模板的配色方案应用到指定备课内容的每页PPT中")
    public ResponseEntity<ApiResponse<Void>> applyTemplate(
            @Parameter(description = "模板ID", required = true)
            @PathVariable Long templateId,
            @Parameter(description = "备课ID", required = true)
            @RequestParam Long prepId) {
        log.info("应用模板，模板ID: {}，备课ID: {}", templateId, prepId);
        try {
            // 1. 查询模板，获取 configJson
            PptTemplate template = pptTemplateService.getById(templateId);
            if (template == null) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error(400, "模板不存在"));
            }

            // 2. 查询备课内容，获取 pptStructure
            TeachingContent teachingContent = teachingContentService.getByPrepId(prepId);
            if (teachingContent == null) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error(400, "备课内容不存在"));
            }
            if (teachingContent.getPptStructure() == null || teachingContent.getPptStructure().isBlank()) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error(400, "备课内容PPT结构为空"));
            }

            // 3. 解析 pptStructure，遍历 pages 数组，应用 colorScheme
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode pptStructure = objectMapper.readTree(teachingContent.getPptStructure());
            JsonNode colorScheme = objectMapper.readTree(template.getConfigJson()).get("colorScheme");

            if (colorScheme == null) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error(400, "模板配置缺少 colorScheme"));
            }
            JsonNode slidesArr = pptStructure.get("slides");
            if (slidesArr != null && slidesArr.isArray()) {
                for (JsonNode slide : slidesArr) {
                    JsonNode style = slide.get("style");
                    if (style != null && style.isObject()) {
                        ((com.fasterxml.jackson.databind.node.ObjectNode) style).put("primaryColor", colorScheme.get("primary").asText());
                        ((com.fasterxml.jackson.databind.node.ObjectNode) style).put("secondaryColor", colorScheme.get("secondary").asText());
                        ((com.fasterxml.jackson.databind.node.ObjectNode) style).put("backgroundColor", colorScheme.get("background").asText());
                        ((com.fasterxml.jackson.databind.node.ObjectNode) style).put("textColor", colorScheme.get("text").asText());
                        ((com.fasterxml.jackson.databind.node.ObjectNode) style).put("accentColor", colorScheme.get("accent").asText());
                    }
                }
            }

            // 5. 更新 pptStructure 和 templateId
            teachingContent.setPptStructure(objectMapper.writeValueAsString(pptStructure));
            teachingContent.setTemplateId(templateId);

            teachingContentService.updateById(teachingContent);
            log.info("模板应用成功，模板ID: {}，备课ID: {}", templateId, prepId);
            return ResponseEntity.ok(ApiResponse.success(null, "应用模板成功"));
        } catch (Exception e) {
            log.error("应用模板失败: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, e.getMessage()));
        }
    }

    /**
     * 将 PPT模板实体 转换为 视图对象
     *
     * @param template 模板实体
     * @return 模板视图对象
     */
    private PptTemplateVO convertToVO(PptTemplate template) {
        return PptTemplateVO.builder()
                .id(template.getId())
                .name(template.getName())
                .configJson(template.getConfigJson())
                .previewImageUrl(template.getPreviewImageUrl())
                .isSystem(template.getIsSystem())
                .createdAt(template.getCreatedAt())
                .build();
    }

    /**
     * 校验 configJson 格式（需包含 colorScheme、fontFamily、layout 字段）
     *
     * @param configJson 配置JSON
     * @return 校验失败返回错误信息，成功返回 null
     */
    private String validateConfigJson(String configJson) {
        if (configJson == null || configJson.isBlank()) {
            return "配置JSON不能为空";
        }
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode root = objectMapper.readTree(configJson);

            if (!root.has("colorScheme")) {
                return "配置JSON缺少 colorScheme 字段";
            }
            if (!root.has("fontFamily")) {
                return "配置JSON缺少 fontFamily 字段";
            }
            if (!root.has("layout")) {
                return "配置JSON缺少 layout 字段";
            }
            return null;
        } catch (JsonProcessingException e) {
            return "配置JSON格式不正确: " + e.getMessage();
        }
    }
}
