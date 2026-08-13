-- ===============================================
-- V5: 补齐 M3 薄弱点模块依赖的核心表
--
-- 背景：
--   Python M3 计算引擎需要读取以下表进行薄弱度计算：
--     knowledge_points  — 知识点树
--     questions         — 题库
--     question_kp_relations — 题目-知识点关联
--     user_answers      — 答题记录
--     wrong_question_book — 错题本
--     conversation_messages — 对话记录（权威结构由 V13 创建）
--     user_profiles     — 用户画像（权威结构由 V9 创建）
--   此前这些表仅存在于 Python sql/init.sql 中，Java Flyway 未创建，
--   导致 Python 引擎直连数据库时无法读取数据。
--
-- 表结构对齐规范文档（LeapMind教育网站.md 第二章）与 Python 引擎的
-- 实际查询字段，包含数学知识点种子数据。
-- ===============================================

-- -----------------------------------------------
-- 1. 知识点树表
-- -----------------------------------------------
CREATE TABLE knowledge_points (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '知识点ID',
    subject     VARCHAR(50)  NOT NULL COMMENT '学科：math/chinese/english/physics/chemistry/biology',
    grade       VARCHAR(20)  NOT NULL DEFAULT 'grade_all' COMMENT '年级：grade_7~grade_12，grade_all=跨年级通用',
    name        VARCHAR(200) NOT NULL COMMENT '知识点名称，如"勾股定理"',
    parent_id   BIGINT       DEFAULT NULL COMMENT '父知识点ID（构建知识树）',
    description TEXT         DEFAULT NULL COMMENT '知识点描述',
    level       TINYINT      DEFAULT 1 COMMENT '层级：1=一级, 2=二级, 3=三级',
    PRIMARY KEY (id),
    INDEX idx_subject_grade (subject, grade),
    INDEX idx_parent (parent_id),
    CONSTRAINT fk_kp_parent FOREIGN KEY (parent_id) REFERENCES knowledge_points(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识点字典表';

-- -----------------------------------------------
-- 2. 题库表
-- -----------------------------------------------
CREATE TABLE questions (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '题目ID',
    kp_id           BIGINT       NOT NULL COMMENT '所属知识点ID（主要关联）',
    subject         VARCHAR(50)  NOT NULL COMMENT '学科',
    grade           VARCHAR(20)  NOT NULL COMMENT '年级',
    chapter         VARCHAR(100) DEFAULT NULL COMMENT '章节',
    type            VARCHAR(30)  NOT NULL COMMENT '题型：single_choice/multi_choice/fill_blank/short_answer/essay',
    difficulty      TINYINT      NOT NULL COMMENT '难度 1-5',
    content_json    TEXT         NOT NULL COMMENT '题目内容JSON：{"stem":"...","options":["A...","B..."],"images":["url1"]}',
    answer_json     TEXT         NOT NULL COMMENT '答案JSON：{"correct":"A","explanation":"...","steps":["步骤1","步骤2"]}',
    source          VARCHAR(50)  DEFAULT 'manual' COMMENT '来源：manual/ai_generated/imported',
    status          TINYINT      DEFAULT 1 COMMENT '1:启用 0:禁用',
    created_at      DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    INDEX idx_subject_grade (subject, grade),
    INDEX idx_kp_id (kp_id),
    INDEX idx_difficulty (difficulty),
    INDEX idx_chapter (chapter),
    CONSTRAINT fk_question_kp FOREIGN KEY (kp_id) REFERENCES knowledge_points(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='题库表';

-- -----------------------------------------------
-- 3. 题目-知识点多对多关联表
-- -----------------------------------------------
CREATE TABLE question_kp_relations (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    question_id BIGINT       NOT NULL COMMENT '题目ID',
    kp_id       BIGINT       NOT NULL COMMENT '知识点ID',
    weight      DECIMAL(3,2) DEFAULT 1.00 COMMENT '该知识点在题中的权重',
    PRIMARY KEY (id),
    UNIQUE KEY uk_q_kp (question_id, kp_id),
    INDEX idx_kp_id (kp_id),
    CONSTRAINT fk_qkr_question FOREIGN KEY (question_id) REFERENCES questions(id),
    CONSTRAINT fk_qkr_kp FOREIGN KEY (kp_id) REFERENCES knowledge_points(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='题目-知识点关联表';

-- -----------------------------------------------
-- 4. 用户答题记录表（M1 写入，M3+Python 读取）
-- -----------------------------------------------
CREATE TABLE user_answers (
    id                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '记录ID',
    user_id           BIGINT       NOT NULL COMMENT '用户ID',
    question_id       BIGINT       NOT NULL COMMENT '题目ID',
    kp_id             BIGINT       NOT NULL COMMENT '知识点ID（冗余，加速Python查询）',
    user_answer_json  TEXT         NOT NULL COMMENT '用户提交的答案JSON',
    is_correct        TINYINT(1)   NOT NULL COMMENT '是否答对：1=正确 0=错误',
    time_spent        INT          DEFAULT NULL COMMENT '作答耗时（秒）',
    attempt_count     INT          DEFAULT 1 COMMENT '第几次尝试',
    source_scene      VARCHAR(30)  DEFAULT NULL COMMENT '场景：free_practice/homework/wrong_redo/exam',
    created_at        DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '答题时间',
    PRIMARY KEY (id),
    INDEX idx_user_time (user_id, created_at DESC),
    INDEX idx_user_correct (user_id, is_correct),
    INDEX idx_user_kp (user_id, kp_id),
    INDEX idx_question (question_id),
    CONSTRAINT fk_ua_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_ua_question FOREIGN KEY (question_id) REFERENCES questions(id),
    CONSTRAINT fk_ua_kp FOREIGN KEY (kp_id) REFERENCES knowledge_points(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户答题记录表';

-- -----------------------------------------------
-- 5. 错题本表（M1 写入，M2 读取，M3+Python 分析）
-- -----------------------------------------------
CREATE TABLE wrong_question_book (
    id                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    user_id           BIGINT       NOT NULL COMMENT '用户ID',
    question_id       BIGINT       NOT NULL COMMENT '题目ID',
    kp_id             BIGINT       NOT NULL COMMENT '知识点ID（冗余，加速Python查询）',
    answer_record_id  BIGINT       DEFAULT NULL COMMENT '关联的答题记录ID',
    wrong_reason_tag  VARCHAR(50)  DEFAULT NULL COMMENT '错误原因标签：concept_unclear/careless/formula_wrong/method_wrong',
    is_key_focus      TINYINT(1)   DEFAULT 0 COMMENT '是否标记为重点复习',
    review_count      INT          DEFAULT 0 COMMENT '复习次数',
    last_review_at    DATETIME     DEFAULT NULL COMMENT '最近复习时间',
    status            VARCHAR(20)  DEFAULT 'unresolved' COMMENT '状态：unresolved/reviewing/resolved',
    created_at        DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '加入错题本时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_question (user_id, question_id),
    INDEX idx_user_kp (user_id, kp_id),
    INDEX idx_user_status (user_id, status),
    CONSTRAINT fk_wqb_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_wqb_question FOREIGN KEY (question_id) REFERENCES questions(id),
    CONSTRAINT fk_wqb_kp FOREIGN KEY (kp_id) REFERENCES knowledge_points(id),
    CONSTRAINT fk_wqb_answer FOREIGN KEY (answer_record_id) REFERENCES user_answers(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='错题本表';

-- -----------------------------------------------
-- 6. 对话消息表（M7 写入，M3+Python 读取分析）
--    注：conversation_messages 权威结构见 V13__add_conversation_tables.sql
--        （含 conversation_sessions），此处不再重复建表，避免同名冲突。
-- -----------------------------------------------

-- -----------------------------------------------
-- 7. 用户画像表（M6 写入，M2/M3/M4/M5/M7 消费）
--    注：user_profiles 权威结构见 V9__create_m6_user_profiles.sql，
--        此处不再重复建表，避免同名冲突。
-- -----------------------------------------------

-- ===============================================
-- 种子数据：数学知识点树（与 Python sql/init.sql 一致）
-- ===============================================
INSERT INTO knowledge_points (id, name, parent_id, subject, level) VALUES
(1,  '几何',              NULL, 'math', 1),
(2,  '代数',              NULL, 'math', 1),
(10, '勾股定理',           1,    'math', 2),
(11, '相似三角形',         1,    'math', 2),
(12, '全等三角形',         1,    'math', 2),
(20, '一元二次方程',       2,    'math', 2),
(21, '二次函数',           2,    'math', 2),
(101,'勾股定理逆定理',     10,   'math', 3),
(102,'勾股数',             10,   'math', 3),
(103,'勾股定理应用',       10,   'math', 3);
