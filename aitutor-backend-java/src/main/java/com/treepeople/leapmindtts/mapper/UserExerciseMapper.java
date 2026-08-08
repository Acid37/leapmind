package com.treepeople.leapmindtts.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.treepeople.leapmindtts.pojo.entity.UserExercise;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户练习记录表 Mapper 接口
 */
@Mapper
public interface UserExerciseMapper extends BaseMapper<UserExercise> {

    /**
     * 根据用户ID查询练习记录
     */
    @Select("SELECT * FROM user_exercises WHERE user_id = #{userId} ORDER BY completed_at DESC")
    List<UserExercise> selectByUserId(@Param("userId") Long userId);

    /**
     * 查询用户7天内的练习记录（用于去重）
     */
    @Select("SELECT DISTINCT exercise_id FROM user_exercises WHERE user_id = #{userId} AND completed_at >= #{since}")
    List<String> selectRecentExerciseIds(@Param("userId") Long userId, @Param("since") LocalDateTime since);

    /**
     * 根据用户ID和练习ID查询记录
     */
    @Select("SELECT * FROM user_exercises WHERE user_id = #{userId} AND exercise_id = #{exerciseId} ORDER BY completed_at DESC")
    List<UserExercise> selectByUserIdAndExerciseId(@Param("userId") Long userId, @Param("exerciseId") String exerciseId);

    /**
     * 查询用户指定时间范围内的练习记录
     */
    @Select("SELECT * FROM user_exercises WHERE user_id = #{userId} AND completed_at BETWEEN #{startTime} AND #{endTime} ORDER BY completed_at DESC")
    List<UserExercise> selectByTimeRange(@Param("userId") Long userId, @Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);
}
