
 package com.treepeople.leapmindtts.service.optimize;
 
 import lombok.RequiredArgsConstructor;
 import lombok.extern.slf4j.Slf4j;
 import org.springframework.data.redis.core.RedisTemplate;
 import org.springframework.stereotype.Service;
 
 import java.time.Duration;
 
 @Slf4j
 @Service("optimizeRedisCacheService")
 @RequiredArgsConstructor
 public class RedisCacheService {
     private final RedisTemplate<String, Object> redisTemplate;
     private final MetricsService metricsService; // Refactored
 
     private static final String NULL_PLACEHOLDER = "__NULL__";
     private static final Duration NULL_TTL = Duration.ofMinutes(5); // 空值防穿透拦截 5 分钟
 
     public String get(String cacheKey) {
         Object value = redisTemplate.opsForValue().get(cacheKey);
         if (value == null) {
             metricsService.incrementCacheStatus("redis-qa", "miss");
             return null;
         }
         String strValue = value.toString();
         if (NULL_PLACEHOLDER.equals(strValue)) {
             metricsService.incrementCacheStatus("redis-qa", "null_hit");
             return null;
         }
         metricsService.incrementCacheStatus("redis-qa", "hit");
         return strValue;
     }
 
     public void set(String cacheKey, String value, Duration ttl) {
         redisTemplate.opsForValue().set(cacheKey, value, ttl);
     }
 
     public void setNullPlaceholder(String cacheKey) {
         redisTemplate.opsForValue().set(cacheKey, NULL_PLACEHOLDER, NULL_TTL);
     }
 }
