"""
Validation pipeline — routes validation calls by output_type.

Each output type (syllabus, slide, narration) has a list of validators
that run sequentially. The pipeline collects results and can optionally
auto-fix where supported.

[Layer 1] QualityGuard: 硬性指标检测（零成本纯 Python 检查）
[Layer 2] SchemaValidator: JSON Schema 结构校验 + auto-fix
[Layer 3] NarrationValidator: 讲稿口语化/时长/一致性检测
"""
import logging
from dataclasses import dataclass, field
from typing import Any, Optional

from .schema_validator import SchemaValidator, ValidationConfig, ValidationResult
from .narration_validator import NarrationValidator, ValidationReport
from .quality_guard import QualityGuard, QualityReport

logger = logging.getLogger(__name__)


@dataclass
class PipelineResult:
    """Aggregated result from all validators for one output type."""
    passed: bool
    output_type: str
    individual_results: list = field(default_factory=list)
    overall_score: float = 1.0
    warnings: list[str] = field(default_factory=list)
    fixes_applied: list[str] = field(default_factory=list)
    quality_report: Optional[QualityReport] = None  # [Layer 1] 硬性指标报告

    def merge(self, other: "PipelineResult") -> "PipelineResult":
        """Merge two pipeline results (for final combined check)."""
        merged_quality = None
        if self.quality_report and other.quality_report:
            merged_quality = self.quality_report.merge(other.quality_report)
        elif self.quality_report:
            merged_quality = self.quality_report
        else:
            merged_quality = other.quality_report

        return PipelineResult(
            passed=self.passed and other.passed,
            output_type=f"{self.output_type}+{other.output_type}",
            individual_results=self.individual_results + other.individual_results,
            overall_score=(self.overall_score + other.overall_score) / 2,
            warnings=self.warnings + other.warnings,
            fixes_applied=self.fixes_applied + other.fixes_applied,
            quality_report=merged_quality,
        )


