"""build-profile 契约测试（profile-engine-contract.yaml v1.0）。

覆盖：
- BuildProfileRequest 解析：userId 主字段 / user_id 兼容 / kpId=null 不报错 / 未知字段容忍 / 必填缺失报错
- 事件字段读取：isCorrect / accuracy / score 兜底 / timeSpentSec / durationSec / mark_reviewed result
- 响应模型：camelCase 序列化、回显、target=base+1、extra=forbid
- 路由三态：READY / INSUFFICIENT_DATA / NO_CHANGE、错误回显、400 契约失败
"""

from datetime import datetime, timezone

import pytest
from fastapi import FastAPI
from fastapi.encoders import jsonable_encoder
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from fastapi.testclient import TestClient
from pydantic import ValidationError

from landppt.m6.profile_engine import compute_profile_from_events, _event_is_correct, _event_duration_seconds
from landppt.m6.router import router
from landppt.m6.schemas import (
    BuildProfileRequest,
    InsufficientResponse,
    KnowledgeMastery,
    NoChangeResponse,
    Profile,
    ReadyResponse,
)

REQ_ID = "11111111-2222-3333-4444-555555555555"


def make_request(**overrides):
    base = {
        "contractVersion": "1.0",
        "requestId": REQ_ID,
        "userId": 7,
        "mode": "INCREMENTAL",
        "baseProfileVersion": 3,
        "fromEventIdExclusive": 10,
        "eventWatermarkInclusive": 12,
        "events": [],
    }
    base.update(overrides)
    return base


def make_event(kp_id, event_type, data, db_event_id=11):
    return {
        "dbEventId": db_event_id,
        "eventId": f"e-{db_event_id}",
        "eventType": event_type,
        "sourceModule": "M6",
        "occurredAt": "2026-08-08T00:00:00Z",
        "schemaVersion": "1.0",
        "kpId": kp_id,
        "data": data,
    }


# ---------------------------------------------------------------------------
# 1. BuildProfileRequest 解析
# ---------------------------------------------------------------------------

class TestBuildProfileRequest:
    def test_user_id_primary_camelcase(self):
        req = BuildProfileRequest.model_validate(make_request(events=[make_event(5, "answer_question", {"isCorrect": True})]))
        assert req.user_id == 7
        assert req.request_id == REQ_ID
        assert req.event_watermark_inclusive == 12
        assert req.base_profile_version == 3
        assert len(req.events) == 1

    def test_user_id_underscore_legacy(self):
        payload = make_request()
        payload.pop("userId")
        payload["user_id"] = 8
        req = BuildProfileRequest.model_validate(payload)
        assert req.user_id == 8

    def test_kp_id_null_not_error(self):
        req = BuildProfileRequest.model_validate(
            make_request(events=[make_event(None, "ask_doubt", {"isFollowUp": True})])
        )
        assert req.events[0].kp_id is None

    def test_unknown_fields_ignored(self):
        req = BuildProfileRequest.model_validate(make_request(extraJavaField="ignored", events=[]))
        assert req.user_id == 7

    def test_missing_required_field_raises(self):
        with pytest.raises(ValidationError):
            BuildProfileRequest.model_validate({"requestId": REQ_ID, "userId": 7})

    def test_event_data_free_form(self):
        req = BuildProfileRequest.model_validate(
            make_request(events=[make_event(5, "answer_question", {"isCorrect": True, "isFollowUp": False, "confusionTag": "careless"})])
        )
        assert req.events[0].data["isFollowUp"] is False
        assert req.events[0].data["confusionTag"] == "careless"


# ---------------------------------------------------------------------------
# 2. 事件字段读取
# ---------------------------------------------------------------------------

