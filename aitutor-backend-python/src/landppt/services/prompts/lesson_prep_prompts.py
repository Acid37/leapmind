"""
Lesson preparation prompt templates for M5 AI备课模块.
Three stages: Syllabus → Slides → Narration.
All prompts are module-level functions, no class hierarchy per spec design.
"""

from typing import Optional

# ─── Schema descriptions injected into prompts for structured output ───

SYLLABUS_SCHEMA_DESC = """
{
  "title": "备课标题",
  "total_hours": 课时数,
  "sections": [
    {
      "hour_index": 1,
      "title": "第X课时：标题",
      "core_content": "本课时核心内容概述",
      "teaching_goals": ["目标1", "目标2"],
      "key_points": ["重点1", "重点2"],
      "difficult_points": ["难点1", "难点2"],
      "teaching_process": [
        {
          "step": "课堂导入",
          "duration": "5min",
          "teacher_activity": "教师活动描述",
          "student_activity": "学生活动描述",
          "design_intent": "设计意图"
        }
      ],
      "homework": {
        "basic": ["基础题1", "基础题2"],
        "advanced": ["提高题1"],
        "optional": ["拓展题1"]
      }
    }
  ],
  "knowledge_structure": "知识点之间的逻辑关系说明"
}
"""

SLIDE_SCHEMA_DESC = """
{
  "page_num": 页码整数,
  "type": "cover | content | interactive | summary | homework",
  "title": "幻灯片标题",
  "bullet_points": ["要点1", "要点2", ...],
  "image_suggestion": "配图描述或空字符串",
  "formula": "LaTeX公式或空字符串",
  "highlight_points": ["需要高亮的关键词"],
  "interaction": {
    "type": "choice_question | think_question | null",
    "question": "问题文本",
    "options": ["A选项", "B选项", "C选项", "D选项"],
    "answer": "正确答案索引（如 A）"
  } | null
}
"""

STYLE_DESCRIPTIONS = {
    "standard": "标准教学风格：内容详实、逻辑清晰、节奏适中",
    "detailed": "详细讲解风格：每个知识点充分展开、大量示例、适合基础薄弱学生",
    "interactive": "互动启发风格：多设问、多讨论、引导学生自主思考",
}


# ─── Stage 1: Syllabus Generation (Streaming) ───

STAGE1_SYSTEM_TEMPLATE = """你是一位经验丰富的{subject}教师，正在为{grade}年级学生备课。你的任务是生成一份结构化的教学大纲。

## 知识点
{knowledge_points}

## 教学目标
{teaching_goals}

## 课时数
{total_hours}课时

## 风格要求
{style_description}

## 学生薄弱点（需重点讲解）
{weak_points}

## 输出格式要求
请输出严格的JSON对象，不要包含markdown代码块标记（```），不要包含任何额外的说明文字。
JSON结构如下：
{syllabus_schema_desc}"""

STAGE1_USER_PROMPT = """请根据以上信息，生成一份完整的教学大纲JSON。每个section必须包含：hour_index, title, core_content, teaching_goals, key_points, difficult_points, teaching_process（逐步骤含环节名、时长、教师活动、学生活动、设计意图）, homework（含基础、提高、拓展题）。"""


def build_stage1_messages(
    subject: str,
    grade: str,
    knowledge_point_ids: list[int],
    teaching_goals: list[str],
    total_hours: int,
    style: str,
    weak_point_ids: list[int],
    user_profile_summary: Optional[str] = None,
) -> list:
    """Build messages for Stage 1: Syllabus generation (streaming)."""
    from ...ai import AIMessage, MessageRole

    kp_str = "\n".join(f"- 知识点ID: {id}" for id in knowledge_point_ids)
    goals_str = "\n".join(f"- {g}" for g in teaching_goals) if teaching_goals else "- 无特定教学目标"
    style_desc = STYLE_DESCRIPTIONS.get(style, STYLE_DESCRIPTIONS["standard"])

    if weak_point_ids:
        weak_str = "\n".join(f"- 薄弱知识点ID: {id}（需放慢节奏、增加示例和互动）" for id in weak_point_ids)
    else:
        weak_str = "- 无特殊薄弱点"

    if user_profile_summary:
        weak_str += f"\n\n## 学生画像\n{user_profile_summary}"

    system = STAGE1_SYSTEM_TEMPLATE.format(
        subject=subject,
        grade=grade,
        knowledge_points=kp_str,
        teaching_goals=goals_str,
        total_hours=total_hours,
        style_description=style_desc,
        weak_points=weak_str,
        syllabus_schema_desc=SYLLABUS_SCHEMA_DESC,
    )

    return [
        AIMessage(role=MessageRole.SYSTEM, content=system),
        AIMessage(role=MessageRole.USER, content=STAGE1_USER_PROMPT),
    ]


