"""一键初始化 M6 前后端联调测试数据"""
import pymysql

conn = pymysql.connect(host='localhost', port=3306, user='root', password='306389', database='leapmind')
cur = conn.cursor()

# 1. 清理
cur.execute('DELETE FROM user_knowledge_mastery WHERE user_id IN (22,23)')
cur.execute('DELETE FROM user_profiles WHERE user_id IN (22,23)')
cur.execute('DELETE FROM user_events WHERE user_id IN (22,23)')
cur.execute('DELETE FROM users WHERE id IN (22,23)')
print('1. 旧数据已清理')

# 2. 用户
cur.execute("INSERT INTO users(id,username,password,grade,status) VALUES(23,'m6_apifox_20260805','$2a$10$kDI6tuzz8BdhewKVa/LFAOM7JE2m9BhkMTJmjtAWfmuPenwjEqlsG','GRADE_1',1)")
print('2. 用户 23 已创建')

# 3. 事件
events = [
    ('e2e-001', 23, 'answer_question', 'M1', 'sess-01', 101, '{"isCorrect":true,"difficulty":3,"timeSpentSec":45,"hintCount":0}', '1.0', '2026-08-06 14:30:00', 'PENDING', 't1'),
    ('e2e-002', 23, 'answer_question', 'M1', 'sess-01', 101, '{"isCorrect":false,"difficulty":4,"timeSpentSec":60,"hintCount":1}', '1.0', '2026-08-06 14:35:00', 'PENDING', 't2'),
    ('e2e-003', 23, 'answer_question', 'M1', 'sess-01', 101, '{"isCorrect":true,"difficulty":2,"timeSpentSec":30,"hintCount":0}', '1.0', '2026-08-06 14:40:00', 'PENDING', 't3'),
    ('e2e-004', 23, 'answer_question', 'M1', 'sess-02', 102, '{"isCorrect":true,"difficulty":3,"timeSpentSec":40,"hintCount":0}', '1.0', '2026-08-06 14:45:00', 'PENDING', 't4'),
    ('e2e-005', 23, 'answer_question', 'M1', 'sess-03', 103, '{"isCorrect":true,"difficulty":2,"timeSpentSec":35,"hintCount":0}', '1.0', '2026-08-06 14:50:00', 'PENDING', 't5'),
]
for e in events:
    cur.execute("INSERT INTO user_events(event_id,user_id,event_type,source_module,session_id,kp_id,event_data_json,schema_version,occurred_at,received_at,process_status,trace_id,payload_hash) VALUES(%s,%s,%s,%s,%s,%s,%s,%s,%s,NOW(),%s,%s,REPEAT('0',64))", e)
print('3. 5 条事件已写入')

# 4. 画像
cur.execute("INSERT INTO user_profiles(user_id,profile_version,profile_status,preferred_content_modes_json,preferred_explanation_style,learning_pace,summary_profile,algorithm_version,confidence,last_event_at,last_processed_event_id,profile_data_json,computed_at) VALUES(23,1,'READY','[\"video\",\"exercise\"]','step_by_step','moderate','该学生整体掌握情况良好。','m6-profile-v1.0.0',0.67,'2026-08-06 14:50:00',5,'{}',NOW())")
print('4. 画像已写入')

# 5. 知识点
kps = [
    (23, 101, 1, 0.85, 0.90, 'MASTERED', 8, 'IMPROVING'),
    (23, 102, 1, 0.72, 0.75, 'BASIC_MASTERY', 4, 'STABLE'),
    (23, 103, 1, 0.45, 0.60, 'CONSOLIDATING', 2, 'STABLE'),
]
for k in kps:
    cur.execute("INSERT INTO user_knowledge_mastery(user_id,kp_id,profile_version,mastery_score,confidence,mastery_status,evidence_count,trend,algorithm_version,window_start,window_end) VALUES(%s,%s,%s,%s,%s,%s,%s,%s,'m6-profile-v1.0.0','2026-07-07 14:30:00','2026-08-06 14:50:00')", k)
print('5. 3 个知识点已写入')

conn.commit()
conn.close()
print('\n数据库初始化完成！')
