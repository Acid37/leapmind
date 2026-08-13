-- ===============================================
-- V7: 扩展复习提醒表，支持 M6 画像事件联动
-- 新增字段：
--   notes          - 复习备注（用户标记已复习时填写）
--   kp_id          - 知识点ID（用于 M6 mark_reviewed 事件关联）
-- ===============================================

ALTER TABLE review_reminders
    ADD COLUMN notes  VARCHAR(500) DEFAULT NULL COMMENT '复习备注，用户在标记已复习时填写的心得或掌握程度',
    ADD COLUMN kp_id  BIGINT       DEFAULT NULL COMMENT '知识点ID，关联 knowledge_points 表，用于 M6 mark_reviewed 事件';
