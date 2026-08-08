"""
LLM-as-Judge — Layer 2 语义质量评判。

用 AI 评判教学大纲和 PPT 的语义质量（内容准确性、逻辑性等），
补充 Layer 1 硬性指标无法检测的"好不好"维度。

Cost: 1 AI call per judge (syllabus + slides = 2 calls).
Fails gracefully: returns JudgeReport with error on failure, never blocks generation.

Usage:
    from .llm_judge import LLMJudge

    judge = LLMJudge()
    report = await judge.judge_syllabus(syllabus, subject="math", grade="grade_9")
    if report.error is None:
        print(f"Score: {report.score}, Issues: {report.issues}")
"""
import logging
from dataclasses import dataclass, field
from typing import Any, Optional

logger = logging.getLogger(__name__)


@dataclass
class DimensionScore:
    """单个评判维度的得分和评语。"""
    score: int = 0
    comment: str = ""


@dataclass
class JudgeReport:
    """LLM 评判报告。

    Attributes:
        score: 0-100 总分
        dimensions: 各维度得分 {dimension_name: {score, comment}}
        issues: 具体问题列表（定位到课时/页码）
        suggestions: 可执行的改进建议
        needs_review: 是否需要人工复查
        error: 评判失败时的错误信息（None 表示成功）
    """
    score: float = 0.0
    dimensions: dict = field(default_factory=dict)
    issues: list[str] = field(default_factory=list)
    suggestions: list[str] = field(default_factory=list)
    needs_review: bool = False
    error: Optional[str] = None

    @property
    def is_low_quality(self) -> bool:
        """是否为低质量（分数 < 60）。"""
        return self.score < 60.0

    @property
    def is_success(self) -> bool:
        """评判是否成功（无错误）。"""
        return self.error is None

    def to_dict(self) -> dict:
        """转为 dict（用于序列化存储和 API 返回）。"""
        return {
            "score": self.score,
            "dimensions": self.dimensions,
            "issues": self.issues if self.issues else [],
            "suggestions": self.suggestions if self.suggestions else [],
            "needs_review": self.needs_review,
            "error": self.error,
        }


