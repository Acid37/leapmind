-- ═══════════════════════════════════════════════════════════════
-- ⚠️ 已废弃 — 仅保留作历史参考
-- 权威建表脚本已迁移至 Java Flyway:
--   aitutor-backend-java/src/main/resources/db/migration/V5__add_m3_core_tables.sql
-- 部署时不要执行此文件，否则与 V5 表结构冲突（content_json vs content, difficulty 1-5 vs 0-1）
-- ═══════════════════════════════════════════════════════════════
-- M3 薄弱点分析模块 - 建表脚本（已废弃）

-- ============================================================
-- 1. 知识点字典表（基础数据）
-- ============================================================
CREATE TABLE IF NOT EXISTS knowledge_points (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL COMMENT '知识点名称',
    parent_id BIGINT DEFAULT NULL COMMENT '父知识点ID',
    subject VARCHAR(50) NOT NULL COMMENT '学科',
    level INT DEFAULT 1 COMMENT '层级：1=学科, 2=模块, 3=具体知识点',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (parent_id) REFERENCES knowledge_points(id)
) COMMENT '知识点字典表';

-- ============================================================
-- 2. 题目表（基础数据）
-- ============================================================
CREATE TABLE IF NOT EXISTS questions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    kp_id BIGINT NOT NULL COMMENT '所属知识点ID',
    content TEXT NOT NULL COMMENT '题目内容',
    difficulty DECIMAL(3,2) DEFAULT 0.5 COMMENT '难度系数 0-1',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (kp_id) REFERENCES knowledge_points(id)
) COMMENT '题目表';

-- ============================================================
-- 3. 用户答题记录（M1 模块提供）
-- ============================================================
CREATE TABLE IF NOT EXISTS user_answers (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '用户ID',
    question_id BIGINT NOT NULL COMMENT '题目ID',
    kp_id BIGINT NOT NULL COMMENT '知识点ID',
    is_correct TINYINT(1) NOT NULL COMMENT '是否正确：1=正确, 0=错误',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '答题时间',
    INDEX idx_user_kp (user_id, kp_id),
    INDEX idx_user_time (user_id, created_at)
) COMMENT '用户答题记录';

-- ============================================================
-- 4. 错题本（M1 模块提供）
-- ============================================================
CREATE TABLE IF NOT EXISTS wrong_question_book (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '用户ID',
    question_id BIGINT NOT NULL COMMENT '题目ID',
    kp_id BIGINT NOT NULL COMMENT '知识点ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '加入错题本时间',
    INDEX idx_user_kp (user_id, kp_id)
) COMMENT '错题本';

-- ============================================================
-- 5. 对话/提问记录（M7 模块提供）
-- ============================================================
CREATE TABLE IF NOT EXISTS conversation_messages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '用户ID',
    content TEXT NOT NULL COMMENT '消息内容',
    kp_id BIGINT DEFAULT NULL COMMENT '关联知识点ID（可为空，需AI标注）',
    role VARCHAR(20) DEFAULT 'user' COMMENT '角色：user/assistant',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_kp (user_id, kp_id),
    INDEX idx_user_time (user_id, created_at)
) COMMENT '对话记录';

-- ============================================================
-- 6. 用户画像表（M6 提供）
-- ============================================================
CREATE TABLE IF NOT EXISTS user_profiles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE COMMENT '用户ID',
    confusion_history_json JSON COMMENT '困惑历史（JSON格式，M6写入）',
    learning_style VARCHAR(50) DEFAULT NULL COMMENT '学习风格',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT '用户画像';

-- ============================================================
-- 7. 薄弱点结果表（M3 写入目标表）
-- ============================================================
CREATE TABLE IF NOT EXISTS user_weak_points (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '用户ID',
    kp_id BIGINT NOT NULL COMMENT '知识点ID',
    weakness_score DECIMAL(5,3) COMMENT '薄弱分数 0-1，越高越薄弱',
    error_count INT COMMENT '错题数',
    total_attempts INT COMMENT '总答题数',
    error_rate DECIMAL(5,3) COMMENT '历史错误率',
    recent_correct_rate DECIMAL(5,3) COMMENT '最近10次正确率',
    confusion_count INT DEFAULT 0 COMMENT '提问困惑次数',
    trend VARCHAR(20) COMMENT '趋势：improving/stable/declining',
    last_error_at DATETIME COMMENT '最近一次错题时间',
    calculated_at DATETIME COMMENT '本记录计算时间',
    UNIQUE KEY uk_user_kp (user_id, kp_id)
) COMMENT '薄弱点结果表';

-- ============================================================
-- 7. 插入示例知识点数据
-- ============================================================
INSERT INTO knowledge_points (id, name, parent_id, subject, level) VALUES
(1,  '几何',              NULL, '数学', 1),
(2,  '代数',              NULL, '数学', 1),
(10, '勾股定理',           1,    '数学', 2),
(11, '相似三角形',         1,    '数学', 2),
(12, '全等三角形',         1,    '数学', 2),
(20, '一元二次方程',       2,    '数学', 2),
(21, '二次函数',           2,    '数学', 2),
(101,'勾股定理逆定理',     10,   '数学', 3),
(102,'勾股数',             10,   '数学', 3),
(103,'勾股定理应用',       10,   '数学', 3);
