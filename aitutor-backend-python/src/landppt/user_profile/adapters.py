"""远程业务表记录到统一学习事件的适配器。"""

import json
from collections.abc import Mapping
from datetime import datetime, timedelta, timezone
from typing import Any

from .models import EventSource, EventType, LearningEvent

DEFAULT_TIMEZONE = timezone(timedelta(hours=8), name="Asia/Shanghai")
PRACTICE_ANSWER_EVENT_PREFIX = "practice_answer"
PRACTICE_MISTAKE_EVENT_PREFIX = "practice_mistake"
EVENT_COLLECTION_EVENT_PREFIX = "event_collection"
EVENT_TYPE_BY_SOURCE = {
  EventSource.USER_ANSWERS: EventType.ANSWER_SUBMITTED,
  EventSource.WRONG_QUESTION_BOOK: EventType.WRONG_QUESTION_ADDED,
  EventSource.CONVERSATION_MESSAGES: EventType.CONVERSATION_MESSAGE,
  EventSource.USER_WEAK_POINTS: EventType.WEAK_POINT_UPDATED,
}


def normalizePracticeAnswer(
  record: Mapping[str, Any],
) -> LearningEvent:
  """将 practice_answer_records 记录转换为统一答题事件。"""
  recordId = _requirePositiveInt(record, "id", "答题记录 ID")
  userId = _requirePositiveInt(record, "user_id", "用户 ID")
  knowledgePointKey = _requireText(
    record,
    "knowledge_point",
    "答题记录缺少知识点",
  )

  return LearningEvent(
    eventId=f"{PRACTICE_ANSWER_EVENT_PREFIX}:{recordId}",
    userId=userId,
    source=EventSource.USER_ANSWERS,
    eventType=EventType.ANSWER_SUBMITTED,
    occurredAt=_parseOccurredAt(record.get("answered_at")),
    knowledgePointKey=knowledgePointKey,
    data={
      "isCorrect": _parseDatabaseBoolean(
        record.get("correct"),
        "答题记录 correct",
      ),
      "questionId": record.get("question_id"),
      "durationSeconds": record.get("duration_seconds"),
      "attemptNumber": record.get("attempt_number"),
    },
  )


def normalizePracticeMistake(
  record: Mapping[str, Any],
) -> LearningEvent:
  """将联表后的 practice_mistakes 记录转换为统一错题事件。"""
  recordId = _requirePositiveInt(record, "id", "错题记录 ID")
  userId = _requirePositiveInt(record, "user_id", "用户 ID")
  knowledgePointKey = _requireText(
    record,
    "knowledge_point",
    "错题记录缺少知识点",
  )

  return LearningEvent(
    eventId=f"{PRACTICE_MISTAKE_EVENT_PREFIX}:{recordId}",
    userId=userId,
    source=EventSource.WRONG_QUESTION_BOOK,
    eventType=EventType.WRONG_QUESTION_ADDED,
    occurredAt=_parseOccurredAt(record.get("last_wrong_at")),
    knowledgePointKey=knowledgePointKey,
    data={
      "questionId": record.get("question_id"),
      "wrongCount": record.get("wrong_count"),
      "reviewCount": record.get("review_count"),
      "doubtful": _parseDatabaseBoolean(
        record.get("doubtful", 0),
        "错题记录 doubtful",
      ),
    },
  )


def normalizeEventCollection(
  record: Mapping[str, Any],
) -> LearningEvent:
  """将 event_collections 记录的约定信封转换为统一事件。"""
  recordId = _requirePositiveInt(record, "id", "事件采集记录 ID")
  userId = _requirePositiveInt(record, "user_id", "用户 ID")
  envelope = _parseEventEnvelope(record.get("event_data"))

  try:
    source = EventSource(envelope.get("source"))
  except (TypeError, ValueError) as error:
    raise ValueError("事件来源不受支持") from error

  payload = envelope.get("payload", {})
  if not isinstance(payload, Mapping):
    raise ValueError("事件 payload 必须为对象")

  knowledgePointKey = envelope.get("knowledgePointKey")
  if knowledgePointKey is not None:
    if not isinstance(knowledgePointKey, str) or not knowledgePointKey.strip():
      raise ValueError("知识点键不能为空")
    knowledgePointKey = knowledgePointKey.strip()

  data = dict(payload)
  data["rawEventType"] = record.get("event_type")
  data["sourceModule"] = record.get("module")

  return LearningEvent(
    eventId=f"{EVENT_COLLECTION_EVENT_PREFIX}:{recordId}",
    userId=userId,
    source=source,
    eventType=EVENT_TYPE_BY_SOURCE[source],
    occurredAt=_parseOccurredAt(record.get("event_time")),
    knowledgePointKey=knowledgePointKey,
    data=data,
  )


def _parseEventEnvelope(value: Any) -> Mapping[str, Any]:
  """解析 event_data 中的稳定 JSON 信封。"""
  if isinstance(value, Mapping):
    return value
  if not isinstance(value, str) or not value.strip():
    raise ValueError("事件 event_data 不能为空")
  try:
    envelope = json.loads(value)
  except json.JSONDecodeError as error:
    raise ValueError("事件 event_data 不是合法 JSON") from error
  if not isinstance(envelope, Mapping):
    raise ValueError("事件 event_data 必须为对象")
  return envelope


def _requirePositiveInt(
  record: Mapping[str, Any],
  fieldName: str,
  displayName: str,
) -> int:
  """读取正整数必填字段。"""
  value = record.get(fieldName)
  if not isinstance(value, int) or isinstance(value, bool) or value <= 0:
    raise ValueError(f"{displayName}必须为正整数")
  return value


def _requireText(
  record: Mapping[str, Any],
  fieldName: str,
  errorMessage: str,
) -> str:
  """读取非空文本必填字段。"""
  value = record.get(fieldName)
  if not isinstance(value, str) or not value.strip():
    raise ValueError(errorMessage)
  return value.strip()


def _parseDatabaseBoolean(
  value: Any,
  displayName: str,
) -> bool:
  """将 MySQL TINYINT 或布尔值转换为 Python 布尔值。"""
  if isinstance(value, bool):
    return value
  if value in (0, 1):
    return bool(value)
  raise ValueError(f"{displayName}必须为 0、1 或布尔值")


def _parseOccurredAt(value: Any) -> datetime:
  """解析事件时间，并为无时区的 MySQL DATETIME 补充项目时区。"""
  if isinstance(value, datetime):
    occurredAt = value
  elif isinstance(value, str):
    normalizedValue = value.replace("Z", "+00:00")
    try:
      occurredAt = datetime.fromisoformat(normalizedValue)
    except ValueError as error:
      raise ValueError("事件时间格式不合法") from error
  else:
    raise ValueError("事件时间不能为空")

  if occurredAt.tzinfo is None or occurredAt.utcoffset() is None:
    return occurredAt.replace(tzinfo=DEFAULT_TIMEZONE)
  return occurredAt
