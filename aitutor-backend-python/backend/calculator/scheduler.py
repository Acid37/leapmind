"""
定时任务调度器 — 凌晨2点全量重算所有用户的薄弱点

⚠️ 生产环境由 Java（Spring Task / XXL-Job）定时触发 Python 计算，
   此调度器仅作为开发/自测用占位，部署时可根据需要启用或替换。

使用 APScheduler，兼容 FastAPI 生命周期管理。

运行方式（无需额外操作，随 FastAPI 自动启动）：
    uvicorn main:app --reload
"""
import logging
from datetime import datetime

from apscheduler.schedulers.background import BackgroundScheduler
from apscheduler.triggers.cron import CronTrigger

from calculator.trend_analysis import analyze_trends
from calculator.weakness_score import calculate_weakness_scores as calc_weakness
from database.mysql_connector import Database

logger = logging.getLogger("m3.scheduler")


def run_full_recalculation():
    """
    全量重算：对所有有答题记录的用户重新计算薄弱点
    """
    logger.info("开始全量薄弱点重算...")
    db = Database()

    try:
        # 获取所有有答题记录的用户
        all_users = _fetch_all_user_ids(db)
        if not all_users:
            logger.info("没有需要计算的用户")
            return

        knowledge_points = db.fetch_knowledge_points()
        if not knowledge_points:
            logger.warning("知识点数据为空，跳过重算")
            return

        success_count = 0
        for user_id in all_users:
            try:
                _recalculate_for_user(user_id, db, knowledge_points)
                success_count += 1
            except Exception as e:
                logger.error(f"用户 {user_id} 重算失败: {e}")

        logger.info(
            f"全量重算完成: 共 {len(all_users)} 人, "
            f"成功 {success_count} 人, "
            f"失败 {len(all_users) - success_count} 人"
        )
    finally:
        pass  # Database 连接会自动释放


def _recalculate_for_user(
    user_id: int,
    db: Database,
    knowledge_points: list[dict],
) -> None:
    """为单个用户执行全流程计算并落库"""
    user_answers = db.fetch_user_answers(user_id)
    conversation_msgs = db.fetch_conversation_messages(user_id)
    wrong_book = db.fetch_wrong_question_book(user_id)
    user_profile = db.fetch_user_profile(user_id)

    if not user_answers:
        return

    # 1. 计算薄弱度（与 API 接口使用相同的数据源，保证结果一致）
    weak_points = calc_weakness(
        user_answers, conversation_msgs, knowledge_points,
        wrong_question_book=wrong_book, user_profile=user_profile,
    )

    # 2. 趋势分析
    weak_points = analyze_trends(user_answers, weak_points)

    # 3. 批量写入
    now = datetime.now()
    for wp in weak_points:
        db.upsert_weak_point({
            "user_id": user_id,
            "kp_id": wp["kp_id"],
            "weakness_score": wp["weakness_score"],
            "error_count": wp["error_count"],
            "total_attempts": wp["total_attempts"],
            "error_rate": wp["error_rate"],
            "recent_correct_rate": wp["recent_correct_rate"],
            "confusion_count": wp["confusion_count"],
            "trend": wp["trend"],
            "last_error_at": wp.get("last_error_at"),
            "calculated_at": now,
        })


def _fetch_all_user_ids(db: Database) -> list[int]:
    """获取所有有答题记录的用户 ID"""
    sql = "SELECT DISTINCT user_id FROM user_answers"
    conn = db._get_conn()
    try:
        with conn.cursor() as cur:
            cur.execute(sql)
            return [row["user_id"] for row in cur.fetchall()]
    finally:
        conn.close()


def run_incremental_update(
    user_id: int,
    kp_ids: list[int],
    db: Database = None,
) -> dict:
    """
    增量更新：用户完成一组练习后，仅重算相关知识点薄弱度

    与全量重算使用相同的计算逻辑（weakness_score → trend_analysis），
    但只对指定 kp_ids 落库，减少不必要的写入。

    参数
    ----
    user_id : int
    kp_ids : list[int]
        需要更新的知识点 ID 列表
    db : Database | None

    返回
    ----
    dict : {"status": "ok", "updated_kp_ids": [...], "updated_count": N}
    """
    should_close = False
    if db is None:
        db = Database()
        should_close = True

    try:
        knowledge_points = db.fetch_knowledge_points()
        if not knowledge_points:
            return {"status": "skipped", "reason": "知识点数据为空"}

        # 拉取用户全量数据（公式需要完整数据作为计算上下文）
        user_answers = db.fetch_user_answers(user_id)
        conversation_msgs = db.fetch_conversation_messages(user_id)
        wrong_book = db.fetch_wrong_question_book(user_id)
        user_profile = db.fetch_user_profile(user_id)

        if not user_answers:
            return {"status": "skipped", "reason": "用户无答题记录"}

        # 1. 全量计算薄弱度（多源融合）
        weak_points = calc_weakness(
            user_answers, conversation_msgs, knowledge_points,
            wrong_question_book=wrong_book, user_profile=user_profile,
        )

        # 2. 趋势分析
        weak_points = analyze_trends(user_answers, weak_points)

        # 3. 仅保留指定知识点落库
        target_set = set(kp_ids)
        updated = [wp for wp in weak_points if wp["kp_id"] in target_set]

        now = datetime.now()
        for wp in updated:
            db.upsert_weak_point({
                "user_id": user_id,
                "kp_id": wp["kp_id"],
                "weakness_score": wp["weakness_score"],
                "error_count": wp["error_count"],
                "total_attempts": wp["total_attempts"],
                "error_rate": wp["error_rate"],
                "recent_correct_rate": wp["recent_correct_rate"],
                "confusion_count": wp["confusion_count"],
                "trend": wp["trend"],
                "last_error_at": wp.get("last_error_at"),
                "calculated_at": now,
            })

        logger.info(
            f"增量更新完成: user_id={user_id}, "
            f"更新 {len(updated)} 个知识点: {[wp['kp_id'] for wp in updated]}"
        )

        return {
            "status": "ok",
            "updated_kp_ids": [wp["kp_id"] for wp in updated],
            "updated_count": len(updated),
        }

    except Exception as e:
        logger.error(f"增量更新失败 user_id={user_id}: {e}")
        raise
    finally:
        if should_close:
            pass


def init_scheduler(app=None) -> BackgroundScheduler:
    """
    初始化定时调度器（在 FastAPI 启动时调用）

    用法：
        scheduler = init_scheduler()
        scheduler.start()
    """
    scheduler = BackgroundScheduler(daemon=True)

    # 每天凌晨 2:00 执行全量重算
    scheduler.add_job(
        run_full_recalculation,
        CronTrigger(hour=2, minute=0),
        id="full_recalc_daily",
        name="每日凌晨全量薄弱点重算",
        replace_existing=True,
    )

    logger.info("定时调度器已初始化: 每天 02:00 全量重算")

    return scheduler
