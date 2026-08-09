package com.treepeople.leapmindtts.service.profile.platform;

/**
 * Second activation key for real adapters.  It is deliberately independent of capability policy
 * so granting a future caller cannot accidentally turn on database-backed platform IO.
 */
public final class PlatformIntegrationReadiness {
    private final boolean ready;
    public PlatformIntegrationReadiness() { this(false); }
    public PlatformIntegrationReadiness(boolean ready) { this.ready = ready; }
    public boolean ready() { return ready; }
}
