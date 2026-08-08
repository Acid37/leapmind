# 用户画像引擎
# 功能：遗忘曲线同步、画像增量更新、全量画像重建、学习风格推断、画像摘要生成
# 由 /api/events/process（03:00）触发增量更新，由 /api/review/calculate-all（02:00）触发全量重建
# 由 /api/internal/ai/build-profile（按需）触发在线画像计算

import json
from datetime import date, datetime, timedelta, timezone

from .config import LEARNING_STYLE_CONFIG, REVIEW_INTERVALS
from .review_scheduler import calc_weakness_score, get_next_review_date, _sync_reminder

GENERATE_AI_SUMMARY = True

MASTERY_STATUS_MAP = {0: 'WEAK', 1: 'CONSOLIDATING', 2: 'BASIC_MASTERY', 3: 'MASTERED'}


def incremental_update(
    user_id: int, kp_id: int, event_type: str, is_correct: bool, conn
) -> None:
    """
    事件驱动的增量更新：处理单条用户事件
    - 原子操作：total_attempts+1、correct_answers+1（答对时）、confusion_count+1（混淆时）
    - 重新计算 weakness_score 并回写
    - 首次遇到该知识点时自动创建排期并同步到 review_reminders
    """
    with conn.cursor() as cur:
        cur.execute(
            "SELECT id, total_attempts, correct_answers, confusion_count "
            "FROM review_schedules WHERE user_id = %s AND kp_id = %s",
            (user_id, kp_id),
        )
        row = cur.fetchone()

        if not row:
            cur.execute(
                "INSERT INTO review_schedules "
                "(user_id, kp_id, total_attempts, correct_answers, review_stage) "
                "VALUES (%s, %s, 1, %s, 0)",
                (user_id, kp_id, 1 if is_correct else 0),
            )
            cur.execute(
                "SELECT name FROM knowledge_points WHERE id = %s",
                (kp_id,),
            )
            kp = cur.fetchone()
            kp_name = kp["name"] if kp else f"kp_{kp_id}"
            next_review_date = date.today() + timedelta(days=REVIEW_INTERVALS[0])
            _sync_reminder(user_id, kp_id, kp_name, 0, next_review_date, 0.0, conn)
            return

        total = row["total_attempts"] + 1
        correct = row["correct_answers"] + (1 if is_correct else 0)
        confused = row["confusion_count"] + (
            1 if event_type == "confused" else 0
        )
        weakness = calc_weakness_score(total, correct, confused)

        cur.execute(
            "UPDATE review_schedules SET "
            "total_attempts = total_attempts + 1, "
            "correct_answers = correct_answers + %s, "
            "confusion_count = confusion_count + %s, "
            "weakness_score = %s "
            "WHERE user_id = %s AND kp_id = %s",
            (1 if is_correct else 0, 1 if event_type == "confused" else 0,
             weakness, user_id, kp_id),
        )


def infer_learning_style(user_id: int, conn) -> str:
    """
    从用户答题行为推断学习风格
    规则（按优先级）：
    1. 答题快且正确率高 → reading（阅读型）
    2. 概念模糊标记多 → visual（视觉型，需要图示辅助）
    3. 答错后频繁重做 → practitioner（实干型）
    4. 以上均不符合 → balanced（均衡型）
    返回值对应 Java V5 user_profiles.preferred_explanation_style
    """
    with conn.cursor() as cur:
        cur.execute(
            "SELECT COUNT(*) AS cnt, "
            "AVG(time_spent) AS avg_time, "
            "AVG(is_correct) AS avg_correct, "
            "SUM(CASE WHEN attempt_count > 1 THEN 1 ELSE 0 END) AS retry_count "
            "FROM user_answers WHERE user_id = %s",
            (user_id,),
        )
        stats = cur.fetchone()

        if not stats or stats["cnt"] == 0:
            return "balanced"

        avg_time = stats["avg_time"] or 0
        avg_correct = stats["avg_correct"] or 0
        retry_count = stats["retry_count"] or 0

        cur.execute(
            "SELECT COUNT(DISTINCT question_id) AS confused_qs "
            "FROM wrong_question_book "
            "WHERE user_id = %s AND wrong_reason_tag = 'concept_unclear'",
            (user_id,),
        )
        confused_qs = cur.fetchone()["confused_qs"] or 0

        cfg = LEARNING_STYLE_CONFIG

        if avg_time < cfg["fast_time_threshold"] and avg_correct > cfg["correct_fast_threshold"]:
            return "reading"

        if confused_qs >= cfg["confused_frequency"]:
            return "visual"

        if retry_count >= cfg["retry_threshold"]:
            return "practitioner"

        return "balanced"


