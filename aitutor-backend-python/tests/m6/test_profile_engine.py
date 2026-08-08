"""M6 无状态画像引擎入口测试。"""

from collections import Counter
from copy import deepcopy
from datetime import datetime, timedelta, timezone
import json
from pathlib import Path
from types import SimpleNamespace

import pytest

from landppt.m6.profile_engine import (
  ALGORITHM_VERSION,
  OpenAISummaryClient,
  SUMMARY_PROMPT_VERSION,
  LearningSituation,
  ProfileRequestError,
  buildSummaryMessages,
  buildProfile,
  calculateAnswerScore,
  calculateLearningSituation,
  calculateLearningSituationFeatures,
  calculatePreferences,
  calculateProfileConfidence,
  calculateQuestionUnderstanding,
  calculateWeakPointScore,
  collectConfusionContributions,
  extractConfusionType,
  inferLearningPace,
)
from landppt.m6.schemas import MAX_INT64, BuildProfileRequest
from tests.m6.conftest import makeEvent, makeRequest


EVALUATED_AT = datetime(2026, 8, 5, 12, tzinfo=timezone.utc)


@pytest.fixture(autouse=True)
def clearSummaryEnvironment(monkeypatch: pytest.MonkeyPatch) -> None:
  """确保画像引擎单元测试不会读取开发机配置或发起网络请求。"""
  for name in ("OPENAI_API_KEY", "OPENAI_BASE_URL", "OPENAI_MODEL", "PROFILE_LLM_TIMEOUT_SECONDS"):
    monkeypatch.delenv(name, raising=False)


class FakeSummaryClient:
  """记录脱敏请求并返回测试指定的摘要。"""

  def __init__(self, result: str | None = None, error: Exception | None = None) -> None:
    self.result = result
    self.error = error
    self.payloads: list[dict] = []

  async def createSummary(self, payload: dict) -> str | None:
    self.payloads.append(payload)
    if self.error is not None:
      raise self.error
    return self.result


def answerEvent(
  dbEventId: int,
  *,
  kpId: int = 101,
  isCorrect: bool = True,
  difficulty: int = 3,
  hintCount: int = 0,
  occurredAt: datetime = EVALUATED_AT,
) -> dict:
  """创建一条 M1 题目级直接表现证据。"""
  return makeEvent(
    dbEventId,
    kpId=kpId,
    occurredAt=occurredAt.isoformat(),
    data={
      "isCorrect": isCorrect,
      "difficulty": difficulty,
      "timeSpentSec": 30,
      "hintCount": hintCount,
    },
  )


def weakEvent(
  dbEventId: int,
  *,
  kpId: int = 101,
  oldScore: float = 0.2,
  newScore: float = 0.7,
  occurredAt: datetime = EVALUATED_AT,
) -> dict:
  """创建一条 M3 薄弱分变化证据。"""
  return makeEvent(
    dbEventId,
    eventType="weak_point_changed",
    kpId=kpId,
    occurredAt=occurredAt.isoformat(),
    data={"oldScore": oldScore, "newScore": newScore, "reason": "RECALCULATED"},
  )


def reviewEvent(
  dbEventId: int,
  result: str,
  *,
  kpId: int = 101,
  hintCount: int = 0,
  occurredAt: datetime = EVALUATED_AT,
) -> dict:
  """创建一条 M6 复习事件。"""
  return makeEvent(
    dbEventId,
    eventType="mark_reviewed",
    kpId=kpId,
    occurredAt=occurredAt.isoformat(),
    data={"result": result, "timeSpentSec": 20, "hintCount": hintCount},
  )


def fillerEvent(dbEventId: int) -> dict:
  """创建不参与知识点计算但可用于全局发布门槛的 M4 事件。"""
  event = makeEvent(
    dbEventId,
    eventType="lecture_interact",
    data={"lectureId": "lecture-1", "chapterId": "chapter-1", "action": "pause"},
  )
  event.pop("kpId")
  return event


def preferenceEvent(
  dbEventId: int,
  preferenceKey: str,
  preferenceValue: str,
  *,
  occurredAt: datetime = EVALUATED_AT,
) -> dict:
  """创建一条显式偏好变化事件。"""
  event = makeEvent(
    dbEventId,
    eventType="preference_changed",
    occurredAt=occurredAt.isoformat(),
    data={"preferenceKey": preferenceKey, "preferenceValue": preferenceValue},
  )
  event.pop("kpId")
  return event


def finishPracticeEvent(
  dbEventId: int,
  accuracy: float,
  *,
  sessionId: str | None = "session-1",
) -> dict:
  """创建一条练习总体正确率事件。"""
  event = makeEvent(
    dbEventId,
    eventType="finish_practice",
    data={"questionCount": 10, "accuracy": accuracy, "durationSec": 60},
  )
  event.pop("kpId")
  if sessionId is None:
    event.pop("sessionId")
  else:
    event["sessionId"] = sessionId
  return event


def doubtEvent(
  dbEventId: int,
  *,
  kpId: int | None = 101,
  topic: str = "为什么这里要换元",
  confusionTag: str = "concept_unclear",
  isFollowUp: bool = False,
  occurredAt: datetime = EVALUATED_AT,
) -> dict:
  """创建一条 M7 提问证据。"""
  event = makeEvent(
    dbEventId,
    eventType="ask_doubt",
    kpId=kpId,
    occurredAt=occurredAt.isoformat(),
    data={
      "topic": topic,
      "confusionTag": confusionTag,
      "isFollowUp": isFollowUp,
    },
  )
  if kpId is None:
    event.pop("kpId")
  return event


def explanationEvent(
  dbEventId: int,
  *,
  eventType: str,
  explainId: str,
  kpId: int | None = 101,
  feedback: str = "still_confused",
  occurredAt: datetime = EVALUATED_AT,
) -> dict:
  """创建 M2 讲解请求或反馈事件。"""
  data = (
    {"explainId": explainId, "reasonTag": "USER_REQUEST"}
    if eventType == "request_explanation"
    else {"explainId": explainId, "feedback": feedback, "repeatCount": 0}
  )
  event = makeEvent(
    dbEventId,
    eventType=eventType,
    kpId=kpId,
    occurredAt=occurredAt.isoformat(),
    data=data,
  )
  if kpId is None:
    event.pop("kpId")
  return event


def readyRequest(events: list[dict]) -> BuildProfileRequest:
  """补足全局五事件门槛并构造 READY 请求。"""
  completed = deepcopy(events)
  nextId = max((event["dbEventId"] for event in completed), default=0) + 1
  while len(completed) < 5:
    completed.append(fillerEvent(nextId))
    nextId += 1
  completed.sort(key=lambda event: event["dbEventId"])
  return BuildProfileRequest.model_validate(makeRequest(completed))


async def calculateMastery(events: list[dict], kpIndex: int = 0):
  """执行画像计算并返回指定知识点结果。"""
  response = await buildProfile(readyRequest(events), evaluatedAt=EVALUATED_AT)
  assert response.status == "READY"
  return response.knowledgeMastery[kpIndex]


def buildRequest(eventCount: int, *, occurredAt: datetime = EVALUATED_AT) -> BuildProfileRequest:
  """创建包含指定数量有效事件的请求。"""
  events = [makeEvent(index, occurredAt=occurredAt.isoformat()) for index in range(1, eventCount + 1)]
  return BuildProfileRequest.model_validate(makeRequest(events))


