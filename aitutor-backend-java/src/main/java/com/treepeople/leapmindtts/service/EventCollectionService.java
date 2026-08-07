package com.treepeople.leapmindtts.service;

import com.treepeople.leapmindtts.pojo.entity.EventCollection;

import java.util.List;

/**
 * 事件采集服务接口
 * <p>
 * 作为 M1/M2/M4/M7 各模块与复习系统之间的数据桥梁。
 * 提供事件采集、查询、标记已处理等核心能力，支撑：
 * <ul>
 *   <li>各模块实时上报用户学习行为事件</li>
 *   <li>定时任务批量拉取未处理事件进行汇总</li>
 *   <li>事件处理后的状态回写</li>
 * </ul>
 * <p>
 * 架构说明：
 * <pre>
 * M1 课程学习 ──┐
 * M2 练习答题 ──┼── POST /api/events/collect ──> event_collections 表
 * M4 知识图谱 ──┤                                    │
 * M7 学习分析 ──┘                                    ↓
 *                              定时任务 → Python AI → 生成复习提醒
 * </pre>
 *
 * @author wuminxi
 * @date 2026-07-21
 */
public interface EventCollectionService {

    /**
     * 采集单条事件数据
     * <p>自动补全 eventTime（如未传则取当前时间）和 processed 状态（默认 0）</p>
     *
     * @param eventCollection 事件数据（module/eventType/userId 为必填核心字段）
     * @return 保存后的事件（含数据库生成的 ID）
     */
    EventCollection collectEvent(EventCollection eventCollection);

    /**
     * 批量采集事件数据
     * <p>适用于模块需要一次性上报多条事件的场景，每条独立插入</p>
     *
     * @param events 事件列表
     * @return 保存后的事件列表（每条均含数据库生成的 ID）
     */
    List<EventCollection> collectEvents(List<EventCollection> events);

    /**
     * 获取指定模块的未处理事件
     * <p>供定时任务按模块维度拉取待处理数据</p>
     *
     * @param module 模块标识（M1/M2/M4/M7）
     * @return 该模块所有未处理的事件，按发生时间升序排列
     */
    List<EventCollection> getUnprocessedEvents(String module);

    /**
     * 获取用户的所有事件数据
     * <p>用于查询单个用户的学习行为时间线</p>
     *
     * @param userId 用户ID
     * @return 该用户所有事件，按发生时间降序排列
     */
    List<EventCollection> getUserEvents(Long userId);

    /**
     * 标记事件为已处理
     * <p>定时任务处理完事件后调用，防止重复处理。使用 MyBatis-Plus LambdaUpdateWrapper 实现精确字段更新</p>
     *
     * @param eventId 事件ID
     */
    void markAsProcessed(Long eventId);
}
