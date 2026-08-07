package com.treepeople.leapmindtts.service.profile.platform;

final class PlatformActorValidator {
    private PlatformActorValidator() { }
    static void validate(Long subjectUserId, ActorKind actorKind, Long actorUserId, ServicePrincipal servicePrincipal,
                         SourceModule sourceModule, Purpose purpose, String requestId) {
        if (subjectUserId == null || subjectUserId <= 0 || actorKind == null || purpose == null || sourceModule == null || blank(requestId)) throw new IllegalArgumentException("actor context is incomplete");
        if (actorKind == ActorKind.USER && (actorUserId == null || !actorUserId.equals(subjectUserId) || servicePrincipal != null)) throw new IllegalArgumentException("user actor must equal subject");
        if (actorKind == ActorKind.SERVICE && (servicePrincipal == null || actorUserId != null)) throw new IllegalArgumentException("service actor is incomplete");
    }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