@pytest.mark.asyncio
async def test_empty_window_returns_no_change_without_version_increment() -> None:
  request = BuildProfileRequest.model_validate(makeRequest([]))

  response = await buildProfile(request, evaluatedAt=EVALUATED_AT)

  assert ALGORITHM_VERSION == "m6-profile-v1.0.0"
  assert response.status == "NO_CHANGE"
  assert response.targetProfileVersion == request.baseProfileVersion
  assert response.evaluatedAt == EVALUATED_AT
  assert response.model_dump(exclude_none=True) == {
    "contractVersion": "1.0",
    "requestId": request.requestId,
    "userId": request.userId,
    "baseProfileVersion": request.baseProfileVersion,
    "targetProfileVersion": request.baseProfileVersion,
    "eventWatermarkInclusive": request.eventWatermarkInclusive,
    "status": "NO_CHANGE",
    "algorithmVersion": ALGORITHM_VERSION,
    "evaluatedAt": EVALUATED_AT,
  }


@pytest.mark.asyncio
@pytest.mark.parametrize("eventKind", ["m5", "expired"])
async def test_ignored_or_expired_events_return_no_change(eventKind: str) -> None:
  if eventKind == "m5":
    event = makeEvent(
      eventType="lesson_material_used",
      sourceModule="M5",
      data={"contentId": "content-1", "materialType": "text", "result": "completed"},
    )
    event.pop("kpId")
  else:
    event = makeEvent(occurredAt=(EVALUATED_AT - timedelta(days=90, microseconds=1)).isoformat())
  request = BuildProfileRequest.model_validate(makeRequest([event]))

  response = await buildProfile(request, evaluatedAt=EVALUATED_AT)

  assert response.status == "NO_CHANGE"
  assert "profile" not in response.model_dump(exclude_none=True)
  assert "knowledgeMastery" not in response.model_dump(exclude_none=True)


@pytest.mark.asyncio
@pytest.mark.parametrize("eventCount", [1, 4])
async def test_fewer_than_five_events_returns_insufficient_data(eventCount: int) -> None:
  request = buildRequest(eventCount)

  response = await buildProfile(request, evaluatedAt=EVALUATED_AT)
  output = response.model_dump(exclude_none=True)

  assert response.status == "INSUFFICIENT_DATA"
  assert response.targetProfileVersion == request.baseProfileVersion + 1
  assert output["knowledgeMastery"] == []
  assert "profile" not in output


@pytest.mark.asyncio
async def test_five_m1_events_returns_ready_profile_with_mastery() -> None:
  request = buildRequest(5)

  response = await buildProfile(request, evaluatedAt=EVALUATED_AT)
  output = response.model_dump(exclude_none=True)

  assert response.status == "READY"
  assert response.targetProfileVersion == request.baseProfileVersion + 1
  summary = output["profile"].pop("summaryProfile")
  assert 150 <= len(summary) <= 300
  assert output["profile"] == {
    "preferredContentModes": [],
    "learningPace": "moderate",
    "recentFocus": [{"kpId": 101, "weight": 1.0}],
    "recentConfusions": [],
    "confidence": 0.385,
  }
  assert len(output["knowledgeMastery"]) == 1
  assert output["knowledgeMastery"][0]["kpId"] == 101
  assert output["knowledgeMastery"][0]["masteryStatus"] == "BASIC_MASTERY"


@pytest.mark.asyncio
async def test_ninety_day_boundary_is_inclusive() -> None:
  boundary = EVALUATED_AT - timedelta(days=90)
  events = [
    makeEvent(1, occurredAt=(boundary - timedelta(microseconds=1)).isoformat()),
    makeEvent(2, occurredAt=boundary.isoformat()),
  ]
  request = BuildProfileRequest.model_validate(makeRequest(events))

  response = await buildProfile(request, evaluatedAt=EVALUATED_AT)

  assert response.status == "INSUFFICIENT_DATA"


@pytest.mark.asyncio
async def test_build_profile_does_not_modify_request() -> None:
  request = buildRequest(5)
  before = deepcopy(request.model_dump())

  await buildProfile(request, evaluatedAt=EVALUATED_AT)

  assert request.model_dump() == before


@pytest.mark.asyncio
async def test_evaluated_at_is_normalized_to_utc() -> None:
  request = BuildProfileRequest.model_validate(makeRequest([]))
  chinaTime = datetime(2026, 8, 5, 20, tzinfo=timezone(timedelta(hours=8)))

  response = await buildProfile(request, evaluatedAt=chinaTime)

  assert response.evaluatedAt == EVALUATED_AT
  assert response.evaluatedAt.tzinfo == timezone.utc


@pytest.mark.asyncio
async def test_naive_evaluated_at_is_rejected_with_chinese_error() -> None:
  request = BuildProfileRequest.model_validate(makeRequest([]))

  with pytest.raises(ProfileRequestError, match="evaluatedAt 必须包含时区信息"):
    await buildProfile(request, evaluatedAt=datetime(2026, 8, 5, 12))


@pytest.mark.asyncio
async def test_max_profile_version_allows_no_change() -> None:
  request = BuildProfileRequest.model_validate(makeRequest([], baseProfileVersion=MAX_INT64))

  response = await buildProfile(request, evaluatedAt=EVALUATED_AT)

  assert response.status == "NO_CHANGE"
  assert response.targetProfileVersion == MAX_INT64


@pytest.mark.asyncio
async def test_max_minus_one_profile_version_can_advance() -> None:
  request = buildRequest(1)
  request = request.model_copy(update={"baseProfileVersion": MAX_INT64 - 1})

  response = await buildProfile(request, evaluatedAt=EVALUATED_AT)

  assert response.status == "INSUFFICIENT_DATA"
  assert response.targetProfileVersion == MAX_INT64


@pytest.mark.asyncio
async def test_max_profile_version_with_usable_event_fails_clearly() -> None:
  request = buildRequest(1)
  request = request.model_copy(update={"baseProfileVersion": MAX_INT64})

  with pytest.raises(ProfileRequestError, match="画像版本已达到上限，无法继续推进"):
    await buildProfile(request, evaluatedAt=EVALUATED_AT)


@pytest.mark.asyncio
@pytest.mark.parametrize(
  ("answerCount", "correctCount", "expectedStatus"),
  [
    (10, 10, "BASIC_MASTERY"),
    (15, 12, "BASIC_MASTERY"),
    (11, 11, "MASTERED"),
  ],
)
async def test_mastered_uses_strict_m1_answer_count_and_accuracy_gates(
  answerCount: int,
  correctCount: int,
  expectedStatus: str,
) -> None:
  events = [answerEvent(index, isCorrect=index <= correctCount) for index in range(1, answerCount + 1)]

  mastery = await calculateMastery(events)

  assert mastery.masteryStatus == expectedStatus


@pytest.mark.asyncio
async def test_low_m1_score_without_m3_is_consolidating_not_weak() -> None:
  mastery = await calculateMastery([answerEvent(index, isCorrect=False) for index in range(1, 6)])

  assert mastery.masteryScore == 0
  assert mastery.masteryStatus == "CONSOLIDATING"


