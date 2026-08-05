-- ===============================================
-- V3: 添加复习提醒与事件采集相关表
-- 支持复习提醒查询、标记已复习、事件采集功能
-- ===============================================

-- -----------------------------------------------
-- 1. 复习提醒表
-- -----------------------------------------------
CREATE TABLE review_reminders (
    id              BIGINT       AUTO_INCREMENT COMMENT '主键ID',
    user_id         BIGINT       NOT NULL COMMENT '用户ID',
    course_id       VARCHAR(255) NOT NULL COMMENT '课程（会话）ID',
    reminder_type   VARCHAR(50)  NOT NULL DEFAULT 'REVIEW' COMMENT '提醒类型：REVIEW(复习)/RECALL(回忆)/SPACED_REPETITION(间隔重复)',
    content         TEXT         DEFAULT NULL COMMENT '复习内容摘要',
    scheduled_date  DATE         NOT NULL COMMENT '计划复习日期',
    priority        TINYINT      DEFAULT 0 COMMENT '优先级：0-普通 1-重要 2-紧急',
    is_reviewed     TINYINT      DEFAULT 0 COMMENT '是否已复习：0-未复习 1-已复习',
    reviewed_at     DATETIME     DEFAULT NULL COMMENT '复习完成时间',
    created_at      DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_user_id (user_id),
    KEY idx_user_reviewed (user_id, is_reviewed),
    KEY idx_scheduled_date (scheduled_date),
    KEY idx_user_scheduled (user_id, scheduled_date, is_reviewed)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='复习提醒表';

-- -----------------------------------------------
-- 2. 事件采集表（协调 M1/M2/M4/M7 模块）
-- -----------------------------------------------
CREATE TABLE event_collections (
    id              BIGINT       AUTO_INCREMENT COMMENT '主键ID',
    module          VARCHAR(10)  NOT NULL COMMENT '模块标识：M1/M2/M4/M7',
    event_type      VARCHAR(50)  NOT NULL COMMENT '事件类型',
    user_id         BIGINT       DEFAULT NULL COMMENT '关联用户ID',
    event_data      JSON         DEFAULT NULL COMMENT '事件数据（JSON格式）',
    event_time      DATETIME     NOT NULL COMMENT '事件发生时间',
    processed       TINYINT      DEFAULT 0 COMMENT '是否已处理：0-未处理 1-已处理',
    processed_at    DATETIME     DEFAULT NULL COMMENT '处理时间',
    created_at      DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_module (module),
    KEY idx_user_event (user_id, event_type),
    KEY idx_event_time (event_time),
    KEY idx_module_processed (module, processed, event_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='事件采集表，协调M1/M2/M4/M7模块事件数据';
