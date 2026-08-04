package com.treepeople.leapmindtts.service.lesson;

import com.treepeople.leapmindtts.config.SegmentedSpeechProperties;
import com.treepeople.leapmindtts.pojo.dto.PlaybackProgress;
import com.treepeople.leapmindtts.pojo.dto.PlaybackState;
import com.treepeople.leapmindtts.pojo.dto.SpeechSegment;
import com.treepeople.leapmindtts.pojo.enums.PlaybackStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 播放状态管理器
 * 负责管理语音片段存储和播放状态，提供会话级别的数据隔离和并发安全机制
 */
@Slf4j
@Service
public class PlaybackStateManager {

    @Autowired
    private SegmentedSpeechProperties properties;



    /**
     * 会话级别的语音片段存储
     * Key: courseId, Value: 片段索引到语音片段的映射
     */
    private final ConcurrentMap<String, ConcurrentMap<Integer, SpeechSegment>> sessionSegments = new ConcurrentHashMap<>();

    /**
     * 会话级别的播放状态存储
     * Key: courseId, Value: 播放状态
     */
    private final ConcurrentMap<String, PlaybackState> sessionStates = new ConcurrentHashMap<>();

    /**
     * 会话级别的读写锁，确保并发安全
     * Key: courseId, Value: 读写锁
     */
    private final ConcurrentMap<String, ReentrantReadWriteLock> sessionLocks = new ConcurrentHashMap<>();

    /**
     * 会话最后访问时间记录，用于自动清理
     * Key: courseId, Value: 最后访问时间
     */
    private final ConcurrentMap<String, LocalDateTime> sessionLastAccess = new ConcurrentHashMap<>();

    /**
     * 定时清理任务执行器
     */
    private ScheduledExecutorService cleanupExecutor;

