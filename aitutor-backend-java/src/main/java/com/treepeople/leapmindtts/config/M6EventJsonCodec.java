package com.treepeople.leapmindtts.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.type.LogicalType;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.treepeople.leapmindtts.pojo.dto.profile.M6Dtos.LearningEventRequest;
import com.treepeople.leapmindtts.pojo.dto.profile.M6Dtos.FieldViolation;
import com.treepeople.leapmindtts.exception.M6ApiException;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Dedicated wrapper: its copied mapper is deliberately not an ObjectMapper bean. */
@Component
public class M6EventJsonCodec {
    private static final Pattern RFC3339_OFFSET_DATE_TIME = Pattern.compile(
            "^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(?:\\.[0-9]+)?(?:Z|[+-][0-9]{2}:[0-9]{2})$");
    private final ObjectMapper strict;
    public M6EventJsonCodec(ObjectMapper global) {
        strict = global.copy().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .enable(MapperFeature.BLOCK_UNSAFE_POLYMORPHIC_BASE_TYPES);
        strict.getFactory().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
        strict.coercionConfigFor(LogicalType.Integer).setCoercion(CoercionInputShape.String, CoercionAction.Fail);
        strict.coercionConfigFor(LogicalType.Integer).setCoercion(CoercionInputShape.Float, CoercionAction.Fail);
        strict.coercionConfigFor(LogicalType.Integer).setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
        strict.coercionConfigFor(LogicalType.Boolean).setCoercion(CoercionInputShape.Integer, CoercionAction.Fail);
        strict.coercionConfigFor(LogicalType.Boolean).setCoercion(CoercionInputShape.String, CoercionAction.Fail);
        strict.coercionConfigFor(LogicalType.Textual).setCoercion(CoercionInputShape.Integer, CoercionAction.Fail);
        strict.coercionConfigFor(LogicalType.Textual).setCoercion(CoercionInputShape.Float, CoercionAction.Fail);
        strict.coercionConfigFor(LogicalType.Textual).setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
    }
    public <T> T read(JsonNode node, Class<T> type) throws com.fasterxml.jackson.core.JsonProcessingException {
        if (type == LearningEventRequest.class) requireTextualOccurredAt(node);
        try {
            return strict.treeToValue(node, type);
        } catch (JsonMappingException mapping) {
            String candidate = mapping.getPath().isEmpty() || mapping.getPath().get(0).getFieldName() == null
                    ? null : mapping.getPath().get(0).getFieldName();
            String field = trustedEnvelopeField(candidate) ? candidate : "body";
            throw invalid(field, "INVALID");
        }
    }
    public com.fasterxml.jackson.databind.JsonNode parse(byte[] bytes) throws java.io.IOException { return strict.readTree(bytes); }

    private void requireTextualOccurredAt(JsonNode node) throws JsonMappingException {
        JsonNode occurredAt = node == null ? null : node.get("occurredAt");
        if (occurredAt == null || !occurredAt.isTextual()
                || !RFC3339_OFFSET_DATE_TIME.matcher(occurredAt.textValue()).matches()) {
            throw invalid("occurredAt", "INVALID");
        }
    }

    private M6ApiException invalid(String field, String reason) {
        return new M6ApiException(HttpStatus.BAD_REQUEST, "PROFILE_EVENT_INVALID", "Invalid request",
                List.of(new FieldViolation(field, reason)));
    }

    private boolean trustedEnvelopeField(String field) {
        return Set.of("eventId", "userId", "eventType", "sourceModule", "occurredAt", "schemaVersion",
                "sessionId", "kpId", "traceId", "data").contains(field);
    }
}
