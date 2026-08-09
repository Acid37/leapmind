"""M6 接口的 Pydantic 响应模型与画像引擎 v1.0 严格契约。"""

from datetime import datetime
from typing import Annotated, Any, Literal
from uuid import UUID

from pydantic import (
  BeforeValidator,
  BaseModel,
  ConfigDict,
  Field,
  StringConstraints,
  model_serializer,
  model_validator,
)


MAX_INT64 = 9_223_372_036_854_775_807
MAX_UINT32 = 4_294_967_295
CONTRACT_VERSION = "1.0"
MAX_EVENTS_PER_REQUEST = 1_000

ContentMode = Literal["text", "image", "audio", "video", "exercise"]
ConfusionTag = Literal[
  "concept_unclear",
  "formula_confusion",
  "step_unclear",
  "application_difficulty",
  "careless_error",
]
Identifier = Annotated[str, StringConstraints(pattern=r"^[A-Za-z0-9][A-Za-z0-9._:-]{0,63}$")]
StrictFraction = Annotated[float, Field(strict=True, ge=0, le=1)]


def validateDatetimeInput(value: Any) -> str | datetime:
  """仅接受契约字符串或内部代码已构造的 datetime。"""
  if not isinstance(value, (str, datetime)):
    raise ValueError("日期时间必须是 ISO 8601 字符串或 datetime 对象")
  return value


ContractDateTime = Annotated[datetime, BeforeValidator(validateDatetimeInput)]

EVENT_SOURCES = {
  "answer_question": "M1",
  "finish_practice": "M1",
  "request_explanation": "M2",
  "explanation_feedback": "M2",
  "weak_point_changed": "M3",
  "lecture_interact": "M4",
  "lesson_material_used": "M5",
  "ask_doubt": "M7",
  "mark_reviewed": "M6",
  "preference_changed": "M6",
  "wrong_question_changed": "M1",
}


class KnowledgeStatusItem(BaseModel):
  """单个知识点的掌握状态，用于知识雷达图。"""

  kp_id: int
  kp_name: str
  review_stage: int
  mastered: bool
  weakness_score: float
  next_review_at: str | None


class KnowledgeStatusResponse(BaseModel):
  """知识掌握状态列表响应。"""

  user_id: int
  items: list[KnowledgeStatusItem]


class TimelineItem(BaseModel):
  """学习时间线中的单个事件。"""

  event_id: int
  module: str
  event_type: str
  created_at: str
  description: str | None


class TimelineResponse(BaseModel):
  """学习时间线响应。"""

  user_id: int
  events: list[TimelineItem]


class StrictContractModel(BaseModel):
  """拒绝契约未声明字段的基础模型。"""

  model_config = ConfigDict(extra="forbid")


class AnswerQuestionData(StrictContractModel):
  """答题事件数据。"""

  isCorrect: bool = Field(strict=True)
  difficulty: int = Field(strict=True, ge=1, le=5)
  timeSpentSec: int = Field(strict=True, ge=0, le=86_400)
  hintCount: int = Field(strict=True, ge=0, le=100)
  confusionTag: ConfusionTag | None = None

  @model_validator(mode="before")
  @classmethod
  def rejectNullConfusionTag(cls, data: Any) -> Any:
    """允许省略困惑标签，但字段一旦出现就不得为 null。"""
    if isinstance(data, dict) and "confusionTag" in data and data["confusionTag"] is None:
      raise ValueError("confusionTag 可以省略，但不得为 null")
    return data


class FinishPracticeData(StrictContractModel):
  """完成练习事件数据。"""

  questionCount: int = Field(strict=True, ge=1, le=10_000)
  accuracy: StrictFraction
  durationSec: int = Field(strict=True, ge=0, le=86_400)


class RequestExplanationData(StrictContractModel):
  """请求讲解事件数据。"""

  explainId: Identifier
  reasonTag: Literal[
    "WRONG_ANSWER",
    "REPEATED_ERROR",
    "USER_REQUEST",
    "LOW_CONFIDENCE",
    "REVIEW_NEEDED",
  ]


class ExplanationFeedbackData(StrictContractModel):
  """讲解反馈事件数据。"""

  explainId: Identifier
  feedback: Literal["understood", "partly_understood", "still_confused"]
  repeatCount: int = Field(strict=True, ge=0, le=100)


class WeakPointChangedData(StrictContractModel):
  """薄弱点变化事件数据。"""

  oldScore: StrictFraction
  newScore: StrictFraction
  reason: Literal["ACCURACY_DROP", "REPEATED_ERROR", "TEACHER_MARKED", "RECALCULATED"]


