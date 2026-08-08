"""M6 遗忘曲线复习排期测试。

覆盖 review_scheduler.py 全部函数：
- 纯函数：间隔计算、下次复习日期、薄弱分数、优先级（无需 mock）。
- 数据库函数：首次学习、多阶段复习、复习失败、排期构建、提醒同步（mock 隔离数据库）。
"""

from datetime import date, timedelta

from landppt.m6.config import MAX_STAGE, REVIEW_INTERVALS
from landppt.m6.review_scheduler import (
    _derive_priority,
    _sync_reminder,
    advance_stage,
    build_all_schedules,
    calc_next_review_delay,
    calc_weakness_score,
    get_next_review_date,
    get_overdue_schedules,
    on_learned,
)


# ---------------------------------------------------------------------------
# 纯函数测试（无需 mock 数据库）
# ---------------------------------------------------------------------------

def test_config_review_intervals():
    """配置应定义 1/3/7/30 天复习间隔与最大阶段数。"""
    assert REVIEW_INTERVALS == [1, 3, 7, 30]
    assert MAX_STAGE == 4


def test_calc_next_review_delay_stages():
    """各复习阶段对应的间隔天数应为 1/3/7/30，超过上限收敛到 30。"""
    assert calc_next_review_delay(0) == 1
    assert calc_next_review_delay(1) == 3
    assert calc_next_review_delay(2) == 7
    assert calc_next_review_delay(3) == 30
    assert calc_next_review_delay(5) == 30


def test_get_next_review_date_with_last_review():
    """给定上次复习日期时，应在该日期上累加间隔。"""
    assert get_next_review_date(1, date(2026, 7, 25)) == date(2026, 7, 28)


def test_get_next_review_date_default_base():
    """未传上次复习日期时以今天为基准。"""
    today = date.today()
    assert get_next_review_date(0) == today + timedelta(days=1)


def test_get_next_review_date_final_stage():
    """最终阶段（stage=3）的下次复习应为 30 天后。"""
    assert get_next_review_date(3, date(2026, 7, 25)) == date(2026, 8, 24)


def test_calc_weakness_score_perfect():
    """全部答对且无混淆时薄弱分为 0。"""
    assert calc_weakness_score(10, 10, 0) == 0.0


def test_calc_weakness_score_zero_total():
    """答题数为 0 时薄弱分应为 0，避免除零异常。"""
    assert calc_weakness_score(0, 0, 0) == 0.0


def test_calc_weakness_score_mixed():
    """10 答 5 对 2 混淆：0.4*0.5 + 0.4*0.5 + 0.2*0.2 = 0.44。"""
    assert calc_weakness_score(10, 5, 2) == 0.44


def test_calc_weakness_score_high_error_low_confusion():
    """高错误率低混淆率：0.8*0.3 + 0.2*0.1 = 0.26。"""
    assert calc_weakness_score(100, 70, 10) == 0.26


def test_calc_weakness_score_boundary():
    """10 答 8 对 1 混淆：0.8*0.2 + 0.2*0.1 = 0.18。"""
    assert calc_weakness_score(10, 8, 1) == 0.18


def test_derive_priority_boundaries():
    """薄弱分映射优先级：>=0.7 紧急、>=0.3 重要、其余普通。"""
    assert _derive_priority(0.8) == 2
    assert _derive_priority(0.7) == 2
    assert _derive_priority(0.5) == 1
    assert _derive_priority(0.3) == 1
    assert _derive_priority(0.29) == 0
    assert _derive_priority(0.0) == 0


# ---------------------------------------------------------------------------
# 首次学习 / 复习推进（mock 数据库）
# ---------------------------------------------------------------------------

def test_on_learned_new_kp_creates_schedule(make_conn, sql_asserts):
    """首次学习新知识点时应创建初始排期并同步复习提醒。"""
    conn, cursor = make_conn(fetchone=[None, None])
    today = date.today()
    next_review = today + timedelta(days=REVIEW_INTERVALS[0])

    on_learned(7, 9, "导数", conn)

    # 创建初始排期：stage=0，1 天后复习
    params = sql_asserts["params_for"](cursor, "INSERT INTO review_schedules")
    assert params == (7, 9, today, next_review)
    # 同步一条间隔重复提醒
    sql_asserts["contains"](cursor, "INSERT INTO review_reminders")
    sql_asserts["contains"](cursor, "SPACED_REPETITION")


def test_on_learned_existing_unmastered_advances(make_conn, sql_asserts):
    """已有未掌握的排期时再次学习应推进复习阶段。"""
    conn, cursor = make_conn(fetchone=[
        {"id": 1, "review_stage": 0, "mastered": 0},
        {"review_stage": 0, "weakness_score": 0.0},
        None,
    ])

    on_learned(7, 9, "导数", conn)

    sql_asserts["contains"](cursor, "UPDATE review_schedules")


def test_on_learned_mastered_does_not_advance(make_conn, sql_asserts):
    """已掌握的知识点再次学习不应推进阶段。"""
    conn, cursor = make_conn(fetchone=[
        {"id": 1, "review_stage": 3, "mastered": 1},
    ])

    on_learned(7, 9, "导数", conn)

    assert sql_asserts["execute_count"](cursor) == 1
    assert "UPDATE review_schedules" not in "\n".join(
        sql_asserts["fragments"](cursor)
    )


