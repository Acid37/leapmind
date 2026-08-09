package com.treepeople.leapmindtts.service.profile.engine;

/** There is deliberately no HTTP client, scheduler, writer, CAS or event-state transition here. */
public final class DisabledProfileEngineAdapter implements ProfileEnginePort {
    @Override public ProfileEngineResponse buildProfile(ProfileEngineRequest request) {
        throw new ProfileEngineUnavailableException("NOT_CONNECTED");
    }
}
