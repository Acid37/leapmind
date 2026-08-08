"""
LLM Judge 低分自动修复流程演示。

Mock AI provider 模拟完整流程：
  1. 第一次评判 → 低分（< 60）+ issues + suggestions
  2. 修复调用 → 返回修复后的内容
  3. 第二次评判 → 高分（≥ 60）

用途：演示 LLM Judge 的修复逻辑，包含 mock 数据和 mock provider。
"""
import sys
import json
import asyncio
from types import SimpleNamespace

sys.path.insert(0, r"d:\leapmind1\leapmind\aitutor-backend-python\src")


# ════════════════════════════════════════════════════════════
#  Mock Data
# ════════════════════════════════════════════════════════════

# 原始低质量大纲（教学目标空洞、缺练习环节、作业不全）
LOW_QUALITY_SYLLABUS = {
    "sections": [
        {
            "title": "勾股定理",
            "core_content": "掌握勾股定理",
            "teaching_goals": ["掌握知识"],
            "teaching_process": [
                {"name": "导入", "duration": 5, "teacher_activity": "讲", "student_activity": "听", "design_intent": "引入"},
                {"name": "讲授", "duration": 30, "teacher_activity": "讲", "student_activity": "听", "design_intent": "教"},
                {"name": "小结", "duration": 5, "teacher_activity": "总结", "student_activity": "听", "design_intent": "结"},
            ],
            "homework": {"basic": ["做题"], "advanced": [], "expansion": []},
            "key_points": ["勾股定理"],
            "difficulties": ["证明"],
        }
    ]
}

# 原始低质量 PPT（第2页信息过载8个要点、缺互动页、无配图）
LOW_QUALITY_SLIDES = [
    {"page_num": 1, "type": "cover", "title": "勾股定理",
     "bullet_points": ["封面"], "image_suggestion": "", "formula": "",
     "highlight_points": [], "interaction": None, "is_fallback": False},
    {"page_num": 2, "type": "content", "title": "内容",
     "bullet_points": ["要点1", "要点2", "要点3", "要点4", "要点5", "要点6", "要点7", "要点8"],
     "image_suggestion": "", "formula": "", "highlight_points": [],
     "interaction": None, "is_fallback": False},
    {"page_num": 3, "type": "content", "title": "内容2",
     "bullet_points": ["要点1"], "image_suggestion": "", "formula": "",
     "highlight_points": [], "interaction": None, "is_fallback": False},
    {"page_num": 4, "type": "summary", "title": "总结",
     "bullet_points": ["总结"], "image_suggestion": "", "formula": "",
     "highlight_points": [], "interaction": None, "is_fallback": False},
]

# 低分评判结果 - 大纲（score=40）
LOW_SCORE_SYLLABUS_JUDGE = {
    "score": 40,
    "dimensions": {
        "goal_relevance": {"score": 8, "comment": "目标太空洞，'掌握知识'不可衡量"},
        "content_accuracy": {"score": 15, "comment": "知识点基本正确"},
        "process_logic": {"score": 7, "comment": "缺少课堂练习环节"},
        "activity_effectiveness": {"score": 5, "comment": "师生活动只有讲和听"},
        "homework_layering": {"score": 5, "comment": "只有基础作业，缺提高和拓展"},
    },
    "issues": [
        "教学目标'掌握知识'过于空洞，不可衡量",
        "教学过程缺少课堂练习环节",
        "师生活动单一，只有教师讲学生听",
        "作业只有基础题，缺少提高和拓展",
    ],
    "suggestions": [
        "将教学目标改为'能运用勾股定理计算直角三角形边长'",
        "在讲授和小结之间增加15分钟课堂练习",
        "增加学生讨论和动手操作活动",
        "增加提高题和拓展题",
    ],
    "needs_review": True,
}

# 低分评判结果 - PPT（score=45）
LOW_SCORE_SLIDES_JUDGE = {
    "score": 45,
    "dimensions": {
        "content_relevance": {"score": 15, "comment": "内容基本相关"},
        "information_density": {"score": 7, "comment": "第2页8个要点过载，第3页只有1个要点"},
        "structure_completeness": {"score": 10, "comment": "缺少互动练习页"},
        "visual_clarity": {"score": 5, "comment": "无配图建议和公式"},
        "teaching_usability": {"score": 8, "comment": "缺少关键步骤"},
    },
    "issues": [
        "第2页信息过载，8个要点超出7±2原则",
        "第3页信息过少，只有1个要点",
        "缺少互动练习页",
        "无配图建议和公式",
    ],
    "suggestions": [
        "第2页拆分为两页，每页4个要点",
        "第3页增加更多要点或合并到其他页",
        "在总结页前增加1页课堂练习",
        "添加配图建议和公式",
    ],
    "needs_review": True,
}

