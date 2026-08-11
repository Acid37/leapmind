-- ===============================================
-- V4: 对齐 Java 与 Python 薄弱点引擎的 user_weak_points 表结构
--
-- 背景：
--   Python 引擎（刘金华）计算 weakness_score（0-1 连续值）、
--   trend、confusion_count 等字段，但 V3 表中无对应列。
--   本迁移新增这些列，使 Python 的 upsert 能正常落库，
--   Java 侧的事件上报和 API 也能读取 Python 计算的权威分数。
-- ===============================================

-- 1. knowledge_point 加默认值（Python INSERT 不含此列，避免 NOT NULL 报错）
ALTER TABLE user_weak_points
    MODIFY COLUMN knowledge_point VARCHAR(200) NOT NULL DEFAULT '' COMMENT '知识点名称';

-- 2. 新增 Python 引擎需要的列
ALTER TABLE user_weak_points
    -- 知识点ID（Python引擎使用，关联 knowledge_points.id）
    ADD COLUMN kp_id            BIGINT        DEFAULT NULL COMMENT '知识点ID（关联knowledge_points表）',
    -- 薄弱度分数 0-1（Python 引擎计算的核心字段）
    ADD COLUMN weakness_score   DECIMAL(5,3)  DEFAULT NULL COMMENT '薄弱度分数 0~1，越高越薄弱',
    -- 总答题次数（Python引擎使用，与 total_count 语义相同）
    ADD COLUMN total_attempts   INT           DEFAULT 0   COMMENT '总答题次数（Python引擎统计）',
    -- 错误率 0-1
    ADD COLUMN error_rate       DECIMAL(5,3)  DEFAULT NULL COMMENT '历史错误率 0~1',
    -- 最近10次正确率
    ADD COLUMN recent_correct_rate DECIMAL(5,3) DEFAULT NULL COMMENT '最近10次正确率',
    -- 提问困惑次数
    ADD COLUMN confusion_count  INT           DEFAULT 0   COMMENT '提问困惑次数（M7对话+M1错题本+M6画像）',
    -- 趋势
    ADD COLUMN trend            VARCHAR(20)   DEFAULT NULL COMMENT '趋势：improving/stable/declining',
    -- 最近错题时间（Python引擎使用，与 last_error_time 语义相同）
    ADD COLUMN last_error_at    DATETIME      DEFAULT NULL COMMENT '最近错题时间（Python引擎填充）',
    -- 计算时间
    ADD COLUMN calculated_at    DATETIME      DEFAULT NULL COMMENT '薄弱度计算时间';

-- 3. Python 引擎使用的唯一索引（user_id + kp_id）
ALTER TABLE user_weak_points
    ADD UNIQUE KEY uk_user_kp (user_id, kp_id);
