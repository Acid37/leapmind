package com.treepeople.leapmindtts.service.lesson;

import com.treepeople.leapmindtts.config.SegmentedSpeechProperties;
import com.treepeople.leapmindtts.pojo.dto.SpeechSegment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 语音片段缓存管理器
 * 实现LRU缓存策略、缓存命中率统计和监控
 */
@Slf4j
@Service
public class SegmentCacheManager {

    @Autowired
    private SegmentedSpeechProperties properties;

    /**
     * 会话级别的LRU缓存
     */
    private final ConcurrentMap<String, LRUCache<Integer, SpeechSegment>> sessionCaches = new ConcurrentHashMap<>();

    /**
     * 缓存统计信息
     */
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);
    private final AtomicLong totalCacheRequests = new AtomicLong(0);

    @PostConstruct
    public void init() {
        log.info("SegmentCacheManager initialized with cache size: {}",
                properties.getCache().getSegmentCacheSize());
    }



    /**
     * 缓存语音片段
     */
    public void putSegment(String courseId, SpeechSegment segment) {
        if (segment == null || !segment.isValid()) {
            log.warn("Attempted to cache invalid segment for session: {}", courseId);
            return;
        }

        LRUCache<Integer, SpeechSegment> cache = sessionCaches.computeIfAbsent(courseId,
            k -> new LRUCache<>(properties.getCache().getSegmentCacheSize()));

        cache.put(segment.getSegmentIndex(), segment);

        log.debug("Cached segment for session: {}, index: {}, cache size: {}",
                courseId, segment.getSegmentIndex(), cache.size());
    }

    /**
     * 检查片段是否在缓存中
     */
    public boolean containsSegment(String courseId, int segmentIndex) {
        LRUCache<Integer, SpeechSegment> cache = sessionCaches.get(courseId);
        return cache != null && cache.containsKey(segmentIndex);
    }


    /**
     * 获取缓存命中率
     */
    public double getCacheHitRate() {
        long hits = cacheHits.get();
        long total = totalCacheRequests.get();
        return total > 0 ? (double) hits / total : 0.0;
    }

    /**
     * 获取会话的缓存命中率
     */
    public double getSessionCacheHitRate(String courseId) {
        // 简化实现，返回全局命中率
        return getCacheHitRate();
    }

    /**
     * 获取缓存统计信息
     */
    public CacheStatistics getGlobalCacheStatistics() {
        return CacheStatistics.builder()
                .hits(cacheHits.get())
                .misses(cacheMisses.get())
                .totalRequests(totalCacheRequests.get())
                .hitRate(getCacheHitRate())
                .activeSessions(sessionCaches.size())
                .totalCachedSegments(getTotalCachedSegments())
                .evictions(0L) // 简化实现，设为0
                .lastUpdated(LocalDateTime.now())
                .build();
    }

    /**
     * 获取会话的缓存大小
     */
    public int getSessionCacheSize(String courseId) {
        LRUCache<Integer, SpeechSegment> cache = sessionCaches.get(courseId);
        return cache != null ? cache.size() : 0;
    }

    /**
     * 获取总的缓存片段数量
     */
    private int getTotalCachedSegments() {
        return sessionCaches.values().stream()
                .mapToInt(LRUCache::size)
                .sum();
    }




    /**
     * LRU缓存实现
     */
    private static class LRUCache<K, V> extends LinkedHashMap<K, V> {
        private final int maxSize;

        public LRUCache(int maxSize) {
            super(16, 0.75f, true);
            this.maxSize = maxSize;
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
            return size() > maxSize;
        }
    }

    /**
     * 缓存统计信息数据类
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class CacheStatistics {
        private long hits;
        private long misses;
        private long totalRequests;
        private double hitRate;
        private int activeSessions;
        private int totalCachedSegments;
        private long evictions;
        private LocalDateTime lastUpdated;

        public long getEvictions() {
            return evictions;
        }
    }


}
