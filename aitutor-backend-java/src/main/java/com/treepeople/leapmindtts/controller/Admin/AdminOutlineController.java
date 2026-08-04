package com.treepeople.leapmindtts.controller.admin;

import com.treepeople.leapmindtts.annotation.AdminRequired;
import com.treepeople.leapmindtts.pojo.dto.ProjectOutlineCreateRequest;
import com.treepeople.leapmindtts.pojo.dto.ProjectOutlineUpdateRequest;
import com.treepeople.leapmindtts.pojo.result.ApiResponse;
import com.treepeople.leapmindtts.pojo.vo.ProjectOutlineVO;
import com.treepeople.leapmindtts.service.admin.ProjectOutlineService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 项目大纲管理控制器
 *
 * @ Package：com.treepeople.leapmindtts.controller.Admin
 * @ Project：leapMind-java
 * @ Description: 提供项目大纲的增删改查功能
 * @ Date：2025/11/9  17:30
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/outline")
@RequiredArgsConstructor
public class AdminOutlineController {

    private final ProjectOutlineService projectOutlineService;

    /**
     * 创建项目大纲
     */
    @PostMapping
    @AdminRequired
    public ApiResponse<ProjectOutlineVO> createOutline(@Valid @RequestBody ProjectOutlineCreateRequest request) {
        log.info("管理员创建项目大纲，项目ID: {}", request.getCourseId());

        try {
            ProjectOutlineVO outline = projectOutlineService.createOutline(request);
            return ApiResponse.success(outline, "项目大纲创建成功");
        } catch (Exception e) {
            log.error("创建项目大纲失败", e);
            return ApiResponse.error("创建项目大纲失败: " + e.getMessage());
        }
    }

    /**
     * 获取所有项目大纲
     */
    @GetMapping
    @AdminRequired
    public ApiResponse<List<ProjectOutlineVO>> getAllOutlines() {
        log.info("管理员查询所有项目大纲");

        try {
            List<ProjectOutlineVO> outlines = projectOutlineService.getAllOutlines();
            return ApiResponse.success(outlines, "查询成功");
        } catch (Exception e) {
            log.error("查询所有项目大纲失败", e);
            return ApiResponse.error("查询大纲列表失败: " + e.getMessage());
        }
    }

    /**
     * 根据课程ID查询项目大纲
     */
    @GetMapping("/{courseId}")
    @AdminRequired
    public ApiResponse<ProjectOutlineVO> getOutlineById(@PathVariable String courseId) {
        log.info("管理员查询项目大纲，ID: {}", courseId);

        try {
            ProjectOutlineVO outline = projectOutlineService.getOutlineById(courseId);
            return ApiResponse.success(outline, "查询成功");
        } catch (Exception e) {
            log.error("查询项目大纲失败，ID: {}", courseId, e);
            return ApiResponse.error("查询项目大纲失败: " + e.getMessage());
        }
    }

    /**
     * 根据项目ID查询大纲列表
     */
    @GetMapping("/project/{courseId}")
    @AdminRequired
    public ApiResponse<List<ProjectOutlineVO>> getOutlinesByCourseId(@PathVariable String courseId) {
        log.info("管理员根据项目ID查询大纲列表，项目ID: {}", courseId);

        try {
            List<ProjectOutlineVO> outlines = projectOutlineService.getOutlinesByCourseId(courseId);
            return ApiResponse.success(outlines, "查询成功");
        } catch (Exception e) {
            log.error("根据项目ID查询大纲列表失败，项目ID: {}", courseId, e);
            return ApiResponse.error("查询大纲列表失败: " + e.getMessage());
        }
    }

    /**
     * 更新项目大纲
     */
    @PutMapping
    @AdminRequired
    public ApiResponse<ProjectOutlineVO> updateOutline(@Valid @RequestBody ProjectOutlineUpdateRequest request) {
        log.info("管理员更新项目大纲，ID: {}", request.getId());

        try {
            ProjectOutlineVO outline = projectOutlineService.updateOutline(request);
            return ApiResponse.success(outline, "项目大纲更新成功");
        } catch (Exception e) {
            log.error("更新项目大纲失败，ID: {}", request.getId(), e);
            return ApiResponse.error("更新项目大纲失败: " + e.getMessage());
        }
    }

    /**
     * 删除项目大纲
     */
    @DeleteMapping("/{id}")
    @AdminRequired
    public ApiResponse<Boolean> deleteOutline(@PathVariable Integer id) {
        log.info("管理员删除项目大纲，ID: {}", id);

        try {
            boolean result = projectOutlineService.deleteOutline(id);
            return ApiResponse.success(result, "项目大纲删除成功");
        } catch (Exception e) {
            log.error("删除项目大纲失败，ID: {}", id, e);
            return ApiResponse.error("删除项目大纲失败: " + e.getMessage());
        }
    }
}
