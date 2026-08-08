# M7 优化组件对接 — 周贺浚（SSE 链路） ↔ 冼湘宜（高可用优化）技术对齐

> 版本：v1 · 2026-08-04
> 本文档结论已由双方代码核对确认，作为联调契约。

---

## 一、现状结论（对接前必读）

冼湘宜的优化组件已经存在，但目前**只挂在 `VoiceChatService` 上，没有接入 M7 核心的 `ConversationService`（SSE 链路）**。

| 组件 | 类 | 现状 |
|------|----|------|
| Redis 缓存 | `service.common.RedisCacheService` | 已实现，含空值防穿透 |
| 请求合并 | `service.common.RequestMergeService` | 已实现（`CompletableFuture<String>`，面向非流式） |
| 上下文压缩 | `service.common.ContextCompressService` | 已实现，带 Resilience4j 熔断 + Micrometer 埋点 + 降级 |
| 埋点 | `service.common.MetricsService` | 已实现 |
| 限流 | `interceptor.RateLimitInterceptor` + `RateLimiterConfig` | 已实现，但只注册到 `/api/voice-chat/**` |

**问题**：这些组件的 API 面向"一次性返回完整字符串"（`Mono<String>`），而 M7 是 `Flux<ServerSentEvent<?>>` 流式链路，不能直接套用。对接的本质 = 把优化组件"接入"流式链路，而非新建。

---

## 二、四条对接决策（定案）

### ① 静态字符串 → Flux 流式包装 —— ✅ 采纳，但拦截位置必须改

- **冼湘宜方案**：缓存命中 / 限流降级时用 `Flux.just(完整JSON)` 返回。
- **定案**：`Flux.just` 单向可行，但**拦截点必须放在 `ConversationService.streamResponse` 内部**（`POST /api/conversation/ask` → controller → `streamResponse`），**不能放在 Controller 层**。
- 原因：用户消息入库、assistant 消息入库、session 更新（`ConversationService.java:174-206`）都发生在 AI subscriber 回调里。若在 Controller 层直接 `Flux.just` 返回，本次对话会**漏写 `conversation_messages`**，从而切断 M6（画像）/M3（薄弱点）的数据消费。
- **SSE 协议必须与正常链路一致**：`thinking → content[0..n] → done`，且 `content` 拆块模拟打字机。

### ② Cache Key —— ❌ 反对带 sessionId（逻辑硬伤）

- **冼湘宜方案**：`sessionId + question` 联合 MD5 做 key。
- **定案**：**sessionId 绝不入 key**。原因：每次对话 sessionId 都是新生成的（`ConversationService.java:89` `UUID.randomUUID()`），key 带 sessionId 会**永远重复、永远 miss**，缓存形同虚设。
- 正确 key：沿用现有 `CacheKeyBuilder.dedupKey(userId, question)`（`userId` + `questionHash(question)`）。
- 「上下文污染」只在"同一问题跨场景"出现（如 teaching 与 doing_exercise 回答不同）。可接受；若确有场景隔离需求，追加 `sceneType` 即可（枚举有限，可控），但**无需且不能加 sessionId**。

### ③ 并发请求合并 vs SSE —— ✅ 采纳但要分档

- **冼湘宜方案**：用 `Sinks.many().multicast()` / `Flux.share()` 让第二个请求订阅第一个的流。
- 正确性：现有 `RequestMergeService` 基于 `CompletableFuture<String>`（`RequestMergeService.java:19`），只支持"等待完整结果"，对 SSE 实时流**不适用**，需换成 Reactor 多播。
- **分档落地**：
  - **第一期（必做，本期已实现钩子）**：**同 `sessionId + question` 去重**。防前端连点 / 网络重试，改动小，依赖 `activeSubscribers` 已有维度。
  - **第二期（可选，可砍）**：跨 session 合并。需处理"两个 session 各自入库 + 打断联动"，成本高；其价值已被 Redis 缓存覆盖大半，时间紧可延期。

### ④ 监控埋点 / 打断 —— ✅ 采纳，切入位置已约定

| 指标 | 埋点位置 | 指标名 |
|------|----------|--------|
| 首字延迟 TTFT | `ConversationService` `hookOnNext` 首次非 last chunk | `conversation.ttft` |
| 打断次数 | `ConversationService.interrupt()` 入口 | `conversation.interrupt.count` |
| AI 流错误 | `hookOnError` | `conversation.stream.error` |
| 连接断开 / 取消 | `fluxSink.onCancel` / `onDispose` | `conversation.stream.disconnect` |
| 缓存命中/未命中 | `RedisCacheService`（已有 `cache.hits`/`cache.misses`） | — |
| 完整回答计数 | AI 完成后 | `questions.processed.total` |

- **打断半成品**：**不缓存**。半成品入库（`hookOnCancel`，`ConversationService.java:227-234`）用于保留上下文供追问，这是对的；但作为 Redis 缓存答案会误导。**Redis 只缓存 `done` 完整答案。**

---

## 三、代码中发现的重复与不一致（需要冼湘宜清理）

1. **两套重复实现**：`service.common.*` 与 `service.optimize.*` 各有一套 `ContextCompressService` / `MetricsService` / `RedisCacheService` / `RequestMergeService`。
   - `optimize` 包用自定义 bean 名（`@Service("optimizeRedisCacheService")` 等）避免冲突。
   - `common` 版 `ContextCompressService` 带完整 Resilience4j 熔断 + 埋点，功能更全。
   - **建议：统一保留 `common` 版，删除 `optimize` 版（`VoiceChatService` 同步改 import）。** 本次 M7 钩子使用的就是 `common` 版。

