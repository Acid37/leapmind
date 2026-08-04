package com.treepeople.leapmindtts.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.treepeople.leapmindtts.pojo.entity.EducationStage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 教育阶段数据访问层
 */
@Mapper
public interface EducationStageMapper extends BaseMapper<EducationStage> {
    
    /**
     * 根据阶段代码查询年级列表
     * 
     * @param stageCode 阶段代码
     * @return 年级列表
     */
    @Select("SELECT * FROM education_stages WHERE stage_code = #{stageCode} ORDER BY sort_order")
    List<EducationStage> findByStageCode(@Param("stageCode") String stageCode);
    
    /**
     * 根据年级代码查询教育阶段
     * 
     * @param gradeCode 年级代码
     * @return 教育阶段信息
     */
    @Select("SELECT * FROM education_stages WHERE grade_code = #{gradeCode}")
    EducationStage findByGradeCode(@Param("gradeCode") String gradeCode);
    
    /**
     * 查询所有教育阶段（去重）
     * 
     * @return 教育阶段列表
     */
    @Select("SELECT DISTINCT stage_name, stage_code, description FROM education_stages ORDER BY MIN(sort_order)")
    List<EducationStage> findAllStages();
    
    /**
     * 查询所有年级信息
     * 
     * @return 年级列表
     */
    @Select("SELECT * FROM education_stages ORDER BY sort_order")
    List<EducationStage> findAllGrades();
}