package com.treepeople.leapmindtts.service.profile.impl;

import com.treepeople.leapmindtts.exception.M6ApiException;
import com.treepeople.leapmindtts.pojo.dto.profile.M6Dtos.LearningEventRequest;
import com.treepeople.leapmindtts.pojo.entity.UserEvent;
import com.treepeople.leapmindtts.service.profile.validation.DuplicateConstraintClassifier;
import com.treepeople.leapmindtts.service.profile.validation.EventPayloadCanonicalizer;
import com.treepeople.leapmindtts.service.profile.validation.LearningEventPolicy;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * The sole HTTP-independent ingestion path for M6 facts.  It intentionally owns validation,
 * normalization, idempotency and persistence so internal callers cannot bypass the public route.
 */
@Service
public class EventIngestionCore {
    private static final Instant MYSQL_DATETIME_MIN_UTC = LocalDateTime.of(1000, 1, 1, 0, 0).toInstant(ZoneOffset.UTC);
    private static final Instant MYSQL_DATETIME_MAX_UTC = LocalDateTime.of(9999, 12, 31, 23, 59, 59, 999_000_000).toInstant(ZoneOffset.UTC);
    private final Validator validator;
    private final EventInsertTransaction writer;
    private final CommittedEventReader reader;

    public EventIngestionCore(Validator validator, EventInsertTransaction writer, CommittedEventReader reader) {
        this.validator = validator;
        this.writer = writer;
        this.reader = reader;
    }

    public IngestionResult ingest(LearningEventRequest event, Instant receivedAt) {
        if (receivedAt == null) throw invalid();
        validate(event);
        Instant stableReceivedAt = receivedAt.truncatedTo(ChronoUnit.MILLIS);
        NormalizedOccurredAt normalized = normalizeOccurredAt(event.occurredAt());
        String payloadHash = hash(event, normalized.instant());
        String processStatus = normalized.instant().isBefore(stableReceivedAt.minus(24, ChronoUnit.HOURS))
                || normalized.instant().isAfter(stableReceivedAt.plus(24, ChronoUnit.HOURS)) ? "QUARANTINED" : "PENDING";
        try {
            writer.insert(UserEvent.builder()
                    .eventId(event.eventId()).userId(event.userId()).eventType(event.eventType())
                    .sourceModule(event.sourceModule()).sessionId(event.sessionId()).kpId(event.kpId())
                    .traceId(event.traceId()).schemaVersion(event.schemaVersion())
                    .eventDataJson(new String(EventPayloadCanonicalizer.canonical(event.data()), StandardCharsets.UTF_8))
                    .occurredAt(normalized.utc()).receivedAt(LocalDateTime.ofInstant(stableReceivedAt, ZoneOffset.UTC))
                    .processStatus(processStatus).payloadHash(payloadHash).payloadHashVersion(1).build());
            return new IngestionResult(event.eventId(), false, processStatus, stableReceivedAt);
        } catch (DuplicateKeyException duplicate) {
            if (!DuplicateConstraintClassifier.isEventId(duplicate)) throw duplicate;
            UserEvent committed = findCommitted(event.eventId());
            if (!payloadHash.equals(committed.getPayloadHash())) {
                throw new M6ApiException(HttpStatus.CONFLICT, "PROFILE_IDEMPOTENCY_CONFLICT", "事件标识冲突");
            }
            if (committed.getReceivedAt() == null || committed.getProcessStatus() == null) throw degraded();
            return new IngestionResult(event.eventId(), true, committed.getProcessStatus(),
                    committed.getReceivedAt().toInstant(ZoneOffset.UTC));
        }
    }

    /** Shared validation entry used by the HTTP self-auth boundary before any persistence occurs. */
    public void validate(LearningEventRequest event) {
        if (event == null) throw invalid();
        Set<ConstraintViolation<LearningEventRequest>> violations = validator.validate(event);
        if (!violations.isEmpty()) throw invalid();
        LearningEventPolicy.validate(event);
    }

    private UserEvent findCommitted(String eventId) {
        for (int delay : new int[]{10, 25, 50}) {
            UserEvent committed = reader.read(eventId);
            if (committed != null) return committed;
            try { Thread.sleep(delay); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw degraded(); }
        }
        throw degraded();
    }

    private NormalizedOccurredAt normalizeOccurredAt(OffsetDateTime supplied) {
        try {
            if (supplied == null) throw invalid();
            Instant instant = supplied.toInstant().truncatedTo(ChronoUnit.MILLIS);
            if (instant.isBefore(MYSQL_DATETIME_MIN_UTC) || instant.isAfter(MYSQL_DATETIME_MAX_UTC)) throw invalid();
            return new NormalizedOccurredAt(instant, LocalDateTime.ofInstant(instant, ZoneOffset.UTC));
        } catch (M6ApiException expected) { throw expected; }
        catch (RuntimeException malformed) { throw invalid(); }
    }

    private String hash(LearningEventRequest event, Instant occurredAt) {
        String semantic = event.userId() + "|" + event.eventType() + "|" + event.sourceModule() + "|"
                + occurredAt.toEpochMilli() + "|" + event.schemaVersion() + "|" + Objects.toString(event.sessionId(), "")
                + "|" + Objects.toString(event.kpId(), "") + "|" + Objects.toString(event.traceId(), "") + "|"
                + new String(EventPayloadCanonicalizer.canonical(event.data()), StandardCharsets.UTF_8);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(semantic.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private M6ApiException invalid() { return new M6ApiException(HttpStatus.BAD_REQUEST, "PROFILE_EVENT_INVALID", "学习事件无效"); }
    private M6ApiException degraded() { return new M6ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PROFILE_SERVICE_DEGRADED", "用户画像服务暂不可用"); }

    public record IngestionResult(String eventId, boolean duplicate, String processStatus, Instant receivedAt) { }
    private record NormalizedOccurredAt(Instant instant, LocalDateTime utc) { }
}
