package com.treepeople.leapmindtts.service.profile.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.treepeople.leapmindtts.config.M6EventJsonCodec;
import com.treepeople.leapmindtts.exception.M6ApiException;
import com.treepeople.leapmindtts.pojo.dto.profile.M6Dtos.EventAck;
import com.treepeople.leapmindtts.pojo.dto.profile.M6Dtos.EventResult;
import com.treepeople.leapmindtts.pojo.dto.profile.M6Dtos.LearningEventRequest;
import com.treepeople.leapmindtts.pojo.entity.UserEvent;
import com.treepeople.leapmindtts.service.profile.UserEventService;
import com.treepeople.leapmindtts.service.profile.security.ProfileActorResolver;
import com.treepeople.leapmindtts.service.profile.validation.DuplicateConstraintClassifier;
import com.treepeople.leapmindtts.service.profile.validation.EventPayloadCanonicalizer;
import com.treepeople.leapmindtts.service.profile.validation.LearningEventPolicy;
import com.treepeople.leapmindtts.util.M6RequestIds;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class UserEventServiceImpl implements UserEventService {
    private static final Instant MYSQL_DATETIME_MIN_UTC = LocalDateTime.of(1000, 1, 1, 0, 0).toInstant(ZoneOffset.UTC);
    private static final Instant MYSQL_DATETIME_MAX_UTC = LocalDateTime.of(9999, 12, 31, 23, 59, 59, 999_000_000).toInstant(ZoneOffset.UTC);
    private final M6EventJsonCodec codec;
    private final Validator validator;
    private final ProfileActorResolver actor;
    private final EventInsertTransaction writer;
    private final CommittedEventReader reader;
    private final Clock clock;

    public UserEventServiceImpl(M6EventJsonCodec codec, Validator validator, ProfileActorResolver actor,
                                EventInsertTransaction writer, CommittedEventReader reader, Clock clock) {
        this.codec = codec;
        this.validator = validator;
        this.actor = actor;
        this.writer = writer;
        this.reader = reader;
        this.clock = clock;
    }

    @Override
    public EventAck record(Long path, LearningEventRequest event, HttpServletRequest request) {
        actor.authorizeSelf(request, path);
        Instant receivedAt = clock.instant().truncatedTo(ChronoUnit.MILLIS);
        validate(event);
        if (!path.equals(event.userId())) throw accessDenied();
        return persist(event, request, receivedAt);
    }

    @Override
    public List<EventResult> batch(Long path, List<JsonNode> nodes, HttpServletRequest request) {
        actor.authorizeSelf(request, path);
        Instant receivedAt = clock.instant().truncatedTo(ChronoUnit.MILLIS);
        String requestId = M6RequestIds.resolveOrCreate(request);
        List<EventResult> results = new ArrayList<>(nodes.size());
        for (int index = 0; index < nodes.size(); index++) {
            String safeEventId = null;
            try {
                JsonNode node = nodes.get(index);
                if (node == null || !node.isObject()) throw invalid();
                LearningEventRequest event = codec.read(node, LearningEventRequest.class);
                validate(event);
                safeEventId = event.eventId();
                if (!path.equals(event.userId())) throw accessDenied();
                EventAck ack = persist(event, request, receivedAt);
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

    private void validate(LearningEventRequest event) {
        if (event == null) throw invalid();
        Set<ConstraintViolation<LearningEventRequest>> violations = validator.validate(event);
        if (!violations.isEmpty()) throw invalid();
        LearningEventPolicy.validate(event);
    }

    private EventAck persist(LearningEventRequest event, HttpServletRequest request, Instant receivedAt) {
        NormalizedOccurredAt normalized = normalizeOccurredAt(event.occurredAt());
        String payloadHash = hash(event, normalized.instant());
        Instant occurredAt = normalized.instant();
        LocalDateTime occurredAtUtc = normalized.utc();
        String processStatus = occurredAt.isBefore(receivedAt.minus(24, ChronoUnit.HOURS))
                || occurredAt.isAfter(receivedAt.plus(24, ChronoUnit.HOURS)) ? "QUARANTINED" : "PENDING";
        try {
            writer.insert(UserEvent.builder()
                    .eventId(event.eventId()).userId(event.userId()).eventType(event.eventType())
                    .sourceModule(event.sourceModule()).sessionId(event.sessionId()).kpId(event.kpId())
                    .traceId(event.traceId()).schemaVersion(event.schemaVersion())
                    .eventDataJson(new String(EventPayloadCanonicalizer.canonical(event.data()), StandardCharsets.UTF_8))
                    .occurredAt(occurredAtUtc)
                    .receivedAt(LocalDateTime.ofInstant(receivedAt, ZoneOffset.UTC))
                    .processStatus(processStatus).payloadHash(payloadHash).payloadHashVersion(1).build());
            return ack(event.eventId(), false, processStatus, receivedAt, request);
        } catch (DuplicateKeyException duplicate) {
            if (!DuplicateConstraintClassifier.isEventId(duplicate)) throw duplicate;
            UserEvent committed = findCommitted(event.eventId());
            if (!payloadHash.equals(committed.getPayloadHash())) {
                throw new M6ApiException(HttpStatus.CONFLICT, "PROFILE_IDEMPOTENCY_CONFLICT", "事件标识冲突");
            }
            if (committed.getReceivedAt() == null || committed.getProcessStatus() == null) {
                throw degraded();
            }
            return ack(event.eventId(), true, committed.getProcessStatus(),
                    committed.getReceivedAt().toInstant(ZoneOffset.UTC), request);
        }
    }

    private UserEvent findCommitted(String eventId) {
        UserEvent committed = null;
        for (int delay : new int[]{10, 25, 50}) {
            committed = reader.read(eventId);
            if (committed != null) return committed;
            try {
                Thread.sleep(delay);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw degraded();
            }
        }
        throw degraded();
    }

    private EventAck ack(String eventId, boolean duplicate, String processStatus, Instant receivedAt,
                         HttpServletRequest request) {
        return new EventAck(true, eventId, duplicate ? "DUPLICATE" : "ACCEPTED", duplicate,
                processStatus, receivedAt.toString(), M6RequestIds.resolveOrCreate(request));
    }

    private NormalizedOccurredAt normalizeOccurredAt(OffsetDateTime supplied) {
        try {
            if (supplied == null) throw invalid();
            Instant instant = supplied.toInstant().truncatedTo(ChronoUnit.MILLIS);
            if (instant.isBefore(MYSQL_DATETIME_MIN_UTC) || instant.isAfter(MYSQL_DATETIME_MAX_UTC)) throw invalid();
            LocalDateTime utc = LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
            return new NormalizedOccurredAt(instant, utc);
        } catch (M6ApiException expected) {
            throw expected;
        } catch (RuntimeException malformed) {
            throw invalid();
        }
    }

    private record NormalizedOccurredAt(Instant instant, LocalDateTime utc) { }

    private String hash(LearningEventRequest event, Instant occurredAt) {
        String semantic = event.userId() + "|" + event.eventType() + "|" + event.sourceModule() + "|"
                + occurredAt.toEpochMilli() + "|"
                + event.schemaVersion() + "|" + Objects.toString(event.sessionId(), "") + "|"
                + Objects.toString(event.kpId(), "") + "|" + Objects.toString(event.traceId(), "") + "|"
                + new String(EventPayloadCanonicalizer.canonical(event.data()), StandardCharsets.UTF_8);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(semantic.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private M6ApiException invalid() { return new M6ApiException(HttpStatus.BAD_REQUEST, "PROFILE_EVENT_INVALID", "学习事件无效"); }
    private M6ApiException accessDenied() { return new M6ApiException(HttpStatus.FORBIDDEN, "PROFILE_ACCESS_DENIED", "无权访问"); }
    private M6ApiException degraded() { return new M6ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PROFILE_SERVICE_DEGRADED", "用户画像服务暂不可用"); }
}
