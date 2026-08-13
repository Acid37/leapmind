package com.treepeople.leapmindtts.service.lesson;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.treepeople.leapmindtts.pojo.dto.profile.M6Dtos.LearningEventRequest;
import com.treepeople.leapmindtts.service.profile.UserEventService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** M3画像事件发布器，统一将事件写入M6的user_events事件流。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventCollectionClient {
    private static final String EVENT_TYPE = "weak_point_changed";
    private static final String SOURCE_MODULE = "M3";
    private static final String SCHEMA_VERSION = "1.0";

    private final UserEventService userEventService;
    private final ObjectMapper objectMapper;

    /**
     * 上报薄弱点变化事件。发布失败不会回滚M3主业务，但会记录完整异常供联调排查。
     *
     * @param userId 用户ID
     * @param kpId 知识点ID
     * @param oldScore 变化前得分
     * @param newScore 变化后得分
     * @param reason 变化原因
     */
    public void reportWeakPointChanged(
            Long userId,
            Long kpId,
            BigDecimal oldScore,
            BigDecimal newScore,
            String reason) {
        try {
            ObjectNode data = objectMapper.createObjectNode();
            data.put("oldScore", safeScale(oldScore));
            data.put("newScore", safeScale(newScore));
            data.put("reason", reason);

            LearningEventRequest event = new LearningEventRequest(
                    "m3-weak:" + UUID.randomUUID(),
                    userId,
                    EVENT_TYPE,
                    SOURCE_MODULE,
                    OffsetDateTime.now(ZoneOffset.UTC),
                    SCHEMA_VERSION,
                    null,
                    kpId,
                    null,
                    data);
            userEventService.recordInternal(event);
            log.info("M3画像事件发布成功: eventId={}, userId={}, kpId={}", event.eventId(), userId, kpId);
        } catch (RuntimeException exception) {
            log.error("M3画像事件发布失败: userId={}, kpId={}, reason={}",
                    userId, kpId, exception.getMessage(), exception);
        }
    }

    private BigDecimal safeScale(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        return value.setScale(4, RoundingMode.HALF_UP);
    }
}
