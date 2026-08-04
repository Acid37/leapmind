package com.treepeople.leapmindtts.controller.admin;


import com.treepeople.leapmindtts.annotation.AdminRequired;
import com.treepeople.leapmindtts.pojo.dto.PptSlideCreateRequest;
import com.treepeople.leapmindtts.pojo.dto.PptSlideUpdateRequest;
import com.treepeople.leapmindtts.pojo.entity.PptSlide;
import com.treepeople.leapmindtts.pojo.result.ApiResponse;
import com.treepeople.leapmindtts.service.admin.PptSlideService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * @ Package：com.treepeople.leapmindtts.controller.Admin
 * @ Project：leapMind-java
 * @ Description: PPT幻灯片管理控制器
 * @ Date：2025/11/11  10:36
 */
@Slf4j
@RestController
@RequestMapping("api/admin/ppt")
@RequiredArgsConstructor
public class AdminPPTController {

    private final PptSlideService pptSlideService;

    /**
     * 查询所有幻灯片
     *
     * @return 幻灯片列表
     */
    @AdminRequired(message = "查询幻灯片列表需要管理员权限")
    @GetMapping("/slides")
    public ApiResponse<List<PptSlide>> getAllSlides() {
        log.info("查询所有幻灯片");
        try {
            List<PptSlide> slides = pptSlideService.list();
            return ApiResponse.success(slides, "查询成功");
        } catch (Exception e) {
            log.error("查询所有幻灯片失败", e);
            return ApiResponse.error("查询失败: " + e.getMessage());
        }
    }

    /**
     * 根据项目ID查询幻灯片
     *
     * @param courseId 项目ID
     * @return 幻灯片列表
     */
    @AdminRequired(message = "根据项目ID查询幻灯片需要管理员权限")
    @GetMapping("/slides/project/{courseId}")
    public ApiResponse<List<PptSlide>> getSlidesByCourseId(@PathVariable String courseId) {
        log.info("根据项目ID查询幻灯片: {}", courseId);
        try {
            List<PptSlide> slides = pptSlideService.getByCourseId(courseId);
            return ApiResponse.success(slides, "查询成功");
        } catch (Exception e) {
            log.error("根据项目ID查询幻灯片失败: {}", courseId, e);
            return ApiResponse.error("查询失败: " + e.getMessage());
        }
    }

    /**
     * 根据ID查询单个幻灯片
     *
     * @param id 幻灯片ID
     * @return 幻灯片信息
     */
    @AdminRequired(message = "查询幻灯片详情需要管理员权限")
    @GetMapping("/slides/{id}")
    public ApiResponse<PptSlide> getSlideById(@PathVariable Integer id) {
        log.info("根据ID查询幻灯片: {}", id);
        try {
            PptSlide slide = pptSlideService.getById(id);
            if (slide == null) {
                return ApiResponse.error(404, "幻灯片不存在");
            }
            return ApiResponse.success(slide, "查询成功");
        } catch (Exception e) {
            log.error("根据ID查询幻灯片失败: {}", id, e);
            return ApiResponse.error("查询失败: " + e.getMessage());
        }
    }

    /**
     * 创建幻灯片
     *
     * @param request 幻灯片创建请求
     * @return 创建结果
     */
    @AdminRequired(message = "创建幻灯片需要管理员权限")
    @PostMapping("/slides")
    public ApiResponse<PptSlide> createSlide(@Valid @RequestBody PptSlideCreateRequest request) {
        log.info("创建幻灯片: {}", request);
        try {
            PptSlide pptSlide = new PptSlide();
            BeanUtils.copyProperties(request, pptSlide);

            boolean success = pptSlideService.createSlide(pptSlide);
            if (success) {
                return ApiResponse.success(pptSlide, "创建成功");
            } else {
                return ApiResponse.error("创建失败");
            }
        } catch (Exception e) {
            log.error("创建幻灯片失败", e);
            return ApiResponse.error("创建失败: " + e.getMessage());
        }
    }

    /**
     * 更新幻灯片
     *
     * @param id 幻灯片ID
     * @param request 幻灯片更新请求
     * @return 更新结果
     */
    @AdminRequired(message = "更新幻灯片需要管理员权限")
    @PutMapping("/slides/{id}")
    public ApiResponse<PptSlide> updateSlide(@PathVariable Integer id, @RequestBody PptSlideUpdateRequest request) {
        log.info("更新幻灯片: ID={}, 数据={}", id, request);
        try {
            // 检查幻灯片是否存在
            PptSlide existingSlide = pptSlideService.getById(id);
            if (existingSlide == null) {
                return ApiResponse.error(404, "幻灯片不存在");
            }

            PptSlide pptSlide = new PptSlide();
            BeanUtils.copyProperties(request, pptSlide);
            pptSlide.setId(id);

            boolean success = pptSlideService.updateSlide(pptSlide);
            if (success) {
                // 返回更新后的数据
                PptSlide updatedSlide = pptSlideService.getById(id);
                return ApiResponse.success(updatedSlide, "更新成功");
            } else {
                return ApiResponse.error("更新失败");
            }
        } catch (Exception e) {
            log.error("更新幻灯片失败: ID={}", id, e);
            return ApiResponse.error("更新失败: " + e.getMessage());
        }
    }

    /**
     * 删除幻灯片
     *
     * @param id 幻灯片ID
     * @return 删除结果
     */
    @AdminRequired(message = "删除幻灯片需要管理员权限")
    @DeleteMapping("/slides/{id}")
    public ApiResponse<Void> deleteSlide(@PathVariable Integer id) {
        log.info("删除幻灯片: {}", id);
        try {
            // 检查幻灯片是否存在
            PptSlide existingSlide = pptSlideService.getById(id);
            if (existingSlide == null) {
                return ApiResponse.error(404, "幻灯片不存在");
            }

            boolean success = pptSlideService.deleteSlide(id);
            if (success) {
                return ApiResponse.success(null, "删除成功");
            } else {
                return ApiResponse.error("删除失败");
            }
        } catch (Exception e) {
            log.error("删除幻灯片失败: ID={}", id, e);
            return ApiResponse.error("删除失败: " + e.getMessage());
        }
    }

    // 根据courseID查询幻灯片是否存在
    @GetMapping("/slides/exists/{courseId}")
    public ApiResponse<Boolean> existsSlidesByCourseId(@PathVariable String courseId) {
        log.info("根据项目ID查询幻灯片是否存在: {}", courseId);
        try {
            boolean exists = pptSlideService.existsByCourseId(courseId);
            return ApiResponse.success(exists, "查询成功");
        } catch (Exception e) {
            log.error("查询幻灯片是否存在失败: {}", courseId, e);
            return ApiResponse.error("查询失败: " + e.getMessage());
        }
    }
}
