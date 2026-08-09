"""M6 无状态用户画像与知识点掌握度计算入口。"""

import asyncio
from dataclasses import dataclass, field
from datetime import datetime, timedelta, timezone
import json
import logging
import math
import os
import re
from statistics import median
from typing import Any
import unicodedata

from .schemas import (
  MAX_INT64,
  BuildProfileRequest,
  BuildProfileResponse,
  InsufficientDataResponse,
  KnowledgeMastery,
  NoChangeResponse,
  Profile,
  ProfileEvent,
  RecentConfusion,
  RecentFocus,
  ReadyResponse,
)


ALGORITHM_VERSION = "m6-profile-v1.1.0"
SUMMARY_PROMPT_VERSION = "m6-summary-prompt-v1"
DEFAULT_LLM_TIMEOUT_SECONDS = 10.0
MIN_SUMMARY_LENGTH = 150
MAX_SUMMARY_LENGTH = 300
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
  "wrong_question_changed",
})
MIN_PROFILE_EVENTS = 5
PROFILE_WINDOW_DAYS = 90
RECENCY_HALF_LIFE_DAYS = 30
MIN_DIRECT_EVIDENCE = 5
MIN_TREND_EVIDENCE = 6
MIN_MASTERED_ANSWERS = 10
MIN_MASTERED_ACCURACY = 0.8
MASTERED_SCORE = 0.8
BASIC_MASTERY_SCORE = 0.6
WEAK_SCORE = 0.6
MAX_HINT_PENALTY_COUNT = 4
HINT_PENALTY = 0.25
CONFLICT_CONFIDENCE_FACTOR = 0.75
EVIDENCE_CONFIDENCE_CAP = 20
MAX_RECENT_ITEMS = 5
MIN_PACE_DURATIONS = 5
FAST_PACE_MAX_SECONDS = 30
MODERATE_PACE_MAX_SECONDS = 90
LEARNING_SITUATION_PRIORS = {
  "answer": 0.24,
  "wrongQuestion": 0.332,
  "question": 0.168,
  "weakPoint": 0.26,
}
AVAILABLE_SITUATION_WEIGHT = 1.0
MIN_SITUATION_SOURCES = 2
PROFILE_CONFIDENCE_CAP = 0.95

logger = logging.getLogger(__name__)

CONFUSION_TAG_DETAILS = {
  "concept_unclear": "概念不理解",
  "formula_confusion": "公式理解困难",
  "step_unclear": "方法或步骤不理解",
  "application_difficulty": "应用困难",
  "careless_error": "粗心错误",
}
FEEDBACK_CONFUSION_WEIGHTS = {
  "understood": -2.0,
  "partly_understood": 0.5,
  "still_confused": 2.0,
}
FEEDBACK_UNDERSTANDING_VALUES = {
  "understood": 1.0,
  "partly_understood": 0.5,
  "still_confused": 0.0,
}
WRONG_QUESTION_STATUS_VALUES = {
  "UNRESOLVED": 0.0,
  "REVIEWING": 0.5,
  "RESOLVED": 1.0,
}

CONFUSION_PATTERNS = (
  ("原因不理解", re.compile(r"为什么|为何|怎么会|\bwhy\b")),
  ("概念不理解", re.compile(r"不懂|没懂|不明白|\bdon['’]t\s+understand\b")),
  ("术语不理解", re.compile(r"什么意思|含义|\bwhat\s+does\b.*?\bmean\b")),
  ("方法或步骤不理解", re.compile(r"怎么做|如何计算|步骤|\bhow\s+(?:do|should)\b")),
  ("概念混淆", re.compile(r"区别|差别|不同在哪里|\bdifference\b")),
)

SCORE_WEIGHTS = {
  "accuracy": 0.55,
  "difficulty": 0.15,
  "independence": 0.15,
  "recency": 0.15,
}
CORRECT_REVIEW_RESULTS = frozenset({"correct_without_hint", "correct_with_hint"})
INCORRECT_REVIEW_RESULTS = frozenset({"incorrect", "still_confused"})


class ProfileRequestError(ValueError):
  """表示画像请求包含无法执行的输入语义。"""


@dataclass(frozen=True)
class DirectEvidence:
  """一道题或一次有效复习形成的直接表现证据。"""

  dbEventId: int
  occurredAt: datetime
  isCorrect: bool
  hintCount: int
  difficulty: int | None
  isM1Answer: bool


@dataclass
class KnowledgeEvidence:
  """同一知识点在画像窗口内使用的证据集合。"""

  kpId: int
  direct: list[DirectEvidence] = field(default_factory=list)
  weakChanges: list[ProfileEvent] = field(default_factory=list)


@dataclass(frozen=True)
class TrendResult:
  """带证据时间的趋势计算结果。"""

  value: str
  occurredAt: datetime


@dataclass(frozen=True)
class ConfusionContribution:
  """单条事件形成的困惑强度或抵消证据。"""

  kpId: int
  occurredAt: datetime
  weight: float
  detail: str | None
  isPositiveEvidence: bool


@dataclass(frozen=True)
class ProfilePreferences:
  """画像中的显式偏好与可推断学习节奏。"""

  preferredContentModes: list[str]
  preferredExplanationStyle: str | None
  learningPace: str | None


@dataclass(frozen=True)
class LearningSituation:
  """供摘要阶段复用、但不直接扩展公开 Profile 契约的综合学习情况。"""

  scores: dict[str, float]
  weights: dict[str, float]
  score: float | None
  label: str | None


