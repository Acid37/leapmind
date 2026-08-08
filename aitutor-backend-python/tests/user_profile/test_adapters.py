"""远程 develop 数据表记录适配测试。"""

import unittest
from datetime import datetime, timedelta

from landppt.user_profile.adapters import (
  normalizeEventCollection,
  normalizePracticeAnswer,
  normalizePracticeMistake,
)
from landppt.user_profile.models import EventSource, EventType


class PracticeRecordAdapterTest(unittest.TestCase):
  """验证 M1 练习记录到统一事件的转换。"""

  def testNormalizesPracticeAnswerRecord(self):
    """答题记录应保留正确性、知识点和可追踪事件 ID。"""
    record = {
      "id": 21,
      "user_id": 1001,
      "question_id": 8,
      "answered_at": datetime(2026, 7, 24, 9, 30),
      "duration_seconds": 35,
      "correct": 1,
      "knowledge_point": "导数定义",
      "attempt_number": 2,
    }

    event = normalizePracticeAnswer(record)

    self.assertEqual("practice_answer:21", event.eventId)
    self.assertEqual(EventSource.USER_ANSWERS, event.source)
    self.assertEqual(EventType.ANSWER_SUBMITTED, event.eventType)
    self.assertEqual("导数定义", event.knowledgePointKey)
    self.assertTrue(event.data["isCorrect"])
    self.assertEqual(35, event.data["durationSeconds"])
    self.assertEqual(timedelta(hours=8), event.occurredAt.utcoffset())

  def testNormalizesJoinedPracticeMistakeRecord(self):
    """错题记录应使用与题目表连接后得到的知识点。"""
    record = {
      "id": 9,
      "user_id": 1001,
      "question_id": 8,
      "knowledge_point": "导数定义",
      "wrong_count": 3,
      "review_count": 1,
      "doubtful": 1,
      "last_wrong_at": "2026-07-24T10:15:00+08:00",
    }

    event = normalizePracticeMistake(record)

    self.assertEqual("practice_mistake:9", event.eventId)
    self.assertEqual(EventSource.WRONG_QUESTION_BOOK, event.source)
    self.assertEqual(EventType.WRONG_QUESTION_ADDED, event.eventType)
    self.assertEqual("导数定义", event.knowledgePointKey)
    self.assertEqual(3, event.data["wrongCount"])
    self.assertTrue(event.data["doubtful"])

  def testRejectsMistakeWithoutJoinedKnowledgePoint(self):
    """未连接题目表的错题记录无法参与知识点画像。"""
    record = {
      "id": 9,
      "user_id": 1001,
      "question_id": 8,
      "last_wrong_at": "2026-07-24T10:15:00+08:00",
    }

    with self.assertRaisesRegex(ValueError, "错题记录缺少知识点"):
      normalizePracticeMistake(record)


class EventCollectionAdapterTest(unittest.TestCase):
  """验证 event_collections 自由 JSON 到稳定契约的转换。"""

  def testNormalizesWeakPointEventEnvelope(self):
    """薄弱点事件应从约定信封中提取来源、知识点和业务载荷。"""
    record = {
      "id": 31,
      "module": "M4",
      "event_type": "KNOWLEDGE_WEAK",
      "user_id": 1001,
      "event_time": datetime(2026, 7, 24, 11, 0),
      "event_data": (
        '{"source":"user_weak_points",'
        '"knowledgePointKey":"导数定义",'
        '"payload":{"weaknessScore":0.72}}'
      ),
    }

    event = normalizeEventCollection(record)

    self.assertEqual("event_collection:31", event.eventId)
    self.assertEqual(EventSource.USER_WEAK_POINTS, event.source)
    self.assertEqual(EventType.WEAK_POINT_UPDATED, event.eventType)
    self.assertEqual("导数定义", event.knowledgePointKey)
    self.assertEqual(0.72, event.data["weaknessScore"])
    self.assertEqual("KNOWLEDGE_WEAK", event.data["rawEventType"])
    self.assertEqual("M4", event.data["sourceModule"])

  def testRejectsUnsupportedEnvelopeSource(self):
    """未约定的数据来源不得静默进入画像计算。"""
    record = {
      "id": 31,
      "module": "M4",
      "event_type": "UNKNOWN",
      "user_id": 1001,
      "event_time": datetime(2026, 7, 24, 11, 0),
      "event_data": '{"source":"unknown","payload":{}}',
    }

    with self.assertRaisesRegex(ValueError, "事件来源不受支持"):
      normalizeEventCollection(record)


if __name__ == "__main__":
  unittest.main()