# 修复后的大纲（目标明确、增加了练习环节、作业三层完整）
REPAIRED_SYLLABUS = {
    "sections": [
        {
            "title": "勾股定理",
            "core_content": "掌握勾股定理及其应用",
            "teaching_goals": ["能运用勾股定理计算直角三角形边长", "理解勾股定理的证明过程"],
            "teaching_process": [
                {"name": "导入", "duration": 5, "teacher_activity": "展示生活实例", "student_activity": "思考", "design_intent": "引入主题"},
                {"name": "讲授", "duration": 20, "teacher_activity": "讲解定理和证明", "student_activity": "记录和思考", "design_intent": "知识传授"},
                {"name": "课堂练习", "duration": 15, "teacher_activity": "指导练习", "student_activity": "动手计算", "design_intent": "巩固知识"},
                {"name": "小结", "duration": 5, "teacher_activity": "总结要点", "student_activity": "回顾", "design_intent": "梳理知识"},
            ],
            "homework": {
                "basic": ["课本P10第1-3题"],
                "advanced": ["已知两边求第三边的应用题"],
                "expansion": ["探究勾股定理的多种证明方法"],
            },
            "key_points": ["勾股定理公式 a²+b²=c²", "直角三角形边长计算"],
            "difficulties": ["勾股定理的证明过程"],
        }
    ]
}

# 修复后的 PPT（拆分了第2页、增加了互动练习页、添加了配图和公式）
REPAIRED_SLIDES = [
    {"page_num": 1, "type": "cover", "title": "勾股定理",
     "bullet_points": ["探索直角三角形的边长关系"],
     "image_suggestion": "直角三角形示意图", "formula": "",
     "highlight_points": ["勾股定理"], "interaction": None, "is_fallback": False},
    {"page_num": 2, "type": "content", "title": "定理内容",
     "bullet_points": ["勾股定理定义", "公式 a²+b²=c²", "适用条件：直角三角形", "c为斜边"],
     "image_suggestion": "公式推导图", "formula": "a² + b² = c²",
     "highlight_points": ["勾股定理", "直角三角形"], "interaction": None, "is_fallback": False},
    {"page_num": 3, "type": "content", "title": "定理证明",
     "bullet_points": ["面积法证明", "步骤一：构造正方形", "步骤二：计算面积", "步骤三：得出结论"],
     "image_suggestion": "面积法证明图", "formula": "",
     "highlight_points": ["面积法"], "interaction": None, "is_fallback": False},
    {"page_num": 4, "type": "interactive", "title": "课堂练习",
     "bullet_points": ["练习题1：求斜边", "练习题2：求直角边", "思考：生活中的应用"],
     "image_suggestion": "", "formula": "",
     "highlight_points": ["练习"],
     "interaction": {"type": "choice_question", "question": "直角三角形两边长为3和4，斜边长？",
                     "options": ["5", "6", "7", "8"], "answer": "A"},
     "is_fallback": False},
    {"page_num": 5, "type": "summary", "title": "本节总结",
     "bullet_points": ["勾股定理公式", "适用条件", "应用场景", "注意事项"],
     "image_suggestion": "", "formula": "a² + b² = c²",
     "highlight_points": ["总结"], "interaction": None, "is_fallback": False},
]

# 高分评判结果 - 大纲（score=82）
HIGH_SCORE_SYLLABUS_JUDGE = {
    "score": 82,
    "dimensions": {
        "goal_relevance": {"score": 18, "comment": "目标明确可衡量"},
        "content_accuracy": {"score": 17, "comment": "知识点准确"},
        "process_logic": {"score": 16, "comment": "环节完整有练习"},
        "activity_effectiveness": {"score": 16, "comment": "师生活动丰富"},
        "homework_layering": {"score": 15, "comment": "三层作业完整"},
    },
    "issues": [],
    "suggestions": [],
    "needs_review": False,
}

# 高分评判结果 - PPT（score=80）
HIGH_SCORE_SLIDES_JUDGE = {
    "score": 80,
    "dimensions": {
        "content_relevance": {"score": 18, "comment": "内容紧扣主题"},
        "information_density": {"score": 16, "comment": "信息量适中"},
        "structure_completeness": {"score": 16, "comment": "流程完整有练习"},
        "visual_clarity": {"score": 15, "comment": "配图和公式合理"},
        "teaching_usability": {"score": 15, "comment": "可直接使用"},
    },
    "issues": [],
    "suggestions": [],
    "needs_review": False,
}


# ════════════════════════════════════════════════════════════
#  Mock AI Provider
# ════════════════════════════════════════════════════════════

