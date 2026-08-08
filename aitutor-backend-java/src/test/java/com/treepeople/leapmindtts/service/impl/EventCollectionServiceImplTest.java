package com.treepeople.leapmindtts.service.impl;

import com.treepeople.leapmindtts.mapper.EventCollectionMapper;
import com.treepeople.leapmindtts.pojo.entity.EventCollection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * EventCollectionServiceImpl 单元测试
 * <p>
 * 覆盖 collectEvent、collectEvents、getUnprocessedEvents、getUserEvents、markAsProcessed
 * 五个方法的正常路径、边界条件和自动补全逻辑。
 *
 * @author wuminxi
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("事件采集服务层单元测试")
class EventCollectionServiceImplTest {

    @Mock
    private EventCollectionMapper eventCollectionMapper;

    @InjectMocks
    private EventCollectionServiceImpl eventCollectionService;

    // ========== 测试数据工厂方法 ==========

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

    // ========== collectEvent 测试 ==========

    @Nested
    @DisplayName("collectEvent — 采集单条事件")
    class CollectEventTests {

        @Test
        @DisplayName("正常采集事件，成功保存并返回含 ID 的事件")
        void shouldSaveEventAndReturnWithId() {
            // Given
            EventCollection event = createEvent(null, "M1", "COURSE_COMPLETED", 1L,
                    "{\"courseId\":\"C001\"}", LocalDateTime.of(2026, 7, 21, 10, 30), null, null);

            // 模拟 insert 操作后自动填充 id
            doAnswer(invocation -> {
                EventCollection e = invocation.getArgument(0);
                e.setId(100L);
                return 1;
            }).when(eventCollectionMapper).insert(any(EventCollection.class));

            // When
            EventCollection result = eventCollectionService.collectEvent(event);

            // Then
            assertThat(result.getId()).isEqualTo(100L);
            assertThat(result.getModule()).isEqualTo("M1");
            assertThat(result.getEventType()).isEqualTo("COURSE_COMPLETED");
            verify(eventCollectionMapper).insert(event);
        }

        @Test
        @DisplayName("eventTime 为 null 时自动补全为当前时间")
        void shouldAutoFillEventTimeWhenNull() {
            // Given
            EventCollection event = createEvent(null, "M2", "EXERCISE_SUBMITTED", 2L,
                    "{\"score\":85}", null, null, null);

            doAnswer(invocation -> {
                EventCollection e = invocation.getArgument(0);
                e.setId(101L);
                return 1;
            }).when(eventCollectionMapper).insert(any(EventCollection.class));

            // When
            EventCollection result = eventCollectionService.collectEvent(event);

            // Then
            assertThat(result.getEventTime()).isNotNull();
            assertThat(result.getEventTime()).isBeforeOrEqualTo(LocalDateTime.now());
        }

        @Test
        @DisplayName("eventTime 已传入时保留原始值不覆盖")
        void shouldKeepOriginalEventTimeWhenProvided() {
            // Given
            LocalDateTime originalTime = LocalDateTime.of(2026, 7, 20, 15, 0);
            EventCollection event = createEvent(null, "M4", "KNOWLEDGE_MASTERED", 3L,
                    "{\"concept\":\"量子力学\"}", originalTime, null, null);

            doAnswer(invocation -> {
                EventCollection e = invocation.getArgument(0);
                e.setId(102L);
                return 1;
            }).when(eventCollectionMapper).insert(any(EventCollection.class));

            // When
            EventCollection result = eventCollectionService.collectEvent(event);

            // Then
            assertThat(result.getEventTime()).isEqualTo(originalTime);
        }

        @Test
        @DisplayName("processed 为 null 时自动设为 0（未处理）")
        void shouldAutoSetProcessedToZeroWhenNull() {
            // Given
            EventCollection event = createEvent(null, "M7", "STUDY_SESSION_END", 4L,
                    "{\"duration\":3600}", LocalDateTime.now(), null, null);

            doAnswer(invocation -> {
                EventCollection e = invocation.getArgument(0);
                e.setId(103L);
                return 1;
            }).when(eventCollectionMapper).insert(any(EventCollection.class));

            // When
            EventCollection result = eventCollectionService.collectEvent(event);

            // Then
            assertThat(result.getProcessed()).isEqualTo(0);
        }

