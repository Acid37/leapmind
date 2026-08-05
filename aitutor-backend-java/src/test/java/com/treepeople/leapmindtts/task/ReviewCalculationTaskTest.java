package com.treepeople.leapmindtts.task;

import com.treepeople.leapmindtts.config.PythonServiceProperties;
import com.treepeople.leapmindtts.mapper.ReviewReminderMapper;
import com.treepeople.leapmindtts.pojo.entity.ReviewReminder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ReviewCalculationTask 单元测试
 * <p>
 * 覆盖三个定时任务方法：全量复习计算、模块事件同步、过期提醒清理。
 * 使用 RETURNS_DEEP_STUBS 简化 WebClient 链式调用的 mock。
 *
 * @author wuminxi
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("复习计算定时任务单元测试")
class ReviewCalculationTaskTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private WebClient webClient;

    @Mock
    private PythonServiceProperties pythonServiceProperties;

    @Mock
    private ReviewReminderMapper reviewReminderMapper;

    @InjectMocks
    private ReviewCalculationTask reviewCalculationTask;

    private static final String BASE_URL = "http://localhost:8000";
    private static final String CALC_PATH = "/api/review/calculate-all";
    private static final String EVENT_PATH = "/api/events/process";

    @BeforeEach
    void setUp() {
        lenient().when(pythonServiceProperties.getBaseUrl()).thenReturn(BASE_URL);
        lenient().when(pythonServiceProperties.getReviewCalculationPath()).thenReturn(CALC_PATH);
        lenient().when(pythonServiceProperties.getEventProcessPath()).thenReturn(EVENT_PATH);
    }

    // ========== executeFullReviewCalculation 测试 ==========

    @Nested
    @DisplayName("executeFullReviewCalculation — 全量复习计算")
    class FullReviewCalculationTests {

        @Test
        @DisplayName("Python 服务正常响应时，任务成功完成")
        void shouldCompleteSuccessfullyWhenPythonResponds() {
            // Given — 使用 RETURNS_DEEP_STUBS 自动处理 WebClient 链式调用
            when(webClient.post()
                    .uri(BASE_URL + CALC_PATH)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block())
                    .thenReturn("{\"status\":\"success\",\"reminders_generated\":150}");

            // When — 不抛异常即为成功
            reviewCalculationTask.executeFullReviewCalculation();

            // Then — 验证调用了 Python 服务的正确 URL
            verify(webClient.post(), times(2)).uri(BASE_URL + CALC_PATH);
        }

        @Test
        @DisplayName("Python 服务调用失败时，异常被捕获，不影响其他任务")
        void shouldCatchExceptionWhenPythonCallFails() {
            // Given
            when(webClient.post()
                    .uri(BASE_URL + CALC_PATH)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block())
                    .thenThrow(new RuntimeException("Connection refused"));

            // When — 不抛异常（被内部 catch 捕获）
            reviewCalculationTask.executeFullReviewCalculation();

            // Then — 方法正常返回，不向上抛出异常
            verify(webClient.post(), times(2)).uri(BASE_URL + CALC_PATH);
        }

        @Test
        @DisplayName("Python 服务返回错误响应，任务也正常完成（不校验响应内容）")
        void shouldCompleteEvenWhenPythonReturnsError() {
            // Given
            when(webClient.post()
                    .uri(BASE_URL + CALC_PATH)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block())
                    .thenReturn("{\"status\":\"error\",\"message\":\"AI model overloaded\"}");

            // When — 不抛异常
            reviewCalculationTask.executeFullReviewCalculation();

            // Then — 调用正常完成
            verify(webClient.post(), times(2)).uri(BASE_URL + CALC_PATH);
        }
    }

    // ========== syncModuleEvents 测试 ==========

    @Nested
    @DisplayName("syncModuleEvents — 模块事件同步")
    class SyncModuleEventsTests {

        @Test
        @DisplayName("Python 服务正常响应时，事件同步成功")
        void shouldSyncEventsSuccessfully() {
            // Given
            when(webClient.post()
                    .uri(BASE_URL + EVENT_PATH)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block())
                    .thenReturn("{\"status\":\"success\",\"events_processed\":42}");

            // When
            reviewCalculationTask.syncModuleEvents();

            // Then
            verify(webClient.post(), times(2)).uri(BASE_URL + EVENT_PATH);
        }

        @Test
        @DisplayName("事件同步失败时，异常被捕获")
        void shouldCatchExceptionWhenEventSyncFails() {
            // Given
            when(webClient.post()
                    .uri(BASE_URL + EVENT_PATH)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block())
                    .thenThrow(new RuntimeException("Python service timeout"));

            // When — 不抛异常
            reviewCalculationTask.syncModuleEvents();

            // Then
            verify(webClient.post(), times(2)).uri(BASE_URL + EVENT_PATH);
        }

        @Test
        @DisplayName("使用正确的 URL 路径调用事件处理接口")
        void shouldCallCorrectEventProcessUrl() {
            // Given
            when(webClient.post()
                    .uri(BASE_URL + EVENT_PATH)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block())
                    .thenReturn("OK");

            // When
            reviewCalculationTask.syncModuleEvents();

            // Then — 验证 URL 是正确的完整路径
            verify(webClient.post(), times(2)).uri(BASE_URL + EVENT_PATH);
        }
    }

    // ========== cleanExpiredReminders 测试 ==========

    @Nested
    @DisplayName("cleanExpiredReminders — 过期提醒清理")
    class CleanExpiredRemindersTests {

        @Test
        @DisplayName("存在过期超过30天的记录时，正确识别并记录")
        void shouldIdentifyExpiredReminders() {
            // Given
            ReviewReminder recent = ReviewReminder.builder()
                    .id(1L).userId(1L).courseId("C001")
                    .scheduledDate(LocalDate.now().minusDays(5))
                    .isReviewed(0)
                    .build();
            ReviewReminder expired1 = ReviewReminder.builder()
                    .id(2L).userId(1L).courseId("C002")
                    .scheduledDate(LocalDate.now().minusDays(31))
                    .isReviewed(0)
                    .build();
            ReviewReminder expired2 = ReviewReminder.builder()
                    .id(3L).userId(2L).courseId("C003")
                    .scheduledDate(LocalDate.now().minusDays(60))
                    .isReviewed(0)
                    .build();
            List<ReviewReminder> pendingList = Arrays.asList(recent, expired1, expired2);

            when(reviewReminderMapper.selectAllPendingReminders(any(LocalDate.class)))
                    .thenReturn(pendingList);

            // When
            reviewCalculationTask.cleanExpiredReminders();

            // Then
            verify(reviewReminderMapper).selectAllPendingReminders(any(LocalDate.class));
        }

        @Test
        @DisplayName("无过期记录时，任务正常完成")
        void shouldCompleteWhenNoExpiredReminders() {
            // Given
            ReviewReminder recent = ReviewReminder.builder()
                    .id(1L).userId(1L).courseId("C001")
                    .scheduledDate(LocalDate.now().minusDays(1))
                    .isReviewed(0)
                    .build();

            when(reviewReminderMapper.selectAllPendingReminders(any(LocalDate.class)))
                    .thenReturn(Collections.singletonList(recent));

            // When — 不抛异常
            reviewCalculationTask.cleanExpiredReminders();

            // Then
            verify(reviewReminderMapper).selectAllPendingReminders(any(LocalDate.class));
        }

        @Test
        @DisplayName("无任何待复习记录时，任务正常完成")
        void shouldCompleteWhenNoPendingReminders() {
            // Given
            when(reviewReminderMapper.selectAllPendingReminders(any(LocalDate.class)))
                    .thenReturn(Collections.emptyList());

            // When — 不抛异常
            reviewCalculationTask.cleanExpiredReminders();

            // Then
            verify(reviewReminderMapper).selectAllPendingReminders(any(LocalDate.class));
        }

        @Test
        @DisplayName("数据库查询失败时，异常被捕获")
        void shouldCatchExceptionWhenDbQueryFails() {
            // Given
            when(reviewReminderMapper.selectAllPendingReminders(any(LocalDate.class)))
                    .thenThrow(new RuntimeException("数据库连接失败"));

            // When — 不抛异常
            reviewCalculationTask.cleanExpiredReminders();

            // Then
            verify(reviewReminderMapper).selectAllPendingReminders(any(LocalDate.class));
        }

        @Test
        @DisplayName("scheduledDate 为 null 的记录正确跳过")
        void shouldSkipRemindersWithNullScheduledDate() {
            // Given
            ReviewReminder nullDate = ReviewReminder.builder()
                    .id(1L).userId(1L).courseId("C001")
                    .scheduledDate(null)  // 异常数据
                    .isReviewed(0)
                    .build();

            when(reviewReminderMapper.selectAllPendingReminders(any(LocalDate.class)))
                    .thenReturn(Collections.singletonList(nullDate));

            // When — 不抛 NPE（null 检查生效）
            reviewCalculationTask.cleanExpiredReminders();

            // Then
            verify(reviewReminderMapper).selectAllPendingReminders(any(LocalDate.class));
        }

        @Test
        @DisplayName("恰好 30 天前的记录不被视为过期")
        void shouldNotFlagExactly30DaysAsExpired() {
            // Given
            ReviewReminder exactly30Days = ReviewReminder.builder()
                    .id(1L).userId(1L).courseId("C001")
                    .scheduledDate(LocalDate.now().minusDays(30))
                    .isReviewed(0)
                    .build();

            when(reviewReminderMapper.selectAllPendingReminders(any(LocalDate.class)))
                    .thenReturn(Collections.singletonList(exactly30Days));

            // When
            reviewCalculationTask.cleanExpiredReminders();

            // Then — 不抛异常，30 天的记录不被标记为过期（isBefore，不包含等于）
            verify(reviewReminderMapper).selectAllPendingReminders(any(LocalDate.class));
        }
    }
}