@pytest.mark.asyncio
async def test_m3_only_uses_latest_score_and_can_create_weak_status() -> None:
  events = [weakEvent(index, newScore=0.1) for index in range(1, 5)]
  events.append(weakEvent(5, newScore=0.7))

  mastery = await calculateMastery(events)

  assert mastery.masteryScore == 0.3
  assert mastery.masteryStatus == "WEAK"
  assert mastery.evidenceCount == 5


@pytest.mark.asyncio
async def test_m3_only_below_weak_boundary_is_insufficient() -> None:
  events = [weakEvent(index, newScore=0.8) for index in range(1, 5)]
  events.append(weakEvent(5, newScore=0.5999))

  mastery = await calculateMastery(events)

  assert mastery.masteryScore == 0.4001
  assert mastery.masteryStatus == "INSUFFICIENT_EVIDENCE"


@pytest.mark.asyncio
@pytest.mark.parametrize(
  ("answerTime", "weakTime", "expectedStatus"),
  [
    (EVALUATED_AT, EVALUATED_AT - timedelta(days=1), "MASTERED"),
    (EVALUATED_AT - timedelta(days=1), EVALUATED_AT, "WEAK"),
  ],
)
async def test_latest_source_resolves_mastered_and_weak_conflict(
  answerTime: datetime,
  weakTime: datetime,
  expectedStatus: str,
) -> None:
  events = [answerEvent(index, occurredAt=answerTime) for index in range(1, 12)]
  events.append(weakEvent(12, occurredAt=weakTime))

  mastery = await calculateMastery(events)

  assert mastery.masteryStatus == expectedStatus
  assert mastery.masteryScore == 1


@pytest.mark.asyncio
async def test_equal_time_mastered_and_weak_conflict_prefers_weak_and_reduces_confidence() -> None:
  events = [answerEvent(index) for index in range(1, 12)]
  events.append(weakEvent(12))

  mastery = await calculateMastery(events)

  assert mastery.masteryStatus == "WEAK"
  assert mastery.masteryScore == 1
  assert mastery.confidence == 0.45


@pytest.mark.asyncio
async def test_m3_does_not_change_score_when_direct_evidence_exists() -> None:
  events = [
    answerEvent(index, occurredAt=EVALUATED_AT - timedelta(days=1))
    for index in range(1, 6)
  ]
  events.append(weakEvent(6, newScore=0.9))

  mastery = await calculateMastery(events)

  assert mastery.masteryScore == 1
  assert mastery.masteryStatus == "WEAK"


@pytest.mark.asyncio
async def test_m6_result_mapping_independence_and_postponed_ignored() -> None:
  events = [
    reviewEvent(1, "correct_without_hint", hintCount=4),
    reviewEvent(2, "correct_with_hint", hintCount=2),
    reviewEvent(3, "incorrect", hintCount=1),
    reviewEvent(4, "still_confused", hintCount=4),
    reviewEvent(5, "postponed", hintCount=0),
  ]

  mastery = await calculateMastery(events)

  assert mastery.masteryScore == 0.4779
  assert mastery.evidenceCount == 4
  assert mastery.masteryStatus == "INSUFFICIENT_EVIDENCE"


@pytest.mark.asyncio
async def test_score_uses_four_components_with_exact_weights() -> None:
  events = [
    answerEvent(1, isCorrect=False, difficulty=1, hintCount=4, occurredAt=EVALUATED_AT - timedelta(days=30)),
    answerEvent(2, isCorrect=True, difficulty=5, hintCount=0),
  ]

  mastery = await calculateMastery(events)

  assert mastery.masteryScore == 0.575


@pytest.mark.asyncio
async def test_score_renormalizes_missing_difficulty_component() -> None:
  events = [reviewEvent(1, "correct_without_hint"), reviewEvent(2, "incorrect")]

  mastery = await calculateMastery(events)

  assert mastery.masteryScore == 0.5


@pytest.mark.asyncio
async def test_recency_uses_thirty_day_half_life() -> None:
  events = [
    reviewEvent(1, "incorrect", occurredAt=EVALUATED_AT - timedelta(days=30)),
    reviewEvent(2, "correct_without_hint"),
  ]

  mastery = await calculateMastery(events)

  assert mastery.masteryScore == 0.5294


@pytest.mark.asyncio
async def test_future_direct_event_does_not_affect_score_or_metadata() -> None:
  events = [
    reviewEvent(index, "correct_without_hint")
    for index in range(1, 6)
  ]
  events.append(reviewEvent(6, "incorrect", occurredAt=EVALUATED_AT + timedelta(days=30)))

  mastery = await calculateMastery(events)

  assert mastery.masteryScore == 1
  assert mastery.evidenceCount == 5
  assert mastery.windowEnd == EVALUATED_AT
  assert mastery.updatedAt == EVALUATED_AT


@pytest.mark.asyncio
async def test_basic_mastery_includes_score_point_six_boundary() -> None:
  events = [answerEvent(index, isCorrect=index <= 3) for index in range(1, 6)]

  mastery = await calculateMastery(events)

  assert mastery.masteryScore == 0.6
  assert mastery.masteryStatus == "BASIC_MASTERY"


@pytest.mark.asyncio
@pytest.mark.parametrize(
  ("results", "expectedTrend"),
  [
    ([False, False, False, True, True, True], "IMPROVING"),
    ([True, True, True, False, False, False], "DECLINING"),
    ([True, False, False, True, False, False], "STABLE"),
  ],
)
async def test_direct_trend_compares_early_and_late_accuracy(
  results: list[bool],
  expectedTrend: str,
) -> None:
  events = [
    answerEvent(index, isCorrect=result, occurredAt=EVALUATED_AT - timedelta(days=7 - index))
    for index, result in enumerate(results, start=1)
  ]

  mastery = await calculateMastery(events)

  assert mastery.trend == expectedTrend


@pytest.mark.asyncio
async def test_direct_trend_declines_when_accuracy_is_equal_but_hints_increase() -> None:
  events = [answerEvent(index, hintCount=0) for index in range(1, 4)]
  events.extend(answerEvent(index, hintCount=4) for index in range(4, 7))

  mastery = await calculateMastery(events)

  assert mastery.trend == "DECLINING"


@pytest.mark.asyncio
@pytest.mark.parametrize(
  ("oldScore", "newScore", "expectedTrend"),
  [
    (0.8, 0.4, "IMPROVING"),
    (0.4, 0.8, "DECLINING"),
    (0.5, 0.5, "STABLE"),
  ],
)
async def test_m3_trend_uses_latest_event(
  oldScore: float,
  newScore: float,
  expectedTrend: str,
) -> None:
  mastery = await calculateMastery([
    weakEvent(index, oldScore=oldScore, newScore=newScore)
    for index in range(1, 6)
  ])

  assert mastery.trend == expectedTrend


@pytest.mark.asyncio
async def test_same_time_m3_events_use_greater_database_event_id() -> None:
  events = [
    weakEvent(1, oldScore=0.2, newScore=0.7),
    weakEvent(2, oldScore=0.8, newScore=0.2),
  ]

  mastery = await calculateMastery(events)

  assert mastery.masteryScore == 0.8
  assert mastery.masteryStatus == "INSUFFICIENT_EVIDENCE"
  assert mastery.trend == "IMPROVING"


