-- ===============================================
-- V9: 统一 prep_id 与 id，保证 prep_id 唯一性
-- ===============================================

-- 1. 更新已有数据：prep_id = id（NULL 或与 id 不一致的行都统一）
UPDATE teaching_contents SET prep_id = id WHERE prep_id IS NULL OR prep_id != id;

-- 2. 删除 V3 建表时的普通索引 idx_prep_id
--    （同名的唯一索引无法直接创建，且 prep_id 唯一后该普通索引已冗余）
ALTER TABLE teaching_contents DROP INDEX idx_prep_id;

-- 3. 添加唯一约束
ALTER TABLE teaching_contents ADD UNIQUE INDEX idx_prep_id (prep_id);
