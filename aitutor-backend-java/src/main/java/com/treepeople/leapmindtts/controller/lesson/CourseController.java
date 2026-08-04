package com.treepeople.leapmindtts.controller.lesson;

import com.treepeople.leapmindtts.pojo.dto.AdminCourseSectionDTO;
import com.treepeople.leapmindtts.pojo.result.ApiResponse;
import com.treepeople.leapmindtts.pojo.vo.CourseVO;
import com.treepeople.leapmindtts.service.admin.CourseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 课程接口 — 课本章节查询
 */
@Slf4j
@RestController
@RequestMapping("/api/courses")
@RequiredArgsConstructor
@Tag(name = "课程", description = "课本章节查询相关接口")
public class CourseController {

    private final CourseService CourseService;

    /**
     * 根据学段、年级、学期、学科获取对应的课本章节列表
     */
    @Operation(summary = "获取课本章节", description = "根据学段名称、年级名称、学期、学科名称查询课本章节列表")
    @PostMapping("/section")
    public ResponseEntity<ApiResponse<List<CourseVO>>> getCourseSectionByStageGradeSemesterSubject(
            @Parameter(description = "查询条件：学段、年级、学期、学科", required = true)
            @RequestBody AdminCourseSectionDTO adminCourseSectionDTO) {
        log.info("查询课程：{}", adminCourseSectionDTO);
        try {
            List<CourseVO> courseSectionVOList = CourseService.getCourseSectionByStageGradeSemesterSubject(adminCourseSectionDTO);
            return ResponseEntity.ok(ApiResponse.success(courseSectionVOList, "获取课本章节成功"));
        } catch (Exception e) {
            log.error("获取课本章节失败: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, "没有这个条件下的课本章节"));
        }
    }
}
