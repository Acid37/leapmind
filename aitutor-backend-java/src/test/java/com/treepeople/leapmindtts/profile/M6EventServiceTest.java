package com.treepeople.leapmindtts.profile;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.treepeople.leapmindtts.config.M6EventJsonCodec;
import com.treepeople.leapmindtts.exception.M6ApiException;
import com.treepeople.leapmindtts.pojo.dto.profile.M6Dtos.EventAck;
import com.treepeople.leapmindtts.pojo.dto.profile.M6Dtos.EventResult;
import com.treepeople.leapmindtts.pojo.dto.profile.M6Dtos.LearningEventRequest;
import com.treepeople.leapmindtts.pojo.entity.UserEvent;
import com.treepeople.leapmindtts.service.profile.impl.CommittedEventReader;
import com.treepeople.leapmindtts.service.profile.impl.EventInsertTransaction;
import com.treepeople.leapmindtts.service.profile.impl.UserEventServiceImpl;
import com.treepeople.leapmindtts.service.profile.security.ProfileActorResolver;
import com.treepeople.leapmindtts.service.profile.validation.DuplicateConstraintClassifier;
import java.sql.SQLException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.List;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.mock.web.MockHttpServletRequest;

class M6EventServiceTest {
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    @Test void acceptsOneAndCanonicalizesOnlyData() throws Exception {
        EventInsertTransaction writer = mock(EventInsertTransaction.class);
        UserEventServiceImpl service = service(writer, mock(CommittedEventReader.class));
        LearningEventRequest event = event("evt-1", "answer_question", validAnswer(true));
        EventAck ack = service.record(1001L, event, request());
        ArgumentCaptor<UserEvent> saved = ArgumentCaptor.forClass(UserEvent.class);
        verify(writer).insert(saved.capture());
        assertTrue(saved.getValue().getEventDataJson().contains("\"isCorrect\":true"));
        assertEquals(1, saved.getValue().getPayloadHashVersion());
        assertFalse(ack.duplicate());
    }

    @Test void duplicateIsAcknowledgedButDifferentPayloadConflicts() throws Exception {
        EventInsertTransaction firstWriter = mock(EventInsertTransaction.class);
        UserEventServiceImpl firstService = service(firstWriter, mock(CommittedEventReader.class));
        firstService.record(1001L, event("evt-2a", "answer_question", validAnswer(true)), request());
        ArgumentCaptor<UserEvent> first = ArgumentCaptor.forClass(UserEvent.class);
        verify(firstWriter).insert(first.capture());
        EventInsertTransaction duplicateWriter = mock(EventInsertTransaction.class);
        CommittedEventReader duplicateReader = mock(CommittedEventReader.class);
        doThrow(duplicate()).when(duplicateWriter).insert(any());
        when(duplicateReader.read("evt-2a")).thenReturn(UserEvent.builder().payloadHash(first.getValue().getPayloadHash()).processStatus("PENDING").receivedAt(java.time.LocalDateTime.of(2026,7,20,2,0)).build());
        EventAck duplicateAck=service(duplicateWriter, duplicateReader).record(1001L, event("evt-2a", "answer_question", validAnswer(true)), request());
        assertTrue(duplicateAck.duplicate());
        assertEquals("2026-07-20T02:00:00Z",duplicateAck.receivedAt());
        assertEquals("PENDING",duplicateAck.profileUpdateStatus());
        EventInsertTransaction writer = mock(EventInsertTransaction.class);
        CommittedEventReader reader = mock(CommittedEventReader.class);
        doThrow(duplicate()).when(writer).insert(any());
        UserEvent same = UserEvent.builder().payloadHash("not-the-hash").build();
        when(reader.read("evt-2")).thenReturn(same);
        M6ApiException conflict = assertThrows(M6ApiException.class, () -> service(writer, reader).record(1001L, event("evt-2", "answer_question", validAnswer(true)), request()));
        assertEquals("PROFILE_IDEMPOTENCY_CONFLICT", conflict.getErrorCode());
    }

