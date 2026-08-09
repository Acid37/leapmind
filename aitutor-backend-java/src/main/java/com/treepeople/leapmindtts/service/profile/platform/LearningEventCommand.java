package com.treepeople.leapmindtts.service.profile.platform;

import java.time.Instant;
import java.util.regex.Pattern;

/** Stable internal event envelope; payload type derives all schema metadata. */
public record LearningEventCommand(String eventId, Long subjectUserId, Instant occurredAt, String sessionId,
                                   KnowledgePointRef knowledgePoint, String traceId,
                                   LearningEventPayload payload) {
    private static final Pattern ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,63}");
    public LearningEventCommand {
        if (eventId == null || !ID.matcher(eventId).matches() || subjectUserId == null || subjectUserId <= 0
                || occurredAt == null || knowledgePoint == null || payload == null || traceId == null) throw new IllegalArgumentException("event envelope is invalid");
        optionalId(sessionId, "sessionId");
        optionalId(traceId, "traceId");
    }
    public String eventType() { return payload.eventType(); }
    public String sourceModule() { return payload.sourceModule(); }
    public String schemaVersion() { return payload.schemaVersion(); }
    private static void optionalId(String value, String name) { if (value != null && !ID.matcher(value).matches()) throw new IllegalArgumentException(name + " is invalid"); }
}
