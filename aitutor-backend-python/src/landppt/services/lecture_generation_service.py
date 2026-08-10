"""
M4 讲课生成 —— SSE 流式服务
参考 M5 LessonPrepService 的 StreamingResponse 模式

Java TeachingController → POST /api/ai/stream-generate-teaching → 本服务
SSE 事件: outline / slide / done
"""
import json
import logging
import re
from typing import AsyncGenerator

from ..ai import get_ai_provider, AIMessage, MessageRole
from ..utils.json_extractor import JSONExtractor
from .lecture_prompts import (
    build_teaching_system_message,
    build_outline_prompt,
    build_slide_prompt,
)

logger = logging.getLogger(__name__)


class LectureGenerationService:

    async def run_stream(
        self,
        source_text: str,
        course_id: str = "",
        grade: str = "",
        weak_points: str = "",
    ) -> AsyncGenerator[str, None]:
        """流式生成讲课内容，逐页 SSE 推送。"""
        provider = get_ai_provider()

        logger.info("M4 lecture: generating outline...")
        outline = await self._generate_outline(provider, source_text, grade, weak_points)

        total_pages = outline.get("totalPages", 5)
        title = outline.get("title", "AI 即时讲课")

        yield self._sse_event("outline", {
            "title": title,
            "content": outline.get("outline", []),
            "totalPages": total_pages,
            "outline": outline.get("outline", []),
        })

        pages = outline.get("outline", [])
        slides = []

        for i, page_info in enumerate(pages):
            page_num = page_info.get("page", i + 1)
            slide_type = page_info.get("type", "content")
            page_title = page_info.get("title", "")

            logger.info("M4 lecture: generating slide %s/%s", page_num, total_pages)

            slide = await self._generate_slide(
                provider, title, page_num, total_pages,
                slide_type, page_title, source_text, grade, weak_points,
                slides,
            )
            if not slide.get("pageNum"):
                slide["pageNum"] = page_num
            if not slide.get("type"):
                slide["type"] = slide_type
            if not slide.get("title"):
                slide["title"] = page_title

            slides.append(slide)

            yield self._sse_event("slide", {
                "pageNum": page_num,
                "totalPages": total_pages,
                "slide": slide,
            })

        yield self._sse_event("done", {
            "lectureId": course_id,
            "prepId": course_id,
            "totalPages": total_pages,
            "slides": slides,
        })

    async def _generate_outline(self, provider, source_text, grade, weak_points):
        prompt = build_outline_prompt(source_text, grade, weak_points)
        messages = [
            build_teaching_system_message(),
            AIMessage(role=MessageRole.USER, content=prompt),
        ]
        response = await provider.chat_completion(messages, temperature=0.7)
        return JSONExtractor.extract_dict(response.content, fallback={
            "title": "AI 讲课",
            "totalPages": 5,
            "outline": [
                {"page": 1, "type": "cover", "title": "封面"},
                {"page": 2, "type": "content", "title": "内容"},
                {"page": 3, "type": "example", "title": "例题"},
                {"page": 4, "type": "summary", "title": "总结"},
                {"page": 5, "type": "ending", "title": "结束"},
            ],
        })

    async def _generate_slide(
        self, provider, title, page_num, total_pages,
        slide_type, page_title, source_text, grade, weak_points,
        previous_slides,
    ):
        previous_summary = self._summarize_slides(previous_slides)
        prompt = build_slide_prompt(
            title, page_title, page_num, total_pages,
            slide_type, source_text, grade, weak_points,
            previous_summary,
        )
        messages = [
            build_teaching_system_message(),
            AIMessage(role=MessageRole.USER, content=prompt),
        ]
        response = await provider.chat_completion(messages, temperature=0.7)
        slide = self._parse_slide_content(response.content, page_num)

        if not self._is_complete_slide(slide):
            logger.warning(
                "M4 lecture: slide %s returned invalid content, regenerating once",
                page_num,
            )
            retry_prompt = build_slide_prompt(
                title, page_title, page_num, total_pages,
                slide_type, source_text, grade, weak_points,
                previous_summary,
                "上一次输出不是完整的幻灯片 JSON 对象。请严格返回包含 title 和真实 bulletPoints 的 JSON。",
            )
            retry_response = await provider.chat_completion(
                [
                    build_teaching_system_message(),
                    AIMessage(role=MessageRole.USER, content=retry_prompt),
                ],
                temperature=0.6,
            )
            slide = self._parse_slide_content(retry_response.content, page_num)
            if not self._is_complete_slide(slide):
                raise ValueError(f"第{page_num}页生成失败：模型未返回完整幻灯片内容")

        duplicate_page = self._find_duplicate_page(slide, previous_slides)
        if duplicate_page is not None and slide_type not in {"summary", "ending"}:
            logger.warning(
                "M4 lecture: slide %s duplicates slide %s, regenerating once",
                page_num,
                duplicate_page,
            )
            retry_prompt = build_slide_prompt(
                title, page_title, page_num, total_pages,
                slide_type, source_text, grade, weak_points,
                previous_summary,
                (
                    f"上一次输出与第{duplicate_page}页内容过于相似。"
                    "请更换知识切入点、例子和要点，确保本页有独立教学价值。"
                ),
            )
            retry_messages = [
                build_teaching_system_message(),
                AIMessage(role=MessageRole.USER, content=retry_prompt),
            ]
            retry_response = await provider.chat_completion(
                retry_messages, temperature=0.8
            )
            retry_slide = self._normalize_slide(
                JSONExtractor.extract_dict(retry_response.content, fallback={})
            )
            if retry_slide:
                slide = retry_slide

        return slide

    @staticmethod
    def _normalize_slide(slide: dict) -> dict:
        if "body" in slide and "bulletPoints" not in slide:
            slide["bulletPoints"] = slide.pop("body")
        if "narration_text" in slide and "narrationText" not in slide:
            slide["narrationText"] = slide.pop("narration_text")
        if "estimated_duration_seconds" in slide and "estimatedDurationSec" not in slide:
            slide["estimatedDurationSec"] = slide.pop("estimated_duration_seconds")
        return slide

    @classmethod
    def _parse_slide_content(cls, content: str, page_num: int) -> dict:
        try:
            return cls._normalize_slide(
                JSONExtractor.extract_dict(content, fallback={})
            )
        except Exception as error:
            logger.warning(
                "M4 lecture: slide %s returned invalid JSON: %s",
                page_num,
                error,
            )
            return {}

    @staticmethod
    def _is_complete_slide(slide: dict) -> bool:
        if not isinstance(slide, dict) or slide.get("isPlaceholder"):
            return False
        title = str(slide.get("title", "")).strip()
        points = slide.get("bulletPoints", [])
        if not isinstance(points, list):
            points = [points]
        meaningful_points = [
            str(point).strip()
            for point in points
            if str(point).strip() not in {"", "内容生成中...", "内容生成中…"}
        ]
        has_other_content = bool(
            str(slide.get("subtitle", "")).strip()
            or str(slide.get("formula", "")).strip()
            or slide.get("interaction")
        )
        return bool(title) and (bool(meaningful_points) or has_other_content)

    @staticmethod
    def _slide_text(slide: dict) -> str:
        parts = [str(slide.get("title", ""))]
        parts.extend(str(item) for item in slide.get("bulletPoints", []) if item)
        return re.sub(r"[^\w\u4e00-\u9fff]+", "", "".join(parts)).lower()

    @classmethod
    def _find_duplicate_page(cls, slide: dict, previous_slides: list[dict]):
        current = cls._slide_text(slide)
        if len(current) < 12:
            return None
        current_bigrams = {current[index:index + 2] for index in range(len(current) - 1)}
        for previous in previous_slides:
            other = cls._slide_text(previous)
            if len(other) < 12:
                continue
            other_bigrams = {other[index:index + 2] for index in range(len(other) - 1)}
            union = current_bigrams | other_bigrams
            similarity = len(current_bigrams & other_bigrams) / len(union) if union else 0
            if similarity >= 0.26:
                return previous.get("pageNum")
        return None

    @staticmethod
    def _summarize_slides(slides: list[dict]) -> str:
        if not slides:
            return ""
        summaries = []
        for slide in slides[-6:]:
            points = slide.get("bulletPoints", [])
            summaries.append(
                f"第{slide.get('pageNum', '?')}页《{slide.get('title', '')}》："
                + "；".join(str(point) for point in points[:3])
            )
        return "\n".join(summaries)

    def _sse_event(self, event_type: str, data: dict) -> str:
        payload = {"type": event_type, **data}
        return f"event: {event_type}\ndata: {json.dumps(payload, ensure_ascii=False)}\n\n"
