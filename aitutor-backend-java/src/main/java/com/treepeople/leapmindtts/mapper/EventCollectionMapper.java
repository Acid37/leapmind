package com.treepeople.leapmindtts.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.treepeople.leapmindtts.pojo.entity.EventCollection;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 事件采集数据访问层
 * <p>
 * 负责 M1/M2/M4/M7 各模块上报事件的持久化操作。
 * 所有事件按 event_time 降序排列（最新在前），
 * 未处理事件按 event_time 升序排列（最早在前，优先处理）。
 * <p>
 * 典型使用场景：
 * <ol>
 *   <li>各模块调用 collect API → insert 到本表</li>
 *   <li>定时任务查询未处理事件 → 汇总发送给 Python 服务</li>
 *   <li>Python 服务处理完成后 → 调用 markAsProcessed 标记已处理</li>
 * </ol>
 *
 * @author wuminxi
 * @date 2026-07-21
 */
@Mapper
public interface EventCollectionMapper extends BaseMapper<EventCollection> {

    /**
     * 根据模块和事件类型查询事件列表
     * <p>用于按模块+事件类型维度检索事件数据，便于数据分析</p>
     *
     * @param module    模块标识（M1/M2/M4/M7）
     * @param eventType 事件类型（如 COURSE_COMPLETED）
     * @return 匹配的事件列表，按发生时间降序
     */
    @Select("SELECT * FROM event_collections WHERE module = #{module} AND event_type = #{eventType} ORDER BY event_time DESC")
    List<EventCollection> selectByModuleAndType(@Param("module") String module, @Param("eventType") String eventType);

    /**
     * 根据用户ID查询事件列表
     * <p>用于查看某个用户的所有学习行为事件，按时间降序</p>
     *
     * @param userId 用户ID
     * @return 该用户的所有事件列表
     */
    @Select("SELECT * FROM event_collections WHERE user_id = #{userId} ORDER BY event_time DESC")
    List<EventCollection> selectByUserId(@Param("userId") Long userId);

    /**
     * 查询指定时间范围内未处理的事件
     * <p>供定时任务批量拉取某一时间段内待处理的事件，按时间升序确保 FIFO</p>
     *
     * @param startTime 起始时间（含）
     * @param endTime   截止时间（含）
     * @return 未处理事件列表，按发生时间升序
     */
    @Select("SELECT * FROM event_collections WHERE processed = 0 AND event_time BETWEEN #{startTime} AND #{endTime} ORDER BY event_time ASC")
    List<EventCollection> selectUnprocessedByTimeRange(@Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);

    /**
     * 根据模块查询未处理的事件
     * <p>用于获取单个模块所有尚未被定时任务处理的事件</p>
     *
     * @param module 模块标识（M1/M2/M4/M7）
     * @return 该模块未处理的事件列表，按发生时间升序
     */
    @Select("SELECT * FROM event_collections WHERE module = #{module} AND processed = 0 ORDER BY event_time ASC")
    List<EventCollection> selectUnprocessedByModule(@Param("module") String module);
}
