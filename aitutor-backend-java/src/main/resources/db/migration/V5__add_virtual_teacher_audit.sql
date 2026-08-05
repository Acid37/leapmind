-- M8 虚拟 AI 教师审计日志

CREATE TABLE virtual_teacher_tts_audit_logs (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    user_id       BIGINT       NOT NULL COMMENT '用户ID',
    course_id     VARCHAR(64)  DEFAULT NULL COMMENT '课程ID',
    voice_type    VARCHAR(100) DEFAULT NULL COMMENT '音色',
    text_length   INT          NOT NULL DEFAULT 0 COMMENT '合成文本长度',
    cache_hit     TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否命中缓存',
    audio_size    BIGINT       NOT NULL DEFAULT 0 COMMENT '音频大小',
    latency_ms    BIGINT       NOT NULL DEFAULT 0 COMMENT '处理耗时',
    status        VARCHAR(20)  NOT NULL COMMENT 'SUCCESS/FAILED',
    error_message VARCHAR(500) DEFAULT NULL COMMENT '失败原因',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_virtual_teacher_tts_user_time (user_id, created_at),
    KEY idx_virtual_teacher_tts_status_time (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='虚拟教师TTS审计日志';
