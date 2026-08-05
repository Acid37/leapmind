package com.treepeople.leapmindtts.profile;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.treepeople.leapmindtts.config.M6EventJsonCodec;
import com.treepeople.leapmindtts.exception.M6ApiException;
import com.treepeople.leapmindtts.pojo.dto.profile.M6Dtos.LearningEventRequest;
import com.treepeople.leapmindtts.pojo.entity.UserEvent;
import com.treepeople.leapmindtts.service.profile.impl.CommittedEventReader;
import com.treepeople.leapmindtts.service.profile.impl.EventInsertTransaction;
import com.treepeople.leapmindtts.service.profile.impl.UserEventServiceImpl;
import com.treepeople.leapmindtts.service.profile.security.ProfileActorResolver;
import jakarta.validation.Validation;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import java.util.List;

class M6OccurredAtQuarantineTest {
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    @Test void exactTwentyFourHourBoundaryIsPendingAndOneMillisecondOutsideIsQuarantined() throws Exception {
        EventInsertTransaction writer = mock(EventInsertTransaction.class);
        UserEventServiceImpl service = service(writer, Clock.fixed(Instant.parse("2026-07-20T00:00:00Z"), ZoneOffset.UTC));
        service.record(1L, event("edge", "2026-07-21T00:00:00Z"), new MockHttpServletRequest());
        service.record(1L, event("outside", "2026-07-21T00:00:00.001Z"), new MockHttpServletRequest());
        ArgumentCaptor<UserEvent> values = ArgumentCaptor.forClass(UserEvent.class);
        verify(writer, times(2)).insert(values.capture());
        assertEquals("PENDING", values.getAllValues().get(0).getProcessStatus());
        assertEquals("QUARANTINED", values.getAllValues().get(1).getProcessStatus());
    }

    @Test void batchCapturesClockExactlyOnceForEveryItem() throws Exception {
        Clock clock=mock(Clock.class);when(clock.instant()).thenReturn(Instant.parse("2026-07-20T00:00:00Z"));EventInsertTransaction writer=mock(EventInsertTransaction.class);UserEventServiceImpl service=service(writer,clock);
        service.batch(1L,List.of(node("one","2026-07-20T00:00:00Z"),node("two","2026-07-20T00:00:01Z")),new MockHttpServletRequest());
        verify(clock,times(1)).instant();ArgumentCaptor<UserEvent> values=ArgumentCaptor.forClass(UserEvent.class);verify(writer,times(2)).insert(values.capture());assertEquals(values.getAllValues().get(0).getReceivedAt(),values.getAllValues().get(1).getReceivedAt());
    }

    @Test void rejectsOccurredAtOutsideMysqlDatetimeRangeAfterUtcConversionBeforeWriting() throws Exception {
        EventInsertTransaction writer = mock(EventInsertTransaction.class);
        UserEventServiceImpl service = service(writer, Clock.fixed(Instant.parse("2026-07-20T00:00:00Z"), ZoneOffset.UTC));
        M6ApiException lower = assertThrows(M6ApiException.class, () -> service.record(1L,
                event("before-min", "1000-01-01T00:00:00+01:00"), new MockHttpServletRequest()));
        M6ApiException upper = assertThrows(M6ApiException.class, () -> service.record(1L,
                event("after-max", "9999-12-31T23:59:59.999-00:01"), new MockHttpServletRequest()));
        assertAll(() -> assertEquals("PROFILE_EVENT_INVALID", lower.getErrorCode()),
                () -> assertEquals("PROFILE_EVENT_INVALID", upper.getErrorCode()),
                () -> verifyNoInteractions(writer));
    }

    @Test void acceptsMysqlDatetimeUtcBoundaries() throws Exception {
        EventInsertTransaction writer = mock(EventInsertTransaction.class);
        UserEventServiceImpl service = service(writer, Clock.fixed(Instant.parse("2026-07-20T00:00:00Z"), ZoneOffset.UTC));
        service.record(1L, event("min", "1000-01-01T00:00:00Z"), new MockHttpServletRequest());
        service.record(1L, event("max", "9999-12-31T23:59:59.999Z"), new MockHttpServletRequest());
        ArgumentCaptor<UserEvent> values = ArgumentCaptor.forClass(UserEvent.class);
        verify(writer, times(2)).insert(values.capture());
        assertEquals(java.time.LocalDateTime.of(1000, 1, 1, 0, 0), values.getAllValues().get(0).getOccurredAt());
        assertEquals(java.time.LocalDateTime.of(9999, 12, 31, 23, 59, 59, 999_000_000), values.getAllValues().get(1).getOccurredAt());
    }

