package com.treepeople.leapmindtts.service.profile.platform;

import java.util.List;

public interface LearningEventPublisher {
    EventPublishOutcome publish(EventPublishContext context, LearningEventCommand command);
    List<EventPublishOutcome> publishBatch(EventPublishContext context, List<LearningEventCommand> commands);
}