class OpenAISummaryClient:
  """调用 OpenAI 兼容接口生成画像摘要，并隔离 SDK 响应结构。"""

  def __init__(
    self,
    *,
    model: str,
    asyncClient: Any = None,
    apiKey: str | None = None,
    baseUrl: str | None = None,
  ) -> None:
    self.model = model
    if asyncClient is None:
      # 仅在配置完整且真正需要远程摘要时导入并创建 SDK 客户端。
      from openai import AsyncOpenAI

      asyncClient = AsyncOpenAI(api_key=apiKey, base_url=baseUrl)
    self.asyncClient = asyncClient

  async def createSummary(self, payload: dict[str, Any]) -> str:
    """发送固定提示词，并稳健提取第一条文本响应。"""
    response = await self.asyncClient.chat.completions.create(
      model=self.model,
      temperature=0.2,
      messages=buildSummaryMessages(payload),
    )
    choices = getattr(response, "choices", None)
    if not choices:
      return ""
    message = getattr(choices[0], "message", None)
    content = getattr(message, "content", None)
    return content if isinstance(content, str) else ""


def buildSummaryMessages(payload: dict[str, Any]) -> list[dict[str, str]]:
  """使用受控 JSON 构造固定消息，避免 Python 表示形式污染提示词。"""
  facts = json.dumps(payload, ensure_ascii=False, sort_keys=True)
  return [
    {
      "role": "system",
      "content": (
        "你是学习画像摘要助手。只能依据给定结构化事实输出150至300个中文Unicode字符；"
        "不得推断身份、敏感信息或固定学习类型，不得补造缺失证据。"
      ),
    },
    {
      "role": "user",
      "content": f"请概括综合情况、掌握、困惑、关注或节奏，并给出审慎建议。结构化事实JSON：{facts}",
    },
  ]


def createDefaultSummaryClient() -> OpenAISummaryClient | None:
  """仅在三项 OpenAI 配置均完整时创建默认客户端。"""
  apiKey = os.getenv("OPENAI_API_KEY", "").strip()
  baseUrl = os.getenv("OPENAI_BASE_URL", "").strip()
  model = os.getenv("OPENAI_MODEL", "").strip()
  if not apiKey or not baseUrl or not model:
    return None
  return OpenAISummaryClient(model=model, apiKey=apiKey, baseUrl=baseUrl)


def buildRecommendationLabels(
  mastery: list[KnowledgeMastery],
  recentConfusions: list[RecentConfusion],
  recentFocus: list[RecentFocus],
) -> list[str]:
  """根据已计算事实生成确定性建议标签。"""
  labels = []
  if any(item.masteryStatus in {"WEAK", "CONSOLIDATING"} for item in mastery):
    labels.append("优先巩固薄弱知识点")
  if any(item.trend == "DECLINING" for item in mastery):
    labels.append("复查掌握度下降知识点")
  if recentConfusions:
    labels.append("针对近期困惑安排讲解与练习")
  if recentFocus:
    labels.append("围绕近期关注知识点保持练习")
  if not mastery and not recentConfusions and not recentFocus:
    labels.append("继续积累有效学习证据")
  labels.append("根据新增证据动态调整学习计划")
  return labels


def buildSummaryPayload(
  mastery: list[KnowledgeMastery],
  profile: Profile,
  situation: LearningSituation,
) -> dict[str, Any]:
  """从已校验结果构造最小化、无身份标识和无事件原文的摘要事实。"""
  return {
    "promptVersion": SUMMARY_PROMPT_VERSION,
    "algorithmVersion": ALGORITHM_VERSION,
    "learningSituation": {
      "score": situation.score,
      "label": situation.label,
      "availableSourceWeights": situation.weights,
      "sourceScores": situation.scores,
    },
    "knowledgeMastery": [
      {
        "kpId": item.kpId,
        "score": item.masteryScore,
        "status": item.masteryStatus,
        "confidence": item.confidence,
        "trend": item.trend,
      }
      for item in mastery
    ],
    "recentConfusions": [
      {
        "kpId": item.kpId,
        "detail": item.detail,
        "evidenceCount": item.evidenceCount,
        "confidence": item.confidence,
      }
      for item in profile.recentConfusions
    ],
    "recentFocus": [
      {"kpId": item.kpId, "weight": item.weight}
      for item in profile.recentFocus
    ],
    "learningPace": profile.learningPace,
    "preferredContentModes": profile.preferredContentModes,
    "preferredExplanationStyle": profile.preferredExplanationStyle,
    "profileConfidence": profile.confidence,
    "recommendations": buildRecommendationLabels(
      mastery,
      profile.recentConfusions,
      profile.recentFocus,
    ),
  }


def normalizeSummary(value: str) -> str:
  """去除首尾并折叠全部异常空白。"""
  return " ".join(value.split())


