"""M6 用户画像引擎测试。

覆盖 profile_engine.py：
- 纯函数：build_summary 摘要生成、compute_profile_from_events 在线画像计算（无需 mock）。
- 学习风格 / 学习节奏推断（mock 统计查询）。
- 画像落盘 _write_user_profiles_v5（mock 隔离数据库）。
"""

import json
from datetime import datetime, timezone
from unittest import mock

from landppt.m6.profile_engine import (
    GENERATE_AI_SUMMARY,
    _infer_learning_pace,
    _write_user_profiles_v5,
    build_summary,
    compute_profile_from_events,
    infer_learning_style,
)


# ---------------------------------------------------------------------------
# build_summary 摘要生成（纯函数）
# ---------------------------------------------------------------------------

def test_build_summary_empty_lists():
    """无强弱项数据时应输出"暂无"占位。"""
    text = build_summary([], [], "balanced", 0.5)
    assert "暂无" in text
    assert "学习风格偏向balanced" in text
    assert "50%" in text


def test_build_summary_with_names():
    """应拼接擅长与需加强的知识点名称，正确率百分比化。"""
    strengths = [{"name": "导数"}, {"name": "积分"}]
    weaknesses = [{"name": "概率"}]
    text = build_summary(strengths, weaknesses, "reading", 0.8)
    assert "导数、积分" in text
    assert "概率" in text
    assert "80%" in text


def test_build_summary_truncates_to_three():
    """超过 3 个知识点时应只取前 3 个。"""
    strengths = [{"name": f"s{i}"} for i in range(5)]
    text = build_summary(strengths, [], "visual", 0.9)
    for name in ["s0", "s1", "s2"]:
        assert name in text
    assert "s3" not in text


def test_build_summary_flag_disabled_returns_empty(monkeypatch):
    """关闭 AI 摘要开关时返回空字符串。"""
    monkeypatch.setattr("landppt.m6.profile_engine.GENERATE_AI_SUMMARY", False)
    assert build_summary([], [], "balanced", 0.5) == ""


# ---------------------------------------------------------------------------
# compute_profile_from_events 在线画像计算（纯函数）
# ---------------------------------------------------------------------------

def test_compute_profile_empty_events():
    """空事件列表应返回 (None, [])。"""
    assert compute_profile_from_events(7, []) == (None, [])


def test_compute_profile_without_kp():
    """所有事件无 kpId 时应返回 (None, [])。"""
    events = [
        {"eventType": "answer_question", "data": {"isCorrect": True}},
        {"eventType": "open_lesson", "data": {}},
    ]
    assert compute_profile_from_events(7, events) == (None, [])


# ---------------------------------------------------------------------------
# compute_profile_from_events 正常流程（修复后）
#
# 修复 profile_engine.py:511-516：删除对 kp_stats.values() 取 s['kpId'] 的
# 死代码（s_names/w_names），消除任何非空事件必抛 KeyError('kpId') 的问题。
# 修复后函数应正常产出画像与按知识点掌握度。
# ---------------------------------------------------------------------------

def test_compute_profile_single_correct_fast():
    """单条正确且答题快：learning_style=reading、learning_pace=fast、证据不足。"""
    events = [
        {"kpId": 5, "eventType": "answer_question",
         "data": {"isCorrect": True, "durationSeconds": 10}},
    ]
    profile, mastery = compute_profile_from_events(7, events)

    assert profile is not None
    assert profile["preferredExplanationStyle"] == "reading"
    assert profile["learningPace"] == "fast"
    assert "该学生近期正确率100%" in profile["summaryProfile"]
    assert len(mastery) == 1
    item = mastery[0]
    assert item["kpId"] == 5
    assert item["masteryScore"] == 1.0
    assert item["masteryStatus"] == "INSUFFICIENT_EVIDENCE"


