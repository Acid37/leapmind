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

    /**
     * 内部事件记录（绕过 HTTP 鉴权）
     * <p>供后端服务层在业务事务完成后发布 M6 画像事件使用，
     * 例如 markAsReviewed → publish mark_reviewed。</p>
     */
    EventAck recordInternal(LearningEventRequest event);
}