class LectureInteractData(StrictContractModel):
  """课堂互动事件数据。"""

  lectureId: Identifier
  chapterId: Identifier
  action: Literal["pause", "resume", "replay", "ask", "complete"]


class LessonMaterialUsedData(StrictContractModel):
  """课程材料使用事件数据。"""

  contentId: Identifier
  materialType: ContentMode
  result: Literal["completed", "skipped", "helpful", "not_helpful"]


class AskDoubtData(StrictContractModel):
  """提问事件数据。"""

  topic: str
  confusionTag: ConfusionTag
  isFollowUp: bool = Field(strict=True)

  @model_validator(mode="after")
  def validateTopic(self) -> "AskDoubtData":
    """按 Unicode 码点校验去除首尾空白后的主题。"""
    normalizedTopic = self.topic.strip()
    if not normalizedTopic or len(normalizedTopic) > 120:
      raise ValueError("topic 去除首尾空白后必须为 1 至 120 个 Unicode 码点")
    return self


class MarkReviewedData(StrictContractModel):
  """复习标记事件数据。"""

  result: Literal[
    "correct_without_hint",
    "correct_with_hint",
    "incorrect",
    "still_confused",
    "postponed",
  ]
  timeSpentSec: int = Field(strict=True, ge=0, le=86_400)
  hintCount: int = Field(strict=True, ge=0, le=100)


class PreferenceChangedData(StrictContractModel):
  """显式学习偏好变化事件数据。"""

  preferenceKey: Literal["content_mode", "explanation_style", "learning_pace"]
  preferenceValue: str

  @model_validator(mode="after")
  def validatePreferencePair(self) -> "PreferenceChangedData":
    """确保偏好值属于对应偏好键的枚举集合。"""
    allowedValues = {
      "content_mode": {"text", "image", "audio", "video", "exercise"},
      "explanation_style": {"step_by_step", "example_first", "concise", "detailed"},
      "learning_pace": {"slow", "moderate", "fast"},
    }
    if self.preferenceValue not in allowedValues[self.preferenceKey]:
      raise ValueError("preferenceValue 与 preferenceKey 不匹配")
    return self


class WrongQuestionChangedData(StrictContractModel):
  """M1 错题本状态变更事件数据。"""

  questionId: int = Field(strict=True, ge=1, le=MAX_INT64)
  status: Literal["UNRESOLVED", "REVIEWING", "RESOLVED"]
  wrongCount: int = Field(strict=True, ge=1, le=9999)


EVENT_DATA_MODELS: dict[str, type[StrictContractModel]] = {
  "answer_question": AnswerQuestionData,
  "finish_practice": FinishPracticeData,
  "request_explanation": RequestExplanationData,
  "explanation_feedback": ExplanationFeedbackData,
  "weak_point_changed": WeakPointChangedData,
  "lecture_interact": LectureInteractData,
  "lesson_material_used": LessonMaterialUsedData,
  "ask_doubt": AskDoubtData,
  "mark_reviewed": MarkReviewedData,
  "preference_changed": PreferenceChangedData,
  "wrong_question_changed": WrongQuestionChangedData,
}

EventType = Literal[
  "answer_question",
  "finish_practice",
  "request_explanation",
  "explanation_feedback",
  "weak_point_changed",
  "lecture_interact",
  "lesson_material_used",
  "ask_doubt",
  "mark_reviewed",
  "preference_changed",
  "wrong_question_changed",
]


def requireTimezone(value: datetime, fieldName: str) -> None:
  """拒绝不带 UTC 偏移信息的时间。"""
  if value.tzinfo is None or value.utcoffset() is None:
    raise ValueError(f"{fieldName} 必须包含时区信息")


