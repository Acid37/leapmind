package com.treepeople.leapmindtts.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.treepeople.leapmindtts.pojo.entity.EventCollection;
import com.treepeople.leapmindtts.service.EventCollectionService;
import com.treepeople.leapmindtts.service.profile.security.ProfileActorResolver;
import com.treepeople.leapmindtts.exception.LegacyProfileSecurityExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * EventCollectionController 单元测试
 * <p>
 * 使用 MockMvcBuilders.standaloneSetup 仅测试 Controller 层逻辑，
 * Service 层通过 @Mock 注入，无需加载完整 Spring 上下文。
 * 覆盖事件采集相关的五个端点：采集、批量采集、查询未处理、查询用户、标记已处理。
 *
 * @author wuminxi
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("事件采集控制器单元测试")
class EventCollectionControllerTest {

    private MockMvc mockMvc;

    private static final ObjectMapper objectMapper;

    static {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Mock
    private EventCollectionService eventCollectionService;

    @Mock
    private ProfileActorResolver profileActorResolver;

    @BeforeEach
    void setUp() {
        EventCollectionController controller = new EventCollectionController(eventCollectionService, profileActorResolver);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .setControllerAdvice(new LegacyProfileSecurityExceptionHandler())
                .build();
    }

    // ========== 测试数据工厂 ==========

    private EventCollection createEvent(Long id, String module, String eventType,
                                         Long userId, String eventData, LocalDateTime eventTime,
                                         Integer processed, LocalDateTime processedAt) {
        return EventCollection.builder()
                .id(id)
                .module(module)
                .eventType(eventType)
                .userId(userId)
                .eventData(eventData)
                .eventTime(eventTime)
                .processed(processed)
                .processedAt(processedAt)
                .createdAt(LocalDateTime.of(2026, 7, 21, 10, 0))
                .build();
    }

    // ========== POST /api/events/collect ==========

    @Nested
    @DisplayName("POST /api/events/collect — 采集单条事件")
    class CollectEventTests {

        @Test
        @DisplayName("正常采集返回 200 和保存后的事件")
        void shouldReturn200WithSavedEvent() throws Exception {
            EventCollection event = createEvent(null, "M1", "COURSE_COMPLETED", 1L,
                    "{\"courseId\":\"C001\"}", LocalDateTime.of(2026, 7, 21, 10, 30), 0, null);
            EventCollection saved = createEvent(100L, "M1", "COURSE_COMPLETED", 1L,
                    "{\"courseId\":\"C001\"}", LocalDateTime.of(2026, 7, 21, 10, 30), 0, null);

            when(eventCollectionService.collectEvent(any(EventCollection.class))).thenReturn(saved);

            mockMvc.perform(post("/api/events/collect")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(event)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("事件采集成功"))
                    .andExpect(jsonPath("$.data.id").value(100))
                    .andExpect(jsonPath("$.data.module").value("M1"))
                    .andExpect(jsonPath("$.data.eventType").value("COURSE_COMPLETED"));

            verify(eventCollectionService).collectEvent(any(EventCollection.class));
        }

        @Test
        @DisplayName("eventData 为空时也能成功采集")
        void shouldReturn200WhenEventDataIsNull() throws Exception {
            EventCollection event = createEvent(null, "M2", "EXERCISE_SUBMITTED", 2L,
                    null, LocalDateTime.now(), null, null);
            EventCollection saved = createEvent(101L, "M2", "EXERCISE_SUBMITTED", 2L,
                    null, LocalDateTime.now(), 0, null);

            when(eventCollectionService.collectEvent(any(EventCollection.class))).thenReturn(saved);

            mockMvc.perform(post("/api/events/collect")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(event)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.eventData").doesNotExist());
        }

        @Test
        @DisplayName("Service 抛出异常时返回 400")
        void shouldReturn400WhenServiceThrowsException() throws Exception {
            EventCollection event = createEvent(null, "M1", "COURSE_COMPLETED", 1L,
                    "{}", LocalDateTime.now(), null, null);

            when(eventCollectionService.collectEvent(any(EventCollection.class)))
                    .thenThrow(new RuntimeException("数据库写入失败"));

            mockMvc.perform(post("/api/events/collect")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(event)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value("数据库写入失败"));
        }

        @Test
        @DisplayName("请求体为非 JSON 格式时返回 415")
        void shouldReturn415WhenContentTypeIsNotJson() throws Exception {
            mockMvc.perform(post("/api/events/collect")
                            .contentType(MediaType.TEXT_PLAIN)
                            .content("module=M1"))
                    .andExpect(status().isUnsupportedMediaType());
        }
    }

    // ========== POST /api/events/collect/batch ==========

    @Nested
    @DisplayName("POST /api/events/collect/batch — 批量采集事件")
    class CollectEventsBatchTests {

        @Test
        @DisplayName("正常批量采集返回 200 和事件列表")
        void shouldReturn200WithSavedEvents() throws Exception {
            EventCollection e1 = createEvent(null, "M1", "COURSE_STARTED", 1L,
                    "{}", LocalDateTime.now(), null, null);
            EventCollection e2 = createEvent(null, "M2", "ANSWER_CORRECT", 1L,
                    "{}", LocalDateTime.now(), null, null);
            List<EventCollection> events = Arrays.asList(e1, e2);

            EventCollection s1 = createEvent(200L, "M1", "COURSE_STARTED", 1L,
                    "{}", LocalDateTime.now(), 0, null);
            EventCollection s2 = createEvent(201L, "M2", "ANSWER_CORRECT", 1L,
                    "{}", LocalDateTime.now(), 0, null);
            List<EventCollection> saved = Arrays.asList(s1, s2);

            when(eventCollectionService.collectEvents(anyList())).thenReturn(saved);

            mockMvc.perform(post("/api/events/collect/batch")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(events)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("批量事件采集成功"))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data", hasSize(2)))
                    .andExpect(jsonPath("$.data[0].id").value(200))
                    .andExpect(jsonPath("$.data[1].id").value(201));

            verify(eventCollectionService).collectEvents(anyList());
        }

        @Test
        @DisplayName("Service 抛出异常时返回 400")
        void shouldReturn400WhenServiceThrowsException() throws Exception {
            when(eventCollectionService.collectEvents(anyList()))
                    .thenThrow(new RuntimeException("批量写入失败"));

            mockMvc.perform(post("/api/events/collect/batch")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("[{\"module\":\"M1\",\"userId\":1,\"eventType\":\"TEST\"}]"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value("批量写入失败"));
        }
    }

    // ========== GET /api/events/unprocessed/{module} ==========

    @Nested
    @DisplayName("GET /api/events/unprocessed/{module} — 查询未处理事件（已禁用）")
    class GetUnprocessedEventsTests {

        @Test
        @DisplayName("接口已禁用，返回 403")
        void shouldReturn403WhenEndpointDisabled() throws Exception {
            mockMvc.perform(get("/api/events/unprocessed/{module}", "M1"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value(403));

            verify(eventCollectionService, never()).getUnprocessedEvents(anyString());
        }
    }

    // ========== GET /api/events/user/{userId} ==========

    @Nested
    @DisplayName("GET /api/events/user/{userId} — 查询用户事件")
    class GetUserEventsTests {

        @Test
        @DisplayName("存在用户事件时返回 200 和事件列表")
        void shouldReturn200WithUserEvents() throws Exception {
            Long userId = 1L;
            EventCollection e1 = createEvent(1L, "M1", "COURSE_COMPLETED", userId,
                    "{}", LocalDateTime.now().minusDays(1), 0, null);
            EventCollection e2 = createEvent(2L, "M7", "STUDY_SESSION_END", userId,
                    "{}", LocalDateTime.now(), 1, LocalDateTime.now());
            List<EventCollection> events = Arrays.asList(e1, e2);

            when(eventCollectionService.getUserEvents(userId)).thenReturn(events);

            mockMvc.perform(get("/api/events/user/{userId}", userId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("查询用户事件成功"))
                    .andExpect(jsonPath("$.data", hasSize(2)))
                    .andExpect(jsonPath("$.data[0].userId").value(1))
                    .andExpect(jsonPath("$.data[1].userId").value(1));

            verify(eventCollectionService).getUserEvents(userId);
        }

        @Test
        @DisplayName("用户无事件时返回 200 和空数组")
        void shouldReturn200WithEmptyList() throws Exception {
            Long userId = 999L;
            when(eventCollectionService.getUserEvents(userId)).thenReturn(Collections.emptyList());

            mockMvc.perform(get("/api/events/user/{userId}", userId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data", hasSize(0)));
        }

        @Test
        @DisplayName("Service 抛出异常时返回 400")
        void shouldReturn400WhenServiceThrowsException() throws Exception {
            Long userId = 1L;
            when(eventCollectionService.getUserEvents(userId))
                    .thenThrow(new RuntimeException("查询用户事件失败"));

            mockMvc.perform(get("/api/events/user/{userId}", userId))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400));
        }
    }

    // ========== PUT /api/events/{eventId}/processed ==========

    @Nested
    @DisplayName("PUT /api/events/{eventId}/processed — 标记事件已处理（已禁用）")
    class MarkAsProcessedTests {

        @Test
        @DisplayName("接口已禁用，返回 403")
        void shouldReturn403WhenEndpointDisabled() throws Exception {
            mockMvc.perform(put("/api/events/{eventId}/processed", 100L))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value(403));

            verify(eventCollectionService, never()).markAsProcessed(anyLong());
        }
    }
}
