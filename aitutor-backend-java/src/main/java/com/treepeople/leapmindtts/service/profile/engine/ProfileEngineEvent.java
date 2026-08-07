package com.treepeople.leapmindtts.service.profile.engine;

import com.treepeople.leapmindtts.service.profile.platform.LearningEventCommand;

public record ProfileEngineEvent(long dbEventId, LearningEventCommand command) {
    public ProfileEngineEvent {
        if (dbEventId <= 0 || command == null) throw new IllegalArgumentException("invalid profile engine event");
    }
    public String eventType() { return command.eventType(); }
    public String sourceModule() { return command.sourceModule(); }
    public String schemaVersion() { return command.schemaVersion(); }
}
