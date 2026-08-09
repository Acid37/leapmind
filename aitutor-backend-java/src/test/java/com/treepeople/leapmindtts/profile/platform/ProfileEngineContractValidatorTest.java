package com.treepeople.leapmindtts.profile.platform;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.treepeople.leapmindtts.service.profile.engine.ProfileEngineContractValidator;
import com.treepeople.leapmindtts.service.profile.engine.ProfileEngineRequest;
import com.treepeople.leapmindtts.service.profile.engine.ProfileEngineResponse;
import com.treepeople.leapmindtts.service.profile.engine.StrictProfileEngineJsonCodec;
import com.treepeople.leapmindtts.service.profile.engine.ProfileEngineEvent;
import com.treepeople.leapmindtts.service.profile.platform.KnowledgePointRef;
import com.treepeople.leapmindtts.service.profile.platform.LearningEventCommand;
import com.treepeople.leapmindtts.service.profile.platform.LearningEventPayload;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProfileEngineContractValidatorTest {
    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-07-28T00:00:00Z");
    private static final ProfileEngineRequest REQUEST = new ProfileEngineRequest("1.0", ID, 7L,
            ProfileEngineRequest.Mode.INCREMENTAL, 3L, 0L, 0L, List.of());

    @Test void acceptsAllThreeResponseStatesAndNullableProfileConfidence() {
        assertDoesNotThrow(() -> ProfileEngineContractValidator.validate(REQUEST, ready(null, new BigDecimal("0.12345"))));
        assertDoesNotThrow(() -> ProfileEngineContractValidator.validate(REQUEST, new ProfileEngineResponse.InsufficientData("1.0", ID, 7L, 3L, 4L, 0L, ProfileEngineResponse.EngineStatus.INSUFFICIENT_DATA, "algo-1", NOW, List.of())));
        assertDoesNotThrow(() -> ProfileEngineContractValidator.validate(REQUEST, new ProfileEngineResponse.NoChange("1.0", ID, 7L, 3L, 3L, 0L, ProfileEngineResponse.EngineStatus.NO_CHANGE, "algo-1", NOW)));
    }

    @Test void rejectsVersionAndTargetConflictsAndMasteryPrecisionOverflow() {
        ProfileEngineResponse.Ready wrongVersion = new ProfileEngineResponse.Ready("2.0", ID, 7L, 3L, 4L, 0L, ProfileEngineResponse.EngineStatus.READY, "algo-1", NOW, readyProfile(null, new BigDecimal("0.12345")), List.of());
        ProfileEngineResponse.Ready badMastery = new ProfileEngineResponse.Ready("1.0", ID, 7L, 3L, 4L, 0L, ProfileEngineResponse.EngineStatus.READY, "algo-1", NOW, readyProfile(null, new BigDecimal("0.12345")), List.of(mastery(new BigDecimal("0.12345"))));
        ProfileEngineResponse.Ready badTarget = new ProfileEngineResponse.Ready("1.0", ID, 7L, 3L, 3L, 0L, ProfileEngineResponse.EngineStatus.READY, "algo-1", NOW, readyProfile(null, new BigDecimal("0.12345")), List.of());
        assertThrows(IllegalArgumentException.class, () -> ProfileEngineContractValidator.validate(REQUEST, wrongVersion));
        assertThrows(IllegalArgumentException.class, () -> ProfileEngineContractValidator.validate(REQUEST, badMastery));
        assertThrows(IllegalArgumentException.class, () -> ProfileEngineContractValidator.validate(REQUEST, badTarget));
    }

    @Test void rejectsDuplicateMasteryKnowledgePointsAndInvertedWindows() {
        ProfileEngineResponse.KnowledgeMastery valid = mastery(BigDecimal.ONE);
        ProfileEngineResponse.KnowledgeMastery inverted = new ProfileEngineResponse.KnowledgeMastery(2L, BigDecimal.ONE,
                "WEAK", BigDecimal.ONE, 0L, null, "algo-1", NOW.plusSeconds(1), NOW, NOW);
        ProfileEngineResponse.Ready duplicate = new ProfileEngineResponse.Ready("1.0", ID, 7L, 3L, 4L, 0L,
                ProfileEngineResponse.EngineStatus.READY, "algo-1", NOW, readyProfile(null, BigDecimal.ONE), List.of(valid, valid));
        ProfileEngineResponse.Ready badWindow = new ProfileEngineResponse.Ready("1.0", ID, 7L, 3L, 4L, 0L,
                ProfileEngineResponse.EngineStatus.READY, "algo-1", NOW, readyProfile(null, BigDecimal.ONE), List.of(inverted));
        assertThrows(IllegalArgumentException.class, () -> ProfileEngineContractValidator.validate(REQUEST, duplicate));
        assertThrows(IllegalArgumentException.class, () -> ProfileEngineContractValidator.validate(REQUEST, badWindow));
    }

    @Test void strictCodecRejectsUnknownDuplicateAndMissingRequiredButAcceptsIntegerDecimal() {
        StrictProfileEngineJsonCodec codec = new StrictProfileEngineJsonCodec();
        String valid = "{\"status\":\"NO_CHANGE\",\"contractVersion\":\"1.0\",\"requestId\":\"" + ID + "\",\"userId\":7,\"baseProfileVersion\":3,\"targetProfileVersion\":3,\"eventWatermarkInclusive\":0,\"algorithmVersion\":\"algo-1\",\"evaluatedAt\":\"2026-07-28T00:00:00Z\"}";
        assertDoesNotThrow(() -> codec.readAndValidateResponse(REQUEST, valid.getBytes(StandardCharsets.UTF_8)));
        assertThrows(Exception.class, () -> codec.readAndValidateResponse(REQUEST, (valid.substring(0, valid.length() - 1) + ",\"unknown\":1}").getBytes(StandardCharsets.UTF_8)));
        assertThrows(Exception.class, () -> codec.readAndValidateResponse(REQUEST, valid.replace("\"userId\":7", "\"userId\":7,\"userId\":7").getBytes(StandardCharsets.UTF_8)));
        assertThrows(IllegalArgumentException.class, () -> ProfileEngineContractValidator.validate(REQUEST, new ProfileEngineResponse.NoChange("1.0", ID, 7L, 3L, null, 0L, ProfileEngineResponse.EngineStatus.NO_CHANGE, "algo-1", NOW)));
    }

    @Test void writeRequestRejectsSensitiveTopicAndMaxBaseForIncrementingResponses() {
        LearningEventCommand sensitive = new LearningEventCommand("evt-1", 7L, NOW, null,
                KnowledgePointRef.none(), "trace-1",
                new LearningEventPayload.AskDoubt("12345678901234567X", "concept_unclear", false));
        ProfileEngineRequest sensitiveRequest = new ProfileEngineRequest("1.0", ID, 7L,
                ProfileEngineRequest.Mode.INCREMENTAL, 0L, 0L, 1L,
                List.of(new ProfileEngineEvent(1L, sensitive)));
        assertThrows(RuntimeException.class,
                () -> new StrictProfileEngineJsonCodec().writeRequest(sensitiveRequest));

        ProfileEngineRequest maxBase = new ProfileEngineRequest("1.0", ID, 7L,
                ProfileEngineRequest.Mode.INCREMENTAL, Long.MAX_VALUE, 0L, 0L, List.of());
        ProfileEngineResponse.Ready ready = new ProfileEngineResponse.Ready("1.0", ID, 7L,
                Long.MAX_VALUE, Long.MIN_VALUE, 0L, ProfileEngineResponse.EngineStatus.READY,
                "algo-1", NOW, readyProfile(null, BigDecimal.ONE), List.of());
        ProfileEngineResponse.InsufficientData insufficient = new ProfileEngineResponse.InsufficientData("1.0", ID, 7L,
                Long.MAX_VALUE, Long.MIN_VALUE, 0L, ProfileEngineResponse.EngineStatus.INSUFFICIENT_DATA,
                "algo-1", NOW, List.of());
        assertThrows(IllegalArgumentException.class, () -> ProfileEngineContractValidator.validate(maxBase, ready));
        assertThrows(IllegalArgumentException.class, () -> ProfileEngineContractValidator.validate(maxBase, insufficient));
    }

    private static ProfileEngineResponse.Ready ready(BigDecimal confidence, BigDecimal focusConfidence) {
        return new ProfileEngineResponse.Ready("1.0", ID, 7L, 3L, 4L, 0L, ProfileEngineResponse.EngineStatus.READY, "algo-1", NOW, readyProfile(confidence, focusConfidence), List.of());
    }
    private static ProfileEngineResponse.EngineProfile readyProfile(BigDecimal confidence, BigDecimal focusWeight) {
        return new ProfileEngineResponse.EngineProfile(null, List.of("text"), null, "slow",
                List.of(new ProfileEngineResponse.RecentFocus(1L, focusWeight)),
                List.of(new ProfileEngineResponse.RecentConfusion(1L, "detail", 0L, new BigDecimal("0.12345"), NOW)), null, confidence);
    }
    private static ProfileEngineResponse.KnowledgeMastery mastery(BigDecimal score) {
        return new ProfileEngineResponse.KnowledgeMastery(1L, score, "WEAK", new BigDecimal("0.1000"), 0L, null, "algo-1", null, null, NOW);
    }
}
