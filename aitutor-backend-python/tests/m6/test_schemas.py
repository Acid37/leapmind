"""M6 画像引擎 v1.0 严格契约模型测试。"""

import json
from copy import deepcopy
from datetime import datetime, timezone

import pytest
from pydantic import TypeAdapter, ValidationError

from landppt.m6.schemas import (
  BuildProfileRequest,
  BuildProfileResponse,
  InsufficientDataResponse,
  NoChangeResponse,
  ReadyResponse,
)
from tests.m6.conftest import FIXED_OCCURRED_AT, FIXED_REQUEST_ID, makeEvent, makeRequest


VALID_EVENT_DATA = {
  "answer_question": {
    "isCorrect": True,
    "difficulty": 1,
    "timeSpentSec": 0,
    "hintCount": 0,
    "confusionTag": "concept_unclear",
  },
  "finish_practice": {"questionCount": 1, "accuracy": 0, "durationSec": 0},
  "request_explanation": {"explainId": "explain:1", "reasonTag": "WRONG_ANSWER"},
  "explanation_feedback": {"explainId": "explain.1", "feedback": "understood", "repeatCount": 0},
  "weak_point_changed": {"oldScore": 0, "newScore": 1, "reason": "ACCURACY_DROP"},
  "lecture_interact": {"lectureId": "lecture-1", "chapterId": "chapter_1", "action": "pause"},
  "lesson_material_used": {"contentId": "content:1", "materialType": "text", "result": "completed"},
  "ask_doubt": {"topic": "  一元二次方程  ", "confusionTag": "formula_confusion", "isFollowUp": False},
  "mark_reviewed": {"result": "correct_without_hint", "timeSpentSec": 0, "hintCount": 0},
  "preference_changed": {"preferenceKey": "content_mode", "preferenceValue": "image"},
}


@pytest.mark.parametrize("eventType,data", VALID_EVENT_DATA.items())
def test_accepts_all_event_data_contracts(eventType: str, data: dict) -> None:
  request = makeRequest([makeEvent(eventType=eventType, data=data)])

  parsed = BuildProfileRequest.model_validate(request)

  assert parsed.events[0].data == data


@pytest.mark.parametrize(
  ("eventType", "data"),
  [
    ("answer_question", {"isCorrect": False, "difficulty": 5, "timeSpentSec": 86400, "hintCount": 100}),
    ("finish_practice", {"questionCount": 10000, "accuracy": 1, "durationSec": 86400}),
    ("explanation_feedback", {"explainId": "a", "feedback": "still_confused", "repeatCount": 100}),
    ("mark_reviewed", {"result": "postponed", "timeSpentSec": 86400, "hintCount": 100}),
  ],
)
def test_accepts_numeric_boundaries(eventType: str, data: dict) -> None:
  BuildProfileRequest.model_validate(makeRequest([makeEvent(eventType=eventType, data=data)]))


@pytest.mark.parametrize(
  ("eventType", "field", "invalidValue"),
  [
    ("answer_question", "difficulty", 0),
    ("answer_question", "difficulty", 6),
    ("answer_question", "timeSpentSec", 86401),
    ("answer_question", "hintCount", -1),
    ("finish_practice", "questionCount", 0),
    ("finish_practice", "accuracy", 1.01),
    ("finish_practice", "durationSec", -1),
    ("explanation_feedback", "repeatCount", 101),
    ("weak_point_changed", "oldScore", -0.01),
    ("weak_point_changed", "newScore", True),
    ("mark_reviewed", "timeSpentSec", True),
  ],
)
def test_rejects_out_of_range_or_boolean_numbers(eventType: str, field: str, invalidValue: object) -> None:
  data = deepcopy(VALID_EVENT_DATA[eventType])
  data[field] = invalidValue

  with pytest.raises(ValidationError):
    BuildProfileRequest.model_validate(makeRequest([makeEvent(eventType=eventType, data=data)]))


