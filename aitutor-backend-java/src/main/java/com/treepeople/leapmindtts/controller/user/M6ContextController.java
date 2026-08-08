package com.treepeople.leapmindtts.controller.user;

import com.fasterxml.jackson.databind.JsonNode;
import com.treepeople.leapmindtts.config.M6EventJsonCodec;
import com.treepeople.leapmindtts.config.M6StrictJsonFilter;
import com.treepeople.leapmindtts.exception.M6ApiException;
import com.treepeople.leapmindtts.pojo.dto.profile.M6Dtos.EventAck;
import com.treepeople.leapmindtts.pojo.dto.profile.M6Dtos.EventResult;
import com.treepeople.leapmindtts.pojo.dto.profile.M6Dtos.FieldViolation;
import com.treepeople.leapmindtts.pojo.dto.profile.M6Dtos.LearningEventRequest;
import com.treepeople.leapmindtts.pojo.dto.profile.M6ProfileDtos.KnowledgeStatusResponse;
import com.treepeople.leapmindtts.pojo.dto.profile.M6ProfileDtos.ProfileView;
import com.treepeople.leapmindtts.pojo.dto.profile.M6ProfileDtos.SummaryView;
import com.treepeople.leapmindtts.pojo.result.ApiResponse;
import com.treepeople.leapmindtts.service.profile.UserEventService;
import com.treepeople.leapmindtts.service.profile.UserProfileQueryService;
import com.treepeople.leapmindtts.util.M6RequestIds;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Validator;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user-profile")
@RequiredArgsConstructor
public class M6ContextController {
    private final UserEventService events;
    private final UserProfileQueryService queries;
    private final M6EventJsonCodec codec;
    private final Validator validator;

    @PostMapping(value = "/{userId}/record-event", consumes = {MediaType.APPLICATION_JSON_VALUE, "application/*+json"})
    public ResponseEntity<ApiResponse<EventAck>> record(@PathVariable Long userId, @RequestBody JsonNode body,
                                                         HttpServletRequest request) throws Exception {
        requirePositiveUserId(userId);
        body = strictPayload(request, body);
        if (body == null || body.isNull() || !body.isObject()) throw invalid("Invalid request", "body", "INVALID");
        LearningEventRequest event = codec.read(body, LearningEventRequest.class);
        if (event == null) throw invalid("Invalid learning event", "body", "INVALID");
        var violations = validator.validate(event);
        if (!violations.isEmpty()) {
            throw invalid("Invalid learning event", violations.iterator().next().getPropertyPath().toString(), "INVALID");
        }
        return ok(events.record(userId, event, request), request);
    }

    @PostMapping(value = "/{userId}/batch-events", consumes = {MediaType.APPLICATION_JSON_VALUE, "application/*+json"})
    public ResponseEntity<ApiResponse<List<EventResult>>> batch(@PathVariable Long userId, @RequestBody JsonNode body,
                                                                 HttpServletRequest request) {
        requirePositiveUserId(userId);
        body = strictPayload(request, body);
        if (body == null || body.isNull() || !body.isObject() || body.size() != 1 || !body.has("events") || !body.get("events").isArray()) {
            throw invalid("Invalid request", "events", "INVALID");
        }
        List<JsonNode> items = new ArrayList<>();
        body.get("events").forEach(items::add);
        if (items.isEmpty() || items.size() > 100) {
            throw invalid("Invalid event count", "events", "INVALID");
        }
        return ok(events.batch(userId, items, request), request);
    }

    /** Keeps unsupported media types on the M6 controller so its scoped advice owns the 415 envelope. */
    @PostMapping(value = {"/{userId}/record-event", "/{userId}/batch-events"}, consumes = MediaType.ALL_VALUE)
    public void unsupportedEventMediaType(HttpServletRequest request) throws HttpMediaTypeNotSupportedException {
        throw new HttpMediaTypeNotSupportedException(request.getContentType());
    }

    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<ProfileView>> profile(@PathVariable Long userId, HttpServletRequest request) {
        requirePositiveUserId(userId);
        return ok(queries.profile(userId, request), request);
    }

    @GetMapping("/{userId}/summary")
    public ResponseEntity<ApiResponse<SummaryView>> summary(@PathVariable Long userId, @RequestParam String sceneType,
                                                             @RequestParam(required = false) Long kpId,
                                                             HttpServletRequest request) {
        requirePositiveUserId(userId);
        ResponseEntity<ApiResponse<SummaryView>> response = ok(queries.summary(userId, sceneType, kpId, request), request);
        if (!"photo_qa".equals(sceneType)) return response;
        return ResponseEntity.status(response.getStatusCode()).headers(response.getHeaders())
                .header("Deprecation", "@1784505600")
                .header("Link", "</docs/m6/user-profile-openapi.yaml>; rel=\"deprecation\"")
                .body(response.getBody());
    }

    @GetMapping("/{userId}/knowledge-status")
    public ResponseEntity<ApiResponse<KnowledgeStatusResponse>> knowledge(@PathVariable Long userId, @RequestParam List<Long> kpId,
                                                                           HttpServletRequest request) {
        requirePositiveUserId(userId);
        return ok(queries.knowledge(userId, kpId, request), request);
    }

    private <T> ResponseEntity<ApiResponse<T>> ok(T data, HttpServletRequest request) {
        String requestId = M6RequestIds.resolveOrCreate(request);
        return ResponseEntity.ok().header("X-Request-Id", requestId).body(ApiResponse.success(data));
    }

    private void requirePositiveUserId(Long userId) {
        if (userId == null || userId < 1) throw invalid("invalid user id", "userId", "INVALID");
    }

    private JsonNode strictPayload(HttpServletRequest request, JsonNode fallback) {
        Object parsed = request.getAttribute(M6StrictJsonFilter.STRICT_JSON_PAYLOAD_ATTRIBUTE);
        return parsed instanceof JsonNode node ? node : fallback;
    }

    private M6ApiException invalid(String message, String field, String reason) {
        return new M6ApiException(HttpStatus.BAD_REQUEST, "PROFILE_EVENT_INVALID", message,
                List.of(new FieldViolation(field, reason)));
    }
}