def buildLocalSummary(payload: dict[str, Any]) -> str:
  """仅依据脱敏事实生成长度稳定的确定性中文摘要。"""
  situation = payload["learningSituation"]
  mastery = payload["knowledgeMastery"]
  confusions = payload["recentConfusions"]
  focus = payload["recentFocus"]
  clauses = []
  if situation["label"] is not None:
    clauses.append(f"综合学习情况为{situation['label']}，综合得分为{situation['score']:.2f}")
  else:
    clauses.append("综合学习情况的可用来源尚未达到形成稳定结论的要求，证据仍在积累")

  if mastery:
    statusCounts: dict[str, int] = {}
    for item in mastery:
      status = item["status"]
      statusCounts[status] = statusCounts.get(status, 0) + 1
    statusLabels = {
      "WEAK": "薄弱",
      "CONSOLIDATING": "巩固中",
      "BASIC_MASTERY": "基本掌握",
      "MASTERED": "已掌握",
      "INSUFFICIENT_EVIDENCE": "证据不足",
    }
    statusText = "、".join(
      f"{statusLabels[status]}{statusCounts[status]}项"
      for status in sorted(statusCounts)
    )
    clauses.append(f"当前掌握记录覆盖{len(mastery)}个知识点，状态分布为{statusText}")
  else:
    clauses.append("当前还没有可输出的知识点掌握记录，不宜据此判断具体知识点能力")

  if confusions:
    confusionText = "、".join(
      f"知识点{item['kpId']}的{item['detail']}"
      for item in confusions[:3]
    )
    clauses.append(f"近期标准化困惑主要包括{confusionText}，可优先结合对应证据复查")
  else:
    clauses.append("近期没有形成可输出的标准化困惑条目，但这不代表不存在理解困难")

  if focus:
    focusText = "、".join(f"知识点{item['kpId']}" for item in focus[:3])
    clauses.append(f"近期关注集中在{focusText}，后续可观察关注度与掌握变化是否一致")
  else:
    clauses.append("近期关注知识点信息不足，暂不推断学习重心")

  paceLabels = {"fast": "较快", "moderate": "适中", "slow": "较慢"}
  pace = payload["learningPace"]
  if pace is None:
    clauses.append("学习节奏证据不足，建议继续记录有效学习时长后再评估")
  else:
    clauses.append(f"现有数据对应的学习节奏为{paceLabels[pace]}，仍应随新增证据动态复核")

  recommendations = "、".join(payload["recommendations"])
  clauses.append(f"建议{recommendations}，并避免把阶段性结果固化为长期学习类型")
  clauses.append("本摘要只反映当前结构化证据，后续结论应随新数据更新，并保留不确定性")
  return normalizeSummary("。".join(clauses) + "。")[:MAX_SUMMARY_LENGTH]


def completeShortSummary(summary: str, localSummary: str) -> str:
  """保留模型开头并用不重复的本地事实补足最低长度。"""
  if localSummary.startswith(summary):
    combined = localSummary
  else:
    combined = f"{summary} {localSummary}"
  return normalizeSummary(combined)[:MAX_SUMMARY_LENGTH]


def resolveSummaryTimeout() -> float | None:
  """读取正有限超时秒数；非法值由调用方直接降级。"""
  rawValue = os.getenv("PROFILE_LLM_TIMEOUT_SECONDS")
  if rawValue is None:
    return DEFAULT_LLM_TIMEOUT_SECONDS
  try:
    value = float(rawValue)
  except ValueError:
    return None
  return value if math.isfinite(value) and value > 0 else None


async def generateSummary(
  payload: dict[str, Any],
  summaryClient: Any = None,
) -> str:
  """在单次超时边界内生成摘要，所有远程失败均安全降级。"""
  localSummary = buildLocalSummary(payload)
  client = summaryClient
  if client is None:
    try:
      client = createDefaultSummaryClient()
    except Exception:
      logger.warning("画像摘要客户端初始化失败，已使用本地模板；promptVersion=%s", SUMMARY_PROMPT_VERSION)
      return localSummary
  if client is None:
    return localSummary

  timeoutSeconds = resolveSummaryTimeout()
  if timeoutSeconds is None:
    logger.warning("画像摘要超时配置非法，已使用本地模板；promptVersion=%s", SUMMARY_PROMPT_VERSION)
    return localSummary
  try:
    rawSummary = await asyncio.wait_for(client.createSummary(payload), timeout=timeoutSeconds)
  except TimeoutError:
    logger.warning("画像摘要调用超时，已使用本地模板；promptVersion=%s", SUMMARY_PROMPT_VERSION)
    return localSummary
  except Exception:
    logger.warning("画像摘要调用异常，已使用本地模板；promptVersion=%s", SUMMARY_PROMPT_VERSION)
    return localSummary

  if not isinstance(rawSummary, str):
    logger.warning("画像摘要返回非字符串，已使用本地模板；promptVersion=%s", SUMMARY_PROMPT_VERSION)
    return localSummary
  summary = normalizeSummary(rawSummary)
  if not summary:
    logger.warning("画像摘要返回空内容，已使用本地模板；promptVersion=%s", SUMMARY_PROMPT_VERSION)
    return localSummary
  if len(summary) > MAX_SUMMARY_LENGTH:
    return summary[:MAX_SUMMARY_LENGTH]
  if len(summary) < MIN_SUMMARY_LENGTH:
    return completeShortSummary(summary, localSummary)
  return summary


def normalizeConfusionTopic(topic: str) -> str:
  """统一 Unicode、大小写和空白，供本地正则匹配使用。"""
  normalized = unicodedata.normalize("NFKC", topic).casefold()
  return " ".join(normalized.split())


def extractConfusionType(topic: str) -> str | None:
  """从提问主题提取标准困惑类型，不返回或保存主题原文。"""
  normalized = normalizeConfusionTopic(topic)
  for detail, pattern in CONFUSION_PATTERNS:
    if pattern.search(normalized):
      return detail
  return None


def calculateQuestionUnderstanding(events: list[ProfileEvent]) -> float | None:
  """计算讲解反馈和追问体现的提问理解度，首次提问不参与。"""
  values = []
  for event in events:
    if event.eventType == "explanation_feedback":
      values.append(FEEDBACK_UNDERSTANDING_VALUES[event.data["feedback"]])
    elif event.eventType == "ask_doubt" and event.data["isFollowUp"]:
      values.append(0.0)
  if not values:
    return None
  return max(0.0, min(sum(values) / len(values), 1.0))