class MockResponse:
    """模拟 AI 响应。"""
    def __init__(self, content):
        self.content = content


class MockAIProvider:
    """根据 messages 内容返回不同 mock 响应。

    调用序列：
      评判大纲 #1 (低分) → 评判PPT #1 (低分)
      → 修复大纲 → 修复PPT
      → 评判大纲 #2 (高分) → 评判PPT #2 (高分)
    """

    def __init__(self, repair_should_fail=False):
        self.syllabus_judge_count = 0
        self.slides_judge_count = 0
        self.repair_syllabus_count = 0
        self.repair_slides_count = 0
        self.call_log = []
        self.repair_should_fail = repair_should_fail

    async def chat_completion(self, messages, json_mode=False, temperature=0.5):
        system_msg = messages[0].content if messages else ""

        # 注意：PPT 评判的 system 中也包含"教学大纲摘要"，
        # 所以必须先检查 PPT，再检查大纲，否则会误匹配

        # 评判 PPT（含"PPT"或"幻灯片"，不含"修复"）
        if "修复" not in system_msg and ("PPT" in system_msg or "幻灯片" in system_msg):
            self.slides_judge_count += 1
            self.call_log.append(f"judge_slides #{self.slides_judge_count}")
            if self.slides_judge_count == 1:
                return MockResponse(json.dumps(LOW_SCORE_SLIDES_JUDGE, ensure_ascii=False))
            else:
                return MockResponse(json.dumps(HIGH_SCORE_SLIDES_JUDGE, ensure_ascii=False))

        # 评判大纲（含"教学大纲"，不含"修复"和"PPT"）
        if "修复" not in system_msg and "教学大纲" in system_msg:
            self.syllabus_judge_count += 1
            self.call_log.append(f"judge_syllabus #{self.syllabus_judge_count}")
            if self.syllabus_judge_count == 1:
                return MockResponse(json.dumps(LOW_SCORE_SYLLABUS_JUDGE, ensure_ascii=False))
            else:
                return MockResponse(json.dumps(HIGH_SCORE_SYLLABUS_JUDGE, ensure_ascii=False))

        # 修复 PPT（含"修复" + "PPT"）
        if "修复" in system_msg and "PPT" in system_msg:
            self.repair_slides_count += 1
            self.call_log.append(f"repair_slides #{self.repair_slides_count}")
            if self.repair_should_fail:
                return MockResponse("invalid json")
            return MockResponse(json.dumps(REPAIRED_SLIDES, ensure_ascii=False))

        # 修复大纲（含"修复" + "教学大纲"）
        if "修复" in system_msg and "教学大纲" in system_msg:
            self.repair_syllabus_count += 1
            self.call_log.append(f"repair_syllabus #{self.repair_syllabus_count}")
            if self.repair_should_fail:
                return MockResponse("invalid json")
            return MockResponse(json.dumps(REPAIRED_SYLLABUS, ensure_ascii=False))

        return MockResponse("{}")


# ════════════════════════════════════════════════════════════
#  Repair Flow Demo
# ════════════════════════════════════════════════════════════

async def demo_repair_flow():
    """演示低分自动修复完整流程：judge → 低分 → repair → rejudge。"""
    mock = MockAIProvider()
    from landppt.validators.llm_judge import LLMJudge

    judge = LLMJudge(provider=mock)

    # ─── 第1步：第一次评判（低分）───
    print("[步骤1] LLM Judge 评判（第一次）...")
    result1 = await judge.judge_all(
        syllabus=LOW_QUALITY_SYLLABUS,
        slides=LOW_QUALITY_SLIDES,
        subject="math",
        grade="grade_9",
        knowledge_point_names=["勾股定理"],
    )
    score1 = result1["score"]
    print(f"  总分: {score1}, needs_review: {result1['needs_review']}")
    print(f"  大纲问题: {len(result1['syllabus']['issues'])}项, PPT问题: {len(result1['slides']['issues'])}项")

    # ─── 第2步：score < 60，触发修复 ───
    if score1 < 60:
        print(f"\n[步骤2] 评分 {score1} < 60，触发自动修复...")
        repaired_syllabus = await judge.repair_syllabus(
            syllabus=LOW_QUALITY_SYLLABUS,
            issues=result1["syllabus"]["issues"],
            suggestions=result1["syllabus"]["suggestions"],
            subject="math",
            grade="grade_9",
        )
        repaired_slides = await judge.repair_slides(
            slides=LOW_QUALITY_SLIDES,
            issues=result1["slides"]["issues"],
            suggestions=result1["slides"]["suggestions"],
            subject="math",
            grade="grade_9",
        )

        orig_syll = LOW_QUALITY_SYLLABUS["sections"][0]
        new_syll = repaired_syllabus["sections"][0]
        print(f"  大纲修复: {len(orig_syll['teaching_process'])}→{len(new_syll['teaching_process'])}环节, "
              f"作业{sum(len(v) for v in orig_syll['homework'].values())}→{sum(len(v) for v in new_syll['homework'].values())}题")
        print(f"  PPT修复: {len(LOW_QUALITY_SLIDES)}→{len(repaired_slides)}页, "
              f"类型={[s['type'] for s in repaired_slides]}")

        # ─── 第3步：修复后重新评判 ───
        print(f"\n[步骤3] 修复后重新评判...")
        result2 = await judge.judge_all(
            syllabus=repaired_syllabus,
            slides=repaired_slides,
            subject="math",
            grade="grade_9",
            knowledge_point_names=["勾股定理"],
        )
        score2 = result2["score"]
        print(f"  总分: {score2}, needs_review: {result2['needs_review']}")
        print(f"  分数提升: {score1} → {score2} (+{score2 - score1:.0f})")

    print(f"\n调用序列: {mock.call_log}")


