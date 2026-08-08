package com.treepeople.leapmindtts.service.common;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisCacheService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final MeterRegistry meterRegistry;

    private static final String NULL_PLACEHOLDER = "__NULL__";
    private static final Duration NULL_TTL = Duration.ofMinutes(5); // 空值防穿透拦截 5 分钟

    public String get(String cacheKey) {
        Object value = redisTemplate.opsForValue().get(cacheKey);
        if (value == null) {
            meterRegistry.counter("cache.misses", "cache", "redis").increment();
            return null;
        }

        String strValue = value.toString();
        if (NULL_PLACEHOLDER.equals(strValue)) {
            meterRegistry.counter("cache.null.hits", "cache", "redis").increment();
            // 返回一个特殊值或null，表示这是一个已知的空结果，但上层服务需要知道这一点以避免进一步处理
            return NULL_PLACEHOLDER;
        }

        meterRegistry.counter("cache.hits", "cache", "redis").increment();
        return strValue;
    }

    public void set(String cacheKey, String value, Duration ttl) {
        redisTemplate.opsForValue().set(cacheKey, value, ttl);
    }

    public void setNullPlaceholder(String cacheKey) {
        redisTemplate.opsForValue().set(cacheKey, NULL_PLACEHOLDER, NULL_TTL);
    }
}
