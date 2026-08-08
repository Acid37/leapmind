package com.treepeople.leapmindtts.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.treepeople.leapmindtts.pojo.entity.UserEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserEventMapper extends BaseMapper<UserEvent> {
    @Select("select * from user_events where event_id=#{eventId}")
    UserEvent findByEventId(String eventId);
}
