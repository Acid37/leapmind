package com.treepeople.leapmindtts.profile;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.treepeople.leapmindtts.config.M6EventJsonCodec;
import com.treepeople.leapmindtts.config.M6StrictJsonFilter;
import com.treepeople.leapmindtts.controller.user.M6ContextController;
import com.treepeople.leapmindtts.exception.UserProfileExceptionHandler;
import com.treepeople.leapmindtts.pojo.dto.profile.M6Dtos.LearningEventRequest;
import com.treepeople.leapmindtts.service.profile.UserEventService;
import com.treepeople.leapmindtts.service.profile.UserProfileQueryService;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = M6StrictJsonFilterMvcTest.MvcConfig.class)
class M6StrictJsonFilterMvcTest {
    @Autowired private M6StrictJsonFilter strictJsonFilter;
    @Autowired private M6ContextController controller;
    @Autowired private UserProfileExceptionHandler advice;
    @Autowired private UserEventService events;
    private MockMvc mvc;

    @BeforeEach void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(advice).addFilters(strictJsonFilter).build();
    }

    @Test void rejectsMalformedJsonBeforeMvcBindingForJsonMediaTypes() throws Exception {
        for (String contentType : new String[]{"application/json", "application/problem+json"}) {
            mvc.perform(post("/api/user-profile/1/record-event").contentType(contentType)
                            .content("{\"eventId\":\"evt-1\",\"eventId\":\"duplicate\"}"))
                    .andExpect(status().isBadRequest()).andExpect(header().exists("X-Request-Id"))
                    .andExpect(jsonPath("$.data.errorCode").value("PROFILE_EVENT_INVALID"));
        }
        verifyNoInteractions(events);
    }

    @Test void delegatesNonJsonAndMissingContentTypeToMvc415Envelope() throws Exception {
        for (String contentType : new String[]{"text/plain", "application/xml", "application/x-www-form-urlencoded", null}) {
            var request = post("/api/user-profile/1/record-event").accept("application/json").content("not json");
            if (contentType != null) request.contentType(contentType);
            mvc.perform(request).andExpect(status().isUnsupportedMediaType()).andExpect(header().exists("X-Request-Id"))
                    .andExpect(jsonPath("$.code").value(415)).andExpect(jsonPath("$.data.errorCode").value("PROFILE_EVENT_INVALID"));
        }
        verifyNoInteractions(events);
    }

    @Test void returns400WhenOccurredAtIsNotATextualRfc3339Value() throws Exception {
        for (String occurredAt : new String[]{"0", "1.2", "true", "null"}) {
            mvc.perform(post("/api/user-profile/1/record-event").contentType("application/json").accept("application/json").content("""
                    {"eventId":"evt-1","userId":1,"eventType":"answer_question","sourceModule":"M1","occurredAt":%s,"schemaVersion":"1.0","data":{"isCorrect":true}}
                    """.formatted(occurredAt)))
                    .andExpect(status().isBadRequest()).andExpect(header().exists("X-Request-Id"))
                    .andExpect(jsonPath("$.data.errorCode").value("PROFILE_EVENT_INVALID"));
        }
        verifyNoInteractions(events);
    }

    @Test void forwardsStrictDecimalNodesToTheEventServiceWithoutDoubleRounding() throws Exception {
        mvc.perform(post("/api/user-profile/1/record-event").contentType("application/json").accept("application/json").content("""
                {"eventId":"evt-precise","userId":1,"eventType":"finish_practice","sourceModule":"M1","occurredAt":"2026-07-20T10:00:00Z","schemaVersion":"1.0","data":{"questionCount":1,"accuracy":1.0000000000000000000000000000000000000001,"durationSec":1}}
                """))
                .andExpect(status().isOk());
        org.mockito.ArgumentCaptor<LearningEventRequest> captured = org.mockito.ArgumentCaptor.forClass(LearningEventRequest.class);
        org.mockito.Mockito.verify(events).record(org.mockito.ArgumentMatchers.eq(1L), captured.capture(), org.mockito.ArgumentMatchers.any());
        org.junit.jupiter.api.Assertions.assertEquals(new java.math.BigDecimal("1.0000000000000000000000000000000000000001"),
                captured.getValue().data().get("accuracy").decimalValue());
    }

    @Configuration
    @EnableWebMvc
    static class MvcConfig {
        @Bean ObjectMapper objectMapper() { return new ObjectMapper().findAndRegisterModules(); }
        @Bean M6EventJsonCodec codec(ObjectMapper objectMapper) { return new M6EventJsonCodec(objectMapper); }
        @Bean M6StrictJsonFilter strictJsonFilter(M6EventJsonCodec codec, ObjectMapper objectMapper) { return new M6StrictJsonFilter(codec, objectMapper); }
        @Bean UserEventService events() { return mock(UserEventService.class); }
        @Bean UserProfileQueryService queries() { return mock(UserProfileQueryService.class); }
        @Bean Validator validator() { return Validation.buildDefaultValidatorFactory().getValidator(); }
        @Bean M6ContextController controller(UserEventService events, UserProfileQueryService queries, M6EventJsonCodec codec, Validator validator) { return new M6ContextController(events, queries, codec, validator); }
        @Bean UserProfileExceptionHandler userProfileExceptionHandler() { return new UserProfileExceptionHandler(); }
    }
}
