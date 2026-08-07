package com.treepeople.leapmindtts.controller.lesson;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.treepeople.leapmindtts.pojo.dto.ConversationSession;
import com.treepeople.leapmindtts.service.lesson.ConversationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class ConversationControllerTest {

    @Mock private ConversationService conversationService;

    @InjectMocks private ConversationController conversationController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(conversationController)
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .build();
    }

    @Test
    void createSession_shouldReturnCreatedSession() throws Exception {
        ConversationSession session = new ConversationSession();
        session.setSessionId("sess_new");
        session.setUserId(1001L);

        when(conversationService.getOrCreateSessionId(any())).thenReturn("sess_new");
        when(conversationService.getSession("sess_new")).thenReturn(session);

        mockMvc.perform(post("/api/conversation/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":1001,\"sceneType\":\"general_qa\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value("sess_new"))
                .andExpect(jsonPath("$.userId").value(1001));
    }

    @Test
    void listSessions_shouldReturnSessionList() throws Exception {
        ConversationSession s1 = new ConversationSession();
        s1.setSessionId("s1");
        s1.setUserId(1001L);

        ConversationSession s2 = new ConversationSession();
        s2.setSessionId("s2");
        s2.setUserId(1001L);

        when(conversationService.listSessions(1001L)).thenReturn(List.of(s1, s2));

        mockMvc.perform(get("/api/conversation/sessions")
                        .param("userId", "1001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void getSession_shouldReturnSession() throws Exception {
        ConversationSession session = new ConversationSession();
        session.setSessionId("sess_123");
        session.setUserId(1001L);

        when(conversationService.getSession("sess_123")).thenReturn(session);

        mockMvc.perform(get("/api/conversation/sessions/{sessionId}", "sess_123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value("sess_123"));
    }

    @Test
    void getSession_notFound_shouldReturn404() throws Exception {
        when(conversationService.getSession("not_exist")).thenReturn(null);

        mockMvc.perform(get("/api/conversation/sessions/{sessionId}", "not_exist"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteSession_shouldReturn200() throws Exception {
        mockMvc.perform(delete("/api/conversation/sessions/{sessionId}", "sess_123"))
                .andExpect(status().isOk());
    }
}