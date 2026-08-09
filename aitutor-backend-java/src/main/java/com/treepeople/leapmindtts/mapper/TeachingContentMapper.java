package com.treepeople.leapmindtts.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.treepeople.leapmindtts.pojo.entity.TeachingContent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 备课内容Mapper接口
 */
@Mapper
public interface TeachingContentMapper extends BaseMapper<TeachingContent> {

    /**
     * 根据备课ID查询备课内容
     *
     * @param prepId 备课ID
     * @return 备课内容
     */
    @Select("SELECT * FROM teaching_contents WHERE prep_id = #{prepId}")
    TeachingContent selectByPrepId(@Param("prepId") Long prepId);
}
