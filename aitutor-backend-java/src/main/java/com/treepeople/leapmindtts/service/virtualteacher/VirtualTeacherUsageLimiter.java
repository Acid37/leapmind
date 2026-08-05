package com.treepeople.leapmindtts.service.virtualteacher;

import com.treepeople.leapmindtts.config.VirtualTeacherProperties;
import com.treepeople.leapmindtts.exception.TooManyRequestsException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
public class VirtualTeacherUsageLimiter {
    private final StringRedisTemplate redis;
    private final VirtualTeacherProperties properties;
    private final Map<String, LocalCounter> localCounters = new ConcurrentHashMap<>();

    public VirtualTeacherUsageLimiter(
            ObjectProvider<StringRedisTemplate> redisProvider,
            VirtualTeacherProperties properties) {
        this.redis = redisProvider.getIfAvailable();
        this.properties = properties;
    }

    public void check(Long userId, int textLength) {
        VirtualTeacherProperties.RateLimit limit = properties.getRateLimit();
        if (!limit.isEnabled()) return;

        String userKey = String.valueOf(userId);
        incrementOrReject(
                "virtual-teacher:rate:" + userKey + ":" + minuteBucket(),
                limit.getRequestsPerMinute(),
                1,
                Duration.ofMinutes(2),
                "语音合成请求过于频繁，请稍后再试");
        incrementOrReject(
                "virtual-teacher:chars:" + userKey + ":" + LocalDate.now(),
                limit.getDailyCharacters(),
                textLength,
                Duration.ofDays(2),
                "今日虚拟教师语音合成字符数已达上限");
    }

    private void incrementOrReject(String key, int limit, int amount, Duration ttl, String message) {
        if (limit <= 0) return;
        Long current = incrementRedis(key, amount, ttl);
        if (current == null) {
            current = incrementLocal(key, amount, ttl);
        }
        if (current > limit) {
            throw new TooManyRequestsException(message);
        }
    }

    private Long incrementRedis(String key, int amount, Duration ttl) {
        if (redis == null) return null;
        try {
            Long current = redis.opsForValue().increment(key, amount);
            if (current != null && current == amount) {
                redis.expire(key, ttl);
            }
            return current;
        } catch (RuntimeException error) {
            log.warn("Redis 限流不可用，使用进程内限流: {}", error.getMessage());
            return null;
        }
    }

    private long incrementLocal(String key, int amount, Duration ttl) {
        LocalDateTime now = LocalDateTime.now();
        LocalCounter counter = localCounters.compute(key, (ignored, current) -> {
            if (current == null || current.expiresAt().isBefore(now)) {
                return new LocalCounter(new AtomicInteger(amount), now.plus(ttl));
            }
            current.value.addAndGet(amount);
            return current;
        });
        return counter.value.get();
    }

    private String minuteBucket() {
        LocalDateTime now = LocalDateTime.now();
        return now.getYear() + "-" + now.getDayOfYear() + "-" + now.getHour() + "-" + now.getMinute();
    }

    private record LocalCounter(AtomicInteger value, LocalDateTime expiresAt) {
    }
}
