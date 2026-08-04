package com.treepeople.leapmindtts.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.treepeople.leapmindtts.pojo.entity.ProjectOutline;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 项目大纲Mapper接口
 */
@Mapper
public interface ProjectOutlineMapper extends BaseMapper<ProjectOutline> {
    
    /**
     * 根据项目ID查询大纲列表
     * 
     * @param courseId 项目ID
     * @return 大纲列表
     */
    @Select("SELECT * FROM project_outline WHERE course_id = #{courseId} ORDER BY create_time DESC")
    List<ProjectOutline> selectByCourseId(@Param("courseId") Integer courseId);
}