@pytest.mark.asyncio
@pytest.mark.parametrize(
  ("directTime", "weakTime", "expectedTrend"),
  [
    (EVALUATED_AT, EVALUATED_AT - timedelta(days=1), "IMPROVING"),
    (EVALUATED_AT - timedelta(days=1), EVALUATED_AT, "DECLINING"),
  ],
)
async def test_newer_source_wins_trend_conflict(
  directTime: datetime,
  weakTime: datetime,
  expectedTrend: str,
) -> None:
  events = [
    answerEvent(index, isCorrect=index > 3, occurredAt=directTime - timedelta(minutes=7 - index))
    for index in range(1, 7)
  ]
  events.append(weakEvent(7, oldScore=0.2, newScore=0.8, occurredAt=weakTime))

  mastery = await calculateMastery(events)

  assert mastery.trend == expectedTrend


@pytest.mark.asyncio
async def test_equal_time_conflicting_trends_become_stable_and_reduce_confidence() -> None:
  events = [answerEvent(index, isCorrect=index > 3) for index in range(1, 7)]
  events.append(weakEvent(7, oldScore=0.2, newScore=0.8))

  mastery = await calculateMastery(events)

  assert mastery.trend == "STABLE"
  assert mastery.confidence == 0.2625


@pytest.mark.asyncio
async def test_mastery_is_sorted_and_contains_consistent_metadata() -> None:
  early = EVALUATED_AT - timedelta(days=2)
  events = [
    answerEvent(1, kpId=202, occurredAt=early),
    weakEvent(2, kpId=101, occurredAt=EVALUATED_AT - timedelta(days=1)),
    reviewEvent(3, "incorrect", kpId=202),
    fillerEvent(4),
    weakEvent(5, kpId=101, newScore=0.6),
  ]

  response = await buildProfile(readyRequest(events), evaluatedAt=EVALUATED_AT)

  assert [item.kpId for item in response.knowledgeMastery] == [101, 202]
  first, second = response.knowledgeMastery
  assert first.evidenceCount == 2
  assert first.windowStart == EVALUATED_AT - timedelta(days=1)
  assert first.windowEnd == EVALUATED_AT
  assert first.updatedAt == EVALUATED_AT
  assert second.evidenceCount == 2
  assert second.windowStart == early
  assert second.windowEnd == EVALUATED_AT
  assert all(item.algorithmVersion == ALGORITHM_VERSION for item in response.knowledgeMastery)


@pytest.mark.asyncio
async def test_ready_without_knowledge_point_evidence_has_empty_mastery() -> None:
  request = readyRequest([fillerEvent(index) for index in range(1, 6)])

  response = await buildProfile(request, evaluatedAt=EVALUATED_AT)

  assert response.status == "READY"
  assert response.knowledgeMastery == []


@pytest.mark.asyncio
async def test_five_future_events_return_no_change() -> None:
  future = EVALUATED_AT + timedelta(seconds=1)
  request = BuildProfileRequest.model_validate(makeRequest([
    answerEvent(index, occurredAt=future)
    for index in range(1, 6)
  ]))

  response = await buildProfile(request, evaluatedAt=EVALUATED_AT)

  assert response.status == "NO_CHANGE"


@pytest.mark.asyncio
async def test_future_event_does_not_complete_global_ready_gate() -> None:
  events = [answerEvent(index) for index in range(1, 5)]
  events.append(answerEvent(5, occurredAt=EVALUATED_AT + timedelta(seconds=1)))
  request = BuildProfileRequest.model_validate(makeRequest(events))

  response = await buildProfile(request, evaluatedAt=EVALUATED_AT)

  assert response.status == "INSUFFICIENT_DATA"
  assert response.knowledgeMastery == []


@pytest.mark.asyncio
async def test_future_m3_does_not_override_current_mastered_status() -> None:
  events = [answerEvent(index) for index in range(1, 12)]
  events.append(weakEvent(12, occurredAt=EVALUATED_AT + timedelta(seconds=1)))

  mastery = await calculateMastery(events)

  assert mastery.masteryStatus == "MASTERED"
  assert mastery.evidenceCount == 11
  assert mastery.windowEnd == EVALUATED_AT


@pytest.mark.asyncio
async def test_finish_practice_is_not_distributed_as_direct_evidence() -> None:
  events = [
    makeEvent(
      index,
      eventType="finish_practice",
      kpId=101,
      data={"questionCount": 10, "accuracy": 1, "durationSec": 60},
    )
    for index in range(1, 6)
  ]
  request = readyRequest(events)

  response = await buildProfile(request, evaluatedAt=EVALUATED_AT)

  assert response.knowledgeMastery == []


@pytest.mark.parametrize(
  ("topic", "expected"),
  [
    ("为什么这个结论成立", "原因不理解"),
    ("WHY does this happen", "原因不理解"),
    ("我还是不明白", "概念不理解"),
    ("I DON’T   UNDERSTAND this", "概念不理解"),
    ("这个符号是什么意思", "术语不理解"),
    ("What does   eigenvalue mean", "术语不理解"),
    ("这道题怎么做", "方法或步骤不理解"),
    ("How Should   I solve it", "方法或步骤不理解"),
    ("它们的区别是什么", "概念混淆"),
    ("What is the DIFFERENCE here", "概念混淆"),
    ("ＷＨＹ　does this happen", "原因不理解"),
    ("今天复习二次函数", None),
  ],
)
def test_extract_confusion_type_normalizes_and_classifies(topic: str, expected: str | None) -> None:
  assert extractConfusionType(topic) == expected


@pytest.mark.asyncio
async def test_understood_feedback_offsets_m7_confusion() -> None:
  events = [
    doubtEvent(1),
    explanationEvent(2, eventType="explanation_feedback", explainId="explain-1", feedback="understood"),
  ]

  response = await buildProfile(readyRequest(events), evaluatedAt=EVALUATED_AT)

  assert response.profile.recentConfusions == []


@pytest.mark.asyncio
@pytest.mark.parametrize(
  ("feedback", "expectedCount"),
  [("partly_understood", 1), ("still_confused", 1)],
)
async def test_positive_m2_feedback_creates_confusion(feedback: str, expectedCount: int) -> None:
  event = explanationEvent(1, eventType="explanation_feedback", explainId="explain-1", feedback=feedback)

  response = await buildProfile(readyRequest([event]), evaluatedAt=EVALUATED_AT)

  confusion = response.profile.recentConfusions[0]
  assert confusion.kpId == 101
  assert confusion.detail == "概念不理解"
  assert confusion.evidenceCount == expectedCount


@pytest.mark.asyncio
async def test_feedback_inherits_request_kp_but_own_kp_takes_priority() -> None:
  events = [
    explanationEvent(1, eventType="request_explanation", explainId="inherited", kpId=101),
    explanationEvent(2, eventType="explanation_feedback", explainId="inherited", kpId=None),
    explanationEvent(3, eventType="request_explanation", explainId="preferred", kpId=101),
    explanationEvent(4, eventType="explanation_feedback", explainId="preferred", kpId=202),
  ]

  response = await buildProfile(readyRequest(events), evaluatedAt=EVALUATED_AT)

  assert [item.kpId for item in response.profile.recentConfusions] == [101, 202]


