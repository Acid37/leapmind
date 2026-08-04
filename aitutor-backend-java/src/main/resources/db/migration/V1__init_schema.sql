-- ===============================================
-- V1: 初始化数据库表结构
-- 包含项目所有核心业务表
-- ===============================================

-- -----------------------------------------------
-- 1. 用户表
-- -----------------------------------------------
CREATE TABLE users (
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    username     VARCHAR(50)  NOT NULL COMMENT '用户账号',
    password     VARCHAR(255) NOT NULL COMMENT '加密密码',
    grade        ENUM('GRADE_1','GRADE_2','GRADE_3','GRADE_4','GRADE_5','GRADE_6','GRADE_7','GRADE_8','GRADE_9')
                              NOT NULL COMMENT '年级',
    stage        VARCHAR(10)  DEFAULT NULL COMMENT '学段（PRIMARY、JUNIOR、SENIOR）',
    student_name VARCHAR(100) DEFAULT NULL COMMENT '学生姓名',
    email        VARCHAR(100) DEFAULT NULL COMMENT '邮箱',
    phone        VARCHAR(20)  DEFAULT NULL COMMENT '手机号',
    status       TINYINT      DEFAULT 1 COMMENT '账号状态 1-正常 0-禁用',
    created_at   DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    identify     VARCHAR(5)   DEFAULT NULL COMMENT '身份（student/admin）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username),
    KEY idx_username (username),
    KEY idx_grade (grade),
    KEY idx_email (email),
    KEY idx_phone (phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- -----------------------------------------------
-- 2. 教育阶段表
-- -----------------------------------------------
CREATE TABLE education_stages (
    id          BIGINT       AUTO_INCREMENT COMMENT '阶段ID',
    stage_name  VARCHAR(20)  NOT NULL COMMENT '阶段名称（小学/初中/高中）',
    stage_code  VARCHAR(20)  NOT NULL COMMENT '阶段代码（PRIMARY/MIDDLE/HIGH）',
    grade_code  VARCHAR(20)  NOT NULL COMMENT '年级代码（GRADE_1~GRADE_12）',
    grade_name  VARCHAR(20)  NOT NULL COMMENT '年级名称（一年级/初一/高一）',
    description VARCHAR(200) DEFAULT NULL COMMENT '阶段描述',
    sort_order  INT          DEFAULT 0 COMMENT '排序',
    created_at  DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_stage_code (stage_code),
    KEY idx_grade_code (grade_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='教育阶段表';

-- -----------------------------------------------
-- 3. 课程表（课程章节排期）
-- -----------------------------------------------
CREATE TABLE course_schedule (
    id              INT UNSIGNED  NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    course_id       VARCHAR(255)  NOT NULL COMMENT '课程ID（会话ID）',
    stage_name      VARCHAR(20)   NOT NULL COMMENT '阶段（小学/初中/高中）',
    grade_name      VARCHAR(20)   NOT NULL COMMENT '年级（一年级/初一/高一）',
    semester        VARCHAR(20)   NOT NULL COMMENT '学期（上册/下册）',
    chapter_number  INT           NOT NULL COMMENT '章节序号',
    chapter_title   VARCHAR(200)  NOT NULL COMMENT '章节题目',
    section_content TEXT          DEFAULT NULL COMMENT '章节内容（支持长文本）',
    section_number  FLOAT         DEFAULT NULL COMMENT '节号',
    section_title   VARCHAR(200)  DEFAULT NULL COMMENT '节名',
    subject         VARCHAR(10)   DEFAULT NULL COMMENT '学科（语文/数学/英语等）',
    PRIMARY KEY (id),
    KEY idx_stage_grade (stage_name, grade_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课程表，存储各阶段年级的章节信息';

-- -----------------------------------------------
-- 4. 课程会话表
-- -----------------------------------------------
CREATE TABLE lesson_sessions (
    id              BIGINT       AUTO_INCREMENT,
    course_id       VARCHAR(255) NOT NULL COMMENT '课程（会话）ID',
    title           VARCHAR(255) DEFAULT NULL COMMENT '课程标题',
    original_text   TEXT         DEFAULT NULL COMMENT '原始课程内容',
    polished_text   TEXT         DEFAULT NULL COMMENT '润色后的课程内容',
    total_segments  INT          DEFAULT NULL COMMENT '总片段数',
    total_duration  BIGINT       DEFAULT NULL COMMENT '总时长（毫秒）',
    total_pages     INT          DEFAULT NULL COMMENT '总页数',
    storage_type    VARCHAR(20)  DEFAULT 'PAGE_LEVEL' COMMENT '存储类型：SEGMENT_LEVEL(片段级) / PAGE_LEVEL(页面级)',
    created_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_course_id (course_id),
    KEY idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课程会话表';

-- -----------------------------------------------
-- 5. 音频片段表
-- -----------------------------------------------
CREATE TABLE audio_segments (
    id                BIGINT       AUTO_INCREMENT,
    course_id         VARCHAR(255) NOT NULL COMMENT '课程（会话）ID',
    segment_index     INT          NOT NULL COMMENT '页面编号（页面级存储）',
    text_content      TEXT         DEFAULT NULL COMMENT '页面标题或描述',
    audio_data        LONGBLOB     DEFAULT NULL COMMENT '页面所有音频片段的合并数据',
    audio_size        BIGINT       DEFAULT NULL COMMENT '页面总音频文件大小（字节）',
    duration          BIGINT       DEFAULT NULL COMMENT '页面总音频时长（毫秒）',
    audio_format      VARCHAR(10)  DEFAULT 'wav' COMMENT '音频格式',
    sample_rate       INT          DEFAULT 16000 COMMENT '采样率',
    checksum          VARCHAR(64)  DEFAULT NULL COMMENT '音频数据校验和',
    slide_page_number INT          DEFAULT NULL COMMENT 'PPT页码',
    slide_title       VARCHAR(500) DEFAULT NULL COMMENT 'PPT页面标题',
    slide_type        VARCHAR(50)  DEFAULT NULL COMMENT 'PPT页面类型（title/agenda/content/thankyou等）',
    slide_description TEXT         DEFAULT NULL COMMENT 'PPT页面描述',
    original_text     TEXT         DEFAULT NULL COMMENT '润色前的原始文本',
    polished_text     TEXT         DEFAULT NULL COMMENT '润色后的文本',
    segment_count     INT          DEFAULT 0 COMMENT '该页面包含的音频片段数量',
    segment_metadata  MEDIUMTEXT   DEFAULT NULL COMMENT '片段元数据（JSON格式）',
    created_at        TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_session_page (course_id, segment_index),
    CONSTRAINT fk_audio_segments_course FOREIGN KEY (course_id) REFERENCES lesson_sessions(course_id),
    KEY idx_course_id (course_id),
    KEY idx_course_segment (course_id, segment_index),
    KEY idx_course_slide (course_id, slide_page_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='音频片段表';

-- -----------------------------------------------
-- 6. PPT页面音频表（页面级存储）
-- -----------------------------------------------
CREATE TABLE ppt_page_audio (
    id                BIGINT       AUTO_INCREMENT,
    course_id         VARCHAR(255) NOT NULL COMMENT '课程（会话）ID',
    page_number       INT          NOT NULL COMMENT 'PPT页码',
    page_title        VARCHAR(500) DEFAULT NULL COMMENT '页面标题',
    slide_type        VARCHAR(50)  DEFAULT NULL COMMENT '页面类型',
    slide_description TEXT         DEFAULT NULL COMMENT '页面描述',
    segment_count     INT          DEFAULT 0 COMMENT '音频片段数量',
    merged_audio_data LONGBLOB     DEFAULT NULL COMMENT '合并后的音频数据',
    total_audio_size  BIGINT       DEFAULT NULL COMMENT '总音频大小（字节）',
    total_duration    BIGINT       DEFAULT NULL COMMENT '总音频时长（毫秒）',
    audio_format      VARCHAR(10)  DEFAULT 'wav' COMMENT '音频格式',
    sample_rate       INT          DEFAULT 16000 COMMENT '采样率',
    segment_metadata  MEDIUMTEXT   DEFAULT NULL COMMENT '片段元数据（JSON格式）',
    checksum          VARCHAR(64)  DEFAULT NULL COMMENT '数据校验和',
    created_at        DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at        DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_course_page (course_id, page_number),
    KEY idx_course_id (course_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='PPT页面音频表';

-- -----------------------------------------------
-- 7. PPT幻灯片表
-- -----------------------------------------------
CREATE TABLE ppt_slides (
    id           INT          AUTO_INCREMENT,
    course_id    VARCHAR(255) NOT NULL COMMENT '课程（会话）ID',
    slide_index  INT          NOT NULL COMMENT '幻灯片索引',
    slide_id     VARCHAR(100) NOT NULL COMMENT '幻灯片ID',
    title        VARCHAR(255) DEFAULT NULL COMMENT '标题',
    content_type VARCHAR(50)  DEFAULT NULL COMMENT '内容类型',
    html_content TEXT         DEFAULT NULL COMMENT 'HTML内容',
    create_at    DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='PPT幻灯片表';

-- -----------------------------------------------
-- 8. 项目大纲表
-- -----------------------------------------------
CREATE TABLE project_outline (
    id           INT          AUTO_INCREMENT COMMENT '主键',
    course_id    VARCHAR(255) NOT NULL COMMENT '课程（会话）ID',
    outline_json JSON         NOT NULL COMMENT 'JSON格式的大纲',
    create_time  DATETIME     DEFAULT NULL COMMENT '创建时间',
    update_time  DATETIME     DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_id (id),
    KEY idx_course_id (course_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='PPT课程大纲表';

-- -----------------------------------------------
-- 9. 短信验证码表
-- -----------------------------------------------
CREATE TABLE sms_verification_code (
    id                BIGINT UNSIGNED AUTO_INCREMENT COMMENT '主键ID',
    phone             VARCHAR(20)   NOT NULL COMMENT '接收验证码的手机号',
    verification_code VARCHAR(10)   NOT NULL COMMENT '手机验证码（6位数字）',
    expire_time       DATETIME      NOT NULL COMMENT '验证码有效期截止时间',
    is_used           TINYINT UNSIGNED DEFAULT 0 NOT NULL COMMENT '使用状态：0-未使用 1-已使用',
    PRIMARY KEY (id),
    UNIQUE KEY uk_phone_code (phone, verification_code),
    KEY idx_phone_status (phone, is_used, expire_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='手机验证码存储表';

-- -----------------------------------------------
-- 10. 学生提问表
-- -----------------------------------------------
CREATE TABLE student_questions (
    id              BIGINT       AUTO_INCREMENT,
    course_id       VARCHAR(255) DEFAULT NULL COMMENT '课程ID',
    segment_index   INT          DEFAULT NULL COMMENT '打断时的片段索引',
    question_text   TEXT         DEFAULT NULL COMMENT '问题文本',
    answer_text     TEXT         DEFAULT NULL COMMENT 'AI回答文本',
    question_audio  LONGBLOB     DEFAULT NULL COMMENT '问题音频数据',
    answer_audio    LONGBLOB     DEFAULT NULL COMMENT '回答音频数据',
    question_type   VARCHAR(20)  DEFAULT 'TEXT' COMMENT '提问类型：TEXT / VOICE',
    created_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_student_questions_course FOREIGN KEY (course_id) REFERENCES lesson_sessions(course_id),
    KEY idx_course_id (course_id),
    KEY idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学生提问表';
