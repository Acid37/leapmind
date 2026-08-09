package com.treepeople.leapmindtts.service.profile.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.treepeople.leapmindtts.config.M6EventJsonCodec;
import com.treepeople.leapmindtts.exception.M6ApiException;
import com.treepeople.leapmindtts.pojo.dto.profile.M6Dtos.EventAck;
import com.treepeople.leapmindtts.pojo.dto.profile.M6Dtos.EventResult;
import com.treepeople.leapmindtts.pojo.dto.profile.M6Dtos.LearningEventRequest;
import com.treepeople.leapmindtts.service.profile.UserEventService;
import com.treepeople.leapmindtts.service.profile.security.ProfileActorResolver;
import com.treepeople.leapmindtts.util.M6RequestIds;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Validator;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.dao.DataAccessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class UserEventServiceImpl implements UserEventService {
    /** 服务端直调模块白名单：这些 sourceModule 无用户 JWT，只校验 userId 一致。 */
    private static final Set<String> SERVICE_SOURCE_MODULES = Set.of("M1", "M3");
    private final M6EventJsonCodec codec;
    private final ProfileActorResolver actor;
    private final EventIngestionCore core;
    private final Clock clock;

    @Autowired
    public UserEventServiceImpl(M6EventJsonCodec codec, ProfileActorResolver actor, EventIngestionCore core, Clock clock) {
        this.codec = codec;
        this.actor = actor;
        this.core = core;
        this.clock = clock;
    }

    /** Compatibility constructor retained for existing focused HTTP tests. */
    public UserEventServiceImpl(M6EventJsonCodec codec, Validator validator, ProfileActorResolver actor,
                                EventInsertTransaction writer, CommittedEventReader reader, Clock clock) {
        this(codec, actor, new EventIngestionCore(validator, writer, reader), clock);
    }

    /** 白名单模块（M1/M3）无 JWT 直调，只校验 userId 一致；其余模块走用户 JWT 鉴权。 */
    private void authorize(HttpServletRequest request, Long pathUserId, LearningEventRequest event) {
        if (SERVICE_SOURCE_MODULES.contains(event.sourceModule())) {
            if (!pathUserId.equals(event.userId())) throw accessDenied();
            return;
        }
        actor.authorizeSelf(request, pathUserId);
    }

    @Override
    public EventAck record(Long path, LearningEventRequest event, HttpServletRequest request) {
        authorize(request, path, event);
        Instant receivedAt = clock.instant().truncatedTo(ChronoUnit.MILLIS);
        core.validate(event);
        return ack(core.ingest(event, receivedAt), request);
    }

    @Override
    public EventAck recordInternal(LearningEventRequest event) {
        Instant receivedAt = clock.instant().truncatedTo(ChronoUnit.MILLIS);
        core.validate(event);
        return ack(core.ingest(event, receivedAt), java.util.UUID.randomUUID().toString());
    }

    @Override
    public List<EventResult> batch(Long path, List<JsonNode> nodes, HttpServletRequest request) {
        Instant receivedAt = clock.instant().truncatedTo(ChronoUnit.MILLIS);
        String requestId = M6RequestIds.resolveOrCreate(request);
        List<EventResult> results = new ArrayList<>(nodes.size());
        for (int index = 0; index < nodes.size(); index++) {
            String safeEventId = null;
            try {
                JsonNode node = nodes.get(index);
                if (node == null || !node.isObject()) throw invalid();
                LearningEventRequest event = codec.read(node, LearningEventRequest.class);
                core.validate(event);
                safeEventId = event.eventId();
                authorize(request, path, event);
                EventAck ack = ack(core.ingest(event, receivedAt), request);
                results.add(new EventResult(index, ack.eventId(), ack.eventStatus(), ack.profileUpdateStatus(),
                        ack.receivedAt(), null, requestId));
            } catch (DataAccessException databaseFailure) {
                throw databaseFailure;
            } catch (M6ApiException | JsonProcessingException inputFailure) {
                String code = inputFailure instanceof M6ApiException m6 ? m6.getErrorCode() : "PROFILE_EVENT_INVALID";
                results.add(new EventResult(index, safeEventId, "FAILED", null, null, code, requestId));
            }
        }
        return results;
    }

    private EventAck ack(EventIngestionCore.IngestionResult result, HttpServletRequest request) {
        return ack(result, M6RequestIds.resolveOrCreate(request));
    }

    private EventAck ack(EventIngestionCore.IngestionResult result, String requestId) {
        return new EventAck(true, result.eventId(), result.duplicate() ? "DUPLICATE" : "ACCEPTED", result.duplicate(),
                result.processStatus(), result.receivedAt().toString(), requestId);
    }

    private M6ApiException invalid() { return new M6ApiException(HttpStatus.BAD_REQUEST, "PROFILE_EVENT_INVALID", "学习事件无效"); }
    private M6ApiException accessDenied() { return new M6ApiException(HttpStatus.FORBIDDEN, "PROFILE_ACCESS_DENIED", "无权访问"); }
}
