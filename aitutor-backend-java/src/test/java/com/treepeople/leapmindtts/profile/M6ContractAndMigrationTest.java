package com.treepeople.leapmindtts.profile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.treepeople.leapmindtts.config.M6EventJsonCodec;
import com.treepeople.leapmindtts.controller.user.M6ContextController;
import com.treepeople.leapmindtts.exception.M6ApiException;
import com.treepeople.leapmindtts.service.profile.UserEventService;
import com.treepeople.leapmindtts.service.profile.UserProfileQueryService;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.yaml.snakeyaml.Yaml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class M6ContractAndMigrationTest {
    @Test void batchHardLimitIsRejectedBeforeService() throws Exception {
        ObjectMapper json = new ObjectMapper().findAndRegisterModules();
        UserEventService events = mock(UserEventService.class);
        M6ContextController controller = new M6ContextController(events, mock(UserProfileQueryService.class), new M6EventJsonCodec(json), Validation.buildDefaultValidatorFactory().getValidator());
        var body = json.createObjectNode(); var items = body.putArray("events");
        for (int i = 0; i < 101; i++) items.addObject();
        M6ApiException error = assertThrows(M6ApiException.class, () -> controller.batch(1001L, body, new MockHttpServletRequest()));
        assertEquals("PROFILE_EVENT_INVALID", error.getErrorCode());
        verifyNoInteractions(events);
    }

    @Test void migrationsDocumentedContractAndOpenApiStayFrozen() throws Exception {
        String v3 = read("src/main/resources/db/migration/V4__create_m6_user_events.sql");
        String v4 = read("src/main/resources/db/migration/V9__create_m6_user_profiles.sql");
        String v5 = read("src/main/resources/db/migration/V6__create_m6_user_knowledge_mastery.sql");
        assertAll(() -> assertTrue(v3.contains("PRIMARY KEY") && v3.contains("uk_user_events_event_id") && v3.contains("payload_hash_version")),
                () -> assertTrue(v4.contains("uk_user_profiles_user_id") && v4.contains("chk_user_profiles_ready_or_stale") && v4.contains("JSON_TYPE(profile_data_json) = 'OBJECT'") && v4.contains("TRIM(algorithm_version)<>''") && v4.contains("DECIMAL(4,3)")
                        && v4.contains("chk_user_profiles_learning_pace") && v4.contains("chk_user_profiles_summary_profile_length") && v4.contains("CHAR_LENGTH(summary_profile)<=16383")),
                () -> assertTrue(v5.contains("uk_user_knowledge_mastery_user_kp") && v5.contains("idx_user_knowledge_mastery_user_version") && v5.contains("chk_ukm_user") && v5.contains("chk_ukm_kp")),
                () -> assertFalse((v3 + v4 + v5).toUpperCase().contains("FOREIGN KEY")));
        String openApi = read("docs/m6/user-profile-openapi.yaml"), contract = read("docs/m6/profile-engine-contract.yaml");
        Map<?, ?> openApiDocument = new Yaml().load(openApi);
        Map<?, ?> paths = (Map<?, ?>) openApiDocument.get("paths");
        Map<?, ?> components = (Map<?, ?>) openApiDocument.get("components");
        Map<?, ?> securitySchemes = (Map<?, ?>) components.get("securitySchemes");
        Map<?, ?> schemas = (Map<?, ?>) components.get("schemas");
        Map<?, ?> learningEvent = (Map<?, ?>) schemas.get("LearningEvent");
        Map<?, ?> answerData = (Map<?, ?>) schemas.get("AnswerQuestionData");
        Map<?, ?> requestExplanationData = (Map<?, ?>) schemas.get("RequestExplanationData");
        Map<?, ?> askDoubtData = (Map<?, ?>) schemas.get("AskDoubtData");
        Map<?, ?> askDoubtTopic = (Map<?, ?>) ((Map<?, ?>) askDoubtData.get("properties")).get("topic");
        Map<?, ?> batchBody = (Map<?, ?>) ((Map<?, ?>) ((Map<?, ?>) ((Map<?, ?>) ((Map<?, ?>) paths.get("/api/user-profile/{userId}/batch-events")).get("post")).get("requestBody")).get("content")).get("application/json");
        Map<?, ?> batchSchema = (Map<?, ?>) batchBody.get("schema");
        Map<?, ?> profileGet = (Map<?, ?>) ((Map<?, ?>) paths.get("/api/user-profile/{userId}")).get("get");
        Map<?, ?> profileResponse = (Map<?, ?>) ((Map<?, ?>) profileGet.get("responses")).get("200");
        Map<?, ?> summaryGet = (Map<?, ?>) ((Map<?, ?>) paths.get("/api/user-profile/{userId}/summary")).get("get");
        Map<?, ?> knowledgeGet = (Map<?, ?>) ((Map<?, ?>) paths.get("/api/user-profile/{userId}/knowledge-status")).get("get");
        Map<?, ?> knowledgeKpSchema = (Map<?, ?>) ((Map<?, ?>) ((java.util.List<?>) knowledgeGet.get("parameters")).get(1)).get("schema");
        Map<?, ?> eventEnvelope = (Map<?, ?>) schemas.get("EventEnvelope");
        Map<?, ?> eventEnvelopeProperties = (Map<?, ?>) eventEnvelope.get("properties");
        Map<?, ?> explaining = (Map<?, ?>) schemas.get("ExplainingSummary");
        Map<?, ?> lecturing = (Map<?, ?>) schemas.get("LecturingSummary");
        Map<?, ?> conversation = (Map<?, ?>) schemas.get("ConversationSummary");
        Map<?, ?> nullableKnowledgeContext = (Map<?, ?>) schemas.get("NullableKnowledgeContext");
        Map<?, ?> recentConfusion = (Map<?, ?>) schemas.get("RecentConfusion");
        Map<?, ?> knowledgeContext = (Map<?, ?>) schemas.get("KnowledgeContext");
        Map<?, ?> fullProfile = (Map<?, ?>) schemas.get("FullProfile");
        Map<?, ?> lecturingKnowledgeContext = (Map<?, ?>) ((Map<?, ?>) lecturing.get("properties")).get("knowledgeContext");
        Map<?, ?> conversationKnowledgeContext = (Map<?, ?>) ((Map<?, ?>) conversation.get("properties")).get("knowledgeContext");
        Map<?, ?> explainingKnowledgeContext = (Map<?, ?>) ((Map<?, ?>) explaining.get("properties")).get("knowledgeContext");
        Map<?, ?> responses = (Map<?, ?>) components.get("responses");
        String askDoubtTopicPattern = (String) askDoubtTopic.get("pattern");
        String responseSchemas = new Yaml().dump(Map.of("profile", responses.get("ProfileRead"), "summary", responses.get("SummaryRead"), "knowledge", responses.get("KnowledgeStatusRead")));
        assertAll(() -> assertTrue(openApi.contains("openapi: 3.0.3") && openApi.contains("batch-events") && openApi.contains("maxItems: 100")),
                () -> assertEquals(10, ((java.util.List<?>) learningEvent.get("oneOf")).size()),
                () -> assertEquals(Boolean.FALSE, batchSchema.get("additionalProperties")),
                () -> assertTrue(((Map<?, ?>) answerData.get("properties")).containsKey("confusionTag")),
                () -> assertFalse(((Map<?, ?>) answerData.get("properties")).containsKey("confusionCode")),
                () -> assertTrue(((Map<?, ?>) requestExplanationData.get("properties")).containsKey("reasonTag")),
                () -> assertEquals(1, askDoubtTopic.get("minLength")),
                () -> assertFalse(Pattern.compile(askDoubtTopicPattern).matcher("   ").matches()),
                () -> assertTrue(Pattern.compile(askDoubtTopicPattern).matcher("a topic").matches()),
                () -> assertEquals(5, paths.size()),
                () -> assertTrue(paths.values().stream().map(Map.class::cast).map(Map::values).flatMap(java.util.Collection::stream)
                        .map(Map.class::cast).allMatch(operation -> ((Map<?, ?>) operation).get("responses") instanceof Map<?, ?> response
                                && response.containsKey("400"))),
                () -> assertTrue(((Map<?, ?>) ((Map<?, ?>) paths.get("/api/user-profile/{userId}/record-event")).get("post")).containsKey("responses")),
                () -> assertTrue(((Map<?, ?>) ((Map<?, ?>) ((Map<?, ?>) paths.get("/api/user-profile/{userId}/record-event")).get("post")).get("responses")).containsKey("415")),
                () -> assertTrue(((Map<?, ?>) ((Map<?, ?>) ((Map<?, ?>) paths.get("/api/user-profile/{userId}/batch-events")).get("post")).get("responses")).containsKey("415")),
                () -> assertTrue(securitySchemes.containsKey("bearerAuth")),
                () -> assertTrue(openApi.contains("photo_qa") && openApi.contains("ErrorEnvelope")
                        && openApi.contains("explaining and photo_qa require kpId") && openApi.contains("lecturing and conversation accept it optionally")),
                () -> assertEquals("#/components/responses/ProfileRead", profileResponse.get("$ref")),
                () -> assertTrue(((Map<?, ?>) summaryGet.get("responses")).containsKey("503")),
                () -> assertTrue(((Map<?, ?>) knowledgeGet.get("responses")).containsKey("503")),
                () -> assertEquals(Boolean.TRUE, ((Map<?, ?>) eventEnvelopeProperties.get("sessionId")).get("nullable")),
                () -> assertEquals(Boolean.TRUE, ((Map<?, ?>) eventEnvelopeProperties.get("kpId")).get("nullable")),
                () -> assertEquals(Boolean.TRUE, ((Map<?, ?>) eventEnvelopeProperties.get("traceId")).get("nullable")),
                () -> assertTrue(((java.util.List<?>) explaining.get("required")).contains("knowledgeContext")),
                () -> assertEquals(4294967295L, ((Number) ((Map<?, ?>) ((Map<?, ?>) recentConfusion.get("properties")).get("evidenceCount")).get("maximum")).longValue()),
                () -> assertEquals(4294967295L, ((Number) ((Map<?, ?>) ((Map<?, ?>) knowledgeContext.get("properties")).get("evidenceCount")).get("maximum")).longValue()),
                () -> assertEquals(16383, ((Map<?, ?>) ((Map<?, ?>) fullProfile.get("properties")).get("summaryProfile")).get("maxLength")),
                () -> assertEquals(Boolean.TRUE, nullableKnowledgeContext.get("nullable")),
                () -> assertEquals("object", nullableKnowledgeContext.get("type")),
                () -> assertEquals("#/components/schemas/KnowledgeContext", ((Map<?, ?>) ((java.util.List<?>) nullableKnowledgeContext.get("allOf")).get(0)).get("$ref")),
                () -> assertEquals(Map.of("$ref", "#/components/schemas/KnowledgeContext"), explainingKnowledgeContext),
                () -> assertEquals(Map.of("$ref", "#/components/schemas/NullableKnowledgeContext"), lecturingKnowledgeContext),
                () -> assertEquals(Map.of("$ref", "#/components/schemas/NullableKnowledgeContext"), conversationKnowledgeContext),
                () -> assertTrue(responseSchemas.contains("oneOf") && responseSchemas.contains("ExplainingSummary") && responseSchemas.contains("KnowledgeStatusResponse")),
                () -> assertTrue(responseSchemas.contains("Deprecation") && responseSchemas.contains("Link") && responseSchemas.contains("@<unix-seconds>")),
                () -> assertEquals(Boolean.TRUE, knowledgeKpSchema.get("uniqueItems")),
                () -> assertTrue(contract.contains("contractVersion") && contract.contains("/api/internal/ai/build-profile") && contract.contains("INSUFFICIENT_DATA") && contract.contains("NO_CHANGE")));
    }

    @Test void packagedStaticOpenApiIsTheAuthoritativeDeprecationLinkTarget() throws Exception {
        Path staticSpec = Path.of("target/classes/static/docs/m6/user-profile-openapi.yaml");
        assertTrue(Files.exists(staticSpec), "Maven resources must package the M6 OpenAPI document");
        assertEquals(Files.readString(Path.of("docs/m6/user-profile-openapi.yaml")), Files.readString(staticSpec));
    }

    private String read(String relative) throws Exception { return Files.readString(Path.of(relative)); }
}
