#!/usr/bin/env python3
"""
M5 AI备课 — Stage1 + Stage2 联桥格式验证测试

Mock AI provider 返回预设的 syllabus / slides JSON，
调用 generate_and_return() 验证返回格式是否符合 M5 模块文档定义。

运行方式:
    cd aitutor-backend-python
    python test_stage12_format.py
"""
import sys
import os
import json
import asyncio

# Add src to Python path
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "src"))

from landppt.ai.base import AIProvider, AIResponse, AIMessage, MessageRole
from landppt.services.lesson_prep_service import LessonPrepService, PrepContext


# ═══════════════════════════════════════════════════════════════
# Mock data — 严格符合 LESSON_PLAN_SCHEMA / PPT_SLIDE_SCHEMA
# ═══════════════════════════════════════════════════════════════

MOCK_SYLLABUS = {
    "title": "勾股定理",
    "total_hours": 1,
    "knowledge_structure": "从直角三角形性质引入，推导勾股定理公式",
    "sections": [
        {
            "hour_index": 1,
            "title": "勾股定理的认识与应用",
            "core_content": "直角三角形两直角边的平方和等于斜边的平方",
            "teaching_goals": [
                "理解勾股定理的内容",
                "掌握勾股定理的证明方法",
                "能运用勾股定理解题",
            ],
            "key_points": [
                "勾股定理公式 a²+b²=c²",
                "区分斜边和直角边",
            ],
            "difficult_points": [
                "在实际图形中识别斜边",
            ],
            "teaching_process": [
                {
                    "step": "课堂导入",
                    "duration": "5min",
                    "teacher_activity": "展示直角三角形图片引入课题",
                    "student_activity": "观察图形并思考边长关系",
                    "design_intent": "激发学习兴趣和探究欲望",
                },
                {
                    "step": "新知讲授",
                    "duration": "20min",
                    "teacher_activity": "讲解勾股定理公式及推导过程",
                    "student_activity": "记录笔记并跟随教师推导",
                    "design_intent": "知识传授与公式理解",
                },
                {
                    "step": "课堂练习",
                    "duration": "10min",
                    "teacher_activity": "布置典型练习题并巡回指导",
                    "student_activity": "独立完成计算并汇报结果",
                    "design_intent": "巩固公式应用能力",
                },
                {
                    "step": "课堂小结",
                    "duration": "5min",
                    "teacher_activity": "总结本节课重点内容",
                    "student_activity": "回顾整理笔记要点",
                    "design_intent": "归纳提升形成知识体系",
                },
            ],
            "homework": {
                "basic": ["课本第50页第1-3题"],
                "advanced": ["已知直角三角形两边长为6和8，求斜边长度"],
                "optional": ["探究勾股定理的面积证法"],
            },
        }
    ],
}

MOCK_SLIDES = [
    {
        "page_num": 1,
        "type": "cover",
        "title": "勾股定理",
        "bullet_points": ["初中数学 · 八年级"],
        "image_suggestion": "直角三角形示意图",
        "formula": "",
        "highlight_points": [],
    },
    {
        "page_num": 2,
        "type": "content",
        "title": "勾股定理公式",
        "bullet_points": [
            "直角三角形两直角边的平方和等于斜边的平方",
            "公式：a² + b² = c²",
        ],
        "image_suggestion": "标注abc的直角三角形",
        "formula": "a^2 + b^2 = c^2",
        "highlight_points": ["斜边", "直角边"],
    },
    {
        "page_num": 3,
        "type": "interactive",
        "title": "课堂互动",
        "bullet_points": ["思考：如果直角边为3和4，斜边是多少？"],
        "image_suggestion": "",
        "formula": "",
        "highlight_points": [],
        "interaction": {
            "type": "think_question",
            "question": "直角三角形两直角边分别为3和4，求斜边长度",
            "answer": "5",
        },
    },
    {
        "page_num": 4,
        "type": "summary",
        "title": "本节小结",
        "bullet_points": [
            "勾股定理公式 a²+b²=c²",
            "斜边是直角对边，是最长边",
        ],
        "image_suggestion": "",
        "formula": "",
        "highlight_points": [],
    },
]

MOCK_SYLLABUS_JSON = json.dumps(MOCK_SYLLABUS, ensure_ascii=False)
MOCK_SLIDES_JSON = json.dumps(MOCK_SLIDES, ensure_ascii=False)


# ═══════════════════════════════════════════════════════════════
# Mock AI Provider
# ═══════════════════════════════════════════════════════════════

