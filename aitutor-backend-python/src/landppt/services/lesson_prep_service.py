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
from ..core.config import ai_config
from ..utils.json_extractor import JSONExtractor
from ..validators import ValidationPipeline
from ..ab_testing import ABExperiment
from .parallel_engine import ParallelExecutor
from .prompts.lesson_prep_prompts import (
    build_stage1_messages,
    build_stage2_messages,
    build_stage3_messages,
    build_goals_messages,
    build_process_messages,
)

logger = logging.getLogger(__name__)

# [P0质量] Stage 1 重试次数（共 MAX_STAGE1_RETRIES+1 次尝试）
MAX_STAGE1_RETRIES = 2
# [Layer 1 质量] Stage 2 重试次数（共 MAX_STAGE2_RETRIES+1 次尝试，仅触发阻断性问题时重试）
MAX_STAGE2_RETRIES = 2


# ─── Context object (in-memory, passed between stages) ───

@dataclass
class PrepContext:
    user_id: int
    title: str
    subject: str
    grade: str
    knowledge_point_ids: list[int]
    knowledge_point_names: list[str] = field(default_factory=list)
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

    @property
    def quality_guard(self):
        """Lazy-init quality guard (Layer 1 hard metrics)."""
        from ..validators import QualityGuard
        if getattr(self, "_quality_guard", None) is None:
            self._quality_guard = QualityGuard()
        return self._quality_guard

    @property
    def llm_judge(self):
        """Lazy-init LLM judge (Layer 2 semantic quality)."""
        from ..validators import LLMJudge
        if getattr(self, "_llm_judge", None) is None:
            self._llm_judge = LLMJudge()
        return self._llm_judge

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
        knowledge_point_names: list[str] | None = None,
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
            knowledge_point_names=knowledge_point_names or [],
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
        """Generate syllabus via streaming AI, yield outline + per-section events.

        [P0质量] 重试机制：流式生成或 JSON 解析失败时自动重试，最多 3 次尝试。
        [P0质量] JSON 模式：启用 response_format=json_object，强制 AI 输出合法 JSON。
        [P0质量] 低温度：使用 stage1_temperature（默认 0.4），提高结构化输出稳定性。
        """
        messages = build_stage1_messages(
            subject=ctx.subject,
            grade=ctx.grade,
            knowledge_point_ids=ctx.knowledge_point_ids,
            knowledge_point_names=ctx.knowledge_point_names,
            teaching_goals=ctx.teaching_goals,
            total_hours=ctx.total_hours,
            style=ctx.style,
            weak_point_ids=ctx.weak_point_ids,
            user_profile_summary=ctx.user_profile_summary,
        )

        syllabus = None
        for attempt in range(MAX_STAGE1_RETRIES + 1):
            full_content = ""
            try:
                # [P0质量] 传入 json_mode + stage1_temperature
                async for chunk in self.provider.stream_chat_completion(
                    messages,
                    json_mode=True,
                    temperature=ai_config.stage1_temperature,
                ):
                    full_content += chunk

                # 尝试解析 JSON
                syllabus = JSONExtractor.extract_dict(full_content)
                break  # 解析成功，退出重试循环

            except Exception as e:
                attempt_num = attempt + 1
                if attempt < MAX_STAGE1_RETRIES:
                    logger.warning(
                        f"Stage 1 attempt {attempt_num}/{MAX_STAGE1_RETRIES+1} failed: {e}, retrying..."
                    )
                    yield _sse_event("warn", {
                        "stage": "stage1",
                        "message": f"大纲生成重试中({attempt_num + 1}/{MAX_STAGE1_RETRIES + 1})",
                    })
                else:
                    logger.exception(f"Stage 1 failed after {MAX_STAGE1_RETRIES + 1} attempts")
                    yield _sse_event("error", {
                        "stage": "stage1",
                        "message": f"大纲生成失败（已重试{MAX_STAGE1_RETRIES}次）: {e}",
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
            # [P0质量] 传入 json_mode + stage2_temperature
            response = await self.provider.chat_completion(
                messages,
                json_mode=True,
                temperature=ai_config.stage2_temperature,
            )
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
        """Generate slides: one AI call per syllabus section, yields per-slide events.

        [Layer 1 质量重试] 若质量守卫检测到阻断性问题（页数不足/占位页过多），
        会自动重试最多 MAX_STAGE2_RETRIES 次，每次重试丢弃旧 slides 重新生成。
        """
        sections = ctx.syllabus.get("sections", [])

        all_slides: list[dict] = []
        slides_qr = None

        for attempt in range(MAX_STAGE2_RETRIES + 1):
            all_slides = []

            # ── 生成 slides（重试时重新生成，丢弃旧结果） ──
            if self.parallel and len(sections) >= self.PARALLEL_THRESHOLD:
                slide_batches = await self.executor.map(
                    items=sections,
                    fn=lambda section: self._generate_single_slide(section, ctx),
                    progress_callback=None,
                )
                for batch in slide_batches:
                    if batch is None:
                        continue
                    for slide in batch:
                        slide.setdefault("page_num", len(all_slides) + 1)
                        all_slides.append(slide)
                        # 重试时不重复发送 slide 事件（只在最后一次成功时发）
                        if attempt == MAX_STAGE2_RETRIES:
                            yield _sse_event("slide", {
                                "page_num": slide["page_num"],
                                "total_pages": "?",
                                "slide": slide,
                            })
            else:
                for section in sections:
                    slides_batch = await self._generate_single_slide(section, ctx)
                    for slide in slides_batch:
                        slide.setdefault("page_num", len(all_slides) + 1)
                        all_slides.append(slide)
                        if attempt == MAX_STAGE2_RETRIES:
                            yield _sse_event("slide", {
                                "page_num": slide["page_num"],
                                "total_pages": "?",
                                "slide": slide,
                            })

            # ── [Layer 1] 质量守卫检测 ──
            slides_qr = self.quality_guard.check_slides(all_slides)

            if not slides_qr.is_blocking:
                # 质量合格，跳出重试循环
                break

            # 阻断性问题，需要重试
            if attempt < MAX_STAGE2_RETRIES:
                logger.warning(
                    f"Stage 2 attempt {attempt + 1}/{MAX_STAGE2_RETRIES + 1} "
                    f"blocking issues: {slides_qr.blocking_issues}, retrying..."
                )
                yield _sse_event("warn", {
                    "stage": "stage2",
                    "message": f"PPT 生成质量不足，重试中({attempt + 2}/{MAX_STAGE2_RETRIES + 1})",
                    "blocking_issues": slides_qr.blocking_issues,
                })
            else:
                logger.error(
                    f"Stage 2 failed after {MAX_STAGE2_RETRIES + 1} attempts: "
                    f"{slides_qr.blocking_issues}"
                )

        ctx.slides = all_slides

        # 收集所有警告（包括重试中的警告）
        if slides_qr and slides_qr.issues:
            ctx.warnings.append({"stage": "stage2", "quality_issues": slides_qr.issues})
        if slides_qr and slides_qr.is_blocking:
            ctx.warnings.append({
                "stage": "stage2",
                "blocking_issues": slides_qr.blocking_issues,
                "note": f"已重试{MAX_STAGE2_RETRIES}次，仍存在阻断性问题",
            })

        yield _sse_event("slides_done", {
            "total_pages": len(all_slides),
            "quality_score": slides_qr.score if slides_qr else 0,
            "quality_issues": slides_qr.issues if slides_qr and slides_qr.issues else None,
            "retries": attempt if attempt > 0 else 0,
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

    async def _save_to_db(self, ctx: PrepContext, quality_info: dict = None) -> int:
        """Write the syllabus to teaching_contents table.

        Args:
            ctx: 备课上下文。
            quality_info: 质量报告（Layer 1 + LLM Judge），存入 generated_content_json。
        """
        from ..database.database import AsyncSessionLocal
        from ..database.models import TeachingContent

        generated_content = {
            "syllabus": ctx.syllabus,
            "subject": ctx.subject,
            "grade": ctx.grade,
            "knowledgePoints": [
                {"id": kp_id, "name": name}
                for kp_id, name in zip(
                    ctx.knowledge_point_ids,
                    ctx.knowledge_point_names or [""] * len(ctx.knowledge_point_ids),
                )
            ],
        }
        if quality_info:
            generated_content["quality"] = quality_info

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
                generated_content_json=json.dumps(generated_content, ensure_ascii=False),
                ppt_structure_json=json.dumps(ctx.slides, ensure_ascii=False) if ctx.slides else json.dumps([]),
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
            # [P0质量] 传 knowledge_point_names + json_mode + stage1_temperature + 重试
            messages = build_stage1_messages(
                subject=ctx.subject,
                grade=ctx.grade,
                knowledge_point_ids=ctx.knowledge_point_ids,
                knowledge_point_names=ctx.knowledge_point_names,
                teaching_goals=ctx.teaching_goals,
                total_hours=ctx.total_hours,
                style=ctx.style,
                weak_point_ids=ctx.weak_point_ids,
                user_profile_summary=ctx.user_profile_summary,
            )

            syllabus = None
            for attempt in range(MAX_STAGE1_RETRIES + 1):
                full_content = ""
                try:
                    async for chunk in self.provider.stream_chat_completion(
                        messages,
                        json_mode=True,
                        temperature=ai_config.stage1_temperature,
                    ):
                        full_content += chunk
                    syllabus = JSONExtractor.extract_dict(full_content)
                    break
                except Exception as e:
                    if attempt < MAX_STAGE1_RETRIES:
                        logger.warning(
                            f"generate_and_return Stage 1 attempt {attempt + 1} failed: {e}, retrying..."
                        )
                    else:
                        logger.exception(f"generate_and_return Stage 1 failed after {MAX_STAGE1_RETRIES + 1} attempts")
                        return {"error": f"大纲生成失败（已重试{MAX_STAGE1_RETRIES}次）: {e}"}

            if not syllabus.get("sections"):
                return {"error": "生成的大纲中没有课时(sections)数据"}
            ctx.syllabus = syllabus

            # Validate
            vr = await self.pipeline.validate("syllabus", syllabus)
            if vr.fixes_applied:
                ctx.syllabus = self.pipeline.get_fixed_data("syllabus", syllabus)

            prep_id = 0
            # Stage 2: Slides（含质量重试）
            all_slides: list[dict] = []
            final_qr = None

            for attempt in range(MAX_STAGE2_RETRIES + 1):
                all_slides = []
                for section in ctx.syllabus.get("sections", []):
                    slides_batch = await self._generate_single_slide(section, ctx)
                    for slide in slides_batch:
                        slide.setdefault("page_num", len(all_slides) + 1)
                        all_slides.append(slide)

                # [Layer 1] 质量守卫：检测 slides 阻断性问题
                slides_qr = self.quality_guard.check_slides(all_slides)

                if not slides_qr.is_blocking:
                    break

                if attempt < MAX_STAGE2_RETRIES:
                    logger.warning(
                        f"generate_and_return Stage 2 attempt {attempt + 1}/{MAX_STAGE2_RETRIES + 1} "
                        f"blocking issues: {slides_qr.blocking_issues}, retrying..."
                    )
                else:
                    logger.error(
                        f"generate_and_return Stage 2 failed after {MAX_STAGE2_RETRIES + 1} attempts: "
                        f"{slides_qr.blocking_issues}"
                    )

            ctx.slides = all_slides

            # [Layer 1] 质量守卫：检测最终产物的硬性指标
            final_qr = self.quality_guard.check_final(ctx.syllabus, all_slides)
            quality_info = {
                "score": final_qr.score,
                "is_low_quality": final_qr.is_low_quality,
                "issues": final_qr.issues if final_qr.issues else None,
                "blocking_issues": final_qr.blocking_issues if final_qr.is_blocking else None,
                "retries": attempt if attempt > 0 else 0,
            }
            if final_qr.issues:
                logger.warning(f"[质量守卫] 检测到 {len(final_qr.issues)} 项问题: {final_qr.issues[:3]}")
            if final_qr.is_blocking:
                logger.error(f"[质量守卫] 阻断性问题: {final_qr.blocking_issues}")

            # [Layer 2] LLM 语义评判：Layer 1 通过后自动执行
            # 失败时降级为 None，不阻断生成流程
            llm_judge_result = None
            try:
                llm_judge_result = await self.llm_judge.judge_all(
                    syllabus=ctx.syllabus,
                    slides=all_slides,
                    subject=ctx.subject,
                    grade=ctx.grade,
                    knowledge_point_names=ctx.knowledge_point_names or None,
                )
                if llm_judge_result.get("error"):
                    logger.warning(f"[LLM Judge] 评判失败（降级为null）: {llm_judge_result['error']}")
                    llm_judge_result = None
                else:
                    logger.info(
                        f"[LLM Judge] 评判完成: 总分={llm_judge_result['score']:.0f}, "
                        f"needs_review={llm_judge_result['needs_review']}"
                    )
            except Exception as e:
                logger.warning(f"[LLM Judge] 异常（降级为null）: {e}")
                llm_judge_result = None

            # [Layer 2.5] 低分自动修复：评分 < 60 时，根据反馈修复并重新评判
            # 最多修复 1 次，避免成本失控
            if (
                llm_judge_result
                and llm_judge_result["score"] < self.llm_judge.LOW_SCORE_THRESHOLD
            ):
                logger.info(
                    f"[LLM Judge] 评分 {llm_judge_result['score']:.0f} < 60，触发自动修复"
                )
                repaired = await self._repair_low_quality(
                    ctx=ctx,
                    judge_result=llm_judge_result,
                )
                if repaired:
                    # 修复成功，更新 ctx 中的内容
                    ctx.syllabus = repaired["syllabus"]
                    ctx.slides = repaired["slides"]
                    all_slides = repaired["slides"]

                    # 重新跑 Layer 1 检测（确保修复后结构没问题）
                    final_qr = self.quality_guard.check_final(ctx.syllabus, all_slides)
                    quality_info["score"] = final_qr.score
                    quality_info["is_low_quality"] = final_qr.is_low_quality
                    quality_info["issues"] = final_qr.issues if final_qr.issues else None
                    quality_info["blocking_issues"] = (
                        final_qr.blocking_issues if final_qr.is_blocking else None
                    )

                    # 重新跑 LLM Judge 验证修复效果
                    try:
                        rejudge = await self.llm_judge.judge_all(
                            syllabus=ctx.syllabus,
                            slides=all_slides,
                            subject=ctx.subject,
                            grade=ctx.grade,
                            knowledge_point_names=ctx.knowledge_point_names or None,
                        )
                        if not rejudge.get("error"):
                            llm_judge_result = rejudge
                            llm_judge_result["repaired"] = True
                            logger.info(
                                f"[LLM Judge] 修复后重新评判: "
                                f"{llm_judge_result['score']:.0f}分"
                            )
                    except Exception as e:
                        logger.warning(f"[LLM Judge] 修复后重新评判失败: {e}")

            quality_info["llm_judge"] = llm_judge_result

            # Save to database（含质量报告）
            if ctx.syllabus:
                prep_id = await self._save_to_db(ctx, quality_info=quality_info)

            return {
                "prep_id": prep_id,
                "total_pages": len(all_slides),
                "syllabus": ctx.syllabus,
                "slides": all_slides,
                "quality": quality_info,
            }

        except Exception as e:
            logger.exception("generate_and_return failed")
            return {"error": str(e)}

    # ══════════════════════════════════════════════════════════
    #  Low-score feedback repair
    # ══════════════════════════════════════════════════════════

    async def _repair_low_quality(
        self,
        ctx: PrepContext,
        judge_result: dict,
    ) -> Optional[dict]:
        """根据 LLM Judge 反馈修复低质量内容。

        从评判结果中提取 issues + suggestions，分别修复大纲和 PPT。
        修复失败的部分保留原始内容。

        Args:
            ctx: 备课上下文（含原始 syllabus 和 slides）。
            judge_result: LLM Judge 的评判结果。

        Returns:
            {"syllabus": 修复后大纲, "slides": 修复后slides}
            修复全部失败时返回 None。
        """
        syll_judge = judge_result.get("syllabus", {})
        slides_judge = judge_result.get("slides", {})

        # 提取问题和建议
        syll_issues = syll_judge.get("issues", [])
        syll_suggestions = syll_judge.get("suggestions", [])
        slides_issues = slides_judge.get("issues", [])
        slides_suggestions = slides_judge.get("suggestions", [])

        repaired_syllabus = ctx.syllabus
        repaired_slides = ctx.slides

        any_success = False

        # 修复大纲（有大纲问题且评判成功时才修复）
        if syll_issues and not syll_judge.get("error"):
            new_syllabus = await self.llm_judge.repair_syllabus(
                syllabus=ctx.syllabus,
                issues=syll_issues,
                suggestions=syll_suggestions,
                subject=ctx.subject,
                grade=ctx.grade,
            )
            if new_syllabus is not None:
                repaired_syllabus = new_syllabus
                any_success = True

        # 修复 PPT（有 PPT 问题且评判成功时才修复）
        if slides_issues and not slides_judge.get("error"):
            new_slides = await self.llm_judge.repair_slides(
                slides=ctx.slides,
                issues=slides_issues,
                suggestions=slides_suggestions,
                subject=ctx.subject,
                grade=ctx.grade,
            )
            if new_slides is not None:
                repaired_slides = new_slides
                any_success = True

        if not any_success:
            logger.warning("[LLM Judge] 修复全部失败，保留原始内容")
            return None

        return {
            "syllabus": repaired_syllabus,
            "slides": repaired_slides,
        }

    # ══════════════════════════════════════════════════════════
    #  Quality Report Query
    # ══════════════════════════════════════════════════════════

    async def get_quality_report(self, prep_id: int) -> dict:
        """查询已保存的备课内容质量报告。

        从 teaching_contents 表读取 generated_content_json，
        解析其中的 quality 字段返回。

        Args:
            prep_id: 备课内容ID。

        Returns:
            {
                "prep_id": int,
                "title": str,
                "subject": str,
                "grade": str,
                "quality": {...} | None
            }
            备课记录不存在时抛 ValueError。

        Raises:
            ValueError: 备课记录不存在。
        """
        from sqlalchemy import select
        from ..database.database import AsyncSessionLocal
        from ..database.models import TeachingContent

        async with AsyncSessionLocal() as session:
            result = await session.execute(
                select(TeachingContent).where(TeachingContent.id == prep_id)
            )
            record = result.scalar_one_or_none()

        if record is None:
            raise ValueError(f"备课记录 {prep_id} 不存在")

        content = json.loads(record.generated_content_json or "{}")
        quality = content.get("quality")

        if not quality:
            raise ValueError(f"备课记录 {prep_id} 无质量报告（尚未生成或生成失败）")

        return {
            "prep_id": prep_id,
            "title": record.title,
            "subject": content.get("subject", ""),
            "grade": content.get("grade", ""),
            "quality": quality,
        }

    # ══════════════════════════════════════════════════════════
    #  Auxiliary: Generate Goals / Process
    # ══════════════════════════════════════════════════════════

    async def generate_goals(
        self,
        subject: str,
        grade: str,
        knowledge_point_ids: list[int],
        knowledge_point_names: Optional[list[str]] = None,
        goal_direction: Optional[str] = None,
        weak_point_ids: Optional[list[int]] = None,
    ) -> dict:
        """Generate teaching goals (3-5 items) for the given knowledge points.

        Returns:
            {"goals": ["目标1", "目标2", ...]}
        """
        messages = build_goals_messages(
            subject=subject,
            grade=grade,
            knowledge_point_ids=knowledge_point_ids,
            knowledge_point_names=knowledge_point_names,
            goal_direction=goal_direction,
            weak_point_ids=weak_point_ids,
        )

        for attempt in range(MAX_STAGE1_RETRIES + 1):
            try:
                result = await self.provider.chat_completion(
                    messages,
                    json_mode=True,
                    temperature=ai_config.stage1_temperature,
                )
                data = JSONExtractor.extract_dict(result)
                goals = data.get("goals", [])

                if not isinstance(goals, list) or len(goals) < 2:
                    raise ValueError(f"Generated {len(goals)} goals, expected at least 2")

                # Filter out empty/short goals
                valid_goals = [g for g in goals if isinstance(g, str) and len(g.strip()) >= 5]
                if len(valid_goals) < 2:
                    raise ValueError(f"Only {len(valid_goals)} valid goals after filtering")

                return {"goals": valid_goals[:5]}

            except Exception as e:
                if attempt < MAX_STAGE1_RETRIES:
                    logger.warning(f"generate_goals attempt {attempt + 1} failed: {e}, retrying...")
                else:
                    logger.exception(f"generate_goals failed after {MAX_STAGE1_RETRIES + 1} attempts")
                    return {"error": f"教学目标生成失败（已重试{MAX_STAGE1_RETRIES}次）: {e}"}

        return {"error": "教学目标生成失败"}

    async def generate_process(
        self,
        subject: str,
        grade: str,
        knowledge_point_ids: list[int],
        teaching_goals: list[str],
        knowledge_point_names: Optional[list[str]] = None,
        total_hours: int = 1,
        section_index: int = 1,
        section_title: Optional[str] = None,
    ) -> dict:
        """Generate teaching process (4-6 steps) for the given goals.

        Returns:
            {"teaching_process": [{step, duration, teacher_activity, student_activity, design_intent}, ...]}
        """
        messages = build_process_messages(
            subject=subject,
            grade=grade,
            knowledge_point_ids=knowledge_point_ids,
            teaching_goals=teaching_goals,
            knowledge_point_names=knowledge_point_names,
            total_hours=total_hours,
            section_index=section_index,
            section_title=section_title,
        )

        for attempt in range(MAX_STAGE1_RETRIES + 1):
            try:
                result = await self.provider.chat_completion(
                    messages,
                    json_mode=True,
                    temperature=ai_config.stage1_temperature,
                )
                data = JSONExtractor.extract_dict(result)
                process = data.get("teaching_process", [])

                if not isinstance(process, list) or len(process) < 3:
                    raise ValueError(f"Generated {len(process)} steps, expected at least 3")

                # Validate each step has required fields
                valid_steps = []
                for step in process:
                    if not isinstance(step, dict):
                        continue
                    required = ["step", "duration", "teacher_activity", "student_activity", "design_intent"]
                    if all(k in step and step[k] for k in required):
                        valid_steps.append(step)

                if len(valid_steps) < 3:
                    raise ValueError(f"Only {len(valid_steps)} valid steps after filtering")

                return {"teaching_process": valid_steps[:6]}

            except Exception as e:
                if attempt < MAX_STAGE1_RETRIES:
                    logger.warning(f"generate_process attempt {attempt + 1} failed: {e}, retrying...")
                else:
                    logger.exception(f"generate_process failed after {MAX_STAGE1_RETRIES + 1} attempts")
                    return {"error": f"教学过程生成失败（已重试{MAX_STAGE1_RETRIES}次）: {e}"}

        return {"error": "教学过程生成失败"}
