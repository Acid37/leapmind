package com.treepeople.leapmindtts.service.profile.platform;
/** New platform capabilities have no ambient permission until an approved integration supplies one. */
public final class DefaultDenyPlatformCapabilityPolicy implements PlatformCapabilityPolicy {
    @Override public CapabilityDecision evaluate(PlatformAccessContext context, Capability capability, String resource) {
        return new CapabilityDecision(false, "NOT_CONFIGURED");
    }
}
