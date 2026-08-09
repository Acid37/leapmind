"""M6 v1.0 契约测试数据工厂。"""

from copy import deepcopy
from typing import Any


FIXED_OCCURRED_AT = "2026-08-05T12:00:00Z"
FIXED_REQUEST_ID = "12345678-1234-5678-1234-567812345678"

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


def makeEvent(
  dbEventId: int = 1,
  eventType: str = "answer_question",
  sourceModule: str | None = None,
  kpId: int | None = 101,
  data: dict[str, Any] | None = None,
  **overrides: Any,
) -> dict[str, Any]:
  """创建字段完整且默认有效的 M6 事件。"""
  event = {
    "dbEventId": dbEventId,
    "eventId": f"event-{dbEventId}",
    "eventType": eventType,
    "sourceModule": EVENT_SOURCES[eventType] if sourceModule is None else sourceModule,
    "occurredAt": FIXED_OCCURRED_AT,
    "schemaVersion": "1.0",
    "sessionId": "session-1",
    "kpId": kpId,
    "traceId": "trace-1",
    "data": data if data is not None else {
      "isCorrect": True,
      "difficulty": 3,
      "timeSpentSec": 45,
      "hintCount": 0,
    },
  }
  event.update(overrides)
  return deepcopy(event)


def makeRequest(
  events: list[dict[str, Any]] | None = None,
  baseProfileVersion: int = 3,
  **overrides: Any,
) -> dict[str, Any]:
  """创建水位与事件列表一致的 M6 画像请求。"""
  requestEvents = deepcopy(events if events is not None else [makeEvent()])
  watermark = max((event["dbEventId"] for event in requestEvents), default=0)
  request = {
    "contractVersion": "1.0",
    "requestId": FIXED_REQUEST_ID,
    "userId": 1001,
    "mode": "FULL",
    "baseProfileVersion": baseProfileVersion,
    "fromEventIdExclusive": 0,
    "eventWatermarkInclusive": watermark,
    "events": requestEvents,
  }
  request.update(overrides)
  return request