@pytest.mark.parametrize(
  ("eventType", "field", "invalidValue"),
  [
    ("answer_question", "isCorrect", 1),
    ("answer_question", "confusionTag", "unknown"),
    ("request_explanation", "explainId", " invalid"),
    ("request_explanation", "reasonTag", "unknown"),
    ("explanation_feedback", "feedback", "unknown"),
    ("weak_point_changed", "reason", "unknown"),
    ("lecture_interact", "action", "unknown"),
    ("lesson_material_used", "materialType", "pdf"),
    ("lesson_material_used", "result", "unknown"),
    ("ask_doubt", "topic", "   "),
    ("ask_doubt", "topic", "测" * 121),
    ("ask_doubt", "isFollowUp", 0),
    ("mark_reviewed", "result", "unknown"),
    ("preference_changed", "preferenceKey", "unknown"),
    ("preference_changed", "preferenceValue", "unknown"),
  ],
)
def test_rejects_invalid_event_values(eventType: str, field: str, invalidValue: object) -> None:
  data = deepcopy(VALID_EVENT_DATA[eventType])
  data[field] = invalidValue

  with pytest.raises(ValidationError):
    BuildProfileRequest.model_validate(makeRequest([makeEvent(eventType=eventType, data=data)]))


@pytest.mark.parametrize("eventType,data", VALID_EVENT_DATA.items())
def test_rejects_missing_and_unknown_data_fields(eventType: str, data: dict) -> None:
  missing = deepcopy(data)
  missing.pop(next(iter(missing)))
  unknown = {**data, "unknown": "value"}

  with pytest.raises(ValidationError):
    BuildProfileRequest.model_validate(makeRequest([makeEvent(eventType=eventType, data=missing)]))
  with pytest.raises(ValidationError):
    BuildProfileRequest.model_validate(makeRequest([makeEvent(eventType=eventType, data=unknown)]))


@pytest.mark.parametrize(
  ("preferenceKey", "preferenceValue"),
  [
    ("content_mode", "exercise"),
    ("explanation_style", "step_by_step"),
    ("learning_pace", "fast"),
  ],
)
def test_accepts_preference_enumerations(preferenceKey: str, preferenceValue: str) -> None:
  data = {"preferenceKey": preferenceKey, "preferenceValue": preferenceValue}
  BuildProfileRequest.model_validate(makeRequest([makeEvent(eventType="preference_changed", data=data)]))


def test_rejects_unknown_request_field_and_wrong_versions() -> None:
  with pytest.raises(ValidationError):
    BuildProfileRequest.model_validate(makeRequest(unknown=True))
  with pytest.raises(ValidationError):
    BuildProfileRequest.model_validate(makeRequest(contractVersion="2.0"))
  with pytest.raises(ValidationError):
    BuildProfileRequest.model_validate(makeRequest([makeEvent(schemaVersion="2.0")]))


def test_rejects_naive_occurred_at_and_mismatched_source() -> None:
  with pytest.raises(ValidationError):
    BuildProfileRequest.model_validate(makeRequest([makeEvent(occurredAt="2026-08-05T12:00:00")]))
  with pytest.raises(ValidationError):
    BuildProfileRequest.model_validate(makeRequest([makeEvent(sourceModule="M2")]))


def test_rejects_invalid_event_sequence_and_watermark() -> None:
  with pytest.raises(ValidationError):
    BuildProfileRequest.model_validate(makeRequest([makeEvent(2), makeEvent(2)]))
  with pytest.raises(ValidationError):
    BuildProfileRequest.model_validate(makeRequest([makeEvent(3)], eventWatermarkInclusive=2))
  with pytest.raises(ValidationError):
    BuildProfileRequest.model_validate(makeRequest([makeEvent(3)], fromEventIdExclusive=3))
  with pytest.raises(ValidationError):
    BuildProfileRequest.model_validate(makeRequest([], fromEventIdExclusive=2, eventWatermarkInclusive=1))


def test_rejects_more_than_one_thousand_events() -> None:
  events = [makeEvent(index) for index in range(1, 1002)]

  with pytest.raises(ValidationError):
    BuildProfileRequest.model_validate(makeRequest(events))