def _infer_learning_pace(user_id: int, conn) -> str:
    """
    从答题耗时推断学习节奏，对应 Java V5 user_profiles.learning_pace
    返回 'slow' / 'moderate' / 'fast'
    """
    with conn.cursor() as cur:
        cur.execute(
            "SELECT AVG(time_spent) AS avg_time FROM user_answers WHERE user_id = %s",
            (user_id,),
        )
        avg_time = cur.fetchone()["avg_time"] or 60
        if avg_time < 20:
            return "fast"
        if avg_time < 60:
            return "moderate"
        return "slow"


def build_summary(
    strengths: list, weaknesses: list, learning_style: str, avg_accuracy: float
) -> str:
    """
    生成画像摘要文本（固定模板，后续可替换为 LLM 调用）
    输出对应 Java V5 user_profiles.summary_profile
    """
    if not GENERATE_AI_SUMMARY:
        return ""
    s_names = [s["name"] for s in strengths[:3]] or ["暂无"]
    w_names = [w["name"] for w in weaknesses[:3]] or ["暂无"]
    return (
        f"该学生整体正确率{avg_accuracy:.0%}，"
        f"学习风格偏向{learning_style}。"
        f"擅长知识点：{'、'.join(s_names)}；"
        f"需加强知识点：{'、'.join(w_names)}。"
        f"建议优先复习薄弱知识点，巩固擅长领域。"
    )


def _sync_to_user_knowledge_mastery(user_id: int, kp_id: int, conn) -> None:
    """
    将 review_schedules 的计算结果同步到 user_knowledge_mastery（Java V6 表）
    review_stage → mastery_status（WEAK/CONSOLIDATING/BASIC_MASTERY/MASTERED）
    weakness_score → mastery_score（反转分数：1 - weakness）
    """
    with conn.cursor() as cur:
        cur.execute(
            "SELECT review_stage, weakness_score, total_attempts, "
            "last_review_at, next_review_at "
            "FROM review_schedules WHERE user_id = %s AND kp_id = %s",
            (user_id, kp_id),
        )
        row = cur.fetchone()
        if not row:
            return

        mastery_score = round(1.0 - (row["weakness_score"] or 0.0), 4)
        mastery_score = max(0.0, min(1.0, mastery_score))
        stage = row["review_stage"]
        status = MASTERY_STATUS_MAP.get(stage, 'INSUFFICIENT_EVIDENCE')
        evidence = row["total_attempts"] or 0
        confidence = round(min(0.5 + evidence * 0.05, 0.95), 4)

        cur.execute(
            "INSERT INTO user_knowledge_mastery "
            "(user_id, kp_id, profile_version, mastery_score, confidence, "
            "mastery_status, evidence_count, algorithm_version, "
            "window_start, window_end) "
            "VALUES (%s, %s, 1, %s, %s, %s, %s, 'm6-v1', %s, %s) "
            "ON DUPLICATE KEY UPDATE "
            "profile_version = profile_version + 1, "
            "mastery_score = VALUES(mastery_score), "
            "confidence = VALUES(confidence), "
            "mastery_status = VALUES(mastery_status), "
            "evidence_count = VALUES(evidence_count), "
            "window_start = VALUES(window_start), "
            "window_end = VALUES(window_end)",
            (user_id, kp_id, mastery_score, confidence, status, evidence,
             row["last_review_at"], row["next_review_at"]),
        )


