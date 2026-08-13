-- ===============================================
-- V8: 为备课内容表添加 M4 备课列表所需字段
-- ===============================================

ALTER TABLE teaching_contents
    ADD COLUMN type             VARCHAR(20) DEFAULT 'ppt' COMMENT '内容类型（ppt/doc/text）',
    ADD COLUMN subject          VARCHAR(50) COMMENT '学科（math/chinese/english/...）',
    ADD COLUMN grade            VARCHAR(20) COMMENT '年级（grade_7/grade_8/...）',
    ADD COLUMN knowledge_points JSON COMMENT '知识点列表 [{id, name}]';
