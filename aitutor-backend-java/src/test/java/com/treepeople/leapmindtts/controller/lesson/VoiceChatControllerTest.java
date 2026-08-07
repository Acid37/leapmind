package com.treepeople.leapmindtts.controller.lesson;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.treepeople.leapmindtts.exception.BizErrorCode;
import com.treepeople.leapmindtts.pojo.dto.VoiceChatRequest;
import com.treepeople.leapmindtts.service.lesson.VoiceChatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class VoiceChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private VoiceChatService voiceChatService; // Mock掉底层服务，专注于Controller层测试

    private VoiceChatRequest request;

    @BeforeEach
    void setUp() {
        request = new VoiceChatRequest();
        request.setCourseId("test-course");
        request.setQuestion("test-question");
    }

    /**
     * 3. 限流触发测试
     */
    @Test
    void testRateLimiterTriggered() throws Exception {
        String userId = "user-limited";

        // 连续发送10个请求，应该都是成功的（或者说，被底层mock接受）
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/api/voice-chat/ask")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
                    .header("X-User-Id", userId))
                    .andExpect(status().isOk());
        }

        // 发送第11个请求，应该被限流
        mockMvc.perform(post("/api/voice-chat/ask")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .header("X-User-Id", userId))
                .andExpect(status().isOk()) // fallback方法返回的是200 OK，但内容是错误信息
                .andExpect(jsonPath("$.code").value(BizErrorCode.RATE_LIMITED.getCode()))
                .andExpect(jsonPath("$.message").value(BizErrorCode.RATE_LIMITED.getMessage()));
    }
}
