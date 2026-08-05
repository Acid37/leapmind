package com.treepeople.leapmindtts.service.user.impl;

import com.treepeople.leapmindtts.exception.UserNotFoundException;
import com.treepeople.leapmindtts.mapper.ReviewReminderMapper;
import com.treepeople.leapmindtts.pojo.dto.MarkReviewedRequest;
import com.treepeople.leapmindtts.pojo.entity.ReviewReminder;
import com.treepeople.leapmindtts.pojo.vo.ReviewReminderVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ReviewReminderServiceImpl 单元测试
 * <p>
 * 覆盖 getReviewReminders、markAsReviewed、getAllReminders 三个核心方法的
 * 正常路径、边界条件和异常路径。
 *
 * @author wuminxi
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("复习提醒服务层单元测试")
class ReviewReminderServiceImplTest {

    @Mock
    private ReviewReminderMapper reviewReminderMapper;

    @InjectMocks
    private ReviewReminderServiceImpl reviewReminderService;

    // ========== 测试数据工厂方法 ==========

    private ReviewReminder createReminder(Long id, Long userId, String courseId,
                                          String reminderType, String content,
                                          LocalDate scheduledDate, Integer priority,
                                          Integer isReviewed, LocalDateTime reviewedAt) {
        return ReviewReminder.builder()
                .id(id)
                .userId(userId)
                .courseId(courseId)
                .reminderType(reminderType)
                .content(content)
                .scheduledDate(scheduledDate)
                .priority(priority)
                .isReviewed(isReviewed)
                .reviewedAt(reviewedAt)
                .createdAt(LocalDateTime.of(2026, 7, 20, 10, 0))
                .updatedAt(LocalDateTime.of(2026, 7, 20, 10, 0))
                .build();
    }

    private MarkReviewedRequest createMarkReviewedRequest(Long reminderId) {
        MarkReviewedRequest request = new MarkReviewedRequest();
        request.setReminderId(reminderId);
        request.setNotes("已掌握");
        return request;
    }

    // ========== getReviewReminders 测试 ==========

    @Nested
    @DisplayName("getReviewReminders — 待复习提醒查询")
    class GetReviewRemindersTests {

        @Test
        @DisplayName("有多个待复习提醒时，返回正确的提醒列表")
        void shouldReturnRemindersWhenMultiplePending() {
            // Given
            Long userId = 1L;
            LocalDate today = LocalDate.now();
            ReviewReminder r1 = createReminder(1L, userId, "C001", "REVIEW",
                    "复习第1课单词", today.minusDays(1), 2, 0, null);
            ReviewReminder r2 = createReminder(2L, userId, "C002", "RECALL",
                    "回忆第2课语法", today, 1, 0, null);
            ReviewReminder r3 = createReminder(3L, userId, "C003", "SPACED_REPETITION",
                    "间隔复习第3课", today, 0, 0, null);
            List<ReviewReminder> pendingList = Arrays.asList(r1, r2, r3);

            when(reviewReminderMapper.selectPendingByUserId(eq(userId), any(LocalDate.class)))
                    .thenReturn(pendingList);

            // When
            List<ReviewReminderVO> result = reviewReminderService.getReviewReminders(userId);

            // Then
            assertThat(result).hasSize(3);
            assertThat(result).extracting(ReviewReminderVO::getId)
                    .containsExactly(1L, 2L, 3L);
            assertThat(result).extracting(ReviewReminderVO::getIsReviewed)
                    .allMatch(r -> r == 0);
            // 逾期记录也应该被包含
            assertThat(result).extracting(ReviewReminderVO::getScheduledDate)
                    .contains(today.minusDays(1));

            verify(reviewReminderMapper).selectPendingByUserId(eq(userId), any(LocalDate.class));
        }