def test_advance_stage_no_record_is_noop(make_conn, sql_asserts):
    """advance_stage 找不到排期记录时应直接返回，不做任何更新。"""
    conn, cursor = make_conn(fetchone=[None])

    advance_stage(7, 9, "导数", conn)

    assert sql_asserts["execute_count"](cursor) == 1


def test_advance_stage_first_review(make_conn, sql_asserts):
    """第一次复习：stage 0→1，下次间隔 3 天，未掌握。"""
    conn, cursor = make_conn(fetchone=[
        {"review_stage": 0, "weakness_score": 0.3},
        None,
    ])
    today = date.today()
    next_review = today + timedelta(days=3)

    advance_stage(7, 9, "导数", conn)

    params = sql_asserts["params_for"](cursor, "UPDATE review_schedules")
    assert params == (1, 0, today, next_review, 7, 9)


def test_advance_stage_to_mastered(make_conn, sql_asserts):
    """阶段推进到 4 时应标记掌握，下次间隔 30 天。"""
    conn, cursor = make_conn(fetchone=[
        {"review_stage": 3, "weakness_score": 0.1},
        None,
    ])
    today = date.today()
    next_review = today + timedelta(days=30)

    advance_stage(7, 9, "导数", conn)

    params = sql_asserts["params_for"](cursor, "UPDATE review_schedules")
    assert params == (4, 1, today, next_review, 7, 9)


def test_get_overdue_schedules_returns_rows(make_conn):
    """查询逾期排期应原样返回游标查询结果。"""
    rows = [
        {"user_id": 7, "kp_id": 9, "next_review_at": date(2026, 7, 1)},
        {"user_id": 7, "kp_id": 10, "next_review_at": date(2026, 7, 2)},
    ]
    conn, _ = make_conn(fetchall=[rows])

    result = get_overdue_schedules(7, conn)

    assert result == rows


# ---------------------------------------------------------------------------
# 全量排期构建（mock 数据库）
# ---------------------------------------------------------------------------

def test_build_all_schedules_empty(make_conn, sql_asserts):
    """无答题数据时全量构建应返回 0 且不写排期。"""
    conn, cursor = make_conn(fetchall=[[]])

    total = build_all_schedules(conn)

    assert total == 0
    assert "INSERT INTO review_schedules" not in "\n".join(
        sql_asserts["fragments"](cursor)
    )


def test_build_all_schedules_single_kp(make_conn, sql_asserts):
    """单用户单知识点：根据答题统计生成初始排期并同步提醒。"""
    conn, cursor = make_conn(
        fetchone=[
            {"cnt": 5, "correct": 3},
            {"confused": 1},
            None,
        ],
        fetchall=[[{"user_id": 7, "kp_id": 9, "kp_name": "导数"}]],
    )

    today = date.today()
    next_review = today + timedelta(days=REVIEW_INTERVALS[0])

    total = build_all_schedules(conn)

    assert total == 1
    params = sql_asserts["params_for"](cursor, "INSERT INTO review_schedules")
    # 弱分：0.4*(2/5) + 0.4*(2/5) + 0.2*(1/5) = 0.36
    assert params == (7, 9, 0, 0, next_review, 5, 3, 1, 0.36)


def test_build_all_schedules_upsert(make_conn, sql_asserts):
    """已存在的排期应走 ON DUPLICATE KEY UPDATE 更新。"""
    conn, cursor = make_conn(
        fetchone=[
            {"cnt": 5, "correct": 3},
            {"confused": 1},
            None,
        ],
        fetchall=[[{"user_id": 7, "kp_id": 9, "kp_name": "导数"}]],
    )

    build_all_schedules(conn)

    sql_asserts["contains"](cursor, "ON DUPLICATE KEY UPDATE")


# ---------------------------------------------------------------------------
# 复习提醒同步（mock 数据库）
# ---------------------------------------------------------------------------

def test_sync_reminder_updates_existing(make_conn, sql_asserts):
    """已有未复习提醒时应更新日期、优先级与内容。"""
    conn, cursor = make_conn(fetchone=[{"id": 5}])
    next_review = date(2026, 8, 1)

    _sync_reminder(7, 9, "导数", 1, next_review, 0.6, conn)

    # 薄弱分 0.6 → 优先级 1（重要）
    params = sql_asserts["params_for"](cursor, "UPDATE review_reminders")
    assert params == (next_review, 1, "导数", 5)


def test_sync_reminder_creates_new(make_conn, sql_asserts):
    """无提醒记录时应新增一条 SPACED_REPETITION 提醒。"""
    conn, cursor = make_conn(fetchone=[None])
    next_review = date(2026, 8, 1)

    _sync_reminder(7, 9, "导数", 0, next_review, 0.0, conn)

    sql_asserts["contains"](cursor, "INSERT INTO review_reminders")
    sql_asserts["contains"](cursor, "SPACED_REPETITION")
    params = sql_asserts["params_for"](cursor, "INSERT INTO review_reminders")
    assert params == (7, "9", "导数", next_review, 0)
