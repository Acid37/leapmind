package com.treepeople.leapmindtts.profile;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

class M6ProfileEngineContractSchemaTest {
    @Test void internalHttpPathAndCrossFieldStateRulesAreMachineReadable() throws Exception {
        try (InputStream input = Files.newInputStream(Path.of("docs/m6/profile-engine-contract.yaml"))) {
            Map<?, ?> root = new Yaml().load(input);
            assertEquals("3.0.3", root.get("openapi"));
            assertTrue(((Map<?, ?>) root.get("paths")).containsKey("/api/internal/ai/build-profile"));
            Map<?, ?> rules = (Map<?, ?>) root.get("x-state-rules");
            assertRule(rules, "READY", 1, "required", "allowed");
            assertRule(rules, "INSUFFICIENT_DATA", 1, "forbidden", "empty");
            assertRule(rules, "NO_CHANGE", 0, "forbidden", "forbidden");
            assertEquals(Boolean.FALSE, ((Map<?, ?>) rules.get("NO_CHANGE")).get("evaluatedAtPersisted"));
            Map<?, ?> schemas = (Map<?, ?>) ((Map<?, ?>) root.get("components")).get("schemas");
            Map<?, ?> response = (Map<?, ?>) schemas.get("BuildProfileResponse");
            Map<?, ?> profile = (Map<?, ?>) schemas.get("Profile");
            Map<?, ?> focus = (Map<?, ?>) schemas.get("RecentFocus");
            Map<?, ?> confusion = (Map<?, ?>) schemas.get("RecentConfusion");
            Map<?, ?> responseBase = (Map<?, ?>) schemas.get("ResponseBase");
            Map<?, ?> responseBaseProperties = (Map<?, ?>) responseBase.get("properties");
            Map<?, ?> algorithmVersion = (Map<?, ?>) responseBaseProperties.get("algorithmVersion");
            Map<?, ?> mastery = (Map<?, ?>) schemas.get("KnowledgeMastery");
            Map<?, ?> masteryProperties = (Map<?, ?>) mastery.get("properties");
            Map<?, ?> confusionProperties = (Map<?, ?>) confusion.get("properties");
            Map<?, ?> profileProperties = (Map<?, ?>) profile.get("properties");
            assertAll(
                    () -> assertEquals(Boolean.FALSE, profile.get("additionalProperties")),
                    () -> assertTrue(((java.util.List<?>) profile.get("required")).containsAll(java.util.List.of("preferredContentModes", "recentFocus", "recentConfusions"))),
                    () -> assertEquals(Boolean.FALSE, focus.get("additionalProperties")),
                    () -> assertEquals(java.util.List.of("kpId", "weight"), focus.get("required")),
                    () -> assertEquals(Boolean.FALSE, confusion.get("additionalProperties")),
                    () -> assertEquals(java.util.List.of("kpId", "detail", "evidenceCount", "confidence", "lastOccurredAt"), confusion.get("required")),
                    () -> assertEquals(Map.of("READY", "#/components/schemas/ReadyResponse",
                            "INSUFFICIENT_DATA", "#/components/schemas/InsufficientResponse",
                            "NO_CHANGE", "#/components/schemas/NoChangeResponse"),
                            ((Map<?, ?>) response.get("discriminator")).get("mapping")),
                    () -> assertEquals(1, algorithmVersion.get("minLength")),
                    () -> assertEquals(30, algorithmVersion.get("maxLength")),
                    () -> assertEquals(java.util.List.of("WEAK", "CONSOLIDATING", "BASIC_MASTERY", "MASTERED", "INSUFFICIENT_EVIDENCE"),
                            ((Map<?, ?>) masteryProperties.get("masteryStatus")).get("enum")),
                    () -> assertEquals(java.util.List.of("IMPROVING", "STABLE", "DECLINING"),
                            ((Map<?, ?>) masteryProperties.get("trend")).get("enum")),
                    () -> assertEquals(0.001d, ((Number) ((Map<?, ?>) profileProperties.get("confidence")).get("multipleOf")).doubleValue()),
                    () -> assertEquals(16383, ((Map<?, ?>) profileProperties.get("summaryProfile")).get("maxLength")),
                    () -> assertEquals(0.0001d, ((Number) ((Map<?, ?>) masteryProperties.get("masteryScore")).get("multipleOf")).doubleValue()),
                    () -> assertEquals(0.0001d, ((Number) ((Map<?, ?>) masteryProperties.get("confidence")).get("multipleOf")).doubleValue()),
                    () -> assertEquals(4294967295L, ((Number) ((Map<?, ?>) masteryProperties.get("evidenceCount")).get("maximum")).longValue()),
                    () -> assertEquals("int64", ((Map<?, ?>) masteryProperties.get("evidenceCount")).get("format")),
                    () -> assertEquals(4294967295L, ((Number) ((Map<?, ?>) confusionProperties.get("evidenceCount")).get("maximum")).longValue()),
                    () -> assertTrue(((java.util.List<?>) mastery.get("required")).contains("algorithmVersion")));
        }
    }

    private void assertRule(Map<?, ?> rules,String state,int delta,String profile,String mastery) {
        Map<?, ?> rule=(Map<?, ?>)rules.get(state);
        assertEquals(delta,rule.get("targetVersionDelta"));
        assertEquals(profile,rule.get("profile"));
        assertEquals(mastery,rule.get("knowledgeMastery"));
    }
}
