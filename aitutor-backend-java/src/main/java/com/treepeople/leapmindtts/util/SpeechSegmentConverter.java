package com.treepeople.leapmindtts.util;

import com.treepeople.leapmindtts.pojo.dto.SpeechSegment;
import com.treepeople.leapmindtts.pojo.entity.AudioSegment;
import com.treepeople.leapmindtts.service.lesson.VoiceDatabaseService;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * SpeechSegment到AudioSegment的转换工具类
 */
@Slf4j
public class SpeechSegmentConverter {

    /**
     * 将SpeechSegment转换为AudioSegment
     *
     * @param courseId 会话ID
     * @param segment 语音片段DTO
     * @return AudioSegment实体对象
     */
    public static AudioSegment toAudioSegment(String courseId, SpeechSegment segment) {
        if (segment == null) {
            log.warn("SpeechSegment为空，无法转换");
            return null;
        }

        if (courseId == null || courseId.trim().isEmpty()) {
            log.warn("会话ID为空，无法转换SpeechSegment");
            return null;
        }

        try {
            return AudioSegment.builder()
                    .courseId(courseId)
                    .segmentIndex(segment.getSegmentIndex())
                    .textContent(segment.getText())
                    .audioData(segment.getAudioData())
                    .audioSize(segment.getAudioData() != null ? (long) segment.getAudioData().length : 0L)
                    .duration(segment.getDuration())
                    .audioFormat(segment.getAudioFormat())
                    .sampleRate(segment.getSampleRate())
                    .checksum(segment.getChecksum())
                    .createdAt(segment.getCreatedAt() != null ? segment.getCreatedAt() : LocalDateTime.now())
                    .build();
        } catch (Exception e) {
            log.error("转换SpeechSegment到AudioSegment时发生错误，会话ID: {}, 片段索引: {}",
                    courseId, segment.getSegmentIndex(), e);
            return null;
        }
    }

    /**
     * 批量将SpeechSegment列表转换为AudioSegment列表
     *
     * @param courseId 会话ID
     * @param segments 语音片段DTO列表
     * @return AudioSegment实体对象列表
     */
    public static List<AudioSegment> toAudioSegmentList(String courseId, List<SpeechSegment> segments) {
        List<AudioSegment> audioSegments = new ArrayList<>();

        if (segments == null || segments.isEmpty()) {
            log.debug("SpeechSegment列表为空，返回空的AudioSegment列表");
            return audioSegments;
        }

        if (courseId == null || courseId.trim().isEmpty()) {
            log.warn("会话ID为空，无法批量转换SpeechSegment列表");
            return audioSegments;
        }

        for (SpeechSegment segment : segments) {
            AudioSegment audioSegment = toAudioSegment(courseId, segment);
            if (audioSegment != null) {
                audioSegments.add(audioSegment);
            }
        }

        log.debug("批量转换完成，会话ID: {}, 原始数量: {}, 转换成功数量: {}",
                courseId, segments.size(), audioSegments.size());

        return audioSegments;
    }

    /**
     * 验证SpeechSegment数据的完整性
     *
     * @param segment 语音片段DTO
     * @return 如果数据完整返回true
     */
    public static boolean isValidSpeechSegment(SpeechSegment segment) {
        if (segment == null) {
            return false;
        }

        // 检查必要字段
        if (segment.getText() == null || segment.getText().trim().isEmpty()) {
            log.debug("SpeechSegment文本内容为空，片段索引: {}", segment.getSegmentIndex());
            return false;
        }

        if (segment.getAudioData() == null || segment.getAudioData().length == 0) {
            log.debug("SpeechSegment音频数据为空，片段索引: {}", segment.getSegmentIndex());
            return false;
        }

        if (segment.getSegmentIndex() < 0) {
            log.debug("SpeechSegment片段索引无效: {}", segment.getSegmentIndex());
            return false;
        }

        return true;
    }

