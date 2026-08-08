"""
LLM-as-Judge prompt templates for M5 AI备课质量评判。

Two judge functions:
  - build_judge_syllabus_messages: 评判教学大纲语义质量
  - build_judge_slides_messages:    评判 PPT 幻灯片语义质量

Judges use temperature=0.1 (stable evaluation) + json_mode (structured output).
"""
import json
from typing import Optional


# ─── Syllabus Judge ───

JUDGE_SYLLABUS_SYSTEM = """你是资深教研专家，负责评审教学大纲的语义质量。

## 评审背景
- 科目：{subject}
- 年级：{grade}
- 知识点：{knowledge_points}

## 评分标准（每项 0-20 分，总分 100）
1. 目标适切性(goal_relevance, 20分)：教学目标是否匹配{grade}年级{subject}学科的认知水平、是否可衡量可达成
2. 内容准确性(content_accuracy, 20分)：知识点是否正确无误、重点难点定位是否准确
3. 过程逻辑性(process_logic, 20分)：教学环节顺序是否符合"导入→讲授→练习→小结"的认知规律、环节之间衔接是否自然
4. 活动有效性(activity_effectiveness, 20分)：教师活动和学生活动是否可执行、是否有真实互动而非满堂灌
5. 作业分层性(homework_layering, 20分)：基础/提高/拓展作业是否层次分明、是否与教学目标匹配

## 评分规则
- 18-20分：优秀，完全符合教学规范
- 14-17分：良好，基本可用有小瑕疵
- 10-13分：合格，存在明显问题但可修改
- 0-9分：不合格，存在严重问题

## 输出格式（严格JSON，不要包含markdown代码块标记）
{{
  "score": 整数(0-100, 五项之和),
  "dimensions": {{
    "goal_relevance": {{"score": 整数(0-20), "comment": "简评(10-30字)"}},
    "content_accuracy": {{"score": 整数(0-20), "comment": "简评(10-30字)"}},
    "process_logic": {{"score": 整数(0-20), "comment": "简评(10-30字)"}},
    "activity_effectiveness": {{"score": 整数(0-20), "comment": "简评(10-30字)"}},
    "homework_layering": {{"score": 整数(0-20), "comment": "简评(10-30字)"}}
  }},
  "issues": ["具体问题1(定位到课时/环节)", "具体问题2"],
  "suggestions": ["可执行的改进建议1", "可执行的改进建议2"],
  "needs_review": false
}}

## 评判原则
✅ 客观打分，不要一律给高分，优秀内容才给18+分
✅ issues 要具体到某一课时或某一环节，不要泛泛而谈
✅ suggestions 要可执行（如"第3课时增加10分钟课堂练习"），不要空洞建议
❌ 不要输出"内容不错"、"整体良好"等无意义评语
❌ 不要编造不存在的问题"""


JUDGE_SYLLABUS_USER = """请评审以下教学大纲的语义质量：

## 教学大纲
{syllabus_json}

请按照评分标准逐项打分，输出JSON对象。"""


def build_judge_syllabus_messages(
    syllabus: dict,
    subject: str,
    grade: str,
    knowledge_point_names: Optional[list[str]] = None,
) -> list:
    """Build messages for judging syllabus semantic quality.

    Args:
        syllabus: 教学大纲 dict（含 sections）。
        subject: 科目。
        grade: 年级。
        knowledge_point_names: 知识点名称列表（用于评判目标适切性）。

    Returns:
        [AIMessage(system), AIMessage(user)]
    """
    from ...ai import AIMessage, MessageRole

    if knowledge_point_names:
        kp_str = "、".join(knowledge_point_names)
    else:
        kp_str = "未提供具体知识点名称"

    system = JUDGE_SYLLABUS_SYSTEM.format(
        subject=subject,
        grade=grade,
        knowledge_points=kp_str,
    )

    # 截断过长的 syllabus 避免超 token
    syllabus_str = json.dumps(syllabus, ensure_ascii=False, indent=2)
    if len(syllabus_str) > 8000:
        syllabus_str = syllabus_str[:8000] + "\n  ... (已截断)"

    user = JUDGE_SYLLABUS_USER.format(syllabus_json=syllabus_str)

    return [
        AIMessage(role=MessageRole.SYSTEM, content=system),
        AIMessage(role=MessageRole.USER, content=user),
    ]


# ─── Slides Judge ───