@pytest.mark.asyncio
async def test_request_itself_and_feedback_before_request_do_not_create_confusion() -> None:
  events = [
    explanationEvent(1, eventType="explanation_feedback", explainId="late-request", kpId=None),
    explanationEvent(2, eventType="request_explanation", explainId="late-request", kpId=101),
  ]

  response = await buildProfile(readyRequest(events), evaluatedAt=EVALUATED_AT)

  assert response.profile.recentConfusions == []


@pytest.mark.asyncio
async def test_newer_kpless_request_blocks_fallback_to_older_request_kp() -> None:
  events = [
    explanationEvent(
      1,
      eventType="request_explanation",
      explainId="reused",
      kpId=101,
      occurredAt=EVALUATED_AT - timedelta(minutes=2),
    ),
    explanationEvent(
      2,
      eventType="request_explanation",
      explainId="reused",
      kpId=None,
      occurredAt=EVALUATED_AT - timedelta(minutes=1),
    ),
    explanationEvent(3, eventType="explanation_feedback", explainId="reused", kpId=None),
  ]
  request = readyRequest(events)

  response = await buildProfile(request, evaluatedAt=EVALUATED_AT)

  assert response.profile.recentConfusions == []
  assert calculateQuestionUnderstanding(request.events) == 0


@pytest.mark.asyncio
async def test_same_time_request_resolution_uses_database_event_id() -> None:
  events = [
    explanationEvent(10, eventType="request_explanation", explainId="same-time", kpId=101),
    explanationEvent(11, eventType="request_explanation", explainId="same-time", kpId=None),
    explanationEvent(12, eventType="explanation_feedback", explainId="same-time", kpId=None),
  ]

  response = await buildProfile(readyRequest(events), evaluatedAt=EVALUATED_AT)

  assert response.profile.recentConfusions == []


def test_question_understanding_uses_feedback_and_followups_only() -> None:
  events = [
    explanationEvent(1, eventType="explanation_feedback", explainId="a", feedback="understood"),
    explanationEvent(2, eventType="explanation_feedback", explainId="b", feedback="partly_understood"),
    explanationEvent(3, eventType="explanation_feedback", explainId="c", feedback="still_confused"),
    doubtEvent(4, kpId=None, isFollowUp=True),
    doubtEvent(5, kpId=None, isFollowUp=False),
  ]
  request = BuildProfileRequest.model_validate(makeRequest(events))

  assert calculateQuestionUnderstanding(request.events) == 0.375


def test_first_question_without_feedback_has_no_understanding_feature() -> None:
  request = BuildProfileRequest.model_validate(makeRequest([doubtEvent(1, isFollowUp=False)]))

  assert calculateQuestionUnderstanding(request.events) is None


@pytest.mark.asyncio
async def test_only_m2_and_m7_create_confusions() -> None:
  events = [
    answerEvent(1, isCorrect=False),
    weakEvent(2),
    makeEvent(3, eventType="lecture_interact", data={
      "lectureId": "lecture-1",
      "chapterId": "chapter-1",
      "action": "ask",
    }),
  ]

  response = await buildProfile(readyRequest(events), evaluatedAt=EVALUATED_AT)

  assert response.profile.recentConfusions == []


@pytest.mark.asyncio
async def test_m7_without_kp_does_not_output_but_followup_affects_understanding() -> None:
  event = doubtEvent(1, kpId=None, isFollowUp=True)
  request = readyRequest([event])

  response = await buildProfile(request, evaluatedAt=EVALUATED_AT)

  assert response.profile.recentConfusions == []
  assert calculateQuestionUnderstanding(request.events) == 0


@pytest.mark.asyncio
async def test_m7_prefers_pattern_and_uses_safe_tag_fallback() -> None:
  events = [
    doubtEvent(
      1,
      kpId=101,
      topic="为什么这里要换元",
      confusionTag="formula_confusion",
      isFollowUp=True,
    ),
    doubtEvent(2, kpId=202, topic="请讲一下这一题", confusionTag="application_difficulty"),
  ]

  response = await buildProfile(readyRequest(events), evaluatedAt=EVALUATED_AT)
  byKp = {item.kpId: item for item in response.profile.recentConfusions}

  assert byKp[101].detail == "原因不理解"
  assert byKp[101].evidenceCount == 3
  assert byKp[202].detail == "应用困难"


@pytest.mark.asyncio
async def test_profile_json_never_contains_original_topic() -> None:
  uniqueTopic = "为什么星尘火箭在紫色山谷中反向旋转"

  response = await buildProfile(
    readyRequest([doubtEvent(1, topic=uniqueTopic)]),
    evaluatedAt=EVALUATED_AT,
  )

  assert uniqueTopic not in response.model_dump_json()


@pytest.mark.asyncio
async def test_confusion_detail_uses_strongest_decayed_positive_evidence() -> None:
  events = [
    doubtEvent(1, topic="为什么成立", occurredAt=EVALUATED_AT - timedelta(days=30)),
    doubtEvent(2, topic="它们的区别", confusionTag="formula_confusion", isFollowUp=True),
  ]

  response = await buildProfile(readyRequest(events), evaluatedAt=EVALUATED_AT)

  assert response.profile.recentConfusions[0].detail == "概念混淆"
  assert response.profile.recentConfusions[0].lastOccurredAt == EVALUATED_AT


@pytest.mark.asyncio
async def test_m7_pattern_and_tag_accumulate_under_their_own_details() -> None:
  events = [
    doubtEvent(1, topic="为什么成立", confusionTag="formula_confusion"),
    doubtEvent(2, topic="请再讲一次", confusionTag="formula_confusion"),
  ]

  response = await buildProfile(readyRequest(events), evaluatedAt=EVALUATED_AT)

  confusion = response.profile.recentConfusions[0]
  assert confusion.detail == "公式理解困难"
  assert confusion.evidenceCount == 3


def test_m7_followup_belongs_to_pattern_detail_before_tag_detail() -> None:
  event = doubtEvent(
    1,
    topic="为什么成立",
    confusionTag="formula_confusion",
    isFollowUp=True,
  )
  request = BuildProfileRequest.model_validate(makeRequest([event]))

  contributions = collectConfusionContributions(request.events)

  assert Counter(item.detail for item in contributions) == Counter({
    "原因不理解": 2,
    "公式理解困难": 1,
  })


@pytest.mark.asyncio
async def test_confusions_sort_by_net_score_then_time_and_limit_to_five() -> None:
  events = [
    doubtEvent(index, kpId=index, topic="为什么", isFollowUp=index == 8)
    for index in range(1, 9)
  ]

  response = await buildProfile(readyRequest(events), evaluatedAt=EVALUATED_AT)

  assert [item.kpId for item in response.profile.recentConfusions] == [8, 1, 2, 3, 4]
  assert all(0 <= item.confidence <= 1 for item in response.profile.recentConfusions)


@pytest.mark.asyncio
async def test_recent_focus_normalizes_frequency_and_decay_and_limits_to_five() -> None:
  events = [answerEvent(index, kpId=index) for index in range(1, 9)]
  events.append(answerEvent(9, kpId=8, occurredAt=EVALUATED_AT - timedelta(days=30)))

  response = await buildProfile(readyRequest(events), evaluatedAt=EVALUATED_AT)
  focus = response.profile.recentFocus

  assert [item.kpId for item in focus] == [8, 1, 2, 3, 4]
  assert focus[0].weight == 1.0
  assert focus[1].weight == 0.667