def test_answer_question_requires_knowledge_point() -> None:
  event = makeEvent()
  event.pop("kpId")
  with pytest.raises(ValidationError):
    BuildProfileRequest.model_validate(makeRequest([event]))


def test_optional_event_fields_reject_explicit_null_in_json_and_omit_null_output() -> None:
  data = {"questionCount": 1, "accuracy": 1, "durationSec": 0}
  event = makeEvent(eventType="finish_practice", data=data)
  for fieldName in ("sessionId", "kpId", "traceId"):
    invalidEvent = {**event, fieldName: None}
    with pytest.raises(ValidationError):
      BuildProfileRequest.model_validate_json(json.dumps(makeRequest([invalidEvent])))

  for fieldName in ("sessionId", "kpId", "traceId"):
    event.pop(fieldName)
  parsed = BuildProfileRequest.model_validate_json(json.dumps(makeRequest([event])))

  outputEvent = parsed.model_dump()["events"][0]
  assert "sessionId" not in outputEvent
  assert "kpId" not in outputEvent
  assert "traceId" not in outputEvent


def test_event_factory_preserves_explicit_empty_source_and_data() -> None:
  event = makeEvent(sourceModule="", data={})

  assert event["sourceModule"] == ""
  assert event["data"] == {}
  with pytest.raises(ValidationError):
    BuildProfileRequest.model_validate(makeRequest([event]))


def test_answer_question_allows_omitted_confusion_but_rejects_null() -> None:
  data = deepcopy(VALID_EVENT_DATA["answer_question"])
  data.pop("confusionTag")
  BuildProfileRequest.model_validate(makeRequest([makeEvent(data=data)]))

  with pytest.raises(ValidationError):
    BuildProfileRequest.model_validate(
      makeRequest([makeEvent(data={**data, "confusionTag": None})]),
    )


def test_datetime_fields_accept_datetime_objects_but_reject_numeric_timestamps() -> None:
  event = makeEvent(occurredAt=datetime(2026, 8, 5, 12, tzinfo=timezone.utc))
  BuildProfileRequest.model_validate(makeRequest([event]))

  for numericTimestamp in (1_754_395_200, 1_754_395_200.5):
    with pytest.raises(ValidationError):
      BuildProfileRequest.model_validate(
        makeRequest([makeEvent(occurredAt=numericTimestamp)]),
      )


def responseBase(status: str, targetProfileVersion: int) -> dict:
  """创建三态响应共享字段。"""
  return {
    "contractVersion": "1.0",
    "requestId": FIXED_REQUEST_ID,
    "userId": 1001,
    "baseProfileVersion": 3,
    "targetProfileVersion": targetProfileVersion,
    "eventWatermarkInclusive": 8,
    "status": status,
    "algorithmVersion": "m6-profile-v1",
    "evaluatedAt": FIXED_OCCURRED_AT,
  }


def readyResponse() -> dict:
  """创建包含全部响应时间字段的有效 READY 响应。"""
  return {
    **responseBase("READY", 4),
    "profile": {
      "preferredContentModes": [],
      "recentFocus": [],
      "recentConfusions": [{
        "kpId": 101,
        "detail": "公式理解不清",
        "evidenceCount": 1,
        "confidence": 0.5,
        "lastOccurredAt": FIXED_OCCURRED_AT,
      }],
    },
    "knowledgeMastery": [{
      "kpId": 101,
      "masteryScore": 0.5,
      "masteryStatus": "CONSOLIDATING",
      "confidence": 0.5,
      "evidenceCount": 1,
      "algorithmVersion": "m6-mastery-v1",
      "windowStart": FIXED_OCCURRED_AT,
      "windowEnd": FIXED_OCCURRED_AT,
      "updatedAt": FIXED_OCCURRED_AT,
    }],
  }


