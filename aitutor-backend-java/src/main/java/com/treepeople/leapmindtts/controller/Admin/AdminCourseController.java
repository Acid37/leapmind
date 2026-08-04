package com.treepeople.leapmindtts.controller.admin;

import com.treepeople.leapmindtts.annotation.AdminRequired;
import com.treepeople.leapmindtts.pojo.dto.CourseCreateRequest;
import com.treepeople.leapmindtts.pojo.dto.CourseSectionDTO;
import com.treepeople.leapmindtts.pojo.dto.CourseUpdateRequest;
import com.treepeople.leapmindtts.pojo.result.ApiResponse;
import com.treepeople.leapmindtts.pojo.vo.CourseVO;
import com.treepeople.leapmindtts.service.admin.CourseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理员课程管理控制器
 *
 * @ Package：com.treepeople.leapmindtts.controller
 * @ Project：leapMind-java
 * @ Description: 提供课程的增删改查功能
 * @ Date：2025/11/8  22:42
 */
@Slf4j
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminCourseController {

    private final CourseService courseService;

    /**
     * 创建课程
     */
    @AdminRequired(message = "创建课程需要管理员权限")
    @PostMapping("/courses")
    public ResponseEntity<ApiResponse<CourseVO>> createCourse(@Valid @RequestBody CourseCreateRequest request) {
        log.info("管理员创建课程: {} - {}", request.getSubject(), request.getChapterTitle());
        try {
            CourseVO courseVO = courseService.createCourse(request);
            return ResponseEntity.ok(ApiResponse.success(courseVO));
        }catch (Exception e){
            return ResponseEntity.ok(ApiResponse.error("课程已存在"));
        }
    }

    /**
     * 根据课程ID获取课程详情
     */
    @AdminRequired(message = "查询课程详情需要管理员权限")
    @GetMapping("/courses/{courseId}")
    public ResponseEntity<ApiResponse<CourseVO>> getCourseById(@PathVariable String courseId) {
        log.info("管理员查询课程详情: {}", courseId);
        CourseVO courseVO = new CourseVO();
        try {
            courseVO = courseService.getCourseById(courseId);
        }catch (Exception e){
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
        return ResponseEntity.ok(ApiResponse.success(courseVO));
    }

    /**
     * 获取所有课程列表
     */
    @AdminRequired(message = "查询课程列表需要管理员权限")
    @GetMapping("/courses")
    public ResponseEntity<ApiResponse<List<CourseVO>>> getAllCourses() {
        log.info("管理员查询所有课程");
        List<CourseVO> courses = courseService.getAllCourses();
        return ResponseEntity.ok(ApiResponse.success(courses));
    }

    /**
     * 根据阶段查询课程列表
     */
    @AdminRequired(message = "根据阶段查询课程需要管理员权限")
    @GetMapping("/courses/search/stage")
    public ResponseEntity<ApiResponse<List<CourseVO>>> getCoursesByStage(@RequestParam String stageName) {
        log.info("管理员根据阶段查询课程: {}", stageName);
        List<CourseVO> courses = courseService.getCoursesByStage(stageName);
        return ResponseEntity.ok(ApiResponse.success(courses));
    }

    /**
     * 根据学科查询课程列表
     */
    @AdminRequired(message = "根据学科查询课程需要管理员权限")
    @GetMapping("/courses/search/subject")
    public ResponseEntity<ApiResponse<List<CourseVO>>> getCoursesBySubject(@RequestParam String subject) {
        log.info("管理员根据学科查询课程: {}", subject);
        List<CourseVO> courses = courseService.getCoursesBySubject(subject);
        return ResponseEntity.ok(ApiResponse.success(courses));
    }

    /**
     * 更新课程信息
     */
    @AdminRequired(message = "更新课程信息需要管理员权限")
    @PutMapping("/courses/{id}")
    public ResponseEntity<ApiResponse<CourseVO>> updateCourse(@PathVariable Integer id,
                                                             @Valid @RequestBody CourseUpdateRequest request) {
        log.info("管理员更新课程信息: {}", id);
        CourseVO courseVO = courseService.updateCourse(id, request);
        return ResponseEntity.ok(ApiResponse.success(courseVO));
    }

    /**
     * 删除课程
     */
    @AdminRequired(message = "删除课程需要管理员权限")
    @DeleteMapping("/courses/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteCourse(@PathVariable Integer id) {
        log.info("管理员删除课程: {}", id);
        courseService.deleteCourse(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    //根据阶段、年级、学科、章节查询课程
    @PostMapping("/courses/search")
    public ResponseEntity<ApiResponse<List<CourseVO>>> getCourseByStageGradeSubjectChapter(
            @RequestBody CourseSectionDTO courseSectionDTO) {
        log.info("管理员根据阶段、年级、学科、章节查询课程: {}", courseSectionDTO);
        List<CourseVO> courses = courseService.getCourseSection(courseSectionDTO);
        return ResponseEntity.ok(ApiResponse.success(courses));
    }


}
