package com.treepeople.leapmindtts.controller.lesson;

import com.treepeople.leapmindtts.pojo.dto.FileUploadResponse;
import com.treepeople.leapmindtts.pojo.dto.LectureCreateRequest;
import com.treepeople.leapmindtts.pojo.dto.LectureProgressDTO;
import com.treepeople.leapmindtts.pojo.result.ApiResponse;
import com.treepeople.leapmindtts.pojo.vo.LectureVO;
import com.treepeople.leapmindtts.service.lesson.AISseService;
import com.treepeople.leapmindtts.service.lesson.FileStorageService;
import com.treepeople.leapmindtts.service.lesson.LectureProgressService;
import com.treepeople.leapmindtts.service.lesson.LectureService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.util.Map;

/**
 * M4 即时讲课 Controller（许沣睿）
 * 路径: /api/teaching/*
 * 响应: ApiResponse&lt;T&gt; 统一格式
 */
@Slf4j
@RestController
@RequestMapping("/api/teaching")
@RequiredArgsConstructor
public class TeachingController {

    private final FileStorageService fileStorageService;
    private final LectureService lectureService;
    private final LectureProgressService lectureProgressService;
    private final AISseService aiSseService;

    // ==================== 文件上传 ====================

    /**
     * 上传课件文件到 MinIO
     */
    @PostMapping("/{courseId}/upload")
    public ApiResponse<FileUploadResponse> uploadFile(
            @PathVariable String courseId,
            @RequestParam("file") MultipartFile file) {
        FileUploadResponse result = fileStorageService.uploadFile(file, courseId);
        return ApiResponse.success(result, "文件上传成功");
    }

    /**
     * 获取文件下载 URL
     */
    @GetMapping("/{courseId}/file-url")
    public ApiResponse<Map<String, String>> getFileUrl(@PathVariable String courseId) {
        LectureVO content = lectureService.getByCourseId(courseId);
        if (content.getSourceFilePath() == null || content.getSourceFilePath().isBlank()) {
            return ApiResponse.error(400, "尚未上传课件文件，请先调用 upload 接口");
        }
        String url = fileStorageService.getFileUrl(content.getSourceFilePath());
        return ApiResponse.success(Map.of("url", url), "获取文件URL成功");
    }

    // ==================== 讲课内容 CRUD ====================

    /**
     * 创建讲课内容
     */
    @PostMapping("/create")
    public ApiResponse<LectureVO> createLecture(@RequestBody @Valid LectureCreateRequest request) {
        LectureVO result = lectureService.createLecture(request);
        return ApiResponse.success(result, "创建成功");
    }

    /**
     * 查询讲课内容
     */
    @GetMapping("/{courseId}")
    public ApiResponse<LectureVO> getLecture(@PathVariable String courseId) {
        LectureVO result = lectureService.getByCourseId(courseId);
        return ApiResponse.success(result, "查询成功");
    }

    /**
     * 查询所有讲课内容
     */
    @GetMapping("/list")
    public ApiResponse<java.util.List<LectureVO>> listLectures(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        java.util.List<LectureVO> result = lectureService.listAll(page, pageSize);
        return ApiResponse.success(result, "查询成功");
    }

    /**
     * 删除讲课内容
     */
    @DeleteMapping("/{courseId}")
    public ApiResponse<Void> deleteLecture(@PathVariable String courseId) {
        lectureService.deleteByCourseId(courseId);
        return ApiResponse.success(null, "删除成功");
    }

    // ==================== AI 生成讲课内容 ====================

    /**
     * 同步生成讲课内容
     */
    @PostMapping("/{courseId}/generate")
    public ApiResponse<Map<String, String>> generateTeachingContent(
            @PathVariable String courseId,
            @RequestBody Map<String, Object> body) {
        String sourceText = (String) body.getOrDefault("source_text", "");
        @SuppressWarnings("unchecked")
        Map<String, Object> userProfile = (Map<String, Object>) body.get("user_profile");
        String result = aiSseService.generateTeachingContent(courseId, sourceText, userProfile);
        return ApiResponse.success(Map.of("content", result), "生成成功");
    }

    /**
     * SSE 流式生成讲课内容
     */
    @PostMapping(path = "/{courseId}/stream-generate",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamGenerateTeachingContent(
            @PathVariable String courseId,
            @RequestBody Map<String, Object> body) {
        String sourceText = (String) body.getOrDefault("source_text", "");
        @SuppressWarnings("unchecked")
        Map<String, Object> userProfile = (Map<String, Object>) body.get("user_profile");
        return aiSseService.streamGenerateTeachingContent(courseId, sourceText, userProfile);
    }

    // ==================== 进度管理 ====================

    /**
     * 保存讲课进度
     */
    @PostMapping("/{courseId}/progress")
    public ApiResponse<Void> saveProgress(
            @PathVariable String courseId,
            @RequestBody Map<String, Object> body) {
        LectureProgressDTO dto = new LectureProgressDTO();
        dto.setCourseId(courseId);
        dto.setCurrentPage(body.get("currentPage") != null ?
                ((Number) body.get("currentPage")).intValue() : 0);
        dto.setProgressMs(body.get("progressMs") != null ?
                ((Number) body.get("progressMs")).longValue() : 0L);
        dto.setStatus((String) body.getOrDefault("status", "PLAYING"));

        lectureProgressService.saveProgress(dto);
        return ApiResponse.success(null, "进度保存成功");
    }

    /**
     * 获取讲课进度
     */
    @GetMapping("/{courseId}/progress")
    public ApiResponse<LectureProgressDTO> getProgress(@PathVariable String courseId) {
        LectureProgressDTO result = lectureProgressService.getProgress(courseId);
        return ApiResponse.success(result, "获取成功");
    }

    // ==================== 回放管理 ====================

    /**
     * 保存回放快照
     */
    @PostMapping("/{courseId}/playback-snapshot")
    public ApiResponse<Void> savePlaybackSnapshot(
            @PathVariable String courseId,
            @RequestBody Map<String, Object> body) {
        String snapshot = (String) body.getOrDefault("snapshot", "{}");
        lectureProgressService.savePlaybackSnapshot(courseId, snapshot);
        return ApiResponse.success(null, "回放快照保存成功");
    }

    /**
     * 获取回放快照
     */
    @GetMapping("/{courseId}/playback-snapshot")
    public ApiResponse<Map<String, String>> getPlaybackSnapshot(@PathVariable String courseId) {
        String snapshot = lectureProgressService.getPlaybackSnapshot(courseId);
        return ApiResponse.success(
                Map.of("snapshot", snapshot != null ? snapshot : "{}"),
                "获取成功");
    }
}
