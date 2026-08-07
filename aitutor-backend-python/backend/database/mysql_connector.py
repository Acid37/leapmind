"""
数据库连接层 — 使用 pymysql 直连 MySQL
"""
import os
from datetime import datetime, date
from typing import Optional

import pymysql
from pymysql.cursors import DictCursor
from dotenv import load_dotenv

load_dotenv()  # 支持 .env 文件配置数据库连接

# ── 默认连接配置（可通过 .env 或环境变量覆盖） ──
DB_CONFIG = {
    "host": os.getenv("DB_HOST", "127.0.0.1"),
    "port": int(os.getenv("DB_PORT", "3306")),
    "user": os.getenv("DB_USER", "root"),
    "password": os.getenv("DB_PASSWORD", ""),
    "database": os.getenv("DB_NAME", "leapmind"),
    "charset": "utf8mb4",
}


class Database:
    """轻量数据库连接池（每次查询获取新连接，使用后关闭）"""

    def __init__(self, config: dict = None):
        self.config = config or DB_CONFIG

    def _get_conn(self):
        return pymysql.connect(**self.config, cursorclass=DictCursor)

    # ──────────────────────────────────────────
    # 查询方法
    # ──────────────────────────────────────────

    def fetch_user_answers(self, user_id: int) -> list[dict]:
        """获取用户所有答题记录"""
        sql = """
            SELECT id, question_id, kp_id, is_correct, created_at
            FROM user_answers
            WHERE user_id = %s
            ORDER BY created_at DESC
        """
        conn = self._get_conn()
        try:
            with conn.cursor() as cur:
                cur.execute(sql, (user_id,))
                return cur.fetchall()
        finally:
            conn.close()

    def fetch_user_answers_by_kp(self, user_id: int, kp_id: int) -> list[dict]:
        """获取用户某个知识点的答题记录"""
        sql = """
            SELECT id, question_id, is_correct, created_at
            FROM user_answers
            WHERE user_id = %s AND kp_id = %s
            ORDER BY created_at DESC
        """
        conn = self._get_conn()
        try:
            with conn.cursor() as cur:
                cur.execute(sql, (user_id, kp_id))
                return cur.fetchall()
        finally:
            conn.close()

    def fetch_knowledge_points(self) -> list[dict]:
        """获取所有知识点"""
        sql = "SELECT id, name, parent_id, subject, level FROM knowledge_points"
        conn = self._get_conn()
        try:
            with conn.cursor() as cur:
                cur.execute(sql)
                return cur.fetchall()
        finally:
            conn.close()

    def fetch_child_kps(self, parent_id: int) -> list[dict]:
        """获取某个知识点的子知识点"""
        sql = "SELECT id, name, parent_id, subject, level FROM knowledge_points WHERE parent_id = %s"
        conn = self._get_conn()
        try:
            with conn.cursor() as cur:
                cur.execute(sql, (parent_id,))
                return cur.fetchall()
        finally:
            conn.close()

    def fetch_conversation_messages(self, user_id: int) -> list[dict]:
        """获取用户的提问/对话记录"""
        sql = """
            SELECT id, content, kp_id, role, created_at
            FROM conversation_messages
            WHERE user_id = %s
            ORDER BY created_at DESC
        """
        conn = self._get_conn()
        try:
            with conn.cursor() as cur:
                cur.execute(sql, (user_id,))
                return cur.fetchall()
        finally:
            conn.close()

    def fetch_questions_by_kp(self, kp_id: int) -> list[dict]:
        """获取某个知识点下的所有题目"""
        sql = """
            SELECT id, kp_id, content, difficulty
            FROM questions
            WHERE kp_id = %s
            ORDER BY difficulty ASC
        """
        conn = self._get_conn()
        try:
            with conn.cursor() as cur:
                cur.execute(sql, (kp_id,))
                return cur.fetchall()
        finally:
            conn.close()

    def fetch_answered_question_ids(self, user_id: int, kp_id: int) -> list[int]:
        """获取用户在某知识点下已做过的题目 ID"""
        sql = """
            SELECT DISTINCT question_id
            FROM user_answers
            WHERE user_id = %s AND kp_id = %s
        """
        conn = self._get_conn()
        try:
            with conn.cursor() as cur:
                cur.execute(sql, (user_id, kp_id))
                return [row["question_id"] for row in cur.fetchall()]
        finally:
            conn.close()

    def fetch_wrong_question_book(self, user_id: int) -> list[dict]:
        """获取用户的错题本记录（M1提供）"""
        sql = """
            SELECT id, question_id, kp_id, created_at
            FROM wrong_question_book
            WHERE user_id = %s
            ORDER BY created_at DESC
        """
        conn = self._get_conn()
        try:
            with conn.cursor() as cur:
                cur.execute(sql, (user_id,))
                return cur.fetchall()
        finally:
            conn.close()

    def fetch_user_profile(self, user_id: int) -> Optional[dict]:
        """获取用户画像信息（M6提供）"""
        sql = """
            SELECT id, user_id, confusion_history_json, learning_style
            FROM user_profiles
            WHERE user_id = %s
            LIMIT 1
        """
        conn = self._get_conn()
        try:
            with conn.cursor() as cur:
                cur.execute(sql, (user_id,))
                return cur.fetchone()
        finally:
            conn.close()

    def fetch_last_answer_time(self, user_id: int) -> Optional[datetime]:
        """获取用户最近一次答题时间"""
        sql = """
            SELECT created_at FROM user_answers
            WHERE user_id = %s
            ORDER BY created_at DESC
            LIMIT 1
        """
        conn = self._get_conn()
        try:
            with conn.cursor() as cur:
                cur.execute(sql, (user_id,))
                row = cur.fetchone()
                return row["created_at"] if row else None
        finally:
            conn.close()

    def fetch_last_error_time(self, user_id: int, kp_id: int) -> Optional[datetime]:
        """获取用户在某知识点上最近一次错题时间"""
        sql = """
            SELECT created_at FROM user_answers
            WHERE user_id = %s AND kp_id = %s AND is_correct = 0
            ORDER BY created_at DESC
            LIMIT 1
        """
        conn = self._get_conn()
        try:
            with conn.cursor() as cur:
                cur.execute(sql, (user_id, kp_id))
                row = cur.fetchone()
                return row["created_at"] if row else None
        finally:
            conn.close()

    # ──────────────────────────────────────────
    # 写入方法
    # ──────────────────────────────────────────

    def upsert_weak_point(self, record: dict) -> None:
        """写入或更新用户薄弱点记录"""
        sql = """
            INSERT INTO user_weak_points
                (user_id, kp_id, knowledge_point, weakness_score, error_count, total_attempts,
                 error_rate, recent_correct_rate, confusion_count, trend,
                 last_error_at, calculated_at)
            VALUES
                (%(user_id)s, %(kp_id)s, %(knowledge_point)s, %(weakness_score)s, %(error_count)s, %(total_attempts)s,
                 %(error_rate)s, %(recent_correct_rate)s, %(confusion_count)s, %(trend)s,
                 %(last_error_at)s, %(calculated_at)s)
            ON DUPLICATE KEY UPDATE
                kp_id             = VALUES(kp_id),
                knowledge_point   = VALUES(knowledge_point),
                weakness_score    = VALUES(weakness_score),
                error_count       = VALUES(error_count),
                total_attempts    = VALUES(total_attempts),
                error_rate        = VALUES(error_rate),
                recent_correct_rate = VALUES(recent_correct_rate),
                confusion_count   = VALUES(confusion_count),
                trend             = VALUES(trend),
                last_error_at     = VALUES(last_error_at),
                calculated_at     = VALUES(calculated_at)
        """
        conn = self._get_conn()
        try:
            with conn.cursor() as cur:
                cur.execute(sql, record)
            conn.commit()
        finally:
            conn.close()

    def batch_upsert_weak_points(self, records: list[dict]) -> None:
        """批量写入薄弱点记录"""
        for rec in records:
            self.upsert_weak_point(rec)
