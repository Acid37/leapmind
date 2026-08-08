# LeapMind M6 用户画像接口文档

> 契约版本：`1.0`  
> 更新日期：2026-08-05  
> 机器可读契约：[`user-profile-openapi.yaml`](./user-profile-openapi.yaml)

## 1. 接口范围与实现状态

本文只描述张梓鸿负责的 M6 学习事件采集和用户画像查询接口。

| 方法 | 路径 | 功能 | 当前状态 |
| --- | --- | --- | --- |
| POST | `/api/user-profile/{userId}/record-event` | 写入单条学习事件 | 已实现：校验、鉴权、幂等和落库可用 |
| POST | `/api/user-profile/{userId}/batch-events` | 写入 1～100 条学习事件 | 已实现：逐项返回处理结果 |
| GET | `/api/user-profile/{userId}` | 查询完整画像 | 查询接口已实现；自动画像投影尚未接通 |
| GET | `/api/user-profile/{userId}/summary` | 查询场景化画像摘要 | 查询接口已实现；自动画像投影尚未接通 |
| GET | `/api/user-profile/{userId}/knowledge-status` | 查询知识点掌握状态 | 查询接口已实现；自动画像投影尚未接通 |

`/review-reminders`、`/mark-reviewed` 和 `/review-history` 属于复习提醒模块，不属于本 M6 核心契约，因此不纳入本 OpenAPI。`/api/auth/**` 属于统一认证模块，也不纳入本契约。

## 2. 公共约定

### 2.1 认证和权限

所有接口要求 Bearer JWT：

```http
Authorization: Bearer <accessToken>
```

接口执行 self-only 权限校验：Token 对应用户、路径 `userId` 和事件体 `userId` 必须一致。

