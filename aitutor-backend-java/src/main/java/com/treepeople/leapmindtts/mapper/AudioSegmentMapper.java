package com.treepeople.leapmindtts.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.treepeople.leapmindtts.pojo.entity.AudioSegment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Delete;

import java.util.List;

/**
 * 音频片段表 Mapper 接口
 */
@Mapper
public interface AudioSegmentMapper extends BaseMapper<AudioSegment> {
    
    /**
     * 根据会话ID和片段索引查询音频片段
     */
    @Select("SELECT * FROM audio_segments WHERE course_id = #{courseId} AND segment_index = #{segmentIndex}")
    AudioSegment selectBySessionAndIndex(@Param("courseId") String courseId, @Param("segmentIndex") Integer segmentIndex);
    
    /**
     * 根据会话ID查询所有音频片段，按片段索引排序
     */
    @Select("SELECT * FROM audio_segments WHERE course_id = #{courseId} ORDER BY segment_index ASC")
    List<AudioSegment> selectByCourseId(@Param("courseId") String courseId);

    /**
     * 根据会话ID查询从指定片段开始的所有音频片段
     */
    @Select("SELECT * FROM audio_segments WHERE course_id = #{courseId} AND segment_index >= #{startIndex} ORDER BY segment_index ASC")
    List<AudioSegment> selectFromSegmentIndex(@Param("courseId") String courseId, @Param("startIndex") Integer startIndex);
    
    /**
     * 查询指定会话的音频片段数量
     */
    @Select("SELECT COUNT(*) FROM audio_segments WHERE course_id = #{courseId}")
    int countByCourseId(@Param("courseId") String courseId);
    
    /**
     * 删除指定会话的所有音频片段
     */
    @Delete("DELETE FROM audio_segments WHERE course_id = #{courseId}")
    int deleteByCourseId(@Param("courseId") String courseId);
    
    /**
     * 查询指定会话的总音频大小
     */
    @Select("SELECT COALESCE(SUM(audio_size), 0) FROM audio_segments WHERE course_id = #{courseId}")
    Long getTotalAudioSizeByCourseId(@Param("courseId") String courseId);
    
    /**
     * 查询指定会话的总时长
     */
    @Select("SELECT COALESCE(SUM(duration), 0) FROM audio_segments WHERE course_id = #{courseId}")
    Long getTotalDurationByCourseId(@Param("courseId") String courseId);
    
    // ========== PPT相关查询方法 ==========
    
    /**
     * 根据会话ID和页码查询音频片段
     */
    @Select("SELECT * FROM audio_segments WHERE course_id = #{courseId} AND slide_page_number = #{pageNumber} ORDER BY segment_index ASC")
    List<AudioSegment> selectBySessionAndSlide(@Param("courseId") String courseId, @Param("pageNumber") Integer pageNumber);
    
    /**
     * 根据会话ID、页码和内容点索引查询音频片段
     */
    @Select("SELECT * FROM audio_segments WHERE course_id = #{courseId} AND slide_page_number = #{pageNumber} AND content_point_index = #{contentPointIndex} ORDER BY segment_index ASC")
    List<AudioSegment> selectBySessionSlideAndContentPoint(@Param("courseId") String courseId,
                                                          @Param("pageNumber") Integer pageNumber, 
                                                          @Param("contentPointIndex") Integer contentPointIndex);
}