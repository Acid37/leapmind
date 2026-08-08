package com.treepeople.leapmindtts.controller.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.treepeople.leapmindtts.exception.UserNotFoundException;
import com.treepeople.leapmindtts.exception.LegacyProfileSecurityExceptionHandler;
import com.treepeople.leapmindtts.exception.M6ApiException;
import com.treepeople.leapmindtts.pojo.dto.MarkReviewedRequest;
import com.treepeople.leapmindtts.pojo.vo.ReviewReminderVO;
import com.treepeople.leapmindtts.service.user.ReviewReminderService;
import com.treepeople.leapmindtts.service.profile.security.ProfileActorResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * UserProfileController 单元测试
 * <p>
 * 使用 MockMvcBuilders.standaloneSetup 仅测试 Controller 层逻辑，
 * Service 层通过 @Mock 注入，无需加载完整 Spring 上下文。
 * 覆盖复习提醒相关的三个端点：待复习提醒查询、标记已复习、复习历史。
 *
 * @author wuminxi
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("用户画像控制器单元测试")
class UserProfileControllerTest {

    private MockMvc mockMvc;

    private static final ObjectMapper objectMapper;

    static {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        objectMapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Mock
    private ReviewReminderService reviewReminderService;
    @Mock
    private ProfileActorResolver profileActorResolver;

    @BeforeEach
    void setUp() {
        UserProfileController controller = new UserProfileController(reviewReminderService, profileActorResolver);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .setControllerAdvice(new LegacyProfileSecurityExceptionHandler())
                .build();
    }

    // ========== 测试数据工厂 ==========

    private ReviewReminderVO createVO(Long id, Long userId, String courseId,
                                      String reminderType, String content,
                                      LocalDate scheduledDate, Integer priority,
                                      Integer isReviewed, LocalDateTime reviewedAt) {
        return ReviewReminderVO.builder()
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
                .build();
    }

    // ========== GET /{userId}/review-reminders ==========

    @Nested
    @DisplayName("GET /{userId}/review-reminders — 待复习提醒查询")
    class GetReviewRemindersTests {

        @Test
        @DisplayName("正常查询返回 200 和提醒列表")
        void shouldReturn200WithReminderList() throws Exception {
            Long userId = 1L;
            ReviewReminderVO vo1 = createVO(1L, userId, "C001", "REVIEW",
                    "复习单词", LocalDate.now(), 2, 0, null);
            ReviewReminderVO vo2 = createVO(2L, userId, "C002", "RECALL",
                    "回忆语法", LocalDate.now(), 0, 0, null);
            List<ReviewReminderVO> reminders = Arrays.asList(vo1, vo2);

            when(reviewReminderService.getReviewReminders(userId)).thenReturn(reminders);

            mockMvc.perform(get("/api/user-profile/{userId}/review-reminders", userId))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("查询待复习提醒成功"))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data", hasSize(2)))
                    .andExpect(jsonPath("$.data[0].id").value(1))
                    .andExpect(jsonPath("$.data[0].reminderType").value("REVIEW"))
                    .andExpect(jsonPath("$.data[1].id").value(2));

            verify(reviewReminderService).getReviewReminders(userId);
        }

        @Test
        @DisplayName("无待复习提醒时返回 200 和空数组")
        void shouldReturn200WithEmptyList() throws Exception {
            Long userId = 1L;
            when(reviewReminderService.getReviewReminders(userId)).thenReturn(Collections.emptyList());

            mockMvc.perform(get("/api/user-profile/{userId}/review-reminders", userId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data", hasSize(0)));
        }

        @Test
        @DisplayName("Service 抛出异常时返回 400")
        void shouldReturn400WhenServiceThrowsException() throws Exception {
            Long userId = 1L;
            when(reviewReminderService.getReviewReminders(userId))
                    .thenThrow(new RuntimeException("数据库连接失败"));

            mockMvc.perform(get("/api/user-profile/{userId}/review-reminders", userId))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value("数据库连接失败"));
        }

        @Test
        @DisplayName("negative userId is rejected before the service runs")
        void shouldRejectNegativeUserId() throws Exception {
            Long negativeUserId = -1L;
            doThrow(new M6ApiException(HttpStatus.FORBIDDEN, "PROFILE_ACCESS_DENIED", "denied"))
                    .when(profileActorResolver).authorizeSelf(any(), eq(negativeUserId));

            mockMvc.perform(get("/api/user-profile/{userId}/review-reminders", negativeUserId))
                    .andExpect(status().isForbidden());
            verifyNoInteractions(reviewReminderService);
        }

        @Test
        @DisplayName("返回的 JSON 包含所有 VO 字段")
        void shouldReturnCompleteVOFields() throws Exception {
            Long userId = 1L;
            ReviewReminderVO vo = createVO(100L, userId, "C-FULL", "SPACED_REPETITION",
                    "完整复习内容", LocalDate.of(2026, 7, 21), 2, 0, null);

            when(reviewReminderService.getReviewReminders(userId))
                    .thenReturn(Collections.singletonList(vo));

            mockMvc.perform(get("/api/user-profile/{userId}/review-reminders", userId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].id").value(100))
                    .andExpect(jsonPath("$.data[0].userId").value(1))
                    .andExpect(jsonPath("$.data[0].courseId").value("C-FULL"))
                    .andExpect(jsonPath("$.data[0].reminderType").value("SPACED_REPETITION"))
                    .andExpect(jsonPath("$.data[0].content").value("完整复习内容"))
                    .andExpect(jsonPath("$.data[0].scheduledDate").value("2026-07-21"))
                    .andExpect(jsonPath("$.data[0].priority").value(2))
                    .andExpect(jsonPath("$.data[0].isReviewed").value(0))
                    .andExpect(jsonPath("$.data[0].reviewedAt").doesNotExist())
                    .andExpect(jsonPath("$.data[0].createdAt").isNotEmpty());
        }
    }

    // ========== POST /{userId}/mark-reviewed ==========

    @Nested
    @DisplayName("POST /{userId}/mark-reviewed — 标记已复习")
    class MarkReviewedTests {

        @Test
        @DisplayName("正常标记返回 200 和更新后的提醒")
        void shouldReturn200WithUpdatedReminder() throws Exception {
            Long userId = 1L;
            MarkReviewedRequest request = new MarkReviewedRequest();
            request.setReminderId(100L);
            request.setNotes("已掌握该知识点");

            ReviewReminderVO updatedVO = createVO(100L, userId, "C001", "REVIEW",
                    "复习内容", LocalDate.now(), 1, 1,
                    LocalDateTime.of(2026, 7, 21, 15, 30));

            when(reviewReminderService.markAsReviewed(eq(userId), any(MarkReviewedRequest.class)))
                    .thenReturn(updatedVO);

            mockMvc.perform(post("/api/user-profile/{userId}/mark-reviewed", userId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("标记已复习成功"))
                    .andExpect(jsonPath("$.data.id").value(100))
                    .andExpect(jsonPath("$.data.isReviewed").value(1))
                    .andExpect(jsonPath("$.data.reviewedAt").isNotEmpty());

            verify(reviewReminderService).markAsReviewed(eq(userId), any(MarkReviewedRequest.class));
        }

        @Test
        @DisplayName("reminderId 为 null 时返回 400")
        void shouldReturn400WhenReminderIdIsNull() throws Exception {
            Long userId = 1L;
            MarkReviewedRequest request = new MarkReviewedRequest();
            request.setReminderId(null);

            mockMvc.perform(post("/api/user-profile/{userId}/mark-reviewed", userId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verify(reviewReminderService, never()).markAsReviewed(anyLong(), any());
        }

        @Test
        @DisplayName("提醒不存在时返回 400")
        void shouldReturn400WhenReminderNotFound() throws Exception {
            Long userId = 1L;
            MarkReviewedRequest request = new MarkReviewedRequest();
            request.setReminderId(999L);

            when(reviewReminderService.markAsReviewed(eq(userId), any(MarkReviewedRequest.class)))
                    .thenThrow(new UserNotFoundException("复习提醒不存在，ID: 999"));

            mockMvc.perform(post("/api/user-profile/{userId}/mark-reviewed", userId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message", containsString("复习提醒不存在")));
        }

        @Test
        @DisplayName("越权操作时返回 400")
        void shouldReturn400WhenUnauthorized() throws Exception {
            Long userId = 1L;
            MarkReviewedRequest request = new MarkReviewedRequest();
            request.setReminderId(100L);

            when(reviewReminderService.markAsReviewed(eq(userId), any(MarkReviewedRequest.class)))
                    .thenThrow(new IllegalArgumentException("复习提醒不属于当前用户"));

            mockMvc.perform(post("/api/user-profile/{userId}/mark-reviewed", userId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message", containsString("不属于当前用户")));
        }

        @Test
        @DisplayName("请求体为空时返回 400")
        void shouldReturn400WhenBodyIsEmpty() throws Exception {
            Long userId = 1L;

            mockMvc.perform(post("/api/user-profile/{userId}/mark-reviewed", userId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("请求体为非 JSON 格式时返回 415")
        void shouldReturn415WhenContentTypeIsNotJson() throws Exception {
            Long userId = 1L;

            mockMvc.perform(post("/api/user-profile/{userId}/mark-reviewed", userId)
                            .contentType(MediaType.TEXT_PLAIN)
                            .content("reminderId=100"))
                    .andExpect(status().isUnsupportedMediaType());
        }
    }

    // ========== GET /{userId}/review-history ==========

    @Nested
    @DisplayName("GET /{userId}/review-history — 复习历史查询")
    class GetReviewHistoryTests {

        @Test
        @DisplayName("正常查询返回 200 和复习记录")
        void shouldReturn200WithReviewHistory() throws Exception {
            Long userId = 1L;
            ReviewReminderVO unreviewed = createVO(1L, userId, "C001", "REVIEW",
                    "未复习", LocalDate.now(), 0, 0, null);
            ReviewReminderVO reviewed = createVO(2L, userId, "C002", "REVIEW",
                    "已复习", LocalDate.now().minusDays(1), 1, 1,
                    LocalDateTime.of(2026, 7, 20, 10, 0));
            List<ReviewReminderVO> allReminders = Arrays.asList(unreviewed, reviewed);

            when(reviewReminderService.getAllReminders(userId)).thenReturn(allReminders);

            mockMvc.perform(get("/api/user-profile/{userId}/review-history", userId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("查询复习记录成功"))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data", hasSize(2)))
                    .andExpect(jsonPath("$.data[0].isReviewed").value(0))
                    .andExpect(jsonPath("$.data[1].isReviewed").value(1));
        }

        @Test
        @DisplayName("无复习记录时返回 200 和空数组")
        void shouldReturn200WithEmptyList() throws Exception {
            Long userId = 1L;
            when(reviewReminderService.getAllReminders(userId)).thenReturn(Collections.emptyList());

            mockMvc.perform(get("/api/user-profile/{userId}/review-history", userId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data", hasSize(0)));
        }

        @Test
        @DisplayName("Service 抛出异常时返回 400")
        void shouldReturn400WhenServiceThrowsException() throws Exception {
            Long userId = 1L;
            when(reviewReminderService.getAllReminders(userId))
                    .thenThrow(new RuntimeException("查询失败"));

            mockMvc.perform(get("/api/user-profile/{userId}/review-history", userId))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400));
        }
    }
}
