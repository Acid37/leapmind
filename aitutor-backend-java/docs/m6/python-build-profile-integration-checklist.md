# Java—Python build-profile 联调清单（交给陈富民）

> 本文档由张梓鸿编写，说明 Java 侧已就绪的契约与客户端，以及 Python 侧需要对齐的点。
> 属于 Python 画像引擎的代码、算法和路由由 Python 成员实现，本清单只负责把契约钉死。
> 状态：**Java 客户端已实现并有 MockWebServer 契约测试（2026-08-08）**；陈富民已回复（`python-build-profile-integration-reply.md`）：
> Python 路由已存在（`src/landppt/m6/router.py`）且将按契约重构；顶层字段以契约 `userId` 为准（Java 侧已同步修正，见 2026-08-08 修订）。
> 修订记录：2026-08-08 顶层字段 `user_id` → `userId`（对齐 `profile-engine-contract.yaml` 必填 `userId`）。

## 1. 权威契约

- `aitutor-backend-java/docs/m6/profile-engine-contract.yaml`（openapi 3.0.3，v1.0）
- Java 严格校验：`StrictProfileEngineJsonCodec` + `ProfileEngineContractValidator`
- 已发布响应状态：`READY`、`INSUFFICIENT_DATA`、`NO_CHANGE`（大小写与 yaml 一致）

## 2. 请求（Java → Python）

| 项 | 要求 | 当前状态 |
|---|---|---|
| 顶层字段 | `userId`（camelCase，契约必填；Python 可保留 `user_id` 别名兼容旧实现） | Java 已实现；**Python 端按 `userId` 接收** |
| 其余顶层字段 | `contractVersion`、`requestId`、`mode`、`baseProfileVersion`、`fromEventIdExclusive`、`eventWatermarkInclusive`、`events` | 见 yaml `BuildProfileRequest` |
| `additionalProperties` | Python 模型**不要设置 forbid_extra**，Java 按完整契约体发送，多余字段应被容忍 | Python 已确认（Pydantic `extra="ignore"`） |
| events.data | 事件 data 保持 Java 驼峰字段（`isCorrect`、`timeSpentSec`、`hintCount`、`isFollowUp`、`confusionTag` 等） | Python 已核对 |
| schemaVersion | 每个事件 `"1.0"` | 一致 |
| kpId | Long，允许 null（未映射知识点事件） | Python 已确认（`Optional[int]`） |

十种事件类型：`answer_question`、`finish_practice`、`request_explanation`、`explanation_feedback`、
`weak_point_changed`、`lecture_interact`、`lesson_material_used`、`ask_doubt`、`mark_reviewed`、`preference_changed`。

## 3. 响应（Python → Java）

| 项 | 要求 | 当前状态 |
|---|---|---|
| status 判别 | 按 `status` 字段区分三态，与 Java 枚举一致 | **待 Python 实现** |
| 回显字段 | 必须原样回显 `requestId`、`userId`、`baseProfileVersion`、`eventWatermarkInclusive` | 缺失或错值 → Java 判 `INVALID_RESPONSE` |
| targetProfileVersion | READY/INSUFFICIENT_DATA 必须 = baseProfileVersion + 1；NO_CHANGE 必须 = base | 同上 |
| knowledgeMastery | 字段名**保持 camelCase**：`masteryScore`、`masteryStatus`、`evidenceCount`、`algorithmVersion`、`windowStart`、`windowEnd`、`updatedAt` | **待 Python 确认** |
| masteryStatus 取值 | `WEAK`、`CONSOLIDATING`、`BASIC_MASTERY`、`MASTERED`、`INSUFFICIENT_EVIDENCE` | 枚举严格 |
| trend 取值 | `IMPROVING`、`STABLE`、`DECLINING`（可 null） | 枚举严格 |
| 精度 | masteryScore/confidence 小数位 ≤ 4（multipleOf 0.0001） | 超精度 → 拒绝 |
| 非法输出 | 不得被 Java 保存，不覆盖旧画像 | Java 已实现 |
| NO_CHANGE | profile/knowledgeMastery 字段不得出现 | Java 已实现 |

## 4. 传输与认证

| 项 | 要求 |
|---|---|
| 端口 | 8000（`m6.profile-engine.base-url` 默认值；Python `main.py` uvicorn 实际端口，契约 yaml 的 `servers.url: 8001` 仅为开发默认，可用 `PROFILE_ENGINE_BASE_URL` 覆盖） |
| 认证 | `/api/internal/ai/build-profile` 在 Python `public_prefixes`（免认证）中；若改走 service bearer，需双方同步 |
| 幂等 | 同一 `requestId` 重试返回同一结果；不同内容同 requestId → 409 语义 |
| 失败码 | 400 契约失败 / 401 未认证 / 403 身份拒绝 / 409 冲突 / 503 引擎不可用 |

## 5. Python 侧待办（交 Python 成员）

1. 核对 `aitutor-backend-python` 是否已有 `/api/internal/ai/build-profile` 路由；没有则新增，映射到现有画像算法。
2. 事件类型枚举与 Java 十种事件对齐（现有 `answer_submitted` 等旧类型作为内部实现细节，不要求对外兼容）。
3. 响应结构按本文第 3 节字段名与枚举输出。
4. 增加 pytest 契约用例：合法 READY / INSUFFICIENT_DATA / NO_CHANGE、错误回显、target 越界、未知字段容忍。
5. 联调时用 `docs/m6/build-profile-local-stub.md` 的 stub 先行自测 Java 侧。

## 6. Java 侧已就绪的验证

- `HttpProfileEngineAdapterTest`（MockWebServer）：请求形状（`user_id` 在、`userId` 不在、事件保 camelCase）、
  READY/INSUFFICIENT_DATA/NO_CHANGE 映射、非 2xx → UNREACHABLE、非法 JSON/契约错配 → INVALID_RESPONSE、空体 → EMPTY_RESPONSE。
- `HttpProfileEngineAdapterDefaultsTest`：默认不注册、enabled=true 时注册且 Disabled 退让。
- 运行：`mvn test -Pm6-tests`。
