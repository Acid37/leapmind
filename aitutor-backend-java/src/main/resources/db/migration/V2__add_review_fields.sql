-- ===============================================
-- V2: 添加课程审核流程字段
-- ===============================================

-- 为 lesson_sessions 表添加审核相关字段
ALTER TABLE lesson_sessions
    ADD COLUMN processing_status VARCHAR(20) DEFAULT 'DRAFT' COMMENT '处理状态：DRAFT/PENDING_REVIEW/APPROVED/REJECTED/SYNTHESIZED',
    ADD COLUMN reviewed_by       VARCHAR(100) DEFAULT NULL COMMENT '审核人',
    ADD COLUMN reviewed_at       DATETIME     DEFAULT NULL COMMENT '审核时间',
    ADD COLUMN review_comments   TEXT         DEFAULT NULL COMMENT '审核意见';

-- 为 audio_segments 表添加片段状态字段
ALTER TABLE audio_segments
    ADD COLUMN segment_status VARCHAR(20) DEFAULT 'AUDIO_GENERATED' COMMENT '片段状态：TEXT_ONLY/AUDIO_GENERATED';

-- 创建索引以提高查询性能
CREATE INDEX idx_lesson_sessions_status ON lesson_sessions(processing_status);
CREATE INDEX idx_audio_segments_status  ON audio_segments(segment_status);
CREATE INDEX idx_audio_segments_course_status ON audio_segments(course_id, segment_status);
