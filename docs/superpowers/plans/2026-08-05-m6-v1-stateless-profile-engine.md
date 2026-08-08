# M6 v1.0 无状态画像引擎实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or
> superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 重写 Python M6 画像引擎，使其按 v1.0 契约无状态计算完整画像，并可靠降级大模型摘要。

**Architecture:** FastAPI 路由只负责 HTTP 契约转换，Pydantic 模型负责严格验证，`profile_engine.py` 负责纯计算与
异步摘要编排。Java 每次传入最近 90 天、最多 1000 条完整事件；引擎不读数据库、不保存用户状态。

**Tech Stack:** Python 3.11、FastAPI、Pydantic 2、OpenAI Async SDK、pytest、pytest-asyncio、pytest-cov

---

## 文件结构

| 文件 | 操作 | 职责 |
| --- | --- | --- |
| `aitutor-backend-python/src/landppt/m6/schemas.py` | 重写 | v1.0 请求、事件和三种响应模型；保留两个 GET 展示模型 |
| `aitutor-backend-python/src/landppt/m6/profile_engine.py` | 重写 | 无状态特征计算、M1/M3冲突处理、NLP、摘要和响应编排 |
| `aitutor-backend-python/src/landppt/m6/router.py` | 修改 | 正式画像 POST 路由；删除两个旧 POST；保留两个 GET |
| `aitutor-backend-python/tests/m6/__init__.py` | 新增 | 将 M6 测试目录声明为可导入包 |
| `aitutor-backend-python/tests/m6/conftest.py` | 新增 | v1.0 请求和事件测试工厂 |
| `aitutor-backend-python/tests/m6/test_schemas.py` | 新增 | 契约和事件校验测试 |
| `aitutor-backend-python/tests/m6/test_profile_engine.py` | 新增 | 画像算法和摘要测试 |
| `aitutor-backend-python/tests/m6/test_router.py` | 新增 | 路由状态码和路径测试 |

所有命令默认在 `aitutor-backend-python` 目录执行。

### Task 1: 建立 v1.0 测试工厂和严格契约模型

**Files:**
- Create: `aitutor-backend-python/tests/m6/__init__.py`
- Create: `aitutor-backend-python/tests/m6/conftest.py`
- Create: `aitutor-backend-python/tests/m6/test_schemas.py`
- Modify: `aitutor-backend-python/src/landppt/m6/schemas.py`

- [ ] **Step 1: 建立测试包并新增请求和事件工厂**

创建空的 `tests/m6/__init__.py`。在 `tests/m6/conftest.py` 写入固定 UTC 时间、递增数据库事件 ID 和
基础请求工厂：

```python
from copy import deepcopy
from datetime import datetime, timezone
from uuid import UUID

import pytest

FIXED_NOW = datetime(2026, 8, 5, 12, 0, tzinfo=timezone.utc)
REQUEST_ID = UUID("12345678-1234-5678-1234-567812345678")


def makeEvent(dbEventId=1, eventType="answer_question", sourceModule="M1", kpId=101, data=None):
  payload = data or {
    "isCorrect": True,
    "difficulty": 3,
    "timeSpentSec": 45,
    "hintCount": 0,
  }


def makeRequest(events, baseProfileVersion=3):
  watermark = max((event["dbEventId"] for event in events), default=0)
  return {
    "contractVersion": "1.0",
    "requestId": str(REQUEST_ID),
    "userId": 1001,
    "mode": "FULL",
    "baseProfileVersion": baseProfileVersion,
    "fromEventIdExclusive": 0,
    "eventWatermarkInclusive": watermark,
    "events": events,
  }
  return {
    "dbEventId": dbEventId,
    "eventId": f"event-{dbEventId}",
    "eventType": eventType,
    "sourceModule": sourceModule,
    "occurredAt": FIXED_NOW.isoformat(),
    "schemaVersion": "1.0",
    "sessionId": "session-1",
    "kpId": kpId,
    "traceId": f"trace-{dbEventId}",
    "data": payload,
  }


@pytest.fixture
def requestPayload():
  return makeRequest([makeEvent()])


@pytest.fixture
def copyRequest(requestPayload):
  return lambda: deepcopy(requestPayload)
```

- [ ] **Step 2: 写契约失败测试**

在 `tests/m6/test_schemas.py` 覆盖未知字段、错误版本、无时区时间、事件来源不匹配、非递增 ID、越过水位、
超过 1000 条事件和 `answer_question` 缺少 `kpId`：

```python
import pytest
from pydantic import ValidationError

from landppt.m6.schemas import BuildProfileRequest
from tests.m6.conftest import makeEvent


def testRejectsUnknownRequestField(copyRequest):
  payload = copyRequest()
  payload["forceRefresh"] = True
  with pytest.raises(ValidationError):
    BuildProfileRequest.model_validate(payload)


def testRejectsMismatchedEventSource(copyRequest):
  payload = copyRequest()
  payload["events"][0]["sourceModule"] = "M2"
  with pytest.raises(ValidationError, match="事件来源模块不匹配"):
    BuildProfileRequest.model_validate(payload)


def testRejectsNonIncreasingDatabaseEventIds(copyRequest):
  payload = copyRequest()
  payload["eventWatermarkInclusive"] = 2
  payload["events"] = [makeEvent(2), makeEvent(1)]
  with pytest.raises(ValidationError, match="严格递增"):
    BuildProfileRequest.model_validate(payload)
```

- [ ] **Step 3: 运行测试并确认失败**

Run: `uv run pytest tests/m6/test_schemas.py -q`

Expected: FAIL，旧 `BuildProfileRequest` 不接受 v1.0 字段或缺少严格校验。

- [ ] **Step 4: 重写画像契约模型**

在 `schemas.py` 保留 `KnowledgeStatusItem`、`KnowledgeStatusResponse`、`TimelineItem`、`TimelineResponse`，删除旧
`CalculateAllResponse`、`EventProcessResponse`、`ProfileData` 和旧画像响应。新增：