def test_compute_profile_mixed_answers():
    """一正确一错误：overall=0.5→balanced，无时长→pace 默认慢速。"""
    events = [
        {"kpId": 5, "eventType": "answer_question", "data": {"isCorrect": True}},
        {"kpId": 5, "eventType": "answer_question", "data": {"isCorrect": False}},
    ]
    profile, mastery = compute_profile_from_events(7, events)

    assert profile["preferredExplanationStyle"] == "balanced"
    assert profile["learningPace"] == "slow"
    assert len(mastery) == 1
    assert mastery[0]["masteryStatus"] == "INSUFFICIENT_EVIDENCE"
    assert mastery[0]["masteryScore"] == 0.5


def test_compute_profile_visual_override():
    """多次 request_explanation 应将风格覆盖为 visual。"""
    events = [
        {"kpId": 1, "eventType": "request_explanation", "data": {}},
        {"kpId": 1, "eventType": "request_explanation", "data": {}},
        {"kpId": 1, "eventType": "request_explanation", "data": {}},
    ]
    profile, mastery = compute_profile_from_events(7, events)

    assert profile["preferredExplanationStyle"] == "visual"
    # evidence=3 → 不再证据不足，mastery_score=0 → WEAK
    assert mastery[0]["masteryStatus"] == "WEAK"
    assert mastery[0]["confidence"] == 0.65


def test_compute_profile_recent_focus_weight():
    """recentFocus 权重 = 该知识点事件数 / 总事件数。"""
    events = [
        {"kpId": 1, "eventType": "answer_question", "data": {"isCorrect": True}},
        {"kpId": 1, "eventType": "answer_question", "data": {"isCorrect": True}},
        {"kpId": 2, "eventType": "answer_question", "data": {"isCorrect": False}},
    ]
    profile, _ = compute_profile_from_events(7, events)

    weights = {f["kpId"]: f["weight"] for f in profile["recentFocus"]}
    assert weights == {1: round(2 / 3, 4), 2: round(1 / 3, 4)}


def test_compute_profile_pace_moderate():
    """答题时长 30 秒应推断为 moderate。"""
    events = [
        {"kpId": 1, "eventType": "answer_question",
         "data": {"isCorrect": True, "durationSeconds": 30}},
    ]
    profile, _ = compute_profile_from_events(7, events)
    assert profile["learningPace"] == "moderate"


# ---------------------------------------------------------------------------
# infer_learning_style 学习风格推断（mock 统计查询）
# ---------------------------------------------------------------------------

def test_infer_learning_style_no_stats(make_conn):
    """无数据时返回 balanced。"""
    conn, _ = make_conn(fetchone=[None])
    assert infer_learning_style(7, conn) == "balanced"


def test_infer_learning_style_zero_count(make_conn):
    """答题数 0 时返回 balanced。"""
    conn, _ = make_conn(fetchone=[{"cnt": 0}])
    assert infer_learning_style(7, conn) == "balanced"


def test_infer_learning_style_reading(make_conn):
    """快且正确率高 → reading。"""
    conn, _ = make_conn(fetchone=[
        {"cnt": 10, "avg_time": 10, "avg_correct": 0.9, "retry_count": 0},
        {"confused_qs": 0},
    ])
    assert infer_learning_style(7, conn) == "reading"


def test_infer_learning_style_visual(make_conn):
    """概念模糊标记多且不满足 reading → visual。"""
    conn, _ = make_conn(fetchone=[
        {"cnt": 10, "avg_time": 50, "avg_correct": 0.5, "retry_count": 0},
        {"confused_qs": 3},
    ])
    assert infer_learning_style(7, conn) == "visual"


def test_infer_learning_style_practitioner(make_conn):
    """答错后频繁重做 → practitioner。"""
    conn, _ = make_conn(fetchone=[
        {"cnt": 10, "avg_time": 50, "avg_correct": 0.5, "retry_count": 2},
        {"confused_qs": 0},
    ])
    assert infer_learning_style(7, conn) == "practitioner"


def test_infer_learning_style_balanced(make_conn):
    """均不满足 → balanced。"""
    conn, _ = make_conn(fetchone=[
        {"cnt": 10, "avg_time": 50, "avg_correct": 0.5, "retry_count": 0},
        {"confused_qs": 0},
    ])
    assert infer_learning_style(7, conn) == "balanced"