    @Test void concurrentIdenticalEventsProduceOneInsertAndOneDuplicateAck() throws Exception {
        ConcurrentHashMap<String, UserEvent> stored = new ConcurrentHashMap<>();
        EventInsertTransaction writer = new EventInsertTransaction(null) {
            @Override public void insert(UserEvent value) {
                if (stored.putIfAbsent(value.getEventId(), value) != null) throw M6EventServiceTest.this.duplicate();
            }
        };
        CommittedEventReader reader = new CommittedEventReader(null) {
            @Override public UserEvent read(String id) { return stored.get(id); }
        };
        UserEventServiceImpl service = service(writer, reader);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var one = pool.submit(() -> service.record(1001L, event("evt-concurrent", "answer_question", validAnswer(true)), request()));
            var two = pool.submit(() -> service.record(1001L, event("evt-concurrent", "answer_question", validAnswer(true)), request()));
            List<EventAck> acks = List.of(one.get(), two.get());
            assertEquals(1, acks.stream().filter(EventAck::duplicate).count());
            assertEquals(1, stored.size());
        } finally { pool.shutdownNow(); }
    }

    @Test void batchKeepsOrderAndContinuesAfterBadItem() throws Exception {
        EventInsertTransaction writer = mock(EventInsertTransaction.class);
        UserEventServiceImpl service = service(writer, mock(CommittedEventReader.class));
        List<EventResult> result = service.batch(1001L, List.of(
                json.readTree(eventJson("evt-3", "answer_question", validAnswer(true))),
                json.readTree(eventJson("evt-4", "unknown", "{}")),
                json.readTree(eventJson("evt-5", "answer_question", validAnswer(false)))), request());
        assertEquals(List.of("ACCEPTED", "FAILED", "ACCEPTED"), result.stream().map(EventResult::status).toList());
        assertEquals("PROFILE_EVENT_TYPE_UNSUPPORTED", result.get(1).errorCode());
        verify(writer, times(2)).insert(any());
    }

    @Test void batchProcessesExactlyOneHundredEventsWithoutAServiceLevelSizeRejection() throws Exception {
        EventInsertTransaction writer = mock(EventInsertTransaction.class);
        UserEventServiceImpl service = service(writer, mock(CommittedEventReader.class));
        List<com.fasterxml.jackson.databind.JsonNode> items = java.util.stream.IntStream.range(0, 100)
                .mapToObj(index -> {
                    try {
                        return json.readTree(eventJson("evt-100-" + index, "answer_question", validAnswer(index % 2 == 0)));
                    } catch (Exception e) {
                        throw new AssertionError(e);
                    }
                }).toList();
        List<EventResult> results = service.batch(1001L, items, request());
        assertEquals(100, results.size());
        assertTrue(results.stream().allMatch(result -> "ACCEPTED".equals(result.status())));
        assertEquals(java.util.List.of(0, 99), java.util.List.of(results.get(0).index(), results.get(99).index()));
        verify(writer, times(100)).insert(any());
    }

    @Test void nullSingleAndBatchElementAreReportedAsProfileEventInvalid() throws Exception {
        EventInsertTransaction writer = mock(EventInsertTransaction.class);
        UserEventServiceImpl service = service(writer, mock(CommittedEventReader.class));
        M6ApiException single = assertThrows(M6ApiException.class,
                () -> service.record(1001L, null, request()));
        assertEquals("PROFILE_EVENT_INVALID", single.getErrorCode());

        List<EventResult> batch = service.batch(1001L, List.of(
                com.fasterxml.jackson.databind.node.NullNode.getInstance(),
                json.readTree(eventJson("evt-after-null", "answer_question", validAnswer(true)))), request());
        assertEquals("FAILED", batch.get(0).status());
        assertEquals("PROFILE_EVENT_INVALID", batch.get(0).errorCode());
        assertEquals("ACCEPTED", batch.get(1).status());
        verify(writer).insert(any());
    }

    @Test void rejectsActorMismatchSchemaAndSensitiveData() throws Exception {
        UserEventServiceImpl service = service(mock(EventInsertTransaction.class), mock(CommittedEventReader.class));
        LearningEventRequest wrongActor = new LearningEventRequest("evt-6",1002L,"answer_question","M1",java.time.OffsetDateTime.parse("2026-07-20T10:00:00+08:00"),"1.0",null,null,null,json.readTree(validAnswer(true)));
        M6ApiException actor = assertThrows(M6ApiException.class, () -> service.record(1001L, wrongActor, request()));
        assertEquals("PROFILE_ACCESS_DENIED", actor.getErrorCode());
        assertEquals("PROFILE_EVENT_VERSION_UNSUPPORTED", assertThrows(M6ApiException.class, () -> service.record(1001L, new LearningEventRequest("evt-7",1001L,"answer_question","M1",java.time.OffsetDateTime.parse("2026-07-20T10:00:00+08:00"),"2.0",null,null,null,json.readTree("{\"isCorrect\":true}")), request())).getErrorCode());
        assertEquals("PROFILE_EVENT_INVALID", assertThrows(M6ApiException.class, () -> service.record(1001L, event("evt-8", "answer_question", "{\"isCorrect\":true,\"hintCount\":\"Bearer secret\"}"), request())).getErrorCode());
    }

    @Test void blankAskDoubtTopicIsRejectedBeforePersisting() throws Exception {
        EventInsertTransaction writer = mock(EventInsertTransaction.class);
        LearningEventRequest event = new LearningEventRequest("evt-blank-topic", 1001L, "ask_doubt", "M7",
                java.time.OffsetDateTime.parse("2026-07-20T10:00:00+08:00"), "1.0", null, null, null,
                json.readTree("{\"topic\":\"   \",\"confusionTag\":\"concept_unclear\",\"isFollowUp\":false}"));
        M6ApiException error = assertThrows(M6ApiException.class,
                () -> service(writer, mock(CommittedEventReader.class)).record(1001L, event, request()));
        assertEquals("PROFILE_EVENT_INVALID", error.getErrorCode());
        verifyNoInteractions(writer);
    }

    @Test void batchDatabaseFailureIs503PathAndDoesNotContinueAsInvalidItems() throws Exception {
        EventInsertTransaction writer=mock(EventInsertTransaction.class);doThrow(new org.springframework.dao.DataAccessResourceFailureException("down")).when(writer).insert(any());
        UserEventServiceImpl service=service(writer,mock(CommittedEventReader.class));
        assertThrows(org.springframework.dao.DataAccessException.class,()->service.batch(1001L,List.of(json.readTree(eventJson("evt-db","answer_question",validAnswer(true))),json.readTree(eventJson("evt-never","answer_question",validAnswer(true)))),request()));
        verify(writer,times(1)).insert(any());
    }

    @Test void duplicateClassifierRequiresVendor1062AndExactConstraintName() {
        assertTrue(DuplicateConstraintClassifier.isEventId(duplicate()));
        assertFalse(DuplicateConstraintClassifier.isEventId(new DuplicateKeyException("x",new SQLException("Duplicate entry for key 'uk_user_events_event_id_suffix'","23000",1062))));
        assertFalse(DuplicateConstraintClassifier.isEventId(new DuplicateKeyException("x",new SQLException("Duplicate entry for key 'uk_user_events_event_id'","23000",999))));
    }

    private UserEventServiceImpl service(EventInsertTransaction writer, CommittedEventReader reader) {
        ProfileActorResolver actors = mock(ProfileActorResolver.class);
        return new UserEventServiceImpl(new M6EventJsonCodec(json), Validation.buildDefaultValidatorFactory().getValidator(), actors, writer, reader,
                Clock.fixed(Instant.parse("2026-07-20T02:00:00Z"), ZoneOffset.UTC));
    }
    private LearningEventRequest event(String id, String type, String data) throws Exception { return new LearningEventRequest(id,1001L,type,"M1",java.time.OffsetDateTime.parse("2026-07-20T10:00:00+08:00"),"1.0",null,null,null,json.readTree(data)); }
    private String eventJson(String id,String type,String data) { return "{\"eventId\":\""+id+"\",\"userId\":1001,\"eventType\":\""+type+"\",\"sourceModule\":\"M1\",\"occurredAt\":\"2026-07-20T10:00:00+08:00\",\"schemaVersion\":\"1.0\",\"data\":"+data+"}"; }
    private String validAnswer(boolean correct) { return "{\"isCorrect\":"+correct+",\"difficulty\":3,\"timeSpentSec\":10,\"hintCount\":0}"; }
    private MockHttpServletRequest request() { return new MockHttpServletRequest(); }
    private DuplicateKeyException duplicate() { return new DuplicateKeyException("duplicate", new SQLException("Duplicate entry 'x' for key 'uk_user_events_event_id'", "23000", 1062)); }
}
