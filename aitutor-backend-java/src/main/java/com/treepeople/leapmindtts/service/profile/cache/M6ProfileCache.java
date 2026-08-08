package com.treepeople.leapmindtts.service.profile.cache;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class M6ProfileCache {
    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final boolean enabled;

    public M6ProfileCache(StringRedisTemplate redis, ObjectMapper json,
                          @Value("${leapmind.m6.cache.enabled:false}") boolean enabled) {
        this.redis = redis;
        this.json = json;
        this.enabled = enabled;
    }

    public JsonNode get(String key, long version, String status, String statusReason, String computedAt) {
        if (!enabled) return null;
        String raw;
        try {
            raw = redis.opsForValue().get(key);
        } catch (Exception redisFailure) {
            cacheFailure("GET", key, redisFailure);
            return null;
        }
        try {
            if (raw == null) return null;
            JsonNode envelope = json.readTree(raw);
            if (!envelope.isObject() || !"1.0".equals(envelope.path("cacheSchema").asText())
                    || envelope.path("profileVersion").asLong(-1) != version
                    || !status.equals(envelope.path("profileStatus").asText())
                    || !Objects.equals(statusReason, nullableText(envelope.get("statusReason")))
                    || !computedAt.equals(envelope.path("computedAt").asText())
                    || !envelope.has("data")) {
                delete(key);
                return null;
            }
            return envelope.get("data");
        } catch (Exception ignored) {
            delete(key);
            return null;
        }
    }

    public void put(String key, long version, String status, String statusReason, String computedAt,
                    JsonNode data, boolean summary) {
        if (!enabled) return;
        final String encoded;
        try {
            var envelope = json.createObjectNode();
            envelope.put("cacheSchema", "1.0");
            envelope.put("profileVersion", version);
            envelope.put("profileStatus", status);
            if (statusReason == null) envelope.putNull("statusReason"); else envelope.put("statusReason", statusReason);
            envelope.put("computedAt", computedAt);
            envelope.set("data", data);
            encoded = json.writeValueAsString(envelope);
        } catch (Exception ignored) {
            return;
        }
        try {
            redis.opsForValue().set(key, encoded,
                    summary ? Duration.ofMinutes(10) : Duration.ofMinutes(30));
        } catch (Exception redisFailure) {
            cacheFailure("PUT", key, redisFailure);
        }
    }

    public void delete(String key) {
        if (!enabled) return;
        try { redis.delete(key); } catch (Exception redisFailure) { cacheFailure("DELETE", key, redisFailure); }
    }

    public static String profileKey(long userId) { return "user:profile:" + userId; }
    public static String summaryKey(long userId, String scene, Long kpId) {
        String canonical = "photo_qa".equals(scene) ? "explaining" : scene;
        return "user:profile:summary:" + userId + ':' + canonical + ':' + (kpId == null ? "all" : kpId);
    }

    private String nullableText(JsonNode node) { return node == null || node.isNull() ? null : node.asText(); }

    private void cacheFailure(String operation, String key, Exception failure) {
        String cacheType = key.contains(":summary:") ? "SUMMARY" : "PROFILE";
        String requestId = MDC.get("requestId");
        if (requestId == null) {
            log.warn("M6 cache operation={} cacheType={} exceptionType={}", operation, cacheType,
                    failure.getClass().getSimpleName());
            return;
        }
        log.warn("M6 cache operation={} cacheType={} exceptionType={} requestId={}", operation, cacheType,
                failure.getClass().getSimpleName(), requestId);
    }
}
