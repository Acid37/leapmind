"""
内部 API 端点 —— Java Spring Boot 通过 HTTP 调用 Python AI 服务

Java → Python 内部通信规范参考：
  POST /api/internal/ai/generate              — 非流式 AI 调用
  POST /api/internal/ai/generate/stream       — 流式 AI 调用 (SSE)
  POST /api/internal/ai/generate-lesson-prep  — M5 备课生成（完整三段管线）
"""
import logging
from typing import Optional

from fastapi import APIRouter
from fastapi.responses import JSONResponse, StreamingResponse
from pydantic import BaseModel, Field

from ..services.lesson_prep_service import LessonPrepService, PrepContext

router = APIRouter(prefix="/api/internal", tags=["Internal AI API (Java→Python)"])
logger = logging.getLogger(__name__)


class AICallRequest(BaseModel):
    """与 Java AIService.AICallRequest 对应的请求体。"""
    module_name: str = Field(..., description="调用模块: prep/lecture/qa")
    scene_type: str = Field(..., description="场景: generate_lesson/create_ppt")
    prompt: str = Field(..., description="完整 prompt（Java 已拼入用户画像等上下文）")
    model_name: Optional[str] = Field(default=None, description="模型选择")
    max_tokens: Optional[int] = Field(default=None, description="最大输出 token")
    temperature: Optional[float] = Field(default=None, description="温度参数")
    extra: Optional[dict] = Field(default=None, description="扩展参数")


class LessonPrepInternalRequest(BaseModel):
    """Java 端调用备课生成时的请求体。"""
    user_id: int = Field(..., description="用户ID")
    title: str = Field(..., description="备课标题")
    subject: str = Field(..., description="科目")
    grade: str = Field(..., description="年级")
    knowledge_point_ids: list[int] = Field(..., description="知识点ID列表")
    teaching_goals: list[str] = Field(default_factory=list, description="教学目标")
    total_hours: int = Field(default=1, ge=1, le=10, description="课时数")
    style: str = Field(default="standard", description="备课风格")
    weak_point_ids: list[int] = Field(default_factory=list, description="薄弱知识点ID")
    user_profile_summary: Optional[str] = Field(
        default=None,
        description="用户画像摘要（Java 从 M6 获取后传入）"
    )


@router.post("/ai/generate")
async def internal_ai_generate(request: AICallRequest):
    """非流式 AI 调用（通用入口）。"""
    logger.info(
        "Internal AI call: module=%s scene=%s model=%s",
        request.module_name, request.scene_type, request.model_name,
    )

    if request.module_name == "prep" and request.scene_type == "generate_lesson":
        extra = request.extra or {}
        prep_request = LessonPrepInternalRequest(
            user_id=extra.get("user_id", 0),
            title=extra.get("title", ""),
            subject=extra.get("subject", ""),
            grade=extra.get("grade", ""),
            knowledge_point_ids=extra.get("knowledge_point_ids", []),
            teaching_goals=extra.get("teaching_goals", []),
            total_hours=extra.get("total_hours", 1),
            style=extra.get("style", "standard"),
            weak_point_ids=extra.get("weak_point_ids", []),
            user_profile_summary=extra.get("user_profile_summary"),
        )
        result = await _run_lesson_prep(prep_request)
        return JSONResponse(content=result)

    return JSONResponse(
        status_code=400,
        content={
            "success": False,
            "error": f"Unsupported module/scene: {request.module_name}/{request.scene_type}",
        },
    )


@router.post("/ai/generate/stream")
async def internal_ai_generate_stream(request: AICallRequest):
    """流式 AI 调用（SSE），供 Java 透传给前端。"""
    if request.module_name == "prep" and request.scene_type in ("generate_lesson", "create_ppt"):
        extra = request.extra or {}
        prep_request = LessonPrepInternalRequest(
            user_id=extra.get("user_id", 0),
            title=extra.get("title", ""),
            subject=extra.get("subject", ""),
            grade=extra.get("grade", ""),
            knowledge_point_ids=extra.get("knowledge_point_ids", []),
            teaching_goals=extra.get("teaching_goals", []),
            total_hours=extra.get("total_hours", 1),
            style=extra.get("style", "standard"),
            weak_point_ids=extra.get("weak_point_ids", []),
            user_profile_summary=extra.get("user_profile_summary"),
        )
        service = LessonPrepService()
        return StreamingResponse(
            service.run_stream(
                user_id=prep_request.user_id,
                title=prep_request.title,
                subject=prep_request.subject,
                grade=prep_request.grade,
                knowledge_point_ids=prep_request.knowledge_point_ids,
                teaching_goals=prep_request.teaching_goals,
                total_hours=prep_request.total_hours,
                style=prep_request.style,
                weak_point_ids=prep_request.weak_point_ids,
                user_profile_summary=prep_request.user_profile_summary,
            ),
            media_type="text/event-stream",
            headers={
                "Cache-Control": "no-cache",
                "X-Accel-Buffering": "no",
                "Connection": "keep-alive",
            },
        )

    return JSONResponse(
        status_code=400,
        content={
            "success": False,
            "error": f"Unsupported module/scene: {request.module_name}/{request.scene_type}",
        },
    )


@router.post("/ai/generate-lesson-prep")
async def internal_generate_lesson_prep(request: LessonPrepInternalRequest):
    """非流式备课生成 —— Java 调用此接口获取完整备课结果。"""
    result = await _run_lesson_prep(request)
    return JSONResponse(content=result)


async def _run_lesson_prep(request: LessonPrepInternalRequest) -> dict:
    """统一调用三段备课管线，返回 dict 结果。"""
    logger.info(
        "Internal lesson prep: user=%d title=%s subject=%s grade=%s kps=%s",
        request.user_id, request.title, request.subject, request.grade,
        request.knowledge_point_ids,
    )

    ctx = PrepContext(
        user_id=request.user_id,
        title=request.title,
        subject=request.subject,
        grade=request.grade,
        knowledge_point_ids=request.knowledge_point_ids,
        teaching_goals=request.teaching_goals,
        total_hours=request.total_hours,
        style=request.style,
        weak_point_ids=request.weak_point_ids,
        user_profile_summary=request.user_profile_summary,
    )

    service = LessonPrepService()
    result = await service.generate_and_return(ctx)
    return result
