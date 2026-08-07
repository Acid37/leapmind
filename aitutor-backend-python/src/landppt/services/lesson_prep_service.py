"""
Three-stage lesson preparation orchestration service for M5 AI备课.

Workflow:
  Stage 1 (streaming):  knowledge_points + goals → syllabus JSON
  Stage 2 (per-page):    syllabus → slide JSONs (one AI call per section, yields per-slide)
  Stage 3 (per-page):    slides → narration JSONs (one AI call per slide)

All stages share a PrepContext passed through memory. On completion, the
full result is written to the teaching_contents table.
"""
import json
import logging
import re
import time
from datetime import datetime
from dataclasses import dataclass, field
from typing import AsyncGenerator, Optional

from ..ai import get_ai_provider, AIMessage, MessageRole
from ..utils.json_extractor import JSONExtractor
from ..validators import ValidationPipeline
from ..ab_testing import ABExperiment
from .parallel_engine import ParallelExecutor
from .prompts.lesson_prep_prompts import (
    build_stage1_messages,
    build_stage2_messages,
    build_stage3_messages,
)

logger = logging.getLogger(__name__)


# ─── Context object (in-memory, passed between stages) ───

@dataclass
class PrepContext:
    user_id: int
    title: str
    subject: str
    grade: str
    knowledge_point_ids: list[int]
    teaching_goals: list[str] = field(default_factory=list)
    total_hours: int = 1
    style: str = "standard"
    weak_point_ids: list[int] = field(default_factory=list)
    user_profile_summary: Optional[str] = None

    # Stage outputs
    syllabus: dict = field(default_factory=dict)
    slides: list[dict] = field(default_factory=list)
    narrations: list[dict] = field(default_factory=list)

    # Error tracking
    errors: list[dict] = field(default_factory=list)
    warnings: list[dict] = field(default_factory=list)


# ─── Event helpers ───

def snake_to_camel(name: str) -> str:
    """Convert snake_case to camelCase."""
    parts = name.split("_")
    return parts[0] + "".join(p.capitalize() for p in parts[1:])


def convert_keys_camel(data: dict) -> dict:
    """Recursively convert all dict keys from snake_case to camelCase."""
    if not isinstance(data, dict):
        return data
    result = {}
    for k, v in data.items():
        camel_key = snake_to_camel(k)
        if isinstance(v, dict):
            result[camel_key] = convert_keys_camel(v)
        elif isinstance(v, list):
            result[camel_key] = [
                convert_keys_camel(item) if isinstance(item, dict) else item
                for item in v
            ]
        else:
            result[camel_key] = v
    return result


def _sse_event(event: str, data: dict) -> str:
    """Unified SSE format: data: {"type":"camelEventName", ...}

    All events are wrapped inside data: with a type discriminator,
    instead of using SSE's built-in event: line.
    """
    payload = {"type": snake_to_camel(event), **convert_keys_camel(data)}
    return f"data: {json.dumps(payload, ensure_ascii=False)}\n\n"


# ─── Main service ───

