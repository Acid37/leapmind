-- V3: M4 即时讲课模块（许沣睿）
-- 表名: lectures（避免与备课模块 teaching_contents 冲突）

CREATE TABLE IF NOT EXISTS lectures (
    id                  BIGINT       AUTO_INCREMENT COMMENT '主键ID',
    course_id           VARCHAR(255) NOT NULL COMMENT '课程ID',
    title               VARCHAR(500) COMMENT '讲课标题',
    status              VARCHAR(20)  NOT NULL DEFAULT 'GENERATING' COMMENT '状态: GENERATING/READY/PLAYING/PAUSED/COMPLETED',
    source_file_path    VARCHAR(500) COMMENT 'MinIO文件路径',
    source_file_name    VARCHAR(500) COMMENT '原始文件名',
    file_size           BIGINT       DEFAULT 0 COMMENT '文件大小(字节)',
    file_type           VARCHAR(50)  COMMENT '文件类型: PDF/WORD/PPT/IMAGE/TEXT',
    ppt_json_path       VARCHAR(500) COMMENT 'PPT JSON路径',
    generated_content   MEDIUMTEXT   COMMENT 'AI生成的讲课内容',
    current_page        INT          DEFAULT 0 COMMENT '当前播放页数',
    total_pages         INT          DEFAULT 0 COMMENT '总页数',
    progress_ms         BIGINT       DEFAULT 0 COMMENT '播放进度(毫秒)',
    total_duration_ms   BIGINT       DEFAULT 0 COMMENT '总时长(毫秒)',
    playback_snapshot   MEDIUMTEXT   COMMENT '回放快照JSON',
    created_at          DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at          DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_course_id (course_id),
    KEY idx_status (status),
    KEY idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='M4即时讲课内容表';