    /**
     * 安全地保存音频片段到数据库，包含完整的错误处理
     *
     * @param voiceDatabaseService 数据库服务
     * @param courseId 会话ID
     * @param segment 语音片段
     * @param context 上下文信息（用于日志）
     */
    public static void safelySaveToDatabase(VoiceDatabaseService voiceDatabaseService,
                                            String courseId, SpeechSegment segment, String context) {
        try {
            if (!isValidSpeechSegment(segment)) {
                log.warn("{}：音频片段数据无效，跳过数据库保存，会话ID: {}, 片段索引: {}",
                        context, courseId, segment != null ? segment.getSegmentIndex() : "null");
                return;
            }

            // 确保会话存在，如果不存在则创建
            ensureSessionExists(voiceDatabaseService, courseId, context);

            try {
                boolean saved = voiceDatabaseService.addAudioSegment(
                    courseId,
                    segment.getSegmentIndex(),
                    segment.getText(),
                    segment.getAudioData(),
                    segment.getAudioFormat(),
                    segment.getSampleRate()
                );

                if (saved) {
                    log.debug("{}：音频片段已保存到数据库，会话ID: {}, 片段索引: {}",
                            context, courseId, segment.getSegmentIndex());
                } else {
                    log.warn("{}：音频片段保存到数据库失败，会话ID: {}, 片段索引: {}",
                            context, courseId, segment.getSegmentIndex());
                }
            } catch (org.springframework.dao.DuplicateKeyException e) {
                // 音频片段已存在，这在并发情况下是正常的
                log.debug("{}：音频片段已存在于数据库，会话ID: {}, 片段索引: {}",
                        context, courseId, segment.getSegmentIndex());
            } catch (Exception e) {
                // 检查是否是重复键异常
                if (e.getCause() instanceof java.sql.SQLIntegrityConstraintViolationException &&
                    e.getMessage().contains("Duplicate entry")) {
                    log.debug("{}：音频片段已存在于数据库（并发），会话ID: {}, 片段索引: {}",
                            context, courseId, segment.getSegmentIndex());
                } else {
                    log.warn("{}：音频片段保存到数据库失败，会话ID: {}, 片段索引: {}",
                            context, courseId, segment.getSegmentIndex());
                    throw e;
                }
            }
        } catch (Exception e) {
            // 数据库保存失败不应该影响音频播放功能
            log.error("{}：保存音频片段到数据库时发生异常，会话ID: {}, 片段索引: {}，但不影响音频播放功能",
                    context, courseId, segment != null ? segment.getSegmentIndex() : "null", e);
        }
    }

    /**
     * 确保会话存在，如果不存在则创建
     *
     * @param voiceDatabaseService 数据库服务
     * @param courseId 会话ID
     * @param context 上下文信息
     */
    private static void ensureSessionExists(VoiceDatabaseService voiceDatabaseService,
                                          String courseId, String context) {
        try {
            // 检查会话是否存在
            if (voiceDatabaseService.getCompleteSessionInfo(courseId) == null) {
                log.debug("{}：会话不存在，创建新会话，会话ID: {}", context, courseId);

                try {
                    // 创建会话记录，使用默认值
                    boolean created = voiceDatabaseService.createCompleteSession(
                        courseId,
                        "AI讲课会话", // 默认标题
                        "", // 原始文本（暂时为空）
                        "", // 润色文本（暂时为空）
                        new java.util.ArrayList<>() // 空的音频片段列表
                    );

                    if (created) {
                        log.debug("{}：会话创建成功，会话ID: {}", context, courseId);
                    } else {
                        log.warn("{}：会话创建失败，会话ID: {}", context, courseId);
                    }
                } catch (org.springframework.dao.DuplicateKeyException e) {
                    // 并发情况下，另一个线程可能已经创建了会话，这是正常的
                    log.debug("{}：会话已被其他线程创建，会话ID: {}", context, courseId);
                } catch (Exception e) {
                    // 检查是否是重复键异常
                    if (e.getCause() instanceof java.sql.SQLIntegrityConstraintViolationException &&
                        e.getMessage().contains("Duplicate entry")) {
                        log.debug("{}：会话已被其他线程创建（并发），会话ID: {}", context, courseId);
                    } else {
                        log.warn("{}：创建会话时发生其他异常，会话ID: {}", context, courseId, e);
                        throw e;
                    }
                }
            } else {
                log.debug("{}：会话已存在，会话ID: {}", context, courseId);
            }
        } catch (Exception e) {
            // 如果是重复键异常，说明会话已存在，不需要抛出异常
            if (e instanceof org.springframework.dao.DuplicateKeyException ||
                (e.getCause() instanceof java.sql.SQLIntegrityConstraintViolationException &&
                 e.getMessage().contains("Duplicate entry"))) {
                log.debug("{}：会话已存在（并发创建），会话ID: {}", context, courseId);
                return; // 正常返回，不抛出异常
            }

            log.warn("{}：检查或创建会话时发生异常，会话ID: {}，将跳过数据库保存", context, courseId, e);
            // 重新抛出异常，让上层方法知道会话创建失败
            throw new RuntimeException("会话处理失败", e);
        }
    }
}
