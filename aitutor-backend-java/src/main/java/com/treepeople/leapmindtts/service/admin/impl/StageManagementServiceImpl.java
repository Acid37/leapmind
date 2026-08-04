package com.treepeople.leapmindtts.service.admin.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.treepeople.leapmindtts.mapper.EducationStageMapper;
import com.treepeople.leapmindtts.mapper.UserMapper;
import com.treepeople.leapmindtts.pojo.dto.CreateEducationStageDTO;
import com.treepeople.leapmindtts.pojo.dto.GradeInfoDTO;
import com.treepeople.leapmindtts.pojo.dto.StageInfoDTO;
import com.treepeople.leapmindtts.pojo.dto.UpdateEducationStageDTO;
import com.treepeople.leapmindtts.pojo.entity.EducationStage;
import com.treepeople.leapmindtts.pojo.entity.User;
import com.treepeople.leapmindtts.pojo.enums.GradeEnum;
import com.treepeople.leapmindtts.service.admin.StageManagementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 阶段管理服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StageManagementServiceImpl implements StageManagementService {

    private final UserMapper userMapper;
    private final EducationStageMapper educationStageMapper;

    @Override
    public List<StageInfoDTO> getAllStages() {
        Map<String, List<GradeInfoDTO>> stageGradeMap = new HashMap<>();

        // 按阶段分组年级
        for (GradeEnum grade : GradeEnum.values()) {
            String stageCode = grade.getStageCode();
            GradeInfoDTO gradeInfo = convertToGradeDTO(grade);

            stageGradeMap.computeIfAbsent(stageCode, k -> new ArrayList<>()).add(gradeInfo);
        }

        // 构建阶段信息
        List<StageInfoDTO> stages = new ArrayList<>();
        for (Map.Entry<String, List<GradeInfoDTO>> entry : stageGradeMap.entrySet()) {
            String stageCode = entry.getKey();
            List<GradeInfoDTO> grades = entry.getValue();

            StageInfoDTO stage = StageInfoDTO.builder()
                    .stageCode(stageCode)
                    .stageName(getStageNameByCode(stageCode))
                    .grades(grades)
                    .userCount(0L) // 基础信息不包含用户统计
                    .build();

            stages.add(stage);
        }

        return stages;
    }

    @Override
    public StageInfoDTO getStageByCode(String stageCode) {
        List<GradeInfoDTO> grades = getGradesByStage(stageCode);

        if (grades.isEmpty()) {
            throw new IllegalArgumentException("未知的阶段代码: " + stageCode);
        }

        return StageInfoDTO.builder()
                .stageCode(stageCode)
                .stageName(getStageNameByCode(stageCode))
                .grades(grades)
                .userCount(0L)
                .build();
    }

    @Override
    public List<GradeInfoDTO> getAllGrades() {
        return Arrays.stream(GradeEnum.values())
                .map(this::convertToGradeDTO)
                .collect(Collectors.toList());
    }


    @Override
    public GradeInfoDTO getGradeByCode(String gradeCode) {
        try {
            GradeEnum grade = GradeEnum.fromCode(gradeCode);
            return convertToGradeDTO(grade);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("未知的年级代码: " + gradeCode);
        }
    }

    @Override
    public List<GradeInfoDTO> getGradesByStage(String stageCode) {
        return Arrays.stream(GradeEnum.values())
                .filter(grade -> grade.getStageCode().equals(stageCode))
                .map(this::convertToGradeDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<GradeInfoDTO> getGradeStatistics() {
        // 获取每个年级的用户数量
        Map<String, Long> gradeUserCount = new HashMap<>();

        for (GradeEnum grade : GradeEnum.values()) {
            QueryWrapper<User> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("grade", grade.getCode());
            Long count = userMapper.selectCount(queryWrapper);
            gradeUserCount.put(grade.getCode(), count);
        }

        // 构建带统计信息的年级列表
        return Arrays.stream(GradeEnum.values())
                .map(grade -> {
                    GradeInfoDTO dto = convertToGradeDTO(grade);
                    // 这里可以扩展DTO添加用户数量字段，暂时在description中显示
                    Long userCount = gradeUserCount.get(grade.getCode());
                    dto.setDescription(dto.getDescription() + " (用户数: " + userCount + ")");
                    return dto;
                })
                .collect(Collectors.toList());
    }

    @Override
    public List<StageInfoDTO> getStageStatistics() {
        Map<String, List<GradeInfoDTO>> stageGradeMap = new HashMap<>();
        Map<String, Long> stageUserCount = new HashMap<>();

        // 按阶段分组并统计用户数
        for (GradeEnum grade : GradeEnum.values()) {
            String stageCode = grade.getStageCode();
            GradeInfoDTO gradeInfo = convertToGradeDTO(grade);

            // 统计该年级的用户数
            QueryWrapper<User> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("grade", grade.getCode());
            Long gradeUserCount = userMapper.selectCount(queryWrapper);

            stageGradeMap.computeIfAbsent(stageCode, k -> new ArrayList<>()).add(gradeInfo);
            stageUserCount.merge(stageCode, gradeUserCount, Long::sum);
        }

        // 构建带统计信息的阶段列表
        List<StageInfoDTO> stages = new ArrayList<>();
        for (Map.Entry<String, List<GradeInfoDTO>> entry : stageGradeMap.entrySet()) {
            String stageCode = entry.getKey();
            List<GradeInfoDTO> grades = entry.getValue();
            Long userCount = stageUserCount.get(stageCode);

            StageInfoDTO stage = StageInfoDTO.builder()
                    .stageCode(stageCode)
                    .stageName(getStageNameByCode(stageCode))
                    .grades(grades)
                    .userCount(userCount)
                    .build();

            stages.add(stage);
        }

        return stages;
    }

    /**
     * 将GradeEnum转换为GradeInfoDTO
     */
    private GradeInfoDTO convertToGradeDTO(GradeEnum grade) {
        return GradeInfoDTO.builder()
                .code(grade.getCode())
                .description(grade.getDescription())
                .stage(grade.getStage())
                .stageCode(grade.getStageCode())
                .build();
    }

    /**
     * 根据阶段代码获取阶段名称
     */
    private String getStageNameByCode(String stageCode) {
        switch (stageCode) {
            case "PRIMARY":
                return "小学";
            case "JUNIOR":
                return "初中";
            default:
                return "未知";
        }
    }

    // ==================== 教育阶段实体管理方法实现 ====================

    @Override
    public EducationStage createEducationStage(CreateEducationStageDTO createDTO) {
        log.info("创建教育阶段: {}", createDTO.getStageName());

        // 根据阶段名称生成阶段代码
        String stageCode = generateStageCode(createDTO.getStageName());
        // 根据年级名称生成年级代码
        String gradeCode = generateGradeCode(createDTO.getGradeName());

        // 验证阶段代码和年级代码组合是否已存在
        QueryWrapper<EducationStage> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("stage_code", stageCode)
                   .eq("grade_code", gradeCode);

        EducationStage existing = educationStageMapper.selectOne(queryWrapper);
        if (existing != null) {
            throw new IllegalArgumentException("该阶段和年级组合已存在: " +
                createDTO.getStageName() + "-" + createDTO.getGradeName());
        }

        // 自动设置排序顺序为最大值+1
        QueryWrapper<EducationStage> maxOrderQuery = new QueryWrapper<>();
        maxOrderQuery.orderByDesc("sort_order").last("LIMIT 1");
        EducationStage maxOrderStage = educationStageMapper.selectOne(maxOrderQuery);
        int nextOrder = (maxOrderStage != null && maxOrderStage.getSortOrder() != null)
            ? maxOrderStage.getSortOrder() + 1 : 1;

        // 构建教育阶段实体
        EducationStage educationStage = EducationStage.builder()
                .stageCode(stageCode)
                .stageName(createDTO.getStageName())
                .gradeCode(gradeCode)
                .gradeName(createDTO.getGradeName())
                .description(createDTO.getDescription())
                .sortOrder(nextOrder)
                .build();

        educationStageMapper.insert(educationStage);
        log.info("教育阶段创建成功，ID: {}", educationStage.getId());
        return educationStage;
    }

    @Override
    public EducationStage updateEducationStage(Long id, UpdateEducationStageDTO updateDTO) {
        log.info("更新教育阶段: ID={}, 名称={}", id, updateDTO.getStageName());

        // 验证教育阶段是否存在
        EducationStage existing = educationStageMapper.selectById(id);
        if (existing == null) {
            throw new IllegalArgumentException("教育阶段不存在，ID: " + id);
        }

        // 根据新的阶段名称和年级名称生成代码
        String newStageCode = generateStageCode(updateDTO.getStageName());
        String newGradeCode = generateGradeCode(updateDTO.getGradeName());

        // 如果修改了阶段或年级，需要验证新的组合是否已存在
        if (!existing.getStageCode().equals(newStageCode) ||
            !existing.getGradeCode().equals(newGradeCode)) {

            QueryWrapper<EducationStage> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("stage_code", newStageCode)
                       .eq("grade_code", newGradeCode)
                       .ne("id", id);

            EducationStage duplicate = educationStageMapper.selectOne(queryWrapper);
            if (duplicate != null) {
                throw new IllegalArgumentException("该阶段和年级组合已存在: " +
                    updateDTO.getStageName() + "-" + updateDTO.getGradeName());
            }
        }

        // 更新教育阶段信息
        existing.setStageCode(newStageCode);
        existing.setStageName(updateDTO.getStageName());
        existing.setGradeCode(newGradeCode);
        existing.setGradeName(updateDTO.getGradeName());
        existing.setDescription(updateDTO.getDescription());

        educationStageMapper.updateById(existing);
        log.info("教育阶段更新成功，ID: {}", id);
        return existing;
    }

    @Override
    public void deleteEducationStage(Long id) {
        log.info("删除教育阶段: ID={}", id);

        // 验证教育阶段是否存在
        EducationStage existing = educationStageMapper.selectById(id);
        if (existing == null) {
            throw new IllegalArgumentException("教育阶段不存在，ID: " + id);
        }

        // 检查是否有用户使用该教育阶段
        QueryWrapper<User> userQuery = new QueryWrapper<>();
        userQuery.eq("grade", existing.getGradeCode());
        Long userCount = userMapper.selectCount(userQuery);

        if (userCount > 0) {
            throw new IllegalStateException("无法删除教育阶段，仍有 " + userCount + " 个用户使用该年级");
        }

        educationStageMapper.deleteById(id);
        log.info("教育阶段删除成功，ID: {}", id);
    }

    @Override
    public EducationStage getEducationStageById(Long id) {
        log.debug("查询教育阶段详情: ID={}", id);

        EducationStage educationStage = educationStageMapper.selectById(id);
        if (educationStage == null) {
            throw new IllegalArgumentException("教育阶段不存在，ID: " + id);
        }

        return educationStage;
    }

    @Override
    public List<EducationStage> getAllEducationStages() {
        log.debug("查询所有教育阶段");

        QueryWrapper<EducationStage> queryWrapper = new QueryWrapper<>();
        queryWrapper.orderByAsc("sort_order");

        return educationStageMapper.selectList(queryWrapper);
    }

    // ==================== 私有工具方法 ====================

    /**
     * 根据阶段名称生成阶段代码
     */
    private String generateStageCode(String stageName) {
        if (stageName == null) {
            throw new IllegalArgumentException("阶段名称不能为空");
        }

        switch (stageName.trim()) {
            case "小学":
                return "PRIMARY";
            case "初中":
                return "JUNIOR";
            case "高中":
                return "SENIOR";
            case "大学":
                return "UNIVERSITY";
            default:
                // 对于其他名称，生成基于拼音首字母的代码
                return stageName.replaceAll("[^\\u4e00-\\u9fa5a-zA-Z0-9]", "").toUpperCase();
        }
    }

    /**
     * 根据年级名称生成年级代码
     */
    private String generateGradeCode(String gradeName) {
        if (gradeName == null) {
            throw new IllegalArgumentException("年级名称不能为空");
        }

        String trimmedName = gradeName.trim();

        // 处理常见的年级名称
        switch (trimmedName) {
            case "一年级":
                return "GRADE_1";
            case "二年级":
                return "GRADE_2";
            case "三年级":
                return "GRADE_3";
            case "四年级":
                return "GRADE_4";
            case "五年级":
                return "GRADE_5";
            case "六年级":
                return "GRADE_6";
            case "七年级":
            case "初一":
                return "GRADE_7";
            case "八年级":
            case "初二":
                return "GRADE_8";
            case "九年级":
            case "初三":
                return "GRADE_9";
            case "十年级":
            case "高一":
                return "GRADE_10";
            case "十一年级":
            case "高二":
                return "GRADE_11";
            case "十二年级":
            case "高三":
                return "GRADE_12";
            default:
                // 对于其他名称，生成基于名称的代码
                return "GRADE_" + trimmedName.replaceAll("[^\\u4e00-\\u9fa5a-zA-Z0-9]", "").toUpperCase();
        }
    }
}
