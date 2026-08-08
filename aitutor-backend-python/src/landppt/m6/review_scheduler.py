# 遗忘曲线复习排期引擎
# 核心算法：基于艾宾浩斯遗忘曲线（间隔 1→3→7→30 天）
# 管理 review_schedules 表（排期管理）和 review_reminders 表（前端展示）

from datetime import date, datetime, timedelta

from .config import REVIEW_INTERVALS, MAX_STAGE


def calc_next_review_delay(stage: int) -> int:
    """
    根据当前复习阶段返回下次复习距今天数
    stage 0→1天, 1→3天, 2→7天, stage≥3→30天（最后间隔）
    """
    idx = min(stage, len(REVIEW_INTERVALS) - 1)
    return REVIEW_INTERVALS[idx]


def get_next_review_date(stage: int, last_review: date = None) -> date:
    """计算下次复习的具体日期"""
    base = last_review or date.today()
    return base + timedelta(days=calc_next_review_delay(stage))


def calc_weakness_score(total: int, correct: int, confused: int) -> float:
    """
    计算知识点薄弱程度分数（0.0~1.0）
    公式：0.4×错误率 + 0.4×(1−正确率) + 0.2×混淆频率
    值越高表示越薄弱
    """
    if total == 0:
        return 0.0
    error_rate = (total - correct) / total
    recent_correct_rate = correct / total
    confusion_freq = confused / total
    return round(
        0.4 * error_rate + 0.4 * (1 - recent_correct_rate) + 0.2 * confusion_freq, 2
    )


def _derive_priority(weakness_score: float) -> int:
    """将薄弱分数映射为 review_reminders 的优先级：0-普通 1-重要 2-紧急"""
    if weakness_score >= 0.7:
        return 2
    if weakness_score >= 0.3:
        return 1
    return 0


def on_learned(user_id: int, kp_id: int, kp_name: str, conn) -> None:
    """
    用户学习了新知识点时调用
    - 如果 review_schedules 已有记录 → 推进复习阶段
    - 如果不存在 → 创建初始排期（stage=0，1天后复习）
    """
    with conn.cursor() as cur:
        cur.execute(
            "SELECT id, review_stage, mastered FROM review_schedules "
            "WHERE user_id = %s AND kp_id = %s",
            (user_id, kp_id),
        )
        row = cur.fetchone()

        if row:
            if not row["mastered"]:
                advance_stage(user_id, kp_id, kp_name, conn)
        else:
            today = date.today()
            next_review = today + timedelta(days=REVIEW_INTERVALS[0])
            cur.execute(
                "INSERT INTO review_schedules "
                "(user_id, kp_id, review_stage, last_review_at, next_review_at) "
                "VALUES (%s, %s, 0, %s, %s)",
                (user_id, kp_id, today, next_review),
            )
            _sync_reminder(user_id, kp_id, kp_name, 0, next_review, 0.0, conn)


def advance_stage(user_id: int, kp_id: int, kp_name: str, conn) -> None:
    """
    推进知识点复习阶段（stage+1）
    - 计算下次复习日期
    - 如果 stage ≥ MAX_STAGE（3），标记为 mastered
    - 同步到 review_reminders 表供 Java 前端读取
    """
    with conn.cursor() as cur:
        cur.execute(
            "SELECT review_stage, weakness_score FROM review_schedules "
            "WHERE user_id = %s AND kp_id = %s",
            (user_id, kp_id),
        )
        row = cur.fetchone()
        if not row:
            return

        new_stage = row["review_stage"] + 1
        weakness = row["weakness_score"]
        mastered = 1 if new_stage >= MAX_STAGE else 0
        today = date.today()
        next_review = get_next_review_date(new_stage, today)

        cur.execute(
            "UPDATE review_schedules SET review_stage = %s, mastered = %s, "
            "last_review_at = %s, next_review_at = %s, review_count = review_count + 1 "
            "WHERE user_id = %s AND kp_id = %s",
            (new_stage, mastered, today, next_review, user_id, kp_id),
        )
        _sync_reminder(user_id, kp_id, kp_name, new_stage, next_review, weakness, conn)


def get_overdue_schedules(user_id: int, conn) -> list[dict]:
    """查询用户逾期未复习的知识点（next_review_at ≤ 今天 且 未掌握）"""
    with conn.cursor() as cur:
        cur.execute(
            "SELECT * FROM review_schedules "
            "WHERE user_id = %s AND mastered = 0 AND next_review_at <= CURDATE() "
            "ORDER BY next_review_at ASC",
            (user_id,),
        )
        return cur.fetchall()


