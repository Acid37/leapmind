-- ============================================================
-- M6 前后端联调测试数据
-- 在 leapmind 库中执行
-- ============================================================

-- 1. 创建测试用户 23（密码：M6Demo-9f4K2!）
INSERT INTO users (id, username, password, grade, status)
VALUES (23, 'm6_apifox_20260805', '$2a$10$kDI6tuzz8BdhewKVa/LFAOM7JE2m9BhkMTJmjtAWfmuPenwjEqlsG', 'GRADE_1', 1)
ON DUPLICATE KEY UPDATE username=VALUES(username), password=VALUES(password);

-- 2. 写入 5 条学习事件
INSERT INTO user_events (event_id, user_id, event_type, source_module, session_id, kp_id, event_data_json, schema_version, occurred_at, received_at, process_status, trace_id, payload_hash)
VALUES
('e2e-001', 23, 'answer_question', 'M1', 'sess-01', 101, '{"isCorrect":true,"difficulty":3,"timeSpentSec":45,"hintCount":0}', '1.0', '2026-08-06 14:30:00', NOW(), 'PENDING', 't1', REPEAT('0',64)),
('e2e-002', 23, 'answer_question', 'M1', 'sess-01', 101, '{"isCorrect":false,"difficulty":4,"timeSpentSec":60,"hintCount":1}', '1.0', '2026-08-06 14:35:00', NOW(), 'PENDING', 't2', REPEAT('0',64)),
('e2e-003', 23, 'answer_question', 'M1', 'sess-01', 101, '{"isCorrect":true,"difficulty":2,"timeSpentSec":30,"hintCount":0}', '1.0', '2026-08-06 14:40:00', NOW(), 'PENDING', 't3', REPEAT('0',64)),
('e2e-004', 23, 'answer_question', 'M1', 'sess-02', 102, '{"isCorrect":true,"difficulty":3,"timeSpentSec":40,"hintCount":0}', '1.0', '2026-08-06 14:45:00', NOW(), 'PENDING', 't4', REPEAT('0',64)),
('e2e-005', 23, 'answer_question', 'M1', 'sess-03', 103, '{"isCorrect":true,"difficulty":2,"timeSpentSec":35,"hintCount":0}', '1.0', '2026-08-06 14:50:00', NOW(), 'PENDING', 't5', REPEAT('0',64));

-- 3. 预填画像（模拟画像引擎已计算）
INSERT INTO user_profiles (user_id, profile_version, profile_status, preferred_content_modes_json, preferred_explanation_style, learning_pace, summary_profile, algorithm_version, confidence, last_event_at, last_processed_event_id, profile_data_json, computed_at)
VALUES (23, 1, 'READY', '["video","exercise"]', 'step_by_step', 'moderate', '该学生整体掌握情况良好，勾股定理相关知识点正确率较高。建议继续保持练习频率，重点关注薄弱环节。', 'm6-profile-v1.0.0', 0.67, '2026-08-06 14:50:00', 5, '{}', NOW())
ON DUPLICATE KEY UPDATE profile_status='READY', summary_profile=VALUES(summary_profile);

-- 4. 预填知识点掌握度
INSERT INTO user_knowledge_mastery (user_id, kp_id, profile_version, mastery_score, confidence, mastery_status, evidence_count, trend, algorithm_version, window_start, window_end)
VALUES
(23, 101, 1, 0.85, 0.90, 'MASTERED',       8, 'IMPROVING', 'm6-profile-v1.0.0', '2026-07-07 14:30:00', '2026-08-06 14:50:00'),
(23, 102, 1, 0.72, 0.75, 'BASIC_MASTERY',  4, 'STABLE',    'm6-profile-v1.0.0', '2026-07-07 14:30:00', '2026-08-06 14:50:00'),
(23, 103, 1, 0.45, 0.60, 'CONSOLIDATING',   2, 'STABLE',    'm6-profile-v1.0.0', '2026-07-07 14:30:00', '2026-08-06 14:50:00')
ON DUPLICATE KEY UPDATE mastery_score=VALUES(mastery_score), mastery_status=VALUES(mastery_status);