def _write_user_profiles_v5(user_id: int, conn) -> None:
    """
    聚合 review_schedules 数据，写入 user_profiles（Java V5 表格式）
    满足 chk_user_profiles_ready_or_stale 约束：
      profile_data_json 为非空 JSON 对象、algorithm_version 非空、computed_at 非空
    """
    with conn.cursor() as cur:
        cur.execute(
            "SELECT rs.*, kp.name AS kp_name "
            "FROM review_schedules rs "
            "JOIN knowledge_points kp ON kp.id = rs.kp_id "
            "WHERE rs.user_id = %s",
            (user_id,),
        )
        kp_rows = cur.fetchall()

        if not kp_rows:
            cur.execute(
                "INSERT INTO user_profiles "
                "(user_id, profile_version, profile_status, status_reason, "
                "preferred_content_modes_json, recent_focus_json, "
                "profile_data_json, algorithm_version, computed_at) "
                "VALUES (%s, 1, 'NOT_READY', 'NO_LEARNING_DATA', "
                "'[]', '[]', '{}', 'm6-v1', NOW()) "
                "ON DUPLICATE KEY UPDATE "
                "profile_version = profile_version + 1, "
                "profile_status = 'NOT_READY', "
                "status_reason = 'NO_LEARNING_DATA', "
                "algorithm_version = 'm6-v1', "
                "computed_at = NOW()",
                (user_id,),
            )
            return

        strengths = []
        weaknesses = []
        confusion_history = []
        total_questions = 0
        total_correct = 0

        for r in kp_rows:
            total_questions += r["total_attempts"]
            total_correct += r["correct_answers"]
            correct_rate = (
                r["correct_answers"] / r["total_attempts"]
                if r["total_attempts"] > 0
                else 0
            )
            kp_entry = {
                "kp_id": r["kp_id"],
                "name": r["kp_name"],
                "level": round(correct_rate, 2),
            }
            if correct_rate > 0.8 and r["total_attempts"] > 10:
                strengths.append(kp_entry)
            elif r["weakness_score"] > 0.6 or correct_rate < 0.6:
                kp_entry["reason"] = (
                    "concept_unclear" if r["confusion_count"] > 0 else "careless"
                )
                weaknesses.append(kp_entry)

            if r["confusion_count"] > 0:
                confusion_history.append({
                    "kp_id": r["kp_id"],
                    "name": r["kp_name"],
                    "frequency": r["confusion_count"],
                })

        avg_accuracy = round(total_correct / total_questions, 2) if total_questions > 0 else 0.0
        learning_style = infer_learning_style(user_id, conn)
        learning_pace = _infer_learning_pace(user_id, conn)
        summary = build_summary(strengths, weaknesses, learning_style, avg_accuracy)

        profile_data = json.dumps({
            "strengths": strengths,
            "weaknesses": weaknesses,
            "confusion_history": confusion_history,
            "avg_accuracy": avg_accuracy,
            "total_questions": total_questions,
            "recentConfusions": [],
        }, ensure_ascii=False)

        cur.execute(
            "INSERT INTO user_profiles "
            "(user_id, profile_version, profile_status, "
            "preferred_content_modes_json, recent_focus_json, "
            "profile_data_json, preferred_explanation_style, "
            "learning_pace, summary_profile, "
            "algorithm_version, computed_at) "
            "VALUES (%s, 1, 'READY', "
            "'[]', '[]', %s, %s, %s, %s, "
            "'m6-v1', NOW()) "
            "ON DUPLICATE KEY UPDATE "
            "profile_version = profile_version + 1, "
            "profile_status = 'READY', "
            "preferred_content_modes_json = '[]', "
            "recent_focus_json = '[]', "
            "profile_data_json = VALUES(profile_data_json), "
            "preferred_explanation_style = VALUES(preferred_explanation_style), "
            "learning_pace = VALUES(learning_pace), "
            "summary_profile = VALUES(summary_profile), "
            "algorithm_version = 'm6-v1', "
            "computed_at = NOW()",
            (user_id, profile_data, learning_style, learning_pace, summary),
        )