class MockAIProvider(AIProvider):
    """Mock provider — stream_chat_completion 返回 syllabus，chat_completion 返回 slides。"""

    def __init__(self):
        super().__init__({"model": "mock-model"})
        self.call_count = 0

    async def chat_completion(self, messages, **kwargs):
        """Stage 2 调用 — 返回 slides JSON 数组。"""
        self.call_count += 1
        return AIResponse(
            content=MOCK_SLIDES_JSON,
            model="mock-model",
            usage={"prompt_tokens": 100, "completion_tokens": 200, "total_tokens": 300},
            finish_reason="stop",
        )

    async def text_completion(self, prompt, **kwargs):
        return AIResponse(
            content=MOCK_SYLLABUS_JSON,
            model="mock-model",
            usage={"prompt_tokens": 50, "completion_tokens": 100, "total_tokens": 150},
            finish_reason="stop",
        )

    async def stream_chat_completion(self, messages, **kwargs):
        """Stage 1 调用 — yield syllabus JSON。"""
        yield MOCK_SYLLABUS_JSON


# ═══════════════════════════════════════════════════════════════
# Mock _save_to_db — 避免数据库操作
# ═══════════════════════════════════════════════════════════════

async def mock_save_to_db(self, ctx):
    print("  [mock] _save_to_db 被调用，返回 prep_id=999（未真正写库）")
    return 999


# ═══════════════════════════════════════════════════════════════
# 格式验证函数
# ═══════════════════════════════════════════════════════════════

def validate_syllabus(syllabus: dict) -> list[str]:
    """验证 syllabus 是否符合 LESSON_PLAN_SCHEMA 必需字段。"""
    errors = []
    if "title" not in syllabus:
        errors.append("缺少 title")
    if "total_hours" not in syllabus:
        errors.append("缺少 total_hours")
    sections = syllabus.get("sections", [])
    if not sections:
        errors.append("sections 为空")
        return errors

    required_sec_fields = [
        "hour_index", "title", "core_content",
        "teaching_goals", "key_points", "difficult_points", "teaching_process",
    ]
    required_step_fields = [
        "step", "duration", "teacher_activity", "student_activity", "design_intent",
    ]

    for i, sec in enumerate(sections):
        for f in required_sec_fields:
            if f not in sec:
                errors.append(f"section[{i}] 缺少 {f}")
        tp = sec.get("teaching_process", [])
        if len(tp) < 3:
            errors.append(f"section[{i}] teaching_process 少于3个环节（{len(tp)}）")
        for j, step in enumerate(tp):
            for f in required_step_fields:
                if f not in step:
                    errors.append(f"section[{i}].teaching_process[{j}] 缺少 {f}")
    return errors


def validate_slides(slides: list) -> list[str]:
    """验证 slides 是否符合 PPT_SLIDE_SCHEMA 必需字段。"""
    errors = []
    if not slides:
        errors.append("slides 为空")
        return errors

    valid_types = {"cover", "content", "interactive", "summary", "homework"}
    for i, s in enumerate(slides):
        for f in ["page_num", "type", "title", "bullet_points"]:
            if f not in s:
                errors.append(f"slide[{i}] 缺少 {f}")
        if s.get("type") not in valid_types:
            errors.append(f"slide[{i}] type 不合法: {s.get('type')}")
        if not s.get("bullet_points"):
            errors.append(f"slide[{i}] bullet_points 为空")
    return errors


# ═══════════════════════════════════════════════════════════════
# 主测试流程
# ═══════════════════════════════════════════════════════════════