        @Test
        @DisplayName("无待复习提醒时，返回空列表")
        void shouldReturnEmptyListWhenNoPendingReminders() {
            // Given
            Long userId = 1L;
            when(reviewReminderMapper.selectPendingByUserId(eq(userId), any(LocalDate.class)))
                    .thenReturn(Collections.emptyList());

            // When
            List<ReviewReminderVO> result = reviewReminderService.getReviewReminders(userId);

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("逾期未复习的记录也应出现在待复习列表中")
        void shouldIncludeOverdueReminders() {
            // Given
            Long userId = 1L;
            ReviewReminder overdue = createReminder(10L, userId, "C010", "REVIEW",
                    "逾期3天的复习", LocalDate.now().minusDays(3), 2, 0, null);
            ReviewReminder todayReminder = createReminder(11L, userId, "C011", "REVIEW",
                    "今天的复习", LocalDate.now(), 0, 0, null);

            when(reviewReminderMapper.selectPendingByUserId(eq(userId), any(LocalDate.class)))
                    .thenReturn(Arrays.asList(overdue, todayReminder));

            // When
            List<ReviewReminderVO> result = reviewReminderService.getReviewReminders(userId);

            // Then
            assertThat(result).hasSize(2);
            // 逾期记录应该排在前面（日期升序）
            assertThat(result.get(0).getScheduledDate()).isBefore(result.get(1).getScheduledDate());
        }

        @Test
        @DisplayName("Entity → VO 转换应包含所有必要字段")
        void shouldConvertAllFieldsFromEntityToVO() {
            // Given
            Long userId = 1L;
            ReviewReminder entity = ReviewReminder.builder()
                    .id(100L)
                    .userId(userId)
                    .courseId("C-FULL")
                    .reminderType("SPACED_REPETITION")
                    .content("完整内容")
                    .scheduledDate(LocalDate.of(2026, 7, 21))
                    .priority(2)
                    .isReviewed(0)
                    .reviewedAt(null)
                    .createdAt(LocalDateTime.of(2026, 7, 20, 12, 0))
                    .updatedAt(LocalDateTime.of(2026, 7, 20, 12, 0))
                    .build();

            when(reviewReminderMapper.selectPendingByUserId(eq(userId), any(LocalDate.class)))
                    .thenReturn(Collections.singletonList(entity));

            // When
            List<ReviewReminderVO> result = reviewReminderService.getReviewReminders(userId);

            // Then
            ReviewReminderVO vo = result.get(0);
            assertThat(vo.getId()).isEqualTo(100L);
            assertThat(vo.getUserId()).isEqualTo(userId);
            assertThat(vo.getCourseId()).isEqualTo("C-FULL");
            assertThat(vo.getReminderType()).isEqualTo("SPACED_REPETITION");
            assertThat(vo.getContent()).isEqualTo("完整内容");
            assertThat(vo.getScheduledDate()).isEqualTo(LocalDate.of(2026, 7, 21));
            assertThat(vo.getPriority()).isEqualTo(2);
            assertThat(vo.getIsReviewed()).isEqualTo(0);
            assertThat(vo.getReviewedAt()).isNull();
            assertThat(vo.getCreatedAt()).isNotNull();
        }
    }

    // ========== markAsReviewed 测试 ==========

    @Nested
    @DisplayName("markAsReviewed — 标记已复习")
    class MarkAsReviewedTests {

        @Test
        @DisplayName("正常标记已复习，返回更新后的提醒")
        void shouldMarkAsReviewedSuccessfully() {
            // Given
            Long userId = 1L;
            Long reminderId = 100L;
            MarkReviewedRequest request = createMarkReviewedRequest(reminderId);

            ReviewReminder beforeUpdate = createReminder(reminderId, userId, "C001",
                    "REVIEW", "内容", LocalDate.now(), 1, 0, null);
            ReviewReminder afterUpdate = createReminder(reminderId, userId, "C001",
                    "REVIEW", "内容", LocalDate.now(), 1, 1,
                    LocalDateTime.of(2026, 7, 21, 15, 30));

            when(reviewReminderMapper.selectById(reminderId)).thenReturn(beforeUpdate, afterUpdate);
            when(reviewReminderMapper.markAsReviewed(reminderId)).thenReturn(1);

            // When
            ReviewReminderVO result = reviewReminderService.markAsReviewed(userId, request);

            // Then
            assertThat(result.getIsReviewed()).isEqualTo(1);
            assertThat(result.getReviewedAt()).isNotNull();

            // 验证调用顺序：先查询 → 再更新 → 再查询（共2次）
            verify(reviewReminderMapper, times(2)).selectById(reminderId);
            verify(reviewReminderMapper).markAsReviewed(reminderId);
        }

