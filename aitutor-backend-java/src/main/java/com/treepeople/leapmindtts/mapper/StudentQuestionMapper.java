package com.treepeople.leapmindtts.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.treepeople.leapmindtts.pojo.entity.StudentQuestion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Delete;

import java.util.List;

/**
 * 学生提问记录表 Mapper 接口
 */
@Mapper
public interface StudentQuestionMapper extends BaseMapper<StudentQuestion> {
    
    /**
     * 根据会话ID查询所有提问记录，按创建时间排序
     */
    @Select("SELECT * FROM student_questions WHERE course_id = #{courseId} ORDER BY created_at ASC")
    List<StudentQuestion> selectByCourseId(@Param("courseId") String courseId);
    
    /**
     * 根据会话ID和片段索引查询提问记录
     */
    @Select("SELECT * FROM student_questions WHERE course_id = #{courseId} AND segment_index = #{segmentIndex} ORDER BY created_at ASC")
    List<StudentQuestion> selectBySessionAndSegment(@Param("courseId") String courseId, @Param("segmentIndex") Integer segmentIndex);
    
    /**
     * 根据提问类型查询记录
     */
    @Select("SELECT * FROM student_questions WHERE course_id = #{courseId} AND question_type = #{questionType} ORDER BY created_at DESC")
    List<StudentQuestion> selectByQuestionType(@Param("courseId") String courseId, @Param("questionType") String questionType);
    
    /**
     * 查询指定会话的提问总数
     */
    @Select("SELECT COUNT(*) FROM student_questions WHERE course_id = #{courseId}")
    int countByCourseId(@Param("courseId") String courseId);
    
    /**
     * 删除指定会话的所有提问记录
     */
    @Delete("DELETE FROM student_questions WHERE course_id = #{courseId}")
    int deleteByCourseId(@Param("courseId") String courseId);
    
    /**
     * 根据时间范围查询提问记录
     */
    @Select("SELECT * FROM student_questions WHERE course_id = #{courseId} AND created_at BETWEEN #{startTime} AND #{endTime} ORDER BY created_at ASC")
    List<StudentQuestion> selectByTimeRange(@Param("courseId") String courseId, 
                                          @Param("startTime") String startTime, 
                                          @Param("endTime") String endTime);
    
    /**
     * 查询最近的提问记录
     */
    @Select("SELECT * FROM student_questions WHERE course_id = #{courseId} ORDER BY created_at DESC LIMIT #{limit}")
    List<StudentQuestion> selectRecentQuestions(@Param("courseId") String courseId, @Param("limit") Integer limit);
    
    /**
     * 查询所有全局打断的提问记录（courseId为null的记录）
     */
    @Select("SELECT * FROM student_questions WHERE course_id IS NULL ORDER BY created_at DESC")
    List<StudentQuestion> selectGlobalInterruptions();
    
    /**
     * 根据时间范围查询全局打断记录
     */
    @Select("SELECT * FROM student_questions WHERE course_id IS NULL AND created_at BETWEEN #{startTime} AND #{endTime} ORDER BY created_at DESC")
    List<StudentQuestion> selectGlobalInterruptionsByTimeRange(@Param("startTime") String startTime, 
                                                             @Param("endTime") String endTime);
    
    /**
     * 查询最近的全局打断记录
     */
    @Select("SELECT * FROM student_questions WHERE course_id IS NULL ORDER BY created_at DESC LIMIT #{limit}")
    List<StudentQuestion> selectRecentGlobalInterruptions(@Param("limit") Integer limit);
    
    /**
     * 根据问题文本模糊查询
     */
    @Select("SELECT * FROM student_questions WHERE question_text LIKE CONCAT('%', #{keyword}, '%') ORDER BY created_at DESC")
    List<StudentQuestion> searchByQuestionText(@Param("keyword") String keyword);
    
    /**
     * 根据回答文本模糊查询
     */
    @Select("SELECT * FROM student_questions WHERE answer_text LIKE CONCAT('%', #{keyword}, '%') ORDER BY created_at DESC")
    List<StudentQuestion> searchByAnswerText(@Param("keyword") String keyword);
}