async def main():
    print("=" * 60)
    print("M5 AI备课 — Stage1 + Stage2 联桥格式验证测试")
    print("=" * 60)

    # 1. 构造 mock service
    service = LessonPrepService()
    service._provider = MockAIProvider()
    LessonPrepService._save_to_db = mock_save_to_db

    ctx = PrepContext(
        user_id=1001,
        title="勾股定理",
        subject="math",
        grade="grade_8",
        knowledge_point_ids=[20, 21],
        teaching_goals=["理解勾股定理", "掌握证明方法", "能运用解题"],
        total_hours=1,
        style="standard",
    )

    # 2. 调用 generate_and_return
    print("\n[1] 调用 generate_and_return()...")
    result = await service.generate_and_return(ctx)

    if "error" in result:
        print(f"  ❌ 生成失败: {result['error']}")
        return

    print(f"  ✅ 生成成功")
    print(f"  返回字段: {list(result.keys())}")

    # 3. 验证返回字段
    expected_keys = {"prep_id", "total_pages", "syllabus", "slides"}
    actual_keys = set(result.keys())
    missing = expected_keys - actual_keys
    extra = actual_keys - expected_keys
    if missing:
        print(f"  ⚠️  缺少字段: {missing}")
    if extra:
        print(f"  ℹ️  额外字段: {extra}")
    if not missing and not extra:
        print(f"  ✅ 返回字段与文档一致: {sorted(expected_keys)}")

    # 4. 验证 syllabus
    print("\n[2] 验证 syllabus 格式...")
    syllabus = result.get("syllabus", {})
    print(f"  title: {syllabus.get('title')}")
    print(f"  total_hours: {syllabus.get('total_hours')}")
    print(f"  sections 数量: {len(syllabus.get('sections', []))}")

    errors = validate_syllabus(syllabus)
    if errors:
        print(f"  ❌ 格式问题:")
        for e in errors:
            print(f"     - {e}")
    else:
        print(f"  ✅ 符合 LESSON_PLAN_SCHEMA")

    if syllabus.get("sections"):
        sec = syllabus["sections"][0]
        print(f"\n  ── section[0] 详情 ──")
        print(f"     hour_index:      {sec.get('hour_index')}")
        print(f"     title:           {sec.get('title')}")
        print(f"     core_content:    {sec.get('core_content', '')[:40]}...")
        print(f"     teaching_goals:  {sec.get('teaching_goals')}")
        print(f"     key_points:      {sec.get('key_points')}")
        print(f"     difficult_points:{sec.get('difficult_points')}")
        print(f"     teaching_process:{len(sec.get('teaching_process', []))} 个环节")
        for j, step in enumerate(sec.get("teaching_process", [])):
            print(f"       [{j}] {step['step']} ({step['duration']}) - {step['teacher_activity'][:20]}...")
        print(f"     homework:        {sec.get('homework')}")

    # 5. 验证 slides
    print(f"\n[3] 验证 slides 格式...")
    slides = result.get("slides", [])
    print(f"  slides 数量: {len(slides)}")
    print(f"  total_pages: {result.get('total_pages')}")

    errors = validate_slides(slides)
    if errors:
        print(f"  ❌ 格式问题:")
        for e in errors:
            print(f"     - {e}")
    else:
        print(f"  ✅ 符合 PPT_SLIDE_SCHEMA")

    for i, s in enumerate(slides):
        print(f"\n  ── slide[{i}] ──")
        print(f"     page_num:       {s.get('page_num')}")
        print(f"     type:           {s.get('type')}")
        print(f"     title:          {s.get('title')}")
        print(f"     bullet_points:  {s.get('bullet_points')}")
        print(f"     formula:        {s.get('formula') or '(无)'}")
        print(f"     image_suggest:  {s.get('image_suggestion') or '(无)'}")
        print(f"     highlight:      {s.get('highlight_points') or '(无)'}")
        if s.get("interaction"):
            print(f"     interaction:    {s['interaction']}")

    # 6. 与 M5 文档对照
    print("\n" + "=" * 60)
    print("[4] 与 M5 模块文档对照检查")
    print("=" * 60)

    checks = [
        ("返回值含 prep_id", "prep_id" in result),
        ("返回值含 total_pages", "total_pages" in result),
        ("返回值含 syllabus", "syllabus" in result),
        ("返回值含 slides", "slides" in result),
        ("syllabus.title 存在", "title" in syllabus),
        ("syllabus.total_hours 存在", "total_hours" in syllabus),
        ("syllabus.sections 非空", bool(syllabus.get("sections"))),
        ("section 含 teaching_goals", all("teaching_goals" in s for s in syllabus.get("sections", []))),
        ("section 含 key_points", all("key_points" in s for s in syllabus.get("sections", []))),
        ("section 含 difficult_points", all("difficult_points" in s for s in syllabus.get("sections", []))),
        ("section 含 teaching_process", all("teaching_process" in s for s in syllabus.get("sections", []))),
        ("section 含 homework", all("homework" in s for s in syllabus.get("sections", []))),
        ("slides 非空", bool(slides)),
        ("slide 含 page_num", all("page_num" in s for s in slides)),
        ("slide 含 type", all("type" in s for s in slides)),
        ("slide 含 title", all("title" in s for s in slides)),
        ("slide 含 bullet_points", all("bullet_points" in s for s in slides)),
        ("total_pages == len(slides)", result.get("total_pages") == len(slides)),
    ]

    all_pass = True
    for desc, passed in checks:
        print(f"  {'✅' if passed else '❌'} {desc}")
        if not passed:
            all_pass = False

    print()
    if all_pass:
        print("🎉 全部检查通过！Stage1 + Stage2 返回格式符合 M5 模块文档。")
    else:
        print("⚠️  部分检查未通过，请查看上方详情。")
    print("=" * 60)


if __name__ == "__main__":
    asyncio.run(main())
