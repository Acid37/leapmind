"""用户画像引擎串联测试。"""

import importlib.util
import unittest
from dataclasses import is_dataclass
from datetime import datetime, timedelta, timezone

from landppt.user_profile import profile_engine
from landppt.user_profile.models import (
  EventSource,
  EventType,
  LearningEvent,
  ProfileStatus,
)


class ProfileEngineModuleTest(unittest.TestCase):
  """验证画像引擎模块的公开入口。"""

  def testProvidesProfileEngineModule(self):
    """用户画像包应提供独立的画像引擎模块。"""
    moduleSpec = importlib.util.find_spec(
      "landppt.user_profile.profile_engine",
    )

    self.assertIsNotNone(moduleSpec)

  def testProvidesBuildUserProfileFunction(self):
    """画像引擎应提供统一的画像生成函数。"""
    self.assertTrue(hasattr(profile_engine, "buildUserProfile"))


class BuildUserProfileTest(unittest.TestCase):
  """验证画像引擎对领域能力的统一编排。"""

  def makeEvent(
    self,
    eventId,
    source,
    eventType,
    occurredAt,
    data=None,
    knowledgePointKey="导数定义",
  ):
    """构造画像引擎使用的统一学习事件。"""
    return LearningEvent(
      eventId=eventId,
      userId=1001,
      source=source,
      eventType=eventType,
      occurredAt=occurredAt,
      knowledgePointKey=knowledgePointKey,
      data=data or {},
    )

  def testBuildsReadyProfileFromFourEventSources(self):
    """四类来源齐全时应串联统计、掌握度、困惑点和元数据。"""
    firstEventAt = datetime(2026, 7, 25, 9, 0, tzinfo=timezone.utc)
    generatedAt = datetime(2026, 7, 25, 10, 0, tzinfo=timezone.utc)
    events = [
      self.makeEvent(
        f"answer-{index}",
        EventSource.USER_ANSWERS,
        EventType.ANSWER_SUBMITTED,
        firstEventAt + timedelta(minutes=index),
        data={"isCorrect": index <= 9},
      )
      for index in range(1, 12)
    ]
    events.extend([
      self.makeEvent(
        "wrong-1",
        EventSource.WRONG_QUESTION_BOOK,
        EventType.WRONG_QUESTION_ADDED,
        firstEventAt + timedelta(minutes=12),
      ),
      self.makeEvent(
        "message-1",
        EventSource.CONVERSATION_MESSAGES,
        EventType.CONVERSATION_MESSAGE,
        firstEventAt + timedelta(minutes=13),
        data={"text": "我不懂这个符号是什么意思"},
      ),
      self.makeEvent(
        "weak-1",
        EventSource.USER_WEAK_POINTS,
        EventType.WEAK_POINT_UPDATED,
        firstEventAt + timedelta(minutes=14),
      ),
    ])

    result = profile_engine.buildUserProfile(
      events,
      userId=1001,
      generatedAt=generatedAt,
    )

    self.assertTrue(is_dataclass(result))
    self.assertEqual("ready", result.overallStatus.value)
    self.assertEqual(14, result.aggregation.totalEventCount)
    self.assertEqual((), result.missingSources)
    mastery = result.masteryByKnowledgePoint["导数定义"]
    self.assertEqual(ProfileStatus.MASTERED, mastery.status)
    self.assertEqual(11, mastery.sampleCount)
    self.assertAlmostEqual(9 / 11, mastery.correctRate)
    self.assertEqual(1, len(result.confusionPoints))
    confusionPoint = result.confusionPoints[0]
    self.assertEqual("message-1", confusionPoint.eventId)
    self.assertEqual("导数定义", confusionPoint.knowledgePointKey)
    self.assertEqual(("不懂", "什么意思"), confusionPoint.matchedPatterns)
    self.assertEqual("我不懂这个符号是什么意思", confusionPoint.textExcerpt)
    self.assertEqual(generatedAt, result.metadata.generatedAt)
    self.assertEqual(firstEventAt + timedelta(minutes=1), result.metadata.firstEventAt)
    self.assertEqual(firstEventAt + timedelta(minutes=14), result.metadata.lastEventAt)
    self.assertEqual(
      "mastery-rule-v1",
      result.metadata.algorithmVersions["mastery"],
    )

  def testMarksProfilePartialWhenEventSourcesAreMissing(self):
    """只有部分来源时应继续生成画像并列出缺失来源。"""
    occurredAt = datetime(2026, 7, 25, 9, 0, tzinfo=timezone.utc)
    event = self.makeEvent(
      "wrong-1",
      EventSource.WRONG_QUESTION_BOOK,
      EventType.WRONG_QUESTION_ADDED,
      occurredAt,
    )

    result = profile_engine.buildUserProfile(
      [event],
      userId=1001,
      generatedAt=occurredAt,
    )

    self.assertEqual("partial", result.overallStatus.value)
    self.assertEqual(
      (
        EventSource.USER_ANSWERS,
        EventSource.CONVERSATION_MESSAGES,
        EventSource.USER_WEAK_POINTS,
      ),
      result.missingSources,
    )

  def testReturnsInsufficientProfileForEmptyEvents(self):
    """空事件列表应返回数据不足画像而不是抛出异常。"""
    generatedAt = datetime(2026, 7, 25, 10, 0, tzinfo=timezone.utc)

    try:
      result = profile_engine.buildUserProfile(
        [],
        userId=1001,
        generatedAt=generatedAt,
      )
    except ValueError as error:
      self.fail(f"空事件列表不应抛出异常：{error}")

    self.assertEqual("insufficient_data", result.overallStatus.value)
    self.assertEqual(0, result.aggregation.totalEventCount)
    self.assertIsNone(result.metadata.firstEventAt)
    self.assertIsNone(result.metadata.lastEventAt)
    self.assertEqual(
      ("NO_LEARNING_DATA",),
      tuple(warning.code for warning in result.dataQualityWarnings),
    )

  def testSkipsConversationWithoutTextAndRecordsWarning(self):
    """缺少文本的对话应跳过困惑提取并记录数据质量警告。"""
    occurredAt = datetime(2026, 7, 25, 9, 0, tzinfo=timezone.utc)
    event = self.makeEvent(
      "message-empty",
      EventSource.CONVERSATION_MESSAGES,
      EventType.CONVERSATION_MESSAGE,
      occurredAt,
      data={},
    )

    try:
      result = profile_engine.buildUserProfile(
        [event],
        userId=1001,
        generatedAt=occurredAt,
      )
    except (KeyError, ValueError) as error:
      self.fail(f"缺少对话文本不应阻断画像生成：{error}")

    self.assertEqual((), result.confusionPoints)
    self.assertEqual(1, len(result.dataQualityWarnings))
    warning = result.dataQualityWarnings[0]
    self.assertEqual("INVALID_CONVERSATION_TEXT", warning.code)
    self.assertEqual("message-empty", warning.eventId)

  def testUsesFirstOccurrenceOfDuplicateEventId(self):
    """重复事件应只使用第一次出现的内容并记录警告。"""
    occurredAt = datetime(2026, 7, 25, 9, 0, tzinfo=timezone.utc)
    firstEvent = self.makeEvent(
      "message-duplicate",
      EventSource.CONVERSATION_MESSAGES,
      EventType.CONVERSATION_MESSAGE,
      occurredAt,
      data={"text": "我为什么总是算错"},
    )
    conflictingEvent = self.makeEvent(
      "message-duplicate",
      EventSource.CONVERSATION_MESSAGES,
      EventType.CONVERSATION_MESSAGE,
      occurredAt + timedelta(minutes=1),
      data={"text": "我不懂这个符号"},
    )

    result = profile_engine.buildUserProfile(
      [firstEvent, conflictingEvent],
      userId=1001,
      generatedAt=occurredAt + timedelta(minutes=2),
    )

    self.assertEqual(1, result.aggregation.duplicateEventCount)
    self.assertEqual(1, len(result.confusionPoints))
    self.assertEqual(
      ("为什么",),
      result.confusionPoints[0].matchedPatterns,
    )
    self.assertIn(
      "DUPLICATE_EVENT_ID",
      tuple(warning.code for warning in result.dataQualityWarnings),
    )

  def testTruncatesLongConversationTextExcerpt(self):
    """困惑点只应保留长度受限的对话片段。"""
    occurredAt = datetime(2026, 7, 25, 9, 0, tzinfo=timezone.utc)
    longText = "为什么" + ("这个步骤我还是不明白" * 20)
    event = self.makeEvent(
      "message-long",
      EventSource.CONVERSATION_MESSAGES,
      EventType.CONVERSATION_MESSAGE,
      occurredAt,
      data={"text": longText},
    )

    result = profile_engine.buildUserProfile(
      [event],
      userId=1001,
      generatedAt=occurredAt,
    )

    textExcerpt = result.confusionPoints[0].textExcerpt
    self.assertLessEqual(len(textExcerpt), 80)
    self.assertTrue(textExcerpt.endswith("…"))
    self.assertNotEqual(longText, textExcerpt)

  def testKeepsUnmappedConfusionPoint(self):
    """未映射知识点的困惑对话仍应进入画像。"""
    occurredAt = datetime(2026, 7, 25, 9, 0, tzinfo=timezone.utc)
    event = self.makeEvent(
      "message-unmapped",
      EventSource.CONVERSATION_MESSAGES,
      EventType.CONVERSATION_MESSAGE,
      occurredAt,
      data={"text": "为什么这里要换元"},
      knowledgePointKey=None,
    )

    result = profile_engine.buildUserProfile(
      [event],
      userId=1001,
      generatedAt=occurredAt,
    )

    self.assertEqual(1, result.aggregation.unmappedEventCount)
    self.assertIsNone(result.confusionPoints[0].knowledgePointKey)
    self.assertEqual((), tuple(result.masteryByKnowledgePoint))

  def testKeepsNonAnswerKnowledgePointAsInsufficientData(self):
    """只有错题证据的知识点应保留并标记为数据不足。"""
    occurredAt = datetime(2026, 7, 25, 9, 0, tzinfo=timezone.utc)
    event = self.makeEvent(
      "wrong-only",
      EventSource.WRONG_QUESTION_BOOK,
      EventType.WRONG_QUESTION_ADDED,
      occurredAt,
    )

    result = profile_engine.buildUserProfile(
      [event],
      userId=1001,
      generatedAt=occurredAt,
    )

    mastery = result.masteryByKnowledgePoint["导数定义"]
    self.assertEqual(ProfileStatus.INSUFFICIENT_DATA, mastery.status)
    self.assertEqual(0, mastery.sampleCount)

  def testRejectsGeneratedAtWithoutTimezone(self):
    """画像生成时间必须包含时区。"""
    with self.assertRaisesRegex(
      ValueError,
      "画像生成时间必须包含时区",
    ):
      profile_engine.buildUserProfile(
        [],
        userId=1001,
        generatedAt=datetime(2026, 7, 25, 10, 0),
      )

  def testIncludesLearningPreferenceVector(self):
    """画像结果应包含四维学习偏好向量。"""
    occurredAt = datetime(2026, 7, 25, 9, 0, tzinfo=timezone.utc)
    events = [
      self.makeEvent(
        f"answer-style-{index}",
        EventSource.USER_ANSWERS,
        EventType.ANSWER_SUBMITTED,
        occurredAt + timedelta(minutes=index),
        data={
          "isCorrect": True,
          "learningMode": "visual" if index < 6 else "reading",
        },
      )
      for index in range(10)
    ]

    result = profile_engine.buildUserProfile(
      events,
      userId=1001,
      generatedAt=occurredAt + timedelta(minutes=10),
    )

    self.assertEqual("ready", result.learningPreference.status.value)
    self.assertEqual(10, result.learningPreference.evidenceCount)
    self.assertEqual(
      ("visual",),
      tuple(
        mode.value
        for mode in result.learningPreference.dominantDimensions
      ),
    )

  def testMergesPreferenceWarningsIntoProfileWarnings(self):
    """非法学习模式警告应汇总到画像顶层。"""
    occurredAt = datetime(2026, 7, 25, 9, 0, tzinfo=timezone.utc)
    event = self.makeEvent(
      "answer-invalid-style",
      EventSource.USER_ANSWERS,
      EventType.ANSWER_SUBMITTED,
      occurredAt,
      data={"isCorrect": True, "learningMode": "video"},
    )

    result = profile_engine.buildUserProfile(
      [event],
      userId=1001,
      generatedAt=occurredAt,
    )

    self.assertIn(
      "INVALID_LEARNING_MODE",
      tuple(warning.code for warning in result.dataQualityWarnings),
    )


if __name__ == "__main__":
  unittest.main()
