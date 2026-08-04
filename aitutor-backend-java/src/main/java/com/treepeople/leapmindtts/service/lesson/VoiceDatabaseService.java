package com.treepeople.leapmindtts.service.lesson;

import com.treepeople.leapmindtts.pojo.entity.AudioSegment;
import com.treepeople.leapmindtts.pojo.entity.LessonSession;

import java.util.List;

/**
 * 语音数据库综合服务接口
 * 提供对讲课会话、音频片段、学生提问的统一管理
 */
public interface VoiceDatabaseService {

    // 会话管理

    /**
     * 创建完整的讲课会话（包含会话信息和音频片段）
     */
    boolean createCompleteSession(String courseId, String title, String originalText,
                                String polishedText, List<AudioSegment> segments);

    /**
     * 获取会话的完整信息（包含音频片段统计）
     */
    LessonSession getCompleteSessionInfo(String courseId);

    /**
     * 删除完整的会话数据（级联删除所有相关数据）
     */
    boolean deleteCompleteSession(String courseId);

    // 音频片段管理

    /**
     * 获取会话的所有音频片段
     */
    List<AudioSegment> getSessionAudioSegments(String courseId);

    /**
     * 获取指定片段的音频数据
     */
    byte[] getSegmentAudioData(String courseId, Integer segmentIndex);

    /**
     * 添加音频片段到会话
     */
    boolean addAudioSegment(String courseId, Integer segmentIndex, String textContent,
                          byte[] audioData, String audioFormat, Integer sampleRate);

    /**
     * 更新会话的润色文本
     */
    boolean updateSessionPolishedText(String courseId, String polishedText);

    /**
     * 更新会话标题
     */
    boolean updateSessionTitle(String courseId, String title);

    // 新增：审核流程相关方法

    /**
     * 根据状态获取会话列表
     * @param status 状态，为null时返回所有会话
     */
    List<LessonSession> getSessionsByStatus(String status);

    /**
     * 更新会话状态
     */
    boolean updateSessionStatus(String courseId, String status, String reviewerId,
                              java.time.LocalDateTime reviewedAt, String comments);

    /**
     * 统计信息内部类
     */
    class SessionStatistics {
        private String courseId;
        private Integer totalSegments;
        private Long totalDuration;
        private Long totalAudioSize;
        private Integer totalQuestions;
        private Integer textQuestions;
        private Integer voiceQuestions;

        // 构造函数、getter和setter
        public SessionStatistics(String courseId, Integer totalSegments, Long totalDuration,
                               Long totalAudioSize, Integer totalQuestions,
                               Integer textQuestions, Integer voiceQuestions) {
            this.courseId = courseId;
            this.totalSegments = totalSegments;
            this.totalDuration = totalDuration;
            this.totalAudioSize = totalAudioSize;
            this.totalQuestions = totalQuestions;
            this.textQuestions = textQuestions;
            this.voiceQuestions = voiceQuestions;
        }

        // Getters
        public String getCourseId() { return courseId; }
        public Integer getTotalSegments() { return totalSegments; }
        public Long getTotalDuration() { return totalDuration; }
        public Long getTotalAudioSize() { return totalAudioSize; }
        public Integer getTotalQuestions() { return totalQuestions; }
        public Integer getTextQuestions() { return textQuestions; }
        public Integer getVoiceQuestions() { return voiceQuestions; }
    }
}