class LLMJudge:
    """LLM 语义质量评判器。

    在 Layer 1 硬性指标检测通过后自动执行，
    评判教学大纲和 PPT 的语义质量。

    设计原则：
      - 失败降级：评判异常时返回 error，不阻断生成流程
      - 稳定输出：temperature=0.1 + json_mode 确保评判结果稳定
      - 成本可控：每次评判 1 次 AI 调用
    """

    # 评判温度（低温度确保评判稳定）
    JUDGE_TEMPERATURE = 0.1

    # 低分阈值（低于此值标记 needs_review）
    LOW_SCORE_THRESHOLD = 60.0

    def __init__(self, provider=None):
        self._provider = provider

    @property
    def provider(self):
        """Lazy-init AI provider."""
        if self._provider is None:
            from ..ai import get_ai_provider
            self._provider = get_ai_provider()
        return self._provider

    async def judge_syllabus(
        self,
        syllabus: dict,
        subject: str,
        grade: str,
        knowledge_point_names: Optional[list[str]] = None,
    ) -> JudgeReport:
        """评判教学大纲语义质量。

        Args:
            syllabus: 教学大纲 dict（含 sections）。
            subject: 科目。
            grade: 年级。
            knowledge_point_names: 知识点名称列表。

        Returns:
            JudgeReport，失败时 error 字段不为 None。
        """
        from ..services.prompts.judge_prompts import build_judge_syllabus_messages
        from ..utils.json_extractor import JSONExtractor

        messages = build_judge_syllabus_messages(
            syllabus=syllabus,
            subject=subject,
            grade=grade,
            knowledge_point_names=knowledge_point_names,
        )

        try:
            result = await self.provider.chat_completion(
                messages,
                json_mode=True,
                temperature=self.JUDGE_TEMPERATURE,
            )
            data = JSONExtractor.extract_dict(result.content, fallback={})
            return self._parse_report(data)

        except Exception as e:
            logger.warning(f"LLM judge syllabus failed: {e}")
            return JudgeReport(error=str(e))

    async def judge_slides(
        self,
        slides: list[dict],
        subject: str,
        grade: str,
        syllabus: Optional[dict] = None,
    ) -> JudgeReport:
        """评判 PPT 幻灯片语义质量。

        Args:
            slides: 幻灯片 dict 列表。
            subject: 科目。
            grade: 年级。
            syllabus: 教学大纲（用于提取摘要辅助评判）。

        Returns:
            JudgeReport，失败时 error 字段不为 None。
        """
        from ..services.prompts.judge_prompts import build_judge_slides_messages
        from ..utils.json_extractor import JSONExtractor

        messages = build_judge_slides_messages(
            slides=slides,
            subject=subject,
            grade=grade,
            syllabus=syllabus,
        )

        try:
            result = await self.provider.chat_completion(
                messages,
                json_mode=True,
                temperature=self.JUDGE_TEMPERATURE,
            )
            data = JSONExtractor.extract_dict(result.content, fallback={})
            return self._parse_report(data)

        except Exception as e:
            logger.warning(f"LLM judge slides failed: {e}")
            return JudgeReport(error=str(e))

    async def judge_all(
        self,
        syllabus: dict,
        slides: list[dict],
        subject: str,
        grade: str,
        knowledge_point_names: Optional[list[str]] = None,
    ) -> dict:
        """同时评判大纲和 PPT，返回合并报告。

        两次评判串行执行（避免并发触发 rate limit）。

        Returns:
            {
                "score": float,           # 总分（大纲+PPT平均）
                "needs_review": bool,
                "syllabus": {...},        # 大纲评判报告
                "slides": {...},          # PPT评判报告
                "error": None             # 整体错误（两次都失败时才有）
            }
        """
        syll_report = await self.judge_syllabus(
            syllabus=syllabus,
            subject=subject,
            grade=grade,
            knowledge_point_names=knowledge_point_names,
        )

        slides_report = await self.judge_slides(
            slides=slides,
            subject=subject,
            grade=grade,
            syllabus=syllabus,
        )

        # 合并总分
        scores = []
        if syll_report.is_success:
            scores.append(syll_report.score)
        if slides_report.is_success:
            scores.append(slides_report.score)

        overall_score = sum(scores) / len(scores) if scores else 0.0
        needs_review = (
            overall_score < self.LOW_SCORE_THRESHOLD
            or syll_report.needs_review
            or slides_report.needs_review
        )

        # 两次都失败才算整体错误
        overall_error = None
        if not syll_report.is_success and not slides_report.is_success:
            overall_error = f"syllabus: {syll_report.error}; slides: {slides_report.error}"

        return {
            "score": overall_score,
            "needs_review": needs_review,
            "syllabus": syll_report.to_dict(),
            "slides": slides_report.to_dict(),
            "error": overall_error,
        }

    # ════════════════════════════════════════════════════════════
    #  Low-score feedback repair
    # ════════════════════════════════════════════════════════════

    async def repair_syllabus(
        self,
        syllabus: dict,
        issues: list[str],
        suggestions: list[str],
        subject: str,
        grade: str,
    ) -> Optional[dict]:
        """根据评判反馈修复教学大纲。

        将 LLM Judge 发现的 issues + suggestions 反馈给 AI，
        让 AI 针对性修改有问题的部分。

        Args:
            syllabus: 原始教学大纲。
            issues: 评判发现的问题列表。
            suggestions: 改进建议列表。
            subject: 科目。
            grade: 年级。

        Returns:
            修复后的大纲 dict，失败时返回 None（调用方应保留原始内容）。
        """
        from ..services.prompts.judge_prompts import build_repair_syllabus_messages
        from ..utils.json_extractor import JSONExtractor

        messages = build_repair_syllabus_messages(
            syllabus=syllabus,
            issues=issues,
            suggestions=suggestions,
            subject=subject,
            grade=grade,
        )

        try:
            result = await self.provider.chat_completion(
                messages,
                json_mode=True,
                temperature=0.3,  # 修复需要一点创意，但保持稳定
            )
            repaired = JSONExtractor.extract_dict(result.content, fallback=None)
            if not isinstance(repaired, dict) or not repaired.get("sections"):
                logger.warning("Repair syllabus: 返回数据无效，保留原始内容")
                return None
            logger.info("Repair syllabus: 修复成功")
            return repaired
        except Exception as e:
            logger.warning(f"Repair syllabus failed: {e}")
            return None

    async def repair_slides(
        self,
        slides: list[dict],
        issues: list[str],
        suggestions: list[str],
        subject: str,
        grade: str,
    ) -> Optional[list[dict]]:
        """根据评判反馈修复 PPT 幻灯片。

        Args:
            slides: 原始幻灯片列表。
            issues: 评判发现的问题列表。
            suggestions: 改进建议列表。
            subject: 科目。
            grade: 年级。

        Returns:
            修复后的 slides 列表，失败时返回 None（调用方应保留原始内容）。
        """
        from ..services.prompts.judge_prompts import build_repair_slides_messages
        from ..utils.json_extractor import JSONExtractor

        messages = build_repair_slides_messages(
            slides=slides,
            issues=issues,
            suggestions=suggestions,
            subject=subject,
            grade=grade,
        )

        try:
            result = await self.provider.chat_completion(
                messages,
                json_mode=True,
                temperature=0.3,
            )
            # PPT 修复返回的是数组，用 extract 而非 extract_dict
            repaired = JSONExtractor.extract(result.content, fallback=None)
            if not isinstance(repaired, list) or len(repaired) == 0:
                logger.warning("Repair slides: 返回数据无效，保留原始内容")
                return None

            # 重新编号 page_num（修复可能增减了页面）
            for idx, slide in enumerate(repaired, start=1):
                if isinstance(slide, dict):
                    slide["page_num"] = idx

            logger.info(f"Repair slides: 修复成功，{len(repaired)}页")
            return repaired
        except Exception as e:
            logger.warning(f"Repair slides failed: {e}")
            return None

    def _parse_report(self, data: dict) -> JudgeReport:
        """解析 AI 返回的评判结果为 JudgeReport。

        做基本的数据清洗和类型校验，确保返回值可靠。
        """
        if not isinstance(data, dict):
            return JudgeReport(error=f"AI返回非dict: {type(data).__name__}")

        # 解析分数（容错：可能是 int/float/str）
        try:
            score = float(data.get("score", 0))
            score = max(0.0, min(100.0, score))  # clamp to [0, 100]
        except (ValueError, TypeError):
            score = 0.0

        # 解析维度
        dimensions = data.get("dimensions", {})
        if not isinstance(dimensions, dict):
            dimensions = {}

        # 解析 issues / suggestions（容错：确保是 list[str]）
        issues = self._parse_str_list(data.get("issues", []))
        suggestions = self._parse_str_list(data.get("suggestions", []))

        needs_review = bool(data.get("needs_review", False))
        # 低分自动标记 needs_review
        if score < self.LOW_SCORE_THRESHOLD:
            needs_review = True

        return JudgeReport(
            score=score,
            dimensions=dimensions,
            issues=issues,
            suggestions=suggestions,
            needs_review=needs_review,
            error=None,
        )

    @staticmethod
    def _parse_str_list(value: Any) -> list[str]:
        """安全解析为 string 列表。"""
        if not isinstance(value, list):
            return []
        return [str(item) for item in value if item]
