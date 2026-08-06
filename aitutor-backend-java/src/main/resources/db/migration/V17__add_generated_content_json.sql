-- ===============================================
-- V7: 为备课内容表添加完整生成内容JSON字段
-- ===============================================

ALTER TABLE teaching_contents
    ADD COLUMN generated_content_json TEXT COMMENT '完整备课生成内容（含大纲、PPT、讲稿），供 generate-ppt 接口读取 syllabus';
