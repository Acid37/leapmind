package com.treepeople.leapmindtts.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.treepeople.leapmindtts.pojo.entity.KnowledgePoint;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 知识点字典表 Mapper — 提供名称↔ID 翻译能力
 */
@Mapper
public interface KnowledgePointMapper extends BaseMapper<KnowledgePoint> {

    /**
     * 根据知识点名称查 ID（精确匹配，用于名称→ID 翻译）
     */
    @Select("SELECT id FROM knowledge_points WHERE name = #{name} LIMIT 1")
    Long selectIdByName(@Param("name") String name);

    /**
     * 批量根据名称查 ID
     */
    @Select("<script>" +
            "SELECT id, name FROM knowledge_points WHERE name IN " +
            "<foreach item='n' collection='names' open='(' separator=',' close=')'>" +
            "#{n}" +
            "</foreach>" +
            "</script>")
    List<java.util.Map<String, Object>> selectIdsByNames(@Param("names") List<String> names);
}