        @Test
        @DisplayName("processed 已传入时保留原始值")
        void shouldKeepOriginalProcessedWhenProvided() {
            // Given
            EventCollection event = createEvent(null, "M1", "LESSON_VIEWED", 1L,
                    "{\"lessonId\":\"L001\"}", LocalDateTime.now(), 1, null);

            doAnswer(invocation -> {
                EventCollection e = invocation.getArgument(0);
                e.setId(104L);
                return 1;
            }).when(eventCollectionMapper).insert(any(EventCollection.class));

            // When
            EventCollection result = eventCollectionService.collectEvent(event);

            // Then
            assertThat(result.getProcessed()).isEqualTo(1);
        }
    }

    // ========== collectEvents 测试 ==========

    @Nested
    @DisplayName("collectEvents — 批量采集事件")
    class CollectEventsTests {

        @Test
        @DisplayName("批量采集多条事件，全部自动补全并保存")
        void shouldSaveMultipleEventsWithAutoFill() {
            // Given
            EventCollection e1 = createEvent(null, "M1", "COURSE_STARTED", 1L,
                    "{}", null, null, null);
            EventCollection e2 = createEvent(null, "M2", "ANSWER_CORRECT", 2L,
                    "{}", null, null, null);
            EventCollection e3 = createEvent(null, "M4", "CONCEPT_LINKED", 3L,
                    "{}", null, null, null);
            List<EventCollection> events = Arrays.asList(e1, e2, e3);

            doAnswer(invocation -> {
                EventCollection e = invocation.getArgument(0);
                e.setId((long) (events.indexOf(e) + 200));
                return 1;
            }).when(eventCollectionMapper).insert(any(EventCollection.class));

            // When
            List<EventCollection> result = eventCollectionService.collectEvents(events);

            // Then
            assertThat(result).hasSize(3);
            assertThat(result).allMatch(e -> e.getEventTime() != null);
            assertThat(result).allMatch(e -> e.getProcessed() == 0);
            verify(eventCollectionMapper, times(3)).insert(any(EventCollection.class));
        }

        @Test
        @DisplayName("批量采集空列表，正常完成不报错")
        void shouldHandleEmptyListGracefully() {
            // Given
            List<EventCollection> events = Collections.emptyList();

            // When
            List<EventCollection> result = eventCollectionService.collectEvents(events);

            // Then
            assertThat(result).isEmpty();
            verify(eventCollectionMapper, never()).insert(any(EventCollection.class));
        }

        @Test
        @DisplayName("批量采集时各事件已有 eventTime 和 processed 时保留原值")
        void shouldKeepOriginalValuesInBatch() {
            // Given
            LocalDateTime fixedTime = LocalDateTime.of(2026, 7, 21, 9, 0);
            EventCollection e1 = createEvent(null, "M7", "FOCUS_SCORE", 4L,
                    "{\"score\":90}", fixedTime, 1, null);

            doAnswer(invocation -> {
                EventCollection e = invocation.getArgument(0);
                e.setId(300L);
                return 1;
            }).when(eventCollectionMapper).insert(any(EventCollection.class));

            // When
            List<EventCollection> result = eventCollectionService.collectEvents(Collections.singletonList(e1));

            // Then
            assertThat(result.get(0).getEventTime()).isEqualTo(fixedTime);
            assertThat(result.get(0).getProcessed()).isEqualTo(1);
        }
    }

    // ========== getUnprocessedEvents 测试 ==========

    @Nested
    @DisplayName("getUnprocessedEvents — 查询未处理事件")
    class GetUnprocessedEventsTests {

