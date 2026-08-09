package com.treepeople.leapmindtts.service.profile.platform;

import java.util.List;

/** Fail-closed default; deliberately contains no database or HTTP collaborator. */
public final class DisabledLearningEventPublisher implements LearningEventPublisher {
    private final EventPublishOutcome.Status status;
    private final PlatformCapabilityPolicy policy;
    public DisabledLearningEventPublisher(PlatformCapabilityPolicy policy) { this(EventPublishOutcome.Status.NOT_CONNECTED, policy); }
    public DisabledLearningEventPublisher(EventPublishOutcome.Status status, PlatformCapabilityPolicy policy) {
        if (status != EventPublishOutcome.Status.NOT_CONNECTED && status != EventPublishOutcome.Status.NOT_CONFIGURED) throw new IllegalArgumentException("disabled status required");
        this.status = status;
        this.policy = policy == null ? new DefaultDenyPlatformCapabilityPolicy() : policy;
    }
    @Override public EventPublishOutcome publish(EventPublishContext context, LearningEventCommand command) {
        if (context == null || command == null || !context.subjectUserId().equals(command.subjectUserId())
                || context.purpose() != Purpose.PUBLISH_LEARNING_EVENT
                || !context.sourceModule().name().equals(command.sourceModule())) {
            return new EventPublishOutcome(EventPublishOutcome.Status.REJECTED, "ACCESS_DENIED");
        }
        try { LearningEventCommandValidator.validate(command); }
        catch (RuntimeException invalid) { return new EventPublishOutcome(EventPublishOutcome.Status.REJECTED, "INVALID_EVENT"); }
        PlatformCapabilityPolicy.CapabilityDecision decision = policy.evaluate(context,
                PlatformCapabilityPolicy.Capability.PUBLISH_LEARNING_EVENT, command.eventType());
        if (decision == null || !decision.allowed()) {
            EventPublishOutcome.Status outcome = decision != null && "NOT_CONFIGURED".equals(decision.reason())
                    ? EventPublishOutcome.Status.NOT_CONFIGURED : EventPublishOutcome.Status.REJECTED;
            return new EventPublishOutcome(outcome, decision == null ? "ACCESS_DENIED" : decision.reason());
        }
        return new EventPublishOutcome(status, status.name());
    }
    @Override public List<EventPublishOutcome> publishBatch(EventPublishContext context, List<LearningEventCommand> commands) {
        if (commands == null || commands.isEmpty() || commands.size() > 100) throw new IllegalArgumentException("batch size must be 1..100");
        return commands.stream().map(command -> publish(context, command)).toList();
    }
}