```python
from datetime import datetime
from typing import Annotated, Any, Literal
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field, model_validator

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
}


class StrictModel(BaseModel):
  model_config = ConfigDict(extra="forbid")


class ProfileEvent(StrictModel):
  dbEventId: int = Field(ge=1)
  eventId: str = Field(min_length=1, max_length=64)
  eventType: str
  sourceModule: str
  occurredAt: datetime
  schemaVersion: Literal["1.0"]
  sessionId: str | None = None
  kpId: int | None = Field(default=None, ge=1)
  traceId: str | None = None
  data: dict[str, Any]

  @model_validator(mode="after")
  def validateEvent(self):
    if self.occurredAt.tzinfo is None:
      raise ValueError("事件时间必须包含时区")
    if EVENT_SOURCES.get(self.eventType) != self.sourceModule:
      raise ValueError("事件来源模块不匹配")
    if self.eventType == "answer_question" and self.kpId is None:
      raise ValueError("答题事件必须包含知识点")
    validateEventData(self.eventType, self.data)
    return self


class BuildProfileRequest(StrictModel):
  contractVersion: Literal["1.0"]
  requestId: UUID
  userId: int = Field(ge=1)
  mode: Literal["INCREMENTAL", "FULL"]
  baseProfileVersion: int = Field(ge=0)
  fromEventIdExclusive: int = Field(ge=0)
  eventWatermarkInclusive: int = Field(ge=0)
  events: list[ProfileEvent] = Field(max_length=1000)

  @model_validator(mode="after")
  def validateWindow(self):
    if self.fromEventIdExclusive > self.eventWatermarkInclusive:
      raise ValueError("事件起始水位不能超过结束水位")
    previous = self.fromEventIdExclusive
    for event in self.events:
      if event.dbEventId <= previous:
        raise ValueError("事件数据库ID必须严格递增")
      if event.dbEventId > self.eventWatermarkInclusive:
        raise ValueError("事件数据库ID不能超过水位")
      previous = event.dbEventId
    return self
```

`validateEventData()` 使用以下完整规则，与 Java `LearningEventPolicy` v1.0 一致：

```python
EVENT_REQUIRED_KEYS = {
  "answer_question": {"isCorrect", "difficulty", "timeSpentSec", "hintCount"},
  "finish_practice": {"questionCount", "accuracy", "durationSec"},
  "request_explanation": {"explainId", "reasonTag"},
  "explanation_feedback": {"explainId", "feedback", "repeatCount"},
  "weak_point_changed": {"oldScore", "newScore", "reason"},
  "lecture_interact": {"lectureId", "chapterId", "action"},
  "lesson_material_used": {"contentId", "materialType", "result"},
  "ask_doubt": {"topic", "confusionTag", "isFollowUp"},
  "mark_reviewed": {"result", "timeSpentSec", "hintCount"},
  "preference_changed": {"preferenceKey", "preferenceValue"},
}
EVENT_OPTIONAL_KEYS = {"answer_question": {"confusionTag"}}
CONFUSION_TAGS = {
  "concept_unclear", "formula_confusion", "step_unclear",
  "application_difficulty", "careless_error",
}
EVENT_ENUMS = {
  ("request_explanation", "reasonTag"): {
    "WRONG_ANSWER", "REPEATED_ERROR", "USER_REQUEST", "LOW_CONFIDENCE", "REVIEW_NEEDED",
  },
  ("explanation_feedback", "feedback"): {
    "understood", "partly_understood", "still_confused",
  },
  ("weak_point_changed", "reason"): {
    "ACCURACY_DROP", "REPEATED_ERROR", "TEACHER_MARKED", "RECALCULATED",
  },
  ("lecture_interact", "action"): {"pause", "resume", "replay", "ask", "complete"},
  ("lesson_material_used", "materialType"): {"text", "image", "audio", "video", "exercise"},
  ("lesson_material_used", "result"): {"completed", "skipped", "helpful", "not_helpful"},
  ("mark_reviewed", "result"): {
    "correct_without_hint", "correct_with_hint", "incorrect", "still_confused", "postponed",
  },
}
INTEGER_RANGES = {
  ("answer_question", "difficulty"): (1, 5),
  ("answer_question", "timeSpentSec"): (0, 86400),
  ("answer_question", "hintCount"): (0, 100),
  ("finish_practice", "questionCount"): (1, 10000),
  ("finish_practice", "durationSec"): (0, 86400),
  ("explanation_feedback", "repeatCount"): (0, 100),
  ("mark_reviewed", "timeSpentSec"): (0, 86400),
  ("mark_reviewed", "hintCount"): (0, 100),
}
```

校验函数拒绝缺少必填键、未知键、布尔值伪装整数、超范围数值和非法枚举。`oldScore`、`newScore`、
`accuracy` 限制在 0 至 1；标识符匹配 `[A-Za-z0-9][A-Za-z0-9._:-]{0,63}`；`topic` 非空且最多
120 个 Unicode 码点。`preferenceValue` 根据 `preferenceKey` 使用下列集合：

