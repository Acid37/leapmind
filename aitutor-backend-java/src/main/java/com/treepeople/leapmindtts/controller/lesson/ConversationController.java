package com.treepeople.leapmindtts.controller.lesson;

import com.treepeople.leapmindtts.pojo.dto.ConversationRequest;
import com.treepeople.leapmindtts.pojo.dto.ConversationSession;
import com.treepeople.leapmindtts.pojo.dto.ConversationRequest.SceneType;
import com.treepeople.leapmindtts.service.lesson.ConversationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/conversation")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;

    @PostMapping(value = "/ask", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @io.github.resilience4j.ratelimiter.annotation.RateLimiter(name = "userQuestionLimiter", fallbackMethod = "askRateLimitFallback")
    public Flux<ServerSentEvent<?>> ask(@RequestBody @Valid ConversationRequest request) {
        log.info("Conversation ask: userId={}, sessionId={}, sceneType={}, question={}",
                request.getUserId(), request.getSessionId(), request.getSceneType(),
                request.getQuestion() != null ? request.getQuestion().substring(0, Math.min(50, request.getQuestion().length())) : "");

        return conversationService.streamResponse(request);
    }

    /**
     * Rate Limiter的降级方法
     */
    public Flux<ServerSentEvent<?>> askRateLimitFallback(ConversationRequest request, Throwable t) {
        log.warn("Conversation API Rate limit triggered for user: {}", request.getUserId());
        return Flux.just(ServerSentEvent.<Object>builder()
                .event("message")
                .data("{\"type\":\"error\",\"message\":\"请求过于频繁，请稍后再试\"}")
                .build());
    }

    @PostMapping("/interrupt")
    public ResponseEntity<Void> interrupt(@RequestParam String sessionId) {
        log.info("Conversation interrupt: sessionId={}", sessionId);
        conversationService.interrupt(sessionId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/sessions")
    public ResponseEntity<ConversationSession> createSession(@RequestBody Map<String, Object> body) {
        Long userId = body.get("userId") != null ? Long.valueOf(body.get("userId").toString()) : null;
        String sceneTypeStr = (String) body.get("sceneType");
        SceneType sceneType = sceneTypeStr != null ? SceneType.valueOf(sceneTypeStr) : null;

        var req = new ConversationRequest();
        req.setUserId(userId);
        req.setSceneType(sceneType);

        String sessionId = conversationService.getOrCreateSessionId(req);
        var session = conversationService.getSession(sessionId);
        return ResponseEntity.ok(session);
    }

    @GetMapping("/sessions")
    public ResponseEntity<List<ConversationSession>> listSessions(@RequestParam Long userId) {
        var sessions = conversationService.listSessions(userId);
        return ResponseEntity.ok(sessions);
    }

    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<ConversationSession> getSession(@PathVariable String sessionId) {
        var session = conversationService.getSession(sessionId);
        if (session == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(session);
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Void> deleteSession(@PathVariable String sessionId) {
        conversationService.deleteSession(sessionId);
        return ResponseEntity.ok().build();
    }
}
