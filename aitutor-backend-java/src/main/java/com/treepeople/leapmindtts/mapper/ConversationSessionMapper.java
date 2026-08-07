package com.treepeople.leapmindtts.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.treepeople.leapmindtts.pojo.entity.ConversationSessionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ConversationSessionMapper extends BaseMapper<ConversationSessionEntity> {

    @Select("SELECT * FROM conversation_sessions WHERE session_id = #{sessionId} AND deleted = 0")
    ConversationSessionEntity selectBySessionId(@Param("sessionId") String sessionId);

    @Select("SELECT * FROM conversation_sessions WHERE user_id = #{userId} AND deleted = 0 ORDER BY updated_at DESC")
    List<ConversationSessionEntity> selectByUserId(@Param("userId") Long userId);

    @Update("UPDATE conversation_sessions SET deleted = 1 WHERE session_id = #{sessionId}")
    int logicDeleteBySessionId(@Param("sessionId") String sessionId);

    @Update("UPDATE conversation_sessions SET message_count = #{count} WHERE session_id = #{sessionId}")
    int updateMessageCount(@Param("sessionId") String sessionId, @Param("count") Integer count);
}