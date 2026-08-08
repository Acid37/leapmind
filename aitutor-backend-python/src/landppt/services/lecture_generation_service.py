"""
M4 讲课生成 —— SSE 流式服务
参考 M5 LessonPrepService 的 StreamingResponse 模式

Java TeachingController → POST /api/ai/stream-generate-teaching → 本服务
SSE 事件: outline / slide / done
"""
import json
import logging
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
        """
        流式生成讲课内容，逐页 SSE 推送。
        两阶段：1) 大纲 → outline 事件  2) 逐页 → slide 事件 → done 事件
        """
        provider = get_ai_provider()
        model = provider.model

        # ── Stage 1: 生成大纲 ──
        logger.info("M4 lecture: generating outline...")
        outline = await self._generate_outline(provider, source_text, grade, weak_points)

        total_pages = outline.get("totalPages", 5)
        title = outline.get("title", "AI 即时讲课")

        # 推送 outline 事件
        yield self._sse_event("outline", {
            "title": title,
            "content": outline.get("outline", []),
            "totalPages": total_pages,
            "outline": outline.get("outline", []),
        })

        # ── Stage 2: 逐页生成 slide ──
        pages = outline.get("outline", [])
        slides = []

        for i, page_info in enumerate(pages):
            page_num = page_info.get("page", i + 1)
            slide_type = page_info.get("type", "content")
            page_title = page_info.get("title", "")

            logger.info("M4 lecture: generating slide %s/%s", page_num, total_pages)

            slide = await self._generate_slide(
                provider, title, page_num, total_pages,
                slide_type, source_text, grade, weak_points,
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

        # ── 推送 done 事件 ──
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
        slide_type, source_text, grade, weak_points,
    ):
        prompt = build_slide_prompt(
            title, page_num, total_pages,
            slide_type, source_text, grade, weak_points,
        )
        messages = [
            build_teaching_system_message(),
            AIMessage(role=MessageRole.USER, content=prompt),
        ]
        response = await provider.chat_completion(messages, temperature=0.7)
        slide = JSONExtractor.extract_dict(response.content, fallback={
            "pageNum": page_num,
            "type": slide_type,
            "title": f"第{page_num}页",
            "bulletPoints": ["内容生成中..."],
            "highlightPoints": [],
        })
        # LLM 可能返回 body，统一转为 bulletPoints
        if "body" in slide and "bulletPoints" not in slide:
            slide["bulletPoints"] = slide.pop("body")
        return slide

    def _sse_event(self, event_type: str, data: dict) -> str:
        return f"event: {event_type}\ndata: {json.dumps(data, ensure_ascii=False)}\n\n"