def inferLearningPace(durations: list[int]) -> str | None:
  """按有效行为时长中位数推断学习节奏。"""
  if len(durations) < MIN_PACE_DURATIONS:
    return None
  medianDuration = median(durations)
  if medianDuration < FAST_PACE_MAX_SECONDS:
    return "fast"
  if medianDuration <= MODERATE_PACE_MAX_SECONDS:
    return "moderate"
  return "slow"


def collectLearningDurations(events: list[ProfileEvent]) -> list[int]:
  """只收集 M1 答题和 M6 复习行为中的有效耗时。"""
  return [
    event.data["timeSpentSec"]
    for event in events
    if event.eventType in {"answer_question", "mark_reviewed"}
  ]


def calculatePreferences(
  events: list[ProfileEvent],
  durations: list[int] | None = None,
) -> ProfilePreferences:
  """按偏好键分别选择最新显式设置，学习节奏允许由行为时长补足。"""
  latestByKey: dict[str, ProfileEvent] = {}
  for event in events:
    if event.eventType != "preference_changed":
      continue
    preferenceKey = event.data["preferenceKey"]
    current = latestByKey.get(preferenceKey)
    eventKey = (toUtc(event.occurredAt), getattr(event, "dbEventId"))
    if current is None:
      latestByKey[preferenceKey] = event
      continue
    currentKey = (toUtc(current.occurredAt), getattr(current, "dbEventId"))
    if eventKey > currentKey:
      latestByKey[preferenceKey] = event

  content = latestByKey.get("content_mode")
  style = latestByKey.get("explanation_style")
  pace = latestByKey.get("learning_pace")
  inferredPace = inferLearningPace(
    collectLearningDurations(events) if durations is None else durations,
  )
  return ProfilePreferences(
    preferredContentModes=[] if content is None else [content.data["preferenceValue"]],
    preferredExplanationStyle=None if style is None else style.data["preferenceValue"],
    learningPace=inferredPace if pace is None else pace.data["preferenceValue"],
  )


def calculateAnswerScore(
  events: list[ProfileEvent],
  mastery: list[KnowledgeMastery],
) -> float | None:
  """合并知识点直接表现与未被逐题事件覆盖的练习总体正确率。"""
  directCounts: dict[int, int] = {}
  answerSessions = {
    event.sessionId
    for event in events
    if event.eventType == "answer_question" and event.sessionId is not None
  }
  for event in events:
    if event.kpId is None:
      continue
    isDirect = event.eventType == "answer_question"
    if event.eventType == "mark_reviewed":
      isDirect = event.data["result"] in CORRECT_REVIEW_RESULTS | INCORRECT_REVIEW_RESULTS
    if isDirect:
      directCounts[event.kpId] = directCounts.get(event.kpId, 0) + 1

  masteryByKp = {item.kpId: item for item in mastery}
  weightedTotal = 0.0
  totalWeight = 0
  for kpId in sorted(directCounts):
    item = masteryByKp.get(kpId)
    if item is None:
      continue
    weight = min(directCounts[kpId], EVIDENCE_CONFIDENCE_CAP)
    weightedTotal += item.masteryScore * weight
    totalWeight += weight

  independentFinishes = []
  latestFinishBySession: dict[str, ProfileEvent] = {}
  for event in events:
    if event.eventType != "finish_practice":
      continue
    if event.sessionId is None:
      independentFinishes.append(event)
      continue
    if event.sessionId in answerSessions:
      continue
    current = latestFinishBySession.get(event.sessionId)
    eventKey = (toUtc(event.occurredAt), getattr(event, "dbEventId"))
    if current is None:
      latestFinishBySession[event.sessionId] = event
      continue
    currentKey = (toUtc(current.occurredAt), getattr(current, "dbEventId"))
    if eventKey > currentKey:
      latestFinishBySession[event.sessionId] = event

  overallFinishes = independentFinishes + [
    latestFinishBySession[sessionId]
    for sessionId in sorted(latestFinishBySession)
  ]
  for event in overallFinishes:
    weightedTotal += event.data["accuracy"]
    totalWeight += 1

  if totalWeight == 0:
    return None
  return round(weightedTotal / totalWeight, 4)


def calculateWeakPointScore(events: list[ProfileEvent]) -> float | None:
  """按知识点最新 M3 薄弱分的补数计算来源得分。"""
  latestByKp: dict[int, ProfileEvent] = {}
  for event in events:
    if event.eventType != "weak_point_changed" or event.kpId is None:
      continue
    current = latestByKp.get(event.kpId)
    eventKey = (toUtc(event.occurredAt), getattr(event, "dbEventId"))
    if current is None:
      latestByKp[event.kpId] = event
      continue
    currentKey = (toUtc(current.occurredAt), getattr(current, "dbEventId"))
    if eventKey > currentKey:
      latestByKp[event.kpId] = event
  if not latestByKp:
    return None
  values = [1 - latestByKp[kpId].data["newScore"] for kpId in sorted(latestByKp)]
  return round(sum(values) / len(values), 4)


def calculateWrongQuestionScore(events: list[ProfileEvent]) -> float | None:
  """按知识点内错题解决比例计算来源得分。"""
  latestByQuestion: dict[tuple[int, int], tuple[datetime, int, str]] = {}
  for event in events:
    if event.eventType != "wrong_question_changed" or event.kpId is None:
      continue
    qid = event.data["questionId"]
    key = (event.kpId, qid)
    eventKey = (toUtc(event.occurredAt), getattr(event, "dbEventId"))
    current = latestByQuestion.get(key)
    if current is None or eventKey > (current[0], current[1]):
      latestByQuestion[key] = (eventKey[0], eventKey[1], event.data["status"])
  if not latestByQuestion:
    return None
  values = [WRONG_QUESTION_STATUS_VALUES[status] for _, _, status in latestByQuestion.values()]
  return round(sum(values) / len(values), 4)


