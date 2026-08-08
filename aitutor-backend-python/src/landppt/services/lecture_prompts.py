"""
M4 讲课生成 · LLM 提示词
参考 M5 lesson_prep_prompts.py 的 message 构建模式
"""
from ..ai import AIMessage, MessageRole


def build_teaching_system_message() -> AIMessage:
    return AIMessage(
        role=MessageRole.SYSTEM,
        content=(
            "你是一个经验丰富的K9教育AI教师。"
            "请根据用户提供的教学内容生成讲课幻灯片。"
            "严格输出JSON，不要包含markdown代码块标记（```json）。"
        ),
    )


def build_outline_prompt(
    source_text: str, grade: str = "", weak_points: str = ""
) -> str:
    return (
        f"请根据以下教学内容生成讲课大纲。\n\n"
        f"教学内容：\n{source_text}\n\n"
        f"年级：{grade or '未指定'}\n"
        f"薄弱点：{weak_points or '无'}\n\n"
        f"JSON格式：\n"
        f'{{"title":"标题","totalPages":8,"outline":['
        f'{{"page":1,"type":"cover","title":"封面"}},'
        f'{{"page":2,"type":"content","title":"内容"}},...]}}\n'
        f"type: cover|content|example|interaction|summary|ending\n"
        f"totalPages: 5-10页"
    )


def build_slide_prompt(
    title: str,
    page_num: int,
    total_pages: int,
    slide_type: str,
    source_text: str,
    grade: str = "",
    weak_points: str = "",
) -> str:
    type_hint = {
        "cover": "封面页：标题+副标题",
        "content": "内容页：知识要点+公式",
        "example": "例题页：题目+解答+重点",
        "interaction": "互动页：提问+答案",
        "summary": "总结页：归纳要点",
        "ending": "结束页：课后思考",
    }.get(slide_type, "内容页")

    return (
        f"讲课主题：{title}\n"
        f"第{page_num}/{total_pages}页 类型：{type_hint}\n"
        f"年级：{grade or '未指定'}\n"
        f"薄弱点：{weak_points or '无'}\n"
        f"教学内容：\n{source_text[:2000]}\n\n"
        f"JSON输出：\n"
        f'{{"pageNum":{page_num},'
        f'"type":"{slide_type}",'
        f'"title":"页标题",'
        f'"subtitle":"副标题",'
        f'"bulletPoints":["要点1","要点2","要点3"],'
        f'"formula":"公式",'
        f'"highlightPoints":["重点1","重点2"],'
        f'"interaction":{{"type":"question","question":"问题","answer":"答案"}}}}\n'
        f"直接输出JSON，不要markdown标记"
    )
