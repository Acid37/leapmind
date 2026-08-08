package com.treepeople.leapmindtts.profile.platform;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;

import com.treepeople.leapmindtts.controller.EventCollectionController;
import com.treepeople.leapmindtts.pojo.entity.EventCollection;
import com.treepeople.leapmindtts.controller.user.UserProfileController;
import com.treepeople.leapmindtts.exception.LegacyProfileSecurityExceptionHandler;
import com.treepeople.leapmindtts.exception.GlobalExceptionHandler;
import com.treepeople.leapmindtts.exception.M6ApiException;
import com.treepeople.leapmindtts.service.EventCollectionService;
import com.treepeople.leapmindtts.service.profile.security.ProfileActorResolver;
import com.treepeople.leapmindtts.service.user.ReviewReminderService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class LegacyRouteAuthorizationTest {
    @Test void userBIsDeniedBeforeUserProfileServiceRuns() throws Exception {
        ReviewReminderService service = mock(ReviewReminderService.class);
        ProfileActorResolver actors = mock(ProfileActorResolver.class);
        doThrow(new M6ApiException(HttpStatus.FORBIDDEN, "PROFILE_ACCESS_DENIED", "denied"))
                .when(actors).authorizeSelf(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(8L));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new UserProfileController(service, actors))
                .setControllerAdvice(new LegacyProfileSecurityExceptionHandler(), new GlobalExceptionHandler()).build();
        mvc.perform(get("/api/user-profile/8/review-history")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test void legacyEventRoutesFailClosedWithoutAConfiguredServiceIdentity() throws Exception {
        EventCollectionService service = mock(EventCollectionService.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new EventCollectionController(service, mock(ProfileActorResolver.class)))
                .setControllerAdvice(new LegacyProfileSecurityExceptionHandler(), new GlobalExceptionHandler()).build();
        mvc.perform(get("/api/events/unprocessed/M1")).andExpect(status().isForbidden());
        mvc.perform(put("/api/events/10/processed")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test void userBIsDeniedBeforeLegacyUserEventReadRuns() throws Exception {
        EventCollectionService service = mock(EventCollectionService.class);
        ProfileActorResolver actors = mock(ProfileActorResolver.class);
        doThrow(new M6ApiException(HttpStatus.FORBIDDEN, "PROFILE_ACCESS_DENIED", "denied"))
                .when(actors).authorizeSelf(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(8L));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new EventCollectionController(service, actors))
                .setControllerAdvice(new LegacyProfileSecurityExceptionHandler(), new GlobalExceptionHandler()).build();
        mvc.perform(get("/api/events/user/8")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test void legacyDatabaseFailureIsUnavailableAndDoesNotLeakDetails() throws Exception {
        ReviewReminderService service = mock(ReviewReminderService.class);
        org.mockito.Mockito.when(service.getReviewReminders(7L)).thenThrow(new DataAccessResourceFailureException("jdbc secret detail"));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new UserProfileController(service, mock(ProfileActorResolver.class)))
                .setControllerAdvice(new LegacyProfileSecurityExceptionHandler(), new GlobalExceptionHandler()).build();
        mvc.perform(get("/api/user-profile/7/review-reminders")).andExpect(status().isServiceUnavailable())
                .andExpect(content().string(not(containsString("jdbc secret detail"))));
    }

    @Test void batchAuthorizesOnceForOneUserAndRejectsMixedOrOversizedBatches() {
        EventCollectionService service = mock(EventCollectionService.class);
        ProfileActorResolver actors = mock(ProfileActorResolver.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        EventCollectionController controller = new EventCollectionController(service, actors);
        EventCollection one = EventCollection.builder().userId(7L).module("M1").eventType("X").build();
        EventCollection two = EventCollection.builder().userId(7L).module("M1").eventType("Y").build();
        org.mockito.Mockito.when(service.collectEvents(List.of(one, two))).thenReturn(List.of(one, two));
        controller.collectEvents(List.of(one, two), request);
        verify(actors, times(1)).authorizeSelf(request, 7L);
        verify(service).collectEvents(List.of(one, two));

        EventCollection mixed = EventCollection.builder().userId(8L).module("M1").eventType("Z").build();
        EventCollectionService mixedService = mock(EventCollectionService.class);
        ProfileActorResolver mixedActors = mock(ProfileActorResolver.class);
        assertThrows(IllegalArgumentException.class, () -> new EventCollectionController(mixedService, mixedActors).collectEvents(List.of(one, mixed), request));
        verify(mixedActors, times(1)).authorizeSelf(request, 7L);
        verifyNoInteractions(mixedService);

        EventCollectionService largeService = mock(EventCollectionService.class);
        ProfileActorResolver largeActors = mock(ProfileActorResolver.class);
        List<EventCollection> large = java.util.stream.IntStream.range(0, 101).mapToObj(i -> one).toList();
        assertThrows(IllegalArgumentException.class, () -> new EventCollectionController(largeService, largeActors).collectEvents(large, request));
        verifyNoInteractions(largeActors, largeService);
    }
}
