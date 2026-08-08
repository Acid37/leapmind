package com.treepeople.leapmindtts.service.profile.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.treepeople.leapmindtts.pojo.dto.profile.M6Dtos.LearningEventRequest;
import com.treepeople.leapmindtts.service.profile.validation.LearningEventPolicy;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/** Bridges the stable internal command to the existing, audited M6 event validator. */
public final class LearningEventCommandValidator {
    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);
    private LearningEventCommandValidator() { }

    public static void validate(LearningEventCommand command) {
        LearningEventPolicy.validate(toRequest(command));
    }

    /** Exact v1 DTO bridge used by both the internal publisher and the HTTP ingestion core. */
    public static LearningEventRequest toRequest(LearningEventCommand command) {
        if (command == null) throw new IllegalArgumentException("learning event command is required");
        Long kpId = command.knowledgePoint() instanceof KnowledgePointRef.Resolved resolved ? resolved.kpId() : null;
        return new LearningEventRequest(command.eventId(), command.subjectUserId(),
                command.eventType(), command.sourceModule(), OffsetDateTime.ofInstant(command.occurredAt(), ZoneOffset.UTC),
                command.schemaVersion(), command.sessionId(), kpId, command.traceId(), MAPPER.valueToTree(command.payload()));
    }
}