    @PostConstruct
    public void init() {
        if (properties.getMemory().isAutoCleanupEnabled()) {
            cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "PlaybackStateManager-Cleanup");
                t.setDaemon(true);
                return t;
            });

            long intervalMinutes = properties.getMemory().getCleanupInterval().toMinutes();
            cleanupExecutor.scheduleAtFixedRate(this::performAutomaticCleanup,
                intervalMinutes, intervalMinutes, TimeUnit.MINUTES);

            log.info("PlaybackStateManager initialized with automatic cleanup every {} minutes", intervalMinutes);
        }
    }

    @PreDestroy
    public void destroy() {
        if (cleanupExecutor != null && !cleanupExecutor.isShutdown()) {
            cleanupExecutor.shutdown();
            try {
                if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    cleanupExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                cleanupExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        log.info("PlaybackStateManager destroyed");
    }

    /**
     * 存储语音片段到内存缓存
     *
     * @param courseId 会话ID
     * @param segment 语音片段
     * @return 存储操作的Mono
     */
    public Mono<Void> storeSpeechSegment(String courseId, SpeechSegment segment) {
        return Mono.<Void>fromRunnable(() -> {
            if (courseId == null || courseId.trim().isEmpty()) {
                throw new IllegalArgumentException("Session ID cannot be null or empty");
            }
            if (segment == null) {
                throw new IllegalArgumentException("Speech segment cannot be null");
            }

            // 对于失败的片段，我们仍然需要存储以保持索引一致性
            if (!segment.isValid() && !"FAILED".equals(segment.getStatus())) {
                throw new IllegalArgumentException("Speech segment is not valid");
            }

            ReentrantReadWriteLock lock = getSessionLock(courseId);
            lock.writeLock().lock();
            try {
                // 获取或创建会话的片段存储
                ConcurrentMap<Integer, SpeechSegment> segments = sessionSegments.computeIfAbsent(
                    courseId, k -> new ConcurrentHashMap<>());

                // 存储片段到内存缓存
                segments.put(segment.getSegmentIndex(), segment);

                // 更新最后访问时间
                updateLastAccessTime(courseId);

                log.debug("Stored speech segment for session: {}, index: {}, text length: {}, audio size: {} bytes",
                    courseId, segment.getSegmentIndex(), segment.getTextLength(), segment.getAudioSize());

            } finally {
                lock.writeLock().unlock();
            }
        }).doOnError(error -> {
            log.error("Failed to store speech segment for session: {}, segment index: {}",
                courseId, segment != null ? segment.getSegmentIndex() : "null", error);
        });
    }

    /**
     * 获取指定的语音片段
     *
     * @param courseId 会话ID
     * @param segmentIndex 片段索引
     * @return 语音片段的Mono，如果不存在则返回empty
     */
    public Mono<SpeechSegment> getSegment(String courseId, int segmentIndex) {
        return Mono.fromCallable(() -> {
            if (courseId == null || courseId.trim().isEmpty()) {
                throw new IllegalArgumentException("Session ID cannot be null or empty");
            }
            if (segmentIndex < 0) {
                throw new IllegalArgumentException("Segment index cannot be negative");
            }

            ReentrantReadWriteLock lock = getSessionLock(courseId);
            lock.readLock().lock();
            try {
                ConcurrentMap<Integer, SpeechSegment> segments = sessionSegments.get(courseId);
                if (segments == null) {
                    log.debug("No segments found for session: {}", courseId);
                    return null;
                }

                SpeechSegment segment = segments.get(segmentIndex);
                if (segment != null) {
                    updateLastAccessTime(courseId);
                    log.debug("Retrieved speech segment for session: {}, index: {}", courseId, segmentIndex);
                } else {
                    log.debug("Speech segment not found for session: {}, index: {}", courseId, segmentIndex);
                }

                return segment;
            } finally {
                lock.readLock().unlock();
            }
        }).doOnError(error -> {
            log.error("Failed to get speech segment for session: {}, index: {}", courseId, segmentIndex, error);
        });
    }

    /**
     * 获取从指定位置开始的所有语音片段
     *
     * @param courseId 会话ID
     * @param startIndex 起始片段索引
     * @return 语音片段流
     */
    public Flux<SpeechSegment> getSegmentsFrom(String courseId, int startIndex) {
        return Mono.fromCallable(() -> {
            if (courseId == null || courseId.trim().isEmpty()) {
                throw new IllegalArgumentException("Session ID cannot be null or empty");
            }
            if (startIndex < 0) {
                throw new IllegalArgumentException("Start index cannot be negative");
            }

            ReentrantReadWriteLock lock = getSessionLock(courseId);
            lock.readLock().lock();
            try {
                ConcurrentMap<Integer, SpeechSegment> segments = sessionSegments.get(courseId);
                if (segments == null || segments.isEmpty()) {
                    log.debug("No segments found for session: {}", courseId);
                    return Collections.<SpeechSegment>emptyList();
                }

                // 获取所有索引大于等于startIndex的片段，按索引排序
                List<SpeechSegment> result = new ArrayList<>();
                segments.entrySet().stream()
                    .filter(entry -> entry.getKey() >= startIndex)
                    .sorted((e1, e2) -> Integer.compare(e1.getKey(), e2.getKey()))
                    .forEach(entry -> result.add(entry.getValue()));

                updateLastAccessTime(courseId);
                log.debug("Retrieved {} segments from index {} for session: {}", result.size(), startIndex, courseId);

                return result;
            } finally {
                lock.readLock().unlock();
            }
        }).flatMapMany(Flux::fromIterable)
        .doOnError(error -> {
            log.error("Failed to get segments from index {} for session: {}", startIndex, courseId, error);
        });
    }

    /**
     * 保存播放位置
     *
     * @param courseId 会话ID
     * @param segmentIndex 当前播放的片段索引
     * @return 保存操作的Mono
     */
    public Mono<Void> savePlaybackPosition(String courseId, int segmentIndex) {
        return Mono.<Void>fromRunnable(() -> {
            if (courseId == null || courseId.trim().isEmpty()) {
                throw new IllegalArgumentException("Session ID cannot be null or empty");
            }
            if (segmentIndex < 0) {
                throw new IllegalArgumentException("Segment index cannot be negative");
            }

            ReentrantReadWriteLock lock = getSessionLock(courseId);
            lock.writeLock().lock();
            try {
                PlaybackState state = sessionStates.computeIfAbsent(courseId, k ->
                    PlaybackState.builder()
                        .courseId(courseId)
                        .currentSegmentIndex(0)
                        .totalSegments(0)
                        .status(PlaybackStatus.NOT_STARTED)
                        .playedDuration(0L)
                        .totalDuration(0L)
                        .playbackSpeed(1.0)
                        .isLooping(false)
                        .build()
                );

                state.setCurrentSegmentIndex(segmentIndex);
                state.updateLastUpdated();
                updateLastAccessTime(courseId);

                log.debug("Saved playback position for session: {}, segment index: {}", courseId, segmentIndex);

            } finally {
                lock.writeLock().unlock();
            }
        }).doOnError(error -> {
            log.error("Failed to save playback position for session: {}, index: {}", courseId, segmentIndex, error);
        });
    }

    /**
     * 获取当前播放位置
     *
     * @param courseId 会话ID
     * @return 当前播放片段索引的Mono，如果会话不存在则返回0
     */
    public Mono<Integer> getCurrentPlaybackPosition(String courseId) {
        return Mono.fromCallable(() -> {
            if (courseId == null || courseId.trim().isEmpty()) {
                throw new IllegalArgumentException("Session ID cannot be null or empty");
            }

            ReentrantReadWriteLock lock = getSessionLock(courseId);
            lock.readLock().lock();
            try {
                PlaybackState state = sessionStates.get(courseId);
                int position = state != null ? state.getCurrentSegmentIndex() : 0;

                if (state != null) {
                    updateLastAccessTime(courseId);
                }

                log.debug("Retrieved playback position for session: {}, position: {}", courseId, position);
                return position;

            } finally {
                lock.readLock().unlock();
            }
        }).doOnError(error -> {
            log.error("Failed to get playback position for session: {}", courseId, error);
        });
    }

    /**
     * 获取播放状态
     *
     * @param courseId 会话ID
     * @return 播放状态的Mono，如果会话不存在则返回empty
     */
    public Mono<PlaybackState> getPlaybackState(String courseId) {
        return Mono.fromCallable(() -> {
            if (courseId == null || courseId.trim().isEmpty()) {
                throw new IllegalArgumentException("Session ID cannot be null or empty");
            }

            ReentrantReadWriteLock lock = getSessionLock(courseId);
            lock.readLock().lock();
            try {
                PlaybackState state = sessionStates.get(courseId);
                if (state != null) {
                    updateLastAccessTime(courseId);
                    log.debug("Retrieved playback state for session: {}, status: {}, current segment: {}",
                        courseId, state.getStatus(), state.getCurrentSegmentIndex());
                } else {
                    log.debug("No playback state found for session: {}", courseId);
                }

                return state;
            } finally {
                lock.readLock().unlock();
            }
        }).doOnError(error -> {
            log.error("Failed to get playback state for session: {}", courseId, error);
        });
    }

    /**
     * 更新播放状态
     *
     * @param courseId 会话ID
     * @param status 新的播放状态
     * @return 更新操作的Mono
     */
    public Mono<Void> updatePlaybackStatus(String courseId, PlaybackStatus status) {
        return Mono.<Void>fromRunnable(() -> {
            if (courseId == null || courseId.trim().isEmpty()) {
                throw new IllegalArgumentException("Session ID cannot be null or empty");
            }
            if (status == null) {
                throw new IllegalArgumentException("Playback status cannot be null");
            }

            ReentrantReadWriteLock lock = getSessionLock(courseId);
            lock.writeLock().lock();
            try {
                PlaybackState state = sessionStates.get(courseId);
                if (state != null) {
                    state.setStatus(status);
                    state.updateLastUpdated();

                    // 根据状态更新相关时间戳
                    LocalDateTime now = LocalDateTime.now();
                    switch (status) {
                        case PLAYING:
                            if (state.getPlaybackStartTime() == null) {
                                state.setPlaybackStartTime(now);
                            }
                            state.setPausedTime(null);
                            break;
                        case PAUSED:
                            state.setPausedTime(now);
                            break;
                        case COMPLETED:
                            state.setCompletedTime(now);
                            break;
                        case ERROR:
                            // 错误状态不需要特殊处理时间戳
                            break;
                    }

                    updateLastAccessTime(courseId);
                    log.debug("Updated playback status for session: {}, new status: {}", courseId, status);
                } else {
                    log.warn("Cannot update status for non-existent session: {}", courseId);
                }

            } finally {
                lock.writeLock().unlock();
            }
        }).doOnError(error -> {
            log.error("Failed to update playback status for session: {}, status: {}", courseId, status, error);
        });
    }

    /**
     * 保存完整的播放状态
     *
     * @param playbackState 播放状态对象
     * @return 保存操作的Mono
     */
    public Mono<Void> savePlaybackState(PlaybackState playbackState) {
        return Mono.<Void>fromRunnable(() -> {
            if (playbackState == null) {
                throw new IllegalArgumentException("Playback state cannot be null");
            }
            if (playbackState.getCourseId() == null || playbackState.getCourseId().trim().isEmpty()) {
                throw new IllegalArgumentException("Session ID in playback state cannot be null or empty");
            }

            String courseId = playbackState.getCourseId();
            ReentrantReadWriteLock lock = getSessionLock(courseId);
            lock.writeLock().lock();
            try {
                playbackState.updateLastUpdated();
                sessionStates.put(courseId, playbackState);
                updateLastAccessTime(courseId);

                log.debug("Saved playback state for session: {}, status: {}, current segment: {}",
                    courseId, playbackState.getStatus(), playbackState.getCurrentSegmentIndex());

            } finally {
                lock.writeLock().unlock();
            }
        }).doOnError(error -> {
            log.error("Failed to save playback state for session: {}",
                playbackState != null ? playbackState.getCourseId() : "null", error);
        });
    }

    /**
     * 清理指定会话的数据
     *
     * @param courseId 会话ID
     * @return 清理操作的Mono
     */
    /**
     * 获取播放进度
     *
     * @param courseId 会话ID
     * @return 播放进度的Mono
     */
    public Mono<PlaybackProgress> getPlaybackProgress(String courseId) {
        return Mono.fromCallable(() -> {
            if (courseId == null || courseId.trim().isEmpty()) {
                log.warn("获取播放进度时会话ID为空");
                return PlaybackProgress.builder()
                        .currentSegment(0)
                        .totalSegments(0)
                        .progressPercentage(0.0)
                        .currentText("")
                        .remainingDuration(0L)
                        .status(PlaybackStatus.NOT_STARTED)
                        .build();
            }

            ReentrantReadWriteLock.ReadLock readLock = getSessionLock(courseId).readLock();
            readLock.lock();
            try {
                PlaybackState state = sessionStates.get(courseId);
                if (state == null) {
                    log.warn("会话 {} 的播放状态不存在", courseId);
                    return PlaybackProgress.builder()
                            .currentSegment(0)
                            .totalSegments(0)
                            .progressPercentage(0.0)
                            .currentText("")
                            .remainingDuration(0L)
                            .status(PlaybackStatus.NOT_STARTED)
                            .build();
                }

                ConcurrentMap<Integer, SpeechSegment> segments = sessionSegments.get(courseId);
                int totalSegments = segments != null ? segments.size() : 0;

                double progressPercentage = totalSegments > 0 ?
                    (double) state.getCurrentSegmentIndex() / totalSegments * 100.0 : 0.0;

                String currentText = "";
                if (segments != null && state.getCurrentSegmentIndex() < totalSegments) {
                    SpeechSegment currentSegment = segments.get(state.getCurrentSegmentIndex());
                    if (currentSegment != null) {
                        currentText = currentSegment.getText();
                    }
                }

                return PlaybackProgress.builder()
                        .currentSegment(state.getCurrentSegmentIndex())
                        .totalSegments(totalSegments)
                        .progressPercentage(progressPercentage)
                        .currentText(currentText)
                        .remainingDuration(state.getRemainingDuration())
                        .status(state.getStatus())
                        .build();
            } finally {
                readLock.unlock();
            }
        })
        .doOnSuccess(progress -> log.debug("获取播放进度成功，会话ID: {}, 当前片段: {}/{}",
                courseId, progress.getCurrentSegment(), progress.getTotalSegments()))
        .doOnError(error -> log.error("获取播放进度失败，会话ID: {}", courseId, error));
    }

    public Mono<Void> cleanupSession(String courseId) {
        return Mono.<Void>fromRunnable(() -> {
            if (courseId == null || courseId.trim().isEmpty()) {
                throw new IllegalArgumentException("Session ID cannot be null or empty");
            }

            ReentrantReadWriteLock lock = getSessionLock(courseId);
            lock.writeLock().lock();
            try {
                // 清理语音片段
                ConcurrentMap<Integer, SpeechSegment> segments = sessionSegments.remove(courseId);
                int segmentCount = segments != null ? segments.size() : 0;

                // 清理播放状态
                PlaybackState state = sessionStates.remove(courseId);

                // 清理最后访问时间
                sessionLastAccess.remove(courseId);

                // 移除锁（注意：这里需要小心处理，因为可能有其他线程正在使用）
                sessionLocks.remove(courseId);

                log.info("Cleaned up session: {}, removed {} segments and playback state", courseId, segmentCount);

            } finally {
                lock.writeLock().unlock();
            }
        }).doOnError(error -> {
            log.error("Failed to cleanup session: {}", courseId, error);
        });
    }

    /**
     * 清理会话（别名方法）
     */
    public void clearSession(String courseId) {
        cleanupSession(courseId).subscribe();
    }

    /**
     * 获取会话的读写锁，如果不存在则创建
     *
     * @param courseId 会话ID
     * @return 读写锁
     */
    private ReentrantReadWriteLock getSessionLock(String courseId) {
        return sessionLocks.computeIfAbsent(courseId, k -> new ReentrantReadWriteLock());
    }

    /**
     * 更新会话的最后访问时间
     *
     * @param courseId 会话ID
     */
    private void updateLastAccessTime(String courseId) {
        sessionLastAccess.put(courseId, LocalDateTime.now());
    }

    /**
     * 执行自动清理过期会话
     */
    private void performAutomaticCleanup() {
        try {
            LocalDateTime cutoffTime = LocalDateTime.now().minus(properties.getCache().getSessionTimeout());
            List<String> expiredSessions = new ArrayList<>();

            // 找出过期的会话
            sessionLastAccess.entrySet().forEach(entry -> {
                if (entry.getValue().isBefore(cutoffTime)) {
                    expiredSessions.add(entry.getKey());
                }
            });

            // 清理过期会话
            for (String courseId : expiredSessions) {
                cleanupSession(courseId).subscribe(
                    null,
                    error -> log.error("Failed to cleanup expired session: {}", courseId, error),
                    () -> log.debug("Successfully cleaned up expired session: {}", courseId)
                );
            }

            if (!expiredSessions.isEmpty()) {
                log.info("Automatic cleanup completed, removed {} expired sessions", expiredSessions.size());
            }

        } catch (Exception e) {
            log.error("Error during automatic cleanup", e);
        }
    }

    /**
     * 获取当前活跃会话数量
     *
     * @return 活跃会话数量
     */
    public int getActiveSessionCount() {
        return sessionStates.size();
    }

    /**
     * 获取指定会话的片段数量
     *
     * @param courseId 会话ID
     * @return 片段数量
     */
    public int getSegmentCount(String courseId) {
        if (courseId == null || courseId.trim().isEmpty()) {
            return 0;
        }

        ConcurrentMap<Integer, SpeechSegment> segments = sessionSegments.get(courseId);
        return segments != null ? segments.size() : 0;
    }

    /**
     * 检查会话是否存在
     *
     * @param courseId 会话ID
     * @return 如果会话存在则返回true
     */
    public boolean sessionExists(String courseId) {
        return courseId != null && !courseId.trim().isEmpty() &&
               (sessionStates.containsKey(courseId) || sessionSegments.containsKey(courseId));
    }
}
