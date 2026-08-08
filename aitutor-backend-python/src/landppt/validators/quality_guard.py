"""
Quality Guard — Layer 1 hard quality metrics for M5 AI备课.

Zero-cost, zero-latency automated detection. Checks "有没有" (presence),
not "好不好" (semantic quality — that's Layer 2 LLM judge).

Checks two output types:
  - syllabus: 教学大纲详细度、字段非空、环节齐全
  - ppt_structure / slides: PPT 完整性、页数、封面、占位页、内容非空

Usage:
    from .quality_guard import QualityGuard, QualityReport

    guard = QualityGuard()
    report = guard.check_syllabus(syllabus)
    if report.issues:
        logger.warning(f"质量警告: {report.issues}")

    report = guard.check_slides(slides)
    if report.is_blocking:
        # 阻断性质量问题，需要触发重试或标记低质量
"""
from dataclasses import dataclass, field
from typing import Any, Optional
import logging

logger = logging.getLogger(__name__)


# ─── 空值/占位符黑名单 ───
# AI 常输出的"空内容"模式，硬性指标应拒绝
EMPTY_PATTERNS = {"", "无", "待补充", "待填写", "暂无", "null", "none", "n/a", "TODO", "TBD"}


def _is_empty(value: Any) -> bool:
    """检查字符串值是否为空或占位符。"""
    if value is None:
        return True
    if isinstance(value, str):
        return value.strip().lower() in EMPTY_PATTERNS
    if isinstance(value, (list, dict)):
        return len(value) == 0
    return False


def _str_len(value: Any) -> int:
    """安全获取字符串长度。"""
    return len(str(value).strip()) if value else 0


@dataclass
class QualityReport:
    """质量守卫检测报告。

    Attributes:
        issues: 所有检测到的问题列表（不阻断，仅警告）
        blocking_issues: 阻断性问题（必须处理后才能返回用户）
        score: 0-100 质量分（100=完美，<60=低质量）
        is_blocking: 是否有阻断性问题
        checked_items: 检查的项数
    """
    issues: list[str] = field(default_factory=list)
    blocking_issues: list[str] = field(default_factory=list)
    score: float = 100.0
    checked_items: int = 0

    @property
    def is_blocking(self) -> bool:
        """是否有阻断性问题。"""
        return len(self.blocking_issues) > 0

    @property
    def is_low_quality(self) -> bool:
        """是否为低质量（分数 < 60）。"""
        return self.score < 60.0

    def merge(self, other: "QualityReport") -> "QualityReport":
        """合并两份报告（用于 syllabus + slides 联合检测）。"""
        return QualityReport(
            issues=self.issues + other.issues,
            blocking_issues=self.blocking_issues + other.blocking_issues,
            score=(self.score + other.score) / 2,
            checked_items=self.checked_items + other.checked_items,
       )