```python
PREFERENCE_VALUES = {
  "content_mode": {"text", "image", "audio", "video", "exercise"},
  "explanation_style": {"step_by_step", "example_first", "concise", "detailed"},
  "learning_pace": {"slow", "moderate", "fast"},
}

IDENTIFIER_FIELDS = {
  ("request_explanation", "explainId"),
  ("explanation_feedback", "explainId"),
  ("lecture_interact", "lectureId"),
  ("lecture_interact", "chapterId"),
  ("lesson_material_used", "contentId"),
}
BOOLEAN_FIELDS = {
  ("answer_question", "isCorrect"),
  ("ask_doubt", "isFollowUp"),
}
DECIMAL_RANGES = {
  ("finish_practice", "accuracy"): (0.0, 1.0),
  ("weak_point_changed", "oldScore"): (0.0, 1.0),
  ("weak_point_changed", "newScore"): (0.0, 1.0),
}
IDENTIFIER_PATTERN = re.compile(r"[A-Za-z0-9][A-Za-z0-9._:-]{0,63}")


def validateEventData(eventType, data):
  required = EVENT_REQUIRED_KEYS[eventType]
  allowed = required | EVENT_OPTIONAL_KEYS.get(eventType, set())
  if missing := required - data.keys():
    raise ValueError(f"事件数据缺少必填字段: {sorted(missing)}")
  if unknown := data.keys() - allowed:
    raise ValueError(f"事件数据包含未知字段: {sorted(unknown)}")
  for typeAndField in BOOLEAN_FIELDS:
    if typeAndField[0] == eventType and type(data[typeAndField[1]]) is not bool:
      raise ValueError(f"事件字段必须是布尔值: {typeAndField[1]}")
  for (targetType, field), (minimum, maximum) in INTEGER_RANGES.items():
    if targetType == eventType:
      value = data[field]
      if type(value) is not int or not minimum <= value <= maximum:
        raise ValueError(f"事件整数字段超出范围: {field}")
  for (targetType, field), (minimum, maximum) in DECIMAL_RANGES.items():
    if targetType == eventType:
      value = data[field]
      if isinstance(value, bool) or not isinstance(value, (int, float)) or not minimum <= value <= maximum:
        raise ValueError(f"事件数值字段超出范围: {field}")
  for targetType, field in IDENTIFIER_FIELDS:
    if targetType == eventType and not IDENTIFIER_PATTERN.fullmatch(data[field]):
      raise ValueError(f"事件标识符格式无效: {field}")
  for (targetType, field), choices in EVENT_ENUMS.items():
    if targetType == eventType and data[field] not in choices:
      raise ValueError(f"事件枚举字段无效: {field}")
  if "confusionTag" in data and data["confusionTag"] not in CONFUSION_TAGS:
    raise ValueError("困惑标签无效")
  if eventType == "ask_doubt":
    topic = data["topic"]
    if not isinstance(topic, str) or not topic.strip() or len(topic) > 120:
      raise ValueError("疑问主题必须是1至120个字符")
  if eventType == "preference_changed":
    key = data["preferenceKey"]
    if key not in PREFERENCE_VALUES or data["preferenceValue"] not in PREFERENCE_VALUES[key]:
      raise ValueError("偏好设置无效")
```

响应模型使用 `Literal` 状态和可辨识联合：

```python
class ReadyResponse(ResponseBase):
  status: Literal["READY"] = "READY"
  profile: Profile
  knowledgeMastery: list[KnowledgeMastery]


class InsufficientDataResponse(ResponseBase):
  status: Literal["INSUFFICIENT_DATA"] = "INSUFFICIENT_DATA"
  knowledgeMastery: list[KnowledgeMastery] = Field(max_length=0)


class NoChangeResponse(ResponseBase):
  status: Literal["NO_CHANGE"] = "NO_CHANGE"


BuildProfileResponse = Annotated[
  ReadyResponse | InsufficientDataResponse | NoChangeResponse,
  Field(discriminator="status"),
]
```

- [ ] **Step 5: 运行契约测试**

Run: `uv run pytest tests/m6/test_schemas.py -q`

Expected: PASS。

- [ ] **Step 6: 提交契约模型**

```powershell
git add aitutor-backend-python/src/landppt/m6/schemas.py `
  aitutor-backend-python/tests/m6/__init__.py `
  aitutor-backend-python/tests/m6/conftest.py `
  aitutor-backend-python/tests/m6/test_schemas.py
git commit -m "feat(m6): 实现画像引擎v1契约模型"
```

### Task 2: 建立无状态引擎入口和三种响应状态

**Files:**
- Modify: `aitutor-backend-python/src/landppt/m6/profile_engine.py`
- Create: `aitutor-backend-python/tests/m6/test_profile_engine.py`

- [ ] **Step 1: 写状态和版本失败测试**

```python
from datetime import timedelta

import pytest

from landppt.m6.profile_engine import ALGORITHM_VERSION, buildProfile
from landppt.m6.schemas import BuildProfileRequest
from tests.m6.conftest import FIXED_NOW, makeEvent


@pytest.mark.asyncio
async def testReturnsNoChangeForEmptyWindow(copyRequest):
  payload = copyRequest()
  payload["events"] = []
  result = await buildProfile(BuildProfileRequest.model_validate(payload), evaluatedAt=FIXED_NOW)
  assert result.status == "NO_CHANGE"
  assert result.targetProfileVersion == payload["baseProfileVersion"]
  assert "profile" not in result.model_dump(exclude_none=True)


@pytest.mark.asyncio
async def testReturnsInsufficientDataForFourUsableEvents(copyRequest):
  payload = copyRequest()
  payload["eventWatermarkInclusive"] = 4
  payload["events"] = [makeEvent(index) for index in range(1, 5)]
  result = await buildProfile(BuildProfileRequest.model_validate(payload), evaluatedAt=FIXED_NOW)
  assert result.status == "INSUFFICIENT_DATA"
  assert result.targetProfileVersion == payload["baseProfileVersion"] + 1
  assert result.knowledgeMastery == []


def testAlgorithmVersionIsTraceable():
  assert ALGORITHM_VERSION == "m6-profile-v1.0.0"
```

- [ ] **Step 2: 运行测试并确认失败**

Run: `uv run pytest tests/m6/test_profile_engine.py -q`

Expected: FAIL，旧引擎没有异步 `buildProfile()` 和 v1.0 响应。

- [ ] **Step 3: 删除旧数据库画像实现并写最小骨架**

`profile_engine.py` 先实现状态、窗口和版本，不导入 `.db`、`.review_scheduler` 或 SQL：

```python
ALGORITHM_VERSION = "m6-profile-v1.0.0"
ACTIVE_EVENT_TYPES = frozenset({
  "answer_question",
  "finish_practice",
  "request_explanation",
  "explanation_feedback",
  "weak_point_changed",
  "lecture_interact",
  "ask_doubt",
  "mark_reviewed",
  "preference_changed",
})
MIN_PROFILE_EVENTS = 5
PROFILE_WINDOW_DAYS = 90


async def buildProfile(request, *, summaryClient=None, evaluatedAt=None):
  evaluatedAt = evaluatedAt or datetime.now(timezone.utc)
  windowStart = evaluatedAt - timedelta(days=PROFILE_WINDOW_DAYS)
  events = [
    event for event in request.events
    if event.eventType in ACTIVE_EVENT_TYPES and event.occurredAt >= windowStart
  ]
  base = responseBase(request, evaluatedAt)
  if not events:
    return NoChangeResponse(**base, targetProfileVersion=request.baseProfileVersion)
  if len(events) < MIN_PROFILE_EVENTS:
    return InsufficientDataResponse(
      **base,
      targetProfileVersion=request.baseProfileVersion + 1,
      knowledgeMastery=[],
    )
  profile = Profile(preferredContentModes=[], recentFocus=[], recentConfusions=[])
  return ReadyResponse(
    **base,
    targetProfileVersion=request.baseProfileVersion + 1,
    profile=profile,
    knowledgeMastery=[],
  )
