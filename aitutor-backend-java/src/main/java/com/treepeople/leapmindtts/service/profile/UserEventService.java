package com.treepeople.leapmindtts.service.profile;

import com.fasterxml.jackson.databind.JsonNode;
import com.treepeople.leapmindtts.pojo.dto.profile.M6Dtos.EventAck;
import com.treepeople.leapmindtts.pojo.dto.profile.M6Dtos.EventResult;
import com.treepeople.leapmindtts.pojo.dto.profile.M6Dtos.LearningEventRequest;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

public interface UserEventService {
    EventAck record(Long path, LearningEventRequest event, HttpServletRequest request);

    List<EventResult> batch(Long path, List<JsonNode> events, HttpServletRequest request);
}
