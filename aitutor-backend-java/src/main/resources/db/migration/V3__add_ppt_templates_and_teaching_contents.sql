-- ===============================================
-- V3: 添加PPT模板表和备课内容表
-- ===============================================

-- -----------------------------------------------
-- 1. PPT风格模板表
-- -----------------------------------------------
CREATE TABLE ppt_templates
(
    id                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    user_id           BIGINT       DEFAULT NULL COMMENT '用户ID（NULL=系统默认模板）',
    name              VARCHAR(100) NOT NULL COMMENT '模板名称',
    config_json       TEXT         DEFAULT NULL COMMENT '配置JSON（配色、字体、布局等）',
    preview_image_url VARCHAR(500) DEFAULT NULL COMMENT '预览图片URL',
    is_system         TINYINT(1)   DEFAULT 0 COMMENT '是否系统模板（1-是，0-否）',
    created_at        DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY               idx_user_id (user_id),
    KEY               idx_is_system (is_system)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='PPT风格模板表';

-- -----------------------------------------------
-- 2. 备课内容表
-- -----------------------------------------------
CREATE TABLE teaching_contents
(
    id            BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    prep_id       BIGINT       DEFAULT NULL COMMENT '备课ID',
    user_id       BIGINT       DEFAULT NULL COMMENT '用户ID',
    title         VARCHAR(200) DEFAULT NULL COMMENT '备课标题',
    status        VARCHAR(20)  DEFAULT 'draft' COMMENT '备课状态（draft-草稿, published-已发布, archived-已归档）',
    ppt_structure TEXT         DEFAULT NULL COMMENT 'PPT结构JSON',
    template_id   BIGINT       DEFAULT NULL COMMENT '应用的模板ID',
    created_at    DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at    DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY           idx_prep_id (prep_id),
    KEY           idx_user_id (user_id),
    KEY           idx_status (status),
    KEY           idx_template_id (template_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='备课内容表';