```

- [ ] **Step 4: 运行状态测试**

Run: `uv run pytest tests/m6/test_profile_engine.py -q`

Expected: PASS 当前状态测试。

- [ ] **Step 5: 提交引擎骨架**

```powershell
git add aitutor-backend-python/src/landppt/m6/profile_engine.py `
  aitutor-backend-python/tests/m6/test_profile_engine.py
git commit -m "feat(m6): 建立无状态画像计算入口"
```

### Task 3: 实现 M1 掌握度和 M3 薄弱状态

**Files:**
- Modify: `aitutor-backend-python/src/landppt/m6/profile_engine.py`
- Modify: `aitutor-backend-python/tests/m6/test_profile_engine.py`

- [ ] **Step 1: 写掌握边界和 M3 冲突测试**

先在 `test_profile_engine.py` 新增确定性辅助函数：

```python
async def buildFromEvents(events, summaryClient=None):
  request = BuildProfileRequest.model_validate(makeRequest(events))
  return await buildProfile(request, summaryClient=summaryClient, evaluatedAt=FIXED_NOW)


def makeAnswerEvents(answerCount, correctCount, kpId=101, firstDbEventId=1):
  events = []
  for index in range(answerCount):
    event = makeEvent(
      dbEventId=firstDbEventId + index,
      kpId=kpId,
      data={
        "isCorrect": index < correctCount,
        "difficulty": 5,
        "timeSpentSec": 30,
        "hintCount": 0,
      },
    )
    event["occurredAt"] = (FIXED_NOW - timedelta(minutes=answerCount - index)).isoformat()
    events.append(event)
  return events


async def buildReadyProfile(answerCount, correctCount, summaryClient=None):
  return await buildFromEvents(makeAnswerEvents(answerCount, correctCount), summaryClient)


def makeM3Event(dbEventId, oldScore, newScore, occurredAt, kpId=101):
  event = makeEvent(
    dbEventId=dbEventId,
    eventType="weak_point_changed",
    sourceModule="M3",
    kpId=kpId,
    data={"oldScore": oldScore, "newScore": newScore, "reason": "RECALCULATED"},
  )
  event["occurredAt"] = occurredAt.isoformat()
  return event
```

然后新增以下测试；M3-only 用 5 条事件达到画像发布门槛，冲突测试使用 11 条全对 M1 事件：

```python
@pytest.mark.asyncio
async def testExactlyTenAnswersCannotBeMastered():
  result = await buildReadyProfile(answerCount=10, correctCount=10)
  assert result.knowledgeMastery[0].masteryStatus == "BASIC_MASTERY"


@pytest.mark.asyncio
async def testExactlyEightyPercentCannotBeMastered():
  result = await buildReadyProfile(answerCount=15, correctCount=12)
  assert result.knowledgeMastery[0].masteryStatus != "MASTERED"


@pytest.mark.asyncio
async def testOnlyM3CanProduceWeakStatus():
  events = [
    makeM3Event(index, 0.5, 0.7, FIXED_NOW - timedelta(minutes=6 - index))
    for index in range(1, 6)
  ]
  result = await buildFromEvents(events)
  assert result.knowledgeMastery[0].masteryStatus == "WEAK"


@pytest.mark.asyncio
async def testLowM1ScoreWithoutM3IsConsolidating():
  result = await buildReadyProfile(answerCount=8, correctCount=1)
  assert result.knowledgeMastery[0].masteryStatus == "CONSOLIDATING"


@pytest.mark.asyncio
async def testNewerMasteredEvidenceOverridesOlderM3Weakness():
  m3 = makeM3Event(1, 0.5, 0.7, FIXED_NOW - timedelta(hours=2))
  answers = makeAnswerEvents(11, 11, firstDbEventId=2)
  result = await buildFromEvents([m3, *answers])
  assert result.knowledgeMastery[0].masteryStatus == "MASTERED"


@pytest.mark.asyncio
async def testNewerM3WeaknessOverridesOlderMasteredEvidence():
  answers = makeAnswerEvents(11, 11)
  m3 = makeM3Event(12, 0.5, 0.7, FIXED_NOW)
  result = await buildFromEvents([*answers, m3])
  assert result.knowledgeMastery[0].masteryStatus == "WEAK"
```

- [ ] **Step 2: 运行新测试并确认失败**

Run: `uv run pytest tests/m6/test_profile_engine.py -q`

Expected: FAIL，尚未计算知识点结果。

- [ ] **Step 3: 实现掌握得分分量**

使用以下确定公式：

```python
MASTERY_COMPONENT_WEIGHTS = {
  "accuracy": 0.55,
  "difficulty": 0.15,
  "independence": 0.15,
  "recency": 0.15,
}


def calculateMasteryScore(evidence, evaluatedAt):
  accuracy = mean(item.isCorrect for item in evidence)
  m1 = [item for item in evidence if item.difficulty is not None]
  difficulty = (
    sum(item.isCorrect * item.difficulty for item in m1) / sum(item.difficulty for item in m1)
    if m1 else None
  )
  independence = mean(
    item.isCorrect * max(0.0, 1.0 - min(item.hintCount, 4) * 0.25)
    for item in evidence
  )
  recencyWeights = [2 ** (-(evaluatedAt - item.occurredAt).total_seconds() / 86400 / 30) for item in evidence]
  recency = sum(item.isCorrect * weight for item, weight in zip(evidence, recencyWeights)) / sum(recencyWeights)
  values = {"accuracy": accuracy, "difficulty": difficulty, "independence": independence, "recency": recency}
  activeWeight = sum(MASTERY_COMPONENT_WEIGHTS[key] for key, value in values.items() if value is not None)
  return round(sum(values[key] * MASTERY_COMPONENT_WEIGHTS[key] for key in values if values[key] is not None)
               / activeWeight, 4)
```

映射 M6 复习结果：`correct_without_hint`、`correct_with_hint` 为正确；`incorrect`、`still_confused` 为错误；
`postponed` 不计入直接表现证据。

- [ ] **Step 4: 实现 M3 和状态优先级**