def calculateLearningSituation(
  answerScore: float | None,
  questionScore: float | None,
  weakPointScore: float | None,
  wrongQuestionScore: float | None = None,
) -> LearningSituation:
  """仅对可用来源重归一，并在至少两个来源时形成综合结论。"""
  candidates = {
    "answer": answerScore,
    "question": questionScore,
    "weakPoint": weakPointScore,
    "wrongQuestion": wrongQuestionScore,
  }
  validateSourceScores(candidates)
  scores = {name: value for name, value in candidates.items() if value is not None}
  totalPrior = sum(LEARNING_SITUATION_PRIORS[name] for name in scores)
  weights = {
    name: round(LEARNING_SITUATION_PRIORS[name] / totalPrior, 4)
    for name in scores
  } if totalPrior else {}
  if len(scores) < MIN_SITUATION_SOURCES:
    return LearningSituation(scores=scores, weights=weights, score=None, label=None)

  score = round(sum(
    value * LEARNING_SITUATION_PRIORS[name] / totalPrior
    for name, value in scores.items()
  ), 4)
  if score >= 0.8:
    label = "学习情况良好"
  elif score >= 0.6:
    label = "学习情况稳定"
  elif score >= 0.4:
    label = "学习情况待加强"
  else:
    label = "学习情况需优先关注"
  return LearningSituation(scores=scores, weights=weights, score=score, label=label)


def calculateLearningSituationFeatures(
  events: list[ProfileEvent],
  mastery: list[KnowledgeMastery],
) -> LearningSituation:
  """从当前事件和掌握度结果构造可供摘要阶段复用的综合特征。"""
  return calculateLearningSituation(
    calculateAnswerScore(events, mastery),
    calculateQuestionUnderstanding(events),
    calculateWeakPointScore(events),
    calculateWrongQuestionScore(events),
  )


def calculateProfileConfidence(validEventCount: int, situation: LearningSituation) -> float:
  """按数量、来源覆盖和来源一致性计算画像证据置信度。"""
  if not isinstance(validEventCount, int) or isinstance(validEventCount, bool) or validEventCount < 0:
    raise ValueError("有效事件数量必须是非负整数")
  validateSourceScores(situation.scores)
  quantity = min(validEventCount / EVIDENCE_CONFIDENCE_CAP, 1)
  availableWeight = sum(LEARNING_SITUATION_PRIORS[name] for name in situation.scores)
  coverage = availableWeight / AVAILABLE_SITUATION_WEIGHT
  values = list(situation.scores.values())
  consistency = max(0.0, min(1 - (max(values) - min(values)), 1.0)) if len(values) >= 2 else 0.5
  confidence = quantity * 0.5 + coverage * 0.3 + consistency * 0.2
  return round(min(PROFILE_CONFIDENCE_CAP, confidence), 3)


def validateSourceScores(scores: dict[str, float | None]) -> None:
  """拒绝范围外或非有限的综合学习情况来源分。"""
  for name, value in scores.items():
    if name not in LEARNING_SITUATION_PRIORS:
      raise ValueError(f"不支持的学习情况来源：{name}")
    if value is None:
      continue
    isNumber = isinstance(value, (int, float)) and not isinstance(value, bool)
    if not isNumber or not math.isfinite(value) or not 0 <= value <= 1:
      raise ValueError(f"{name} 来源分必须是 0 到 1 范围内的有限数值")


def calculateRecencyWeight(occurredAt: datetime, evaluatedAt: datetime) -> float:
  """按三十天半衰期计算事件权重。"""
  ageDays = max(0.0, (evaluatedAt - occurredAt).total_seconds() / 86_400)
  return 2 ** (-ageDays / RECENCY_HALF_LIFE_DAYS)


def mapExplanationRequests(events: list[ProfileEvent]) -> dict[str, list[ProfileEvent]]:
  """按讲解编号记录全部可关联请求，包括未携带知识点的请求。"""
  requests = [
    event
    for event in events
    if event.eventType == "request_explanation"
  ]
  requests.sort(key=lambda event: (toUtc(event.occurredAt), getattr(event, "dbEventId")))
  grouped: dict[str, list[ProfileEvent]] = {}
  for event in requests:
    grouped.setdefault(event.data["explainId"], []).append(event)
  return grouped


def resolveFeedbackKpId(
  feedback: ProfileEvent,
  requestsByExplainId: dict[str, list[ProfileEvent]],
) -> int | None:
  """优先使用反馈知识点，否则关联其之前最近的同编号请求。"""
  if feedback.kpId is not None:
    return feedback.kpId
  feedbackKey = (toUtc(feedback.occurredAt), getattr(feedback, "dbEventId"))
  candidates = [
    request
    for request in requestsByExplainId.get(feedback.data["explainId"], [])
    if (toUtc(request.occurredAt), getattr(request, "dbEventId")) < feedbackKey
  ]
  return candidates[-1].kpId if candidates else None


