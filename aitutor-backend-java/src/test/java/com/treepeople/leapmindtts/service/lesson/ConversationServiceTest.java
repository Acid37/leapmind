package com.treepeople.leapmindtts.service.lesson;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.treepeople.leapmindtts.config.ConversationProperties;
import com.treepeople.leapmindtts.mapper.ConversationMessageMapper;
import com.treepeople.leapmindtts.mapper.ConversationSessionMapper;
import com.treepeople.leapmindtts.pojo.dto.ConversationRequest;
import com.treepeople.leapmindtts.pojo.dto.ConversationSession;
import com.treepeople.leapmindtts.pojo.dto.ConversationRequest.SceneType;
import com.treepeople.leapmindtts.pojo.entity.ConversationMessageEntity;
import com.treepeople.leapmindtts.pojo.entity.ConversationSessionEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConversationServiceTest {

    @Mock private AIModelService aiModelService;
    @Mock private AiTeacherBaiduAsrService aiTeacherBaiduAsrService;
    @Mock private WebClient.Builder webClientBuilder;
    @Mock private WebClient webClient;
    @Mock private StringRedisTemplate stringRedisTemplate;
    @Mock private ValueOperations<String, String> valueOps;
    @Mock private ConversationSessionMapper sessionMapper;
    @Mock private ConversationMessageMapper messageMapper;
    @Mock private ConversationProperties properties;

    @Captor private ArgumentCaptor<ConversationSessionEntity> sessionEntityCaptor;
    @Captor private ArgumentCaptor<ConversationMessageEntity> messageEntityCaptor;

    private ConversationService service;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        when(webClientBuilder.build()).thenReturn(webClient);
    }

    @Test
    void createNewSession_shouldSaveToRedisAndDb() throws Exception {
        when(properties.getTimeout()).thenReturn(Duration.ofMinutes(30));
        when(properties.getMessageLimit()).thenReturn(20);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);

        service = new ConversationService(aiModelService, aiTeacherBaiduAsrService,
                webClientBuilder, stringRedisTemplate, sessionMapper, messageMapper,
                properties, objectMapper);

        ConversationRequest req = new ConversationRequest();
        req.setUserId(1001L);
        req.setSceneType(SceneType.explaining);
        req.setContext(Map.of("questionId", 456));

        String sessionId = service.getOrCreateSessionId(req);

        assertNotNull(sessionId);
        verify(valueOps).set(eq("user:session:" + sessionId), anyString(), eq(1800L), any());
        verify(sessionMapper).insert(sessionEntityCaptor.capture());
        ConversationSessionEntity entity = sessionEntityCaptor.getValue();
        assertEquals(sessionId, entity.getSessionId());
        assertEquals(1001L, entity.getUserId());
        assertEquals("explaining", entity.getSceneType());
        assertTrue(entity.getContextJson().contains("\"questionId\":456"));
    }

    @Test
    void existingSessionInRedis_shouldReturnDirectly() {
        when(properties.getTimeout()).thenReturn(Duration.ofMinutes(30));
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);

        service = new ConversationService(aiModelService, aiTeacherBaiduAsrService,
                webClientBuilder, stringRedisTemplate, sessionMapper, messageMapper,
                properties, objectMapper);

        String sessionId = "sess_existing";
        String redisJson = "{\"sessionId\":\"" + sessionId + "\",\"userId\":1001,\"sceneType\":\"general_qa\","
                + "\"createdAt\":1000,\"updatedAt\":1000,\"messages\":[]}";
        when(valueOps.get("user:session:" + sessionId)).thenReturn(redisJson);

        ConversationRequest req = new ConversationRequest();
        req.setSessionId(sessionId);
        req.setUserId(1001L);

        String result = service.getOrCreateSessionId(req);

        assertEquals(sessionId, result);
        verify(stringRedisTemplate).expire("user:session:" + sessionId, 1800L, TimeUnit.SECONDS);
        verify(sessionMapper, never()).selectBySessionId(any());
    }

    @Test
    void sessionNotInRedis_shouldRestoreFromDb() {
        when(properties.getTimeout()).thenReturn(Duration.ofMinutes(30));
        when(properties.getMessageLimit()).thenReturn(20);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);

        service = new ConversationService(aiModelService, aiTeacherBaiduAsrService,
                webClientBuilder, stringRedisTemplate, sessionMapper, messageMapper,
                properties, objectMapper);

        String sessionId = "sess_db_only";
        when(valueOps.get("user:session:" + sessionId)).thenReturn(null);

        ConversationSessionEntity dbEntity = new ConversationSessionEntity();
        dbEntity.setSessionId(sessionId);
        dbEntity.setUserId(1001L);
        dbEntity.setSceneType("doing_exercise");
        dbEntity.setContextJson("{\"questionId\":123}");
        dbEntity.setCreatedAt(LocalDateTime.now());
        dbEntity.setUpdatedAt(LocalDateTime.now());
        when(sessionMapper.selectBySessionId(sessionId)).thenReturn(dbEntity);

        ConversationMessageEntity msg = new ConversationMessageEntity();
        msg.setRole("user");
        msg.setContent("hello");
        when(messageMapper.selectBySessionId(sessionId)).thenReturn(List.of(msg));

        ConversationRequest req = new ConversationRequest();
        req.setSessionId(sessionId);
        req.setUserId(1001L);

        String result = service.getOrCreateSessionId(req);

        assertEquals(sessionId, result);
        verify(valueOps).set(eq("user:session:" + sessionId), anyString(), eq(1800L), any());
    }

    @Test
    void getSession_fromRedis() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);

        service = new ConversationService(aiModelService, aiTeacherBaiduAsrService,
                webClientBuilder, stringRedisTemplate, sessionMapper, messageMapper,
                properties, objectMapper);

        String sessionId = "sess_redis_get";
        String redisJson = "{\"sessionId\":\"" + sessionId + "\",\"userId\":1001,\"sceneType\":\"general_qa\","
                + "\"createdAt\":1000,\"updatedAt\":1000,\"messages\":[]}";
        when(valueOps.get("user:session:" + sessionId)).thenReturn(redisJson);

        ConversationSession session = service.getSession(sessionId);

        assertNotNull(session);
        assertEquals(sessionId, session.getSessionId());
        verify(sessionMapper, never()).selectBySessionId(any());
    }

    @Test
    void getSession_notFound_shouldReturnNull() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);

        service = new ConversationService(aiModelService, aiTeacherBaiduAsrService,
                webClientBuilder, stringRedisTemplate, sessionMapper, messageMapper,
                properties, objectMapper);

        when(valueOps.get("user:session:not_exist")).thenReturn(null);
        when(sessionMapper.selectBySessionId("not_exist")).thenReturn(null);

        assertNull(service.getSession("not_exist"));
    }

    @Test
    void listSessions_shouldReturnUserSessions() {
        service = new ConversationService(aiModelService, aiTeacherBaiduAsrService,
                webClientBuilder, stringRedisTemplate, sessionMapper, messageMapper,
                properties, objectMapper);

        Long userId = 1001L;
        ConversationSessionEntity e1 = new ConversationSessionEntity();
        e1.setSessionId("s1");
        e1.setUserId(userId);
        e1.setSceneType("general_qa");
        e1.setCreatedAt(LocalDateTime.now());
        e1.setUpdatedAt(LocalDateTime.now());

        ConversationSessionEntity e2 = new ConversationSessionEntity();
        e2.setSessionId("s2");
        e2.setUserId(userId);
        e2.setSceneType("explaining");
        e2.setCreatedAt(LocalDateTime.now());
        e2.setUpdatedAt(LocalDateTime.now());

        when(sessionMapper.selectByUserId(userId)).thenReturn(List.of(e1, e2));

        List<ConversationSession> sessions = service.listSessions(userId);
        assertEquals(2, sessions.size());
    }

    @Test
    void deleteSession_shouldCleanRedisAndDb() {
        service = new ConversationService(aiModelService, aiTeacherBaiduAsrService,
                webClientBuilder, stringRedisTemplate, sessionMapper, messageMapper,
                properties, objectMapper);

        service.deleteSession("sess_to_delete");

        verify(stringRedisTemplate).delete("user:session:sess_to_delete");
        verify(sessionMapper).logicDeleteBySessionId("sess_to_delete");
    }
}