class ProfileEvent(StrictContractModel):
  """Java 侧传入的单个已提交画像事件。"""

  dbEventId: int = Field(strict=True, ge=1, le=MAX_INT64)
  eventId: str = Field(min_length=1, max_length=64)
  eventType: EventType
  sourceModule: Literal["M1", "M2", "M3", "M4", "M5", "M6", "M7"]
  occurredAt: ContractDateTime
  schemaVersion: Literal[CONTRACT_VERSION]
  sessionId: str | None = None
  kpId: int | None = Field(default=None, strict=True, ge=1, le=MAX_INT64)
  traceId: str | None = None
  data: dict[str, Any]

  @model_validator(mode="before")
  @classmethod
  def rejectNullOptionalFields(cls, data: Any) -> Any:
    """允许省略可选标识，但拒绝外部 JSON 显式传入 null。"""
    if isinstance(data, dict):
      for fieldName in ("sessionId", "kpId", "traceId"):
        if fieldName in data and data[fieldName] is None:
          raise ValueError(f"{fieldName} 可以省略，但不得为 null")
    return data

  @model_validator(mode="after")
  def validateEventContract(self) -> "ProfileEvent":
    """联合校验事件来源、知识点、时间和事件 data。"""
    requireTimezone(self.occurredAt, "occurredAt")
    if not self.eventId.strip():
      raise ValueError("eventId 不得为空")
    if self.sourceModule != EVENT_SOURCES[self.eventType]:
      raise ValueError("sourceModule 与 eventType 不匹配")
    if self.eventType == "answer_question" and self.kpId is None:
      raise ValueError("answer_question 必须提供 kpId")
    # 使用对应的严格子模型完成 data 必填、枚举、范围及未知字段校验。
    EVENT_DATA_MODELS[self.eventType].model_validate(self.data)
    return self

  @model_serializer(mode="wrap")
  def serializeWithoutNullOptionals(self, serializer: Any) -> dict[str, Any]:
    """序列化时不生成契约禁止的可选 null 字段。"""
    output = serializer(self)
    for fieldName in ("sessionId", "kpId", "traceId"):
      if output.get(fieldName) is None:
        output.pop(fieldName, None)
    return output


class BuildProfileRequest(StrictContractModel):
  """画像引擎 v1.0 构建请求。"""

  contractVersion: Literal[CONTRACT_VERSION]
  requestId: UUID
  userId: int = Field(strict=True, ge=1, le=MAX_INT64)
  mode: Literal["INCREMENTAL", "FULL"]
  baseProfileVersion: int = Field(strict=True, ge=0, le=MAX_INT64)
  fromEventIdExclusive: int = Field(strict=True, ge=0, le=MAX_INT64)
  eventWatermarkInclusive: int = Field(strict=True, ge=0, le=MAX_INT64)
  events: list[ProfileEvent] = Field(max_length=MAX_EVENTS_PER_REQUEST)

  @model_validator(mode="after")
  def validateEventWindow(self) -> "BuildProfileRequest":
    """确保批次事件位于水位窗口内并按数据库编号严格递增。"""
    if self.fromEventIdExclusive > self.eventWatermarkInclusive:
      raise ValueError("fromEventIdExclusive 不得大于 eventWatermarkInclusive")
    previousId = self.fromEventIdExclusive
    for event in self.events:
      if event.dbEventId <= previousId:
        raise ValueError("events 的 dbEventId 必须从 fromEventIdExclusive 之后严格递增")
      if event.dbEventId > self.eventWatermarkInclusive:
        raise ValueError("事件 dbEventId 不得超过 eventWatermarkInclusive")
      previousId = event.dbEventId
    return self


class ResponseBase(StrictContractModel):
  """三态画像引擎响应的共享字段。"""

  contractVersion: Literal[CONTRACT_VERSION]
  requestId: UUID
  userId: int = Field(strict=True, ge=1, le=MAX_INT64)
  baseProfileVersion: int = Field(strict=True, ge=0, le=MAX_INT64)
  targetProfileVersion: int = Field(strict=True, ge=0, le=MAX_INT64)
  eventWatermarkInclusive: int = Field(strict=True, ge=0, le=MAX_INT64)
  status: Literal["READY", "INSUFFICIENT_DATA", "NO_CHANGE"]
  algorithmVersion: str = Field(min_length=1, max_length=30)
  evaluatedAt: ContractDateTime

  @model_validator(mode="after")
  def validateEvaluatedAt(self) -> "ResponseBase":
    """确保算法评估时间可以无歧义地跨服务传输。"""
    requireTimezone(self.evaluatedAt, "evaluatedAt")
    return self


class RecentFocus(StrictContractModel):
  """画像中的近期关注知识点。"""

  kpId: int = Field(strict=True, ge=1, le=MAX_INT64)
  weight: StrictFraction


class RecentConfusion(StrictContractModel):
  """画像中的近期困惑证据。"""

  kpId: int = Field(strict=True, ge=1, le=MAX_INT64)
  detail: str = Field(max_length=120)
  evidenceCount: int = Field(strict=True, ge=0, le=MAX_UINT32)
  confidence: StrictFraction
  lastOccurredAt: ContractDateTime

  @model_validator(mode="after")
  def validateLastOccurredAt(self) -> "RecentConfusion":
    """确保困惑证据时间包含时区。"""
    requireTimezone(self.lastOccurredAt, "lastOccurredAt")
    return self


