"""用户画像领域能力编排引擎。"""

from collections.abc import Iterable
from datetime import datetime

from .aggregator import (
  MASTERY_ALGORITHM_VERSION,
  aggregateUserEvents,
  calculateMastery,
)
from .confusion import (
  CONFUSION_ALGORITHM_VERSION,
  extractConfusionSignals,
)
from .preferences import (
  LEARNING_PREFERENCE_ALGORITHM_VERSION,
  calculateLearningPreference,
)
from .models import (
  ConfusionPoint,
  DataQualityWarning,
  EventSource,
  LearningEvent,
  ProfileBuildStatus,
  UserProfileMetadata,
  UserProfileResult,
)

PROFILE_ENGINE_ALGORITHM_VERSION = "profile-engine-v1"
CONFUSION_TEXT_EXCERPT_LIMIT = 80


def buildUserProfile(
  events: Iterable[LearningEvent],
  userId: int,
  generatedAt: datetime,
) -> UserProfileResult:
  """生成指定用户的画像结果。"""
  if (
    not isinstance(generatedAt, datetime)
    or generatedAt.tzinfo is None
    or generatedAt.utcoffset() is None
  ):
    raise ValueError("画像生成时间必须包含时区")

  eventList = tuple(events)
  aggregation = aggregateUserEvents(eventList, userId)
  uniqueEvents, duplicateEventIds = _keepFirstEvents(eventList)
  learningPreference = calculateLearningPreference(uniqueEvents)
  masteryByKnowledgePoint = {
    knowledgePointKey: calculateMastery(evidence)
    for knowledgePointKey, evidence in sorted(
      aggregation.evidenceByKnowledgePoint.items(),
    )
  }
  confusionPoints, confusionWarnings = _extractConfusionPoints(uniqueEvents)
  eventTimes = tuple(event.occurredAt for event in uniqueEvents)
  missingSources = tuple(
    source
    for source in EventSource
    if aggregation.sourceRecordCounts[source] == 0
  )
  profileWarnings = (
    (
      DataQualityWarning(
        code="NO_LEARNING_DATA",
        message="暂无学习数据，无法生成有效画像",
      ),
    )
    if not eventTimes
    else ()
  )
  duplicateWarnings = tuple(
    DataQualityWarning(
      code="DUPLICATE_EVENT_ID",
      message="检测到重复事件 ID，已保留第一次出现的事件",
      eventId=eventId,
    )
    for eventId in duplicateEventIds
  )
  dataQualityWarnings = (
    profileWarnings
    + duplicateWarnings
    + confusionWarnings
    + learningPreference.dataQualityWarnings
  )

  return UserProfileResult(
    userId=userId,
    overallStatus=(
      ProfileBuildStatus.INSUFFICIENT_DATA
      if not eventTimes
      else (
        ProfileBuildStatus.PARTIAL
        if missingSources
        else ProfileBuildStatus.READY
      )
    ),
    aggregation=aggregation,
    masteryByKnowledgePoint=masteryByKnowledgePoint,
    confusionPoints=confusionPoints,
    missingSources=missingSources,
    dataQualityWarnings=dataQualityWarnings,
    learningPreference=learningPreference,
    metadata=UserProfileMetadata(
      generatedAt=generatedAt,
      firstEventAt=min(eventTimes) if eventTimes else None,
      lastEventAt=max(eventTimes) if eventTimes else None,
      algorithmVersions={
        "profileEngine": PROFILE_ENGINE_ALGORITHM_VERSION,
        "mastery": MASTERY_ALGORITHM_VERSION,
        "confusion": CONFUSION_ALGORITHM_VERSION,
        "learningPreference": LEARNING_PREFERENCE_ALGORITHM_VERSION,
      },
    ),
  )


def _keepFirstEvents(
  events: tuple[LearningEvent, ...],
) -> tuple[tuple[LearningEvent, ...], tuple[str, ...]]:
  """按事件 ID 去重并保留第一次出现的事件。"""
  seenEventIds = set()
  uniqueEvents = []
  duplicateEventIds = []

  for event in events:
    if event.eventId in seenEventIds:
      if event.eventId not in duplicateEventIds:
        duplicateEventIds.append(event.eventId)
      continue
    seenEventIds.add(event.eventId)
    uniqueEvents.append(event)

  return tuple(uniqueEvents), tuple(duplicateEventIds)


def _extractConfusionPoints(
  events: tuple[LearningEvent, ...],
) -> tuple[
  tuple[ConfusionPoint, ...],
  tuple[DataQualityWarning, ...],
]:
  """从对话事件中提取命中显式模式的困惑点。"""
  confusionPoints = []
  warnings = []
  for event in events:
    if event.source != EventSource.CONVERSATION_MESSAGES:
      continue

    try:
      extraction = extractConfusionSignals(event.data.get("text"))
    except ValueError:
      warnings.append(
        DataQualityWarning(
          code="INVALID_CONVERSATION_TEXT",
          message="对话事件缺少有效文本，已跳过困惑提取",
          eventId=event.eventId,
        ),
      )
      continue

    if extraction.isConfused:
      confusionPoints.append(
        ConfusionPoint(
          eventId=event.eventId,
          knowledgePointKey=event.knowledgePointKey,
          occurredAt=event.occurredAt,
          matchedPatterns=extraction.matchedPatterns,
          textExcerpt=_truncateText(extraction.normalizedText),
        ),
      )

  return tuple(confusionPoints), tuple(warnings)


def _truncateText(text: str) -> str:
  """将对话文本截断为适合画像展示的短片段。"""
  if len(text) <= CONFUSION_TEXT_EXCERPT_LIMIT:
    return text
  return f"{text[:CONFUSION_TEXT_EXCERPT_LIMIT - 1]}…"
