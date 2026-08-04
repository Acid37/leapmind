package com.treepeople.leapmindtts.service.lesson.impl;

import com.treepeople.leapmindtts.mapper.EducationStageMapper;
import com.treepeople.leapmindtts.pojo.entity.EducationStage;
import com.treepeople.leapmindtts.pojo.vo.EducationStageVO;
import com.treepeople.leapmindtts.pojo.vo.GradeVO;
import com.treepeople.leapmindtts.service.lesson.EducationStageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 教育阶段服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EducationStageServiceImpl implements EducationStageService {

    private final EducationStageMapper educationStageMapper;

    @Override
    public List<EducationStageVO> getAllStages() {
        List<EducationStage> stages = educationStageMapper.findAllGrades();

        // 按阶段代码分组，去重
        Map<String, EducationStage> stageMap = stages.stream()
                .collect(Collectors.toMap(
                        EducationStage::getStageCode,
                        stage -> stage,
                        (existing, replacement) -> existing
                ));

        return stageMap.values().stream()
                .map(this::convertToStageVO)
                .collect(Collectors.toList());
    }

    @Override
    public List<GradeVO> getGradesByStage(String stageCode) {
        List<EducationStage> grades = educationStageMapper.findByStageCode(stageCode);
        return grades.stream()
                .map(this::convertToGradeVO)
                .collect(Collectors.toList());
    }

    @Override
    public List<GradeVO> getAllGrades() {
        List<EducationStage> grades = educationStageMapper.findAllGrades();
        return grades.stream()
                .map(this::convertToGradeVO)
                .collect(Collectors.toList());
    }

    @Override
    public EducationStageVO getStageByGrade(String gradeCode) {
        EducationStage stage = educationStageMapper.findByGradeCode(gradeCode);
        if (stage == null) {
            throw new RuntimeException("年级不存在: " + gradeCode);
        }
        return convertToStageVO(stage);
    }

    /**
     * 将EducationStage实体转换为EducationStageVO
     *
     * @param stage 教育阶段实体
     * @return 教育阶段视图对象
     */
    private EducationStageVO convertToStageVO(EducationStage stage) {
        return EducationStageVO.builder()
                .stageName(stage.getStageName())
                .stageCode(stage.getStageCode())
                .description(stage.getDescription())
                .build();
    }

    /**
     * 将EducationStage实体转换为GradeVO
     *
     * @param stage 教育阶段实体
     * @return 年级视图对象
     */
    private GradeVO convertToGradeVO(EducationStage stage) {
        return GradeVO.builder()
                .gradeCode(stage.getGradeCode())
                .gradeName(stage.getGradeName())
                .stageCode(stage.getStageCode())
                .stageName(stage.getStageName())
                .sortOrder(stage.getSortOrder())
                .build();
    }
}
