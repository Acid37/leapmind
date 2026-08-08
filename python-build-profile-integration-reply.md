# 回复：Java—Python build-profile 联调清单

> 本回复文档由陈富民编写，回复张梓鸿的 `python-build-profile-integration-checklist.md`。
> 已逐节核对 `aitutor-backend-python`、`aitutor-backend-java` 代码、`profile-engine-contract.yaml` 与 `docs/m6/*.md`。
> 日期：2026-08-08

## 0. 总体结论

1. 路由 `/api/internal/ai/build-profile` **已存在**（`aitutor-backend-python/src/landppt/m6/router.py`），
   但当前请求/响应模型不符合 `profile-engine-contract.yaml`，Python 侧将按契约重构。
2. 清单第 2/6 节有多处"Java 已就绪"断言与仓库实际**不符**，需要 Java 侧复核（详见第 6 节）。
3. 契约与仓库里的权威结论（`profile-engine-contract.yaml` + `ProfileEngineWireRequest` + `docs/m6/*.md`）
   **以 `userId`(camelCase) 为准**，清单第 2/3 节中的 `user_id` 与权威契约冲突，需修正。

## 1. 权威契约

- 确认：`aitutor-backend-java/docs/m6/profile-engine-contract.yaml` 为唯一权威，Java 侧
  `StrictProfileEngineJsonCodec` + `ProfileEngineContractValidator` 严格校验。
- 三态 `READY / INSUFFICIENT_DATA / NO_CHANGE` 大小写与 yaml 一致，确认。
- ⚠️ 提醒：yaml `BuildProfileRequest.additionalProperties: false` 且必填 `userId`，
  而清单第 2 节要求 Python"接受 `user_id`"——**两者矛盾**，见第 2 节。

## 2. 请求（Java → Python）逐项回复

| 项 | 回复 |
|---|---|
| 顶层字段名 | ❌ 清单写 `user_id` 有误。yaml 必填 `userId`，Java `ProfileEngineWireRequest` 序列化同为 `userId`（`ProfileEngineWireRequest.java`，默认 camelCase）。**Python 按 `userId` 收**，并做 `AliasChoices("userId","user_id")` 双兼容作为防御。响应一律回显 camelCase `userId` |
| 其余顶层字段 | ✅ 已核对，`contractVersion / requestId / mode / baseProfileVersion / fromEventIdExclusive / eventWatermarkInclusive / events` 全部进入 Python 请求模型 |
| additionalProperties | ✅ Python 不设置 forbid_extra（Pydantic `extra="ignore"`），容忍多余字段。⚠️ 但注意：**容忍只适用于请求**；响应侧 Java 是 `FAIL_ON_UNKNOWN_PROPERTIES`，响应必须严格契约、不得含 `success/data/message` 等多余键（当前 Python 响应正是这类结构，将重构） |
| events.data 驼峰 | ✅ 已核对。Python 将按契约字段读取：`answer_question.isCorrect`、`finish_practice.accuracy`（当前误读 `score`）、时长 `durationSec`（当前误读 `durationSeconds/duration_seconds`），并保留旧字段兜底 |
| schemaVersion `"1.0"` | ✅ 一致 |
| kpId 允许 null | ✅ Python 侧 `Optional[int]`，未映射事件跳过，不报错 |
| 十种事件类型 | ✅ 已对齐；yaml `eventType` 为自由字符串，Python 仅关注影响计算的类型（`answer_question`/`finish_practice`/`request_explanation`/`mark_reviewed`），无需强制枚举 |

## 3. 响应（Python → Java）逐项回复

| 项 | 回复 |
|---|---|
| status 三态 | 将按 yaml 三态实现：**空事件批 → NO_CHANGE**；有事件但无可解析 kp → INSUFFICIENT_DATA；有 kp 证据 → READY |
| 回显字段 | 将原样回显 `requestId / userId / baseProfileVersion / eventWatermarkInclusive`（wire 字段为 `userId`，非 `user_id`） |
| targetProfileVersion | READY/INSUFFICIENT_DATA = base+1；NO_CHANGE = base；并处理 `base == 2^63-1` 边界 |
| knowledgeMastery camelCase | ✅ 已核对，将按 `masteryScore/masteryStatus/confidence/evidenceCount/trend/algorithmVersion/windowStart/windowEnd/updatedAt` 输出 |
| masteryStatus 五值 | ✅ 引擎已产出 `WEAK/CONSOLIDATING/BASIC_MASTERY/MASTERED/INSUFFICIENT_EVIDENCE` |
| trend 三值（可 null） | ✅ 无状态引擎无历史对比，将省略 trend（Java 校验器允许 null），不产生非法值 |
| 精度 ≤ 4 位 | ✅ 引擎已 round 到 4 位（masteryScore/confidence）、3 位（profile.confidence） |
| 非法输出不被保存 | Java 侧责任，确认 Java 已实现；Python 只输出契约内字段 |
| NO_CHANGE 不带 profile/mastery | ✅ Python 的 NoChangeResponse 模型不含这两键 |