```python
def resolveMasteryStatus(stats):
  m1Status = calculateM1Status(stats)
  latestM3 = stats.latestWeakPointEvent
  m3Weak = latestM3 is not None and latestM3.data["newScore"] >= 0.6
  if m1Status == "MASTERED" and m3Weak:
    if latestM3.occurredAt >= stats.lastDirectEvidenceAt:
      return "WEAK"
    return "MASTERED"
  if m3Weak:
    return "WEAK"
  if stats.directEvidenceCount < 5:
    return "INSUFFICIENT_EVIDENCE"
  if stats.masteryScore >= 0.6:
    return "BASIC_MASTERY"
  return "CONSOLIDATING"
```

M3-only 知识点使用 `1 - latest(newScore)` 作为 `masteryScore`；非 `WEAK` 时保持
`INSUFFICIENT_EVIDENCE`。趋势选择最后发生时间较新的 M1/M6 或 M3 趋势。

- [ ] **Step 5: 运行掌握度测试**

Run: `uv run pytest tests/m6/test_profile_engine.py -q`

Expected: PASS 当前全部测试。

- [ ] **Step 6: 提交掌握与薄弱算法**

```powershell
git add aitutor-backend-python/src/landppt/m6/profile_engine.py `
  aitutor-backend-python/tests/m6/test_profile_engine.py
git commit -m "feat(m6): 实现M1掌握与M3薄弱算法"
```

### Task 4: 实现 M2/M7 困惑 NLP 和近期关注点

**Files:**
- Modify: `aitutor-backend-python/src/landppt/m6/profile_engine.py`
- Modify: `aitutor-backend-python/tests/m6/test_profile_engine.py`

- [ ] **Step 1: 写 NLP、隐私和关注点失败测试**

先新增 M2/M7 测试事件工厂：

```python
def makeAskDoubtEvent(dbEventId, topic, kpId=101, isFollowUp=False):
  return makeEvent(
    dbEventId=dbEventId,
    eventType="ask_doubt",
    sourceModule="M7",
    kpId=kpId,
    data={
      "topic": topic,
      "confusionTag": "concept_unclear",
      "isFollowUp": isFollowUp,
    },
  )


def makeQuestionEvents(feedback, count):
  events = []
  for index in range(1, count + 1):
    if index % 2:
      events.append(makeEvent(
        dbEventId=index,
        eventType="request_explanation",
        sourceModule="M2",
        data={"explainId": f"explain-{index}", "reasonTag": "USER_REQUEST"},
      ))
    else:
      events.append(makeEvent(
        dbEventId=index,
        eventType="explanation_feedback",
        sourceModule="M2",
        data={"explainId": f"explain-{index - 1}", "feedback": feedback, "repeatCount": 0},
      ))
  return events
```

```python
@pytest.mark.parametrize(("topic", "expected"), [
  ("为什么这里要除以二", "原因不理解"),
  ("我不懂这个概念", "概念不理解"),
  ("这个符号是什么意思", "术语不理解"),
  ("how should I calculate this", "方法或步骤不理解"),
  ("what is the difference", "概念混淆"),
])
def testExtractsNormalizedConfusionWithoutLeakingText(topic, expected):
  assert extractConfusionType(topic) == expected


@pytest.mark.asyncio
async def testUnderstoodFeedbackOffsetsUnresolvedConfusion():
  events = makeQuestionEvents(feedback="understood", count=5)
  result = await buildFromEvents(events)
  assert result.profile.recentConfusions == []


@pytest.mark.asyncio
async def testM3DoesNotCreateConfusionPoint():
  events = [
    makeM3Event(index, 0.5, 0.7, FIXED_NOW - timedelta(minutes=6 - index))
    for index in range(1, 6)
  ]
  result = await buildFromEvents(events)
  assert result.profile.recentConfusions == []


@pytest.mark.asyncio
async def testReturnsAtMostFiveNormalizedFocusItems():
  events = [makeEvent(index, kpId=100 + index) for index in range(1, 9)]
  result = await buildFromEvents(events)
  assert len(result.profile.recentFocus) == 5
  assert result.profile.recentFocus[0].weight == 1.0
```

- [ ] **Step 2: 运行测试并确认失败**

Run: `uv run pytest tests/m6/test_profile_engine.py -q`

Expected: FAIL，困惑和关注仍为空。

- [ ] **Step 3: 实现本地 NLP 和困惑抵消**

```python
CONFUSION_PATTERNS = (
  (re.compile(r"为什么|为何|怎么会|\bwhy\b", re.IGNORECASE), "原因不理解"),
  (re.compile(r"不懂|没懂|不明白|don['’]?t understand", re.IGNORECASE), "概念不理解"),
  (re.compile(r"什么意思|含义|what does.+mean", re.IGNORECASE), "术语不理解"),
  (re.compile(r"怎么做|如何计算|步骤|how (?:do|should)", re.IGNORECASE), "方法或步骤不理解"),
  (re.compile(r"区别|差别|不同在哪里|difference", re.IGNORECASE), "概念混淆"),
)


def extractConfusionType(topic):
  normalized = " ".join(unicodedata.normalize("NFKC", topic).casefold().split())
  for pattern, detail in CONFUSION_PATTERNS:
    if pattern.search(normalized):
      return detail
  return None
```

按 `kpId` 聚合 M7 匹配、`confusionTag`、追问及 M2 反馈；`understood=-2`、`partly_understood=0.5`、
`still_confused=2`，衰减后分数大于 0 才输出。`detail` 只能来自标准化标签，最多 5 条。

提问理解得分与困惑强度分开计算：M2 `understood=1`、`partly_understood=0.5`、
`still_confused=0`，M7 追问记 0，首次 M7 提问不计好坏。没有反馈和追问时返回 `None`。

- [ ] **Step 4: 实现近期关注点**

所有当前范围且带 `kpId` 的事件贡献 `2 ** (-ageDays / 30)`，每个知识点累计后除以最高累计值，按权重和
最后发生时间排序，返回前 5 条。

- [ ] **Step 5: 运行 NLP 和关注点测试**

Run: `uv run pytest tests/m6/test_profile_engine.py -q`

Expected: PASS。

- [ ] **Step 6: 提交困惑和关注算法**

```powershell
git add aitutor-backend-python/src/landppt/m6/profile_engine.py `
  aitutor-backend-python/tests/m6/test_profile_engine.py
git commit -m "feat(m6): 实现困惑NLP与近期关注"
```

