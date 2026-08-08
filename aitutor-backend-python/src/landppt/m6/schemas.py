# Pydantic 响应模型
# 用于 FastAPI 自动校验响应格式、生成 OpenAPI 文档

from typing import Literal, Optional

from pydantic import AliasChoices, BaseModel, ConfigDict, Field


# ---------------------------------------------------------------------------
# V5/V6 既有模型（批量任务、知识状态、时间线）——保持不变
# ---------------------------------------------------------------------------


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


# ---------------------------------------------------------------------------
# build-profile 契约模型（profile-engine-contract.yaml v1.0）
# ---------------------------------------------------------------------------


class ProfileEvent(BaseModel):
    """单个学习事件（契约 ProfileEvent）。

    kpId 允许为 null（未映射知识点的事件不得导致报错）；data 为自由对象。
    """
    model_config = ConfigDict(populate_by_name=True, extra="ignore")

    db_event_id: int = Field(alias="dbEventId")
    event_id: str = Field(alias="eventId")
    event_type: str = Field(alias="eventType")
    source_module: str = Field(alias="sourceModule")
    occurred_at: str = Field(alias="occurredAt")
    schema_version: str = Field(alias="schemaVersion")
    session_id: Optional[str] = Field(default=None, alias="sessionId")
    kp_id: Optional[int] = Field(default=None, alias="kpId")
    trace_id: Optional[str] = Field(default=None, alias="traceId")
    data: dict = Field(default_factory=dict)


class BuildProfileRequest(BaseModel):
    """在线画像计算请求（契约 BuildProfileRequest）。

    userId 为主字段（camelCase）；AliasChoices 兼容旧实现下发的 user_id。
    extra="ignore"：Java 按完整契约体发送，多余字段应被容忍。
    """
    model_config = ConfigDict(populate_by_name=True, extra="ignore")

    contract_version: Literal["1.0"] = Field(default="1.0", alias="contractVersion")
    request_id: str = Field(alias="requestId")
    user_id: int = Field(alias="userId", validation_alias=AliasChoices("userId", "user_id"))
    mode: Literal["INCREMENTAL", "FULL"] = Field(default="INCREMENTAL", alias="mode")
    base_profile_version: int = Field(alias="baseProfileVersion")
    from_event_id_exclusive: int = Field(alias="fromEventIdExclusive")
    event_watermark_inclusive: int = Field(alias="eventWatermarkInclusive")
    events: list[ProfileEvent] = Field(default_factory=list)


class KnowledgeMastery(BaseModel):
    """单个知识点掌握度（契约 KnowledgeMastery，字段名保持 camelCase）。"""
    model_config = ConfigDict(populate_by_name=True, extra="forbid")

    kp_id: int = Field(alias="kpId")
    mastery_score: float = Field(alias="masteryScore")
    mastery_status: Literal[
        "WEAK", "CONSOLIDATING", "BASIC_MASTERY", "MASTERED", "INSUFFICIENT_EVIDENCE"
    ] = Field(alias="masteryStatus")
    confidence: float = Field(alias="confidence")
    evidence_count: int = Field(alias="evidenceCount")
    trend: Optional[Literal["IMPROVING", "STABLE", "DECLINING"]] = Field(default=None, alias="trend")
    algorithm_version: str = Field(alias="algorithmVersion")
    window_start: Optional[str] = Field(default=None, alias="windowStart")
    window_end: Optional[str] = Field(default=None, alias="windowEnd")
    updated_at: str = Field(alias="updatedAt")


class RecentFocusItem(BaseModel):
    """近期关注点（契约 RecentFocus）。"""
    model_config = ConfigDict(populate_by_name=True, extra="forbid")

    kp_id: int = Field(alias="kpId")
    weight: float = Field(alias="weight")


class RecentConfusionItem(BaseModel):
    """近期混淆点（契约 RecentConfusion）。"""
    model_config = ConfigDict(populate_by_name=True, extra="forbid")

    kp_id: int = Field(alias="kpId")
    detail: str
    evidence_count: int = Field(alias="evidenceCount")
    confidence: float
    last_occurred_at: str = Field(alias="lastOccurredAt")


class Profile(BaseModel):
    """用户画像（契约 Profile，额外字段一律拒绝）。"""
    model_config = ConfigDict(populate_by_name=True, extra="forbid")

    grade: Optional[str] = Field(default=None, alias="grade")
    preferred_content_modes: list[Literal["text", "image", "audio", "video", "exercise"]] = Field(
        default_factory=list, alias="preferredContentModes"
    )
    preferred_explanation_style: Optional[str] = Field(default=None, alias="preferredExplanationStyle")
    learning_pace: Optional[Literal["slow", "moderate", "fast"]] = Field(default=None, alias="learningPace")
    recent_focus: list[RecentFocusItem] = Field(default_factory=list, alias="recentFocus")
    recent_confusions: list[RecentConfusionItem] = Field(default_factory=list, alias="recentConfusions")
    summary_profile: Optional[str] = Field(default=None, alias="summaryProfile")
    confidence: Optional[float] = Field(default=None, alias="confidence")


class ResponseBase(BaseModel):
    """三态共有的响应头（契约 ResponseBase）。"""
    model_config = ConfigDict(populate_by_name=True, extra="forbid")

    contract_version: Literal["1.0"] = Field(alias="contractVersion")
    request_id: str = Field(alias="requestId")
    user_id: int = Field(alias="userId")
    base_profile_version: int = Field(alias="baseProfileVersion")
    target_profile_version: int = Field(alias="targetProfileVersion")
    event_watermark_inclusive: int = Field(alias="eventWatermarkInclusive")
    status: Literal["READY", "INSUFFICIENT_DATA", "NO_CHANGE"]
    algorithm_version: str = Field(alias="algorithmVersion")
    evaluated_at: str = Field(alias="evaluatedAt")


class ReadyResponse(ResponseBase):
    """status=READY，targetProfileVersion=baseProfileVersion+1，带画像与掌握度。"""
    status: Literal["READY"] = "READY"
    profile: Profile
    knowledge_mastery: list[KnowledgeMastery] = Field(alias="knowledgeMastery")


class InsufficientResponse(ResponseBase):
    """status=INSUFFICIENT_DATA，targetProfileVersion=base+1，profile 不得出现，mastery 为空数组。"""
    status: Literal["INSUFFICIENT_DATA"] = "INSUFFICIENT_DATA"
    knowledge_mastery: list[KnowledgeMastery] = Field(default_factory=list, alias="knowledgeMastery")


class NoChangeResponse(ResponseBase):
    """status=NO_CHANGE，targetProfileVersion=base，profile/knowledgeMastery 不得出现。"""
    status: Literal["NO_CHANGE"] = "NO_CHANGE"


BuildProfileResponse = ReadyResponse | InsufficientResponse | NoChangeResponse