2. **限流实例名不一致**：
   - `application-dev.yml:112` 定义了 `resilience4j.ratelimiter.instances.userQuestionLimiter`。
   - `VoiceChatController` 用的是 `@RateLimiter(name = "default")`；`RateLimitInterceptor` 用 `"user-rate-limiter"`（不存在，回退 default）。
   - **建议：M7 `conversation/ask` 复用 `userQuestionLimiter` 实例，统一命名规范。**

3. **拦截器未覆盖 conversation**：`WebMvcConfig.addInterceptors` 只注册 `/api/voice-chat/**`。M7 改用**注解式**限流（见下），不依赖拦截器。

---

## 四、本次已实现的钩子（周贺浚侧变更）

> 改动均为"读取 + 写入缓存 + 埋点"，把优化组件嵌进 SSE 链路，不影响原有走 AI 的主链路。

- `ConversationService`：
  - 注入 `service.common.RedisCacheService`、`service.common.MetricsService`、`MeterRegistry`。
  - `streamResponse` 内用户消息入库后，调用 `tryServeOptimized(req, sessionId, session, callId)`；**缓存命中则直接返回打包好的 SSE 流，跳过 AI 调用**。
  - `buildCachedStream(...)` 组装 `thinking → content… → done` 流（`Flux.fromIterable + delayElements` 模拟打字机）。
  - AI 完整回答（`isLast` 分支）成功后写入 Redis 缓存，形成闭环。
  - TTFT / 打断 / 错误 / 断开 埋点。
- `ConversationController`：
  - `POST /ask` 增加 `@RateLimiter(name = "userQuestionLimiter", fallbackMethod = "askRateLimitFallback")`，降级返回 SSE 错误流。
- `service.common.RedisCacheService`：`NULL_PLACEHOLDER` 改 `public`，供钩子判断"防穿透占位"。

---

## 五、冼湘宜跟进清单

1. 清理 `service.optimize.*` 冗余（统一到 `common` 版；`VoiceChatService` 改 import）。
2. 提供 **SSE 场景的请求合并实现**（`Sinks.many().multicast()` / `Flux.share()`），交付 `Flux<ServerSentEvent<?>>` 而非 `CompletableFuture<String>`——第二期。
3. 确认 Python 端 `/api/internal/ai/compress-context` 已实现并返回结构（`ContextCompressService` 的 `CompressResponse` 字段）。
4. 统一限流实例命名；确认 `userQuestionLimiter` 阈值（现 10 次/min）。
5. 部署 Micrometer → Prometheus 采集端点，约定指标名以 `conversation.*` 前缀为准。

---

## 六、SSE 协议（联调基准，勿改）

```
POST /api/conversation/ask  →  text/event-stream
event: message
data: {"type":"thinking","content":"","sessionId":"..."}

event: message
data: {"type":"content","chunk":"...","index":0}

event: message
data: {"type":"done","callId":"...","sessionId":"...","tokenUsage":{"input":0,"output":0}}

限流降级：
data: {"type":"error","message":"请求过于频繁，请稍后再试"}
```

---

## 七、为什么 M7 需要 `qa:cache` 答案缓存（需求溯源）

> 被反复问"是自己随意加的吗？"——不是，是需求明文要求，且前缀非自造。

**需求出处：**

| 文档 | 条款 | 内容 |
|------|------|------|
| `职责范围.md` | 降级策略（第 66 行） | Redis 缓存常见问题答案（**按 question hash 做 key**），兜底模板化回答 |
| `LeapMind教育网站.md` | 对话请求优化（第 2036 行） | 相似问题缓存：Redis 缓存高频问题的答案（**按 question 文本 hash 做 key，TTL 1h**） |

**`qa:cache:` 前缀的来源：** 文档只要求"按 question hash 做 key + TTL 1h"，没有指定前缀名。`qa:cache:` 是冼湘宜 `CacheKeyBuilder.QUESTION_CACHE_PREFIX` 里**现成的 key 空间约定**，`VoiceChatService` 已在用。沿用它是为：文本对话 + 语音问答**共享同一 key 空间**，同一个问题在任一条链路问过、另一条也能命中，缓存与空值防穿透（`__NULL__`）逻辑统一对齐。

**与 `user:session:{sessionId}` 不是同类，勿混淆：**

| 维度 | `user:session:{sessionId}` | `qa:cache:{questionHash}` |
|------|---------------------------|---------------------------|
| 按什么 | **会话**维度 | **问题**维度 |
| 内容 | 最近 20 条消息摘要（状态） | 单个问题完整答案 |
| TTL | 30min | 1h |
| 用途 | 断线恢复 / 追问上下文 | 高频问题秒回 / 降级防穿透 |
| 归属 | 周贺浚（4.1.3 会话管理） | 冼湘宜组件实现，周贺浚负责接入链条 |
| key | `user:session:{UUID}` 每会话唯一 | `qa:cache:{问题hash}` 全局共享 |

**责任分工（重要）：** `qa:cache` 的**组件**（`RedisCacheService`/`CacheKeyBuilder`/`MetricsService`）由冼湘宜实现；周贺浚作为 M7 Java 接口人负责的是**把组件嵌进 SSE 链路**（先查、命中秒回、AI 完成后写回、埋点）。不是自创业务，而是复用组件 + 接入落点。

---

## 八、配套文档

- `docs/m7/M7-verification.md`：缓存 / 限流 / 埋点手动验证手册（含 curl 脚本、Grafana 面板、常见坑、安全提醒）。