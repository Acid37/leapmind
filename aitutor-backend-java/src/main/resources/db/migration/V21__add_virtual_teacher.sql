-- M8 虚拟 AI 教师

CREATE TABLE teacher_avatars
(
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '形象ID',
    avatar_code   VARCHAR(64)  NOT NULL COMMENT '稳定业务编码',
    name          VARCHAR(100) NOT NULL COMMENT '显示名称',
    description   VARCHAR(500)          DEFAULT NULL COMMENT '形象描述',
    model_url     VARCHAR(500) NOT NULL COMMENT 'VRM模型地址',
    thumbnail_url VARCHAR(500)          DEFAULT NULL COMMENT '缩略图地址',
    voice_type    VARCHAR(100) NOT NULL DEFAULT 'default' COMMENT '默认音色',
    accent        VARCHAR(50)           DEFAULT '普通话' COMMENT '口音',
    enabled       TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否启用',
    sort_order    INT          NOT NULL DEFAULT 0 COMMENT '排序',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_teacher_avatar_code (avatar_code),
    KEY           idx_teacher_avatar_enabled_sort (enabled, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='虚拟教师形象';

CREATE TABLE user_teacher_preferences
(
    id         BIGINT        NOT NULL AUTO_INCREMENT,
    user_id    BIGINT        NOT NULL COMMENT '用户ID',
    avatar_id  BIGINT        NOT NULL COMMENT '教师形象ID',
    voice_type VARCHAR(100)  NOT NULL DEFAULT 'default' COMMENT '用户音色',
    speed      DECIMAL(3, 2) NOT NULL DEFAULT 1.00 COMMENT '语速 0.5-2.0',
    created_at DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_teacher_preference (user_id),
    KEY        idx_user_teacher_avatar (avatar_id),
    CONSTRAINT fk_user_teacher_preference_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_teacher_preference_avatar FOREIGN KEY (avatar_id) REFERENCES teacher_avatars (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户虚拟教师偏好';

INSERT INTO teacher_avatars
(avatar_code, name, description, model_url, voice_type, accent, enabled, sort_order)
VALUES ('teacher-001', '小跃', '亲切活泼，适合语言与通识课程', '/vrm/teacher001_girl.vrm', 'zhixiaoxia', '普通话', 1,
        10),
       ('teacher-002', '知夏', '沉稳清晰，适合理工科讲解', '/vrm/teacher002_with_glasses_girl.vrm', 'zhixiaobai',
        '普通话', 1, 20),
       ('teacher-003', '星澜', '自然耐心，适合互动答疑', '/vrm/teacher003_girl.vrm', 'zhixiaoxia', '普通话', 1, 30);