class LessonPrepService:
    """Three-stage lesson preparation service."""

    # Minimum number of items to trigger parallel mode
    PARALLEL_THRESHOLD = 3

    def __init__(self, parallel: bool = False, experiment: ABExperiment | None = None):
        self.parallel = parallel
        self.experiment = experiment
        self._provider = None
        self._pipeline: ValidationPipeline | None = None
        self._executor: ParallelExecutor | None = None

    @property
    def provider(self):
        """Lazy-init AI provider (avoids failure on import when no API key)."""
        if self._provider is None:
            self._provider = get_ai_provider()
        return self._provider

    @property
    def pipeline(self) -> ValidationPipeline:
        """Lazy-init validation pipeline (avoids import overhead on every request)."""
        if self._pipeline is None:
            self._pipeline = ValidationPipeline(auto_fix=True)
        return self._pipeline

    @property
    def executor(self) -> ParallelExecutor:
        """Lazy-init parallel executor (5 concurrent by default)."""
        if self._executor is None:
            self._executor = ParallelExecutor(max_concurrency=5)
        return self._executor

    # ════════════════════════════════════════════════════════════
    # [Layer 3] A/B test hooks
    # ════════════════════════════════════════════════════════════

    def _select_prompt_version(self, stage: str, user_id: int) -> str | None:
        """Select prompt version based on A/B experiment assignment.

        Returns:
            "A" for control, "B" for variant, None if no experiment is running.
        """
        if not self.experiment or self.experiment.status != "running":
            return None
        return self.experiment.assign(user_id)

    def _record_ab_metrics(self, ctx: PrepContext) -> None:
        """Record A/B test metrics after all stages complete."""
        if not self.experiment or self.experiment.status != "running":
            return

        group = self._select_prompt_version("final", ctx.user_id)
        if group is None:
            return

        # Calculate aggregate metrics from validation results
        slides = ctx.slides or []
        narrations = ctx.narrations or []

        schema_pass_rate = 1.0
        if slides:
            fallback = sum(1 for s in slides if s.get("is_fallback"))
            schema_pass_rate = 1.0 - (fallback / len(slides))

        orality_scores = []
        consistency_scores = []
        duration_deviations = []

        for n in narrations:
            val = n.get("_validation", {})
            if val:
                orality_scores.append(val.get("orality", {}).get("score", 0))
                consistency_scores.append(val.get("consistency", {}).get("coverage", 0))
                dur = val.get("duration", {})
                if dur.get("target_seconds", 0) > 0:
                    duration_deviations.append(dur.get("deviation", 0))

        avg_orality = sum(orality_scores) / max(len(orality_scores), 1)
        avg_consistency = sum(consistency_scores) / max(len(consistency_scores), 1)
        avg_dur_dev = sum(duration_deviations) / max(len(duration_deviations), 1)

        self.experiment.record_result(
            user_id=ctx.user_id,
            group=group,
            metrics={
                "schema_pass_rate": schema_pass_rate,
                "avg_orality_score": avg_orality,
                "avg_consistency_score": avg_consistency,
                "avg_duration_deviation": avg_dur_dev,
            },
        )

    # ════════════════════════════════════════════════════════════
    # Public entry point
    # ════════════════════════════════════════════════════════════

    async def run_stream(
        self,
        user_id: int,
        title: str,
        subject: str,
        grade: str,
        knowledge_point_ids: list[int],
        teaching_goals: list[str],
        total_hours: int = 1,
        style: str = "standard",
        weak_point_ids: list[int] | None = None,
        user_profile_summary: str | None = None,
    ) -> AsyncGenerator[str, None]:
        """Main entry: generate syllabus and emit structured SSE events.

        Event flow:
          data: {"type":"outline","content":{...}}
          data: {"type":"section","index":1,"title":"...","content":{...}}
          data: {"type":"section","index":2,"title":"...","content":{...}}
          ...
          data: {"type":"done","prepId":301}
        """
        ctx = PrepContext(
            user_id=user_id,
            title=title,
            subject=subject,
            grade=grade,
            knowledge_point_ids=knowledge_point_ids,
            teaching_goals=teaching_goals,
            total_hours=total_hours,
            style=style,
            weak_point_ids=weak_point_ids or [],
            user_profile_summary=user_profile_summary,
        )

        try:
            # Stage 1: Syllabus generation (outline + section events)
            async for event in self._stage1_syllabus(ctx):
                yield event

            # Save to database and emit done
            if ctx.syllabus:
                try:
                    prep_id = await self._save_to_db(ctx)
                    yield _sse_event("done", {
                        "prep_id": prep_id,
                    })
                except Exception as e:
                    logger.exception("Database write failed")
                    yield _sse_event("error", {
                        "stage": "db",
                        "message": f"备课内容已生成但保存失败: {e}",
                    })

        except Exception as e:
            logger.exception("Lesson preparation failed")
            yield _sse_event("error", {
                "stage": "global",
                "message": str(e),
            })

    # ════════════════════════════════════════════════════════════
    # Stage 1: Syllabus generation (streaming)
    # ════════════════════════════════════════════════════════════

    async def _stage1_syllabus(self, ctx: PrepContext) -> AsyncGenerator[str, None]:
        """Generate syllabus via streaming AI, yield outline + per-section events."""
        messages = build_stage1_messages(
            subject=ctx.subject,
            grade=ctx.grade,
            knowledge_point_ids=ctx.knowledge_point_ids,
            teaching_goals=ctx.teaching_goals,
            total_hours=ctx.total_hours,
            style=ctx.style,
            weak_point_ids=ctx.weak_point_ids,
            user_profile_summary=ctx.user_profile_summary,
        )

        full_content = ""
        try:
            async for chunk in self.provider.stream_chat_completion(messages):
                full_content += chunk
        except Exception as e:
            logger.exception("Stage 1 (syllabus) streaming failed")
            yield _sse_event("error", {"stage": "stage1", "message": f"大纲生成失败: {e}"})
            return

        # Parse syllabus JSON from the full streaming output
        try:
            syllabus = JSONExtractor.extract_dict(full_content)
        except Exception as e:
            logger.exception("Failed to parse syllabus JSON")
            yield _sse_event("error", {
                "stage": "stage1",
                "message": f"大纲JSON解析失败: {e}",
            })
            return

        # ── [Layer 2] Validate syllabus schema (silent auto-fix) ──
        vr = await self.pipeline.validate("syllabus", syllabus)
        if vr.fixes_applied:
            syllabus = self.pipeline.get_fixed_data("syllabus", syllabus)
            ctx.warnings.append({"stage": "stage1", "fixes": vr.fixes_applied})
        if vr.warnings:
            ctx.warnings.append({"stage": "stage1", "warnings": vr.warnings})

        # Basic validation
        sections = syllabus.get("sections", [])
        if not sections:
            yield _sse_event("error", {
                "stage": "stage1",
                "message": "生成的大纲中没有课时(sections)数据",
            })
            return

        ctx.syllabus = syllabus

        # Emit outline event (full syllabus)
        yield _sse_event("outline", {
            "content": syllabus,
        })

        # Emit per-section events
        for idx, section in enumerate(sections):
            yield _sse_event("section", {
                "index": idx + 1,
                "title": section.get("title", ""),
                "content": section,
            })

    # ════════════════════════════════════════════════════════════
    # Stage 2: PPT slide generation (per-section batch, per-slide yield)
    # ════════════════════════════════════════════════════════════

    async def _generate_single_slide(
        self, section: dict, ctx: PrepContext
    ) -> list[dict]:
        """Generate one or more slides from a syllabus section.

        Returns:
            List of slide dicts (may contain fallback placeholders).
        """
        section_json = json.dumps(section, ensure_ascii=False, indent=2)
        messages = build_stage2_messages(
            section_json=section_json,
            user_profile_summary=ctx.user_profile_summary,
        )

        try:
            response = await self.provider.chat_completion(messages)
            raw = response.content
            parsed = JSONExtractor.extract(raw, fallback=[])
        except Exception as e:
            logger.warning(f"Slide generation failed for section '{section.get('title', '')}': {e}")
            ctx.errors.append({
                "stage": "stage2",
                "section_title": section.get("title", ""),
                "error": str(e),
            })
            # Return one fallback slide
            return [{
                "page_num": 0,
                "type": "content",
                "title": section.get("title", "内容页"),
                "bullet_points": ["内容生成失败，请重试"],
                "is_fallback": True,
            }]

        slides_batch = parsed if isinstance(parsed, list) else [parsed]
        validated: list[dict] = []
        for slide in slides_batch:
            if not isinstance(slide, dict) or "title" not in slide:
                slide = {
                    "page_num": 0,
                    "type": "content",
                    "title": section.get("title", "内容页"),
                    "bullet_points": ["内容生成失败，请重试"],
                    "is_fallback": True,
                }
            # Validate and fix
            slide_vr = await self.pipeline.validate("slide", slide)
            if slide_vr.warnings:
                ctx.warnings.append({
                    "stage": "stage2",
                    "section_title": section.get("title", ""),
                    "warnings": slide_vr.warnings,
                })
            if slide_vr.fixes_applied:
                slide = self.pipeline.get_fixed_data("slide", slide)
            validated.append(slide)
        return validated

    async def _stage2_slides(self, ctx: PrepContext) -> AsyncGenerator[str, None]:
        """Generate slides: one AI call per syllabus section, yields per-slide events."""
        sections = ctx.syllabus.get("sections", [])

        if self.parallel and len(sections) >= self.PARALLEL_THRESHOLD:
            # ── Parallel path [Layer 3] ──
            slide_batches = await self.executor.map(
                items=sections,
                fn=lambda section: self._generate_single_slide(section, ctx),
                progress_callback=None,  # we yield after collection
            )
            all_slides: list[dict] = []
            for batch in slide_batches:
                if batch is None:
                    continue
                for slide in batch:
                    slide.setdefault("page_num", len(all_slides) + 1)
                    all_slides.append(slide)
                    yield _sse_event("slide", {
                        "page_num": slide["page_num"],
                        "total_pages": "?",
                        "slide": slide,
                    })
        else:
            # ── Serial path (original) ──
            all_slides = []
            for section in sections:
                slides_batch = await self._generate_single_slide(section, ctx)
                for slide in slides_batch:
                    slide.setdefault("page_num", len(all_slides) + 1)
                    all_slides.append(slide)
                    yield _sse_event("slide", {
                        "page_num": slide["page_num"],
                        "total_pages": "?",
                        "slide": slide,
                    })

        ctx.slides = all_slides
        yield _sse_event("slides_done", {
            "total_pages": len(all_slides),
        })

    # ════════════════════════════════════════════════════════════
    # Stage 3: Narration generation (per-slide)
    # ════════════════════════════════════════════════════════════

    async def _generate_single_narration(
        self, slide: dict, target_seconds: int, ctx: PrepContext
    ) -> dict:
        """Generate corrected narration for a single slide.

        Returns:
            Narration dict with _validation metadata (may be fallback on error).
        """
        slide_json = json.dumps(slide, ensure_ascii=False, indent=2)
        messages = build_stage3_messages(
            slide_json=slide_json,
            slide_title=slide.get("title", ""),
            slide_type=slide.get("type", "content"),
            target_seconds=target_seconds,
            subject=ctx.subject,
        )

        try:
            response = await self.provider.chat_completion(messages)
            raw = response.content
            narration = JSONExtractor.extract_dict(raw, fallback={})
        except Exception as e:
            logger.warning(f"Narration failed for slide {slide.get('page_num', '?')}: {e}")
            ctx.errors.append({
                "stage": "stage3",
                "page_num": slide.get("page_num", 0),
                "error": str(e),
            })
            narration = {
                "narration_text": f"（第{slide.get('page_num', '?')}页讲解词生成失败）",
                "estimated_duration_seconds": 30,
            }

        narration.setdefault("narration_text", "")
        narration.setdefault("estimated_duration_seconds", target_seconds)
        narration.setdefault("key_emphasis", [])
        narration.setdefault("pauses", [])

        # Validate + correct (Layer 2)
        corrected = self.pipeline.get_fixed_data(
            "narration", narration,
            slide=slide,
            target_seconds=target_seconds,
        )
        return corrected

    async def _stage3_narrations(self, ctx: PrepContext) -> AsyncGenerator[str, None]:
        """Generate narration text: one AI call per slide."""
        target_seconds_list = self._compute_target_seconds(ctx)
        slides = ctx.slides
        slide_count = len(slides)

        # Build task inputs: (slide, target_seconds)
        class NarrationTask:
            def __init__(self, slide: dict, target: int):
                self.slide = slide
                self.target_seconds = target

        tasks = [
            NarrationTask(slide, target_seconds_list[i] if i < len(target_seconds_list) else 60)
            for i, slide in enumerate(slides)
        ]

        if self.parallel and len(tasks) >= self.PARALLEL_THRESHOLD:
            # ── Parallel path [Layer 3] ──
            raw_narrations = await self.executor.map(
                items=tasks,
                fn=lambda t: self._generate_single_narration(t.slide, t.target_seconds, ctx),
                progress_callback=None,
            )
            all_narrations = [
                n if n is not None else {
                    "narration_text": "（讲解词生成失败）",
                    "estimated_duration_seconds": 30,
                }
                for n in raw_narrations
            ]
        else:
            # ── Serial path (original) ──
            all_narrations = []
            for i, task in enumerate(tasks):
                narration = await self._generate_single_narration(
                    task.slide, task.target_seconds, ctx
                )
                all_narrations.append(narration)

        # Yield narration events (and warnings) from collected results
        for i, (narration, task) in enumerate(zip(all_narrations, tasks)):
            page_num = task.slide.get("page_num", i + 1)
            val_score = narration.get("_validation", {}).get("overall_score", 1.0)

            # Check for warnings (re-run validation to get warn text)
            nar_vr = await self.pipeline.validate(
                "narration", narration.get("narration_text", ""),
                slide=task.slide,
                target_seconds=task.target_seconds,
            )
            if nar_vr.warnings:
                ctx.warnings.append({
                    "stage": "stage3",
                    "page_num": page_num,
                    "warnings": nar_vr.warnings,
                })
                for w in nar_vr.warnings:
                    yield _sse_event("warn", {
                        "stage": "stage3",
                        "validator": "narration_quality",
                        "page_num": page_num,
                        "message": w,
                    })

            yield _sse_event("narration", {
                "page_num": page_num,
                "total_pages": slide_count,
                "narration_text": narration["narration_text"],
                "estimated_duration_seconds": narration["estimated_duration_seconds"],
                "quality_score": val_score,
                "duration_deviation": narration.get("duration_deviation"),
                "needs_review": narration.get("_needs_review", False),
            })

        ctx.narrations = all_narrations

    # ════════════════════════════════════════════════════════════
    # Duration estimation
    # ════════════════════════════════════════════════════════════

    def _compute_target_seconds(self, ctx: PrepContext) -> list[int]:
        """Estimate target narration seconds for each slide based on teaching process."""
        total_minutes = 0
        for section in ctx.syllabus.get("sections", []):
            for step in section.get("teaching_process", []):
                dur_str = step.get("duration", "5min")
                match = re.search(r"(\d+)", dur_str)
                if match:
                    total_minutes += int(match.group(1))

        if total_minutes <= 0:
            total_minutes = 45 * ctx.total_hours  # fallback

        num_slides = len(ctx.slides)
        if num_slides == 0:
            return []

        seconds_per_slide = (total_minutes * 60) / num_slides
        # Clamp to [30, 180] seconds per slide
        clamped = max(30, min(180, int(seconds_per_slide)))
        return [clamped] * num_slides

    # ════════════════════════════════════════════════════════════
    # Database persistence
    # ════════════════════════════════════════════════════════════

    async def _save_to_db(self, ctx: PrepContext) -> int:
        """Write the syllabus to teaching_contents table."""
        from ..database.database import AsyncSessionLocal
        from ..database.models import TeachingContent

        async with AsyncSessionLocal() as session:
            record = TeachingContent(
                user_id=ctx.user_id,
                type="lesson_plan",
                title=ctx.title,
                source_type="from_weakpoint" if ctx.weak_point_ids else "from_text",
                source_content_json=json.dumps({
                    "title": ctx.title,
                    "subject": ctx.subject,
                    "grade": ctx.grade,
                    "knowledge_point_ids": ctx.knowledge_point_ids,
                    "teaching_goals": ctx.teaching_goals,
                    "total_hours": ctx.total_hours,
                    "style": ctx.style,
                    "weak_point_ids": ctx.weak_point_ids,
                    "user_profile_summary": ctx.user_profile_summary,
                }, ensure_ascii=False),
                generated_content_json=json.dumps({
                    "syllabus": ctx.syllabus,
                }, ensure_ascii=False),
                ppt_structure_json=json.dumps([]),
                status="published",
            )
            session.add(record)
            await session.commit()
            await session.refresh(record)
            return record.id

    # ════════════════════════════════════════════════════════════
    # [generate-ppt] Standalone PPT generation (non-streaming JSON)
    # ════════════════════════════════════════════════════════════

    async def generate_ppt(
        self,
        prep_id: int,
        template_style: str = "default",
        max_slides: int = 20,
    ) -> tuple[int, list[dict]]:
        """基于已有备课内容单独生成PPT结构（跳过Stage 1/3），非流式JSON返回。

        从 teaching_contents 表读取已保存的 syllabus，
        复用 _generate_single_slide 逻辑生成PPT，更新数据库后返回。

        Args:
            prep_id: 备课内容ID。
            template_style: PPT模板风格（保留字段，暂未使用）。
            max_slides: 最大页数（保留字段，暂未使用）。

        Returns:
            (ppt_id, slides) — ppt_id 与 prep_id 相同（同一备课记录），
            slides 为 PPT 结构 JSON 数组。

        Raises:
            ValueError: 备课记录不存在或没有教学大纲数据。
            Exception: AI生成或数据库写入失败。
        """
        from sqlalchemy import select, update
        from ..database.database import AsyncSessionLocal
        from ..database.models import TeachingContent

        # 1. 读取备课记录
        async with AsyncSessionLocal() as session:
            result = await session.execute(
                select(TeachingContent).where(TeachingContent.id == prep_id)
            )
            record = result.scalar_one_or_none()

        if record is None:
            raise ValueError(f"备课记录 {prep_id} 不存在")

        # 2. 解析已保存的教学大纲
        content = json.loads(record.generated_content_json or "{}")
        syllabus = content.get("syllabus", {})
        if not syllabus:
            raise ValueError(f"备课记录 {prep_id} 中没有教学大纲数据，无法生成PPT")

        # 3. 构造简版上下文
        ppt_ctx = PrepContext(
            user_id=record.user_id,
            title=record.title,
            subject="",
            grade="",
            knowledge_point_ids=[],
            teaching_goals=[],
        )
        ppt_ctx.syllabus = syllabus

        # 4. 复用 Stage 2 串行逻辑生成所有幻灯片
        all_slides: list[dict] = []
        for section in syllabus.get("sections", []):
            slides_batch = await self._generate_single_slide(section, ppt_ctx)
            for slide in slides_batch:
                slide.setdefault("page_num", len(all_slides) + 1)
                all_slides.append(slide)

        ppt_ctx.slides = all_slides

        # 4.5 为每页生成配图（失败降级，不影响 PPT 结构）
        try:
            await self._enrich_slides_with_images(all_slides, topic=record.title)
        except Exception as e:
            logger.error(f"PPT 配图步骤异常（继续导出纯文字版）: {e}")

        # 5. 更新数据库中的 PPT 结构
        if all_slides:
            async with AsyncSessionLocal() as session:
                await session.execute(
                    update(TeachingContent)
                    .where(TeachingContent.id == prep_id)
                    .values(
                        ppt_structure_json=json.dumps(
                            all_slides, ensure_ascii=False
                        ),
                        updated_at=datetime.utcnow(),
                    )
                )
                await session.commit()

        # 6. 返回 (pptId, slides)，pptId 复用 prepId
        return prep_id, all_slides

    # ════════════════════════════════════════════════════════════
    # [PPT配图] 为幻灯片 JSON 生成配图 URL（image_url 字段）
    # ════════════════════════════════════════════════════════════

    async def _select_image_provider(self):
        """按 config 默认值选择生成 provider；未注册则 fallback pollinations；都没有返回 None。"""
        from .image.providers.base import provider_registry

        registered = {p.provider.value for p in provider_registry.get_generation_providers()}
        if not registered:
            return None

        from .image.models import ImageProvider
        try:
            from .config_service import get_config_service
            cfg = get_config_service().get_all_config()
            default = str(cfg.get("default_ai_image_provider", "dalle") or "dalle").lower()
        except Exception:
            default = "dalle"

        if default in registered:
            return ImageProvider(default)
        if ImageProvider.POLLINATIONS.value in registered:
            return ImageProvider.POLLINATIONS
        return None

    async def _generate_slide_image_url(
        self, slide: dict, topic: str, page_num: int, total_pages: int, provider
    ) -> Optional[str]:
        """为单页生成配图，成功返回 image_url，失败返回 None（绝不抛异常）。"""
        from .image.adapters.ppt_prompt_adapter import PPTSlideContext
        from .image.image_service import get_image_service
        from .url_service import build_image_url

        suggestion = (slide.get("image_suggestion") or "").strip()
        bullets = [b for b in (slide.get("bullet_points") or []) if b]
        # image_suggestion 优先，附带最多 3 条要点补充语境
        if suggestion:
            content = suggestion + ("；" + "；".join(bullets[:3]) if bullets else "")
        else:
            content = "；".join(bullets) or slide.get("title", "")

        context = PPTSlideContext(
            title=slide.get("title", ""),
            content=content,
            scenario="education",
            topic=topic,
            page_number=page_num,
            total_pages=total_pages,
            slide_type=slide.get("type", "content"),
            language="zh",
        )
        image_service = get_image_service()
        result = await image_service.generate_ppt_slide_image(context, provider)
        if result.success and result.image_info:
            return build_image_url(result.image_info.image_id)
        logger.warning("第 %s 页配图失败: %s", slide.get("page_num", "?"), result.message)
        return None

    async def _enrich_slides_with_images(self, slides: list[dict], topic: str = "") -> list[dict]:
        """并发为每页生成配图并写 slide["image_url"]；单页失败降级，不阻塞整体。"""
        import asyncio

        provider = await self._select_image_provider()
        if provider is None:
            logger.warning("没有可用的图片生成 provider，跳过 PPT 配图")
            return slides

        total = len(slides)
        semaphore = asyncio.Semaphore(3)  # 限并发 3

        async def _one(slide: dict) -> None:
            if not isinstance(slide, dict):
                return
            try:
                async with semaphore:
                    url = await self._generate_slide_image_url(
                        slide, topic, slide.get("page_num", 0), total, provider
                    )
                if url:
                    slide["image_url"] = url
                    logger.info("第 %s 页配图成功: %s", slide.get("page_num"), url)
            except Exception as e:
                logger.warning("第 %s 页配图异常: %s", slide.get("page_num", "?"), e)

        await asyncio.gather(*(_one(s) for s in slides))
        return slides

    # ════════════════════════════════════════════════════════════
    # [internal] Non-streaming full pipeline (for Java internal API)
    # ════════════════════════════════════════════════════════════

    async def generate_and_return(self, ctx: PrepContext) -> dict:
        """三段管线执行后直接返回完整结果 dict（不经过 SSE）。

        Java 内部调用时使用此方法，通过 internal_ai.py 暴露。

        Returns:
            {
                "prep_id": int,
                "total_pages": int,
                "total_duration_seconds": int,
                "syllabus": {...},
                "slides": [...],
                "narrations": [...]
            }
            出错时返回 {"error": "错误信息"}
        """
        try:
            # Stage 1: Syllabus (stream non-SSE)
            messages = build_stage1_messages(
                subject=ctx.subject,
                grade=ctx.grade,
                knowledge_point_ids=ctx.knowledge_point_ids,
                teaching_goals=ctx.teaching_goals,
                total_hours=ctx.total_hours,
                style=ctx.style,
                weak_point_ids=ctx.weak_point_ids,
                user_profile_summary=ctx.user_profile_summary,
            )
            full_content = ""
            async for chunk in self.provider.stream_chat_completion(messages):
                full_content += chunk

            syllabus = JSONExtractor.extract_dict(full_content)
            if not syllabus.get("sections"):
                return {"error": "生成的大纲中没有课时(sections)数据"}
            ctx.syllabus = syllabus

            # Validate
            vr = await self.pipeline.validate("syllabus", syllabus)
            if vr.fixes_applied:
                ctx.syllabus = self.pipeline.get_fixed_data("syllabus", syllabus)

            prep_id = 0
            # Stage 2: Slides
            all_slides: list[dict] = []
            for section in ctx.syllabus.get("sections", []):
                slides_batch = await self._generate_single_slide(section, ctx)
                for slide in slides_batch:
                    slide.setdefault("page_num", len(all_slides) + 1)
                    all_slides.append(slide)
            ctx.slides = all_slides

            # Save to database
            if ctx.syllabus:
                prep_id = await self._save_to_db(ctx)

            return {
                "prep_id": prep_id,
                "total_pages": len(all_slides),
                "syllabus": ctx.syllabus,
                "slides": all_slides,
            }

        except Exception as e:
            logger.exception("generate_and_return failed")
            return {"error": str(e)}
