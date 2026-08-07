-- ===============================================
-- V6: 为备课内容表添加PPT下载链接字段
-- ===============================================

ALTER TABLE teaching_contents
    ADD COLUMN ppt_download_url VARCHAR(500) DEFAULT NULL COMMENT 'PPT导出下载链接';