### 2.2 统一响应结构

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {},
  "timestamp": 1785888000000
}
```

M6 错误响应的 `data` 结构：

```json
{
  "requestId": "req-001",
  "errorCode": "PROFILE_EVENT_INVALID",
  "details": []
}
```

响应头 `X-Request-Id` 用于服务端问题追踪。废弃接口别名还会返回 `Deprecation` 和 `Link`。

### 2.3 媒体类型

事件写入支持：

- `application/json`
- `application/*+json`

其他媒体类型返回 HTTP `415`。

## 3. 学习事件结构

```json
{
  "eventId": "web-answer-20260805-0001",
  "userId": 22,
  "eventType": "answer_question",
  "sourceModule": "M1",
  "occurredAt": "2026-08-05T14:30:00+08:00",
  "schemaVersion": "1.0",
  "sessionId": "practice-session-001",
  "kpId": 101,
  "traceId": "web-trace-20260805-0001",
  "data": {
    "isCorrect": false,
    "difficulty": 3,
    "timeSpentSec": 42,
    "hintCount": 1,
    "confusionTag": "concept_unclear"
  }
}
```

### 3.1 公共字段

| 字段 | 必填 | 约束 |
| --- | --- | --- |
| `eventId` | 是 | 1～64 字符；仅字母、数字、`.`、`_`、`:`、`-`；同一业务事件保持稳定 |
| `userId` | 是 | 正整数，必须等于路径 userId 和当前用户 |
| `eventType` | 是 | 固定枚举，见下表 |
| `sourceModule` | 是 | 必须与 eventType 对应 |
| `occurredAt` | 是 | 带时区的 ISO 8601 时间；服务端规范化为 UTC 毫秒精度 |
| `schemaVersion` | 是 | 当前固定为 `1.0` |
| `sessionId` | 否 | 最长 64 字符，格式同 eventId |
| `kpId` | 否 | 正整数；业务上 `answer_question` 要求传入可解析知识点 ID |
| `traceId` | 否 | 最长 64 字符 |
| `data` | 是 | 严格按事件类型校验，不允许未知字段 |

### 3.2 事件类型

| eventType | sourceModule | data 必填字段 |
| --- | --- | --- |
| `answer_question` | `M1` | `isCorrect,difficulty,timeSpentSec,hintCount` |
| `finish_practice` | `M1` | `questionCount,accuracy,durationSec` |
| `request_explanation` | `M2` | `explainId,reasonTag` |
| `explanation_feedback` | `M2` | `explainId,feedback,repeatCount` |
| `weak_point_changed` | `M3` | `oldScore,newScore,reason` |
| `lecture_interact` | `M4` | `lectureId,chapterId,action` |
| `lesson_material_used` | `M5` | `contentId,materialType,result` |
| `ask_doubt` | `M7` | `topic,confusionTag,isFollowUp` |
| `mark_reviewed` | `M6` | `result,timeSpentSec,hintCount` |
| `preference_changed` | `M6` | `preferenceKey,preferenceValue` |

完整枚举值和每个字段的范围以 OpenAPI schema 为准。

### 3.3 大小和安全限制

- 单次 HTTP 请求最大 2 MiB。
- 规范化后的 `data` 最大 16 KiB。
- JSON 最大深度 8，最多 256 个节点。
- 单个文本值最多 2048 UTF-8 字节。
- 不允许在 `data` 中传入密码、Token、Authorization、API Key、身份证号、题目/答案全文、聊天全文或调用方自行拼接的画像摘要。
- 超过接收时间前后 24 小时的事件返回成功 ACK，但 `profileUpdateStatus=QUARANTINED`，不进入正常画像投影。

### 3.4 幂等语义

| 情况 | 结果 |
| --- | --- |
| 首次提交 eventId | `eventStatus=ACCEPTED`、`duplicate=false` |
| 相同 eventId 和相同规范化 payload | `eventStatus=DUPLICATE`、`duplicate=true` |
| 相同 eventId 但 payload 不同 | HTTP `409`、`PROFILE_IDEMPOTENCY_CONFLICT` |

`occurredAt` 在参与幂等哈希前会转换为 UTC 并截断到毫秒精度。

## 4. 写入单条事件

```http
POST /api/user-profile/{userId}/record-event
```

请求体为第 3 节的学习事件结构。

成功响应 `data`：

```json
{
  "acknowledged": true,
  "eventId": "web-answer-20260805-0001",
  "eventStatus": "ACCEPTED",
  "duplicate": false,
  "profileUpdateStatus": "PENDING",
  "receivedAt": "2026-08-05T06:30:01Z",
  "requestId": "req-001"
}
```

## 5. 批量写入事件

```http
POST /api/user-profile/{userId}/batch-events
```

请求体：

```json
{
  "events": [
    {
      "eventId": "web-answer-001",
      "userId": 22,
      "eventType": "answer_question",
      "sourceModule": "M1",
      "occurredAt": "2026-08-05T14:30:00+08:00",
      "schemaVersion": "1.0",
      "kpId": 101,
      "data": {
        "isCorrect": true,
        "difficulty": 2,
        "timeSpentSec": 20,
        "hintCount": 0
      }
    }
  ]
}
```

`events` 数量必须为 1～100。响应 `data` 按请求顺序返回，每项状态为 `ACCEPTED`、`DUPLICATE` 或 `FAILED`。参数错误允许部分成功；数据库故障返回 HTTP `503`，此前已经提交的项可能保留。

## 6. 查询完整画像

```http
GET /api/user-profile/{userId}
```

无可用画像时返回 HTTP `200`：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "userId": 22,
    "profileStatus": "NOT_READY",
    "statusReason": "NO_PROFILE",
    "profileVersion": 0
  },
  "timestamp": 1785888002000
}
```

存在画像投影时，`profileStatus` 为 `READY` 或 `STALE`，并可能返回：

- `profileVersion`
- `grade`
- `preferredContentModes`
- `preferredExplanationStyle`
- `learningPace`
- `recentFocus`
- `recentConfusions`
- `summaryProfile`
- `confidence`
- `algorithmVersion`
- `lastEventAt`、`computedAt`
- `knowledge`

`STALE` 表示画像可读取，但不是基于最新事件计算。

## 7. 查询场景化摘要

```http
GET /api/user-profile/{userId}/summary?sceneType=<scene>&kpId=<kpId>
```

| sceneType | kpId | 状态 |
| --- | --- | --- |
| `explaining` | 必填 | 支持 |
| `lecturing` | 可选 | 支持 |
| `conversation` | 可选 | 支持 |
| `photo_qa` | 必填 | `explaining` 的废弃别名 |
| `lesson_prep` | - | 当前未开放，返回 403 |

`photo_qa` 的响应体会规范化为 `sceneType=explaining`，并返回 `Deprecation` 和 `Link` 响应头。无可用画像时返回 `NotReadyProfile`。

## 8. 查询知识点掌握状态

```http
GET /api/user-profile/{userId}/knowledge-status?kpId=101&kpId=102
```

`kpId` 数量为 1～100，必须为不重复的正整数。响应顺序与请求顺序一致。

| status | 含义 |
| --- | --- |
| `AVAILABLE` | 有可用掌握度数据 |
| `INSUFFICIENT_EVIDENCE` | 存在记录，但证据不足 |
| `EMPTY` | 当前画像存在，但没有该知识点记录 |
| `NOT_READY` | 用户画像尚未生成 |

`masteryScore`、`confidence` 和 `trend` 可能为 null；null 不等于 0。

## 9. 错误码

| HTTP | errorCode | 含义 |
| --- | --- | --- |
| 400 | `PROFILE_EVENT_INVALID` | 参数或事件 payload 不合法 |
| 400 | `PROFILE_EVENT_TYPE_UNSUPPORTED` | 不支持的事件类型 |
| 400 | `PROFILE_EVENT_VERSION_UNSUPPORTED` | 不支持的 schemaVersion |
| 401 | `PROFILE_UNAUTHENTICATED` | JWT 缺失或失效 |
| 403 | `PROFILE_ACCESS_DENIED` | 用户越权或场景未开放 |
| 409 | `PROFILE_IDEMPOTENCY_CONFLICT` | 同一 eventId 对应不同 payload |
| 415 | `PROFILE_EVENT_INVALID` | Content-Type 不支持 |
| 500 | `PROFILE_INTERNAL_ERROR` | 未分类的服务端错误 |
| 503 | `PROFILE_SERVICE_DEGRADED` | 数据库或依赖服务不可用 |

## 10. 当前契约限制

1. 事件采集、严格校验、self-only 鉴权、幂等和 `user_events` 落库已经实现。
2. Java → Python 画像计算、投影回写和自动刷新尚未接通，因此事件成功写入不代表画像立即变为 `READY`。
3. `answer_question` 在业务契约中要求 `kpId`，但当前 HTTP DTO/OpenAPI 尚未强制；是否在服务端拒绝缺失 `kpId`，需要 M1/M6 负责人确认。
4. 旧 `/api/events/**` 使用另一张事件表，不属于本契约；新接入只使用本文两条事件写入接口。