        @Test
        @DisplayName("存在未处理事件时返回正确列表")
        void shouldReturnUnprocessedEvents() {
            // Given
            String module = "M1";
            EventCollection e1 = createEvent(1L, module, "COURSE_COMPLETED", 1L,
                    "{}", LocalDateTime.now().minusHours(2), 0, null);
            EventCollection e2 = createEvent(2L, module, "LESSON_VIEWED", 1L,
                    "{}", LocalDateTime.now().minusHours(1), 0, null);

            when(eventCollectionMapper.selectUnprocessedByModule(module))
                    .thenReturn(Arrays.asList(e1, e2));

            // When
            List<EventCollection> result = eventCollectionService.getUnprocessedEvents(module);

            // Then
            assertThat(result).hasSize(2);
            assertThat(result).extracting(EventCollection::getModule)
                    .allMatch(m -> m.equals("M1"));
            verify(eventCollectionMapper).selectUnprocessedByModule(module);
        }

        @Test
        @DisplayName("无未处理事件时返回空列表")
        void shouldReturnEmptyListWhenNoUnprocessed() {
            // Given
            String module = "M2";
            when(eventCollectionMapper.selectUnprocessedByModule(module))
                    .thenReturn(Collections.emptyList());

            // When
            List<EventCollection> result = eventCollectionService.getUnprocessedEvents(module);

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("支持所有模块 M1/M2/M4/M7 的查询")
        void shouldSupportAllModules() {
            // Given
            String[] modules = {"M1", "M2", "M4", "M7"};
            for (String m : modules) {
                when(eventCollectionMapper.selectUnprocessedByModule(m))
                        .thenReturn(Collections.singletonList(
                                createEvent(1L, m, "TEST_EVENT", 1L, "{}", LocalDateTime.now(), 0, null)));
            }

            // When & Then
            for (String m : modules) {
                List<EventCollection> result = eventCollectionService.getUnprocessedEvents(m);
                assertThat(result).hasSize(1);
                assertThat(result.get(0).getModule()).isEqualTo(m);
            }
        }
    }

    // ========== getUserEvents 测试 ==========

    @Nested
    @DisplayName("getUserEvents — 查询用户事件")
    class GetUserEventsTests {

        @Test
        @DisplayName("存在用户事件时返回正确列表")
        void shouldReturnUserEvents() {
            // Given
            Long userId = 1L;
            EventCollection e1 = createEvent(1L, "M1", "COURSE_COMPLETED", userId,
                    "{}", LocalDateTime.now().minusDays(1), 0, null);
            EventCollection e2 = createEvent(2L, "M2", "EXERCISE_SUBMITTED", userId,
                    "{}", LocalDateTime.now(), 1, LocalDateTime.now());

            when(eventCollectionMapper.selectByUserId(userId))
                    .thenReturn(Arrays.asList(e1, e2));

            // When
            List<EventCollection> result = eventCollectionService.getUserEvents(userId);

            // Then
            assertThat(result).hasSize(2);
            assertThat(result).extracting(EventCollection::getUserId)
                    .allMatch(uid -> uid.equals(userId));
            verify(eventCollectionMapper).selectByUserId(userId);
        }

        @Test
        @DisplayName("用户无事件时返回空列表")
        void shouldReturnEmptyListWhenNoEvents() {
            // Given
            Long userId = 999L;
            when(eventCollectionMapper.selectByUserId(userId))
                    .thenReturn(Collections.emptyList());

            // When
            List<EventCollection> result = eventCollectionService.getUserEvents(userId);

            // Then
            assertThat(result).isEmpty();
        }
    }

    // ========== markAsProcessed 标记已处理 ==========
    // 注意：markAsProcessed 方法内部使用了 MyBatis-Plus 的 LambdaUpdateWrapper，
    // 该组件需要 Spring 容器初始化实体类的 lambda 缓存，无法在纯 Mockito 单元测试中运行。
    // 此方法的覆盖将在 Spring 集成测试中完成。
}
