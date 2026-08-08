package com.treepeople.leapmindtts.controller.lesson;

import com.treepeople.leapmindtts.pojo.dto.*;
import com.treepeople.leapmindtts.service.admin.BulkSpeechService;
import com.treepeople.leapmindtts.service.impl.TtsBatchServiceImpl;
import com.treepeople.leapmindtts.service.lesson.NarrationBridgeService;
import com.treepeople.leapmindtts.service.lesson.PageLevelAudioService;
import com.treepeople.leapmindtts.service.lesson.VoiceDatabaseService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 批量语音合成控制器
 */
@RestController
@RequestMapping("/api/speech")
@RequiredArgsConstructor
@Slf4j
@Validated
public class BulkSpeechController {

    private final BulkSpeechService bulkSpeechService;
    private final PageLevelAudioService pageLevelAudioService;
    private final VoiceDatabaseService voiceDatabaseService;
    private final NarrationBridgeService narrationBridgeService;

    //use：批量音频合成
    /**
     * 批量语音合成接口
     *
     * 【M8 对接新增】Authorization 请求头：
     *   - 文档未给出固定内部服务 Token，M8 的 POST /api/virtual-teacher/tts 必须使用
     *     用户登录后获取到的 JWT，格式：Authorization: Bearer <token>。
     *   - 本方法会自动去掉 "Bearer " 前缀后，一路透传到 M8TtsClient.userJwt 参数，
     *     再放到 M8 TTS 请求的 Authorization 头。
     */
    @PostMapping("/bulk-synthesis")
    public ResponseEntity<BulkSynthesisResponse> bulkSynthesis(
            @Valid @RequestBody BulkSynthesisRequest request,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        log.info("接收到批量语音合成请求，PPT标题: {}, slides数量: {}, Authorization 存在: {}",
                request.getTitle(),
                request.getSlides() != null ? request.getSlides().size() : 0,
                authHeader != null && !authHeader.isBlank());

        // 从 Authorization: Bearer <token> 中提取纯 token，给 M8 TTS 接口鉴权用
        String userJwt = TtsBatchServiceImpl.extractBearer(authHeader);

        try {
            BulkSynthesisResponse response = bulkSpeechService.processBulkSynthesis(request, userJwt);
            log.info("批量语音合成请求处理成功，会话ID: {}", response.getCourseId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("批量语音合成请求处理失败", e);
            return ResponseEntity.internalServerError()
                    .body(BulkSynthesisResponse.builder()
                            .status("FAILED")
                            .message("处理失败: " + e.getMessage())
                            .build());
        }
    }

    // use:获取音频片段信息
    /**
     * 查询指定页面的音频信息和片段元数据
     * 数据库存储的是页面级音频，返回的是该页面的音频信息和片段元数据
     */
    @GetMapping("/ppt/{courseId}/page/{pageNumber}")
    public ResponseEntity<List<PPTAudioSegment>> getPageAudioSegments(
            @PathVariable @NotBlank String courseId,
            @PathVariable @NotNull Integer pageNumber) {

        log.info("查询页面音频信息，会话ID: {}, 页码: {}", courseId, pageNumber);

        try {
            List<PPTAudioSegment> segments = bulkSpeechService.getPageAudioSegments(courseId, pageNumber);
            if (segments.isEmpty()) {
                log.warn("未找到指定页面的音频信息，会话ID: {}, 页码: {}", courseId, pageNumber);
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(segments);
        } catch (Exception e) {
            log.error("查询页面音频信息失败，会话ID: {}, 页码: {}", courseId, pageNumber, e);
            return ResponseEntity.internalServerError().build();
        }
    }


    /**
     * 查询整个PPT的音频信息和统计数据
     */
    @GetMapping("/ppt/{courseId}")
    public ResponseEntity<PPTAudioInfo> getPPTAudioInfo(@PathVariable @NotBlank String courseId) {

        log.info("查询PPT音频信息，会话ID: {}", courseId);

        try {
            PPTAudioInfo audioInfo = bulkSpeechService.getPPTAudioInfo(courseId);
            if (audioInfo == null) {
                log.warn("未找到指定的PPT音频信息，会话ID: {}", courseId);
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(audioInfo);
        } catch (Exception e) {
            log.error("查询PPT音频信息失败，会话ID: {}", courseId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    //use:获取页面音频,合并音频
    /**
     * 播放指定页面的完整音频文件
     * 注意：现在数据库存储的是页面级音频，返回整个页面的合并音频
     */
    @GetMapping("/ppt/{courseId}/page/{pageNumber}/audio")
    public ResponseEntity<byte[]> playPageAudio(
            @PathVariable @NotBlank String courseId,
            @PathVariable @NotNull Integer pageNumber) {

        log.info("[新体系] 播放页面音频，会话ID: {}, 页码: {}", courseId, pageNumber);

        try {
            // 统一走新体系 NarrationBridge：读 teaching_contents → 解析 [AUDIO_URL:] → 下载 MinIO → 包装老分隔符
            byte[] bridged = narrationBridgeService.downloadAndWrapAudio(courseId, pageNumber);
            if (bridged != null && bridged.length > 0) {
                return ResponseEntity.ok()
                        .header("Content-Type", "audio/wav")
                        .header("Content-Length", String.valueOf(bridged.length))
                        .header("Cache-Control", "public, max-age=3600")
                        .body(bridged);
            }
            log.warn("未找到页面音频（可能 TTS 尚未生成，或 slide.notes 未带 [AUDIO_URL:]），courseId={}, page={}",
                    courseId, pageNumber);
            return ResponseEntity.notFound().build();

        } catch (Exception e) {
            log.error("播放页面音频失败，会话ID: {}, 页码: {}", courseId, pageNumber, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    //分离式批量语音合成接口

    /**
     * 批量文本预处理接口（不进行语音合成）
     * 处理文本润色和分句，保存到数据库等待审核
     */
    @PostMapping("/bulk-preprocessing")
    public ResponseEntity<BulkPreprocessingResponse> bulkPreprocessing(@Valid @RequestBody BulkSynthesisRequest request) {
        log.info("接收到批量文本预处理请求，PPT标题: {}, slides数量: {}", request.getTitle(), request.getSlides().size());

        try {
            BulkPreprocessingResponse response = bulkSpeechService.processBulkPreprocessing(request);
            log.info("批量文本预处理请求处理完成，会话ID: {}, 状态: {}", response.getCourseId(), response.getStatus());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("批量文本预处理请求处理失败", e);
            return ResponseEntity.internalServerError()
                    .body(BulkPreprocessingResponse.builder()
                            .status("FAILED")
                            .message("处理失败: " + e.getMessage())
                            .startTime(LocalDateTime.now())
                            .endTime(LocalDateTime.now())
                            .build());
        }
    }

    /**
     * 执行批量语音合成接口（基于已审核通过的文本）
     * 【M8 对接新增】需要 Authorization 请求头透传用户登录 JWT。
     */
    @PostMapping("/bulk-synthesis-execute/{courseId}")
    public ResponseEntity<BulkSynthesisResponse> executeBulkSynthesis(
            @PathVariable @NotBlank String courseId,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        log.info("接收到批量语音合成执行请求，会话ID: {}, Authorization 存在: {}",
                courseId, authHeader != null && !authHeader.isBlank());

        // 从 Authorization: Bearer <token> 中提取纯 token，给 M8 TTS 接口鉴权用
        String userJwt = TtsBatchServiceImpl.extractBearer(authHeader);

        try {
            BulkSynthesisResponse response = bulkSpeechService.executeBulkSynthesis(courseId, userJwt);
            log.info("批量语音合成执行完成，会话ID: {}, 状态: {}", courseId, response.getStatus());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("批量语音合成执行失败，会话ID: {}", courseId, e);
            return ResponseEntity.internalServerError()
                    .body(BulkSynthesisResponse.builder()
                            .courseId(courseId)
                            .status("FAILED")
                            .message("合成失败: " + e.getMessage())
                            .startTime(LocalDateTime.now())
                            .build());
        }
    }

}
