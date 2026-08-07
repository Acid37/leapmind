package com.treepeople.leapmindtts.service.profile.engine;

import java.util.List;
import java.util.UUID;
import com.treepeople.leapmindtts.service.profile.platform.KnowledgePointRef;

/** Java DTO for the fixed v1.0 engine contract; it has no untyped extension bag. */
public record ProfileEngineRequest(String contractVersion, UUID requestId, Long userId, Mode mode,
                                   Long baseProfileVersion, Long fromEventIdExclusive,
                                   Long eventWatermarkInclusive, List<ProfileEngineEvent> events) {
    public enum Mode { INCREMENTAL, FULL }
    public ProfileEngineRequest {
        if (!"1.0".equals(contractVersion) || requestId == null || userId == null || userId <= 0 || mode == null
                || nonNegative(baseProfileVersion) || nonNegative(fromEventIdExclusive) || nonNegative(eventWatermarkInclusive)
                || fromEventIdExclusive > eventWatermarkInclusive || events == null) throw new IllegalArgumentException("invalid profile engine request");
        events = List.copyOf(events);
        long previous = fromEventIdExclusive;
        for (ProfileEngineEvent event : events) {
            if (event == null || event.dbEventId() <= previous || event.dbEventId() > eventWatermarkInclusive
                    || !userId.equals(event.command().subjectUserId())
                    || event.command().knowledgePoint() instanceof KnowledgePointRef.Unresolved
                    || ("answer_question".equals(event.command().eventType())
                    && !(event.command().knowledgePoint() instanceof KnowledgePointRef.Resolved)))
                throw new IllegalArgumentException("events must be ordered, within watermark, resolved and owned by the request user");
            previous = event.dbEventId();
        }
    }
    private static boolean nonNegative(Long value) { return value == null || value < 0; }
}
