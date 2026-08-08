package com.treepeople.leapmindtts.service.user.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.treepeople.leapmindtts.exception.UserNotFoundException;
import com.treepeople.leapmindtts.mapper.ReviewReminderMapper;
import com.treepeople.leapmindtts.pojo.dto.MarkReviewedRequest;
import com.treepeople.leapmindtts.pojo.dto.profile.M6Dtos.LearningEventRequest;
import com.treepeople.leapmindtts.pojo.entity.ReviewReminder;
import com.treepeople.leapmindtts.pojo.vo.ReviewReminderVO;
import com.treepeople.leapmindtts.service.profile.UserEventService;
import com.treepeople.leapmindtts.service.user.ReviewReminderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 复习提醒服务实现类
 * <p>
 * 核心业务实现，包含三个主要功能：
 * <ol>
 *   <li><b>查询待复习</b> — 以当天日期为基准，查询 scheduled_date ≤ today 的所有未复习记录</li>
 *   <li><b>标记已复习</b> — 双重校验（存在性 + 归属权），事务性更新记录状态，并发布 M6 mark_reviewed 事件</li>
 *   <li><b>复习历史</b> — 合并已复习和未复习记录，统一返回</li>
 * </ol>
 * <p>
 * 设计要点：
 * <ul>
 *   <li>markAsReviewed 使用 @Transactional 保证状态更新原子性</li>
 *   <li>逾期未复习的记录也会出现在待复习列表中（scheduled_date < today 且未复习）</li>
 *   <li>Entity → VO 转换通过私有方法 convertToVO 统一处理，避免字段遗漏</li>
 *   <li>M6 事件发布失败不回滚"已复习"状态（outbox 补偿模式），仅记录错误日志</li>
 * </ul>
 *
 * @author wuminxi
 * @since 2026-07-21
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewReminderServiceImpl implements ReviewReminderService {

    private final ReviewReminderMapper reviewReminderMapper;
    private final UserEventService userEventService;
    private final ObjectMapper objectMapper;

    /**
     * 获取用户待复习提醒列表
     * <p>以 LocalDate.now() 作为截止日期，查询到期和逾期的未复习记录</p>
     */
    @Override
    public List<ReviewReminderVO> getReviewReminders(Long userId) {
        log.info("查询用户待复习提醒，用户ID: {}", userId);

        // 以当天日期为基准，查询 scheduled_date <= today 且未复习的所有记录
        List<ReviewReminder> reminders = reviewReminderMapper.selectPendingByUserId(userId, LocalDate.now());

        log.info("用户 {} 共有 {} 条待复习提醒", userId, reminders.size());
        return reminders.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
    }

    /**
     * 标记指定提醒为已复习
     * <p>
     * 执行流程：
     * <ol>
     *   <li>根据 ID 查询提醒记录，不存在则抛异常</li>
     *   <li>校验记录归属权，防止用户 A 标记用户 B 的提醒</li>
     *   <li>执行 UPDATE（含 notes 持久化），受影响行数为 0 则抛异常</li>
     *   <li>重新查询最新数据返回给前端</li>
     *   <li>发布 M6 mark_reviewed 事件（失败不回滚，仅记录日志）</li>
     * </ol>
     */
    @Override
    @Transactional
    public ReviewReminderVO markAsReviewed(Long userId, MarkReviewedRequest request) {
        log.info("用户 {} 标记复习提醒 {} 为已复习, result={}, timeSpentSec={}, hintCount={}",
                userId, request.getReminderId(), request.getResult(), request.getTimeSpentSec(), request.getHintCount());

        // 1. 查询提醒记录是否存在
        ReviewReminder reminder = reviewReminderMapper.selectById(request.getReminderId());
        if (reminder == null) {
            throw new UserNotFoundException("复习提醒不存在，ID: " + request.getReminderId());
        }

        // 2. 校验记录归属权，防止越权操作
        if (!java.util.Objects.equals(reminder.getUserId(), userId)) {
            throw new IllegalArgumentException("复习提醒不属于当前用户");
        }

        // 3. 执行状态更新（含 notes 持久化）
        int updated = reviewReminderMapper.markAsReviewed(request.getReminderId(), request.getNotes());
        if (updated <= 0) {
            throw new RuntimeException("标记已复习失败");
        }

        // 4. 重新查询最新数据返回（含数据库生成的时间戳）
        ReviewReminder updatedReminder = reviewReminderMapper.selectById(request.getReminderId());
        log.info("用户 {} 复习提醒 {} 已标记为已复习", userId, request.getReminderId());

        // 5. 发布 M6 mark_reviewed 事件（事务后异步投递，失败不回滚）
        publishMarkReviewedEvent(updatedReminder, request);

        return convertToVO(updatedReminder);
    }

    /**
     * 获取用户所有复习提醒（已复习 + 未复习）
     * <p>未复习记录在前，已复习记录在后，方便前端分类展示</p>
     */
    @Override
    public List<ReviewReminderVO> getAllReminders(Long userId) {
        log.info("查询用户所有复习提醒，用户ID: {}", userId);

        // 分别查询未复习和已复习记录，先未复习后已复习
        List<ReviewReminder> unreviewed = reviewReminderMapper.selectByUserIdAndStatus(userId, 0);
        List<ReviewReminder> reviewed = reviewReminderMapper.selectByUserIdAndStatus(userId, 1);

        // 合并列表：未复习在前，已复习在后
        java.util.ArrayList<ReviewReminder> allReminders = new java.util.ArrayList<>(unreviewed);
        allReminders.addAll(reviewed);
        return allReminders.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
    }

    /**
     * 发布 M6 mark_reviewed 事件
     * <p>
     * 事件发布失败不回滚"已复习"状态更新（outbox 补偿模式），
     * 仅记录错误日志供后续人工或定时任务补偿。
     * <p>
     * 事件 schema：
     * <ul>
     *   <li>eventType = mark_reviewed</li>
     *   <li>sourceModule = M6</li>
     *   <li>data.result = correct_without_hint / correct_with_hint / incorrect / still_confused / postponed</li>
     *   <li>data.timeSpentSec = 0-86400</li>
     *   <li>data.hintCount = 0-100</li>
     * </ul>
     *
     * @param reminder 已更新的复习提醒实体
     * @param request  前端提交的复习结果
     */
    private void publishMarkReviewedEvent(ReviewReminder reminder, MarkReviewedRequest request) {
        try {
            // 构建事件 data JSON
            ObjectNode data = objectMapper.createObjectNode();
            data.put("result", request.getResult());
            data.put("timeSpentSec", request.getTimeSpentSec());
            data.put("hintCount", request.getHintCount());

            // 构建 M6 事件请求
            LearningEventRequest event = new LearningEventRequest(
                    "review-" + reminder.getId(),                     // eventId — 基于 reminder 主键保证幂等
                    reminder.getUserId(),                              // userId
                    "mark_reviewed",                                   // eventType
                    "M6",                                              // sourceModule
                    OffsetDateTime.now(ZoneOffset.UTC),                // occurredAt
                    "1.0",                                             // schemaVersion
                    reminder.getCourseId(),                            // sessionId — 复用 courseId 做会话追踪
                    reminder.getKpId(),                                // kpId — 可为 null
                    null,                                              // traceId
                    data                                               // data
            );

            var ack = userEventService.recordInternal(event);
            log.info("M6 mark_reviewed 事件发布成功: eventId={}, userId={}, result={}, status={}",
                    event.eventId(), event.userId(), request.getResult(), ack.eventStatus());

        } catch (Exception e) {
            // outbox 补偿模式：事件发布失败不回滚业务状态
            log.error("M6 mark_reviewed 事件发布失败（已复习状态已保存，需补偿）: reminderId={}, userId={}, error={}",
                    reminder.getId(), reminder.getUserId(), e.getMessage(), e);
        }
    }

    /**
     * Entity → VO 转换
     * <p>统一转换入口，后续如需增加字段只需改此一处</p>
     *
     * @param reminder 复习提醒实体
     * @return 复习提醒视图对象
     */
    private ReviewReminderVO convertToVO(ReviewReminder reminder) {
        return ReviewReminderVO.builder()
                .id(reminder.getId())
                .userId(reminder.getUserId())
                .courseId(reminder.getCourseId())
                .reminderType(reminder.getReminderType())
                .content(reminder.getContent())
                .scheduledDate(reminder.getScheduledDate())
                .priority(reminder.getPriority())
                .isReviewed(reminder.getIsReviewed())
                .reviewedAt(reminder.getReviewedAt())
                .notes(reminder.getNotes())
                .kpId(reminder.getKpId())
                .createdAt(reminder.getCreatedAt())
                .build();
    }
}