@pytest.mark.asyncio
async def test_recent_focus_weight_tie_uses_latest_time_then_kp() -> None:
  events = [
    answerEvent(1, kpId=101),
    answerEvent(2, kpId=202, occurredAt=EVALUATED_AT - timedelta(days=30)),
    answerEvent(3, kpId=202, occurredAt=EVALUATED_AT - timedelta(days=30)),
  ]

  response = await buildProfile(readyRequest(events), evaluatedAt=EVALUATED_AT)

  assert [(item.kpId, item.weight) for item in response.profile.recentFocus[:2]] == [
    (101, 1.0),
    (202, 1.0),
  ]


@pytest.mark.asyncio
async def test_recent_focus_ignores_m5_missing_kp_and_future_events() -> None:
  m5 = makeEvent(
    1,
    eventType="lesson_material_used",
    kpId=999,
    data={"contentId": "content-1", "materialType": "text", "result": "completed"},
  )
  future = answerEvent(2, kpId=888, occurredAt=EVALUATED_AT + timedelta(seconds=1))
  current = answerEvent(3, kpId=101)

  activeFillers = [fillerEvent(index) for index in range(4, 8)]
  request = BuildProfileRequest.model_validate(makeRequest([m5, future, current, *activeFillers]))

  response = await buildProfile(request, evaluatedAt=EVALUATED_AT)

  assert [(item.kpId, item.weight) for item in response.profile.recentFocus] == [(101, 1.0)]


@pytest.mark.asyncio
async def test_ready_with_only_kpless_m4_has_empty_profile_features() -> None:
  request = readyRequest([fillerEvent(index) for index in range(1, 6)])

  response = await buildProfile(request, evaluatedAt=EVALUATED_AT)

  assert response.profile.preferredContentModes == []
  assert response.profile.recentConfusions == []
  assert response.profile.recentFocus == []


@pytest.mark.parametrize(
  ("durations", "expected"),
  [
    ([1, 2, 3, 4], None),
    ([29, 29, 29, 29, 29], "fast"),
    ([30, 30, 30, 30, 30], "moderate"),
    ([90, 90, 90, 90, 90], "moderate"),
    ([91, 91, 91, 91, 91], "slow"),
  ],
)
def test_learning_pace_uses_median_boundaries(durations: list[int], expected: str | None) -> None:
  assert inferLearningPace(durations) == expected


def test_preferences_use_latest_event_per_key_and_database_id_tiebreaker() -> None:
  earlier = EVALUATED_AT - timedelta(minutes=1)
  events = [
    preferenceEvent(1, "content_mode", "video", occurredAt=earlier),
    preferenceEvent(2, "content_mode", "text"),
    preferenceEvent(3, "content_mode", "image"),
    preferenceEvent(4, "explanation_style", "concise"),
    preferenceEvent(5, "learning_pace", "slow"),
  ]
  request = BuildProfileRequest.model_validate(makeRequest(events))

  preferences = calculatePreferences(request.events, [10, 10, 10, 10, 10])

  assert preferences.preferredContentModes == ["image"]
  assert preferences.preferredExplanationStyle == "concise"
  assert preferences.learningPace == "slow"


def test_preferences_do_not_guess_content_or_style_from_behavior() -> None:
  request = BuildProfileRequest.model_validate(makeRequest([
    answerEvent(index) for index in range(1, 6)
  ]))

  preferences = calculatePreferences(request.events, [29, 29, 29, 29, 29])

  assert preferences.preferredContentModes == []
  assert preferences.preferredExplanationStyle is None
  assert preferences.learningPace == "fast"


@pytest.mark.asyncio
async def test_postponed_reviews_still_contribute_to_inferred_pace() -> None:
  events = [reviewEvent(index, "postponed") for index in range(1, 6)]

  response = await buildProfile(readyRequest(events), evaluatedAt=EVALUATED_AT)

  assert response.profile.learningPace == "fast"


@pytest.mark.asyncio
async def test_finish_practice_duration_does_not_complete_pace_evidence() -> None:
  events = [answerEvent(index) for index in range(1, 5)]
  events.append(finishPracticeEvent(5, 1))

  response = await buildProfile(readyRequest(events), evaluatedAt=EVALUATED_AT)

  assert response.profile.learningPace is None


@pytest.mark.parametrize(
  ("answerScore", "questionScore", "weakPointScore", "expectedScore", "expectedLabel"),
  [
    (0.8, 0.8, None, 0.8, "学习情况良好"),
    (0.6, 0.6, None, 0.6, "学习情况稳定"),
    (0.4, 0.4, None, 0.4, "学习情况待加强"),
    (0.3999, 0.3999, None, 0.3999, "学习情况需优先关注"),
  ],
)
def test_learning_situation_label_boundaries(
  answerScore: float,
  questionScore: float,
  weakPointScore: float | None,
  expectedScore: float,
  expectedLabel: str,
) -> None:
  situation = calculateLearningSituation(answerScore, questionScore, weakPointScore)

  assert situation.score == expectedScore
  assert situation.label == expectedLabel


def test_learning_situation_renormalizes_available_sources() -> None:
  withoutWeak = calculateLearningSituation(0.9, 0.3, None)
  withWeak = calculateLearningSituation(0.9, 0.3, 0.6)

  assert withoutWeak.weights == {"answer": 0.6667, "question": 0.3333}
  assert withoutWeak.score == 0.7
  assert withWeak.weights == {"answer": 0.5333, "question": 0.2667, "weakPoint": 0.2}
  assert withWeak.score == 0.68


def test_learning_situation_single_source_has_no_conclusion() -> None:
  situation = calculateLearningSituation(0.8, None, None)

  assert situation.scores == {"answer": 0.8}
  assert situation.weights == {"answer": 1.0}
  assert situation.score is None
  assert situation.label is None


def test_weak_point_score_uses_latest_event_per_knowledge_point() -> None:
  events = [
    weakEvent(1, kpId=101, newScore=0.9, occurredAt=EVALUATED_AT - timedelta(minutes=1)),
    weakEvent(2, kpId=101, newScore=0.4),
    weakEvent(3, kpId=101, newScore=0.2),
    weakEvent(4, kpId=202, newScore=0.6),
  ]
  request = BuildProfileRequest.model_validate(makeRequest(events))

  assert calculateWeakPointScore(request.events) == 0.6


@pytest.mark.asyncio
async def test_m3_only_mastery_does_not_enter_answer_score() -> None:
  request = readyRequest([weakEvent(index) for index in range(1, 6)])
  response = await buildProfile(request, evaluatedAt=EVALUATED_AT)

  assert calculateAnswerScore(request.events, response.knowledgeMastery) is None


@pytest.mark.asyncio
async def test_answer_score_weights_mastery_by_capped_direct_evidence() -> None:
  events = [answerEvent(index, kpId=101, isCorrect=True) for index in range(1, 6)]
  events.append(answerEvent(6, kpId=202, isCorrect=False))
  request = readyRequest(events)
  response = await buildProfile(request, evaluatedAt=EVALUATED_AT)

  assert calculateAnswerScore(request.events, response.knowledgeMastery) == 0.8333


