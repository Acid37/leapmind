package com.treepeople.leapmindtts.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.treepeople.leapmindtts.pojo.entity.UserWeakPoint;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 用户薄弱点表 Mapper 接口
 */
@Mapper
public interface UserWeakPointMapper extends BaseMapper<UserWeakPoint> {

    /**
     * 根据用户ID查询薄弱点，按薄弱程度和错误次数降序
     */
    @Select("SELECT * FROM user_weak_points WHERE user_id = #{userId} ORDER BY FIELD(weakness_level, 'HIGH', 'MEDIUM', 'LOW'), error_count DESC")
    List<UserWeakPoint> selectByUserId(@Param("userId") Long userId);

    /**
     * 根据用户ID和学科查询薄弱点
     */
    @Select("SELECT * FROM user_weak_points WHERE user_id = #{userId} AND subject = #{subject}")
    List<UserWeakPoint> selectByUserIdAndSubject(@Param("userId") Long userId, @Param("subject") String subject);

    /**
     * 根据用户ID和状态查询薄弱点
     */
    @Select("SELECT * FROM user_weak_points WHERE user_id = #{userId} AND status = #{status}")
    List<UserWeakPoint> selectByUserIdAndStatus(@Param("userId") Long userId, @Param("status") String status);

    /**
     * 根据用户ID、学科和状态三条件联合查询薄弱点
     */
    @Select("SELECT * FROM user_weak_points WHERE user_id = #{userId} AND subject = #{subject} AND status = #{status} ORDER BY FIELD(weakness_level, 'HIGH', 'MEDIUM', 'LOW'), error_count DESC")
    List<UserWeakPoint> selectByUserIdSubjectStatus(@Param("userId") Long userId, @Param("subject") String subject, @Param("status") String status);

    /**
     * 根据用户ID和知识点精确查询单条薄弱点记录
     */
    @Select("SELECT * FROM user_weak_points WHERE user_id = #{userId} AND knowledge_point = #{knowledgePoint} LIMIT 1")
    UserWeakPoint selectByUserIdAndKnowledgePoint(@Param("userId") Long userId, @Param("knowledgePoint") String knowledgePoint);

    /**
     * 查询用户所有活跃的薄弱点
     */
    @Select("SELECT * FROM user_weak_points WHERE user_id = #{userId} AND status = 'ACTIVE' ORDER BY FIELD(weakness_level, 'HIGH', 'MEDIUM', 'LOW'), error_count DESC")
    List<UserWeakPoint> selectActiveByUserId(@Param("userId") Long userId);

    /**
     * 更新AI分析结果
     */
    @Update("UPDATE user_weak_points SET ai_analysis = #{aiAnalysis}, ai_suggestion = #{aiSuggestion}, analyzed_at = NOW() WHERE id = #{id}")
    int updateAiAnalysis(@Param("id") Long id, @Param("aiAnalysis") String aiAnalysis, @Param("aiSuggestion") String aiSuggestion);

    /**
     * 查询用户已解决的薄弱点对应的知识点（推荐练习用）
     */
    @Select("SELECT DISTINCT knowledge_point FROM user_weak_points WHERE user_id = #{userId} AND status = 'RESOLVED'")
    List<String> selectResolvedKnowledgePoints(@Param("userId") Long userId);
}
