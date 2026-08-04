package com.treepeople.leapmindtts.controller.admin;

import com.treepeople.leapmindtts.annotation.AdminRequired;
import com.treepeople.leapmindtts.pojo.dto.AudioSegmentRequest;
import com.treepeople.leapmindtts.pojo.entity.AudioSegment;
import com.treepeople.leapmindtts.pojo.result.ApiResponse;
import com.treepeople.leapmindtts.service.admin.AudioSegmentService;
import com.treepeople.leapmindtts.service.admin.LessonSessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * @ Package：com.treepeople.leapmindtts.controller.Admin
 * @ Project：leapMind-java
 * @ Description: 管理员音频片段管理控制器
 * @ Date：2025/11/11  21:02
 */
@RequiredArgsConstructor
@Slf4j
@RestController
@RequestMapping("/api/admin/audio-segments")
public class AdminAudioSegmentsController {

    private final AudioSegmentService audioSegmentService;
    private final LessonSessionService lessonSessionService;

    /**
     * 查询所有音频片段
     */
    @AdminRequired(message = "查询所有音频片段需要管理员权限")
    @GetMapping
    public ResponseEntity<ApiResponse<List<AudioSegment>>> getAllAudioSegments() {
        log.info("管理员查询所有音频片段");
        try {
            List<AudioSegment> segments = audioSegmentService.list();
            return ResponseEntity.ok(ApiResponse.success(segments, "查询所有音频片段成功"));
        } catch (Exception e) {
            log.error("查询所有音频片段失败: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, "查询所有音频片段失败: " + e.getMessage()));
        }
    }

    /**
     * 根据课程ID查询音频片段
     */
    @AdminRequired(message = "根据课程ID查询音频片段需要管理员权限")
    @GetMapping("/course/{courseId}")
    public ResponseEntity<ApiResponse<List<AudioSegment>>> getAudioSegmentsByCourseId(@PathVariable String courseId) {
        log.info("管理员根据课程ID查询音频片段: {}", courseId);
        try {
            List<AudioSegment> segments = audioSegmentService.getByCourseId(courseId);
            return ResponseEntity.ok(ApiResponse.success(segments, "根据课程ID查询音频片段成功"));
        } catch (Exception e) {
            log.error("根据课程ID查询音频片段失败: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, "根据课程ID查询音频片段失败: " + e.getMessage()));
        }
    }

    /**
     * 根据ID查询单个音频片段
     */
    @AdminRequired(message = "查询音频片段详情需要管理员权限")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AudioSegment>> getAudioSegmentById(@PathVariable Long id) {
        log.info("管理员查询音频片段详情: {}", id);
        try {
            AudioSegment segment = audioSegmentService.getById(id);
            if (segment == null) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error(404, "音频片段不存在"));
            }
            return ResponseEntity.ok(ApiResponse.success(segment, "查询音频片段详情成功"));
        } catch (Exception e) {
            log.error("查询音频片段详情失败: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, "查询音频片段详情失败: " + e.getMessage()));
        }
    }

    /**
     * 更新音频片段
     */
    @AdminRequired(message = "更新音频片段需要管理员权限")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<AudioSegment>> updateAudioSegment(
            @PathVariable Long id,
            @Valid @RequestBody AudioSegmentRequest request) {
        log.info("管理员更新音频片段: {}", id);
        try {
            AudioSegment existingSegment = audioSegmentService.getById(id);
            if (existingSegment == null) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error(404, "音频片段不存在"));
            }

            // 更新字段
            existingSegment.setCourseId(request.getCourseId());
            existingSegment.setSegmentIndex(request.getSegmentIndex());
            existingSegment.setTextContent(request.getTextContent());
            existingSegment.setAudioData(request.getAudioData());
            existingSegment.setAudioFormat(request.getAudioFormat());
            existingSegment.setSampleRate(request.getSampleRate());
            existingSegment.setDuration(request.getDuration());

            if (request.getAudioData() != null) {
                existingSegment.setAudioSize((long) request.getAudioData().length);
            }

            boolean updated = audioSegmentService.updateById(existingSegment);
            if (updated) {
                return ResponseEntity.ok(ApiResponse.success(existingSegment, "更新音频片段成功"));
            } else {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error(400, "更新音频片段失败"));
            }
        } catch (Exception e) {
            log.error("更新音频片段失败: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, "更新音频片段失败: " + e.getMessage()));
        }
    }

    /**
     * 删除音频片段
     */
    @AdminRequired(message = "删除音频片段需要管理员权限")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteAudioSegment(@PathVariable Long id) {
        log.info("管理员删除音频片段: {}", id);
        try {
            AudioSegment segment = audioSegmentService.getById(id);
            if (segment == null) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error(404, "音频片段不存在"));
            }

            boolean deleted = audioSegmentService.removeById(id);
            if (deleted) {
                return ResponseEntity.ok(ApiResponse.success(null, "删除音频片段成功"));
            } else {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error(400, "删除音频片段失败"));
            }
        } catch (Exception e) {
            log.error("删除音频片段失败: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, "删除音频片段失败: " + e.getMessage()));
        }
    }

    /**
     * 批量删除指定课程的所有音频片段
     */
    @AdminRequired(message = "批量删除音频片段需要管理员权限")
    @DeleteMapping("/course/{courseId}")
    public ResponseEntity<ApiResponse<Void>> deleteAudioSegmentsByCourseId(@PathVariable String courseId) {
        log.info("管理员批量删除课程音频片段: {}", courseId);
        try {
            boolean deleted = audioSegmentService.deleteByCourseId(courseId);
            if (deleted) {
                return ResponseEntity.ok(ApiResponse.success(null, "批量删除音频片段成功"));
            } else {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error(400, "批量删除音频片段失败"));
            }
        } catch (Exception e) {
            log.error("批量删除音频片段失败: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, "批量删除音频片段失败: " + e.getMessage()));
        }
    }

    /**
     * 获取课程音频统计信息
     */
    @AdminRequired(message = "查询音频统计信息需要管理员权限")
    @GetMapping("/course/{courseId}/stats")
    public ResponseEntity<ApiResponse<AudioSegmentStats>> getAudioSegmentStats(@PathVariable String courseId) {
        log.info("管理员查询课程音频统计信息: {}", courseId);
        try {
            int count = audioSegmentService.countByCourseId(courseId);
            Long totalSize = audioSegmentService.getTotalAudioSize(courseId);
            Long totalDuration = audioSegmentService.getTotalDuration(courseId);

            AudioSegmentStats stats = AudioSegmentStats.builder()
                    .courseId(courseId)
                    .segmentCount(count)
                    .totalAudioSize(totalSize != null ? totalSize : 0L)
                    .totalDuration(totalDuration != null ? totalDuration : 0L)
                    .build();

            return ResponseEntity.ok(ApiResponse.success(stats, "查询音频统计信息成功"));
        } catch (Exception e) {
            log.error("查询音频统计信息失败: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, "查询音频统计信息失败: " + e.getMessage()));
        }
    }

    // 根据课程ID查询课程大纲是否润色--是否存在这个会话
    @GetMapping("/course/{courseId}/session")
    public ResponseEntity<ApiResponse<Boolean>> isSessionExist(@PathVariable String courseId) {
        log.info("管理员查询课程大纲是否润色: {}", courseId);
        try {
            boolean exist = audioSegmentService.isSessionExist(courseId);
            return ResponseEntity.ok(ApiResponse.success(exist, "查询课程大纲是否润色成功"));
        } catch (Exception e) {
            log.error("查询课程大纲是否润色失败: {}", e.getMessage());
            return ResponseEntity.ok(ApiResponse.error(400, "改课程暂时没有生成润色文本" ));
        }
    }

    // 根据课程ID查询会话
    @GetMapping("/course/{courseId}/audio-status")
    public ResponseEntity<ApiResponse<String>> getSessionStatus(@PathVariable String courseId) {
        log.info("管理员根据课程ID查询会话状态: {}", courseId);
        try {
            String status = lessonSessionService.getSessionStatus(courseId);
            if (status == null){
                status = "未开始";
            }
            return ResponseEntity.ok(ApiResponse.success(status, "根据课程ID查询会话状态成功"));
        } catch (Exception e) {
            log.error("查询根据课程ID查询会话状态失败: {}", e.getMessage());
            return ResponseEntity.ok(ApiResponse.error(400, "查根据课程ID查询会话状态失败: " + e.getMessage()));
        }
    }

    // 根据courseID查询是否进行语音合成
    @GetMapping("/course/{courseId}/audio-synthesis")
    public ResponseEntity<ApiResponse<Boolean>> isAudioSynthesisExist(@PathVariable String courseId) {
        log.info("管理员查询课程音频是否进行语音合成: {}", courseId);
        try {
            boolean exist = audioSegmentService.isAudioSynthesisExist(courseId);
            return ResponseEntity.ok(ApiResponse.success(exist, "查询课程音频是否进行语音合成成功"));
        } catch (Exception e) {
            log.error("查询课程音频是否进行语音合成失败: {}", e.getMessage());
            return ResponseEntity.ok(ApiResponse.error(400, "查询课程音频是否进行语音合成失败: " + e.getMessage()));
        }
    }


    /**
     * 音频片段统计信息DTO
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class AudioSegmentStats {
        private String courseId;
        private Integer segmentCount;
        private Long totalAudioSize;
        private Long totalDuration;
    }
}
