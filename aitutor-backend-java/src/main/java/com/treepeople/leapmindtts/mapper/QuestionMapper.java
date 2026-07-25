package com.treepeople.leapmindtts.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 题库 Mapper（M2 题库匹配用）
 *
 * TODO: 等 M1 组建好 questions 表 + Question 实体后，
 *       改为 extends BaseMapper<Question>，返回 List<Question>
 */
@Mapper
public interface QuestionMapper {

    /**
     * 全文索引搜索相似题目
     */
    @Select("<script>" +
            "SELECT id, content_json, answer_json, subject, type, " +
            "MATCH(content_json) AGAINST(#{keyword} IN NATURAL LANGUAGE MODE) AS relevance " +
            "FROM questions " +
            "WHERE MATCH(content_json) AGAINST(#{keyword} IN NATURAL LANGUAGE MODE) " +
            "AND status = 1 " +
            "<if test='subject != null and subject != \"\"'>" +
            "AND subject = #{subject} " +
            "</if>" +
            "ORDER BY relevance DESC " +
            "LIMIT #{limit}" +
            "</script>")
    List<Map<String, Object>> fulltextSearch(@Param("keyword") String keyword,
                                              @Param("subject") String subject,
                                              @Param("limit") int limit);

    /**
     * LIKE 模糊匹配（兜底方案）
     */
    @Select("<script>" +
            "SELECT id, content_json, answer_json, subject, type FROM questions " +
            "WHERE status = 1 " +
            "<if test='subject != null and subject != \"\"'>" +
            "AND subject = #{subject} " +
            "</if>" +
            "AND content_json LIKE CONCAT('%', #{keyword}, '%') " +
            "LIMIT #{limit}" +
            "</script>")
    List<Map<String, Object>> likeSearch(@Param("keyword") String keyword,
                                          @Param("subject") String subject,
                                          @Param("limit") int limit);
}
