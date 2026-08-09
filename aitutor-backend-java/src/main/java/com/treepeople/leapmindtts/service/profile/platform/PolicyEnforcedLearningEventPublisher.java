package com.treepeople.leapmindtts.service.profile.platform;

import com.treepeople.leapmindtts.exception.M6ApiException;
import com.treepeople.leapmindtts.service.profile.impl.EventIngestionCore;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.springframework.dao.DataAccessException;

/**
 * Deliberately unregistered real adapter.  Construction is an explicit future activation step;
 * policy and readiness must both permit each call before the shared ingestion core is touched.
 */
public final class PolicyEnforcedLearningEventPublisher implements LearningEventPublisher {
    private final PlatformCapabilityPolicy policy;
    private final PlatformIntegrationReadiness readiness;
    private final EventIngestionCore core;
    private final Clock clock;

    public PolicyEnforcedLearningEventPublisher(PlatformCapabilityPolicy policy,
                                                PlatformIntegrationReadiness readiness,
                                                EventIngestionCore core, Clock clock) {
        this.policy = policy == null ? new DefaultDenyPlatformCapabilityPolicy() : policy;
        this.readiness = readiness == null ? new PlatformIntegrationReadiness() : readiness;
        this.core = java.util.Objects.requireNonNull(core, "core");
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
    }

    @Override public EventPublishOutcome publish(EventPublishContext context, LearningEventCommand command) {
        EventPublishOutcome guard = guard(context, command);
        if (guard != null) return guard;
        try {
            EventIngestionCore.IngestionResult result = core.ingest(LearningEventCommandValidator.toRequest(command),
                    clock.instant().truncatedTo(ChronoUnit.MILLIS));
            return new EventPublishOutcome(result.duplicate() ? EventPublishOutcome.Status.DUPLICATE
                    : "QUARANTINED".equals(result.processStatus()) ? EventPublishOutcome.Status.QUARANTINED
                    : EventPublishOutcome.Status.ACCEPTED, result.processStatus());
        } catch (M6ApiException failure) {
            return switch (failure.getErrorCode()) {
                case "PROFILE_IDEMPOTENCY_CONFLICT" -> new EventPublishOutcome(EventPublishOutcome.Status.CONFLICT, "IDEMPOTENCY_CONFLICT");
                case "PROFILE_SERVICE_DEGRADED" -> new EventPublishOutcome(EventPublishOutcome.Status.REJECTED, "PROFILE_SERVICE_DEGRADED");
                default -> new EventPublishOutcome(EventPublishOutcome.Status.REJECTED, "INVALID_EVENT");
            };
        } catch (DataAccessException failure) {
            return new EventPublishOutcome(EventPublishOutcome.Status.REJECTED, "PROFILE_SERVICE_DEGRADED");
        }
    }

    @Override public List<EventPublishOutcome> publishBatch(EventPublishContext context, List<LearningEventCommand> commands) {
        if (commands == null || commands.isEmpty() || commands.size() > 100) throw new IllegalArgumentException("batch size must be 1..100");
        List<EventPublishOutcome> outcomes = new ArrayList<>(commands.size());
        for (LearningEventCommand command : commands) outcomes.add(publish(context, command));
        return List.copyOf(outcomes);
    }

    private EventPublishOutcome guard(EventPublishContext context, LearningEventCommand command) {
        if (context == null || command == null || context.purpose() != Purpose.PUBLISH_LEARNING_EVENT
                || !context.subjectUserId().equals(command.subjectUserId())
                || !context.sourceModule().name().equals(command.sourceModule())
                || context.sourceModule() == SourceModule.M8) {
            return new EventPublishOutcome(EventPublishOutcome.Status.REJECTED, "ACCESS_DENIED");
        }
        if (command.knowledgePoint() instanceof KnowledgePointRef.Unresolved)
            return new EventPublishOutcome(EventPublishOutcome.Status.REJECTED, "UNRESOLVED_KNOWLEDGE_POINT");
        if ("answer_question".equals(command.eventType())
                && !(command.knowledgePoint() instanceof KnowledgePointRef.Resolved))
            return new EventPublishOutcome(EventPublishOutcome.Status.REJECTED, "KNOWLEDGE_POINT_REQUIRED");
        try { LearningEventCommandValidator.validate(command); }
        catch (RuntimeException invalid) { return new EventPublishOutcome(EventPublishOutcome.Status.REJECTED, "INVALID_EVENT"); }
        PlatformCapabilityPolicy.CapabilityDecision decision = policy.evaluate(context,
                PlatformCapabilityPolicy.Capability.PUBLISH_LEARNING_EVENT, command.eventType());
        if (decision == null || !decision.allowed()) return new EventPublishOutcome(
                decision != null && "NOT_CONFIGURED".equals(decision.reason()) ? EventPublishOutcome.Status.NOT_CONFIGURED
                        : EventPublishOutcome.Status.REJECTED, decision == null ? "ACCESS_DENIED" : decision.reason());
        if (!readiness.ready()) return new EventPublishOutcome(EventPublishOutcome.Status.NOT_CONNECTED, "NOT_CONNECTED");
        return null;
    }
}
