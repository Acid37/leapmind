# M6 模块 FastAPI 路由
# 提供 4 个端点：
# - 2 个 POST 由 Java ReviewCalculationTask 定时任务调用
# - 2 个 GET 由前端直接调用（知识雷达图、学习时间线）

import logging

from fastapi import APIRouter

from .db import get_conn, init_tables
from .profile_engine import build_all_profiles, process_new_events, compute_profile_from_events
from .review_scheduler import build_all_schedules
from .schemas import (
    BuildProfileRequest,
    BuildProfileResponse,
    ProfileData,
    KnowledgeMasteryItem,
    CalculateAllResponse,
    EventProcessResponse,
    KnowledgeStatusItem,
    KnowledgeStatusResponse,
    TimelineItem,
    TimelineResponse,
)

logger = logging.getLogger(__name__)
router = APIRouter(prefix="/api", tags=["M6 复习与画像"])


@router.post("/review/calculate-all", response_model=CalculateAllResponse)
async def calculate_all():
    """
    全量复习计算（Java ReviewCalculationTask 定时任务 02:00 调用）
    1. 全量重建所有用户的复习排期（遗忘曲线）
    2. 全量重建所有用户的画像（含学习风格推断、摘要生成）
    """
    logger.info("M6 calculate-all start")
    conn = get_conn()
    try:
        init_tables()
        schedule_count = build_all_schedules(conn)
        user_count = build_all_profiles(conn)
        conn.commit()
        logger.info("M6 calculate-all done: %d users, %d schedules", user_count, schedule_count)
        return CalculateAllResponse(
            status="ok",
            user_count=user_count,
            message=f"全量复习计算完成，处理 {user_count} 个用户，{schedule_count} 个知识点排期",
        )
    except Exception as e:
        logger.error("M6 calculate-all failed: %s", e)
        raise
    finally:
        conn.close()


@router.post("/events/process", response_model=EventProcessResponse)
async def process_events():
    """
    增量事件处理（Java ReviewCalculationTask 定时任务 03:00 调用）
    1. 读取 event_collections 中未处理的事件
    2. 逐条增量更新计数器和薄弱分数
    3. 更新受影响的用户画像
    """
    logger.info("M6 events-process start")
    conn = get_conn()
    try:
        init_tables()
        count = process_new_events(conn)
        conn.commit()
        logger.info("M6 events-process done: %d events", count)
        return EventProcessResponse(
            status="ok",
            processed_count=count,
            message=f"处理了 {count} 条新事件",
        )
    except Exception as e:
        logger.error("M6 events-process failed: %s", e)
        raise
    finally:
        conn.close()


@router.post("/internal/ai/build-profile", response_model=BuildProfileResponse)
async def build_profile(req: BuildProfileRequest):
    """
    在线画像计算（profile-engine-contract.yaml 内部契约）
    接收 Java 传来的用户事件列表，返回画像数据和知识点掌握程度
    不写数据库，Java 端负责持久化
    """
    logger.info("M6 build-profile start: user_id=%d, events=%d", req.user_id, len(req.events))
    try:
        profile, mastery_items = compute_profile_from_events(
            req.user_id, req.events
        )
        if profile is None:
            return BuildProfileResponse(
                success=False,
                data=None,
                message="无有效事件数据，无法生成画像",
            )
        mastery = [
            KnowledgeMasteryItem(
                kp_id=item["kpId"],
                kp_name=f"kp_{item['kpId']}",
                mastery_level=item["masteryScore"],
                review_count=item["evidenceCount"],
                last_reviewed=item.get("updatedAt"),
            )
            for item in mastery_items
        ]
        overall = sum(m.mastery_level for m in mastery) / len(mastery) if mastery else 0.0
        from datetime import datetime, timezone
        data = ProfileData(
            user_id=req.user_id,
            knowledge_mastery=mastery,
            overall_mastery=round(overall, 4),
            calculated_at=datetime.now(timezone.utc).isoformat(),
        )
        return BuildProfileResponse(success=True, data=data, message=None)
    except Exception as e:
        logger.error("M6 build-profile failed: %s", e)
        return BuildProfileResponse(success=False, data=None, message=str(e))


@router.get(
    "/user-profile/{userId}/knowledge-status",
    response_model=KnowledgeStatusResponse,
)
async def get_knowledge_status(userId: int):
    """
    知识掌握状态查询（前端知识雷达图使用）
    返回该用户所有知识点的当前阶段、掌握状态、薄弱分数、下次复习日期
    """
    conn = get_conn()
    try:
        with conn.cursor() as cur:
            cur.execute(
                "SELECT rs.kp_id, kp.name AS kp_name, rs.review_stage, "
                "rs.mastered, rs.weakness_score, rs.next_review_at "
                "FROM review_schedules rs "
                "JOIN knowledge_points kp ON kp.id = rs.kp_id "
                "WHERE rs.user_id = %s "
                "ORDER BY rs.weakness_score DESC",
                (userId,),
            )
            rows = cur.fetchall()
        items = [
            KnowledgeStatusItem(
                kp_id=r["kp_id"],
                kp_name=r["kp_name"],
                review_stage=r["review_stage"],
                mastered=bool(r["mastered"]),
                weakness_score=float(r["weakness_score"]),
                next_review_at=str(r["next_review_at"]) if r["next_review_at"] else None,
            )
            for r in rows
        ]
        return KnowledgeStatusResponse(user_id=userId, items=items)
    finally:
        conn.close()


@router.get(
    "/user-profile/{userId}/timeline",
    response_model=TimelineResponse,
)
async def get_timeline(userId: int, limit: int = 50):
    """
    学习时间线查询（前端学习时间线组件使用）
    返回该用户最近的事件记录，按时间倒序排列
    """
    conn = get_conn()
    try:
        with conn.cursor() as cur:
            cur.execute(
                "SELECT id, module, event_type, event_time, event_data "
                "FROM event_collections "
                "WHERE user_id = %s "
                "ORDER BY event_time DESC "
                "LIMIT %s",
                (userId, limit),
            )
            rows = cur.fetchall()
        events = [
            TimelineItem(
                event_id=r["id"],
                module=r["module"],
                event_type=r["event_type"],
                created_at=str(r["event_time"]),
                description=str(r["event_data"]) if r["event_data"] else None,
            )
            for r in rows
        ]
        return TimelineResponse(user_id=userId, events=events)
    finally:
        conn.close()
