package com.treepeople.leapmindtts.service.admin;

import com.treepeople.leapmindtts.pojo.dto.CreateEducationStageDTO;
import com.treepeople.leapmindtts.pojo.dto.GradeInfoDTO;
import com.treepeople.leapmindtts.pojo.dto.StageInfoDTO;
import com.treepeople.leapmindtts.pojo.dto.UpdateEducationStageDTO;
import com.treepeople.leapmindtts.pojo.entity.EducationStage;

import java.util.List;

/**
 * 阶段管理服务接口
 */
public interface StageManagementService {

    /**
     * 获取所有教育阶段信息
     * @return 阶段信息列表
     */
    List<StageInfoDTO> getAllStages();

    /**
     * 根据阶段代码获取阶段信息
     * @param stageCode 阶段代码
     * @return 阶段信息
     */
    StageInfoDTO getStageByCode(String stageCode);

    /**
     * 获取所有年级信息
     * @return 年级信息列表
     */
    List<GradeInfoDTO> getAllGrades();

    /**
     * 根据年级代码获取年级信息
     * @param gradeCode 年级代码
     * @return 年级信息
     */
    GradeInfoDTO getGradeByCode(String gradeCode);

    /**
     * 根据阶段获取年级列表
     * @param stageCode 阶段代码
     * @return 年级信息列表
     */
    List<GradeInfoDTO> getGradesByStage(String stageCode);

    /**
     * 获取年级统计信息（包含用户数量）
     * @return 年级统计信息
     */
    List<GradeInfoDTO> getGradeStatistics();

    /**
     * 获取阶段统计信息（包含用户数量）
     * @return 阶段统计信息
     */
    List<StageInfoDTO> getStageStatistics();

    // ==================== 教育阶段实体管理方法 ====================

    /**
     * 创建教育阶段
     * @param createDTO 创建教育阶段DTO
     * @return 创建后的教育阶段
     */
    EducationStage createEducationStage(CreateEducationStageDTO createDTO);

    /**
     * 更新教育阶段
     * @param id 教育阶段ID
     * @param updateDTO 更新教育阶段DTO
     * @return 更新后的教育阶段
     */
    EducationStage updateEducationStage(Long id, UpdateEducationStageDTO updateDTO);

    /**
     * 删除教育阶段
     * @param id 教育阶段ID
     */
    void deleteEducationStage(Long id);

    /**
     * 根据ID获取教育阶段
     * @param id 教育阶段ID
     * @return 教育阶段信息
     */
    EducationStage getEducationStageById(Long id);

    /**
     * 获取所有教育阶段实体
     * @return 教育阶段列表
     */
    List<EducationStage> getAllEducationStages();
}