def collectConfusionContributions(events: list[ProfileEvent]) -> list[ConfusionContribution]:
  """仅从 M2 反馈和 M7 提问收集标准化困惑证据。"""
  requestsByExplainId = mapExplanationRequests(events)
  contributions = []
  for event in events:
    occurredAt = toUtc(event.occurredAt)
    if event.eventType == "explanation_feedback":
      kpId = resolveFeedbackKpId(event, requestsByExplainId)
      if kpId is None:
        continue
      weight = FEEDBACK_CONFUSION_WEIGHTS[event.data["feedback"]]
      contributions.append(ConfusionContribution(
        kpId=kpId,
        occurredAt=occurredAt,
        weight=weight,
        detail="概念不理解" if weight > 0 else None,
        isPositiveEvidence=weight > 0,
      ))
    elif event.eventType == "ask_doubt" and event.kpId is not None:
      matchedDetail = extractConfusionType(event.data["topic"])
      tagDetail = CONFUSION_TAG_DETAILS[event.data["confusionTag"]]
      details = [tagDetail]
      if matchedDetail is not None:
        details.append(matchedDetail)
      if event.data["isFollowUp"]:
        details.append(matchedDetail or tagDetail)
      contributions.extend(
        ConfusionContribution(
          kpId=event.kpId,
          occurredAt=occurredAt,
          weight=1.0,
          detail=detail,
          isPositiveEvidence=True,
        )
        for detail in details
      )
  return contributions


def buildRecentConfusions(
  events: list[ProfileEvent],
  evaluatedAt: datetime,
) -> list[RecentConfusion]:
  """聚合、抵消并排序近期困惑，仅输出标准安全标签。"""
  grouped: dict[int, list[ConfusionContribution]] = {}
  for contribution in collectConfusionContributions(events):
    grouped.setdefault(contribution.kpId, []).append(contribution)

  ranked = []
  for kpId, contributions in grouped.items():
    weighted = [
      (item, item.weight * calculateRecencyWeight(item.occurredAt, evaluatedAt))
      for item in contributions
    ]
    netScore = sum(score for _, score in weighted)
    if netScore <= 0:
      continue
    positives = [(item, score) for item, score in weighted if item.isPositiveEvidence]
    detailScores: dict[str, float] = {}
    detailTimes: dict[str, datetime] = {}
    for item, score in positives:
      assert item.detail is not None
      detailScores[item.detail] = detailScores.get(item.detail, 0.0) + score
      detailTimes[item.detail] = max(detailTimes.get(item.detail, item.occurredAt), item.occurredAt)
    detail = min(
      detailScores,
      key=lambda value: (-detailScores[value], -detailTimes[value].timestamp(), value),
    )
    positiveScore = sum(score for _, score in positives)
    positiveCount = len(positives)
    netRatio = min(netScore / positiveScore, 1.0)
    confidence = round(min(positiveCount / 5, 1.0) * netRatio, 3)
    lastOccurredAt = max(item.occurredAt for item in contributions)
    ranked.append((
      netScore,
      lastOccurredAt,
      RecentConfusion(
        kpId=kpId,
        detail=detail,
        evidenceCount=positiveCount,
        confidence=confidence,
        lastOccurredAt=lastOccurredAt,
      ),
    ))
  ranked.sort(key=lambda item: (-item[0], -item[1].timestamp(), item[2].kpId))
  return [item[2] for item in ranked[:MAX_RECENT_ITEMS]]


def buildRecentFocus(events: list[ProfileEvent], evaluatedAt: datetime) -> list[RecentFocus]:
  """按所有带知识点的有效事件计算近期关注权重。"""
  scores: dict[int, float] = {}
  latestTimes: dict[int, datetime] = {}
  for event in events:
    if event.kpId is None:
      continue
    occurredAt = toUtc(event.occurredAt)
    scores[event.kpId] = scores.get(event.kpId, 0.0) + calculateRecencyWeight(occurredAt, evaluatedAt)
    latestTimes[event.kpId] = max(latestTimes.get(event.kpId, occurredAt), occurredAt)
  if not scores:
    return []
  maximum = max(scores.values())
  orderedKpIds = sorted(
    scores,
    key=lambda kpId: (-scores[kpId], -latestTimes[kpId].timestamp(), kpId),
  )
  return [
    RecentFocus(kpId=kpId, weight=round(scores[kpId] / maximum, 3))
    for kpId in orderedKpIds[:MAX_RECENT_ITEMS]
  ]


def buildResponseBase(
  request: BuildProfileRequest,
  evaluatedAt: datetime,
  targetProfileVersion: int,
  status: str,
) -> dict[str, Any]:
  """构造三种响应共享且可追溯的契约字段。"""
  return {
    "contractVersion": request.contractVersion,
    "requestId": request.requestId,
    "userId": request.userId,
    "baseProfileVersion": request.baseProfileVersion,
    "targetProfileVersion": targetProfileVersion,
    "eventWatermarkInclusive": request.eventWatermarkInclusive,
    "status": status,
    "algorithmVersion": ALGORITHM_VERSION,
    "evaluatedAt": evaluatedAt,
  }


def toUtc(value: datetime) -> datetime:
  """将契约已校验的时间统一转换为 UTC。"""
  return value.astimezone(timezone.utc)


