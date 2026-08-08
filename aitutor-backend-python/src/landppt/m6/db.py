# MySQL 数据库连接与建表
# 提供 get_conn() 获取连接、init_tables() 自动创建 M6 所需表

import pymysql
from pymysql.cursors import DictCursor
from .config import MYSQL

# review_schedules：复习排期表（每用户+知识点一行）
# 由遗忘曲线算法驱动，记录每个知识点的复习阶段、下次复习日期、薄弱程度
# 全量计算在 02:00 更新，增量更新在 03:00 处理
CREATE_REVIEW_SCHEDULES = """
CREATE TABLE IF NOT EXISTS review_schedules (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    kp_id BIGINT NOT NULL,
    review_stage TINYINT DEFAULT 0,
    mastered TINYINT(1) DEFAULT 0,
    last_review_at DATETIME,
    next_review_at DATETIME,
    review_count INT DEFAULT 0,
    total_attempts INT DEFAULT 0,
    correct_answers INT DEFAULT 0,
    confusion_count INT DEFAULT 0,
    weakness_score DECIMAL(5,2) DEFAULT 0.00,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_kp (user_id, kp_id),
    INDEX idx_next_review (next_review_at),
    INDEX idx_user_stage (user_id, review_stage)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
"""

def get_conn():
    """获取 MySQL 数据库连接（返回 DictCursor，查询结果以字典形式返回）"""
    return pymysql.connect(
        host=MYSQL["host"],
        port=MYSQL["port"],
        user=MYSQL["user"],
        password=MYSQL["password"],
        database=MYSQL["database"],
        charset=MYSQL["charset"],
        cursorclass=DictCursor,
    )


def init_tables():
    """应用启动时调用，CREATE TABLE IF NOT EXISTS 确保 review_schedules 表存在（幂等安全）"""
    conn = get_conn()
    try:
        with conn.cursor() as cur:
            cur.execute(CREATE_REVIEW_SCHEDULES)
        conn.commit()
    finally:
        conn.close()
