package com.treepeople.leapmindtts.service.virtualteacher;

import com.treepeople.leapmindtts.config.VirtualTeacherProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class VirtualTeacherTtsCache {
    private final StringRedisTemplate redis;
    private final VirtualTeacherProperties properties;
    private final Map<String, LocalEntry> localFallback = new ConcurrentHashMap<>();

    public VirtualTeacherTtsCache(
            ObjectProvider<StringRedisTemplate> redisProvider,
            VirtualTeacherProperties properties) {
        this.redis = redisProvider.getIfAvailable();
        this.properties = properties;
    }

    public Optional<String> get(String key) {
        if (redis != null) {
            try {
                String value = redis.opsForValue().get(key);
                if (value != null) return Optional.of(value);
            } catch (RuntimeException error) {
                log.warn("Redis 不可用，使用进程内 TTS 缓存: {}", error.getMessage());
            }
        }
        LocalEntry entry = localFallback.get(key);
        if (entry == null || entry.expiresAt().isBefore(Instant.now())) {
            localFallback.remove(key);
            return Optional.empty();
        }
        return Optional.of(entry.objectKey());
    }

    public void put(String key, String objectKey) {
        if (redis != null) {
            try {
                redis.opsForValue().set(key, objectKey, properties.getCacheTtl());
            } catch (RuntimeException error) {
                log.warn("写入 Redis 失败，保留进程内缓存: {}", error.getMessage());
            }
        }
        localFallback.put(key, new LocalEntry(
                objectKey,
                Instant.now().plus(properties.getCacheTtl())));
    }

    private record LocalEntry(String objectKey, Instant expiresAt) {
    }
}