### Task 5: 实现学习节奏、明确偏好和综合学习情况

**Files:**
- Modify: `aitutor-backend-python/src/landppt/m6/profile_engine.py`
- Modify: `aitutor-backend-python/tests/m6/test_profile_engine.py`

- [ ] **Step 1: 写节奏、偏好和来源权重失败测试**

偏好测试工厂返回已经通过 v1.0 校验的事件：

```python
def makePreferenceEvents(*values):
  events = []
  for index, value in enumerate(values, start=1):
    event = makeEvent(
      dbEventId=index,
      eventType="preference_changed",
      sourceModule="M6",
      kpId=None,
      data={"preferenceKey": "content_mode", "preferenceValue": value},
    )
    event["occurredAt"] = (FIXED_NOW - timedelta(minutes=len(values) - index)).isoformat()
    events.append(ProfileEvent.model_validate(event))
  return events
```

```python
def testUsesMedianForLearningPace():
  assert inferLearningPace([10, 30, 50, 90, 1000]) == "moderate"


def testLatestExplicitPreferenceWins():
  events = makePreferenceEvents("text", "audio")
  profile = calculatePreferences(events)
  assert profile.preferredContentModes == ["audio"]


def testRenormalizesSourcesWhenM3IsMissing():
  situation = calculateLearningSituation(answerScore=0.8, questionScore=0.5, weakPointScore=None)
  assert situation.weights == {"answer": 0.6667, "question": 0.3333}


def testUsesM3SourceWhenAvailable():
  situation = calculateLearningSituation(answerScore=0.8, questionScore=0.5, weakPointScore=0.4)
  assert situation.weights == {"answer": 0.5333, "question": 0.2667, "weakPoint": 0.2}


def testOneSourceDoesNotProduceOverallConclusion():
  situation = calculateLearningSituation(answerScore=0.8, questionScore=None, weakPointScore=None)
  assert situation.score is None
  assert situation.label is None
```

- [ ] **Step 2: 运行测试并确认失败**

Run: `uv run pytest tests/m6/test_profile_engine.py -q`

Expected: FAIL，尚无对应特征函数。

- [ ] **Step 3: 实现节奏和最新偏好**

```python
def inferLearningPace(durations):
  if len(durations) < 5:
    return None
  medianSeconds = median(durations)
  if medianSeconds < 30:
    return "fast"
  if medianSeconds <= 90:
    return "moderate"
  return "slow"
```

最新 `preference_changed` 按 `occurredAt`、`dbEventId` 排序；`content_mode`、`explanation_style`、
`learning_pace` 分别写入对应字段，明确节奏覆盖中位数推断。

- [ ] **Step 4: 实现三来源综合分**

```python
LEARNING_SITUATION_WEIGHTS = {
  "answer": 0.40,
  "wrongQuestion": 0.25,
  "question": 0.20,
  "weakPoint": 0.15,
}


def normalizeAvailableWeights(scores):
  active = {key: value for key, value in scores.items() if value is not None}
  total = sum(LEARNING_SITUATION_WEIGHTS[key] for key in active)
  return {key: round(LEARNING_SITUATION_WEIGHTS[key] / total, 4) for key in active}
```

当前 `wrongQuestion` 始终为 `None`。M3 薄弱点来源得分为每个知识点最新 `1 - newScore` 的平均值。
少于两个有效来源时不生成内部综合分和标签。

做题表现使用知识点掌握得分的证据加权平均，每个知识点权重为 `min(evidenceCount, 20)`。同一
`sessionId` 已存在逐题 `answer_question` 时，不再加入对应 `finish_practice.accuracy`；没有逐题事件时，
练习正确率作为该会话的一条总体证据。

- [ ] **Step 5: 实现整体置信度**

```python
def calculateProfileConfidence(validEventCount, sourceScores):
  quantity = min(validEventCount / 20, 1.0)
  availableWeight = sum(
    LEARNING_SITUATION_WEIGHTS[key]
    for key, score in sourceScores.items()
    if score is not None
  )
  coverage = availableWeight / 0.75
  values = [score for score in sourceScores.values() if score is not None]
  consistency = 1.0 - (max(values) - min(values)) if len(values) >= 2 else 0.5
  return round(min(0.95, quantity * 0.5 + coverage * 0.3 + consistency * 0.2), 3)
```

- [ ] **Step 6: 运行特征测试**

Run: `uv run pytest tests/m6/test_profile_engine.py -q`

Expected: PASS。

- [ ] **Step 7: 提交完整画像特征**

```powershell
git add aitutor-backend-python/src/landppt/m6/profile_engine.py `
  aitutor-backend-python/tests/m6/test_profile_engine.py
git commit -m "feat(m6): 实现画像偏好与综合评分"
```

### Task 6: 接入可降级的异步大模型摘要

**Files:**
- Modify: `aitutor-backend-python/src/landppt/m6/profile_engine.py`
- Modify: `aitutor-backend-python/tests/m6/test_profile_engine.py`

- [ ] **Step 1: 写摘要成功、隐私和降级失败测试**

```python
class FakeSummaryClient:
  def __init__(self, response=None, error=None):
    self.response = response
    self.error = error
    self.payload = None

  async def createSummary(self, payload):
    self.payload = payload
    if self.error:
      raise self.error
    return self.response


@pytest.mark.asyncio
async def testUsesLlmSummaryWithoutSendingRawTopic():
  client = FakeSummaryClient("学生当前学习状态基本稳定，建议继续巩固近期知识点。")
  events = [*makeAnswerEvents(5, 4), makeAskDoubtEvent(6, "我不懂秘密内容")]
  result = await buildFromEvents(events, client)
  assert result.profile.summaryProfile.startswith("学生当前")
  assert "秘密内容" not in str(client.payload)
  assert "userId" not in client.payload


@pytest.mark.asyncio
async def testFallsBackWhenLlmFails():
  client = FakeSummaryClient(error=TimeoutError())
  result = await buildReadyProfile(5, 4, client)
  assert result.status == "READY"
  assert result.profile.summaryProfile
  assert "warnings" not in result.model_dump()


@pytest.mark.asyncio
async def testTruncatesSummaryToThreeHundredCodePoints():
  client = FakeSummaryClient("学" * 400)
  result = await buildReadyProfile(5, 4, client)
  assert len(result.profile.summaryProfile) == 300
