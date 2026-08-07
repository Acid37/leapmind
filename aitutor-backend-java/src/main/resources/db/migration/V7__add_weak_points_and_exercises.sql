-- ===============================================
-- V3: 薄弱点分析与练习推荐功能
-- 新增 user_weak_points + user_exercises 两张表
-- ===============================================

-- -----------------------------------------------
-- 1. 用户薄弱点表
-- -----------------------------------------------
CREATE TABLE user_weak_points (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    user_id         BIGINT       NOT NULL COMMENT '用户ID',
    knowledge_point VARCHAR(200) NOT NULL COMMENT '知识点名称',
    subject         VARCHAR(50)  DEFAULT NULL COMMENT '学科',
    weakness_level  VARCHAR(20)  DEFAULT 'MEDIUM' COMMENT '薄弱程度：HIGH/MEDIUM/LOW',
    error_count     INT          DEFAULT 0 COMMENT '错误次数',
    total_count     INT          DEFAULT 0 COMMENT '总答题次数',
    accuracy_rate   DECIMAL(5,2) DEFAULT NULL COMMENT '正确率(%)',
    last_error_time DATETIME     DEFAULT NULL COMMENT '最近一次错误时间',
    status          VARCHAR(20)  DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/RESOLVED/IMPROVING',
    ai_analysis     TEXT         DEFAULT NULL COMMENT 'AI 综合分析结果(JSON)',
    ai_suggestion   TEXT         DEFAULT NULL COMMENT 'AI 个性化学习建议',
    analyzed_at     DATETIME     DEFAULT NULL COMMENT 'AI 分析时间',
    created_at      DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_knowledge (user_id, knowledge_point),
    KEY idx_user_id (user_id),
    KEY idx_user_subject (user_id, subject),
    KEY idx_status (status),
    KEY idx_user_status (user_id, status),
    CONSTRAINT fk_weak_points_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户薄弱点分析表';

-- -----------------------------------------------
-- 2. 用户练习记录表
-- -----------------------------------------------
CREATE TABLE user_exercises (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    user_id         BIGINT       NOT NULL COMMENT '用户ID',
    exercise_id     VARCHAR(100) NOT NULL COMMENT '练习题ID（外部题库唯一标识）',
    knowledge_point VARCHAR(200) DEFAULT NULL COMMENT '知识点名称',
    subject         VARCHAR(50)  DEFAULT NULL COMMENT '学科',
    is_correct      TINYINT      DEFAULT 0 COMMENT '是否正确：1-正确 0-错误',
    completed_at    DATETIME     DEFAULT NULL COMMENT '完成时间',
    created_at      DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_user_completed (user_id, completed_at),
    KEY idx_user_knowledge (user_id, knowledge_point),
    KEY idx_user_exercise (user_id, exercise_id),
    CONSTRAINT fk_exercises_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户练习记录表';
