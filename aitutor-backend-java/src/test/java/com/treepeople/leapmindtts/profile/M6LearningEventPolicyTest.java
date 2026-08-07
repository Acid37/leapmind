package com.treepeople.leapmindtts.profile;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.treepeople.leapmindtts.exception.M6ApiException;
import com.treepeople.leapmindtts.pojo.dto.profile.M6Dtos.LearningEventRequest;
import com.treepeople.leapmindtts.service.profile.validation.LearningEventPolicy;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class M6LearningEventPolicyTest {
    private final ObjectMapper json = new ObjectMapper().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);

    @Test void allTenFrozenSchemasAcceptOnlyTheDocumentedCanonicalData() throws Exception {
        List<LearningEventRequest> valid = List.of(
                event("answer_question","M1","{\"isCorrect\":true,\"difficulty\":3,\"timeSpentSec\":10,\"hintCount\":0,\"confusionTag\":\"concept_unclear\"}",null),
                event("finish_practice","M1","{\"questionCount\":10,\"accuracy\":0.8,\"durationSec\":60}",null),
                event("request_explanation","M2","{\"explainId\":\"exp-1\",\"reasonTag\":\"WRONG_ANSWER\"}",null),
                event("explanation_feedback","M2","{\"explainId\":\"exp-1\",\"feedback\":\"understood\",\"repeatCount\":0}",null),
                event("weak_point_changed","M3","{\"oldScore\":0.2,\"newScore\":0.4,\"reason\":\"RECALCULATED\"}",null),
                event("lecture_interact","M4","{\"lectureId\":\"lec-1\",\"chapterId\":\"ch-1\",\"action\":\"pause\"}",null),
                event("lesson_material_used","M5","{\"contentId\":\"content-1\",\"materialType\":\"image\",\"result\":\"helpful\"}",null),
                event("ask_doubt","M7","{\"topic\":\"right_triangle\",\"confusionTag\":\"concept_unclear\",\"isFollowUp\":true}",null),
                event("mark_reviewed","M6","{\"result\":\"correct_without_hint\",\"timeSpentSec\":30,\"hintCount\":0}",9L),
                event("preference_changed","M6","{\"preferenceKey\":\"learning_pace\",\"preferenceValue\":\"fast\"}",null));
        valid.forEach(LearningEventPolicy::validate);
        LearningEventRequest wrongSource = event("ask_doubt","M1","{\"topic\":\"right_triangle\",\"confusionTag\":\"concept_unclear\",\"isFollowUp\":true}",null);
        assertThrows(M6ApiException.class, () -> LearningEventPolicy.validate(wrongSource));
    }

    @Test void rejectsUndocumentedLegacyFieldNamesInsteadOfSilentlyCanonicalizingThem() throws Exception {
        for (LearningEventRequest drift : List.of(
                event("answer_question", "M1", "{\"isCorrect\":true,\"difficulty\":3,\"timeSpentSec\":10,\"hintCount\":0,\"confusionCode\":\"concept_unclear\"}", null),
                event("request_explanation", "M2", "{\"explainId\":\"exp-1\",\"reasonCode\":\"WRONG_ANSWER\"}", null),
                event("weak_point_changed", "M3", "{\"oldScore\":0.2,\"newScore\":0.4,\"reasonCode\":\"RECALCULATED\"}", null),
                event("ask_doubt", "M7", "{\"confusionCode\":\"concept_unclear\",\"isFollowUp\":true}", 9L))) {
            assertEquals("PROFILE_EVENT_INVALID", assertThrows(M6ApiException.class,
                    () -> LearningEventPolicy.validate(drift)).getErrorCode());
        }
    }

    @Test void rejectsMissingWrongTypedNestedAndSensitiveData() throws Exception {
        for (String data : List.of("{}", "{\"isCorrect\":{}}",
                "{\"isCorrect\":true,\"difficulty\":6,\"timeSpentSec\":10,\"hintCount\":0}",
                "{\"isCorrect\":true,\"difficulty\":3,\"timeSpentSec\":10,\"hintCount\":{\"password\":123}}")) {
            assertThrows(M6ApiException.class, () -> LearningEventPolicy.validate(event("answer_question","M1",data,null)));
        }
    }

    @Test void askDoubtTopicMustContainNonWhitespaceText() throws Exception {
        for (String topic : List.of("", "   ", "\t\n")) {
            LearningEventRequest event = event("ask_doubt", "M7", "{\"topic\":"
                    + json.writeValueAsString(topic) + ",\"confusionTag\":\"concept_unclear\",\"isFollowUp\":true}", null);
            M6ApiException error = assertThrows(M6ApiException.class, () -> LearningEventPolicy.validate(event));
            assertEquals("PROFILE_EVENT_INVALID", error.getErrorCode());
        }
    }

    @Test void acceptsOrdinaryTechnicalTermsThatOnlyResembleSensitiveVocabulary() throws Exception {
        for (String topic : List.of("Explain token bucket", "secret sharing", "authorization is a protocol", "a bearer is a person")) {
            LearningEventPolicy.validate(event("ask_doubt", "M7", "{\"topic\":"
                    + json.writeValueAsString(topic) + ",\"confusionTag\":\"concept_unclear\",\"isFollowUp\":true}", null));
        }
        LearningEventPolicy.validate(new LearningEventRequest("token", 1001L, "answer_question", "M1",
                OffsetDateTime.parse("2026-07-20T10:00:00+08:00"), "1.0", "secret", null, "bearer",
                json.readTree("{\"isCorrect\":true,\"difficulty\":3,\"timeSpentSec\":10,\"hintCount\":0}")));
    }

    @Test void rejectsSensitiveKeysAndOnlyStructuredSensitiveValues() throws Exception {
        assertThrows(M6ApiException.class, () -> LearningEventPolicy.validate(event("answer_question", "M1",
                "{\"isCorrect\":true,\"difficulty\":3,\"timeSpentSec\":10,\"hintCount\":0,\"access_token\":\"harmless\"}", null)));
        assertThrows(M6ApiException.class, () -> LearningEventPolicy.validate(event("answer_question", "M1",
                "{\"isCorrect\":true,\"difficulty\":3,\"timeSpentSec\":10,\"hintCount\":0,\"token\":\"harmless\"}", null)));
        for (String topic : List.of(
                "Bearer abcdefghijklmnopqrstuvwxyz0123456789",
                "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.signaturevalue",
                "-----BEGIN PRIVATE KEY-----\\nprivate material",
                "11010519491231002X",
                "aK7pQ2xV9mL4rT8zN1cD6fG3hJ5sW0yB7uE2iO9kP4qR")) {
            LearningEventRequest event = event("ask_doubt", "M7", "{\"topic\":" + json.writeValueAsString(topic)
                    + ",\"confusionTag\":\"concept_unclear\",\"isFollowUp\":true}", null);
            assertEquals("PROFILE_EVENT_INVALID", assertThrows(M6ApiException.class, () -> LearningEventPolicy.validate(event)).getErrorCode());
        }
        LearningEventRequest jwtInEnvelope = new LearningEventRequest("evt-envelope", 1001L, "answer_question", "M1",
                OffsetDateTime.parse("2026-07-20T10:00:00+08:00"), "1.0",
                "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.signaturevalue", null, null,
                json.readTree("{\"isCorrect\":true,\"difficulty\":3,\"timeSpentSec\":10,\"hintCount\":0}"));
        assertThrows(M6ApiException.class, () -> LearningEventPolicy.validate(jwtInEnvelope));
    }

    @Test void policyReportsSafePreciseDataPathsWithoutRejectedValues() throws Exception {
        M6ApiException error = assertThrows(M6ApiException.class, () -> LearningEventPolicy.validate(event("answer_question", "M1",
                "{\"isCorrect\":true,\"difficulty\":3,\"timeSpentSec\":10,\"hintCount\":\"Bearer secret-should-not-leak\"}", null)));
        assertEquals("data.hintCount", error.getDetails().get(0).field());
        assertEquals("INVALID", error.getDetails().get(0).reason());
        assertFalse(error.getDetails().toString().contains("secret-should-not-leak"));
    }

    @Test void policyNeverEchoesAnUnknownOrSensitiveDataKey() throws Exception {
        String key = "Bearer_secret-should-not-leak";
        M6ApiException error = assertThrows(M6ApiException.class, () -> LearningEventPolicy.validate(event("answer_question", "M1",
                "{\"isCorrect\":true,\"difficulty\":3,\"timeSpentSec\":10,\"hintCount\":0,\"Bearer_secret-should-not-leak\":\"value\"}", null)));
        assertEquals("data", error.getDetails().get(0).field());
        assertFalse(error.getDetails().toString().contains(key));
    }

    @Test void nestedMaliciousKeysAndScannerLimitsUseOnlyTrustedDataPaths() throws Exception {
        String key = "Bearer_secret-should-not-leak";
        for (String payload : List.of(
                "{\"isCorrect\":true,\"difficulty\":3,\"timeSpentSec\":10,\"hintCount\":{\"Bearer_secret-should-not-leak\":{\"token\":\"x\"}}}",
                "{\"isCorrect\":true,\"difficulty\":3,\"timeSpentSec\":10,\"hintCount\":{\"x\":{\"x\":{\"x\":{\"x\":{\"x\":{\"x\":{\"x\":{\"x\":{\"x\":0}}}}}}}}}}")) {
            M6ApiException error = assertThrows(M6ApiException.class,
                    () -> LearningEventPolicy.validate(event("answer_question", "M1", payload, null)));
            assertTrue(error.getDetails().get(0).field().equals("data") || error.getDetails().get(0).field().equals("data.hintCount"));
            assertFalse(error.getDetails().toString().contains(key));
        }
    }

    @Test void scannerCountLimitDoesNotEchoNestedKeys() throws Exception {
        StringBuilder payload = new StringBuilder("{\"isCorrect\":true,\"difficulty\":3,\"timeSpentSec\":10,\"hintCount\":{");
        for (int index = 0; index < 260; index++) {
            if (index > 0) payload.append(',');
            payload.append("\"nested-key-").append(index).append("\":0");
        }
        payload.append("}}");
        M6ApiException error = assertThrows(M6ApiException.class,
                () -> LearningEventPolicy.validate(event("answer_question", "M1", payload.toString(), null)));
        assertEquals("data.hintCount", error.getDetails().get(0).field());
        assertFalse(error.getDetails().toString().contains("nested-key-"));
    }

    @Test void rejects36CharacterHighEntropyCredentialsOnlyInDataValues() throws Exception {
        String highEntropy = "Q7m2Vk9zLp4Rt8Nx1Cd6Fg3Hj5Sw0By7UeIo";
        assertEquals(36, highEntropy.length());
        LearningEventRequest credentialInData = event("ask_doubt", "M7", "{\"topic\":"
                + json.writeValueAsString(highEntropy) + ",\"confusionTag\":\"concept_unclear\",\"isFollowUp\":true}", null);
        assertEquals("PROFILE_EVENT_INVALID", assertThrows(M6ApiException.class,
                () -> LearningEventPolicy.validate(credentialInData)).getErrorCode());

        LearningEventPolicy.validate(new LearningEventRequest("550e8400-e29b-41d4-a716-446655440000", 1001L,
                "answer_question", "M1", OffsetDateTime.parse("2026-07-20T10:00:00+08:00"), "1.0",
                "01J1A2B3C4D5E6F7G8H9J0K1M2", null, "4bf92f3577b34da6a3ce929d0e0e4736",
                json.readTree("{\"isCorrect\":true,\"difficulty\":3,\"timeSpentSec\":10,\"hintCount\":0}")));
        LearningEventPolicy.validate(event("ask_doubt", "M7", "{\"topic\":\"Use a SHA-256 checksum in this URL-safe base64 lesson example.\",\"confusionTag\":\"concept_unclear\",\"isFollowUp\":true}", null));
    }

    @Test void rejectsHighEntropyStandardBase64IncludingPlusSlashAndPadding() throws Exception {
        String standardBase64 = "Q7m2Vk9zLp4Rt8Nx1Cd6Fg3Hj5Sw0By7UeIo+/AbCDe";
        assertEquals(43, standardBase64.length());
        for (String credential : List.of(standardBase64, standardBase64 + "=")) {
            LearningEventRequest event = event("ask_doubt", "M7", "{\"topic\":"
                    + json.writeValueAsString(credential) + ",\"confusionTag\":\"concept_unclear\",\"isFollowUp\":true}", null);
            assertEquals("PROFILE_EVENT_INVALID", assertThrows(M6ApiException.class,
                    () -> LearningEventPolicy.validate(event)).getErrorCode());
        }
        LearningEventPolicy.validate(event("ask_doubt", "M7", "{\"topic\":\"Compare UUID 550e8400-e29b-41d4-a716-446655440000 with an ULID in the lesson.\",\"confusionTag\":\"concept_unclear\",\"isFollowUp\":true}", null));
    }

    @Test void measuresHighEntropyBase64CandidatesIncludingTerminalPadding() throws Exception {
        String exactlyThirtyFive = "Q7m2Vk9zLp4Rt8Nx1Cd6Fg3Hj5Sw0By7UeI";
        String exactlyThirtySix = "Q7m2Vk9zLp4Rt8Nx1Cd6Fg3Hj5Sw0By7UeIo";
        String exactlyThirtySeven = "Q7m2Vk9zLp4Rt8Nx1Cd6Fg3Hj5Sw0By7UeIox";
        String thirtyFourPlusPadding = "VwkINM/hDeVutiknOE3ACcE78uz+0OqDDQ==";
        assertEquals(35, exactlyThirtyFive.length());
        assertEquals(36, exactlyThirtySix.length());
        assertEquals(37, exactlyThirtySeven.length());
        assertEquals(36, thirtyFourPlusPadding.length());

        LearningEventPolicy.validate(askDoubt(exactlyThirtyFive));
        for (String credential : List.of(exactlyThirtySix, exactlyThirtySeven, thirtyFourPlusPadding)) {
            assertEquals("PROFILE_EVENT_INVALID", assertThrows(M6ApiException.class,
                    () -> LearningEventPolicy.validate(askDoubt(credential))).getErrorCode());
        }
    }

    @Test void keepsCanonicalIdentifiersAndOrdinaryTechnicalTextOutOfTheEntropyRule() throws Exception {
        for (String value : List.of(
                "550e8400-e29b-41d4-a716-446655440000",
                "01J1A2B3C4D5E6F7G8H9J0K1M2",
                "4bf92f3577b34da6a3ce929d0e0e4736",
                "Use SHA-256 with URL-safe base64 encoding in the trace pipeline.")) {
            LearningEventPolicy.validate(askDoubt(value));
        }
    }

    @Test void checksUnitIntervalWithExactDecimalSemantics() throws Exception {
        LearningEventPolicy.validate(event("finish_practice", "M1", "{\"questionCount\":1,\"accuracy\":0,\"durationSec\":1}", null));
        LearningEventPolicy.validate(event("finish_practice", "M1", "{\"questionCount\":1,\"accuracy\":1,\"durationSec\":1}", null));
        for (String accuracy : List.of("1.0000000000000000000000000000000000000001", "-1e-1000")) {
            LearningEventRequest event = event("finish_practice", "M1", "{\"questionCount\":1,\"accuracy\":"
                    + accuracy + ",\"durationSec\":1}", null);
            assertEquals("PROFILE_EVENT_INVALID", assertThrows(M6ApiException.class,
                    () -> LearningEventPolicy.validate(event)).getErrorCode());
        }
    }

    private LearningEventRequest event(String type,String source,String data,Long kp) throws Exception {
        return new LearningEventRequest("evt-"+type,1001L,type,source,OffsetDateTime.parse("2026-07-20T10:00:00+08:00"),"1.0",null,kp,null,json.readTree(data));
    }
    private LearningEventRequest askDoubt(String topic) throws Exception {
        return event("ask_doubt", "M7", "{\"topic\":" + json.writeValueAsString(topic)
                + ",\"confusionTag\":\"concept_unclear\",\"isFollowUp\":true}", null);
    }
}