def build_all_schedules(conn) -> int:
    """
    全量构建所有用户的复习排期（由 /api/review/calculate-all 触发，02:00 定时任务）
    从 user_answers + question_kp_relations 读取答题记录，
    为每个用户的每个知识点计算初始排期（stage=0），
    结果写入 review_schedules，同时同步到 review_reminders
    """
    with conn.cursor() as cur:
        cur.execute(
            "SELECT DISTINCT ua.user_id, qkr.kp_id, kp.name AS kp_name "
            "FROM user_answers ua "
            "JOIN question_kp_relations qkr ON qkr.question_id = ua.question_id "
            "JOIN knowledge_points kp ON kp.id = qkr.kp_id"
        )
        rows = cur.fetchall()

        users_kps: dict[int, set] = {}
        for r in rows:
            uid = r["user_id"]
            if uid not in users_kps:
                users_kps[uid] = set()
            users_kps[uid].add((r["kp_id"], r["kp_name"]))

        total = 0
        for uid, kps in users_kps.items():
            for kp_id, kp_name in kps:
                cur.execute(
                    "SELECT COUNT(*) AS cnt, SUM(is_correct) AS correct "
                    "FROM user_answers ua "
                    "JOIN question_kp_relations qkr ON qkr.question_id = ua.question_id "
                    "WHERE ua.user_id = %s AND qkr.kp_id = %s",
                    (uid, kp_id),
                )
                stats = cur.fetchone()
                total_attempts = stats["cnt"] or 0
                correct_answers = stats["correct"] or 0

                cur.execute(
                    "SELECT COUNT(*) AS confused FROM wrong_question_book "
                    "WHERE user_id = %s AND question_id IN ("
                    "  SELECT question_id FROM question_kp_relations WHERE kp_id = %s"
                    ")",
                    (uid, kp_id),
                )
                confused = cur.fetchone()["confused"] or 0

                weakness = calc_weakness_score(
                    total_attempts, correct_answers, confused
                )

                stage = 0
                mastered = 1 if stage >= MAX_STAGE else 0
                next_review = get_next_review_date(stage)

                cur.execute(
                    "INSERT INTO review_schedules "
                    "(user_id, kp_id, review_stage, mastered, "
                    "next_review_at, total_attempts, correct_answers, "
                    "confusion_count, weakness_score) "
                    "VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s) "
                    "ON DUPLICATE KEY UPDATE "
                    "review_stage = VALUES(review_stage), "
                    "mastered = VALUES(mastered), "
                    "next_review_at = VALUES(next_review_at), "
                    "total_attempts = VALUES(total_attempts), "
                    "correct_answers = VALUES(correct_answers), "
                    "confusion_count = VALUES(confusion_count), "
                    "weakness_score = VALUES(weakness_score)",
                    (
                        uid,
                        kp_id,
                        stage,
                        mastered,
                        next_review,
                        total_attempts,
                        correct_answers,
                        confused,
                        weakness,
                    ),
                )
                _sync_reminder(
                    uid, kp_id, kp_name, stage, next_review, weakness, conn
                )
                total += 1

        conn.commit()
        return total


def _sync_reminder(
    user_id: int,
    kp_id: int,
    kp_name: str,
    stage: int,
    next_review_date: date,
    weakness_score: float,
    conn,
) -> None:
    """
    将排期同步到 review_reminders 表
    该表由 Java UserProfileController 读取，供前端展示复习提醒
    - 已有未完成的提醒 → 更新日期和优先级
    - 没有提醒 → 新增一条 SPACED_REPETITION 类型提醒
    """
    priority = _derive_priority(weakness_score)
    course_id = str(kp_id)
    with conn.cursor() as cur:
        cur.execute(
            "SELECT id FROM review_reminders "
            "WHERE user_id = %s AND course_id = %s AND is_reviewed = 0",
            (user_id, course_id),
        )
        existing = cur.fetchone()
        if existing:
            cur.execute(
                "UPDATE review_reminders SET scheduled_date = %s, priority = %s, "
                "content = %s WHERE id = %s",
                (next_review_date, priority, kp_name, existing["id"]),
            )
        else:
            cur.execute(
                "INSERT INTO review_reminders "
                "(user_id, course_id, reminder_type, content, scheduled_date, priority, is_reviewed) "
                "VALUES (%s, %s, 'SPACED_REPETITION', %s, %s, %s, 0)",
                (user_id, course_id, kp_name, next_review_date, priority),
            )