def collectKnowledgeEvidence(events: list[ProfileEvent]) -> dict[int, KnowledgeEvidence]:
  """仅收集 M1/M6 直接表现和 M3 薄弱变化证据。"""
  grouped: dict[int, KnowledgeEvidence] = {}
  for event in events:
    if event.kpId is None:
      continue
    evidence = grouped.setdefault(event.kpId, KnowledgeEvidence(kpId=event.kpId))
    if event.eventType == "answer_question":
      evidence.direct.append(DirectEvidence(
        dbEventId=getattr(event, "dbEventId"),
        occurredAt=toUtc(event.occurredAt),
        isCorrect=event.data["isCorrect"],
        hintCount=event.data["hintCount"],
        difficulty=event.data["difficulty"],
        isM1Answer=True,
      ))
    elif event.eventType == "mark_reviewed":
      result = event.data["result"]
      if result not in CORRECT_REVIEW_RESULTS | INCORRECT_REVIEW_RESULTS:
        continue
      hintCount = 0 if result == "correct_without_hint" else event.data["hintCount"]
      evidence.direct.append(DirectEvidence(
        dbEventId=getattr(event, "dbEventId"),
        occurredAt=toUtc(event.occurredAt),
        isCorrect=result in CORRECT_REVIEW_RESULTS,
        hintCount=hintCount,
        difficulty=None,
        isM1Answer=False,
      ))
    elif event.eventType == "weak_point_changed":
      evidence.weakChanges.append(event)

  return {
    kpId: evidence
    for kpId, evidence in grouped.items()
    if evidence.direct or evidence.weakChanges
  }


def latestWeakChange(evidence: KnowledgeEvidence) -> ProfileEvent | None:
  """按发生时间、数据库事件编号选择唯一最新 M3 事件。"""
  if not evidence.weakChanges:
    return None
  return max(
    evidence.weakChanges,
    key=lambda event: (toUtc(event.occurredAt), getattr(event, "dbEventId")),
  )


def calculateDirectScore(direct: list[DirectEvidence], evaluatedAt: datetime) -> float:
  """计算直接表现的四分量加权得分，并对缺失分量重新归一。"""
  count = len(direct)
  accuracy = sum(item.isCorrect for item in direct) / count
  independence = sum(
    int(item.isCorrect) * max(0, 1 - min(item.hintCount, MAX_HINT_PENALTY_COUNT) * HINT_PENALTY)
    for item in direct
  ) / count

  weightedCorrect = 0.0
  totalRecencyWeight = 0.0
  for item in direct:
    ageDays = max(0.0, (evaluatedAt - item.occurredAt).total_seconds() / 86_400)
    recencyWeight = 2 ** (-ageDays / RECENCY_HALF_LIFE_DAYS)
    weightedCorrect += int(item.isCorrect) * recencyWeight
    totalRecencyWeight += recencyWeight
  recency = weightedCorrect / totalRecencyWeight

  components = {
    "accuracy": accuracy,
    "independence": independence,
    "recency": recency,
  }
  difficultyEvidence = [item for item in direct if item.difficulty is not None]
  if difficultyEvidence:
    difficultyTotal = sum(item.difficulty for item in difficultyEvidence if item.difficulty is not None)
    difficultyCorrect = sum(
      int(item.isCorrect) * item.difficulty
      for item in difficultyEvidence
      if item.difficulty is not None
    )
    components["difficulty"] = difficultyCorrect / difficultyTotal

  usedWeight = sum(SCORE_WEIGHTS[name] for name in components)
  score = sum(SCORE_WEIGHTS[name] * value for name, value in components.items()) / usedWeight
  return round(score, 4)


def calculateMasteryScore(
  evidence: KnowledgeEvidence,
  latestWeak: ProfileEvent | None,
  evaluatedAt: datetime,
) -> float:
  """直接证据优先；只有 M3 时使用最新薄弱分的补数。"""
  if evidence.direct:
    return calculateDirectScore(evidence.direct, evaluatedAt)
  assert latestWeak is not None
  return round(1 - latestWeak.data["newScore"], 4)


def calculateDirectTrend(
  direct: list[DirectEvidence],
  evaluatedAt: datetime,
) -> TrendResult | None:
  """将按时间排序的直接证据等分，比较前后段统一掌握得分。"""
  if len(direct) < MIN_TREND_EVIDENCE:
    return None
  ordered = sorted(direct, key=lambda item: (item.occurredAt, getattr(item, "dbEventId")))
  splitIndex = len(ordered) // 2
  early = ordered[:splitIndex]
  late = ordered[splitIndex:]
  difference = calculateDirectScore(late, evaluatedAt) - calculateDirectScore(early, evaluatedAt)
  if difference > 0.1:
    trend = "IMPROVING"
  elif difference < -0.1:
    trend = "DECLINING"
  else:
    trend = "STABLE"
  return TrendResult(trend, max(item.occurredAt for item in direct))


def calculateWeakTrend(latestWeak: ProfileEvent | None) -> TrendResult | None:
  """用最新 M3 事件的薄弱分变化方向生成趋势。"""
  if latestWeak is None:
    return None
  oldScore = latestWeak.data["oldScore"]
  newScore = latestWeak.data["newScore"]
  if newScore < oldScore:
    trend = "IMPROVING"
  elif newScore > oldScore:
    trend = "DECLINING"
  else:
    trend = "STABLE"
  return TrendResult(trend, toUtc(latestWeak.occurredAt))


def resolveTrend(
  directTrend: TrendResult | None,
  weakTrend: TrendResult | None,
) -> tuple[str | None, bool]:
  """按最新时间选择趋势，同时间冲突时降级为稳定。"""
  if directTrend is None:
    return (weakTrend.value if weakTrend is not None else None), False
  if weakTrend is None:
    return directTrend.value, False
  if directTrend.occurredAt > weakTrend.occurredAt:
    return directTrend.value, False
  if weakTrend.occurredAt > directTrend.occurredAt:
    return weakTrend.value, False
  if directTrend.value == weakTrend.value:
    return directTrend.value, False
  return "STABLE", True