def build_all_profiles(conn) -> int:
    """
    全量重建所有用户的画像（由 /api/review/calculate-all 触发，02:00 定时任务）
    1. 调 review_scheduler.build_all_schedules() 重建 review_schedules
    2. 对每个用户的每个知识点同步到 user_knowledge_mastery（V6）
    3. 对每个用户写出 user_profiles（V5）
    """
    from .review_scheduler import build_all_schedules as build_schedules
    build_schedules(conn)

    with conn.cursor() as cur:
        cur.execute("SELECT DISTINCT user_id FROM review_schedules")
        users = cur.fetchall()
        for u in users:
            uid = u["user_id"]
            cur.execute(
                "SELECT kp_id FROM review_schedules WHERE user_id = %s",
                (uid,),
            )
            for row in cur.fetchall():
                _sync_to_user_knowledge_mastery(uid, row["kp_id"], conn)
            _write_user_profiles_v5(uid, conn)

    conn.commit()
    return len(users)


def process_new_events(conn) -> int:
    """
    增量处理未处理的事件（由 /api/events/process 触发，03:00 定时任务）
    1. 读取 event_collections WHERE processed=0
    2. 逐条调用 incremental_update 更新 review_schedules 计数器
    3. 同步到 user_knowledge_mastery（V6）
    4. 标记事件为已处理
    5. 重建受影响用户的 user_profiles（V5）
    """
    with conn.cursor() as cur:
        cur.execute(
            "SELECT * FROM event_collections "
            "WHERE processed = 0 ORDER BY event_time ASC"
        )
        events = cur.fetchall()

        if not events:
            return 0

        processed_ids = []
        updated_users = set()

        for ev in events:
            processed_ids.append(ev["id"])
            uid = ev["user_id"]
            event_type = ev["event_type"]
            is_correct = event_type == "answer_correct"

            event_data = {}
            if ev["event_data"]:
                if isinstance(ev["event_data"], str):
                    event_data = json.loads(ev["event_data"])
                else:
                    event_data = ev["event_data"]

            kp_id = event_data.get("kp_id")
            if kp_id is None:
                qid = event_data.get("question_id")
                if qid:
                    cur.execute(
                        "SELECT kp_id FROM question_kp_relations "
                        "WHERE question_id = %s LIMIT 1",
                        (qid,),
                    )
                    kp_row = cur.fetchone()
                    if kp_row:
                        kp_id = kp_row["kp_id"]

            if kp_id:
                incremental_update(uid, kp_id, event_type, is_correct, conn)
                _sync_to_user_knowledge_mastery(uid, kp_id, conn)
                updated_users.add(uid)

        for pid in processed_ids:
            cur.execute(
                "UPDATE event_collections SET processed = 1, "
                "processed_at = NOW() WHERE id = %s",
                (pid,),
            )

        for uid in updated_users:
            _write_user_profiles_v5(uid, conn)

        conn.commit()
        return len(processed_ids)


