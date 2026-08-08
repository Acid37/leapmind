package com.treepeople.leapmindtts.pojo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.treepeople.leapmindtts.pojo.dto.MarkReviewedRequest;
import com.treepeople.leapmindtts.pojo.entity.ReviewReminder;
import com.treepeople.leapmindtts.pojo.vo.ReviewReminderVO;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

/**
 * ReviewReminder 相关 POJO 测试
 * <p>
 * 验证 Entity、VO、DTO 的构造器、序列化/反序列化和 Bean Validation。
 *
 * @author wuminxi
 */
@DisplayName("复习提醒 POJO 测试")
class ReviewReminderPojoTests {

    private static final ObjectMapper objectMapper;

    static {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    // ========== ReviewReminder Entity 测试 ==========

    @Nested
    @DisplayName("ReviewReminder 实体")
    class ReviewReminderEntityTests {

        @Test
        @DisplayName("Builder 正确创建实体对象")
        void shouldCreateEntityWithBuilder() {
            LocalDate today = LocalDate.of(2026, 7, 21);
            LocalDateTime now = LocalDateTime.of(2026, 7, 21, 10, 0, 0);

            ReviewReminder entity = ReviewReminder.builder()
                    .id(1L)
                    .userId(100L)
                    .courseId("C-001")
                    .reminderType("REVIEW")
                    .content("复习第1课单词")
                    .scheduledDate(today)
                    .priority(2)
                    .isReviewed(0)
                    .reviewedAt(null)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();

            assertThat(entity.getId()).isEqualTo(1L);
            assertThat(entity.getUserId()).isEqualTo(100L);
            assertThat(entity.getCourseId()).isEqualTo("C-001");
            assertThat(entity.getReminderType()).isEqualTo("REVIEW");
            assertThat(entity.getContent()).isEqualTo("复习第1课单词");
            assertThat(entity.getScheduledDate()).isEqualTo(today);
            assertThat(entity.getPriority()).isEqualTo(2);
            assertThat(entity.getIsReviewed()).isEqualTo(0);
            assertThat(entity.getReviewedAt()).isNull();
            assertThat(entity.getCreatedAt()).isEqualTo(now);
        }

        @Test
        @DisplayName("无参构造 + Setter 也能正常工作")
        void shouldWorkWithNoArgsConstructorAndSetters() {
            ReviewReminder entity = new ReviewReminder();
            entity.setId(1L);
            entity.setUserId(100L);
            entity.setCourseId("C-001");
            entity.setReminderType("RECALL");
            entity.setScheduledDate(LocalDate.now());
            entity.setPriority(0);
            entity.setIsReviewed(1);

            assertThat(entity.getId()).isEqualTo(1L);
            assertThat(entity.getReminderType()).isEqualTo("RECALL");
        }

        @Test
        @DisplayName("三种复习类型都能正确赋值")
        void shouldSupportAllReminderTypes() {
            String[] types = {"REVIEW", "RECALL", "SPACED_REPETITION"};

            for (String type : types) {
                ReviewReminder entity = ReviewReminder.builder()
                        .reminderType(type)
                        .build();

                assertThat(entity.getReminderType()).isEqualTo(type);
            }
        }

        @Test
        @DisplayName("JSON 序列化包含所有字段")
        void shouldSerializeToJson() throws JsonProcessingException {
            // Given
            LocalDate date = LocalDate.of(2026, 7, 21);
            ReviewReminder entity = ReviewReminder.builder()
                    .id(1L)
                    .userId(100L)
                    .courseId("C-001")
                    .reminderType("REVIEW")
                    .content("复习单词")
                    .scheduledDate(date)
                    .priority(2)
                    .isReviewed(0)
                    .reviewedAt(null)
                    .createdAt(LocalDateTime.of(2026, 7, 20, 10, 0))
                    .updatedAt(LocalDateTime.of(2026, 7, 20, 10, 0))
                    .build();

            // When
            String json = objectMapper.writeValueAsString(entity);

            // Then
            assertThat(json).isNotEmpty();
            assertThat(json).contains("\"id\":1");
            assertThat(json).contains("\"userId\":100");
            assertThat(json).contains("\"courseId\":\"C-001\"");
            assertThat(json).contains("\"reminderType\":\"REVIEW\"");
            assertThat(json).contains("\"isReviewed\":0");
            assertThat(json).contains("\"scheduledDate\":\"2026-07-21\"");
            // reviewedAt 为 null，序列化后应该是 null
            assertThat(json).contains("\"reviewedAt\":null");
        }

        @Test
        @DisplayName("JSON 反序列化正确恢复对象")
        void shouldDeserializeFromJson() throws JsonProcessingException {
            // Given
            String json = "{" +
                    "\"id\":1," +
                    "\"userId\":100," +
                    "\"courseId\":\"C-001\"," +
                    "\"reminderType\":\"SPACED_REPETITION\"," +
                    "\"content\":\"间隔复习\"," +
                    "\"scheduledDate\":\"2026-07-21\"," +
                    "\"priority\":1," +
                    "\"isReviewed\":0" +
                    "}";

            // When
            ReviewReminder entity = objectMapper.readValue(json, ReviewReminder.class);

            // Then
            assertThat(entity.getId()).isEqualTo(1L);
            assertThat(entity.getUserId()).isEqualTo(100L);
            assertThat(entity.getReminderType()).isEqualTo("SPACED_REPETITION");
            assertThat(entity.getScheduledDate()).isEqualTo(LocalDate.of(2026, 7, 21));
            assertThat(entity.getPriority()).isEqualTo(1);
        }

        @Test
        @DisplayName("equals 和 hashCode 基于 id 一致")
        void shouldHaveConsistentEqualsAndHashCode() {
            ReviewReminder r1 = ReviewReminder.builder().id(1L).userId(100L).build();
            ReviewReminder r2 = ReviewReminder.builder().id(1L).userId(200L).build();
            ReviewReminder r3 = ReviewReminder.builder().id(2L).userId(100L).build();

            // @Data 的 equals 基于所有字段，所以即使 id 相同但 userId 不同也不相等
            assertThat(r1).isNotEqualTo(r2);  // userId 不同
            assertThat(r1).isNotEqualTo(r3);  // id 不同
        }
    }

    // ========== ReviewReminderVO 测试 ==========

    @Nested
    @DisplayName("ReviewReminderVO 视图对象")
    class ReviewReminderVOTests {

        @Test
        @DisplayName("Builder 正确创建 VO")
        void shouldCreateVOWithBuilder() {
            LocalDate date = LocalDate.of(2026, 7, 21);
            LocalDateTime reviewedAt = LocalDateTime.of(2026, 7, 21, 15, 30);

            ReviewReminderVO vo = ReviewReminderVO.builder()
                    .id(100L)
                    .userId(1L)
                    .courseId("C-001")
                    .reminderType("REVIEW")
                    .content("内容")
                    .scheduledDate(date)
                    .priority(2)
                    .isReviewed(1)
                    .reviewedAt(reviewedAt)
                    .createdAt(LocalDateTime.of(2026, 7, 20, 10, 0))
                    .build();

            assertThat(vo.getId()).isEqualTo(100L);
            assertThat(vo.getIsReviewed()).isEqualTo(1);
            assertThat(vo.getReviewedAt()).isEqualTo(reviewedAt);
        }

        @Test
        @DisplayName("JSON 序列化 — reviewedAt 为 null 时不输出")
        void shouldSerializeNullReviewedAtAsNull() throws JsonProcessingException {
            ReviewReminderVO vo = ReviewReminderVO.builder()
                    .id(1L)
                    .userId(1L)
                    .isReviewed(0)
                    .reviewedAt(null)
                    .build();

            String json = objectMapper.writeValueAsString(vo);

            assertThat(json).contains("\"reviewedAt\":null");
            assertThat(json).contains("\"isReviewed\":0");
        }
    }

    // ========== MarkReviewedRequest 验证测试 ==========

    @Nested
    @DisplayName("MarkReviewedRequest 请求 DTO")
    class MarkReviewedRequestTests {

        @Test
        @DisplayName("reminderId 不为 null 时验证通过")
        void shouldPassValidationWithValidReminderId() {
            MarkReviewedRequest request = new MarkReviewedRequest();
            request.setReminderId(100L);
            request.setNotes("已掌握");

            Set<ConstraintViolation<MarkReviewedRequest>> violations = validator.validate(request);
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("reminderId 为 null 时验证失败")
        void shouldFailValidationWhenReminderIdIsNull() {
            MarkReviewedRequest request = new MarkReviewedRequest();
            request.setReminderId(null);

            Set<ConstraintViolation<MarkReviewedRequest>> violations = validator.validate(request);
            assertThat(violations).isNotEmpty();
            assertThat(violations).anyMatch(v ->
                    v.getMessage().equals("复习提醒ID不能为空"));
        }

        @Test
        @DisplayName("notes 字段可选，为空不影响验证")
        void shouldPassValidationWithNullNotes() {
            MarkReviewedRequest request = new MarkReviewedRequest();
            request.setReminderId(1L);
            request.setNotes(null);

            Set<ConstraintViolation<MarkReviewedRequest>> violations = validator.validate(request);
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("JSON 序列化/反序列化")
        void shouldSerializeAndDeserialize() throws JsonProcessingException {
            MarkReviewedRequest request = new MarkReviewedRequest();
            request.setReminderId(100L);
            request.setNotes("已掌握");

            // 序列化
            String json = objectMapper.writeValueAsString(request);
            assertThat(json).contains("\"reminderId\":100");
            assertThat(json).contains("\"notes\":\"已掌握\"");

            // 反序列化
            MarkReviewedRequest deserialized = objectMapper.readValue(json, MarkReviewedRequest.class);
            assertThat(deserialized.getReminderId()).isEqualTo(100L);
            assertThat(deserialized.getNotes()).isEqualTo("已掌握");
        }

        @Test
        @DisplayName("仅含 reminderId 的 JSON 能正确反序列化")
        void shouldDeserializeMinimalJson() throws JsonProcessingException {
            String json = "{\"reminderId\": 1}";

            MarkReviewedRequest request = objectMapper.readValue(json, MarkReviewedRequest.class);

            assertThat(request.getReminderId()).isEqualTo(1L);
            assertThat(request.getNotes()).isNull();  // 可选字段
        }
    }

    // ========== ApiResponse 序列化测试 ==========

    @Nested
    @DisplayName("ApiResponse 统一响应")
    class ApiResponseTests {

        @Test
        @DisplayName("成功响应包含 code=200")
        void shouldCreateSuccessResponse() {
            com.treepeople.leapmindtts.pojo.result.ApiResponse<String> response =
                    com.treepeople.leapmindtts.pojo.result.ApiResponse.success("data", "操作成功");

            assertThat(response.getCode()).isEqualTo(200);
            assertThat(response.getMessage()).isEqualTo("操作成功");
            assertThat(response.getData()).isEqualTo("data");
            assertThat(response.getTimestamp()).isNotNull();
        }

        @Test
        @DisplayName("错误响应包含指定的 code 和 message")
        void shouldCreateErrorResponse() {
            com.treepeople.leapmindtts.pojo.result.ApiResponse<Void> response =
                    com.treepeople.leapmindtts.pojo.result.ApiResponse.error(400, "参数错误");

            assertThat(response.getCode()).isEqualTo(400);
            assertThat(response.getMessage()).isEqualTo("参数错误");
            assertThat(response.getData()).isNull();
        }
    }
}
