package com.treepeople.leapmindtts.service.lesson;

import com.treepeople.leapmindtts.config.SegmentedSpeechProperties;
import com.treepeople.leapmindtts.pojo.dto.SpeechSegment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 语音片段预加载服务
 * 实现智能预加载策略，提前合成后续几个片段
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SegmentPreloadService {

    private final SegmentedSpeechProperties properties;
    private final TextToSpeechService textToSpeechService;
    private final SegmentCacheManager cacheManager;
    private final PlaybackStateManager playbackStateManager;

    /**
     * 会话级别的预加载状态跟踪
     */
    private final ConcurrentMap<String, PreloadState> sessionPreloadStates = new ConcurrentHashMap<>();

    /**
     * 启动预加载任务
     */
    public Mono<Void> startPreloading(String courseId, List<String> sentences, int currentIndex) {
        if (!properties.getCache().isEnablePreload()) {
            log.debug("Preloading is disabled for session: {}", courseId);
            return Mono.empty();
        }

        if (sentences == null || sentences.isEmpty()) {
            log.debug("No sentences to preload for session: {}", courseId);
            return Mono.empty();
        }

        return Mono.fromRunnable(() -> {
            PreloadState state = sessionPreloadStates.computeIfAbsent(courseId,
                k -> new PreloadState(courseId));

            if (state.isPreloading()) {
                log.debug("Preloading already in progress for session: {}", courseId);
                return;
            }

            state.setPreloading(true);
            log.info("Starting preloading for session: {}, current index: {}", courseId, currentIndex);

            performPreloading(courseId, sentences, currentIndex)
                .subscribeOn(Schedulers.boundedElastic())
                .doFinally(signalType -> {
                    state.setPreloading(false);
                    log.debug("Preloading finished for session: {}, signal: {}", courseId, signalType);
                })
                .subscribe(
                    null,
                    error -> log.error("Preloading failed for session: {}", courseId, error),
                    () -> log.debug("Preloading completed successfully for session: {}", courseId)
                );
        });
    }

    /**
     * 执行预加载操作
     */
    private Mono<Void> performPreloading(String courseId, List<String> sentences, int currentIndex) {
        int preloadCount = properties.getCache().getPreloadSegments();
        int startIndex = currentIndex + 1;
        int endIndex = Math.min(startIndex + preloadCount, sentences.size());

        if (startIndex >= sentences.size()) {
            log.debug("No more segments to preload for session: {}", courseId);
            return Mono.empty();
        }

        log.debug("Preloading segments {} to {} for session: {}", startIndex, endIndex - 1, courseId);

        return Flux.range(startIndex, endIndex - startIndex)
                .flatMap(index -> preloadSingleSegment(courseId, sentences.get(index), index),
                        properties.getSynthesis().getMaxConcurrentRequests())
                .then()
                .doOnSuccess(unused -> {
                    PreloadState state = sessionPreloadStates.get(courseId);
                    if (state != null) {
                        state.updateLastPreloadTime();
                        state.incrementPreloadedCount(endIndex - startIndex);
                    }
                    log.info("Successfully preloaded {} segments for session: {}",
                            endIndex - startIndex, courseId);
                });
    }

    /**
     * 预加载单个语音片段
     */
    private Mono<SpeechSegment> preloadSingleSegment(String courseId, String sentence, int segmentIndex) {
        if (cacheManager.containsSegment(courseId, segmentIndex)) {
            log.debug("Segment {} already cached for session: {}, skipping preload", segmentIndex, courseId);
            return Mono.empty();
        }

        return playbackStateManager.getSegment(courseId, segmentIndex)
                .switchIfEmpty(
                    textToSpeechService.synthesizeSpeech(sentence)
                            .map(audioData -> SpeechSegment.builder()
                                    .segmentIndex(segmentIndex)
                                    .text(sentence)
                                    .audioData(audioData)
                                    .duration(estimateAudioDuration(audioData))
                                    .createdAt(LocalDateTime.now())
                                    .isPreloaded(true)
                                    .status("PRELOADED")
                                    .audioFormat("wav")
                                    .sampleRate(16000)
                                    .checksum(generateChecksum(audioData))
                                    .build())
                            .flatMap(segment ->
                                playbackStateManager.storeSpeechSegment(courseId, segment)
                                        .doOnSuccess(unused -> {
                                            cacheManager.putSegment(courseId, segment);
                                            log.debug("Preloaded and cached segment {} for session: {}",
                                                    segmentIndex, courseId);
                                        })
                                        .thenReturn(segment)
                            )
                            .onErrorResume(error -> {
                                log.error("Failed to preload segment {} for session: {}",
                                        segmentIndex, courseId, error);
                                return Mono.empty();
                            })
                )
                .doOnNext(segment -> {
                    if (segment != null) {
                        cacheManager.putSegment(courseId, segment);

                        PreloadState state = sessionPreloadStates.get(courseId);
                        if (state != null) {
                            state.setLastPreloadedIndex(segmentIndex);
                        }
                    }
                });
    }



    /**
     * 获取预加载状态
     */
    public PreloadState getPreloadState(String courseId) {
        return sessionPreloadStates.get(courseId);
    }


    /**
     * 估算音频时长
     */
    private long estimateAudioDuration(byte[] audioData) {
        if (audioData == null || audioData.length <= 44) {
            return 0;
        }
        int dataSize = audioData.length - 44;
        return (long) (dataSize / (16000.0 * 2)) * 1000;
    }

    /**
     * 生成音频数据校验和
     */
    private String generateChecksum(byte[] audioData) {
        if (audioData == null) return "";
        return String.valueOf(audioData.length);
    }

    /**
     * 预加载状态数据类
     */
    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class PreloadState {
        private String courseId;
        private AtomicBoolean preloading = new AtomicBoolean(false);
        private LocalDateTime lastPreloadTime;
        private int lastPreloadedIndex = -1;
        private int totalPreloadedCount = 0;

        public PreloadState(String courseId) {
            this.courseId = courseId;
        }

        public boolean isPreloading() {
            return preloading.get();
        }

        public void setPreloading(boolean preloading) {
            this.preloading.set(preloading);
        }

        public void updateLastPreloadTime() {
            this.lastPreloadTime = LocalDateTime.now();
        }

        public void incrementPreloadedCount(int count) {
            this.totalPreloadedCount += count;
        }
    }
}
