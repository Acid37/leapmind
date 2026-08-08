"""
Quality validators for M5 AI备课 module.

Layers:
  QualityGuard       — [Layer 1] 硬性指标检测（零成本纯 Python 检查）
  SchemaValidator    — [Layer 2] structural JSON Schema validation
  NarrationValidator — [Layer 3] orality, consistency, duration checks
  ValidationPipeline — routes by output_type through validators
"""
from .lesson_plan_schema import LESSON_PLAN_SCHEMA, OUTLINE_SCHEMA, LESSON_DETAIL_SCHEMA
from .ppt_structure_schema import PPT_STRUCTURE_SCHEMA, PPT_SLIDE_SCHEMA
from .schema_validator import SchemaValidator, ValidationConfig, ValidationResult
from .narration_validator import (
    NarrationValidator,
    OralityScore,
    DurationScore,
    ConsistencyScore,
    ValidationReport,
)
from .quality_guard import QualityGuard, QualityReport
from .validation_pipeline import ValidationPipeline, PipelineResult

__all__ = [
    "LESSON_PLAN_SCHEMA",
    "OUTLINE_SCHEMA",
    "LESSON_DETAIL_SCHEMA",
    "PPT_STRUCTURE_SCHEMA",
    "PPT_SLIDE_SCHEMA",
    "SchemaValidator",
    "ValidationConfig",
    "ValidationResult",
    "NarrationValidator",
    "OralityScore",
    "DurationScore",
    "ConsistencyScore",
    "ValidationReport",
    "QualityGuard",
    "QualityReport",
    "ValidationPipeline",
    "PipelineResult",
]
