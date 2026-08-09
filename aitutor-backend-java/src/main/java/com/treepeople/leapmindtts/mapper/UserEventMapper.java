package com.treepeople.leapmindtts.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.treepeople.leapmindtts.pojo.entity.UserEvent;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserEventMapper extends BaseMapper<UserEvent> {
    @Select("select * from user_events where event_id=#{eventId}")
    UserEvent findByEventId(String eventId);

    @Select("select distinct user_id from user_events where process_status in ('PENDING','QUARANTINED') order by user_id")
    List<Long> selectPendingUserIds();

    @Select("select * from user_events where user_id=#{userId} and id>#{afterId} "
            + "and process_status in ('PENDING','QUARANTINED') order by id limit #{limit}")
    List<UserEvent> selectPendingSince(@Param("userId") Long userId, @Param("afterId") Long afterId, @Param("limit") int limit);
}