class ValidationPipeline:
    """Orchestrates validators by output type.

    Usage:
        pipeline = ValidationPipeline()
        result = await pipeline.validate("slide", slide_data)
        if not result.passed:
            for w in result.warnings:
                logger.warning(w)
    """

    def __init__(self, auto_fix: bool = True):
        self.auto_fix = auto_fix
        self.schema_validator = SchemaValidator(
            config=ValidationConfig(auto_fix=auto_fix)
        )
        self.narration_validator = NarrationValidator()
        self.quality_guard = QualityGuard()  # [Layer 1] 硬性指标检测

    async def validate(
        self,
        output_type: str,
        data: Any,
        **context,
    ) -> PipelineResult:
        """Route data to the correct set of validators.

        Args:
            output_type: "syllabus" | "slide" | "ppt_structure" | "narration" | "final"
            data: The data to validate.
            **context: Extra context (slide dict for narration, target_seconds, etc.)

        Returns:
            PipelineResult with passed flag + warnings.
        """
        results: list = []
        fixes: list[str] = []
        warnings: list[str] = []
        quality_report: Optional[QualityReport] = None  # [Layer 1] 硬性指标报告

        if output_type == "syllabus":
            # [Layer 1] 硬性指标检测
            quality_report = self.quality_guard.check_syllabus(data)
            if quality_report.issues:
                warnings.extend(quality_report.issues)
            if quality_report.is_blocking:
                warnings.extend(quality_report.blocking_issues)

            # [Layer 2] Schema 校验
            result = self.schema_validator.validate(data, "lesson_plan")
            results.append(result)
            if result.fixes_applied:
                fixes.extend(result.fixes_applied)
            if not result.passed:
                warnings.append(
                    f"教学大纲 {len(result.errors)} 项校验未通过"
                )

        elif output_type == "outline":
            result = self.schema_validator.validate(data, "outline")
            results.append(result)
            if not result.passed:
                warnings.append(
                    f"大纲结构 {len(result.errors)} 项校验未通过"
                )

        elif output_type == "slide":
            # [Layer 2] Schema 校验（单页 slide 不做硬性指标，整体 ppt_structure 才做）
            result = self.schema_validator.validate(data, "slide")
            results.append(result)
            if result.fixes_applied:
                fixes.extend(result.fixes_applied)
            if not result.passed:
                warnings.append(
                    f"幻灯片结构 {len(result.errors)} 项校验未通过"
                )

        elif output_type == "ppt_structure":
            # [Layer 1] 硬性指标检测（支持裸数组或 {slides: [...]} 格式）
            slides_to_check = data
            if isinstance(data, dict) and "slides" in data:
                slides_to_check = data["slides"]
            quality_report = self.quality_guard.check_slides(slides_to_check)
            if quality_report.issues:
                warnings.extend(quality_report.issues)
            if quality_report.is_blocking:
                warnings.extend(quality_report.blocking_issues)

            # [Layer 2] Schema 校验
            result = self.schema_validator.validate(data, "ppt_structure")
            results.append(result)
            if not result.passed:
                warnings.append(
                    f"PPT结构 {len(result.errors)} 项校验未通过"
                )

        elif output_type == "narration":
            slide = context.get("slide", {})
            target_seconds = context.get("target_seconds")
            narration_text = data if isinstance(data, str) else data.get("narration_text", "")

            report = self.narration_validator.validate(
                narration_text=narration_text,
                slide=slide,
                target_seconds=target_seconds,
            )
            results.append(report)

            if not report.passed:
                issues = []
                if report.orality.score < 0.6:
                    issues.append("口语化不足")
                if not report.duration.is_reasonable:
                    issues.append(f"时长偏差 {report.duration.deviation:.0%}")
                if report.consistency.coverage < 0.7:
                    issues.append(f"术语覆盖率 {report.consistency.coverage:.0%}")
                if issues:
                    warnings.append(f"讲解词质量警告: {'、'.join(issues)}")

        elif output_type == "final":
            # Final combined check across all stages
            syllabus_data = data.get("syllabus", {})
            slides_data = data.get("slides", []) or []

            # [Layer 1] 联合硬性指标检测
            quality_report = self.quality_guard.check_final(syllabus_data, slides_data)
            if quality_report.issues:
                warnings.extend(quality_report.issues)
            if quality_report.is_blocking:
                warnings.extend(quality_report.blocking_issues)

            # [Layer 2] Schema 校验
            syll_result = self.schema_validator.validate(syllabus_data, "lesson_plan")
            ppt_result = self.schema_validator.validate(slides_data, "ppt_structure")

            # Count fallback slides（保留原有逻辑，QualityGuard 也会检测）
            fallback_count = sum(
                1 for s in slides_data if isinstance(s, dict) and s.get("is_fallback")
            )
            if fallback_count > 0 and f"{fallback_count} 页为占位内容（生成失败）" not in warnings:
                warnings.append(f"{fallback_count} 页为占位内容（生成失败）")

            # Count narration errors
            narrations = data.get("narrations", []) or []
            empty_narr = sum(
                1 for n in narrations
                if isinstance(n, dict) and not n.get("narration_text", "").strip()
            )
            if empty_narr > 0:
                warnings.append(f"{empty_narr} 页讲解词为空")

            results = [syll_result, ppt_result]

        else:
            raise ValueError(f"Unknown output_type: {output_type}")

        # Calculate overall score
        scores = []
        for r in results:
            if hasattr(r, "overall_score"):
                scores.append(r.overall_score)
            elif hasattr(r, "score"):
                scores.append(r.score)
            else:
                scores.append(1.0 if r.passed else 0.0)

        overall_score = sum(scores) / max(len(scores), 1) if scores else 1.0

        return PipelineResult(
            passed=all(
                r.passed if hasattr(r, "passed") else False
                for r in results
            ),
            output_type=output_type,
            individual_results=results,
            overall_score=overall_score,
            warnings=warnings,
            fixes_applied=fixes,
            quality_report=quality_report,
        )

    def get_fixed_data(self, output_type: str, data: Any, **context) -> Any:
        """Get auto-fixed version of data. Returns original if no fix needed."""
        if output_type in ("syllabus", "lesson_plan"):
            result = self.schema_validator.validate(data, "lesson_plan")
        elif output_type == "slide":
            result = self.schema_validator.validate(data, "slide")
        elif output_type == "narration":
            narration_text = data if isinstance(data, str) else data.get("narration_text", "")
            slide = context.get("slide", {})
            target_seconds = context.get("target_seconds")
            report = self.narration_validator.validate(
                narration_text=narration_text,
                slide=slide,
                target_seconds=target_seconds,
            )
            # Return corrected narration dict with validation metadata
            corrected = self.narration_validator.correct_narration(
                data if isinstance(data, dict) else {"narration_text": data},
                report,
            )
            return corrected
        else:
            return data

        if hasattr(result, "fixes_applied") and result.fixes_applied and result.fixed_data is not None:
            return result.fixed_data
        return data
