package com.treepeople.leapmindtts.controller.lesson;

import com.treepeople.leapmindtts.pojo.result.ApiResponse;
import com.treepeople.leapmindtts.service.impl.PptGenerationServiceImpl;
import com.treepeople.leapmindtts.service.impl.PptxExportServiceImpl;
import com.treepeople.leapmindtts.service.impl.SsePushServiceImpl;
import com.treepeople.leapmindtts.service.impl.TtsBatchServiceImpl;
import io.minio.MinioClient;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/ppt")
@RequiredArgsConstructor
@Slf4j
public class PptExportController {

    private final PptxExportServiceImpl pptxExportService;
    private final TtsBatchServiceImpl ttsBatchService;
    private final SsePushServiceImpl ssePushService;
    private final MinioClient minioClient;
    private final PptGenerationServiceImpl pptGenerationService;

    @Value("${minio.bucket-name:leapmind}")
    private String bucketName;

    @PostMapping("/export-from-json")
    public ResponseEntity<ApiResponse<Map<String, String>>> exportFromJson(
            @RequestBody Map<String, Object> request) {

        String pptStructureJson = (String) request.get("pptStructure");
        Long templateId = request.get("templateId") != null ?
                Long.valueOf(request.get("templateId").toString()) : null;
        String fileName = (String) request.get("fileName");

        log.info("根据 JSON 导出 PPT 请求，文件名: {}", fileName);

        if (pptStructureJson == null || pptStructureJson.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, "PPT 结构数据不能为空"));
        }

        try {
            String downloadUrl = pptxExportService.exportFromJson(pptStructureJson, templateId, fileName);

            Map<String, String> data = new HashMap<>();
            data.put("downloadUrl", downloadUrl);

            return ResponseEntity.ok(ApiResponse.success(data, "PPT 导出成功"));

        } catch (Exception e) {
            log.error("根据 JSON 导出 PPT 失败", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error(500, "PPT 导出失败: " + e.getMessage()));
        }
    }

    @PostMapping("/pipeline/{prepId}")
    public ResponseEntity<ApiResponse<Map<String, String>>> triggerPipeline(
            @PathVariable Long prepId,
            @RequestParam(required = false) String connectionId) {

        log.info("触发 PPT 生成管道，备课 ID: {}, 连接 ID: {}", prepId, connectionId);

        if (connectionId == null || connectionId.isEmpty()) {
            connectionId = "pipeline-" + UUID.randomUUID().toString().substring(0, 8);
        }

        try {
            PptGenerationServiceImpl.PipelineResult result =
                    pptGenerationService.executeAsync(prepId, connectionId);

            Map<String, String> data = new HashMap<>();
            data.put("connectionId", connectionId);
            data.put("message", "PPT 生成管道已启动");

            return ResponseEntity.ok(ApiResponse.success(data, "管道已启动"));

        } catch (Exception e) {
            log.error("管道启动失败，备课 ID: {}", prepId, e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error(500, "管道启动失败: " + e.getMessage()));
        }
    }

    @PostMapping("/generate-narration/{prepId}")
    public ResponseEntity<ApiResponse<Map<String, String>>> generateNarration(
            @PathVariable Long prepId,
            @RequestParam(required = false) String connectionId) {

        log.info("开始生成 PPT 旁白，备课 ID: {}, 连接 ID: {}", prepId, connectionId);

        if (connectionId == null || connectionId.isEmpty()) {
            connectionId = "tts-" + UUID.randomUUID().toString().substring(0, 8);
        }

        try {
            String taskId = ttsBatchService.generateNarrationsAsync(prepId, null, connectionId);

            Map<String, String> data = new HashMap<>();
            data.put("taskId", taskId);
            data.put("connectionId", connectionId);

            return ResponseEntity.ok(ApiResponse.success(data, "旁白生成任务已启动"));

        } catch (Exception e) {
            log.error("旁白生成启动失败，备课 ID: {}", prepId, e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error(500, "任务启动失败: " + e.getMessage()));
        }
    }

    @GetMapping("/task-status/{taskId}")
    public ResponseEntity<ApiResponse<TtsBatchServiceImpl.TaskStatus>> getTaskStatus(
            @PathVariable String taskId) {

        log.info("查询任务状态，任务 ID: {}", taskId);

        TtsBatchServiceImpl.TaskStatus status = ttsBatchService.getTaskStatus(taskId);

        if (status == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(ApiResponse.success(status, "查询成功"));
    }

    @PostMapping("/cancel-task/{taskId}")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> cancelTask(
            @PathVariable String taskId) {

        log.info("取消任务，任务 ID: {}", taskId);

        boolean success = ttsBatchService.cancelTask(taskId);

        Map<String, Boolean> data = new HashMap<>();
        data.put("success", success);

        return ResponseEntity.ok(ApiResponse.success(data,
                success ? "任务取消成功" : "任务不存在或已完成"));
    }

    @GetMapping("/preview")
    public ResponseEntity<ApiResponse<Map<String, String>>> getPreviewUrl(
            @RequestParam String objectName,
            @RequestParam(defaultValue = "3600") int expiry) {

        log.info("获取预览 URL，对象: {}, 过期: {}s", objectName, expiry);

        try {
            String previewUrl = minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucketName)
                            .object(objectName)
                            .expiry(expiry, TimeUnit.SECONDS)
                            .build()
            );

            Map<String, String> data = new HashMap<>();
            data.put("previewUrl", previewUrl);

            return ResponseEntity.ok(ApiResponse.success(data, "获取成功"));

        } catch (Exception e) {
            log.error("获取预览 URL 失败", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error(500, "获取失败: " + e.getMessage()));
        }
    }

    @GetMapping(value = "/sse/{connectionId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter connectSse(@PathVariable String connectionId) {
        log.info("注册 SSE 连接，连接 ID: {}", connectionId);

        SseEmitter emitter = ssePushService.registerConnection(connectionId);

        try {
            Map<String, Object> initialData = new HashMap<>();
            initialData.put("connectionId", connectionId);
            initialData.put("message", "连接成功");
            initialData.put("timestamp", System.currentTimeMillis());

            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data(initialData));
        } catch (Exception e) {
            log.error("发送初始消息失败", e);
        }

        return emitter;
    }

    @GetMapping("/sse/status/{connectionId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> checkSseStatus(
            @PathVariable String connectionId) {

        boolean exists = ssePushService.hasConnection(connectionId);

        Map<String, Object> data = new HashMap<>();
        data.put("connectionId", connectionId);
        data.put("connected", exists);
        data.put("totalConnections", ssePushService.getConnectionCount());

        return ResponseEntity.ok(ApiResponse.success(data, exists ? "连接存在" : "连接不存在"));
    }

    @DeleteMapping("/sse/{connectionId}")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> disconnectSse(
            @PathVariable String connectionId) {

        log.info("断开 SSE 连接，连接 ID: {}", connectionId);

        ssePushService.removeConnection(connectionId);

        Map<String, Boolean> data = new HashMap<>();
        data.put("success", true);

        return ResponseEntity.ok(ApiResponse.success(data, "连接已断开"));
    }
}