@pytest.mark.parametrize(
  "fieldPath",
  [
    ("evaluatedAt",),
    ("profile", "recentConfusions", 0, "lastOccurredAt"),
    ("knowledgeMastery", 0, "windowStart"),
    ("knowledgeMastery", 0, "windowEnd"),
    ("knowledgeMastery", 0, "updatedAt"),
  ],
)
@pytest.mark.parametrize("numericTimestamp", [1_754_395_200, 1_754_395_200.5])
def test_response_datetime_fields_reject_numeric_timestamps(
  fieldPath: tuple,
  numericTimestamp: int | float,
) -> None:
  response = readyResponse()
  target = response
  for key in fieldPath[:-1]:
    target = target[key]
  target[fieldPath[-1]] = numericTimestamp

  with pytest.raises(ValidationError):
    TypeAdapter(BuildProfileResponse).validate_python(response)


def test_response_datetime_fields_accept_datetime_objects() -> None:
  response = readyResponse()
  fixedDatetime = datetime(2026, 8, 5, 12, tzinfo=timezone.utc)
  response["evaluatedAt"] = fixedDatetime
  response["profile"]["recentConfusions"][0]["lastOccurredAt"] = fixedDatetime
  mastery = response["knowledgeMastery"][0]
  mastery["windowStart"] = fixedDatetime
  mastery["windowEnd"] = fixedDatetime
  mastery["updatedAt"] = fixedDatetime

  TypeAdapter(BuildProfileResponse).validate_python(response)


def test_accepts_ready_response_and_discriminates_union() -> None:
  response = {
    **responseBase("READY", 4),
    "profile": {
      "grade": None,
      "preferredContentModes": ["text", "image"],
      "preferredExplanationStyle": None,
      "learningPace": "moderate",
      "recentFocus": [{"kpId": 101, "weight": 1}],
      "recentConfusions": [{
        "kpId": 101,
        "detail": "公式理解不清",
        "evidenceCount": 1,
        "confidence": 0.5,
        "lastOccurredAt": FIXED_OCCURRED_AT,
      }],
      "summaryProfile": None,
      "confidence": 0.75,
    },
    "knowledgeMastery": [{
      "kpId": 101,
      "masteryScore": 0.625,
      "masteryStatus": "CONSOLIDATING",
      "confidence": 0.5,
      "evidenceCount": 2,
      "trend": "STABLE",
      "algorithmVersion": "m6-mastery-v1",
      "windowStart": FIXED_OCCURRED_AT,
      "windowEnd": FIXED_OCCURRED_AT,
      "updatedAt": FIXED_OCCURRED_AT,
    }],
  }

  parsed = TypeAdapter(BuildProfileResponse).validate_python(response)

  assert isinstance(parsed, ReadyResponse)


def test_enforces_response_state_shapes_and_version_rules() -> None:
  insufficient = {**responseBase("INSUFFICIENT_DATA", 4), "knowledgeMastery": []}
  noChange = responseBase("NO_CHANGE", 3)
  assert isinstance(TypeAdapter(BuildProfileResponse).validate_python(insufficient), InsufficientDataResponse)
  assert isinstance(TypeAdapter(BuildProfileResponse).validate_python(noChange), NoChangeResponse)

  invalidResponses = [
    {**insufficient, "knowledgeMastery": [{"kpId": 1}]},
    {**insufficient, "profile": {}},
    {**noChange, "knowledgeMastery": []},
    responseBase("READY", 3),
    {**responseBase("NO_CHANGE", 4)},
    {**noChange, "unknown": True},
  ]
  for response in invalidResponses:
    with pytest.raises(ValidationError):
      TypeAdapter(BuildProfileResponse).validate_python(response)


def test_rejects_null_for_optional_non_nullable_mastery_windows() -> None:
  response = {
    **responseBase("READY", 4),
    "profile": {
      "preferredContentModes": [],
      "recentFocus": [],
      "recentConfusions": [],
    },
    "knowledgeMastery": [{
      "kpId": 101,
      "masteryScore": 0.5,
      "masteryStatus": "CONSOLIDATING",
      "confidence": 0.5,
      "evidenceCount": 1,
      "algorithmVersion": "m6-mastery-v1",
      "windowStart": None,
      "updatedAt": FIXED_OCCURRED_AT,
    }],
  }

  with pytest.raises(ValidationError):
    TypeAdapter(BuildProfileResponse).validate_python(response)
