"""
数据模型 — API 请求/响应校验，字段自动转 camelCase
"""
from datetime import datetime
from typing import Optional
from pydantic import BaseModel, Field, ConfigDict


def _to_camel(name: str) -> str:
    """snake_case → camelCase:  kp_id → kpId"""
    words = name.split("_")
    return words[0] + "".join(w.capitalize() for w in words[1:])


class _AliasModel(BaseModel):
    model_config = ConfigDict(
        alias_generator=_to_camel, populate_by_name=True, from_attributes=True
    )


class SubWeakPoint(_AliasModel):
    kp_id: int
    kp_name: str
    weakness_score: float


class WeakPoint(_AliasModel):
    kp_id: int
    kp_name: str
    weakness_score: float = Field(..., ge=0, le=1)
    error_count: int
    total_attempts: int
    error_rate: float = Field(..., ge=0, le=1)
    trend: str
    last_error_at: Optional[datetime] = None
    recommended_questions: list[int] = []
    sub_weak_points: list[SubWeakPoint] = []


class WeakPointsResponse(_AliasModel):
    user_id: int
    weak_points: list[WeakPoint]
    updated_at: datetime
    overall_assessment: str = ""


class QuestionItem(_AliasModel):
    id: int
    kp_id: int
    content: str
    difficulty: float


class RecommendQuestionsResponse(_AliasModel):
    kp_name: str
    questions: list[QuestionItem]


class IncrementalUpdateRequest(_AliasModel):
    user_id: int
    kp_ids: list[int]


class IncrementalUpdateResponse(_AliasModel):
    status: str = "ok"
    reason: Optional[str] = None
    updated_kp_ids: list[int] = []
    updated_count: int = 0


# ── 知识图谱 ──

class GraphNode(_AliasModel):
    id: int
    name: str
    weakness_score: float = 0


class GraphEdge(_AliasModel):
    source: int
    target: int


class KnowledgeGraphResponse(_AliasModel):
    nodes: list[GraphNode]
    edges: list[GraphEdge]


# ── 统一错误响应 ──

class ErrorResponse(_AliasModel):
    status: str = "error"
    message: str
    detail: Optional[str] = None
    error_code: Optional[str] = None
