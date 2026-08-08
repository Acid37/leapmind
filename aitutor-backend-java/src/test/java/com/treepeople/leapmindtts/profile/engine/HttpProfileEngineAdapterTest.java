package com.treepeople.leapmindtts.profile.engine;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.treepeople.leapmindtts.config.PythonInternalAiProperties;
import com.treepeople.leapmindtts.service.profile.engine.HttpProfileEngineAdapter;
import com.treepeople.leapmindtts.service.profile.engine.ProfileEngineRequest;
import com.treepeople.leapmindtts.service.profile.engine.ProfileEngineResponse;
import com.treepeople.leapmindtts.service.profile.engine.ProfileEngineUnavailableException;
import com.treepeople.leapmindtts.service.profile.engine.ProfileEngineEvent;
import com.treepeople.leapmindtts.service.profile.platform.KnowledgePointRef;
import com.treepeople.leapmindtts.service.profile.platform.LearningEventCommand;
import com.treepeople.leapmindtts.service.profile.platform.LearningEventPayload;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

class HttpProfileEngineAdapterTest {
    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-07-28T00:00:00Z");
    private static final ObjectMapper JSON = new ObjectMapper();

    private MockWebServer server;
    private HttpProfileEngineAdapter adapter;

    @BeforeEach
    void setUp() {
        server = new MockWebServer();
        PythonInternalAiProperties properties = new PythonInternalAiProperties();
        properties.setEnabled(true);
        properties.setBaseUrl(server.url("/").toString());
        properties.setBuildProfilePath("/api/internal/ai/build-profile");
        adapter = new HttpProfileEngineAdapter(WebClient.builder(), properties);
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    private static ProfileEngineRequest request() {
        LearningEventCommand command = new LearningEventCommand("evt-1", 7L, NOW, null,
                KnowledgePointRef.none(), "trace-1",
                new LearningEventPayload.AskDoubt("right_triangle", "concept_unclear", true));
        return new ProfileEngineRequest("1.0", ID, 7L, ProfileEngineRequest.Mode.INCREMENTAL,
                0L, 0L, 1L, List.of(new ProfileEngineEvent(1L, command)));
    }

    @Test void sendsCamelCaseEventsWithTopLevelUserIdRenamedToUserUnderscoreId() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(noChange()));
        adapter.buildProfile(request());

