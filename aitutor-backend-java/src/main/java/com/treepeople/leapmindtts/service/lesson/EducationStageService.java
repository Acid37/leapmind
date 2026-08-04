package com.treepeople.leapmindtts.service.lesson;

import com.treepeople.leapmindtts.pojo.vo.EducationStageVO;
import com.treepeople.leapmindtts.pojo.vo.GradeVO;

import java.util.List;

/**
 * 教育阶段服务接口
 */
public interface EducationStageService {

    /**
     * 查询所有教育阶段
     *
     * @return 教育阶段列表
     */
    List<EducationStageVO> getAllStages();

    /**
     * 根据阶段代码查询年级列表
     *
     * @param stageCode 阶段代码
     * @return 年级列表
     */
    List<GradeVO> getGradesByStage(String stageCode);

    /**
     * 查询所有年级
     *
     * @return 年级列表
     */
    List<GradeVO> getAllGrades();

    /**
     * 根据年级代码查询教育阶段
     *
     * @param gradeCode 年级代码
     * @return 教育阶段信息
     */
    EducationStageVO getStageByGrade(String gradeCode);
}
