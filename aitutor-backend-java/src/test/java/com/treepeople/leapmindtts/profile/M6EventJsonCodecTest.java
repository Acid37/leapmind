package com.treepeople.leapmindtts.profile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.treepeople.leapmindtts.config.M6EventJsonCodec;
import com.treepeople.leapmindtts.config.M6StrictJsonFilter;
import com.treepeople.leapmindtts.pojo.dto.profile.M6Dtos.LearningEventRequest;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.springframework.mock.web.*;

class M6EventJsonCodecTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final M6EventJsonCodec codec = new M6EventJsonCodec(mapper);

    @Test void rejectsUnknownFieldsAndStringScalarCoercion() throws Exception {
        assertThrows(Exception.class, () -> codec.read(mapper.readTree("""
          {"eventId":"evt-1","userId":"1001","eventType":"answer_question","sourceModule":"M1","occurredAt":"2026-07-20T10:00:00+08:00","schemaVersion":"1.0","data":{"isCorrect":true},"unknown":1}
          """), LearningEventRequest.class));
    }

    @Test void rejectsFloatingPointUserIdInsteadOfTruncatingIt() throws Exception {
        assertThrows(Exception.class, () -> codec.read(mapper.readTree("""
          {"eventId":"evt-1","userId":1001.9,"eventType":"answer_question","sourceModule":"M1","occurredAt":"2026-07-20T10:00:00+08:00","schemaVersion":"1.0","data":{}}
          """), LearningEventRequest.class));
    }

    @Test void rejectsNonStringScalarsForEveryStringEnvelopeField() throws Exception {
        ObjectNode validEvent = (ObjectNode) mapper.readTree("""
          {"eventId":"evt-1","userId":1001,"eventType":"answer_question","sourceModule":"M1","occurredAt":"2026-07-20T10:00:00+08:00","schemaVersion":"1.0","sessionId":"session-1","traceId":"trace-1","data":{"isCorrect":true}}
          """);
        List<JsonNode> nonStringScalars = List.of(mapper.getNodeFactory().numberNode(123), mapper.getNodeFactory().numberNode(1.0), mapper.getNodeFactory().booleanNode(true));
        for (String field : List.of("eventId", "eventType", "sourceModule", "schemaVersion", "sessionId", "traceId")) {
            for (JsonNode nonStringScalar : nonStringScalars) {
                ObjectNode maliciousEvent = validEvent.deepCopy();
                maliciousEvent.set(field, nonStringScalar);
                assertThrows(Exception.class, () -> codec.read(maliciousEvent, LearningEventRequest.class), field + " must be a JSON string");
            }
        }
    }

    @Test void acceptsTypedEnvelopeWithStringFieldsDateAndObjectData() throws Exception {
        LearningEventRequest event = codec.read(mapper.readTree("""
          {"eventId":"evt-1","userId":1001,"eventType":"answer_question","sourceModule":"M1","occurredAt":"2026-07-20T10:00:00+08:00","schemaVersion":"1.0","sessionId":"session-1","traceId":"trace-1","data":{"isCorrect":true}}
          """), LearningEventRequest.class);
        assertEquals("evt-1", event.eventId());
        assertEquals(1001L, event.userId());
        assertEquals(OffsetDateTime.parse("2026-07-20T10:00:00+08:00").toInstant(), event.occurredAt().toInstant());
        assertTrue(event.data().isObject());
    }

    @Test void requiresOccurredAtToBeARfc3339JsonString() throws Exception {
        for (JsonNode invalidOccurredAt : List.of(mapper.getNodeFactory().numberNode(0),
                mapper.getNodeFactory().numberNode(1.2), mapper.getNodeFactory().booleanNode(true),
                mapper.getNodeFactory().nullNode())) {
            ObjectNode event = (ObjectNode) mapper.readTree("""
              {"eventId":"evt-1","userId":1001,"eventType":"answer_question","sourceModule":"M1","occurredAt":"2026-07-20T10:00:00+08:00","schemaVersion":"1.0","data":{"isCorrect":true}}
              """);
            event.set("occurredAt", invalidOccurredAt);
            assertThrows(Exception.class, () -> codec.read(event, LearningEventRequest.class));
        }
        LearningEventRequest valid = codec.read(mapper.readTree("""
              {"eventId":"evt-1","userId":1001,"eventType":"answer_question","sourceModule":"M1","occurredAt":"2026-07-20T10:00:00Z","schemaVersion":"1.0","data":{"isCorrect":true}}
              """), LearningEventRequest.class);
        assertEquals(OffsetDateTime.parse("2026-07-20T10:00:00Z"), valid.occurredAt());
    }

    @Test void requiresStrictRfc3339SecondsAndFullOffsetBeforeJacksonDeserializes() throws Exception {
        for (String occurredAt : List.of("2026-07-20T10:00Z", "2026-07-20T10:00:00+08", "10000-01-01T00:00:00Z", "+12026-07-20T10:00:00Z")) {
            assertThrows(Exception.class, () -> codec.read(mapper.readTree("""
                    {"eventId":"evt-1","userId":1001,"eventType":"answer_question","sourceModule":"M1","occurredAt":"%s","schemaVersion":"1.0","data":{}}
                    """.formatted(occurredAt)), LearningEventRequest.class));
        }
        LearningEventRequest valid = codec.read(mapper.readTree("""
                {"eventId":"evt-1","userId":1001,"eventType":"answer_question","sourceModule":"M1","occurredAt":"2026-07-20T10:00:00.123+08:00","schemaVersion":"1.0","data":{}}
                """), LearningEventRequest.class);
        assertEquals(OffsetDateTime.parse("2026-07-20T10:00:00.123+08:00").toInstant(), valid.occurredAt().toInstant());
    }

    @Test void requestFilterRejectsDuplicateKeysAndTrailingTokensBeforeMvcBinding() throws Exception {
        M6StrictJsonFilter filter=new M6StrictJsonFilter(codec,mapper);
        for(String body:new String[]{"{\"events\":[],\"events\":[]}","{\"events\":[]} {}"}){
            MockHttpServletRequest request=new MockHttpServletRequest("POST","/api/user-profile/1/batch-events");request.setContentType("application/json");request.setContent(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));MockHttpServletResponse response=new MockHttpServletResponse();
            filter.doFilter(request,response,new MockFilterChain());assertEquals(400,response.getStatus());
        }
    }

    @Test void requestFilterRejectsJsonNullAsAnInvalidM6Payload() throws Exception {
        M6StrictJsonFilter filter = new M6StrictJsonFilter(codec, mapper);
        for (String endpoint : List.of("/api/user-profile/1/record-event", "/api/user-profile/1/batch-events")) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", endpoint);
            request.setContentType("application/json");
            request.setContent("null".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, new MockFilterChain());
            assertEquals(400, response.getStatus());
            assertTrue(response.getContentAsString().contains("PROFILE_EVENT_INVALID"));
        }
    }
}
