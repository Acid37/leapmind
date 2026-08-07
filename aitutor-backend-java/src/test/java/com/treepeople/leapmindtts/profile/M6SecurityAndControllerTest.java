package com.treepeople.leapmindtts.profile;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.treepeople.leapmindtts.config.M6EventJsonCodec;
import com.treepeople.leapmindtts.config.SpringDocConfig;
import com.treepeople.leapmindtts.controller.user.M6ContextController;
import com.treepeople.leapmindtts.exception.M6ApiException;
import com.treepeople.leapmindtts.exception.UserProfileExceptionHandler;
import com.treepeople.leapmindtts.mapper.M6ProfileActorMapper;
import com.treepeople.leapmindtts.pojo.dto.profile.M6ProfileDtos.ExplainingSummary;
import com.treepeople.leapmindtts.pojo.entity.M6ProfileActor;
import com.treepeople.leapmindtts.service.profile.UserEventService;
import com.treepeople.leapmindtts.service.profile.UserProfileQueryService;
import com.treepeople.leapmindtts.service.profile.security.ProfileActorResolver;
import jakarta.validation.Validation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class M6SecurityAndControllerTest {
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    @AfterEach void clearSecurity() { SecurityContextHolder.clearContext(); }

    @Test void actorBindsAuthenticationJwtAttributesAndEnabledDatabaseUser() {
        M6ProfileActorMapper users=mock(M6ProfileActorMapper.class);M6ProfileActor enabled=new M6ProfileActor();enabled.setId(7L);enabled.setUsername("alice");enabled.setStatus(1);when(users.selectActorById(7L)).thenReturn(enabled);
        ProfileActorResolver resolver=new ProfileActorResolver(users);MockHttpServletRequest request=new MockHttpServletRequest();request.setAttribute("userId",7L);request.setAttribute("username","alice");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("alice",null,java.util.List.of()));
        assertEquals(7L,resolver.requireActor(request));
        assertEquals("PROFILE_ACCESS_DENIED",assertThrows(M6ApiException.class,()->resolver.authorizeSelf(request,8L)).getErrorCode());
    }

    @Test void anonymousNeverQueriesUserAndDisabledUserIsForbidden() {
        M6ProfileActorMapper users=mock(M6ProfileActorMapper.class);ProfileActorResolver resolver=new ProfileActorResolver(users);
        assertEquals("PROFILE_UNAUTHENTICATED",assertThrows(M6ApiException.class,()->resolver.requireActor(new MockHttpServletRequest())).getErrorCode());verifyNoInteractions(users);
        M6ProfileActor disabled=new M6ProfileActor();disabled.setUsername("alice");disabled.setStatus(0);when(users.selectActorById(7L)).thenReturn(disabled);MockHttpServletRequest request=new MockHttpServletRequest();request.setAttribute("userId",7L);request.setAttribute("username","alice");SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("alice",null,java.util.List.of()));
        assertEquals("PROFILE_ACCESS_DENIED",assertThrows(M6ApiException.class,()->resolver.requireActor(request)).getErrorCode());
    }

    @Test void actorLookupUsesTheDedicatedMinimalProjectionRatherThanTheFullUserMapper() throws Exception {
        String sql = M6ProfileActorMapper.class.getMethod("selectActorById", Long.class)
                .getAnnotation(org.apache.ibatis.annotations.Select.class).value()[0].toLowerCase(java.util.Locale.ROOT);
        assertEquals("select id, username, status from users where id = #{userid}", sql);
        assertAll(() -> assertFalse(sql.contains("password")), () -> assertFalse(sql.contains("email")),
                () -> assertFalse(sql.contains("phone")), () -> assertFalse(sql.contains("student_name")),
                () -> assertFalse(java.util.Arrays.stream(ProfileActorResolver.class.getDeclaredFields())
                        .anyMatch(field -> field.getType().getSimpleName().equals("UserMapper"))));
    }

    @Test void m6AdviceHandlesBindingAndStrictCodecWithoutAffectingOtherControllers() throws Exception {
        UserEventService events=mock(UserEventService.class);UserProfileQueryService queries=mock(UserProfileQueryService.class);
        M6ContextController controller=new M6ContextController(events,queries,new M6EventJsonCodec(json),Validation.buildDefaultValidatorFactory().getValidator());
        MockMvc mvc=MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new UserProfileExceptionHandler()).build();
        mvc.perform(get("/api/user-profile/1/summary").accept("application/json")).andExpect(status().isBadRequest()).andExpect(header().exists("X-Request-Id")).andExpect(jsonPath("$.data.errorCode").value("PROFILE_EVENT_INVALID"));
        mvc.perform(post("/api/user-profile/1/record-event").contentType("application/json").accept("application/json").content("""
            {"eventId":"evt-1","userId":1.5,"eventType":"answer_question","sourceModule":"M1","occurredAt":"2026-07-20T00:00:00Z","schemaVersion":"1.0","data":{}}
            """)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.data.errorCode").value("PROFILE_EVENT_INVALID"));
        mvc.perform(post("/api/user-profile/1/record-event").contentType("application/json").accept("application/json").content("null"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.data.errorCode").value("PROFILE_EVENT_INVALID"));
        verifyNoInteractions(events);
        RestControllerAdviceScope.assertOnlyProfileController();
    }

    @Test void eventInputErrorsExposeOnlyTheSafeFieldPath() throws Exception {
        UserEventService events = mock(UserEventService.class);
        M6ContextController controller = new M6ContextController(events, mock(UserProfileQueryService.class),
                new M6EventJsonCodec(json), Validation.buildDefaultValidatorFactory().getValidator());
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new UserProfileExceptionHandler()).build();

        mvc.perform(post("/api/user-profile/1/record-event").contentType("application/json").accept("application/json").content("""
                {"eventId":"evt-1","userId":1.5,"eventType":"answer_question","sourceModule":"M1","occurredAt":"2026-07-20T00:00:00Z","schemaVersion":"1.0","data":{"isCorrect":true,"difficulty":3,"timeSpentSec":10,"hintCount":0}}
                """))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.data.errorCode").value("PROFILE_EVENT_INVALID"))
                .andExpect(jsonPath("$.data.details[0].field").value("userId"))
                .andExpect(jsonPath("$.data.details[0].reason").value("INVALID"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("1.5"))));
        verifyNoInteractions(events);
    }

    @Test void maliciousUnknownEnvelopeKeysNeverAppearInResponseDetails() throws Exception {
        UserEventService events = mock(UserEventService.class);
        M6ContextController controller = new M6ContextController(events, mock(UserProfileQueryService.class),
                new M6EventJsonCodec(json), Validation.buildDefaultValidatorFactory().getValidator());
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new UserProfileExceptionHandler()).build();
        String key = "Bearer_secret-should-not-leak";

        mvc.perform(post("/api/user-profile/1/record-event").contentType("application/json").accept("application/json").content("""
                {"eventId":"evt-1","userId":1,"eventType":"answer_question","sourceModule":"M1","occurredAt":"2026-07-20T00:00:00Z","schemaVersion":"1.0","data":{"isCorrect":true,"difficulty":3,"timeSpentSec":10,"hintCount":0},"Bearer_secret-should-not-leak":"value"}
                """))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.data.details[0].field").value("body"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(key))));
        verifyNoInteractions(events);
    }

    @Test void nestedMaliciousDataKeysNeverAppearInHttpErrorDetails() throws Exception {
        UserEventService events = mock(UserEventService.class);
        when(events.record(anyLong(), any(), any())).thenAnswer(invocation -> {
            com.treepeople.leapmindtts.service.profile.validation.LearningEventPolicy.validate(invocation.getArgument(1));
            return null;
        });
        M6ContextController controller = new M6ContextController(events, mock(UserProfileQueryService.class),
                new M6EventJsonCodec(json), Validation.buildDefaultValidatorFactory().getValidator());
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new UserProfileExceptionHandler()).build();
        String key = "Bearer_secret-should-not-leak";

        mvc.perform(post("/api/user-profile/1/record-event").contentType("application/json").accept("application/json").content("""
                {"eventId":"evt-1","userId":1,"eventType":"answer_question","sourceModule":"M1","occurredAt":"2026-07-20T00:00:00Z","schemaVersion":"1.0","data":{"isCorrect":true,"difficulty":3,"timeSpentSec":10,"hintCount":{"Bearer_secret-should-not-leak":{"token":"x"}}}}
                """))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.data.details[0].field").value("data.hintCount"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(key))));
    }

    @Test void batchOfExactlyOneHundredIsDelegatedWhileOneHundredAndOneIsRejected() throws Exception {
        UserEventService events = mock(UserEventService.class);
        when(events.batch(eq(1L), anyList(), any())).thenReturn(java.util.List.of());
        M6ContextController controller = new M6ContextController(events, mock(UserProfileQueryService.class),
                new M6EventJsonCodec(json), Validation.buildDefaultValidatorFactory().getValidator());
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new UserProfileExceptionHandler()).build();
        String oneHundred = "{\"events\":[" + "{}".repeat(100).replace("}{", "},{") + "]}";
        String oneHundredAndOne = "{\"events\":[" + "{}".repeat(101).replace("}{", "},{") + "]}";
        mvc.perform(post("/api/user-profile/1/batch-events").contentType("application/json").accept("application/json").content(oneHundred))
                .andExpect(status().isOk());
        verify(events).batch(eq(1L), argThat(values -> values.size() == 100), any());
        mvc.perform(post("/api/user-profile/1/batch-events").contentType("application/json").accept("application/json").content(oneHundredAndOne))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.data.details[0].field").value("events"));
    }

    @Test void m6AdviceReturnsStable415EnvelopeForUnsupportedMediaType() throws Exception {
        UserEventService events = mock(UserEventService.class);
        M6ContextController controller = new M6ContextController(events, mock(UserProfileQueryService.class),
                new M6EventJsonCodec(json), Validation.buildDefaultValidatorFactory().getValidator());
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new UserProfileExceptionHandler()).build();
        mvc.perform(post("/api/user-profile/1/record-event").contentType("text/plain").accept("application/json").content("not json"))
                .andExpect(status().isUnsupportedMediaType()).andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.code").value(415)).andExpect(jsonPath("$.data.errorCode").value("PROFILE_EVENT_INVALID"));
        verifyNoInteractions(events);
    }

    @Test void photoAliasReturnsRfc9745Header() throws Exception {
        UserProfileQueryService queries=mock(UserProfileQueryService.class);when(queries.summary(eq(1L),eq("photo_qa"),eq(9L),any())).thenReturn(new ExplainingSummary(1L,"explaining","READY",null,1L,java.time.Instant.EPOCH,null,null,java.util.List.of(),java.util.List.of(),null,null));
        M6ContextController controller=new M6ContextController(mock(UserEventService.class),queries,new M6EventJsonCodec(json),Validation.buildDefaultValidatorFactory().getValidator());
        MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new UserProfileExceptionHandler()).build()
                .perform(get("/api/user-profile/1/summary?sceneType=photo_qa&kpId=9"))
                .andExpect(status().isOk()).andExpect(header().string("Deprecation","@1784505600"))
                .andExpect(header().string("Link", "</docs/m6/user-profile-openapi.yaml>; rel=\"deprecation\""));
    }

    @Test void m6SpringDocGroupMakesTheDeprecationLinkResolvable() {
        assertEquals("m6-user-profile", new SpringDocConfig().m6ProfileApi().getGroup());
    }

    @Test void everyProfileUserIdPathRejectsNonPositiveValuesBeforeServices() throws Exception {
        UserEventService events = mock(UserEventService.class);
        UserProfileQueryService queries = mock(UserProfileQueryService.class);
        M6ContextController controller = new M6ContextController(events, queries, new M6EventJsonCodec(json),
                Validation.buildDefaultValidatorFactory().getValidator());
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new UserProfileExceptionHandler()).build();

        mvc.perform(post("/api/user-profile/0/record-event").contentType("application/json").accept("application/json").content("""
                {"eventId":"evt-1","userId":1,"eventType":"answer_question","sourceModule":"M1","occurredAt":"2026-07-20T00:00:00Z","schemaVersion":"1.0","data":{"isCorrect":true,"difficulty":3,"timeSpentSec":10,"hintCount":0}}
                """)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.data.errorCode").value("PROFILE_EVENT_INVALID"));
        mvc.perform(post("/api/user-profile/-1/batch-events").contentType("application/json").accept("application/json").content("{\"events\":[{}]}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.data.errorCode").value("PROFILE_EVENT_INVALID"));
        mvc.perform(get("/api/user-profile/0").accept("application/json")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.data.errorCode").value("PROFILE_EVENT_INVALID"));
        mvc.perform(get("/api/user-profile/0/summary?sceneType=conversation").accept("application/json")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.data.errorCode").value("PROFILE_EVENT_INVALID"));
        mvc.perform(get("/api/user-profile/-1/knowledge-status?kpId=1").accept("application/json")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.data.errorCode").value("PROFILE_EVENT_INVALID"));
        verifyNoInteractions(events, queries);
    }

    private static final class RestControllerAdviceScope {
        static void assertOnlyProfileController(){org.springframework.web.bind.annotation.RestControllerAdvice a=UserProfileExceptionHandler.class.getAnnotation(org.springframework.web.bind.annotation.RestControllerAdvice.class);assertArrayEquals(new Class<?>[]{M6ContextController.class},a.assignableTypes());}
    }
}
