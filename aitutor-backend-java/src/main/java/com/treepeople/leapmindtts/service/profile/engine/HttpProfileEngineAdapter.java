package com.treepeople.leapmindtts.service.profile.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.treepeople.leapmindtts.config.PythonInternalAiProperties;
import java.io.IOException;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * 真实 Python build-profile HTTP 客户端。
 * 仅在 m6.profile-engine.enabled=true 时注册，否则由 DisabledProfileEngineAdapter 兜底。
 * 请求体沿用 Java 契约，仅顶层 userId 重命名为 Python 契约的 user_id；响应严格校验，非法结果不得覆盖旧画像。
 */
@Component
@ConditionalOnProperty(prefix = "m6.profile-engine", name = "enabled", havingValue = "true")
public final class HttpProfileEngineAdapter implements ProfileEnginePort {

    private static final ObjectMapper RENAME_MAPPER = new ObjectMapper();
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final WebClient webClient;
    private final PythonInternalAiProperties properties;
    private final StrictProfileEngineJsonCodec codec = new StrictProfileEngineJsonCodec();

    public HttpProfileEngineAdapter(WebClient.Builder webClientBuilder, PythonInternalAiProperties properties) {
        this.properties = properties;
        this.webClient = webClientBuilder.baseUrl(properties.getBaseUrl()).build();
    }

    @Override
    public ProfileEngineResponse buildProfile(ProfileEngineRequest request) {
        byte[] responseBytes = post(request);
        try {
            return codec.readAndValidateResponse(request, responseBytes);
        } catch (IOException | IllegalArgumentException e) {
            throw new ProfileEngineUnavailableException("INVALID_RESPONSE");
        }
    }

    private byte[] post(ProfileEngineRequest request) {
        byte[] body;
        try {
            body = codec.writeRequest(request);
        } catch (IOException e) {
            throw new IllegalArgumentException("profile engine request serialization failed", e);
        }
        byte[] wire = toPythonWire(body);
        try {
            byte[] response = webClient.post()
                    .uri(properties.getBuildProfilePath())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(wire)
                    .retrieve()
                    .bodyToMono(byte[].class)
                    .block(TIMEOUT);
            if (response == null) throw new ProfileEngineUnavailableException("EMPTY_RESPONSE");
            return response;
        } catch (ProfileEngineUnavailableException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new ProfileEngineUnavailableException("UNREACHABLE");
        }
    }

    private static byte[] toPythonWire(byte[] camelCase) {
        try {
            JsonNode tree = RENAME_MAPPER.readTree(camelCase);
            ObjectNode root = (ObjectNode) tree;
            JsonNode userId = root.remove("userId");
            if (userId != null) root.set("user_id", userId);
            return RENAME_MAPPER.writeValueAsBytes(root);
        } catch (IOException e) {
            throw new IllegalArgumentException("profile engine request rename failed", e);
        }
    }
}