class TestEventFieldReading:
    def test_is_correct_true(self):
        assert _event_is_correct("answer_question", {"isCorrect": True}) is True

    def test_is_correct_false(self):
        assert _event_is_correct("answer_question", {"isCorrect": False}) is False

    def test_finish_practice_accuracy(self):
        assert _event_is_correct("finish_practice", {"accuracy": 0.9}) is True
        assert _event_is_correct("finish_practice", {"accuracy": 0.3}) is False

    def test_finish_practice_score_fallback(self):
        assert _event_is_correct("finish_practice", {"score": 0.8}) is True
        assert _event_is_correct("finish_practice", {"score": 0.4}) is False

    def test_mark_reviewed_result(self):
        assert _event_is_correct("mark_reviewed", {"result": "correct_without_hint"}) is True
        assert _event_is_correct("mark_reviewed", {"result": "correct_with_hint"}) is True
        assert _event_is_correct("mark_reviewed", {"result": "incorrect"}) is False
        assert _event_is_correct("mark_reviewed", {"result": "still_confused"}) is False
        assert _event_is_correct("mark_reviewed", {"result": "postponed"}) is False

    def test_duration_time_spent_sec(self):
        assert _event_duration_seconds({"timeSpentSec": 42}) == 42

    def test_duration_fallback_fields(self):
        assert _event_duration_seconds({"durationSec": 30}) == 30
        assert _event_duration_seconds({"durationSeconds": 20}) == 20
        assert _event_duration_seconds({"duration_seconds": 10}) == 10
        assert _event_duration_seconds({}) is None

    def test_finish_practice_accuracy_counts_in_mastery(self):
        events = [make_event(5, "finish_practice", {"accuracy": 0.9, "timeSpentSec": 15})]
        profile, mastery = compute_profile_from_events(7, events)
        assert mastery[0]["masteryScore"] == 1.0
        assert profile["learningPace"] == "fast"

    def test_mark_reviewed_correct_counts_in_mastery(self):
        events = [make_event(5, "mark_reviewed", {"result": "correct_without_hint"})]
        _, mastery = compute_profile_from_events(7, events)
        assert mastery[0]["masteryScore"] == 1.0

    def test_trend_present_and_valid(self):
        events = [
            make_event(5, "answer_question", {"isCorrect": True}, 11),
            make_event(5, "answer_question", {"isCorrect": True}, 12),
            make_event(5, "answer_question", {"isCorrect": True}, 13),
            make_event(5, "answer_question", {"isCorrect": True}, 14),
        ]
        _, mastery = compute_profile_from_events(7, events)
        assert mastery[0]["trend"] in ("IMPROVING", "STABLE", "DECLINING", None)

    def test_mastery_precision_at_most_4(self):
        events = [make_event(5, "answer_question", {"isCorrect": True}, 11)]
        _, mastery = compute_profile_from_events(7, events)
        for field in ("masteryScore", "confidence"):
            value = mastery[0][field]
            assert round(value, 4) == value


# ---------------------------------------------------------------------------
# 3. 响应模型序列化
# ---------------------------------------------------------------------------

class TestResponseModels:
    def _ready(self):
        events = [make_event(5, "answer_question", {"isCorrect": True})]
        profile, mastery = compute_profile_from_events(7, events)
        return ReadyResponse(
            contract_version="1.0",
            request_id=REQ_ID,
            user_id=7,
            base_profile_version=3,
            target_profile_version=4,
            event_watermark_inclusive=12,
            algorithm_version="m6-v1",
            evaluated_at="2026-08-08T00:00:00Z",
            profile=Profile.model_validate(profile),
            knowledge_mastery=[KnowledgeMastery.model_validate(m) for m in mastery],
        )

    def test_ready_camelcase_serialization(self):
        body = self._ready().model_dump(by_alias=True)
        assert body["status"] == "READY"
        assert body["userId"] == 7
        assert body["baseProfileVersion"] == 3
        assert body["targetProfileVersion"] == 4
        assert body["requestId"] == REQ_ID
        assert body["eventWatermarkInclusive"] == 12
        assert body["profile"]["preferredContentModes"] == []
        assert body["knowledgeMastery"][0]["masteryStatus"] == "INSUFFICIENT_EVIDENCE"

    def test_ready_no_extra_keys(self):
        body = self._ready().model_dump(by_alias=True)
        assert "success" not in body
        assert "data" not in body
        assert "message" not in body

    def test_no_change_target_equals_base(self):
        body = NoChangeResponse(
            contract_version="1.0",
            request_id=REQ_ID,
            user_id=7,
            base_profile_version=3,
            target_profile_version=3,
            event_watermark_inclusive=12,
            algorithm_version="m6-v1",
            evaluated_at="2026-08-08T00:00:00Z",
        ).model_dump(by_alias=True)
        assert body["status"] == "NO_CHANGE"
        assert body["targetProfileVersion"] == 3
        assert "profile" not in body
        assert "knowledgeMastery" not in body

    def test_insufficient_target_plus_one(self):
        body = InsufficientResponse(
            contract_version="1.0",
            request_id=REQ_ID,
            user_id=7,
            base_profile_version=3,
            target_profile_version=4,
            event_watermark_inclusive=12,
            algorithm_version="m6-v1",
            evaluated_at="2026-08-08T00:00:00Z",
        ).model_dump(by_alias=True)
        assert body["status"] == "INSUFFICIENT_DATA"
        assert body["targetProfileVersion"] == 4
        assert body["knowledgeMastery"] == []
        assert "profile" not in body

    def test_extra_field_forbidden_in_response(self):
        with pytest.raises(ValidationError):
            NoChangeResponse.model_validate(
                {
                    "contractVersion": "1.0",
                    "requestId": REQ_ID,
                    "userId": 7,
                    "baseProfileVersion": 3,
                    "targetProfileVersion": 3,
                    "eventWatermarkInclusive": 12,
                    "algorithmVersion": "m6-v1",
                    "evaluatedAt": "now",
                    "success": True,
                }
            )


