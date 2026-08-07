package com.treepeople.leapmindtts.service.profile.engine;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.treepeople.leapmindtts.service.profile.platform.KnowledgePointRef;
import com.treepeople.leapmindtts.service.profile.platform.LearningEventPayload;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Exact v1.0 engine wire request, kept separate from the internal event command model. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProfileEngineWireRequest(String contractVersion, UUID requestId, Long userId,
                                       ProfileEngineRequest.Mode mode, Long baseProfileVersion,
                                       Long fromEventIdExclusive, Long eventWatermarkInclusive,
                                       List<ProfileEngineWireEvent> events) {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ProfileEngineWireEvent(long dbEventId, String eventId, String eventType,
                                         String sourceModule, Instant occurredAt, String schemaVersion,
                                         String sessionId, Long kpId, String traceId,
                                         LearningEventPayload data) { }

    static ProfileEngineWireRequest from(ProfileEngineRequest request) {
        List<ProfileEngineWireEvent> events = request.events().stream().map(ProfileEngineWireRequest::from).toList();
        return new ProfileEngineWireRequest(request.contractVersion(), request.requestId(), request.userId(), request.mode(),
                request.baseProfileVersion(), request.fromEventIdExclusive(), request.eventWatermarkInclusive(), events);
    }

    private static ProfileEngineWireEvent from(ProfileEngineEvent event) {
        var command = event.command();
        Long kpId = command.knowledgePoint() instanceof KnowledgePointRef.Resolved resolved ? resolved.kpId() : null;
        return new ProfileEngineWireEvent(event.dbEventId(), command.eventId(), event.eventType(), event.sourceModule(),
                command.occurredAt(), event.schemaVersion(), command.sessionId(), kpId, command.traceId(), command.payload());
    }
}