def compute_profile_from_events(
    user_id: int, events: list[dict]
) -> tuple:
    """
    按 profile-engine-contract.yaml 契约计算在线画像
    接收 Java 发送的 ProfileEvent 列表，返回 (ProfileData dict, KnowledgeMastery list)
    不写数据库，Java 端负责持久化
    """
    if not events:
        return None, []

    kp_stats = {}
    total_events = len(events)
    correct_count = 0
    total_time = 0
    time_count = 0

    for ev in events:
        kp_id = ev.get("kpId")
        event_type = ev.get("eventType", "")
        data = ev.get("data", {}) or {}
        occurred_at = ev.get("occurredAt", datetime.now(timezone.utc).isoformat())

        if kp_id is not None and kp_id > 0:
            if kp_id not in kp_stats:
                kp_stats[kp_id] = {
                    "evidence_count": 0,
                    "correct_count": 0,
                    "first_seen": occurred_at,
                    "last_seen": occurred_at,
                }
            kp_stats[kp_id]["evidence_count"] += 1
            kp_stats[kp_id]["last_seen"] = occurred_at

            if data.get("isCorrect") is True:
                kp_stats[kp_id]["correct_count"] += 1
                correct_count += 1
            elif event_type == "answer_question" and data.get("isCorrect") is False:
                pass
            elif event_type == "finish_practice":
                correct_count += 1 if data.get("score", 0) > 0.5 else 0

        duration = data.get("durationSeconds") or data.get("duration_seconds")
        if duration is not None:
            total_time += duration
            time_count += 1

    if not kp_stats:
        return None, []

    avg_time = total_time / time_count if time_count > 0 else 60
    if avg_time < 20:
        learning_pace = "fast"
    elif avg_time < 60:
        learning_pace = "moderate"
    else:
        learning_pace = "slow"

    overall_accuracy = correct_count / total_events if total_events > 0 else 0
    if overall_accuracy > 0.8:
        learning_style = "reading"
    else:
        learning_style = "balanced"

    from collections import Counter
    type_counter = Counter(ev.get("eventType", "") for ev in events)
    if type_counter.get("request_explanation", 0) >= 3:
        learning_style = "visual"
    elif type_counter.get("mark_reviewed", 0) >= 2:
        learning_style = "practitioner"

    now_iso = datetime.now(timezone.utc).isoformat()

    mastery_items = []
    focus_items = []
    for kp_id, stats in sorted(kp_stats.items()):
        evidence = stats["evidence_count"]
        error_rate = 1.0 - (stats["correct_count"] / evidence if evidence > 0 else 0)
        mastery_score = round(max(0.0, min(1.0, 1.0 - error_rate)), 4)
        confidence = round(min(0.5 + evidence * 0.05, 0.95), 4)

        if evidence < 3:
            status = 'INSUFFICIENT_EVIDENCE'
        elif mastery_score > 0.8:
            status = 'MASTERED'
        elif mastery_score > 0.6:
            status = 'BASIC_MASTERY'
        elif mastery_score > 0.3:
            status = 'CONSOLIDATING'
        else:
            status = 'WEAK'

        mastery_items.append({
            "kpId": kp_id,
            "masteryScore": mastery_score,
            "masteryStatus": status,
            "confidence": confidence,
            "evidenceCount": evidence,
            "algorithmVersion": "m6-v1",
            "windowStart": stats["first_seen"],
            "windowEnd": stats["last_seen"],
            "updatedAt": now_iso,
        })

        focus_items.append({
            "kpId": kp_id,
            "weight": round(evidence / total_events, 4) if total_events > 0 else 0,
        })

    s_names = [f"kp_{s['kpId']}" for s in sorted(kp_stats.values(),
               key=lambda x: x["correct_count"] / x["evidence_count"] if x["evidence_count"] > 0 else 0,
               reverse=True)[:3]]
    w_names = [f"kp_{s['kpId']}" for s in sorted(kp_stats.values(),
               key=lambda x: x["correct_count"] / x["evidence_count"] if x["evidence_count"] > 0 else 1,
               reverse=False)[:3]]
    summary = (
        f"该学生近期正确率{overall_accuracy:.0%}，"
        f"学习风格偏向{learning_style}。"
        f"共参与{total_events}次学习活动，涉及{len(kp_stats)}个知识点。"
    )

    profile = {
        "preferredContentModes": [],
        "preferredExplanationStyle": learning_style,
        "learningPace": learning_pace,
        "recentFocus": focus_items[:20],
        "recentConfusions": [],
        "summaryProfile": summary,
        "confidence": round(min(0.5 + total_events * 0.02, 0.95), 3),
    }

    return profile, mastery_items