class Profile(StrictContractModel):
  """READY 响应中的用户画像。"""

  grade: str | None = Field(default=None, max_length=30)
  preferredContentModes: list[ContentMode] = Field(max_length=5)
  preferredExplanationStyle: str | None = Field(default=None, max_length=50)
  learningPace: Literal["slow", "moderate", "fast"] | None = None
  recentFocus: list[RecentFocus] = Field(max_length=20)
  recentConfusions: list[RecentConfusion] = Field(max_length=20)
  summaryProfile: str | None = Field(default=None, max_length=16_383)
  confidence: Annotated[float, Field(strict=True, ge=0, le=1, multiple_of=0.001)] | None = None


class KnowledgeMastery(StrictContractModel):
  """单个知识点的算法掌握度。"""

  kpId: int = Field(strict=True, ge=1, le=MAX_INT64)
  masteryScore: float = Field(strict=True, ge=0, le=1, multiple_of=0.0001)
  masteryStatus: Literal[
    "WEAK",
    "CONSOLIDATING",
    "BASIC_MASTERY",
    "MASTERED",
    "INSUFFICIENT_EVIDENCE",
  ]
  confidence: float = Field(strict=True, ge=0, le=1, multiple_of=0.0001)
  evidenceCount: int = Field(strict=True, ge=0, le=MAX_UINT32)
  trend: Literal["IMPROVING", "STABLE", "DECLINING"] | None = None
  algorithmVersion: str = Field(min_length=1, max_length=30)
  windowStart: ContractDateTime | None = None
  windowEnd: ContractDateTime | None = None
  updatedAt: ContractDateTime

  @model_validator(mode="before")
  @classmethod
  def rejectNullWindows(cls, data: Any) -> Any:
    """允许省略窗口边界，但拒绝 YAML 未声明的显式 null。"""
    if isinstance(data, dict):
      for fieldName in ("windowStart", "windowEnd"):
        if fieldName in data and data[fieldName] is None:
          raise ValueError(f"{fieldName} 可以省略，但不得为 null")
    return data

  @model_validator(mode="after")
  def validateMasteryTimes(self) -> "KnowledgeMastery":
    """校验契约中所有已提供的掌握度时间。"""
    if self.windowStart is not None:
      requireTimezone(self.windowStart, "windowStart")
    if self.windowEnd is not None:
      requireTimezone(self.windowEnd, "windowEnd")
    requireTimezone(self.updatedAt, "updatedAt")
    return self


class ReadyResponse(ResponseBase):
  """数据充分且生成新画像的响应。"""

  status: Literal["READY"]
  profile: Profile
  knowledgeMastery: list[KnowledgeMastery]

  @model_validator(mode="after")
  def validateTargetVersion(self) -> "ReadyResponse":
    """READY 必须把画像版本推进一版。"""
    if self.targetProfileVersion != self.baseProfileVersion + 1:
      raise ValueError("READY 的 targetProfileVersion 必须等于 baseProfileVersion + 1")
    return self


class InsufficientDataResponse(ResponseBase):
  """证据不足但推进画像版本的响应。"""

  status: Literal["INSUFFICIENT_DATA"]
  knowledgeMastery: list[KnowledgeMastery] = Field(max_length=0)

  @model_validator(mode="after")
  def validateTargetVersion(self) -> "InsufficientDataResponse":
    """INSUFFICIENT_DATA 必须推进一版且不返回掌握度。"""
    if self.targetProfileVersion != self.baseProfileVersion + 1:
      raise ValueError("INSUFFICIENT_DATA 的 targetProfileVersion 必须等于 baseProfileVersion + 1")
    return self


class NoChangeResponse(ResponseBase):
  """没有新证据且不推进画像版本的响应。"""

  status: Literal["NO_CHANGE"]

  @model_validator(mode="after")
  def validateTargetVersion(self) -> "NoChangeResponse":
    """NO_CHANGE 必须保持画像版本不变。"""
    if self.targetProfileVersion != self.baseProfileVersion:
      raise ValueError("NO_CHANGE 的 targetProfileVersion 必须等于 baseProfileVersion")
    return self


BuildProfileResponse = Annotated[
  ReadyResponse | InsufficientDataResponse | NoChangeResponse,
  Field(discriminator="status"),
]
