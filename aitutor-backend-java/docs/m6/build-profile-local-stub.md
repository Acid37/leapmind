# M6 build-profile 本地联调 stub

本文说明如何在没有 Python 画像引擎时，用本地 stub 验证 Java 侧的
`HttpProfileEngineAdapter`（真实 WebClient 客户端）。

## 1. 启用真实客户端

`application.yml`（已含默认值，可用环境变量覆盖）：

```yaml
m6:
  profile-engine:
    enabled: ${M6_PROFILE_ENGINE_ENABLED:false}
    base-url: ${M6_PROFILE_ENGINE_BASE_URL:http://localhost:8001}
    build-profile-path: /api/internal/ai/build-profile
```

- `enabled=true` 时注册 `HttpProfileEngineAdapter`，`DisabledProfileEngineAdapter` 兜底自动退让；
- `enabled=false`（默认）时保持 NOT_CONNECTED 兜底，现有行为不变；
- 前缀刻意不使用 `python-service`/`python.api`，避免与复习计算（吴敏希）在 Spring 宽松绑定下互相覆盖。

## 2. 请求形状（Java → Python）

Java 按 `profile-engine-contract.yaml` 原样发送契约体，顶层字段为 camelCase `userId`（与 yaml 必填一致）。
事件体保持 Java 驼峰字段（`isCorrect`、`timeSpentSec`、`isFollowUp` 等）。

```json
{
  "contractVersion": "1.0",
  "requestId": "00000000-0000-0000-0000-000000000001",
  "userId": 7,
  "mode": "INCREMENTAL",
  "baseProfileVersion": 0,
  "fromEventIdExclusive": 0,
  "eventWatermarkInclusive": 1,
  "events": [
    {
      "dbEventId": 1,
      "eventId": "evt-1",
      "eventType": "ask_doubt",
      "sourceModule": "M7",
      "occurredAt": "2026-07-28T00:00:00Z",
      "schemaVersion": "1.0",
      "kpId": null,
      "traceId": "trace-1",
      "data": {"topic": "right_triangle", "confusionTag": "concept_unclear", "isFollowUp": true}
    }
  ]
}
```

## 3. 本地 stub（Python FastAPI 最小实现）

```python
# stub_profile_engine.py — 仅用于本地联调，非生产实现
from fastapi import FastAPI
from pydantic import BaseModel
from typing import Any, Optional
import uvicorn

app = FastAPI()

class ProfileEvent(BaseModel):
    dbEventId: int
    eventId: str
    eventType: str
    sourceModule: str
    occurredAt: str
    schemaVersion: str
    sessionId: Optional[str] = None
    kpId: Optional[int] = None
    traceId: Optional[str] = None
    data: dict[str, Any]

class BuildProfileRequest(BaseModel):
    contractVersion: str
    requestId: str
    userId: int
    mode: str
    baseProfileVersion: int
    fromEventIdExclusive: int
    eventWatermarkInclusive: int
    events: list[ProfileEvent]

class KnowledgeMastery(BaseModel):
    kpId: int
    masteryScore: float
    masteryStatus: str
    confidence: float
    evidenceCount: int
    trend: Optional[str] = None
    algorithmVersion: str
    windowStart: Optional[str] = None
    windowEnd: Optional[str] = None
    updatedAt: str

class Profile(BaseModel):
    grade: Optional[str] = None
    preferredContentModes: list[str]
    preferredExplanationStyle: Optional[str] = None
    learningPace: Optional[str] = None
    recentFocus: list[dict]
    recentConfusions: list[dict]
    summaryProfile: Optional[str] = None
    confidence: Optional[float] = None

@app.post("/api/internal/ai/build-profile")
def build_profile(req: BuildProfileRequest):
    if not req.events:
        return {
            "status": "NO_CHANGE",
            "contractVersion": "1.0",
            "requestId": req.requestId,
            "userId": req.userId,
            "baseProfileVersion": req.baseProfileVersion,
            "targetProfileVersion": req.baseProfileVersion,
            "eventWatermarkInclusive": req.eventWatermarkInclusive,
            "algorithmVersion": "stub-1",
            "evaluatedAt": "2026-07-28T00:00:00Z",
        }
    return {
        "status": "READY",
        "contractVersion": "1.0",
        "requestId": req.requestId,
        "userId": req.userId,
        "baseProfileVersion": req.baseProfileVersion,
        "targetProfileVersion": req.baseProfileVersion + 1,
        "eventWatermarkInclusive": req.eventWatermarkInclusive,
        "algorithmVersion": "stub-1",
        "evaluatedAt": "2026-07-28T00:00:00Z",
        "profile": {
            "grade": "7",
            "preferredContentModes": ["text", "image"],
            "preferredExplanationStyle": "step_by_step",
            "learningPace": "moderate",
            "recentFocus": [{"kpId": 1, "weight": 0.8}],
            "recentConfusions": [{"kpId": 2, "detail": "formula", "evidenceCount": 3, "confidence": 0.5,
                                  "lastOccurredAt": "2026-07-28T00:00:00Z"}],
            "summaryProfile": "stub",
            "confidence": 0.5,
        },
        "knowledgeMastery": [{
            "kpId": 1, "masteryScore": 0.8, "masteryStatus": "BASIC_MASTERY",
            "confidence": 0.6, "evidenceCount": 5, "trend": "IMPROVING",
            "algorithmVersion": "stub-1",
            "windowStart": "2026-07-27T00:00:00Z", "windowEnd": "2026-07-28T00:00:00Z",
            "updatedAt": "2026-07-28T00:00:00Z",
        }],
    }

if __name__ == "__main__":
    uvicorn.run(app, host="127.0.0.1", port=8001)
```

运行：`python stub_profile_engine.py`，然后设置 `M6_PROFILE_ENGINE_ENABLED=true` 启动 Java。

## 4. 客户端行为

- 非 2xx / 连接失败 / 超时（30s）→ `ProfileEngineUnavailableException("UNREACHABLE")`；
- 空响应 → `EMPTY_RESPONSE`；
- 响应 JSON 非法、字段未知/重复、回显 requestId/userId/baseProfileVersion/eventWatermarkInclusive
  不匹配、target ≠ base+1、masteryStatus/trend 枚举越界 → `INVALID_RESPONSE`，
  调用方（CAS 写入器）必须拒绝保存，不得覆盖旧画像。