class QualityGuard:
    """Layer 1 硬性质量指标检测器。

    所有方法都是零成本纯 Python 检查，不调用 AI。
    检测两类问题：
      1. blocking_issues: 阻断性（如 PPT 只有 1 页、完全无 sections）
      2. issues: 警告性（如某课时作业缺少拓展题）

    阻断性问题建议触发 Stage 重试，警告性问题记入 ctx.warnings。
    """

    # ─── 阈值配置 ───
    SYLLABUS_MIN_GOALS = 2          # 每课时至少 2 条教学目标
    SYLLABUS_MIN_PROCESS_STEPS = 4  # 每课时至少 4 个教学环节
    SYLLABUS_MIN_ACTIVITY_LEN = 10  # 活动/意图描述至少 10 字
    SYLLABUS_MIN_CORE_CONTENT_LEN = 10  # 核心内容至少 10 字

    SLIDES_MIN_COUNT = 4            # PPT 至少 4 页
    SLIDES_MAX_BULLET_LEN = 7       # 每页至多 7 个要点（7±2 原则）
    SLIDES_MIN_BULLET_LEN = 5       # 每条要点至少 5 字
    SLIDES_MAX_FALLBACK_RATIO = 0.3  # 占位页占比不超过 30%

    def check_syllabus(self, syllabus: dict) -> QualityReport:
        """检测教学大纲质量。

        检查项：
          - sections 非空（阻断）
          - 每课时教学目标 ≥ 2 条
          - 每课时教学环节 ≥ 4 个
          - 教师活动/学生活动/设计意图 非空且 ≥ 10 字
          - 作业三档齐全（basic 必填，advanced/optional 警告）
          - 核心内容非空且 ≥ 10 字
          - 字段无空值占位符（"无"/"待补充"等）
        """
        report = QualityReport()

        if not isinstance(syllabus, dict):
            report.blocking_issues.append("syllabus 不是 dict 类型")
            report.score = 0.0
            return report

        sections = syllabus.get("sections", []) or []
        report.checked_items += 1

        # ── 阻断性：sections 不能为空 ──
        if not sections:
            report.blocking_issues.append("教学大纲没有课时(sections)数据")
            report.score = 0.0
            return report

        # ── 检查每个课时 ──
        total_checks = 0
        failed_checks = 0

        for si, section in enumerate(sections, start=1):
            section_label = f"第{si}课时"

            # 1. 核心内容非空
            core = section.get("core_content", "")
            total_checks += 1
            if _is_empty(core) or _str_len(core) < self.SYLLABUS_MIN_CORE_CONTENT_LEN:
                report.issues.append(f"{section_label}核心内容为空或过短(<{self.SYLLABUS_MIN_CORE_CONTENT_LEN}字)")
                failed_checks += 1

            # 2. 教学目标 ≥ 2 条
            goals = section.get("teaching_goals", []) or []
            total_checks += 1
            if len(goals) < self.SYLLABUS_MIN_GOALS:
                report.issues.append(
                    f"{section_label}教学目标不足{self.SYLLABUS_MIN_GOALS}条(当前{len(goals)}条)"
                )
                failed_checks += 1
            else:
                # 检查目标内容是否为空
                empty_goals = [g for g in goals if _is_empty(g)]
                if empty_goals:
                    report.issues.append(f"{section_label}有{len(empty_goals)}条空教学目标")
                    failed_checks += 1

            # 3. 教学环节 ≥ 4 个
            process = section.get("teaching_process", []) or []
            total_checks += 1
            if len(process) < self.SYLLABUS_MIN_PROCESS_STEPS:
                report.issues.append(
                    f"{section_label}教学环节不足{self.SYLLABUS_MIN_PROCESS_STEPS}个(当前{len(process)}个)"
                )
                failed_checks += 1

            # 4. 每个环节的活动描述非空且 ≥ 10 字
            for pi, step in enumerate(process, start=1):
                step_label = f"{section_label}环节{pi}"
                for field_name in ("teacher_activity", "student_activity", "design_intent"):
                    total_checks += 1
                    val = step.get(field_name, "")
                    if _is_empty(val) or _str_len(val) < self.SYLLABUS_MIN_ACTIVITY_LEN:
                        report.issues.append(
                            f"{step_label}.{field_name} 为空或过短(<{self.SYLLABUS_MIN_ACTIVITY_LEN}字)"
                        )
                        failed_checks += 1

            # 5. 作业三档齐全
            homework = section.get("homework", {}) or {}
            total_checks += 1
            if _is_empty(homework.get("basic")):
                report.issues.append(f"{section_label}缺少基础作业(basic)")
                failed_checks += 1
            if _is_empty(homework.get("advanced")):
                report.issues.append(f"{section_label}缺少提高作业(advanced)")
                failed_checks += 1  # 不阻断，但计失败

            # 6. 重点/难点非空
            for field_name in ("key_points", "difficult_points"):
                total_checks += 1
                if _is_empty(section.get(field_name)):
                    report.issues.append(f"{section_label}{field_name} 为空")
                    failed_checks += 1

        # ── 计算分数 ──
        if total_checks > 0:
            report.score = max(0.0, 100.0 * (1 - failed_checks / total_checks))
        report.checked_items += total_checks

        # 全部课时都失败 → 阻断
        if failed_checks == total_checks and total_checks > 0:
            report.blocking_issues.append("教学大纲所有检查项都未通过")

        return report

    def check_slides(self, slides: list) -> QualityReport:
        """检测 PPT 幻灯片质量。

        检查项：
          - slides 非空且 ≥ 4 页（阻断）
          - 至少 1 个 cover 页
          - 占位页(is_fallback)占比 ≤ 30%
          - 每页 bullet_points 非空且每条 ≥ 5 字
          - 每页 bullet_points ≤ 7 条（7±2 原则）
          - 无空值占位符
        """
        report = QualityReport()

        if not isinstance(slides, list):
            report.blocking_issues.append("slides 不是 list 类型")
            report.score = 0.0
            return report

        report.checked_items += 1

        # ── 阻断性：页数 ≥ 4 ──
        if len(slides) == 0:
            report.blocking_issues.append("PPT 没有任何幻灯片")
            report.score = 0.0
            return report

        if len(slides) < self.SLIDES_MIN_COUNT:
            report.blocking_issues.append(
                f"PPT 页数不足{self.SLIDES_MIN_COUNT}页(当前{len(slides)}页)"
            )
            # 不直接返回 0 分，继续检查其他项

        # ── 检查每页 ──
        total_checks = 0
        failed_checks = 0

        cover_count = 0
        fallback_count = 0

        for pi, slide in enumerate(slides, start=1):
            page_label = f"第{pi}页"

            if not isinstance(slide, dict):
                report.issues.append(f"{page_label}不是 dict 类型")
                failed_checks += 1
                total_checks += 1
                continue

            # 统计 cover 和 fallback
            if slide.get("type") == "cover":
                cover_count += 1
            if slide.get("is_fallback"):
                fallback_count += 1

            # 1. title 非空
            total_checks += 1
            if _is_empty(slide.get("title")):
                report.issues.append(f"{page_label}标题为空")
                failed_checks += 1

            # 2. bullet_points 非空
            bullets = slide.get("bullet_points", []) or []
            total_checks += 1
            if _is_empty(bullets):
                report.issues.append(f"{page_label}正文要点为空")
                failed_checks += 1
            else:
                # 每条要点 ≥ 5 字
                short_bullets = [b for b in bullets if _str_len(b) < self.SLIDES_MIN_BULLET_LEN]
                if short_bullets:
                    report.issues.append(
                        f"{page_label}有{len(short_bullets)}条要点过短(<{self.SLIDES_MIN_BULLET_LEN}字)"
                    )
                    failed_checks += 1

                # 要点数 ≤ 7（7±2 原则）
                if len(bullets) > self.SLIDES_MAX_BULLET_LEN:
                    report.issues.append(
                        f"{page_label}要点数{len(bullets)}超过{self.SLIDES_MAX_BULLET_LEN}(7±2原则)"
                    )
                    failed_checks += 1

            # 3. cover 页只出现一次
            # (在循环外统计)

        # ── 封面页检查 ──
        total_checks += 1
        if cover_count == 0:
            report.issues.append("PPT 缺少封面页(cover)")
            failed_checks += 1
        elif cover_count > 1:
            report.issues.append(f"PPT 有{cover_count}个封面页(应只有1个)")
            failed_checks += 1

        # ── 占位页占比检查 ──
        total_checks += 1
        fallback_ratio = fallback_count / len(slides) if slides else 0
        if fallback_ratio > self.SLIDES_MAX_FALLBACK_RATIO:
            report.issues.append(
                f"占位页占比{fallback_ratio:.0%}超过{self.SLIDES_MAX_FALLBACK_RATIO:.0%}阈值"
            )
            failed_checks += 1
        elif fallback_count > 0:
            # 有占位页但未超阈值，记录为警告
            report.issues.append(f"有{fallback_count}页为生成失败的占位页")

        # ── 计算分数 ──
        if total_checks > 0:
            # 占位页额外扣分
            penalty = fallback_ratio * 30  # 占位页最多扣 30 分
            base_score = 100.0 * (1 - failed_checks / total_checks)
            report.score = max(0.0, base_score - penalty)
        report.checked_items += total_checks

        # 占位页过多 → 阻断
        if fallback_count > 0 and fallback_ratio > 0.5:
            report.blocking_issues.append(
                f"超过50%的页({fallback_count}/{len(slides)})是生成失败的占位页"
            )

        return report

    def check_final(self, syllabus: dict, slides: list) -> QualityReport:
        """联合检测：syllabus + slides。

        用于在最终返回前做整体质量守门。
        """
        syll_report = self.check_syllabus(syllabus)
        slides_report = self.check_slides(slides)
        return syll_report.merge(slides_report)

    def filter_fallback_slides(self, slides: list) -> tuple[list, list[int]]:
        """过滤掉 fallback 占位页。

        Returns:
            (clean_slides, removed_indices): 清理后的 slides 列表 + 被移除的页码索引
        """
        clean = []
        removed = []
        for idx, slide in enumerate(slides):
            if isinstance(slide, dict) and slide.get("is_fallback"):
                removed.append(idx)
            else:
                clean.append(slide)
        return clean, removed

    def renumber_slides(self, slides: list) -> list:
        """重新编号 slide 的 page_num（过滤后页码可能不连续）。"""
        for idx, slide in enumerate(slides, start=1):
            if isinstance(slide, dict):
                slide["page_num"] = idx
        return slides