## 4. 传输与认证

| 项 | 回复 |
|---|---|
| 端口 | ⚠️ 清单写 8001，但仓库中**不存在** `m6.profile-engine.base-url` 配置键；Java 现有 `python-service.base-url` 与 Python 实际运行均为 **8000**（`application.yml`、`main.py`、README）。**请 Java 侧确认统一 8000**，并同步修正 yaml `servers.url` |
| 认证 | ⚠️ 当前 `public_prefixes`（`auth/middleware.py`）**不含** `/api/internal/`，会被中间件按未认证拦截返回 401。Python 侧将把 `/api/internal/` 加入 `public_prefixes`（临时免认证）；serviceBearer 留待后续双方同步 |
| 幂等 | 同 requestId 同结果对无状态引擎天然成立 ✅；"不同内容同 requestId → 409"属 Java CAS 持久化职责（`platform-integration-adr.md`、`profile-projection-cas-runbook.md`），**Python 不实现** |
| 失败码 | Python 将请求校验失败映射为 **400**（契约失败）、引擎错误为 **503**；401/403/409 由 Java/部署层承担 |

## 5. Python 侧待办回复

1. 路由已存在，将**重构为符合契约**（请求模型、三态响应、400/503）。
2. 事件类型已对齐（见第 2 节）；`answer_submitted` 等旧类型仅在 `user_profile/` legacy 模块内部，不动。
3. 响应结构按第 3 节字段名与枚举输出。
4. 将新增 pytest 契约用例：合法 READY / INSUFFICIENT_DATA / NO_CHANGE、缺必填字段→400、未知字段容忍、
   响应顶层键集合严格等于契约（防多余键）、target 规则、回显正确、时间戳 `Z` 结尾、`user_id` 兼容解析。
5. ⚠️ `docs/m6/build-profile-local-stub.md` **不存在**。将改用 pytest 契约用例自测；请 Java 侧补 stub 文档，或确认此方式。

## 6. Java 侧"已就绪的验证"——与仓库不符，需复核

| 清单断言 | 实际 |
|---|---|
| `HttpProfileEngineAdapterTest`（MockWebServer）存在 | ❌ 不存在。全分支搜索无 `HttpProfileEngineAdapter`/`MockWebServer` 提交 |
| `HttpProfileEngineAdapterDefaultsTest` 存在 | ❌ 不存在 |
| Java 客户端已实现 | ❌ 未实现。`ProfileEnginePort` 唯一实现为 `DisabledProfileEngineAdapter`（no-op） |
| 依据 | PR #68 正文明确"不包含 Java HTTP client、Python route、真实 policy-enabled Adapter"；`module-integration-guide.md`、`platform-integration-adr.md:13`、`data-dictionary.md:7` 均写明 not included |

⚠️ 另：清单第 6 节 MockWebServer 测试断言为"`user_id` 在、`userId` 不在"——与契约相反。
将来实现适配器时，断言应为"`userId` 在、`user_id` 不在"，事件 data 保持 camelCase。
已存在的真实 m6 契约测试是 `PlatformContractsTest` / `ProfileEngineContractValidatorTest` /
`M6ProfileEngineContractSchemaTest`（`mvn test -Pm6-tests` 可跑），但不是 HTTP 适配器测试。

## 7. 需要 Java 侧确认/修正的清单

| 项 | 建议 |
|---|---|
| `user_id` → `userId` | 请求/响应/测试断言统一 camelCase |
| 端口 8001 → 8000 | 补 `m6.profile-engine` 配置或复用 `python-service.base-url`；修正 yaml `servers.url` |
| `HttpProfileEngineAdapter` | 确认交付计划（本次 Python 侧不依赖它，可并行开发） |
| `build-profile-local-stub.md` | 补文档或改用 pytest 契约用例 |
| public_prefixes 状态 | 确认临时免认证方案（Python 侧将新增 `/api/internal/`） |

## 8. 下一步

- Python 侧：按第 2/3/5 节重构 `m6` 模块并补齐契约测试（当前分支 `feat/m6-chenfuming`）。
- Java 侧：按第 6/7 节复核并实现 HTTP 适配器后，双方以 `profile-engine-contract.yaml` 联调。