@pytest.mark.asyncio
async def test_finish_practice_deduplicates_session_with_question_events() -> None:
  events = [answerEvent(index, isCorrect=True) for index in range(1, 6)]
  events.append(finishPracticeEvent(6, 0, sessionId="session-1"))
  request = readyRequest(events)
  response = await buildProfile(request, evaluatedAt=EVALUATED_AT)

  assert calculateAnswerScore(request.events, response.knowledgeMastery) == 1


@pytest.mark.asyncio
async def test_finish_practice_without_question_events_is_overall_evidence() -> None:
  request = readyRequest([finishPracticeEvent(1, 0.25, sessionId="practice-only")])
  response = await buildProfile(request, evaluatedAt=EVALUATED_AT)

  assert calculateAnswerScore(request.events, response.knowledgeMastery) == 0.25


@pytest.mark.asyncio
async def test_repeated_finish_practice_in_session_uses_latest_event_only() -> None:
  earlier = finishPracticeEvent(1, 0.2, sessionId="practice-only")
  earlier["occurredAt"] = (EVALUATED_AT - timedelta(minutes=1)).isoformat()
  latest = finishPracticeEvent(2, 0.8, sessionId="practice-only")
  request = readyRequest([earlier, latest])
  response = await buildProfile(request, evaluatedAt=EVALUATED_AT)

  assert calculateAnswerScore(request.events, response.knowledgeMastery) == 0.8


@pytest.mark.asyncio
async def test_same_time_finish_practice_uses_greater_database_event_id() -> None:
  events = [
    finishPracticeEvent(1, 0.2, sessionId="practice-only"),
    finishPracticeEvent(2, 0.8, sessionId="practice-only"),
  ]
  request = readyRequest(events)
  response = await buildProfile(request, evaluatedAt=EVALUATED_AT)

  assert calculateAnswerScore(request.events, response.knowledgeMastery) == 0.8


@pytest.mark.asyncio
async def test_empty_string_session_answer_deduplicates_finish_practice() -> None:
  answer = answerEvent(1)
  answer["sessionId"] = ""
  finish = finishPracticeEvent(2, 0, sessionId="")
  request = readyRequest([answer, finish])
  response = await buildProfile(request, evaluatedAt=EVALUATED_AT)

  assert calculateAnswerScore(request.events, response.knowledgeMastery) == 1


@pytest.mark.asyncio
async def test_finish_practice_without_session_is_independent_evidence() -> None:
  events = [
    finishPracticeEvent(1, 0.2, sessionId=None),
    finishPracticeEvent(2, 0.8, sessionId=None),
  ]
  request = readyRequest(events)
  response = await buildProfile(request, evaluatedAt=EVALUATED_AT)

  assert calculateAnswerScore(request.events, response.knowledgeMastery) == 0.5


@pytest.mark.asyncio
async def test_learning_features_reuse_question_understanding() -> None:
  events = [
    explanationEvent(1, eventType="explanation_feedback", explainId="a", feedback="understood"),
    explanationEvent(2, eventType="explanation_feedback", explainId="b", feedback="still_confused"),
  ]
  request = readyRequest(events)
  response = await buildProfile(request, evaluatedAt=EVALUATED_AT)

  situation = calculateLearningSituationFeatures(request.events, response.knowledgeMastery)

  assert situation.scores["question"] == calculateQuestionUnderstanding(request.events) == 0.5


@pytest.mark.parametrize(
  ("validEventCount", "scores", "expected"),
  [
    (10, (1.0, None, None), 0.51),
    (20, (0.9, 0.8, 0.7), 0.95),
    (20, (1.0, 0.0, 0.5), 0.8),
    (100, (0.8, 0.8, 0.8), 0.95),
  ],
)
def test_profile_confidence_uses_quantity_coverage_consistency_and_cap(
  validEventCount: int,
  scores: tuple[float | None, float | None, float | None],
  expected: float,
) -> None:
  situation = calculateLearningSituation(*scores)

  assert calculateProfileConfidence(validEventCount, situation) == expected


@pytest.mark.parametrize("invalidScore", [float("nan"), float("inf"), float("-inf"), -0.1, 1.1])
def test_learning_situation_rejects_invalid_source_scores(invalidScore: float) -> None:
  with pytest.raises(ValueError, match="来源分.*0 到 1"):
    calculateLearningSituation(invalidScore, 0.5, None)


@pytest.mark.parametrize("invalidScore", [float("nan"), float("inf"), float("-inf"), -0.1, 1.1])
def test_profile_confidence_rejects_invalid_source_scores(invalidScore: float) -> None:
  situation = LearningSituation(
    scores={"answer": invalidScore},
    weights={"answer": 1.0},
    score=None,
    label=None,
  )

  with pytest.raises(ValueError, match="来源分.*0 到 1"):
    calculateProfileConfidence(20, situation)


@pytest.mark.parametrize("invalidCount", [float("nan"), float("inf"), float("-inf"), -1])
def test_profile_confidence_rejects_invalid_event_count(invalidCount: float) -> None:
  situation = calculateLearningSituation(0.5, None, None)

  with pytest.raises(ValueError, match="有效事件数量必须是非负整数"):
    calculateProfileConfidence(invalidCount, situation)


@pytest.mark.asyncio
async def test_ready_profile_serializes_preferences_pace_and_confidence() -> None:
  events = [
    answerEvent(1, isCorrect=True),
    answerEvent(2, isCorrect=False),
    reviewEvent(3, "postponed"),
    preferenceEvent(4, "content_mode", "video"),
    preferenceEvent(5, "explanation_style", "step_by_step"),
    preferenceEvent(6, "learning_pace", "slow"),
  ]

  response = await buildProfile(readyRequest(events), evaluatedAt=EVALUATED_AT)
  serialized = response.model_dump(mode="json")

  assert response.profile.preferredContentModes == ["video"]
  assert response.profile.preferredExplanationStyle == "step_by_step"
  assert response.profile.learningPace == "slow"
  assert response.profile.confidence == 0.41
  assert serialized["profile"]["confidence"] == 0.41


def test_engine_source_has_no_legacy_stateful_dependencies() -> None:
  sourcePath = Path(__file__).parents[2] / "src" / "landppt" / "m6" / "profile_engine.py"
  source = sourcePath.read_text(encoding="utf-8")

  forbiddenFragments = (".db", ".review_scheduler", "SELECT ", "INSERT ", "UPDATE ", "DELETE ")
  assert all(fragment not in source for fragment in forbiddenFragments)


@pytest.mark.asyncio
async def test_ready_uses_injected_summary_and_sends_only_sanitized_payload() -> None:
  secretTopic = "绝密主题-紫色峡谷-不可外发"
  secretRequestId = "87654321-4321-8765-4321-876543218765"
  events = [doubtEvent(1, topic=secretTopic)]
  events[0]["eventId"] = "secret-event-marker"
  events[0]["sessionId"] = "secret-session-marker"
  events[0]["traceId"] = "secret-trace-marker"
  request = readyRequest(events).model_copy(update={"requestId": secretRequestId, "userId": 987654321})
  client = FakeSummaryClient("这是模型生成的摘要开头。" + "内容" * 80)

  response = await buildProfile(request, summaryClient=client, evaluatedAt=EVALUATED_AT)

  assert response.status == "READY"
  assert response.profile.summaryProfile.startswith("这是模型生成的摘要开头。")
  assert 150 <= len(response.profile.summaryProfile) <= 300
  assert len(client.payloads) == 1
  serializedPayload = json.dumps(client.payloads[0], ensure_ascii=False)
  for secret in (
    secretTopic,
    secretRequestId,
    "987654321",
    "secret-event-marker",
    "secret-session-marker",
    "secret-trace-marker",
  ):
    assert secret not in serializedPayload
  assert set(client.payloads[0]) == {
    "promptVersion",
    "algorithmVersion",
    "learningSituation",
    "knowledgeMastery",
    "recentConfusions",
    "recentFocus",
    "learningPace",
    "preferredContentModes",
    "preferredExplanationStyle",
    "profileConfidence",
    "recommendations",
  }


