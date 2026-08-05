# Pydantic 响应模型
# 用于 FastAPI 自动校验响应格式、生成 OpenAPI 文档

from pydantic import BaseModel
from typing import Optional


class CalculateAllResponse(BaseModel):
    """全量复习计算接口响应模型"""
    status: str
    user_count: int
    message: str


class EventProcessResponse(BaseModel):
    """增量事件处理接口响应模型"""
    status: str
    processed_count: int
    message: str


class KnowledgeStatusItem(BaseModel):
    """单个知识点的掌握状态（用于知识雷达图）"""
    kp_id: int
    kp_name: str
    review_stage: int
    mastered: bool
    weakness_score: float
    next_review_at: Optional[str]


class KnowledgeStatusResponse(BaseModel):
    """知识掌握状态列表响应模型"""
    user_id: int
    items: list[KnowledgeStatusItem]


class TimelineItem(BaseModel):
    """单个学习事件（用于学习时间线）"""
    event_id: int
    module: str
    event_type: str
    created_at: str
    description: Optional[str]


class TimelineResponse(BaseModel):
    """学习时间线响应模型"""
    user_id: int
    events: list[TimelineItem]
