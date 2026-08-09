package com.treepeople.leapmindtts.service.profile.platform;
public interface PlatformCapabilityPolicy {
    CapabilityDecision evaluate(PlatformAccessContext context, Capability capability, String resource);
    enum Capability { PUBLISH_LEARNING_EVENT, READ_PROFILE_CONTEXT, CALL_PROFILE_ENGINE }
    record CapabilityDecision(boolean allowed, String reason) { }
}
