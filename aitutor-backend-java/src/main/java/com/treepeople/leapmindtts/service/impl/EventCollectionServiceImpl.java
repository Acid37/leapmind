package com.treepeople.leapmindtts.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.treepeople.leapmindtts.mapper.EventCollectionMapper;
import com.treepeople.leapmindtts.pojo.entity.EventCollection;
import com.treepeople.leapmindtts.service.EventCollectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 事件采集服务实现类
 * <p>
 * 实现 M1/M2/M4/M7 各模块事件数据的统一采集与查询。
 * 设计要点：
 * <ul>
 *   <li><b>采集接口不做格式校验</b> — eventData 为 JSON 自由格式，由各模块自行定义</li>
 *   <li><b>自动补全缺失字段</b> — eventTime 取当前时间，processed 默认 0</li>
 *   <li><b>批量采集逐条插入</b> — 每条事件独立 insert，保证部分失败不影响其他</li>
 *   <li><b>处理状态更新使用 LambdaUpdateWrapper</b> — 避免硬编码字段名，类型安全</li>
 * </ul>
 * <p>
 * 模块事件类型参考：
 * <ul>
 *   <li>M1 课程学习：COURSE_STARTED / COURSE_COMPLETED / LESSON_VIEWED</li>
 *   <li>M2 练习答题：EXERCISE_SUBMITTED / ANSWER_CORRECT / ANSWER_WRONG</li>
 *   <li>M4 知识图谱：KNOWLEDGE_MASTERED / KNOWLEDGE_WEAK / CONCEPT_LINKED</li>
 *   <li>M7 学习分析：STUDY_SESSION_START / STUDY_SESSION_END / FOCUS_SCORE</li>
 * </ul>
 *
 * @author wuminxi
 * @date 2026-07-21
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventCollectionServiceImpl implements EventCollectionService {

    private final EventCollectionMapper eventCollectionMapper;

    /**
     * 采集单条事件数据
     * <p>
     * 自动补全策略：
     * <ul>
     *   <li>eventTime 为空 → 取当前时间（事件即时报）</li>
     *   <li>processed 为空 → 默认 0（待处理）</li>
     * </ul>
     */
    @Override
    @Transactional
    public EventCollection collectEvent(EventCollection eventCollection) {
        log.info("采集事件，模块: {}, 事件类型: {}, 用户ID: {}",
                eventCollection.getModule(), eventCollection.getEventType(), eventCollection.getUserId());

        // 自动补全缺失的时间字段
        if (eventCollection.getEventTime() == null) {
            eventCollection.setEventTime(LocalDateTime.now());
        }
        // 新采集的事件默认未处理
        if (eventCollection.getProcessed() == null) {
            eventCollection.setProcessed(0);
        }

        eventCollectionMapper.insert(eventCollection);
        log.info("事件采集成功，事件ID: {}", eventCollection.getId());

        return eventCollection;
    }

    /**
     * 批量采集事件数据
     * <p>整个批量操作在一个事务中，全部成功或全部回滚。适用场景：模块批量同步历史数据</p>
     */
    @Override
    @Transactional
    public List<EventCollection> collectEvents(List<EventCollection> events) {
        log.info("批量采集事件，共 {} 条", events.size());

        // 逐条处理：自动补全字段后插入
        for (EventCollection event : events) {
            if (event.getEventTime() == null) {
                event.setEventTime(LocalDateTime.now());
            }
            if (event.getProcessed() == null) {
                event.setProcessed(0);
            }
            eventCollectionMapper.insert(event);
        }

        log.info("批量事件采集完成，共 {} 条", events.size());
        return events;
    }

    /**
     * 获取指定模块的未处理事件
     * <p>按 event_time 升序返回，确保先发生的事件先处理（FIFO）</p>
     */
    @Override
    public List<EventCollection> getUnprocessedEvents(String module) {
        log.info("查询模块 {} 未处理的事件", module);
        return eventCollectionMapper.selectUnprocessedByModule(module);
    }

    /**
     * 获取用户的所有事件数据
     * <p>按 event_time 降序，最近的事件排在最前面</p>
     */
    @Override
    public List<EventCollection> getUserEvents(Long userId) {
        log.info("查询用户 {} 的事件数据", userId);
        return eventCollectionMapper.selectByUserId(userId);
    }

    /**
     * 标记事件为已处理
     * <p>
     * 使用 MyBatis-Plus 的 LambdaUpdateWrapper 进行字段级精确更新：
     * <ul>
     *   <li>processed 置为 1（已处理）</li>
     *   <li>processedAt 写入当前时间（处理完成时间戳）</li>
     * </ul>
     * 这是一种乐观的标记策略：仅做状态更新，不做额外的业务校验
     */
    @Override
    @Transactional
    public void markAsProcessed(Long eventId) {
        log.info("标记事件 {} 为已处理", eventId);

        // 使用 LambdaUpdateWrapper 避免硬编码字段名，编译期类型安全
        LambdaUpdateWrapper<EventCollection> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(EventCollection::getId, eventId)
                .set(EventCollection::getProcessed, 1)
                .set(EventCollection::getProcessedAt, LocalDateTime.now());

        eventCollectionMapper.update(null, updateWrapper);
    }
}
