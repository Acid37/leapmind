package com.treepeople.leapmindtts.controller.lesson;

import com.treepeople.leapmindtts.pojo.result.ApiResponse;
import com.treepeople.leapmindtts.pojo.vo.EducationStageVO;
import com.treepeople.leapmindtts.pojo.vo.GradeVO;
import com.treepeople.leapmindtts.service.lesson.EducationStageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 教育阶段控制器
 * 处理教育阶段和年级相关的查询操作
 */
@Slf4j
@RestController
@RequestMapping("/api/education")
@RequiredArgsConstructor
public class EducationStageController {

    private final EducationStageService educationStageService;

    /**
     * 查询所有教育阶段
     *
     * @return 教育阶段列表
     */
    @GetMapping("/stages")
    public ResponseEntity<ApiResponse<List<EducationStageVO>>> getAllStages() {
        try {
            List<EducationStageVO> stages = educationStageService.getAllStages();
            return ResponseEntity.ok(ApiResponse.success(stages, "查询教育阶段成功"));
        } catch (Exception e) {
            log.error("查询教育阶段失败: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, e.getMessage()));
        }
    }

    /**
     * 根据阶段代码查询年级列表
     *
     * @param stageCode 阶段代码
     * @return 年级列表
     */
    @GetMapping("/stages/{stageCode}/grades")
    public ResponseEntity<ApiResponse<List<GradeVO>>> getGradesByStage(@PathVariable String stageCode) {
        try {
            List<GradeVO> grades = educationStageService.getGradesByStage(stageCode);
            return ResponseEntity.ok(ApiResponse.success(grades, "查询年级列表成功"));
        } catch (Exception e) {
            log.error("查询年级列表失败: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, e.getMessage()));
        }
    }
}
