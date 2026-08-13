package com.treepeople.leapmindtts.service.lesson;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.treepeople.leapmindtts.pojo.dto.profile.M6Dtos.LearningEventRequest;
import com.treepeople.leapmindtts.service.profile.UserEventService;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class EventCollectionClientTest {
    @Test
    void weakPointChangedIsRecordedInUnifiedUserEvents() {
        UserEventService userEventService = org.mockito.Mockito.mock(UserEventService.class);
        EventCollectionClient client = new EventCollectionClient(userEventService, new ObjectMapper());

        client.reportWeakPointChanged(
                23L,
                42L,
                new BigDecimal("0.8000"),
                new BigDecimal("0.3000"),
                "ACCURACY_DROP");

        ArgumentCaptor<LearningEventRequest> captor = ArgumentCaptor.forClass(LearningEventRequest.class);
        verify(userEventService).recordInternal(captor.capture());
        LearningEventRequest event = captor.getValue();
        assertEquals(23L, event.userId());
        assertEquals(42L, event.kpId());
        assertEquals("weak_point_changed", event.eventType());
        assertEquals("M3", event.sourceModule());
        assertEquals("1.0", event.schemaVersion());
        assertEquals(new BigDecimal("0.8000"), event.data().get("oldScore").decimalValue());
        assertEquals(new BigDecimal("0.3000"), event.data().get("newScore").decimalValue());
        assertEquals("ACCURACY_DROP", event.data().get("reason").textValue());
    }
}
