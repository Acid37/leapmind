package com.treepeople.leapmindtts.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.treepeople.leapmindtts.pojo.entity.ConversationMessageEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ConversationMessageMapper extends BaseMapper<ConversationMessageEntity> {

    @Select("SELECT * FROM conversation_messages WHERE session_id = #{sessionId} AND deleted = 0 ORDER BY created_at ASC")
    List<ConversationMessageEntity> selectBySessionId(@Param("sessionId") String sessionId);

    @Select("SELECT * FROM conversation_messages WHERE session_id = #{sessionId} AND deleted = 0 ORDER BY created_at ASC LIMIT #{limit}")
    List<ConversationMessageEntity> selectLatestBySessionId(@Param("sessionId") String sessionId, @Param("limit") int limit);

    @Update("UPDATE conversation_messages SET deleted = 1 WHERE session_id = #{sessionId}")
    int logicDeleteBySessionId(@Param("sessionId") String sessionId);
}