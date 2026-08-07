package com.treepeople.leapmindtts.service.profile.platform;

public record EventPublishContext(Long subjectUserId, ActorKind actorKind, Long actorUserId,
                                  ServicePrincipal servicePrincipal, SourceModule sourceModule, Purpose purpose,
                                  String requestId) implements PlatformAccessContext {
    public EventPublishContext {
        PlatformActorValidator.validate(subjectUserId, actorKind, actorUserId, servicePrincipal, sourceModule, purpose, requestId);
    }
}
