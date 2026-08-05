"""四维学习偏好向量计算。"""

from collections.abc import Iterable

from .models import (
  DataQualityWarning,
  LearningEvent,
  LearningMode,
  LearningPreferenceStatus,
  LearningPreferenceVector,
)

MINIMUM_PREFERENCE_EVIDENCE_COUNT = 10
LEARNING_PREFERENCE_ALGORITHM_VERSION = (
  "learning-preference-count-v1"
)


def calculateLearningPreference(
  events: Iterable[LearningEvent],
) -> LearningPreferenceVector:
  """按唯一学习行为事件生成归一化四维偏好向量。"""
  counts = {mode: 0 for mode in LearningMode}
  seenEventIds = set()
  ignoredEvidenceCount = 0
  warnings = []

  for event in events:
    if event.eventId in seenEventIds:
      continue
    seenEventIds.add(event.eventId)

    rawMode = event.data.get("learningMode")
    if rawMode is None:
      ignoredEvidenceCount += 1
      continue

    try:
      learningMode = LearningMode(rawMode)
    except (TypeError, ValueError):
      ignoredEvidenceCount += 1
      warnings.append(
        DataQualityWarning(
          code="INVALID_LEARNING_MODE",
          message="学习模式取值不受支持，已跳过偏好计算",
          eventId=event.eventId,
        ),
      )
      continue

    counts[learningMode] += 1

  evidenceCount = sum(counts.values())
  scores = _normalizeScores(counts, evidenceCount)
  status = (
    LearningPreferenceStatus.READY
    if evidenceCount >= MINIMUM_PREFERENCE_EVIDENCE_COUNT
    else LearningPreferenceStatus.INSUFFICIENT_DATA
  )
  dominantDimensions = _findDominantDimensions(scores, status)

  return LearningPreferenceVector(
    status=status,
    scores=scores,
    evidenceCount=evidenceCount,
    ignoredEvidenceCount=ignoredEvidenceCount,
    dominantDimensions=dominantDimensions,
    minimumEvidenceCount=MINIMUM_PREFERENCE_EVIDENCE_COUNT,
    algorithmVersion=LEARNING_PREFERENCE_ALGORITHM_VERSION,
    dataQualityWarnings=tuple(warnings),
  )


def _normalizeScores(
  counts: dict[LearningMode, int],
  evidenceCount: int,
) -> dict[LearningMode, float]:
  """将四维计数归一化为和为一的向量。"""
  if evidenceCount == 0:
    return {mode: 0.0 for mode in LearningMode}
  return {
    mode: counts[mode] / evidenceCount
    for mode in LearningMode
  }


def _findDominantDimensions(
  scores: dict[LearningMode, float],
  status: LearningPreferenceStatus,
) -> tuple[LearningMode, ...]:
  """数据充足时返回所有并列最高的维度。"""
  if status != LearningPreferenceStatus.READY:
    return ()

  maximumScore = max(scores.values())
  return tuple(
    mode
    for mode in LearningMode
    if scores[mode] == maximumScore
  )
