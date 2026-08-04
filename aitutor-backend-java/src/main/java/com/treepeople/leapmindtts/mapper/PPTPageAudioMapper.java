package com.treepeople.leapmindtts.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.treepeople.leapmindtts.pojo.entity.PPTPageAudio;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * PPT页面音频表 Mapper 接口
 */
@Mapper
public interface PPTPageAudioMapper extends BaseMapper<PPTPageAudio> {
    
    /**
     * 根据会话ID和页码查询页面音频
     */
    @Select("SELECT * FROM ppt_page_audio WHERE course_id = #{courseId} AND page_number = #{pageNumber}")
    PPTPageAudio selectBySessionAndPage(@Param("courseId") String courseId, @Param("pageNumber") Integer pageNumber);
    
    /**
     * 根据会话ID查询所有页面音频
     */
    @Select("SELECT * FROM ppt_page_audio WHERE course_id = #{courseId} ORDER BY page_number ASC")
    List<PPTPageAudio> selectByCourseId(@Param("courseId") String courseId);
    
    /**
     * 根据会话ID统计总音频大小
     */
    @Select("SELECT COALESCE(SUM(total_audio_size), 0) FROM ppt_page_audio WHERE course_id = #{courseId}")
    Long getTotalAudioSize(@Param("courseId") String courseId);
    
    /**
     * 根据会话ID统计总时长
     */
    @Select("SELECT COALESCE(SUM(total_duration), 0) FROM ppt_page_audio WHERE course_id = #{courseId}")
    Long getTotalDuration(@Param("courseId") String courseId);
    
    /**
     * 根据会话ID统计总片段数
     */
    @Select("SELECT COALESCE(SUM(segment_count), 0) FROM ppt_page_audio WHERE course_id = #{courseId}")
    Integer getTotalSegmentCount(@Param("courseId") String courseId);
}