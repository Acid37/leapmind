package com.treepeople.leapmindtts.service.profile.platform;

/** Immutable caller identity carried by every internal platform port invocation. */
public interface PlatformAccessContext {
    Long subjectUserId();
    ActorKind actorKind();
    Long actorUserId();
    ServicePrincipal servicePrincipal();
    SourceModule sourceModule();
    Purpose purpose();
    String requestId();
}
