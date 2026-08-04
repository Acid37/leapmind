package com.treepeople.leapmindtts.service.lesson.impl;

import com.treepeople.leapmindtts.pojo.entity.AudioSegment;
import com.treepeople.leapmindtts.pojo.entity.LessonSession;
import com.treepeople.leapmindtts.pojo.entity.StudentQuestion;
import com.treepeople.leapmindtts.service.admin.AudioSegmentService;
import com.treepeople.leapmindtts.service.admin.LessonSessionService;
import com.treepeople.leapmindtts.service.lesson.StudentQuestionService;
import com.treepeople.leapmindtts.service.lesson.VoiceDatabaseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 语音数据库综合服务实现类
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VoiceDatabaseServiceImpl implements VoiceDatabaseService {

    private final LessonSessionService lessonSessionService;
    private final AudioSegmentService audioSegmentService;
    private final StudentQuestionService studentQuestionService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean createCompleteSession(String courseId, String title, String originalText,
                                       String polishedText, List<AudioSegment> segments) {
        try {
            // 1. 创建会话记录
            boolean sessionCreated = lessonSessionService.createSession(courseId, title, originalText, polishedText);
            if (!sessionCreated) {
                log.error("创建会话记录失败，会话ID: {}", courseId);
                return false;
            }

            // 2. 批量保存音频片段
            if (segments != null && !segments.isEmpty()) {
                // 设置会话ID
                segments.forEach(segment -> segment.setCourseId(courseId));

                boolean segmentsSaved = audioSegmentService.saveBatchAudioSegments(segments);
                if (!segmentsSaved) {
                    log.error("批量保存音频片段失败，会话ID: {}", courseId);
                    throw new RuntimeException("保存音频片段失败");
                }

                // 3. 更新会话统计信息
                Long totalDuration = audioSegmentService.getTotalDuration(courseId);
                boolean statsUpdated = lessonSessionService.updateSessionStats(courseId, segments.size(), totalDuration);
                if (!statsUpdated) {
                    log.warn("更新会话统计信息失败，会话ID: {}", courseId);
                }
            }

            log.info("创建完整会话成功，会话ID: {}, 音频片段数: {}", courseId,
                    segments != null ? segments.size() : 0);
            return true;

        } catch (Exception e) {
            log.error("创建完整会话失败，会话ID: {}", courseId, e);
            throw e;
        }
    }

    @Override
    public LessonSession getCompleteSessionInfo(String courseId) {
        LessonSession session = lessonSessionService.getByCourseId(courseId);
        if (session != null) {
            // 更新实时统计信息
            int segmentCount = audioSegmentService.countByCourseId(courseId);
            Long totalDuration = audioSegmentService.getTotalDuration(courseId);

            session.setTotalSegments(segmentCount);
            session.setTotalDuration(totalDuration);

            log.debug("获取完整会话信息，会话ID: {}, 片段数: {}, 总时长: {}ms",
                    courseId, segmentCount, totalDuration);
        }
        return session;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteCompleteSession(String courseId) {
        return lessonSessionService.deleteSessionCompletely(courseId);
    }

    @Override
    public List<AudioSegment> getSessionAudioSegments(String courseId) {
        return audioSegmentService.getByCourseId(courseId);
    }

    @Override
    public byte[] getSegmentAudioData(String courseId, Integer segmentIndex) {
        AudioSegment segment = audioSegmentService.getBySessionAndIndex(courseId, segmentIndex);
        return segment != null ? segment.getAudioData() : null;
    }

    @Override
    public boolean addAudioSegment(String courseId, Integer segmentIndex, String textContent,
                                 byte[] audioData, String audioFormat, Integer sampleRate) {
        boolean result = audioSegmentService.saveAudioSegment(courseId, segmentIndex, textContent,
                audioData, audioFormat, sampleRate);

        if (result) {
            // 更新会话统计信息
            updateSessionStatistics(courseId);
        }

        return result;
    }

//    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean addBatchAudioSegments(String courseId, List<AudioSegment> segments) {
        // 设置会话ID
        segments.forEach(segment -> segment.setCourseId(courseId));

        boolean result = audioSegmentService.saveBatchAudioSegments(segments);

        if (result) {
            // 更新会话统计信息
            updateSessionStatistics(courseId);
        }

        return result;
    }

    //@Override
    public boolean recordTextQuestion(String courseId, Integer segmentIndex,
                                    String questionText, String answerText) {
        return studentQuestionService.saveTextQuestion(courseId, segmentIndex, questionText, answerText);
    }

    //@Override
    public boolean recordVoiceQuestion(String courseId, Integer segmentIndex, String questionText,
                                     String answerText, byte[] questionAudio, byte[] answerAudio) {
        return studentQuestionService.saveVoiceQuestion(courseId, segmentIndex, questionText,
                answerText, questionAudio, answerAudio);
    }

   // @Override
    public List<StudentQuestion> getSessionQuestions(String courseId) {
        return studentQuestionService.getByCourseId(courseId);
    }

    //@Override
    public List<StudentQuestion> getSegmentQuestions(String courseId, Integer segmentIndex) {
        return studentQuestionService.getBySessionAndSegment(courseId, segmentIndex);
    }

    //@Override
    public SessionStatistics getSessionStatistics(String courseId) {
        // 获取音频片段统计
        int totalSegments = audioSegmentService.countByCourseId(courseId);
        Long totalDuration = audioSegmentService.getTotalDuration(courseId);
        Long totalAudioSize = audioSegmentService.getTotalAudioSize(courseId);

        // 获取提问统计
        int totalQuestions = studentQuestionService.countByCourseId(courseId);
        List<StudentQuestion> textQuestions = studentQuestionService.getByQuestionType(courseId, "TEXT");
        List<StudentQuestion> voiceQuestions = studentQuestionService.getByQuestionType(courseId, "VOICE");

        return new SessionStatistics(
                courseId,
                totalSegments,
                totalDuration != null ? totalDuration : 0L,
                totalAudioSize != null ? totalAudioSize : 0L,
                totalQuestions,
                textQuestions.size(),
                voiceQuestions.size()
        );
    }

    // ========== PPT音频片段管理实现 ==========

    //@Override
    public boolean savePPTAudioSegment(String courseId, Integer slidePageNumber, String slideTitle,
                                      Integer contentPointIndex, Integer globalSegmentIndex, String slideType,
                                      String slideDescription, String originalText, String polishedText,
                                      String finalText, byte[] audioData, String audioFormat,
                                      Integer sampleRate, Long duration) {
        try {
            // 创建AudioSegment对象，包含PPT上下文信息
            AudioSegment segment = AudioSegment.builder()
                    .courseId(courseId)
                    .segmentIndex(globalSegmentIndex)
                    .textContent(finalText)
                    .audioData(audioData)
                    .audioSize(audioData != null ? (long) audioData.length : 0L)
                    .duration(duration)
                    .audioFormat(audioFormat)
                    .sampleRate(sampleRate)
                    .checksum(generateChecksum(audioData))
                    // PPT上下文字段
                    .slidePageNumber(slidePageNumber)
                    .slideTitle(slideTitle)
                    .slideType(slideType)
                    .slideDescription(slideDescription)
                    .originalText(originalText)
                    .polishedText(polishedText)
                    .build();

            boolean result = audioSegmentService.saveAudioSegment(segment);

            if (result) {
                log.info("保存PPT音频片段成功，会话ID: {}, 页码: {}, 内容点: {}, 全局索引: {}",
                        courseId, slidePageNumber, contentPointIndex, globalSegmentIndex);
                // 更新会话统计信息
                updateSessionStatistics(courseId);
            } else {
                log.error("保存PPT音频片段失败，会话ID: {}, 页码: {}, 内容点: {}",
                        courseId, slidePageNumber, contentPointIndex);
            }

            return result;

        } catch (Exception e) {
            log.error("保存PPT音频片段异常，会话ID: {}, 页码: {}, 内容点: {}",
                    courseId, slidePageNumber, contentPointIndex, e);
            return false;
        }
    }

    //@Override
    public List<AudioSegment> getSlideAudioSegments(String courseId, Integer pageNumber) {
        try {
            List<AudioSegment> segments = audioSegmentService.getBySessionAndSlide(courseId, pageNumber);
            log.debug("查询页面音频片段，会话ID: {}, 页码: {}, 找到 {} 个片段",
                    courseId, pageNumber, segments.size());
            return segments;
        } catch (Exception e) {
            log.error("查询页面音频片段失败，会话ID: {}, 页码: {}", courseId, pageNumber, e);
            return List.of();
        }
    }

    //@Override
    public AudioSegment getAudioSegmentByIndex(String courseId, Integer segmentIndex) {
        try {
            AudioSegment segment = audioSegmentService.getBySessionAndIndex(courseId, segmentIndex);
            if (segment != null) {
                log.debug("查询音频片段成功，会话ID: {}, 片段索引: {}", courseId, segmentIndex);
            } else {
                log.warn("未找到音频片段，会话ID: {}, 片段索引: {}", courseId, segmentIndex);
            }
            return segment;
        } catch (Exception e) {
            log.error("查询音频片段失败，会话ID: {}, 片段索引: {}", courseId, segmentIndex, e);
            return null;
        }
    }

    //@Override
    public List<AudioSegment> getContentPointAudioSegments(String courseId, Integer pageNumber, Integer contentPointIndex) {
        try {
            List<AudioSegment> segments = audioSegmentService.getBySessionSlideAndContentPoint(
                    courseId, pageNumber, contentPointIndex);
            log.debug("查询内容点音频片段，会话ID: {}, 页码: {}, 内容点: {}, 找到 {} 个片段",
                    courseId, pageNumber, contentPointIndex, segments.size());
            return segments;
        } catch (Exception e) {
            log.error("查询内容点音频片段失败，会话ID: {}, 页码: {}, 内容点: {}",
                    courseId, pageNumber, contentPointIndex, e);
            return List.of();
        }
    }

    /**
     * 生成音频数据校验和
     */
    private String generateChecksum(byte[] audioData) {
        if (audioData == null || audioData.length == 0) {
            return "";
        }
        // 简单的校验和实现，实际项目中可以使用MD5或SHA256
        return String.valueOf(audioData.length);
    }

    /**
     * 更新会话统计信息
     */
    private void updateSessionStatistics(String courseId) {
        try {
            int segmentCount = audioSegmentService.countByCourseId(courseId);
            Long totalDuration = audioSegmentService.getTotalDuration(courseId);

            lessonSessionService.updateSessionStats(courseId, segmentCount, totalDuration);

            log.debug("更新会话统计信息，会话ID: {}, 片段数: {}, 总时长: {}ms",
                    courseId, segmentCount, totalDuration);
        } catch (Exception e) {
            log.warn("更新会话统计信息失败，会话ID: {}", courseId, e);
        }
    }

    @Override
    public boolean updateSessionPolishedText(String courseId, String polishedText) {
        try {
            boolean result = lessonSessionService.updatePolishedText(courseId, polishedText);
            if (result) {
                log.info("更新会话润色文本成功，会话ID: {}, 文本长度: {}", courseId,
                        polishedText != null ? polishedText.length() : 0);
            } else {
                log.warn("更新会话润色文本失败，会话ID: {}", courseId);
            }
            return result;
        } catch (Exception e) {
            log.error("更新会话润色文本异常，会话ID: {}", courseId, e);
            return false;
        }
    }

    @Override
    public boolean updateSessionTitle(String courseId, String title) {
        try {
            boolean result = lessonSessionService.updateTitle(courseId, title);
            if (result) {
                log.info("更新会话标题成功，会话ID: {}, 标题: {}", courseId, title);
            } else {
                log.warn("更新会话标题失败，会话ID: {}", courseId);
            }
            return result;
        } catch (Exception e) {
            log.error("更新会话标题异常，会话ID: {}", courseId, e);
            return false;
        }
    }

    @Override
    public List<LessonSession> getSessionsByStatus(String status) {
        try {
            List<LessonSession> sessions;
            if (status == null || status.trim().isEmpty()) {
                // 获取所有会话
                sessions = lessonSessionService.list();
                log.info("查询所有会话，找到 {} 个会话", sessions.size());
            } else {
                sessions = lessonSessionService.getSessionsByStatus(status);
                log.info("查询指定状态的会话，状态: {}, 找到 {} 个会话", status, sessions.size());
            }
            return sessions;
        } catch (Exception e) {
            log.error("查询会话失败，状态: {}", status, e);
            return List.of();
        }
    }

    @Override
    public boolean updateSessionStatus(String courseId, String status, String reviewerId,
                                     java.time.LocalDateTime reviewedAt, String comments) {
        try {
            boolean result = lessonSessionService.updateSessionStatus(courseId, status, reviewerId, reviewedAt, comments);
            if (result) {
                log.info("更新会话状态成功，会话ID: {}, 新状态: {}, 审核人: {}", courseId, status, reviewerId);
            } else {
                log.warn("更新会话状态失败，会话ID: {}, 状态: {}", courseId, status);
            }
            return result;
        } catch (Exception e) {
            log.error("更新会话状态异常，会话ID: {}, 状态: {}", courseId, status, e);
            return false;
        }
    }
}
