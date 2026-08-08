"""M6 事件驱动处理 / 增量更新测试。

覆盖 profile_engine.py 中与事件流水线相关的函数：
- incremental_update：单事件增量更新排期计数。
- _sync_to_user_knowledge_mastery：同步到 V6 掌握度表（含分数夹取）。
- process_new_events：批处理未消费事件并标记已处理。
- build_all_profiles：全量画像重建（经 review_scheduler 构建排期后回写）。
"""

import json
from datetime import date, timedelta

from landppt.m6.profile_engine import (
    _sync_to_user_knowledge_mastery,
    build_all_profiles,
    incremental_update,
    process_new_events,
)
from landppt.m6.review_scheduler import calc_weakness_score


# ---------------------------------------------------------------------------
# incremental_update 单事件增量更新（mock 隔离数据库）
# ---------------------------------------------------------------------------

def test_incremental_update_creates_new_correct(make_conn, sql_asserts):
    """首次遇到知识点且答对：创建 total_attempts=1、correct_answers=1 的初始排期。"""
    conn, cursor = make_conn(
        fetchone=[None, {"name": "导数"}, None],
    )

    incremental_update(7, 9, "answer_correct", True, conn)

    params = sql_asserts["params_for"](cursor, "INSERT INTO review_schedules")
    assert params == (7, 9, 1)
    sql_asserts["contains"](cursor, "INSERT INTO review_reminders")


def test_incremental_update_creates_new_wrong(make_conn, sql_asserts):
    """首次遇到知识点且答错：创建 total_attempts=1、correct_answers=0 的初始排期。"""
    conn, cursor = make_conn(
        fetchone=[None, {"name": "导数"}, None],
    )

    incremental_update(7, 9, "answer_wrong", False, conn)

    params = sql_asserts["params_for"](cursor, "INSERT INTO review_schedules")
    assert params == (7, 9, 0)


def test_incremental_update_creates_new_missing_name(make_conn, sql_asserts):
    """知识点名称缺失时使用 kp_{id} 兜底命名。"""
    conn, cursor = make_conn(
        fetchone=[None, None, None],
    )

    incremental_update(7, 9, "answer_correct", True, conn)

    params = sql_asserts["params_for"](cursor, "INSERT INTO review_reminders")
    assert params[2] == "kp_9"


def test_incremental_update_existing_correct(make_conn, sql_asserts):
    """已存在排期：答对时正确数 +1 并重算薄弱分。"""
    conn, cursor = make_conn(fetchone=[
        {"id": 1, "total_attempts": 5, "correct_answers": 3, "confusion_count": 0},
    ])

    incremental_update(7, 9, "answer_correct", True, conn)

    params = sql_asserts["params_for"](cursor, "UPDATE review_schedules")
    # 计算后：total=6, correct=4, confused=0
    expected_weakness = calc_weakness_score(6, 4, 0)
    assert params == (1, 0, expected_weakness, 7, 9)


def test_incremental_update_existing_confused(make_conn, sql_asserts):
    """混淆事件：混淆计数 +1 并重算薄弱分（其他计数不变）。"""
    conn, cursor = make_conn(fetchone=[
        {"id": 1, "total_attempts": 5, "correct_answers": 3, "confusion_count": 0},
    ])

    incremental_update(7, 9, "confused", False, conn)

    params = sql_asserts["params_for"](cursor, "UPDATE review_schedules")
    expected_weakness = calc_weakness_score(6, 3, 1)
    assert params == (0, 1, expected_weakness, 7, 9)


# ---------------------------------------------------------------------------
# _sync_to_user_knowledge_mastery 掌握度同步（mock 隔离数据库）
# ---------------------------------------------------------------------------

def test_sync_mastery_no_row_is_noop(make_conn, sql_asserts):
    """排期不存在时直接返回，不做任何写入。"""
    conn, cursor = make_conn(fetchone=[None])

    _sync_to_user_knowledge_mastery(7, 9, conn)

    assert sql_asserts["execute_count"](cursor) == 1


def test_sync_mastery_weak_stage_zero(make_conn, sql_asserts):
    """stage=0、weakness=0.5：mastery_score=0.5，状态 WEAK，confidence 随证据增长。"""
    last_review = date(2026, 7, 20)
    next_review = date(2026, 7, 21)
    conn, cursor = make_conn(fetchone=[
        {"review_stage": 0, "weakness_score": 0.5, "total_attempts": 4,
         "last_review_at": last_review, "next_review_at": next_review},
    ])

    _sync_to_user_knowledge_mastery(7, 9, conn)

    sql_asserts["contains"](cursor, "INSERT INTO user_knowledge_mastery")
    params = sql_asserts["params_for"](cursor, "INSERT INTO user_knowledge_mastery")
    # (user_id, kp_id, mastery_score, confidence, mastery_status,
    #  evidence_count, last_review_at, next_review_at)
    assert params[0] == 7
    assert params[1] == 9
    assert params[2] == 0.5
    assert params[3] == 0.7          # min(0.5 + 4*0.05, 0.95)
    assert params[4] == "WEAK"
    assert params[5] == 4
    assert params[6] == last_review
    assert params[7] == next_review


def test_sync_mastery_mastered_stage_three(make_conn, sql_asserts):
    """stage=3、weakness=0.0：mastery_score=1.0，状态 MASTERED。"""
    conn, cursor = make_conn(fetchone=[
        {"review_stage": 3, "weakness_score": 0.0, "total_attempts": 30,
         "last_review_at": None, "next_review_at": None},
    ])

    _sync_to_user_knowledge_mastery(7, 9, conn)

    params = sql_asserts["params_for"](cursor, "INSERT INTO user_knowledge_mastery")
    assert params[2] == 1.0
    assert params[4] == "MASTERED"
    assert params[3] == 0.95         # min(0.5 + 30*0.05, 0.95) 已封顶


