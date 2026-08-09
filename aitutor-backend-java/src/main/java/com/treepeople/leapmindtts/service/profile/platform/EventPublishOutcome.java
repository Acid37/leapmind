package com.treepeople.leapmindtts.service.profile.platform;
public record EventPublishOutcome(Status status, String reason) {
    public enum Status { ACCEPTED, DUPLICATE, CONFLICT, QUARANTINED, NOT_CONNECTED, NOT_CONFIGURED, REJECTED }
}
