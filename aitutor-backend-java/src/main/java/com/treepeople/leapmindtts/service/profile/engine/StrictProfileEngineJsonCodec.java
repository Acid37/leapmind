package com.treepeople.leapmindtts.service.profile.engine;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.type.LogicalType;
import com.treepeople.leapmindtts.service.profile.platform.LearningEventCommandValidator;
import java.io.IOException;

/** Isolated mapper: unknown fields, duplicate fields, coercion and trailing tokens all fail closed. */
public final class StrictProfileEngineJsonCodec {
    private final ObjectMapper mapper;
    public StrictProfileEngineJsonCodec() {
        mapper = new ObjectMapper().findAndRegisterModules()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .enable(MapperFeature.BLOCK_UNSAFE_POLYMORPHIC_BASE_TYPES);
        mapper.getFactory().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
        mapper.coercionConfigFor(LogicalType.Integer).setCoercion(CoercionInputShape.String, CoercionAction.Fail);
        mapper.coercionConfigFor(LogicalType.Integer).setCoercion(CoercionInputShape.Float, CoercionAction.Fail);
        mapper.coercionConfigFor(LogicalType.Float).setCoercion(CoercionInputShape.String, CoercionAction.Fail);
        mapper.coercionConfigFor(LogicalType.Boolean).setCoercion(CoercionInputShape.String, CoercionAction.Fail);
        mapper.coercionConfigFor(LogicalType.Boolean).setCoercion(CoercionInputShape.Integer, CoercionAction.Fail);
        mapper.coercionConfigFor(LogicalType.Textual).setCoercion(CoercionInputShape.Integer, CoercionAction.Fail);
        mapper.coercionConfigFor(LogicalType.Textual).setCoercion(CoercionInputShape.Float, CoercionAction.Fail);
    }
    private ProfileEngineResponse readResponse(byte[] json) throws IOException {
        return mapper.readValue(json, ProfileEngineResponse.class);
    }
    public ProfileEngineResponse readAndValidateResponse(ProfileEngineRequest request, byte[] json) throws IOException {
        ProfileEngineResponse response = readResponse(json);
        ProfileEngineContractValidator.validate(request, response);
        return response;
    }
    public byte[] writeRequest(ProfileEngineRequest request) throws IOException {
        if (request == null) throw new IllegalArgumentException("profile engine request is required");
        request.events().forEach(event -> LearningEventCommandValidator.validate(event.command()));
        return mapper.writeValueAsBytes(ProfileEngineWireRequest.from(request));
    }
}