```

- [ ] **Step 2: 运行测试并确认失败**

Run: `uv run pytest tests/m6/test_profile_engine.py -q`

Expected: FAIL，摘要仍为空。

- [ ] **Step 3: 实现可注入摘要客户端和本地模板**

```python
SUMMARY_PROMPT_VERSION = "m6-summary-prompt-v1"
DEFAULT_LLM_TIMEOUT_SECONDS = 10.0


class OpenAISummaryClient:
  def __init__(self, apiKey, baseUrl, model):
    self.model = model
    self.client = AsyncOpenAI(api_key=apiKey, base_url=baseUrl)

  async def createSummary(self, payload):
    response = await self.client.chat.completions.create(
      model=self.model,
      temperature=0.2,
      messages=buildSummaryMessages(payload),
    )
    return response.choices[0].message.content or ""
```

`generateSummary()` 使用 `asyncio.wait_for()` 执行客户端，超时读取
`PROFILE_LLM_TIMEOUT_SECONDS`。未配置 API Key、异常或空结果均记录不含隐私的 `WARN`，并调用
`buildTemplateSummary()`。发送载荷只包含结构化得分、状态、标准化困惑、关注点 ID、节奏和明确偏好。

本地模板使用确定字段拼接，并始终限制在 300 个 Unicode 码点：

```python
def buildTemplateSummary(payload):
  situation = payload.get("learningSituationLabel") or "当前综合学习情况证据仍在积累"
  mastery = payload.get("masterySummary") or "暂无足够的知识掌握证据"
  confusion = payload.get("confusionSummary") or "暂未识别到明确的未解决困惑"
  pace = payload.get("learningPace") or "学习节奏尚未形成稳定结论"
  suggestion = payload.get("suggestion") or "建议继续完成练习并在不理解时及时反馈"
  return f"{situation}。{mastery}。{confusion}。{pace}。{suggestion}。"[:300]
```

- [ ] **Step 4: 运行摘要测试**

Run: `uv run pytest tests/m6/test_profile_engine.py -q`

Expected: PASS。

- [ ] **Step 5: 提交摘要生成**

```powershell
git add aitutor-backend-python/src/landppt/m6/profile_engine.py `
  aitutor-backend-python/tests/m6/test_profile_engine.py
git commit -m "feat(m6): 接入可降级画像摘要"
```

### Task 7: 完成 READY 画像编排和精确序列化

**Files:**
- Modify: `aitutor-backend-python/src/landppt/m6/profile_engine.py`
- Modify: `aitutor-backend-python/tests/m6/test_profile_engine.py`

- [ ] **Step 1: 写端到端画像失败测试**

构造同时包含 M1、M2、M3、M4、M6、M7 的完整窗口：

```python
def makeFullRequestPayload():
  events = makeAnswerEvents(11, 10)
  events.extend([
    makeEvent(
      12,
      "request_explanation",
      "M2",
      data={"explainId": "explain-12", "reasonTag": "USER_REQUEST"},
    ),
    makeEvent(
      13,
      "explanation_feedback",
      "M2",
      data={"explainId": "explain-12", "feedback": "partly_understood", "repeatCount": 0},
    ),
    makeM3Event(14, 0.5, 0.3, FIXED_NOW - timedelta(minutes=4)),
    makeEvent(
      15,
      "lecture_interact",
      "M4",
      data={"lectureId": "lecture-1", "chapterId": "chapter-1", "action": "replay"},
    ),
    makeEvent(
      16,
      "preference_changed",
      "M6",
      kpId=None,
      data={"preferenceKey": "content_mode", "preferenceValue": "text"},
    ),
    makeAskDoubtEvent(17, "为什么这里这样计算"),
  ])
  return makeRequest(events)
```

然后断言：

```python
@pytest.mark.asyncio
async def testBuildsCompleteV1ReadyResponse():
  fullRequest = BuildProfileRequest.model_validate(makeFullRequestPayload())
  client = FakeSummaryClient("完整画像摘要")
  result = await buildProfile(fullRequest, summaryClient=client, evaluatedAt=FIXED_NOW)
  payload = result.model_dump(mode="json", exclude_none=True)
  assert payload["contractVersion"] == "1.0"
  assert payload["status"] == "READY"
  assert payload["algorithmVersion"] == "m6-profile-v1.0.0"
  assert payload["targetProfileVersion"] == payload["baseProfileVersion"] + 1
  assert len(payload["profile"]["recentFocus"]) <= 5
  assert all("算法" not in item["detail"] for item in payload["profile"]["recentConfusions"])
  assert all(item["algorithmVersion"] == payload["algorithmVersion"]
             for item in payload["knowledgeMastery"])
  assert "warnings" not in payload
```

- [ ] **Step 2: 运行测试并确认失败**

Run: `uv run pytest tests/m6/test_profile_engine.py::testBuildsCompleteV1ReadyResponse -q`

Expected: FAIL，特征尚未全部组装到响应。

- [ ] **Step 3: 编排完整画像**

在 `buildProfile()` 的 `READY` 分支按固定顺序组装：

```python
aggregated = aggregateEvents(events, evaluatedAt)
knowledgeMastery = calculateKnowledgeMastery(aggregated, evaluatedAt)
recentConfusions, questionScore = calculateConfusions(events, evaluatedAt)
recentFocus = calculateRecentFocus(events, evaluatedAt)
preferences = calculatePreferences(events)
learningPace = preferences.learningPace or inferLearningPace(aggregated.durations)
answerScore = calculateAnswerPerformance(aggregated, knowledgeMastery)
weakPointScore = calculateWeakPointSourceScore(aggregated.latestM3ByKnowledgePoint)
situation = calculateLearningSituation(answerScore, questionScore, weakPointScore)
confidence = calculateProfileConfidence(
  validEventCount=len(events),
  sourceScores={"answer": answerScore, "question": questionScore, "weakPoint": weakPointScore},
)
summaryPayload = buildSanitizedSummaryPayload(
  knowledgeMastery=knowledgeMastery,
  recentConfusions=recentConfusions,
  recentFocus=recentFocus,
  learningPace=learningPace,
  preferences=preferences,
  situation=situation,
  confidence=confidence,
)
summary = await generateSummary(summaryPayload, summaryClient)
profile = Profile(
  grade=None,
  preferredContentModes=preferences.preferredContentModes,
  preferredExplanationStyle=preferences.preferredExplanationStyle,
  learningPace=learningPace,
  recentFocus=recentFocus,
  recentConfusions=recentConfusions,
  summaryProfile=summary,
  confidence=confidence,
)
return ReadyResponse(
  **responseBase(request, evaluatedAt),
  targetProfileVersion=request.baseProfileVersion + 1,
  profile=profile,
  knowledgeMastery=sorted(knowledgeMastery, key=lambda item: item.kpId),
)
```

