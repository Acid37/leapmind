"""
SSE streaming API routes for M5 AI备课模块.

Endpoints:
  POST /api/lesson-prep/generate      → 三阶段完整备课（大纲+PPT+讲解词）
  POST /api/lesson-prep/generate-ppt  → 基于已有备课单独生成PPT
"""
from typing import Optional

from fastapi import APIRouter
from fastapi.responses import StreamingResponse, JSONResponse
from pydantic import BaseModel, Field

from ..services.lesson_prep_service import LessonPrepService, convert_keys_camel

router = APIRouter(prefix="/api/lesson-prep", tags=["Lesson Preparation"])


# ─── Request models ───

class LessonPrepRequest(BaseModel):
    """Request body for lesson preparation generation."""
    model_config = {"populate_by_name": True}

    user_id: int = Field(..., alias="userId", description="用户ID")
    title: str = Field(..., description="备课标题")
    subject: str = Field(..., description="科目: math/chinese/english/physics/chemistry/biology")
    grade: str = Field(..., description="年级: grade_7 ~ grade_12")
    knowledge_point_ids: list[int] = Field(..., alias="knowledgePointIds", description="知识点ID列表")
    teaching_goals: list[str] = Field(default_factory=list, alias="teachingGoals", description="教学目标列表")
    total_hours: int = Field(default=1, ge=1, le=10, alias="totalHours", description="课时数")
    style: str = Field(default="standard", description="备课风格: standard/detailed/interactive")
    weak_point_ids: list[int] = Field(default_factory=list, alias="weakPointIds", description="薄弱知识点ID列表（可选）")
    user_profile_summary: Optional[str] = Field(default=None, alias="userProfileSummary", description="用户画像摘要（M6注入，可选）")
    parallel: bool = Field(default=False, description="[Layer 3] 是否启用并行加速（Stage 2/3 限流并发，3倍以上加速）")


class GeneratePPTRequest(BaseModel):
    """基于已有备课内容生成PPT的请求。"""
    model_config = {"populate_by_name": True}

    prep_id: int = Field(..., alias="prepId", description="备课内容ID（teaching_contents 表主键）")
    template_style: str = Field(default="default", alias="templateStyle", description="PPT模板风格")
    max_slides: int = Field(default=20, ge=4, le=50, alias="maxSlides", description="最大页数")


# ─── SSE streaming endpoints ───

@router.post("/generate")
async def generate_lesson_prep(request: LessonPrepRequest):
    """AI备课生成接口。

    返回 SSE (text/event-stream) 流，事件顺序：

      1. outline               — 完整教学大纲 JSON
      2. section × N            — 逐课时详情（index, title, content）
      3. done                   — 全部完成（含 prepId）

    异常时返回:
      error — 包含 stage 和 message 字段
    """
    service = LessonPrepService(parallel=request.parallel)
    return StreamingResponse(
        service.run_stream(
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
        ),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "X-Accel-Buffering": "no",
            "Connection": "keep-alive",
        },
    )


@router.post("/generate-ppt")
async def generate_ppt(request: GeneratePPTRequest):
    """基于已有备课内容单独生成PPT结构（非流式JSON响应）。

    从 teaching_contents 表读取已保存的教学大纲，
    重新生成PPT结构并更新数据库。

    返回:
    {
      "pptId": 301,
      "slides": [{...}]     // PPT结构JSON数组
    }
    """
    service = LessonPrepService()
    try:
        ppt_id, slides = await service.generate_ppt(
            prep_id=request.prep_id,
            template_style=request.template_style,
            max_slides=request.max_slides,
        )
        # Convert snake_case keys to camelCase for consistency with SSE events
        camel_slides = [convert_keys_camel(s) for s in slides]
        return JSONResponse(content={
            "pptId": ppt_id,
            "slides": camel_slides,
        })
    except ValueError as e:
        return JSONResponse(status_code=404, content={"detail": str(e)})
    except Exception as e:
        return JSONResponse(status_code=500, content={"detail": f"PPT生成失败: {e}"})
