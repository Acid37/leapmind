-- ===============================================
-- V3: 添加 AI 对话会话与消息表
-- 支持 M7 会话状态管理服务
-- ===============================================

-- -----------------------------------------------
-- 1. AI 对话会话表
-- -----------------------------------------------
CREATE TABLE conversation_sessions (
    id            BIGINT       AUTO_INCREMENT,
    session_id    VARCHAR(64)  NOT NULL COMMENT '会话ID',
    user_id       BIGINT       NOT NULL COMMENT '用户ID',
    scene_type    VARCHAR(20)  DEFAULT NULL COMMENT '场景类型(doing_exercise/explaining/teaching/lesson_prep/general_qa)',
    context_json  TEXT         DEFAULT NULL COMMENT '上下文JSON',
    message_count INT          DEFAULT 0 COMMENT '消息总数',
    created_at    DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at    DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted       TINYINT      DEFAULT 0 COMMENT '逻辑删除 0-未删除 1-已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_session_id (session_id),
    KEY idx_user_id (user_id),
    KEY idx_updated_at (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI对话会话表';

-- -----------------------------------------------
-- 2. AI 对话消息表
-- -----------------------------------------------
CREATE TABLE conversation_messages (
    id            BIGINT       AUTO_INCREMENT,
    session_id    VARCHAR(64)  NOT NULL COMMENT '会话ID',
    role          VARCHAR(10)  NOT NULL COMMENT '角色(user/assistant/system)',
    content       TEXT         NOT NULL COMMENT '消息内容',
    input_tokens  INT          DEFAULT 0 COMMENT '输入token数',
    output_tokens INT          DEFAULT 0 COMMENT '输出token数',
    call_id       VARCHAR(32)  DEFAULT NULL COMMENT 'AI调用ID',
    created_at    DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    deleted       TINYINT      DEFAULT 0 COMMENT '逻辑删除 0-未删除 1-已删除',
    PRIMARY KEY (id),
    KEY idx_session_id (session_id),
    KEY idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI对话消息表';