def resolveStatus(
  evidence: KnowledgeEvidence,
  latestWeak: ProfileEvent | None,
  masteryScore: float,
) -> tuple[str, bool]:
  """应用 M3 独占 WEAK 与 M1 严格 MASTERED 门槛。"""
  answers = [item for item in evidence.direct if item.isM1Answer]
  answerAccuracy = sum(item.isCorrect for item in answers) / len(answers) if answers else 0.0
  masteredCandidate = (
    len(answers) > MIN_MASTERED_ANSWERS
    and answerAccuracy > MIN_MASTERED_ACCURACY
    and masteryScore >= MASTERED_SCORE
  )
  weakCandidate = latestWeak is not None and latestWeak.data["newScore"] >= WEAK_SCORE

  if masteredCandidate and weakCandidate:
    latestDirectTime = max(item.occurredAt for item in evidence.direct)
    latestWeakTime = toUtc(latestWeak.occurredAt)
    if latestDirectTime > latestWeakTime:
      return "MASTERED", False
    if latestWeakTime > latestDirectTime:
      return "WEAK", False
    return "WEAK", True
  if weakCandidate:
    return "WEAK", False
  if masteredCandidate:
    return "MASTERED", False
  if len(evidence.direct) < MIN_DIRECT_EVIDENCE:
    return "INSUFFICIENT_EVIDENCE", False
  if masteryScore >= BASIC_MASTERY_SCORE:
    return "BASIC_MASTERY", False
  return "CONSOLIDATING", False


def buildKnowledgeMastery(
  evidence: KnowledgeEvidence,
  evaluatedAt: datetime,
) -> KnowledgeMastery:
  """将单知识点证据计算并序列化为公开契约。"""
  latestWeak = latestWeakChange(evidence)
  masteryScore = calculateMasteryScore(evidence, latestWeak, evaluatedAt)
  status, statusConflict = resolveStatus(evidence, latestWeak, masteryScore)
  trend, trendConflict = resolveTrend(
    calculateDirectTrend(evidence.direct, evaluatedAt),
    calculateWeakTrend(latestWeak),
  )
  evidenceCount = len(evidence.direct) + len(evidence.weakChanges)
  confidence = min(evidenceCount / EVIDENCE_CONFIDENCE_CAP, 1)
  if statusConflict or trendConflict:
    confidence *= CONFLICT_CONFIDENCE_FACTOR

  times = [item.occurredAt for item in evidence.direct]
  times.extend(toUtc(event.occurredAt) for event in evidence.weakChanges)
  return KnowledgeMastery(
    kpId=evidence.kpId,
    masteryScore=masteryScore,
    masteryStatus=status,
    confidence=round(confidence, 4),
    evidenceCount=evidenceCount,
    trend=trend,
    algorithmVersion=ALGORITHM_VERSION,
    windowStart=min(times),
    windowEnd=max(times),
    updatedAt=max(times),
  )


def calculateKnowledgeMastery(
  events: list[ProfileEvent],
  evaluatedAt: datetime,
) -> list[KnowledgeMastery]:
  """按知识点编号升序生成全部掌握度。"""
  grouped = collectKnowledgeEvidence(events)
  return [buildKnowledgeMastery(grouped[kpId], evaluatedAt) for kpId in sorted(grouped)]


async def buildProfile(
  request: BuildProfileRequest,
  *,
  summaryClient: Any = None,
  evaluatedAt: datetime | None = None,
) -> BuildProfileResponse:
  """在 90 天窗口内计算画像三态，不读取数据库或保存任何状态。"""
  evaluationTime = datetime.now(timezone.utc) if evaluatedAt is None else evaluatedAt
  if evaluationTime.tzinfo is None or evaluationTime.utcoffset() is None:
    raise ProfileRequestError("evaluatedAt 必须包含时区信息")
  evaluationTime = evaluationTime.astimezone(timezone.utc)
  windowStart = evaluationTime - timedelta(days=PROFILE_WINDOW_DAYS)
  # M5 事件仍属于合法输入，但不作为本画像算法的证据。
  usableEvents = [
    event
    for event in request.events
    if (
      event.eventType in ACTIVE_EVENT_TYPES
      and windowStart <= event.occurredAt <= evaluationTime
    )
  ]

  if not usableEvents:
    return NoChangeResponse(**buildResponseBase(
      request,
      evaluationTime,
      request.baseProfileVersion,
      "NO_CHANGE",
    ))

  if request.baseProfileVersion == MAX_INT64:
    raise ProfileRequestError("画像版本已达到上限，无法继续推进")

  targetVersion = request.baseProfileVersion + 1
  if len(usableEvents) < MIN_PROFILE_EVENTS:
    return InsufficientDataResponse(
      **buildResponseBase(request, evaluationTime, targetVersion, "INSUFFICIENT_DATA"),
      knowledgeMastery=[],
    )

  mastery = calculateKnowledgeMastery(usableEvents, evaluationTime)
  preferences = calculatePreferences(usableEvents)
  situation = calculateLearningSituationFeatures(usableEvents, mastery)
  profile = Profile(
    preferredContentModes=preferences.preferredContentModes,
    preferredExplanationStyle=preferences.preferredExplanationStyle,
    learningPace=preferences.learningPace,
    recentFocus=buildRecentFocus(usableEvents, evaluationTime),
    recentConfusions=buildRecentConfusions(usableEvents, evaluationTime),
    confidence=calculateProfileConfidence(len(usableEvents), situation),
  )
  summaryPayload = buildSummaryPayload(mastery, profile, situation)
  profile = profile.model_copy(update={
    "summaryProfile": await generateSummary(summaryPayload, summaryClient),
  })
  return ReadyResponse(
    **buildResponseBase(request, evaluationTime, targetVersion, "READY"),
    profile=profile,
    knowledgeMastery=mastery,
  )