# ---------------------------------------------------------------------------
# 4. 路由三态（最小 FastAPI 应用，仅挂载 m6 router）
# ---------------------------------------------------------------------------

@pytest.fixture(scope="module")
def client():
    app = FastAPI()

    @app.exception_handler(RequestValidationError)
    async def validation_exception_handler(request, exc):
        # 与 main.py 保持一致：契约校验失败统一返回 400
        return JSONResponse(
            status_code=400,
            content={"detail": "Contract validation failed", "errors": jsonable_encoder(exc.errors())},
        )

    app.include_router(router)
    return TestClient(app)


class TestBuildProfileRoute:
    def test_no_change_empty_events(self, client):
        resp = client.post("/api/internal/ai/build-profile", json=make_request())
        assert resp.status_code == 200
        body = resp.json()
        assert body["status"] == "NO_CHANGE"
        assert body["targetProfileVersion"] == 3
        assert body["userId"] == 7
        assert body["requestId"] == REQ_ID
        assert body["eventWatermarkInclusive"] == 12
        assert "profile" not in body
        assert "knowledgeMastery" not in body

    def test_insufficient_no_kp_events(self, client):
        payload = make_request(
            events=[make_event(None, "ask_doubt", {"isFollowUp": True})]
        )
        resp = client.post("/api/internal/ai/build-profile", json=payload)
        assert resp.status_code == 200
        body = resp.json()
        assert body["status"] == "INSUFFICIENT_DATA"
        assert body["targetProfileVersion"] == 4
        assert body["knowledgeMastery"] == []
        assert "profile" not in body

    def test_ready_with_events(self, client):
        payload = make_request(
            events=[make_event(5, "finish_practice", {"accuracy": 0.9, "timeSpentSec": 15})]
        )
        resp = client.post("/api/internal/ai/build-profile", json=payload)
        assert resp.status_code == 200
        body = resp.json()
        assert body["status"] == "READY"
        assert body["targetProfileVersion"] == 4
        assert body["profile"]["learningPace"] == "fast"
        assert body["knowledgeMastery"][0]["masteryStatus"] == "INSUFFICIENT_EVIDENCE"

    def test_legacy_user_id_underscore_accepted(self, client):
        payload = make_request()
        payload.pop("userId")
        payload["user_id"] = 9
        resp = client.post("/api/internal/ai/build-profile", json=payload)
        assert resp.status_code == 200
        assert resp.json()["userId"] == 9

    def test_contract_failure_returns_400(self, client):
        resp = client.post(
            "/api/internal/ai/build-profile",
            json={"requestId": REQ_ID, "userId": 7},  # 缺少多个必填字段
        )
        assert resp.status_code == 400
        assert resp.json()["detail"] == "Contract validation failed"

    def test_no_success_data_message_wrapper(self, client):
        resp = client.post("/api/internal/ai/build-profile", json=make_request())
        body = resp.json()
        assert "success" not in body
        assert "data" not in body
        assert "message" not in body