async def demo_service_repair():
    """演示 service._repair_low_quality 方法。"""
    from landppt.validators.llm_judge import LLMJudge
    from landppt.services.lesson_prep_service import LessonPrepService

    mock = MockAIProvider()
    judge = LLMJudge(provider=mock)
    service = LessonPrepService()
    service._llm_judge = judge

    ctx = SimpleNamespace(
        syllabus=LOW_QUALITY_SYLLABUS,
        slides=LOW_QUALITY_SLIDES,
        subject="math",
        grade="grade_9",
        knowledge_point_names=["勾股定理"],
    )

    judge_result = {
        "score": 42.5,
        "needs_review": True,
        "syllabus": {
            "score": 40,
            "issues": LOW_SCORE_SYLLABUS_JUDGE["issues"],
            "suggestions": LOW_SCORE_SYLLABUS_JUDGE["suggestions"],
            "error": None,
        },
        "slides": {
            "score": 45,
            "issues": LOW_SCORE_SLIDES_JUDGE["issues"],
            "suggestions": LOW_SCORE_SLIDES_JUDGE["suggestions"],
            "error": None,
        },
        "error": None,
    }

    repaired = await service._repair_low_quality(ctx, judge_result)
    if repaired:
        print(f"  修复成功: 大纲{len(repaired['syllabus']['sections'][0]['teaching_process'])}环节, "
              f"PPT{len(repaired['slides'])}页")
    else:
        print(f"  修复失败: 保留原始内容")


async def demo_repair_failure():
    """演示修复失败时的降级处理。"""
    from landppt.validators.llm_judge import LLMJudge
    from landppt.services.lesson_prep_service import LessonPrepService

    mock = MockAIProvider(repair_should_fail=True)
    judge = LLMJudge(provider=mock)
    service = LessonPrepService()
    service._llm_judge = judge

    ctx = SimpleNamespace(
        syllabus=LOW_QUALITY_SYLLABUS,
        slides=LOW_QUALITY_SLIDES,
        subject="math",
        grade="grade_9",
        knowledge_point_names=["勾股定理"],
    )

    judge_result = {
        "score": 42.5, "needs_review": True,
        "syllabus": {"score": 40, "issues": LOW_SCORE_SYLLABUS_JUDGE["issues"],
                     "suggestions": LOW_SCORE_SYLLABUS_JUDGE["suggestions"], "error": None},
        "slides": {"score": 45, "issues": LOW_SCORE_SLIDES_JUDGE["issues"],
                   "suggestions": LOW_SCORE_SLIDES_JUDGE["suggestions"], "error": None},
        "error": None,
    }

    repaired = await service._repair_low_quality(ctx, judge_result)
    print(f"  修复失败降级: returned={repaired}（保留原始内容）")


# ════════════════════════════════════════════════════════════
#  Main
# ════════════════════════════════════════════════════════════

async def main():
    print()
    print("╔" + "═" * 58 + "╗")
    print("║  LLM Judge 低分自动修复流程 - 演示                        ║")
    print("╚" + "═" * 58 + "╝")
    print()

    print("=" * 60)
    print("演示1：完整修复流程 judge → repair → rejudge")
    print("=" * 60)
    await demo_repair_flow()

    print()
    print("=" * 60)
    print("演示2：service._repair_low_quality")
    print("=" * 60)
    await demo_service_repair()

    print()
    print("=" * 60)
    print("演示3：修复失败降级")
    print("=" * 60)
    await demo_repair_failure()


if __name__ == "__main__":
    asyncio.run(main())
