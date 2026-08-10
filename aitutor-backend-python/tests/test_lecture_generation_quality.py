from landppt.services.lecture_generation_service import LectureGenerationService
from landppt.services.lecture_prompts import build_slide_prompt


def test_slide_prompt_contains_outline_title_and_previous_pages() -> None:
    prompt = build_slide_prompt(
        title="光合作用",
        page_title="光合作用的产物",
        page_num=3,
        total_pages=8,
        slide_type="content",
        source_text="植物通过光合作用制造有机物并释放氧气。",
        previous_slides="第2页《光合作用的原料》：二氧化碳；水",
    )

    assert "本页大纲标题：光合作用的产物" in prompt
    assert "第2页《光合作用的原料》" in prompt
    assert "本页不得重复其主要观点" in prompt


def test_duplicate_page_detector_finds_reworded_same_topic() -> None:
    previous = [{
        "pageNum": 2,
        "title": "VS Code 名称解码",
        "bulletPoints": [
            "VS 是 Visual Studio 的缩写",
            "Code 表示代码编辑器",
            "大战代码是中文社区的趣味谐音",
        ],
    }]
    repeated = {
        "pageNum": 3,
        "title": "为什么叫微软大战代码",
        "bulletPoints": [
            "VS 来源于 Visual Studio",
            "Code 的含义是代码",
            "大战代码来自网友对 VS 的谐音解读",
        ],
    }

    assert LectureGenerationService._find_duplicate_page(repeated, previous) == 2


def test_duplicate_page_detector_allows_distinct_page_role() -> None:
    previous = [{
        "pageNum": 2,
        "title": "VS Code 名称由来",
        "bulletPoints": ["VS 是 Visual Studio 的缩写", "Code 表示代码"],
    }]
    distinct = {
        "pageNum": 3,
        "title": "安装插件并配置 Python 环境",
        "bulletPoints": ["打开扩展市场", "安装 Python 插件", "选择解释器"],
    }

    assert LectureGenerationService._find_duplicate_page(distinct, previous) is None


def test_placeholder_slide_is_not_complete() -> None:
    placeholder = {
        "pageNum": 4,
        "type": "interactive",
        "title": "第4页",
        "bulletPoints": ["内容生成中..."],
    }

    assert LectureGenerationService._is_complete_slide(placeholder) is False


def test_real_slide_is_complete() -> None:
    slide = {
        "pageNum": 4,
        "type": "interactive",
        "title": "资源调度挑战",
        "bulletPoints": ["根据任务优先级分配算力", "说明你的选择理由"],
    }

    assert LectureGenerationService._is_complete_slide(slide) is True
