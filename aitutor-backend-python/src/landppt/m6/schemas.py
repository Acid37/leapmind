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


class BuildProfileRequest(BaseModel):
    """在线画像计算请求模型（profile-engine-contract.yaml）"""
    user_id: int
    events: list[dict]
    force_refresh: bool = False


class KnowledgeMasteryItem(BaseModel):
    """单个知识点的掌握程度（V6 格式）"""
    kp_id: int
    kp_name: str
    mastery_level: float
    review_count: int
    last_reviewed: Optional[str]


class ProfileData(BaseModel):
    """用户画像数据（V5 格式）"""
    user_id: int
    knowledge_mastery: list[KnowledgeMasteryItem]
    overall_mastery: float
    calculated_at: str


class BuildProfileResponse(BaseModel):
    """在线画像计算响应模型"""
    success: bool
    data: Optional[ProfileData]
    message: Optional[str]