def test_sync_mastery_clamps_out_of_range(make_conn, sql_asserts):
    """越界薄弱分（1.5）应被夹取到合法区间 [0,1]，不会写出负掌握度。"""
    conn, cursor = make_conn(fetchone=[
        {"review_stage": 0, "weakness_score": 1.5, "total_attempts": 1,
         "last_review_at": None, "next_review_at": None},
    ])

    _sync_to_user_knowledge_mastery(7, 9, conn)

    params = sql_asserts["params_for"](cursor, "INSERT INTO user_knowledge_mastery")
    assert params[2] == 0.0
    assert params[4] == "WEAK"


# ---------------------------------------------------------------------------
# process_new_events 批处理事件（mock 隔离数据库）
# ---------------------------------------------------------------------------

def test_process_events_empty(make_conn, sql_asserts):
    """无待处理事件时返回 0，且不执行任何写入。"""
    conn, cursor = make_conn(fetchall=[[]])

    count = process_new_events(conn)

    assert count == 0
    assert sql_asserts["execute_count"](cursor) == 1


def test_process_events_single_correct(make_conn, sql_asserts):
    """单条正确事件：更新排期、同步掌握度、标记已处理、重建画像。"""
    events = [{
        "id": 1, "user_id": 7, "event_type": "answer_correct",
        "event_data": json.dumps({"kp_id": 9}),
    }]
    conn, cursor = make_conn(
        fetchall=[events, []],
        fetchone=[
            # incremental_update 查询既有排期
            {"id": 1, "total_attempts": 5, "correct_answers": 3, "confusion_count": 0},
            # _sync_to_user_knowledge_mastery 查询排期
            {"review_stage": 0, "weakness_score": 0.3, "total_attempts": 6,
             "last_review_at": None, "next_review_at": None},
        ],
    )

    count = process_new_events(conn)

    assert count == 1
    sql_asserts["contains"](cursor, "UPDATE review_schedules")
    sql_asserts["contains"](cursor, "INSERT INTO user_knowledge_mastery")
    sql_asserts["contains"](cursor, "UPDATE event_collections")
    params = sql_asserts["params_for"](cursor, "UPDATE event_collections")
    assert params == (1,)


def test_process_events_question_id_fallback(make_conn, sql_asserts):
    """事件仅带 question_id 时通过题目-知识点映射回查 kp_id。"""
    events = [{
        "id": 2, "user_id": 7, "event_type": "answer_wrong",
        "event_data": {"question_id": 55},
    }]
    conn, cursor = make_conn(
        fetchall=[events, []],
        fetchone=[
            {"kp_id": 9},   # question_kp_relations 回查
            {"id": 1, "total_attempts": 0, "correct_answers": 0, "confusion_count": 0},
            {"review_stage": 0, "weakness_score": 1.0, "total_attempts": 1,
             "last_review_at": None, "next_review_at": None},
        ],
    )

    count = process_new_events(conn)

    assert count == 1
    sql_asserts["contains"](cursor, "UPDATE review_schedules")
    sql_asserts["contains"](cursor, "question_kp_relations")


def test_process_events_no_kp_still_marks_processed(make_conn, sql_asserts):
    """无法解析 kp_id 的事件不更新画像，但仍标记为已处理。"""
    events = [{
        "id": 3, "user_id": 7, "event_type": "open_lesson",
        "event_data": json.dumps({}),
    }]
    conn, cursor = make_conn(fetchall=[events])

    count = process_new_events(conn)

    assert count == 1
    sql_asserts["contains"](cursor, "UPDATE event_collections")
    # 没有增量更新 / 掌握度同步 / 画像重建
    assert "UPDATE review_schedules" not in "\n".join(sql_asserts["fragments"](cursor))


# ---------------------------------------------------------------------------
# build_all_profiles 全量画像重建（mock 隔离数据库）
# ---------------------------------------------------------------------------

def test_build_all_profiles_empty(make_conn):
    """无任何答题数据时返回 0。"""
    conn, _ = make_conn(fetchall=[[], []])

    assert build_all_profiles(conn) == 0


def test_build_all_profiles_single_user(make_conn, sql_asserts):
    """单用户单知识点：构建排期并同步掌握度与画像。"""
    conn, cursor = make_conn(
        fetchall=[
            # review_scheduler.build_all_schedules 的 distinct 用户-知识点
            [{"user_id": 7, "kp_id": 9, "kp_name": "导数"}],
            # build_all_profiles 的 distinct 用户
            [{"user_id": 7}],
            # 该用户的 kp 列表
            [{"kp_id": 9}],
            # _write_user_profiles_v5 的排期明细（空 → NOT_READY）
            [],
        ],
        fetchone=[
            # build_all_schedules：答题统计 / 混淆统计 / 提醒查询
            {"cnt": 5, "correct": 3},
            {"confused": 1},
            None,
            # _sync_to_user_knowledge_mastery：排期详情
            {"review_stage": 0, "weakness_score": 0.36, "total_attempts": 5,
             "last_review_at": None, "next_review_at": None},
        ],
    )

    count = build_all_profiles(conn)

    assert count == 1
    sql_asserts["contains"](cursor, "INSERT INTO review_schedules")
    sql_asserts["contains"](cursor, "INSERT INTO user_knowledge_mastery")
    sql_asserts["contains"](cursor, "INSERT INTO user_profiles")
    sql_asserts["contains"](cursor, "NOT_READY")