# ---------------------------------------------------------------------------
# _infer_learning_pace 学习节奏推断（mock 统计查询）
# ---------------------------------------------------------------------------

def test_infer_learning_pace_fast(make_conn):
    conn, _ = make_conn(fetchone=[{"avg_time": 10}])
    assert _infer_learning_pace(7, conn) == "fast"


def test_infer_learning_pace_moderate(make_conn):
    conn, _ = make_conn(fetchone=[{"avg_time": 30}])
    assert _infer_learning_pace(7, conn) == "moderate"


def test_infer_learning_pace_slow(make_conn):
    conn, _ = make_conn(fetchone=[{"avg_time": 90}])
    assert _infer_learning_pace(7, conn) == "slow"


def test_infer_learning_pace_default_null(make_conn):
    """无平均耗时数据时默认 60 → slow。"""
    conn, _ = make_conn(fetchone=[{"avg_time": None}])
    assert _infer_learning_pace(7, conn) == "slow"


# ---------------------------------------------------------------------------
# _write_user_profiles_v5 画像落盘（mock 隔离数据库）
# ---------------------------------------------------------------------------

def test_write_profiles_no_data(make_conn, sql_asserts):
    """无学习数据时应写入 NOT_READY / NO_LEARNING_DATA 记录。"""
    conn, cursor = make_conn(fetchall=[[]])

    _write_user_profiles_v5(7, conn)

    sql_asserts["contains"](cursor, "INSERT INTO user_profiles")
    sql_asserts["contains"](cursor, "NOT_READY")
    sql_asserts["contains"](cursor, "NO_LEARNING_DATA")
    # 1 次排期查询 + 1 次画像写入
    assert sql_asserts["execute_count"](cursor) == 2


def test_write_profiles_ready(make_conn, sql_asserts):
    """有学习数据时写入 READY 画像，包含推断出的风格与节奏。"""
    kp_row = {
        "user_id": 7, "kp_id": 9, "kp_name": "导数",
        "total_attempts": 5, "correct_answers": 3,
        "weakness_score": 0.3, "confusion_count": 0, "review_stage": 0,
    }
    conn, cursor = make_conn(
        fetchall=[[kp_row]],
        fetchone=[
            # infer_learning_style 的统计与混淆查询
            {"cnt": 1, "avg_time": 10, "avg_correct": 0.9, "retry_count": 0},
            {"confused_qs": 0},
            # _infer_learning_pace
            {"avg_time": 10},
        ],
    )

    _write_user_profiles_v5(7, conn)

    sql_asserts["contains"](cursor, "READY")
    params = sql_asserts["params_for"](cursor, "INSERT INTO user_profiles")
    user_id, profile_data, learning_style, learning_pace, summary = params
    assert user_id == 7
    assert learning_style == "reading"
    assert learning_pace == "fast"
    data = json.loads(profile_data)
    assert data["avg_accuracy"] == 0.6
    assert data["total_questions"] == 5
    assert "暂无" in summary


def test_write_profiles_strength_category(make_conn, sql_asserts):
    """正确率高且答题数多时应归类为擅长（strengths）。"""
    kp_row = {
        "user_id": 7, "kp_id": 2, "kp_name": "极限",
        "total_attempts": 20, "correct_answers": 19,
        "weakness_score": 0.05, "confusion_count": 0, "review_stage": 2,
    }
    conn, cursor = make_conn(
        fetchall=[[kp_row]],
        fetchone=[
            {"cnt": 1, "avg_time": 10, "avg_correct": 0.9, "retry_count": 0},
            {"confused_qs": 0},
            {"avg_time": 10},
        ],
    )

    _write_user_profiles_v5(7, conn)

    params = sql_asserts["params_for"](cursor, "INSERT INTO user_profiles")
    data = json.loads(params[1])
    assert [s["name"] for s in data["strengths"]] == ["极限"]