        @Test
        @DisplayName("提醒不存在时抛出 UserNotFoundException")
        void shouldThrowExceptionWhenReminderNotFound() {
            // Given
            Long userId = 1L;
            MarkReviewedRequest request = createMarkReviewedRequest(999L);
            when(reviewReminderMapper.selectById(999L)).thenReturn(null);

            // When & Then
            assertThatThrownBy(() -> reviewReminderService.markAsReviewed(userId, request))
                    .isInstanceOf(UserNotFoundException.class)
                    .hasMessageContaining("复习提醒不存在")
                    .hasMessageContaining("999");

            verify(reviewReminderMapper, never()).markAsReviewed(anyLong());
        }

        @Test
        @DisplayName("提醒不属于当前用户时抛出 IllegalArgumentException")
        void shouldThrowExceptionWhenReminderBelongsToAnotherUser() {
            // Given
            Long currentUserId = 1L;
            Long otherUserId = 2L;
            Long reminderId = 100L;
            MarkReviewedRequest request = createMarkReviewedRequest(reminderId);

            ReviewReminder otherUsersReminder = createReminder(reminderId, otherUserId, "C001",
                    "REVIEW", "内容", LocalDate.now(), 1, 0, null);

            when(reviewReminderMapper.selectById(reminderId)).thenReturn(otherUsersReminder);

            // When & Then
            assertThatThrownBy(() -> reviewReminderService.markAsReviewed(currentUserId, request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("不属于当前用户");

            verify(reviewReminderMapper, never()).markAsReviewed(anyLong());
        }

        @Test
        @DisplayName("数据库更新影响行数为 0 时抛出 RuntimeException")
        void shouldThrowExceptionWhenUpdateAffectsZeroRows() {
            // Given
            Long userId = 1L;
            Long reminderId = 100L;
            MarkReviewedRequest request = createMarkReviewedRequest(reminderId);

            ReviewReminder reminder = createReminder(reminderId, userId, "C001",
                    "REVIEW", "内容", LocalDate.now(), 1, 0, null);

            when(reviewReminderMapper.selectById(reminderId)).thenReturn(reminder);
            when(reviewReminderMapper.markAsReviewed(reminderId)).thenReturn(0);

            // When & Then
            assertThatThrownBy(() -> reviewReminderService.markAsReviewed(userId, request))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("标记已复习失败");
        }

        @Test
        @DisplayName("重复标记已复习的记录也应该成功（幂等操作）")
        void shouldHandleMarkingAlreadyReviewedReminder() {
            // Given
            Long userId = 1L;
            Long reminderId = 100L;
            MarkReviewedRequest request = createMarkReviewedRequest(reminderId);

            LocalDateTime firstReviewTime = LocalDateTime.of(2026, 7, 20, 10, 0);
            ReviewReminder alreadyReviewed = createReminder(reminderId, userId, "C001",
                    "REVIEW", "内容", LocalDate.now(), 1, 1, firstReviewTime);

            // 模拟已复习的记录：查询返回已复习状态，更新仍然执行（幂等）
            when(reviewReminderMapper.selectById(reminderId)).thenReturn(alreadyReviewed);
            when(reviewReminderMapper.markAsReviewed(reminderId)).thenReturn(1);
            when(reviewReminderMapper.selectById(reminderId)).thenReturn(alreadyReviewed);

            // When
            ReviewReminderVO result = reviewReminderService.markAsReviewed(userId, request);

            // Then — 幂等操作不会失败
            assertThat(result.getIsReviewed()).isEqualTo(1);
            verify(reviewReminderMapper).markAsReviewed(reminderId);
        }

        @Test
        @DisplayName("userId 为 null 时仍正确进行归属权校验")
        void shouldHandleNullUserIdInReminder() {
            // Given
            Long userId = 1L;
            Long reminderId = 100L;
            MarkReviewedRequest request = createMarkReviewedRequest(reminderId);

            ReviewReminder reminderWithNullUserId = createReminder(reminderId, null, "C001",
                    "REVIEW", "内容", LocalDate.now(), 1, 0, null);

            when(reviewReminderMapper.selectById(reminderId)).thenReturn(reminderWithNullUserId);

            // When & Then — userId 不匹配（null != 1L）
            assertThatThrownBy(() -> reviewReminderService.markAsReviewed(userId, request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("不属于当前用户");
        }
    }

    // ========== getAllReminders 测试 ==========

    @Nested
    @DisplayName("getAllReminders — 获取所有复习记录")
    class GetAllRemindersTests {

        @Test
        @DisplayName("同时有未复习和已复习记录时，未复习在前")
        void shouldReturnUnreviewedFirstThenReviewed() {
            // Given
            Long userId = 1L;
            ReviewReminder unreviewed1 = createReminder(1L, userId, "C001", "REVIEW",
                    "未复习1", LocalDate.now().minusDays(2), 2, 0, null);
            ReviewReminder unreviewed2 = createReminder(2L, userId, "C002", "RECALL",
                    "未复习2", LocalDate.now(), 0, 0, null);
            ReviewReminder reviewed1 = createReminder(3L, userId, "C003", "REVIEW",
                    "已复习1", LocalDate.now().minusDays(5), 1, 1,
                    LocalDateTime.of(2026, 7, 20, 10, 0));
            ReviewReminder reviewed2 = createReminder(4L, userId, "C004", "SPACED_REPETITION",
                    "已复习2", LocalDate.now().minusDays(3), 0, 1,
                    LocalDateTime.of(2026, 7, 19, 15, 0));

            when(reviewReminderMapper.selectByUserIdAndStatus(userId, 0))
                    .thenReturn(Arrays.asList(unreviewed1, unreviewed2));
            when(reviewReminderMapper.selectByUserIdAndStatus(userId, 1))
                    .thenReturn(Arrays.asList(reviewed1, reviewed2));

            // When
            List<ReviewReminderVO> result = reviewReminderService.getAllReminders(userId);

            // Then
            assertThat(result).hasSize(4);
            // 前两个应该是未复习
            assertThat(result.get(0).getIsReviewed()).isEqualTo(0);
            assertThat(result.get(1).getIsReviewed()).isEqualTo(0);
            // 后两个应该是已复习
            assertThat(result.get(2).getIsReviewed()).isEqualTo(1);
            assertThat(result.get(3).getIsReviewed()).isEqualTo(1);
        }

        @Test
        @DisplayName("仅有未复习记录时，正确返回")
        void shouldReturnOnlyUnreviewedWhenNoReviewed() {
            // Given
            Long userId = 1L;
            ReviewReminder unreviewed = createReminder(1L, userId, "C001", "REVIEW",
                    "未复习", LocalDate.now(), 0, 0, null);

            when(reviewReminderMapper.selectByUserIdAndStatus(userId, 0))
                    .thenReturn(Collections.singletonList(unreviewed));
            when(reviewReminderMapper.selectByUserIdAndStatus(userId, 1))
                    .thenReturn(Collections.emptyList());

            // When
            List<ReviewReminderVO> result = reviewReminderService.getAllReminders(userId);

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getIsReviewed()).isEqualTo(0);
        }

        @Test
        @DisplayName("无任何复习记录时，返回空列表")
        void shouldReturnEmptyListWhenNoReminders() {
            // Given
            Long userId = 1L;
            when(reviewReminderMapper.selectByUserIdAndStatus(userId, 0))
                    .thenReturn(Collections.emptyList());
            when(reviewReminderMapper.selectByUserIdAndStatus(userId, 1))
                    .thenReturn(Collections.emptyList());

            // When
            List<ReviewReminderVO> result = reviewReminderService.getAllReminders(userId);

            // Then
            assertThat(result).isEmpty();
        }
    }
}