所有浮点字段在进入 Pydantic 模型前按契约精度舍入。

- [ ] **Step 4: 运行全部引擎测试**

Run: `uv run pytest tests/m6/test_profile_engine.py -q`

Expected: PASS。

- [ ] **Step 5: 提交画像编排**

```powershell
git add aitutor-backend-python/src/landppt/m6/profile_engine.py `
  aitutor-backend-python/tests/m6/test_profile_engine.py
git commit -m "feat(m6): 完成v1画像响应编排"
```

### Task 8: 将 FastAPI 路由切换到正式 v1.0 引擎

**Files:**
- Modify: `aitutor-backend-python/src/landppt/m6/router.py`
- Create: `aitutor-backend-python/tests/m6/test_router.py`

- [ ] **Step 1: 写路由失败测试**

使用独立 FastAPI 应用挂载 M6 router，避免访问真实数据库：

```python
from fastapi import FastAPI
from fastapi.testclient import TestClient

from landppt.m6.router import router

app = FastAPI()
app.include_router(router)
client = TestClient(app)


def testExposesOnlyFormalProfilePost(requestPayload):
  response = client.post("/api/internal/ai/build-profile", json=requestPayload)
  assert response.status_code == 200
  assert response.json()["contractVersion"] == "1.0"


def testReturnsBadRequestForInvalidContract(requestPayload):
  requestPayload["contractVersion"] = "0.9"
  response = client.post("/api/internal/ai/build-profile", json=requestPayload)
  assert response.status_code == 400


def testRemovesLegacyProfilePosts():
  assert client.post("/api/review/calculate-all").status_code == 404
  assert client.post("/api/events/process").status_code == 404


def testKeepsExistingReadRoutes():
  paths = {route.path for route in app.routes}
  assert "/api/user-profile/{userId}/knowledge-status" in paths
  assert "/api/user-profile/{userId}/timeline" in paths
```

- [ ] **Step 2: 运行路由测试并确认失败**

Run: `uv run pytest tests/m6/test_router.py -q`

Expected: FAIL，旧请求模型和旧路由仍存在。

- [ ] **Step 3: 重写画像 POST 路由**

删除旧画像函数导入和两个旧 POST 函数。使用自定义 `APIRoute` 将画像请求的 `RequestValidationError` 转为中文
HTTP 400；正式入口直接等待新引擎：

```python
class ContractValidationRoute(APIRoute):
  def get_route_handler(self):
    originalHandler = super().get_route_handler()

    async def contractHandler(request):
      try:
        return await originalHandler(request)
      except RequestValidationError as exception:
        raise HTTPException(status_code=400, detail="画像请求不符合v1.0契约") from exception

    return contractHandler


router = APIRouter(
  prefix="/api",
  tags=["M6 复习与画像"],
  route_class=ContractValidationRoute,
)


@router.post(
  "/internal/ai/build-profile",
  response_model=BuildProfileResponse,
  response_model_exclude_none=True,
)
async def buildProfileRoute(request: BuildProfileRequest):
  logger.info("开始构建M6画像: requestId=%s", request.requestId)
  try:
    return await buildProfile(request)
  except HTTPException:
    raise
  except Exception as exception:
    logger.error("M6画像引擎不可用: requestId=%s", request.requestId, exc_info=exception)
    raise HTTPException(status_code=503, detail="画像引擎暂时不可用") from exception
```

保留两个 GET 函数及其数据库查询，不在本任务重构。

- [ ] **Step 4: 运行路由测试**

Run: `uv run pytest tests/m6/test_router.py -q`

Expected: PASS。

- [ ] **Step 5: 提交路由切换**

```powershell
git add aitutor-backend-python/src/landppt/m6/router.py `
  aitutor-backend-python/tests/m6/test_router.py
git commit -m "feat(m6): 切换v1画像引擎路由"
```

### Task 9: 回归、覆盖率和交付检查

**Files:**
- Verify: all files listed in this plan

- [ ] **Step 1: 运行 M6 全部测试**

Run: `uv run pytest tests/m6 -q`

Expected: PASS，无真实网络和数据库访问。

- [ ] **Step 2: 运行旧用户画像回归测试**

Run: `uv run pytest tests/user_profile -q`

Expected: PASS，旧实验包未被本次重写破坏。

- [ ] **Step 3: 验证 M6 核心覆盖率**

Run:

```powershell
uv run pytest tests/m6 `
  --cov=landppt.m6.profile_engine `
  --cov=landppt.m6.schemas `
  --cov-report=term-missing `
  --cov-fail-under=80
```

Expected: PASS，总覆盖率不低于 80%。

- [ ] **Step 4: 运行语法和文本检查**

Run:

```powershell
$env:PYTHONPYCACHEPREFIX = Join-Path $env:TEMP "leapmind-m6-pycache"
uv run python -m compileall src/landppt/m6 tests/m6
git diff --check
```

Expected: Python 编译成功；`git diff --check` 无输出。

- [ ] **Step 5: 检查改动范围和旧数据库依赖**

Run:

```powershell
git status --short
git diff --name-only 2cbf27e..HEAD
rg -n "get_conn|event_collections|user_answers|wrong_question_book|user_weak_points" `
  src/landppt/m6/profile_engine.py
```

Expected: 只包含设计、计划、三个 M6 源文件和三个 M6 测试文件；最后一条搜索无输出。

- [ ] **Step 6: 最终提交仅包含验证修正**

如果验证阶段产生必要修正：

```powershell
git add aitutor-backend-python/src/landppt/m6 `
  aitutor-backend-python/tests/m6
git commit -m "test(m6): 完善画像引擎边界验证"
```

如果没有修正，不创建空提交。
