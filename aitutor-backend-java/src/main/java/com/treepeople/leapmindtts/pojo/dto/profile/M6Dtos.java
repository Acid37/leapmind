package com.treepeople.leapmindtts.pojo.dto.profile;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.*;
import java.time.OffsetDateTime;
import java.util.List;

public final class M6Dtos {
    private M6Dtos() { }
    public record LearningEventRequest(@NotBlank @Size(max=64) String eventId, @NotNull @Positive Long userId,
        @NotBlank String eventType, @NotBlank String sourceModule, @NotNull OffsetDateTime occurredAt,
        @NotBlank String schemaVersion, @Size(max=64) String sessionId, @Positive Long kpId,
        @Size(max=64) String traceId, @NotNull JsonNode data) { }
    public record BatchEventsRequest(@NotNull @Size(min=1,max=100) List<JsonNode> events) { }
    public record EventResult(Integer index, String eventId, String status, String profileUpdateStatus,
                              String receivedAt, String errorCode, String requestId) { }
    public record EventAck(boolean acknowledged, String eventId, String eventStatus, boolean duplicate,
                           String profileUpdateStatus, String receivedAt, String requestId) { }
    public record ErrorData(String requestId, String errorCode, List<FieldViolation> details) { }
    /** Safe validation metadata only. Never include rejected input values. */
    public record FieldViolation(String field, String reason) { }
}
