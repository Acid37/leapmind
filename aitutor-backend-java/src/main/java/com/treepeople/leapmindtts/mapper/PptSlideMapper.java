package com.treepeople.leapmindtts.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.treepeople.leapmindtts.pojo.entity.PptSlide;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * PPT幻灯片Mapper接口
 */
@Mapper
public interface PptSlideMapper extends BaseMapper<PptSlide> {
    
    /**
     * 根据项目ID查询幻灯片列表
     * 
     * @param courseId 项目ID
     * @return 幻灯片列表
     */
    List<PptSlide> selectByCourseId(@Param("courseId") String courseId);
}