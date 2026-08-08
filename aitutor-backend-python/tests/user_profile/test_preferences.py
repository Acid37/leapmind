"""四维学习偏好向量测试。"""

import unittest
from datetime import datetime, timezone

from landppt.user_profile.models import (
  EventSource,
  EventType,
  LearningEvent,
  LearningMode,
  LearningPreferenceStatus,
)
from landppt.user_profile.preferences import calculateLearningPreference

_UNSET = object()


class CalculateLearningPreferenceTest(unittest.TestCase):
  """验证统一学习事件到四维偏好向量的转换。"""

  def makeEvent(self, eventId, learningMode=_UNSET):
    """构造带可选学习模式的统一事件。"""
    data = (
      {}
      if learningMode is _UNSET
      else {"learningMode": learningMode}
    )
    return LearningEvent(
      eventId=eventId,
      userId=1001,
      source=EventSource.USER_ANSWERS,
      eventType=EventType.ANSWER_SUBMITTED,
      occurredAt=datetime(2026, 7, 25, 9, 0, tzinfo=timezone.utc),
      knowledgePointKey="导数定义",
      data=data,
    )

  def testCalculatesNormalizedScoresFromValidModes(self):
    """十条有效证据应生成归一化向量和优势维度。"""
    modes = (
      [LearningMode.VISUAL] * 4
      + [LearningMode.AUDITORY]
      + [LearningMode.READING] * 3
      + [LearningMode.KINESTHETIC] * 2
    )
    events = [
      self.makeEvent(f"event-{index}", mode.value)
      for index, mode in enumerate(modes)
    ]

    result = calculateLearningPreference(events)

    self.assertEqual(LearningPreferenceStatus.READY, result.status)
    self.assertEqual(10, result.evidenceCount)
    self.assertEqual(0, result.ignoredEvidenceCount)
    self.assertAlmostEqual(0.4, result.scores[LearningMode.VISUAL])
    self.assertAlmostEqual(0.1, result.scores[LearningMode.AUDITORY])
    self.assertAlmostEqual(0.3, result.scores[LearningMode.READING])
    self.assertAlmostEqual(0.2, result.scores[LearningMode.KINESTHETIC])
    self.assertEqual((LearningMode.VISUAL,), result.dominantDimensions)
    self.assertEqual(
      "learning-preference-count-v1",
      result.algorithmVersion,
    )

  def testKeepsVectorButHidesDominantDimensionWhenInsufficient(self):
    """不足十条证据时应保留向量但不输出优势维度。"""
    events = [
      self.makeEvent(f"visual-{index}", "visual")
      for index in range(3)
    ]

    result = calculateLearningPreference(events)

    self.assertEqual(
      LearningPreferenceStatus.INSUFFICIENT_DATA,
      result.status,
    )
    self.assertAlmostEqual(1.0, result.scores[LearningMode.VISUAL])
    self.assertEqual((), result.dominantDimensions)

  def testReturnsZeroVectorForNoEvents(self):
    """没有证据时应返回四个零分。"""
    result = calculateLearningPreference([])

    self.assertEqual(0, result.evidenceCount)
    self.assertEqual(
      {mode: 0.0 for mode in LearningMode},
      result.scores,
    )
    self.assertEqual((), result.dominantDimensions)

  def testReturnsAllTiedDominantDimensions(self):
    """多个维度并列最高时不得强行选择单一维度。"""
    events = [
      self.makeEvent(f"visual-{index}", "visual")
      for index in range(5)
    ]
    events.extend([
      self.makeEvent(f"reading-{index}", "reading")
      for index in range(5)
    ])

    result = calculateLearningPreference(events)

    self.assertEqual(
      (LearningMode.VISUAL, LearningMode.READING),
      result.dominantDimensions,
    )

  def testIgnoresMissingAndWarnsForInvalidMode(self):
    """缺失和非法模式均应忽略，但只有非法值生成警告。"""
    events = [
      self.makeEvent("valid", "visual"),
      self.makeEvent("missing"),
      self.makeEvent("invalid", "video"),
    ]

    result = calculateLearningPreference(events)

    self.assertEqual(1, result.evidenceCount)
    self.assertEqual(2, result.ignoredEvidenceCount)
    self.assertEqual(1, len(result.dataQualityWarnings))
    warning = result.dataQualityWarnings[0]
    self.assertEqual("INVALID_LEARNING_MODE", warning.code)
    self.assertEqual("invalid", warning.eventId)

  def testUsesFirstOccurrenceOfDuplicateEventId(self):
    """重复事件不得重复影响四维向量。"""
    events = [
      self.makeEvent("duplicate", "visual"),
      self.makeEvent("duplicate", "auditory"),
    ]

    result = calculateLearningPreference(events)

    self.assertEqual(1, result.evidenceCount)
    self.assertAlmostEqual(1.0, result.scores[LearningMode.VISUAL])
    self.assertAlmostEqual(0.0, result.scores[LearningMode.AUDITORY])


if __name__ == "__main__":
  unittest.main()
