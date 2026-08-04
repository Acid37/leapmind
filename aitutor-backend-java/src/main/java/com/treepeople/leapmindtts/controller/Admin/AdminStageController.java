package com.treepeople.leapmindtts.controller.admin;

import com.treepeople.leapmindtts.annotation.AdminRequired;
import com.treepeople.leapmindtts.pojo.dto.CreateEducationStageDTO;
import com.treepeople.leapmindtts.pojo.dto.GradeInfoDTO;
import com.treepeople.leapmindtts.pojo.dto.StageInfoDTO;
import com.treepeople.leapmindtts.pojo.dto.UpdateEducationStageDTO;
import com.treepeople.leapmindtts.pojo.entity.EducationStage;
import com.treepeople.leapmindtts.pojo.result.ApiResponse;
import com.treepeople.leapmindtts.service.admin.StageManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理员阶段和年级管理控制器
 *
 * @ Package：com.treepeople.leapmindtts.controller
 * @ Project：leapMind-java
 * @ Description: 提供阶段和年级的增删改查功能
 * @ Date：2025/11/8  22:33
 */
@Slf4j
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminStageController {

    private final StageManagementService stageManagementService;

    /**
     * 获取所有教育阶段信息
     */
    @AdminRequired(message = "查询教育阶段需要管理员权限")
    @GetMapping("/stages")
    public ResponseEntity<ApiResponse<List<StageInfoDTO>>> getAllStages() {
        log.info("管理员查询所有教育阶段");
        List<StageInfoDTO> stages = stageManagementService.getAllStages();
        return ResponseEntity.ok(ApiResponse.success(stages));
    }

    /**
     * 根据阶段代码获取阶段详情
     */
    @AdminRequired(message = "查询阶段详情需要管理员权限")
    @GetMapping("/stages/{stageCode}")
    public ResponseEntity<ApiResponse<StageInfoDTO>> getStageByCode(@PathVariable String stageCode) {
        log.info("管理员查询阶段详情: {}", stageCode);
        StageInfoDTO stage = stageManagementService.getStageByCode(stageCode);
        return ResponseEntity.ok(ApiResponse.success(stage));
    }

    /**
     * 获取阶段统计信息（包含用户数量）
     */
    @AdminRequired(message = "查询阶段统计需要管理员权限")
    @GetMapping("/stages/statistics")
    public ResponseEntity<ApiResponse<List<StageInfoDTO>>> getStageStatistics() {
        log.info("管理员查询阶段统计信息");
        List<StageInfoDTO> statistics = stageManagementService.getStageStatistics();
        return ResponseEntity.ok(ApiResponse.success(statistics));
    }

    /**
     * 获取所有年级信息
     */
    @AdminRequired(message = "查询年级信息需要管理员权限")
    @GetMapping("/grades")
    public ResponseEntity<ApiResponse<List<GradeInfoDTO>>> getAllGrades() {
        log.info("管理员查询所有年级");
        List<GradeInfoDTO> grades = stageManagementService.getAllGrades();
        return ResponseEntity.ok(ApiResponse.success(grades));
    }

    /**
     * 根据年级代码获取年级详情
     */
    @AdminRequired(message = "查询年级详情需要管理员权限")
    @GetMapping("/grades/{gradeCode}")
    public ResponseEntity<ApiResponse<GradeInfoDTO>> getGradeByCode(@PathVariable String gradeCode) {
        log.info("管理员查询年级详情: {}", gradeCode);
        GradeInfoDTO grade = stageManagementService.getGradeByCode(gradeCode);
        return ResponseEntity.ok(ApiResponse.success(grade));
    }

    /**
     * 根据阶段获取年级列表
     */
    @AdminRequired(message = "根据阶段查询年级需要管理员权限")
    @GetMapping("/stages/{stageCode}/grades")
    public ResponseEntity<ApiResponse<List<GradeInfoDTO>>> getGradesByStage(@PathVariable String stageCode) {
        log.info("管理员根据阶段查询年级: {}", stageCode);
        List<GradeInfoDTO> grades = stageManagementService.getGradesByStage(stageCode);
        return ResponseEntity.ok(ApiResponse.success(grades));
    }

    /**
     * 获取年级统计信息（包含用户数量）
     */
    @AdminRequired(message = "查询年级统计需要管理员权限")
    @GetMapping("/grades/statistics")
    public ResponseEntity<ApiResponse<List<GradeInfoDTO>>> getGradeStatistics() {
        log.info("管理员查询年级统计信息");
        List<GradeInfoDTO> statistics = stageManagementService.getGradeStatistics();
        return ResponseEntity.ok(ApiResponse.success(statistics));
    }


    /**
     * 创建教育阶段
     */
    @AdminRequired(message = "创建教育阶段需要管理员权限")
    @PostMapping("/education-stages")
    public ResponseEntity<ApiResponse<EducationStage>> createEducationStage(@Valid @RequestBody CreateEducationStageDTO createDTO) {
        log.info("管理员创建教育阶段: {}", createDTO.getStageName());
        EducationStage created = stageManagementService.createEducationStage(createDTO);
        return ResponseEntity.ok(ApiResponse.success(created));
    }

    /**
     * 更新教育阶段
     */
    @AdminRequired(message = "更新教育阶段需要管理员权限")
    @PutMapping("/education-stages/{id}")
    public ResponseEntity<ApiResponse<EducationStage>> updateEducationStage(
            @PathVariable Long id,
            @Valid @RequestBody UpdateEducationStageDTO updateDTO) {
        log.info("管理员更新教育阶段: ID={}, 名称={}", id, updateDTO.getStageName());
        EducationStage updated = stageManagementService.updateEducationStage(id, updateDTO);
        return ResponseEntity.ok(ApiResponse.success(updated));
    }

    /**
     * 删除教育阶段
     */
    @AdminRequired(message = "删除教育阶段需要管理员权限")
    @DeleteMapping("/education-stages/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteEducationStage(@PathVariable Long id) {
        log.info("管理员删除教育阶段: ID={}", id);
        stageManagementService.deleteEducationStage(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /**
     * 根据ID获取教育阶段详情
     */
    @AdminRequired(message = "查询教育阶段详情需要管理员权限")
    @GetMapping("/education-stages/{id}")
    public ResponseEntity<ApiResponse<EducationStage>> getEducationStageById(@PathVariable Long id) {
        log.info("管理员查询教育阶段详情: ID={}", id);
        EducationStage educationStage = stageManagementService.getEducationStageById(id);
        return ResponseEntity.ok(ApiResponse.success(educationStage));
    }

    /**
     * 获取所有教育阶段（原始实体）
     */
    @AdminRequired(message = "查询教育阶段列表需要管理员权限")
    @GetMapping("/education-stages")
    public ResponseEntity<ApiResponse<List<EducationStage>>> getAllEducationStages() {
        log.info("管理员查询所有教育阶段实体");
        List<EducationStage> educationStages = stageManagementService.getAllEducationStages();
        return ResponseEntity.ok(ApiResponse.success(educationStages));
    }

}