# ─── Stage 2: PPT Slide Generation (non-streaming, per-page) ───

STAGE2_SYSTEM_TEMPLATE = """你是PPT教学设计专家，擅长将教案内容转化为视觉化幻灯片结构。

## 设计原则
1. 将教学内容拆分为多张幻灯片，页数由内容复杂度决定（典型3-8页，覆盖完整教学流程）
2. 每页5-7个信息点（7±2原则）
3. 核心概念需要突出显示
4. 如有公式，用LaTeX格式输出
5. 在适当位置插入互动问题（选择题/思考题）

## 幻灯片类型说明
- cover: 封面页（仅标题页使用，一个section最多一张）
- content: 内容页（知识点讲解）
- interactive: 互动页（提问/练习）
- summary: 总结页
- homework: 课后作业页

## 输出格式
请输出一个JSON数组，数组的每个元素是一张幻灯片，严格遵循以下schema，不要包含markdown代码块标记：
{slide_schema_desc}"""

STAGE2_USER_TEMPLATE = """请为以下教学内容生成多张幻灯片JSON数组：

## 教学内容
{section_json}

## 学生特点
{student_profile}

请根据教学内容的完整教学流程，输出一个JSON数组（包含多张幻灯片），
按"导入→讲解→练习→总结→作业"的结构拆分，不要只输出一页。
数组的每个元素严格遵循schema格式。"""


def build_stage2_messages(
    section_json: str,
    user_profile_summary: Optional[str] = None,
) -> list:
    """Build messages for Stage 2: Multi-page PPT slide generation."""
    from ...ai import AIMessage, MessageRole

    system = STAGE2_SYSTEM_TEMPLATE.format(slide_schema_desc=SLIDE_SCHEMA_DESC)

    profile = user_profile_summary or "无特殊信息"
    user = STAGE2_USER_TEMPLATE.format(
        section_json=section_json,
        student_profile=profile,
    )

    return [
        AIMessage(role=MessageRole.SYSTEM, content=system),
        AIMessage(role=MessageRole.USER, content=user),
    ]


# ─── Stage 3: Narration Generation (non-streaming, per-page) ───

STAGE3_SYSTEM_TEMPLATE = """你是一位富有经验的{subject}教师，正在用口语化方式讲解一堂课。

## 语速要求
中文教学语速：200-250字/分钟。根据本页的{target_seconds}秒目标时长，生成合适长度的讲稿。

## 讲稿结构（必须包含以下四个部分）
1. **开场过渡**（与上一页衔接，自然引入本页内容）
2. **核心讲解**（展开幻灯片上的每个要点，用口语化语言解释）
3. **互动/举例**（加入生活化例子或引导性提问）——"大家猜猜看"、"比如说"等
4. **小结过渡**（总结本页要点 + 预告下页内容）

## 语言风格要求
✅ 口语化：像真实老师在讲课——"同学们，我们来看这个公式"、"其实特别简单"
✅ 适度语气词："好"、"那么"、"大家注意"、"明白了没"
✅ 引导性提问："大家猜猜，如果a=3，b=4，c是多少？"
❌ 书面化表达：不要出现"本页"、"如图所示"、"综上所述"
❌ 逐字读PPT：要用自己的话展开解释，不是复述bullet_points

## 输出格式
{{
  "narration_text": "完整的口语化讲稿文本...",
  "estimated_duration_seconds": 整数,
  "key_emphasis": ["术语1", "术语2"],
  "pauses": [
    {{"position_char": 停顿位置（字符索引）, "duration_seconds": 1.5, "reason": "提问后等待学生思考"}}
  ]
}}"""

STAGE3_USER_TEMPLATE = """请为以下PPT页面生成口语化讲稿：

## 幻灯片内容
{slide_json}

## 本页信息
- 标题：{slide_title}
- 类型：{slide_type}
- 目标时长：{target_seconds}秒
- 科目：{subject}

请输出JSON对象，不要包含markdown代码块标记。"""


def build_stage3_messages(
    slide_json: str,
    slide_title: str,
    slide_type: str,
    target_seconds: int,
    subject: str,
) -> list:
    """Build messages for Stage 3: Narration generation."""
    from ...ai import AIMessage, MessageRole

    system = STAGE3_SYSTEM_TEMPLATE.format(
        subject=subject,
        target_seconds=target_seconds,
    )

    user = STAGE3_USER_TEMPLATE.format(
        slide_json=slide_json,
        slide_title=slide_title,
        slide_type=slide_type,
        target_seconds=target_seconds,
        subject=subject,
    )

    return [
        AIMessage(role=MessageRole.SYSTEM, content=system),
        AIMessage(role=MessageRole.USER, content=user),
    ]
