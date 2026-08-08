"""
SSE streaming API routes for M5 AI备课模块.

Endpoints:
  POST /api/lesson-prep/generate          → 三阶段完整备课（大纲+PPT+讲解词）
  POST /api/lesson-prep/generate-ppt      → 基于已有备课单独生成PPT
  POST /api/lesson-prep/generate-goals    → AI生成教学目标（3-5条）
  POST /api/lesson-prep/generate-process  → AI生成教学过程（4-6个环节）
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
    knowledge_point_names: list[str] = Field(default_factory=list, alias="knowledgePointNames", description="知识点名称列表（与ID一一对应）")
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


class GenerateGoalsRequest(BaseModel):
    """AI生成教学目标的请求。"""
    model_config = {"populate_by_name": True}

    user_id: int = Field(..., alias="userId", description="用户ID")
    knowledge_point_ids: list[int] = Field(..., alias="knowledgePointIds", description="知识点ID列表")
    knowledge_point_names: list[str] = Field(default_factory=list, alias="knowledgePointNames", description="知识点名称列表")
    subject: str = Field(..., description="科目: math/chinese/english/physics")
    grade: str = Field(..., description="年级: grade_7 ~ grade_12")
    goal_direction: Optional[str] = Field(default=None, alias="goalDirection", description="教学方向提示")
    weak_point_ids: list[int] = Field(default_factory=list, alias="weakPointIds", description="薄弱知识点ID（可选）")


class GenerateProcessRequest(BaseModel):
    """AI生成教学过程的请求。"""
    model_config = {"populate_by_name": True}

    user_id: int = Field(..., alias="userId", description="用户ID")
    knowledge_point_ids: list[int] = Field(..., alias="knowledgePointIds", description="知识点ID列表")
    knowledge_point_names: list[str] = Field(default_factory=list, alias="knowledgePointNames", description="知识点名称列表")
    subject: str = Field(..., description="科目: math/chinese/english/physics")
    grade: str = Field(..., description="年级: grade_7 ~ grade_12")
    teaching_goals: list[str] = Field(..., alias="teachingGoals", description="教学目标列表")
    total_hours: int = Field(default=1, ge=1, le=10, alias="totalHours", description="课时数")
    section_index: int = Field(default=1, alias="sectionIndex", description="当前课时序号")
    section_title: Optional[str] = Field(default=None, alias="sectionTitle", description="当前课时标题")


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
            knowledge_point_names=request.knowledge_point_names,
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


# ─── Auxiliary endpoints (non-streaming JSON) ───

@router.post("/generate-goals")
async def generate_goals(request: GenerateGoalsRequest):
    """AI生成教学目标（3-5条）。

    用于创建备课页，用户输入知识点后点击「AI智能生成」按钮获取建议的教学目标。

    返回:
    {
      "goals": ["目标1", "目标2", "目标3", "目标4"]
    }
    """
    service = LessonPrepService()
    try:
        result = await service.generate_goals(
            subject=request.subject,
            grade=request.grade,
            knowledge_point_ids=request.knowledge_point_ids,
            knowledge_point_names=request.knowledge_point_names or None,
            goal_direction=request.goal_direction,
            weak_point_ids=request.weak_point_ids or None,
        )
        if "error" in result:
            return JSONResponse(status_code=500, content={"detail": result["error"]})
        return JSONResponse(content=result)
    except Exception as e:
        return JSONResponse(status_code=500, content={"detail": f"教学目标生成失败: {e}"})


@router.post("/generate-process")
async def generate_process(request: GenerateProcessRequest):
    """AI生成教学过程（4-6个环节）。

    用于编辑页，用户修改教学目标后点击「AI生成」按钮重新生成教学过程。

    返回:
    {
      "teachingProcess": [
        {
          "step": "情境导入",
          "duration": "5min",
          "teacherActivity": "教师活动描述",
          "studentActivity": "学生活动描述",
          "designIntent": "设计意图"
        },
        ...
      ]
    }
    """
    service = LessonPrepService()
    try:
        result = await service.generate_process(
            subject=request.subject,
            grade=request.grade,
            knowledge_point_ids=request.knowledge_point_ids,
            teaching_goals=request.teaching_goals,
            knowledge_point_names=request.knowledge_point_names or None,
            total_hours=request.total_hours,
            section_index=request.section_index,
            section_title=request.section_title,
        )
        if "error" in result:
            return JSONResponse(status_code=500, content={"detail": result["error"]})
        # Convert snake_case keys to camelCase
        camel_process = [convert_keys_camel(s) for s in result["teaching_process"]]
        return JSONResponse(content={"teachingProcess": camel_process})
    except Exception as e:
        return JSONResponse(status_code=500, content={"detail": f"教学过程生成失败: {e}"})


# ─── Quality report endpoint ───

@router.get("/{prep_id}/quality")
async def get_quality_report(prep_id: int):
    """查询已保存的备课内容质量报告。

    质量报告在生成时已自动生成并持久化，此接口直接读取返回。

    返回:
    {
      "prepId": 301,
      "title": "勾股定理备课",
      "subject": "math",
      "grade": "grade_9",
      "quality": {
        "layer1": { "score": 85.0, "isBlocking": false, ... },
        "llmJudge": { "score": 78, "needsReview": false, ... } | null
      }
    }
    """
    service = LessonPrepService()
    try:
        result = await service.get_quality_report(prep_id)
        # Convert snake_case keys to camelCase for frontend
        camel_result = convert_keys_camel(result)
        return JSONResponse(content=camel_result)
    except ValueError as e:
        msg = str(e)
        if "不存在" in msg:
            return JSONResponse(status_code=404, content={"detail": msg})
        return JSONResponse(status_code=400, content={"detail": msg})
    except Exception as e:
        return JSONResponse(status_code=500, content={"detail": f"查询质量报告失败: {e}"})