@pytest.mark.asyncio
async def test_summary_truncates_long_result_and_completes_short_result() -> None:
  longClient = FakeSummaryClient("长" * 350)
  shortClient = FakeSummaryClient("保留这个模型开头")

  longResponse = await buildProfile(readyRequest([]), summaryClient=longClient, evaluatedAt=EVALUATED_AT)
  shortResponse = await buildProfile(readyRequest([]), summaryClient=shortClient, evaluatedAt=EVALUATED_AT)

  assert longResponse.profile.summaryProfile == "长" * 300
  assert shortResponse.profile.summaryProfile.startswith("保留这个模型开头")
  assert 150 <= len(shortResponse.profile.summaryProfile) <= 300


@pytest.mark.asyncio
@pytest.mark.parametrize("result", ["", "   ", None])
async def test_empty_summary_falls_back_to_deterministic_local_template(result: str | None) -> None:
  request = buildRequest(5)
  first = await buildProfile(request, summaryClient=FakeSummaryClient(result), evaluatedAt=EVALUATED_AT)
  second = await buildProfile(request, summaryClient=FakeSummaryClient(result), evaluatedAt=EVALUATED_AT)

  assert first.status == "READY"
  assert first.profile.summaryProfile == second.profile.summaryProfile
  assert 150 <= len(first.profile.summaryProfile) <= 300


@pytest.mark.asyncio
async def test_summary_exception_and_timeout_fall_back_without_private_logs(
  monkeypatch: pytest.MonkeyPatch,
  caplog: pytest.LogCaptureFixture,
) -> None:
  secret = "secret-user-request-topic"
  exceptionClient = FakeSummaryClient(error=RuntimeError(secret))
  request = readyRequest([doubtEvent(1, topic=secret)])

  exceptionResponse = await buildProfile(
    request,
    summaryClient=exceptionClient,
    evaluatedAt=EVALUATED_AT,
  )

  class HangingClient:
    async def createSummary(self, payload: dict) -> str:
      del payload
      await __import__("asyncio").sleep(1)
      return "不会返回"

  monkeypatch.setenv("PROFILE_LLM_TIMEOUT_SECONDS", "0.001")
  timeoutResponse = await buildProfile(
    request,
    summaryClient=HangingClient(),
    evaluatedAt=EVALUATED_AT,
  )

  assert exceptionResponse.status == timeoutResponse.status == "READY"
  assert 150 <= len(exceptionResponse.profile.summaryProfile) <= 300
  assert 150 <= len(timeoutResponse.profile.summaryProfile) <= 300
  assert secret not in caplog.text
  assert str(request.userId) not in caplog.text
  assert str(request.requestId) not in caplog.text


@pytest.mark.asyncio
async def test_invalid_timeout_skips_client_and_falls_back(
  monkeypatch: pytest.MonkeyPatch,
) -> None:
  client = FakeSummaryClient("不会被调用" + "文" * 160)
  monkeypatch.setenv("PROFILE_LLM_TIMEOUT_SECONDS", "nan")

  response = await buildProfile(readyRequest([]), summaryClient=client, evaluatedAt=EVALUATED_AT)

  assert response.status == "READY"
  assert client.payloads == []
  assert 150 <= len(response.profile.summaryProfile) <= 300


@pytest.mark.asyncio
@pytest.mark.parametrize("eventCount", [0, 1])
async def test_non_ready_status_never_calls_summary_client(eventCount: int) -> None:
  client = FakeSummaryClient("不应调用")
  request = buildRequest(eventCount)

  response = await buildProfile(request, summaryClient=client, evaluatedAt=EVALUATED_AT)

  assert response.status in {"NO_CHANGE", "INSUFFICIENT_DATA"}
  assert client.payloads == []


@pytest.mark.asyncio
async def test_default_summary_is_deterministic_when_openai_config_is_incomplete(
  monkeypatch: pytest.MonkeyPatch,
) -> None:
  request = buildRequest(5)
  configurations = (
    {"OPENAI_BASE_URL": "https://example.invalid", "OPENAI_MODEL": "model"},
    {"OPENAI_API_KEY": "key", "OPENAI_MODEL": "model"},
    {"OPENAI_API_KEY": "key", "OPENAI_BASE_URL": "https://example.invalid"},
  )
  summaries = []
  for configuration in configurations:
    for name in ("OPENAI_API_KEY", "OPENAI_BASE_URL", "OPENAI_MODEL"):
      monkeypatch.delenv(name, raising=False)
    for name, value in configuration.items():
      monkeypatch.setenv(name, value)
    response = await buildProfile(request, evaluatedAt=EVALUATED_AT)
    summaries.append(response.profile.summaryProfile)

  assert len(set(summaries)) == 1
  assert 150 <= len(summaries[0]) <= 300
  assert "BASIC_MASTERY" not in summaries[0]


@pytest.mark.asyncio
async def test_openai_adapter_passes_model_temperature_and_json_messages() -> None:
  calls = []

  class FakeCompletions:
    async def create(self, **kwargs):
      calls.append(kwargs)
      message = SimpleNamespace(content="适配器摘要")
      return SimpleNamespace(choices=[SimpleNamespace(message=message)])

  asyncClient = SimpleNamespace(chat=SimpleNamespace(completions=FakeCompletions()))
  payload = {"promptVersion": SUMMARY_PROMPT_VERSION, "knowledgeMastery": [{"kpId": 101}]}
  client = OpenAISummaryClient(model="test-model", asyncClient=asyncClient)

  result = await client.createSummary(payload)

  assert result == "适配器摘要"
  assert calls[0]["model"] == "test-model"
  assert calls[0]["temperature"] == 0.2
  assert calls[0]["messages"] == buildSummaryMessages(payload)
  assert '"kpId": 101' in calls[0]["messages"][1]["content"]
  assert "{'kpId': 101}" not in calls[0]["messages"][1]["content"]


@pytest.mark.asyncio
async def test_openai_adapter_returns_empty_for_missing_choice_or_content() -> None:
  responses = [SimpleNamespace(choices=[]), SimpleNamespace(
    choices=[SimpleNamespace(message=SimpleNamespace(content=None))],
  )]

  class FakeCompletions:
    async def create(self, **kwargs):
      del kwargs
      return responses.pop(0)

  client = OpenAISummaryClient(
    model="test-model",
    asyncClient=SimpleNamespace(chat=SimpleNamespace(completions=FakeCompletions())),
  )

  assert await client.createSummary({}) == ""
  assert await client.createSummary({}) == ""
