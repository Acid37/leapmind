package com.treepeople.leapmindtts.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.treepeople.leapmindtts.pojo.entity.LessonSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 讲课会话表 Mapper 接口
 */
@Mapper
public interface LessonSessionMapper extends BaseMapper<LessonSession> {
    
    /**
     * 根据会话ID查询会话信息
     */
    @Select("SELECT * FROM lesson_sessions WHERE course_id = #{courseId}")
    LessonSession selectByCourseId(@Param("courseId") String courseId);
    
    /**
     * 更新会话的总片段数和总时长
     */
    @Update("UPDATE lesson_sessions SET total_segments = #{totalSegments}, total_duration = #{totalDuration} WHERE course_id = #{courseId}")
    int updateSessionStats(@Param("courseId") String courseId, 
                          @Param("totalSegments") Integer totalSegments, 
                          @Param("totalDuration") Long totalDuration);
    
    /**
     * 根据创建时间范围查询会话列表
     */
    @Select("SELECT * FROM lesson_sessions WHERE created_at BETWEEN #{startTime} AND #{endTime} ORDER BY created_at DESC")
    List<LessonSession> selectByTimeRange(@Param("startTime") String startTime, @Param("endTime") String endTime);
    
    /**
     * 根据标题模糊查询
     */
    @Select("SELECT * FROM lesson_sessions WHERE title LIKE CONCAT('%', #{keyword}, '%') ORDER BY created_at DESC")
    List<LessonSession> selectByTitleKeyword(@Param("keyword") String keyword);
    
    /**
     * 更新会话的润色文本
     */
    @Update("UPDATE lesson_sessions SET polished_text = #{polishedText} WHERE course_id = #{courseId}")
    int updatePolishedText(@Param("courseId") String courseId, @Param("polishedText") String polishedText);
    
    /**
     * 更新会话标题
     */
    @Update("UPDATE lesson_sessions SET title = #{title} WHERE course_id = #{courseId}")
    int updateTitle(@Param("courseId") String courseId, @Param("title") String title);
    
    /**
     * 根据状态查询会话列表
     */
    @Select("SELECT * FROM lesson_sessions WHERE processing_status = #{status} ORDER BY created_at DESC")
    List<LessonSession> selectByStatus(@Param("status") String status);
    
    /**
     * 更新会话状态
     */
    @Update("UPDATE lesson_sessions SET processing_status = #{status}, reviewed_by = #{reviewerId}, reviewed_at = #{reviewedAt}, review_comments = #{comments} WHERE course_id = #{courseId}")
    int updateSessionStatus(@Param("courseId") String courseId, 
                           @Param("status") String status,
                           @Param("reviewerId") String reviewerId,
                           @Param("reviewedAt") java.time.LocalDateTime reviewedAt,
                           @Param("comments") String comments);
}