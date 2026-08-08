"""M6 画像计算与历史查询路由。"""

import logging
from collections.abc import Callable, Coroutine
from typing import Any

from fastapi import APIRouter, HTTPException, Request
from fastapi.exceptions import RequestValidationError
from fastapi.routing import APIRoute
from starlette.responses import Response

from .profile_engine import ALGORITHM_VERSION, ProfileRequestError, buildProfile
from .schemas import (
  BuildProfileRequest,
  BuildProfileResponse,
  KnowledgeStatusItem,
  KnowledgeStatusResponse,
  TimelineItem,
  TimelineResponse,
)

logger = logging.getLogger(__name__)


class ContractValidationRoute(APIRoute):
  """把本路由内的请求模型校验失败统一映射为契约错误。"""

  def get_route_handler(self) -> Callable[[Request], Coroutine[Any, Any, Response]]:
    """包装 FastAPI 默认处理器，仅改变请求校验错误的 HTTP 状态。"""
    originalRouteHandler = super().get_route_handler()

    async def contractRouteHandler(request: Request) -> Response:
      try:
        return await originalRouteHandler(request)
      except RequestValidationError as exception:
        raise HTTPException(
          status_code=400,
          detail="画像请求不符合v1.0契约",
        ) from exception

    return contractRouteHandler


router = APIRouter(
  prefix="/api",
  tags=["M6 复习与画像"],
  route_class=ContractValidationRoute,
)


@router.post(
  "/internal/ai/build-profile",
  response_model=BuildProfileResponse,
  response_model_exclude_none=True,
)
async def buildProfileRoute(request: BuildProfileRequest) -> BuildProfileResponse:
  """调用无状态画像引擎，返回严格三态响应。"""
  try:
    response = await buildProfile(request)
  except ProfileRequestError as exception:
    raise HTTPException(status_code=400, detail=str(exception)) from exception
  except Exception as exception:
    logger.error(
      "M6 build-profile 失败：requestId=%s",
      request.requestId,
      exc_info=exception,
    )
    raise HTTPException(status_code=503, detail="画像引擎暂时不可用") from exception

  logger.info(
    "M6 build-profile 成功：requestId=%s，algorithmVersion=%s",
    request.requestId,
    ALGORITHM_VERSION,
  )
  return response


@router.get(
  "/user-profile/{userId}/knowledge-status",
  response_model=KnowledgeStatusResponse,
)
async def get_knowledge_status(userId: int):
  """
  知识掌握状态查询（前端知识雷达图使用）
  返回该用户所有知识点的当前阶段、掌握状态、薄弱分数、下次复习日期
  """
  from .db import get_conn

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
  from .db import get_conn

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