JUDGE_SLIDES_SYSTEM = """你是PPT教学设计专家，负责评审幻灯片内容的语义质量。

## 评审背景
- 科目：{subject}
- 年级：{grade}
- 教学大纲摘要：{syllabus_summary}

## 评分标准（每项 0-20 分，总分 100）
1. 内容相关性(content_relevance, 20分)：每页是否紧扣知识点和教学目标、有无跑题或凑数页
2. 信息适度性(information_density, 20分)：每页要点是否精炼符合7±2原则、有无信息过载或信息过少
3. 结构完整性(structure_completeness, 20分)：是否覆盖"封面→导入→讲解→练习→总结→作业"完整教学流程
4. 视觉提示性(visual_clarity, 20分)：配图建议(image_suggestion)、公式(formula)、高亮(highlight_points)是否合理
5. 教学可用性(teaching_usability, 20分)：能否直接用于课堂讲授、有无跳跃太大或缺关键步骤

## 评分规则
- 18-20分：优秀，可直接用于课堂
- 14-17分：良好，小修即可用
- 10-13分：合格，需修改部分内容
- 0-9分：不合格，需重新生成

## 输出格式（严格JSON，不要包含markdown代码块标记）
{{
  "score": 整数(0-100, 五项之和),
  "dimensions": {{
    "content_relevance": {{"score": 整数(0-20), "comment": "简评(10-30字)"}},
    "information_density": {{"score": 整数(0-20), "comment": "简评(10-30字)"}},
    "structure_completeness": {{"score": 整数(0-20), "comment": "简评(10-30字)"}},
    "visual_clarity": {{"score": 整数(0-20), "comment": "简评(10-30字)"}},
    "teaching_usability": {{"score": 整数(0-20), "comment": "简评(10-30字)"}}
  }},
  "issues": ["具体问题1(定位到页码)", "具体问题2"],
  "suggestions": ["可执行的改进建议1", "可执行的改进建议2"],
  "needs_review": false
}}

## 评判原则
✅ 客观打分，优秀内容才给18+分
✅ issues 要具体到某一页（如"第3页信息过载"）
✅ suggestions 要可执行（如"第2页拆分为两页"）
❌ 不要输出"排版不错"等无意义评语
❌ 不要编造不存在的问题"""


JUDGE_SLIDES_USER = """请评审以下PPT幻灯片的语义质量：

## 幻灯片列表
{slides_json}

请按照评分标准逐项打分，输出JSON对象。"""


def build_judge_slides_messages(
    slides: list[dict],
    subject: str,
    grade: str,
    syllabus: Optional[dict] = None,
) -> list:
    """Build messages for judging slides semantic quality.

    Args:
        slides: 幻灯片 dict 列表。
        subject: 科目。
        grade: 年级。
        syllabus: 教学大纲（用于提取摘要，帮助评判内容相关性）。

    Returns:
        [AIMessage(system), AIMessage(user)]
    """
    from ...ai import AIMessage, MessageRole

    # 从 syllabus 提取摘要（避免传整个大纲超 token）
    if syllabus:
        sections = syllabus.get("sections", [])
        summary_parts = []
        for s in sections[:3]:  # 最多取3个课时
            title = s.get("title", "")
            core = s.get("core_content", "")[:50]
            summary_parts.append(f"{title}: {core}")
        syllabus_summary = "; ".join(summary_parts) if summary_parts else "无大纲摘要"
    else:
        syllabus_summary = "无大纲摘要"

    system = JUDGE_SLIDES_SYSTEM.format(
        subject=subject,
        grade=grade,
        syllabus_summary=syllabus_summary,
    )

    # 截断过长的 slides 避免超 token
    slides_str = json.dumps(slides, ensure_ascii=False, indent=2)
    if len(slides_str) > 8000:
        slides_str = slides_str[:8000] + "\n  ... (已截断)"

    user = JUDGE_SLIDES_USER.format(slides_json=slides_str)

    return [
        AIMessage(role=MessageRole.SYSTEM, content=system),
        AIMessage(role=MessageRole.USER, content=user),
    ]


# ─── Repair Prompts (low-score feedback repair) ───

REPAIR_SYLLABUS_SYSTEM = """你是资深教研专家。以下教学大纲在质量评审中发现了一些问题，请根据反馈进行修复。

## 评审背景
- 科目：{subject}
- 年级：{grade}

## 修复原则
1. **只修改有问题的部分**，保留质量好的内容，不要全盘重写
2. 严格保持原有的 JSON 结构和字段名不变
3. 针对每个 issue 逐条修复，确保 suggestions 落实
4. 修复后内容必须完整可用（不能有占位符或空字段）

## 输出格式
输出修复后的完整教学大纲 JSON 对象，不要包含markdown代码块标记，不要包含任何说明文字。"""