        RecordedRequest recorded = server.takeRequest();
        assertEquals("/api/internal/ai/build-profile", recorded.getPath());
        assertTrue(recorded.getHeader("Content-Type").startsWith("application/json"));
        JsonNode body = JSON.readTree(recorded.getBody().readByteArray());
        assertNull(body.get("userId"));
        assertEquals(7L, body.get("user_id").asLong());
        assertEquals("ask_doubt", body.get("events").get(0).get("eventType").asText());
        assertEquals("right_triangle", body.get("events").get(0).get("data").get("topic").asText());
        assertTrue(body.get("events").get(0).get("data").get("isFollowUp").asBoolean());
        assertEquals("1.0", body.get("contractVersion").asText());
    }

    @Test void mapsReadyResponse() {
        server.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(ready()));
        ProfileEngineResponse response = adapter.buildProfile(request());
        assertInstanceOf(ProfileEngineResponse.Ready.class, response);
        ProfileEngineResponse.Ready ready = (ProfileEngineResponse.Ready) response;
        assertEquals("7", ready.profile().grade());
        assertEquals(List.of("text", "image"), ready.profile().preferredContentModes());
        assertEquals(1, ready.knowledgeMastery().size());
    }

    @Test void mapsInsufficientDataResponse() {
        server.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(insufficient()));
        ProfileEngineResponse response = adapter.buildProfile(request());
        assertInstanceOf(ProfileEngineResponse.InsufficientData.class, response);
    }

    @Test void mapsNoChangeResponse() {
        server.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(noChange()));
        ProfileEngineResponse response = adapter.buildProfile(request());
        assertInstanceOf(ProfileEngineResponse.NoChange.class, response);
    }

    @Test void non2xxMapsToUnreachable() {
        server.enqueue(new MockResponse().setResponseCode(500));
        ProfileEngineUnavailableException error = assertThrows(ProfileEngineUnavailableException.class,
                () -> adapter.buildProfile(request()));
        assertEquals("UNREACHABLE", error.getMessage());
    }

    @Test void nonJsonBodyMapsToInvalidResponse() {
        server.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("not-json"));
        ProfileEngineUnavailableException error = assertThrows(ProfileEngineUnavailableException.class,
                () -> adapter.buildProfile(request()));
        assertEquals("INVALID_RESPONSE", error.getMessage());
    }

    @Test void contractMismatchMapsToInvalidResponse() {
        server.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(noChange().replace(ID.toString(), "00000000-0000-0000-0000-000000000099")));
        ProfileEngineUnavailableException error = assertThrows(ProfileEngineUnavailableException.class,
                () -> adapter.buildProfile(request()));
        assertEquals("INVALID_RESPONSE", error.getMessage());
    }

    @Test void emptyBodyMapsToEmptyResponse() {
        server.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json"));
        ProfileEngineUnavailableException error = assertThrows(ProfileEngineUnavailableException.class,
                () -> adapter.buildProfile(request()));
        assertEquals("EMPTY_RESPONSE", error.getMessage());
    }

    private static String noChange() {
        return "{\"status\":\"NO_CHANGE\",\"contractVersion\":\"1.0\",\"requestId\":\"" + ID
                + "\",\"userId\":7,\"baseProfileVersion\":0,\"targetProfileVersion\":0,"
                + "\"eventWatermarkInclusive\":1,\"algorithmVersion\":\"algo-1\",\"evaluatedAt\":\"2026-07-28T00:00:00Z\"}";
    }

    private static String insufficient() {
        return "{\"status\":\"INSUFFICIENT_DATA\",\"contractVersion\":\"1.0\",\"requestId\":\"" + ID
                + "\",\"userId\":7,\"baseProfileVersion\":0,\"targetProfileVersion\":1,"
                + "\"eventWatermarkInclusive\":1,\"algorithmVersion\":\"algo-1\",\"evaluatedAt\":\"2026-07-28T00:00:00Z\","
                + "\"knowledgeMastery\":[]}";
    }

    private static String ready() {
        return "{\"status\":\"READY\",\"contractVersion\":\"1.0\",\"requestId\":\"" + ID
                + "\",\"userId\":7,\"baseProfileVersion\":0,\"targetProfileVersion\":1,"
                + "\"eventWatermarkInclusive\":1,\"algorithmVersion\":\"algo-1\",\"evaluatedAt\":\"2026-07-28T00:00:00Z\","
                + "\"profile\":{\"grade\":\"7\",\"preferredContentModes\":[\"text\",\"image\"],"
                + "\"preferredExplanationStyle\":\"step_by_step\",\"learningPace\":\"moderate\","
                + "\"recentFocus\":[{\"kpId\":1,\"weight\":0.8}],"
                + "\"recentConfusions\":[{\"kpId\":2,\"detail\":\"formula\",\"evidenceCount\":3,\"confidence\":0.5,\"lastOccurredAt\":\"2026-07-28T00:00:00Z\"}],"
                + "\"summaryProfile\":\"summary\",\"confidence\":0.5},"
                + "\"knowledgeMastery\":[{\"kpId\":1,\"masteryScore\":0.8,\"masteryStatus\":\"BASIC_MASTERY\","
                + "\"confidence\":0.6,\"evidenceCount\":5,\"trend\":\"IMPROVING\",\"algorithmVersion\":\"algo-1\","
                + "\"windowStart\":\"2026-07-27T00:00:00Z\",\"windowEnd\":\"2026-07-28T00:00:00Z\",\"updatedAt\":\"2026-07-28T00:00:00Z\"}]}";
    }
}