    @Test void evaluatesMysqlDatetimeBoundsAfterUtcNormalizationInsteadOfLocalYear() throws Exception {
        EventInsertTransaction writer = mock(EventInsertTransaction.class);
        UserEventServiceImpl service = service(writer, Clock.fixed(Instant.parse("2026-07-20T00:00:00Z"), ZoneOffset.UTC));

        service.record(1L, event("lower-offset", "0999-12-31T23:00:00-01:00"), new MockHttpServletRequest());
        M6ApiException aboveMaximum = assertThrows(M6ApiException.class, () -> service.record(1L,
                event("upper-offset", "9999-12-31T23:59:59.999-00:01"), new MockHttpServletRequest()));
        LearningEventRequest directFiveDigitYear = new LearningEventRequest("evt-direct-five-digit", 1L,
                "answer_question", "M1", OffsetDateTime.of(10000, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC), "1.0",
                null, null, null, json.readTree("{\"isCorrect\":true,\"difficulty\":3,\"timeSpentSec\":1,\"hintCount\":0}"));
        M6ApiException direct = assertThrows(M6ApiException.class,
                () -> service.record(1L, directFiveDigitYear, new MockHttpServletRequest()));

        ArgumentCaptor<UserEvent> saved = ArgumentCaptor.forClass(UserEvent.class);
        verify(writer).insert(saved.capture());
        assertAll(() -> assertEquals(java.time.LocalDateTime.of(1000, 1, 1, 0, 0), saved.getValue().getOccurredAt()),
                () -> assertEquals("PROFILE_EVENT_INVALID", aboveMaximum.getErrorCode()),
                () -> assertEquals("PROFILE_EVENT_INVALID", direct.getErrorCode()));
    }

    @Test void rejectsExtremeDirectServiceTimestampsBeforeHashingOrWriting() throws Exception {
        EventInsertTransaction writer = mock(EventInsertTransaction.class);
        UserEventServiceImpl service = service(writer, Clock.fixed(Instant.parse("2026-07-20T00:00:00Z"), ZoneOffset.UTC));
        for (OffsetDateTime occurredAt : List.of(OffsetDateTime.MIN, OffsetDateTime.MAX)) {
            LearningEventRequest event = new LearningEventRequest("evt-extreme-" + occurredAt.getYear(), 1L,
                    "answer_question", "M1", occurredAt, "1.0", null, null, null,
                    json.readTree("{\"isCorrect\":true,\"difficulty\":3,\"timeSpentSec\":1,\"hintCount\":0}"));
            assertEquals("PROFILE_EVENT_INVALID", assertThrows(M6ApiException.class,
                    () -> service.record(1L, event, new MockHttpServletRequest())).getErrorCode());
        }
        verifyNoInteractions(writer);
    }

    private UserEventServiceImpl service(EventInsertTransaction writer, Clock clock) {
        return new UserEventServiceImpl(new M6EventJsonCodec(json), Validation.buildDefaultValidatorFactory().getValidator(),
                mock(ProfileActorResolver.class), writer, mock(CommittedEventReader.class), clock);
    }
    private LearningEventRequest event(String id,String at) throws Exception {
        return new LearningEventRequest("evt-"+id,1L,"answer_question","M1",OffsetDateTime.parse(at),"1.0",null,null,null,
                json.readTree("{\"isCorrect\":true,\"difficulty\":3,\"timeSpentSec\":1,\"hintCount\":0}"));
    }
    private com.fasterxml.jackson.databind.JsonNode node(String id, String occurredAt) throws Exception {
        return json.readTree("""
                {"eventId":"evt-%s","userId":1,"eventType":"answer_question","sourceModule":"M1","occurredAt":"%s","schemaVersion":"1.0","data":{"isCorrect":true,"difficulty":3,"timeSpentSec":1,"hintCount":0}}
                """.formatted(id, occurredAt));
    }
}