REPAIR_SYLLABUS_USER = """请修复以下教学大纲：

## 原始大纲
{syllabus_json}

## 质量评审发现的问题
{issues}

## 改进建议
{suggestions}

请根据以上问题和建议，修复大纲并输出完整的 JSON 对象。"""


REPAIR_SLIDES_SYSTEM = """你是PPT教学设计专家。以下PPT在质量评审中发现了一些问题，请根据反馈进行修复。

## 评审背景
- 科目：{subject}
- 年级：{grade}

## 修复原则
1. **只修改有问题的页面**，保留质量好的页面，不要全盘重写
2. 严格保持每页的 JSON 结构（page_num, type, title, bullet_points 等字段名不变）
3. 页码 page_num 必须从1开始连续编号
4. 如建议要求增加页面，可在合适位置插入新页；如建议要求拆分页面，拆分后重新编号
5. 修复后每页内容必须完整（bullet_points 非空、每条≥5字）

## 输出格式
输出修复后的完整 PPT JSON 数组，不要包含markdown代码块标记，不要包含任何说明文字。"""


REPAIR_SLIDES_USER = """请修复以下PPT幻灯片：

## 原始PPT
{slides_json}

## 质量评审发现的问题
{issues}

## 改进建议
{suggestions}

请根据以上问题和建议，修复PPT并输出完整的 JSON 数组。"""


def build_repair_syllabus_messages(
    syllabus: dict,
    issues: list[str],
    suggestions: list[str],
    subject: str,
    grade: str,
) -> list:
    """Build messages for repairing syllabus based on judge feedback.

    Args:
        syllabus: 原始教学大纲。
        issues: LLM Judge 发现的问题列表。
        suggestions: LLM Judge 的改进建议列表。
        subject: 科目。
        grade: 年级。

    Returns:
        [AIMessage(system), AIMessage(user)]
    """
    from ...ai import AIMessage, MessageRole

    system = REPAIR_SYLLABUS_SYSTEM.format(subject=subject, grade=grade)

    syllabus_str = json.dumps(syllabus, ensure_ascii=False, indent=2)
    if len(syllabus_str) > 8000:
        syllabus_str = syllabus_str[:8000] + "\n  ... (已截断)"

    issues_str = "\n".join(f"- {issue}" for issue in issues) if issues else "- 无具体问题"
    suggestions_str = "\n".join(f"- {sug}" for sug in suggestions) if suggestions else "- 无具体建议"

    user = REPAIR_SYLLABUS_USER.format(
        syllabus_json=syllabus_str,
        issues=issues_str,
        suggestions=suggestions_str,
    )

    return [
        AIMessage(role=MessageRole.SYSTEM, content=system),
        AIMessage(role=MessageRole.USER, content=user),
    ]


def build_repair_slides_messages(
    slides: list[dict],
    issues: list[str],
    suggestions: list[str],
    subject: str,
    grade: str,
) -> list:
    """Build messages for repairing slides based on judge feedback.

    Args:
        slides: 原始幻灯片列表。
        issues: LLM Judge 发现的问题列表。
        suggestions: LLM Judge 的改进建议列表。
        subject: 科目。
        grade: 年级。

    Returns:
        [AIMessage(system), AIMessage(user)]
    """
    from ...ai import AIMessage, MessageRole

    system = REPAIR_SLIDES_SYSTEM.format(subject=subject, grade=grade)

    slides_str = json.dumps(slides, ensure_ascii=False, indent=2)
    if len(slides_str) > 8000:
        slides_str = slides_str[:8000] + "\n  ... (已截断)"

    issues_str = "\n".join(f"- {issue}" for issue in issues) if issues else "- 无具体问题"
    suggestions_str = "\n".join(f"- {sug}" for sug in suggestions) if suggestions else "- 无具体建议"

    user = REPAIR_SLIDES_USER.format(
        slides_json=slides_str,
        issues=issues_str,
        suggestions=suggestions_str,
    )

    return [
        AIMessage(role=MessageRole.SYSTEM, content=system),
        AIMessage(role=MessageRole.USER, content=user),
    ]
