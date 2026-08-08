# M7 手动验证手册（缓存 / 限流 / 埋点）

> 版本：v1 · 2026-08-04 · 周贺浚
> 适用：`conversation/ask` 接入 Redis 缓存、Resilience4j 限流、Micrometer 埋点后的验收验证。
> 前置：Redis 6379、MySQL `leapmind-voice`、DashScope API Key 已配置；Java 应用已启动（`mvn spring-boot:run` 或 IDE）。

---

## 〇、验证前准备

```bash
# 1. 起 Redis
redis-server

# 2. 起 MySQL，确认 leapmind-voice 库存在（Flyway 会自动建表）

# 3. 启动 Java
mvn spring-boot:run
```

- `/api/conversation/**` 与 `/actuator/**` 均为 `permitAll`（`SecurityConfig.java:77,80`），**无需 token**，可直接 curl。
- 终端中文乱码是显示问题，不影响数据，先执行一次：
  ```powershell
  [Console]::OutputEncoding = [Text.Encoding]::UTF8
  ```

---

## 一、验证 1：缓存闭环

### 步骤

1. 建 `ask.json`（UTF-8 无 BOM）：
   ```json
   {
     "userId": 1001,
     "question": "勾股定理是什么",
     "sceneType": "general_qa"
   }
   ```

2. **第一次 ask**（走 AI，`thinking → content[0..n] → done`，日志出现 `Cached AI answer to Redis: key=qa:cache:..., ttl=1h`）：
   ```powershell
   curl.exe -N -X POST http://localhost:8080/api/conversation/ask -H "Content-Type: application/json" -d "@ask.json"
   ```

3. **第二次 ask 同问题**（秒回，日志出现 `Redis cache hit, replaying answer...`）：
   ```powershell
   curl.exe -N -X POST http://localhost:8080/api/conversation/ask -H "Content-Type: application/json" -d "@ask.json"
   ```

### 判定

| 检查点 | 通过标准 |
|--------|----------|
| 第二次返回内容 | = 第一次完整答案，且几乎无延迟（不调 AI） |
| 前端协议 | `thinking → content(整段, index:0) → done`，与正常链路一致 |
| Redis key | `qa:cache:<md5(规范化问题)>`，TTL 1h |
| MySQL 入库 | 第二次问后新增 `user` + `assistant` 两条记录（缓存命中也要入库，保证 M6/M3 数据） |

```bash
# 查 Redis 缓存 key
redis-cli keys "qa:cache:*"

# 查入库（最近 5 条）
mysql -uroot -p -e "SELECT session_id, role, LEFT(content,30) FROM leapmind-voice.conversation_messages ORDER BY id DESC LIMIT 5;"
```

### 负向验证

问一个没缓存过的问题 → 必须走 AI、不命中缓存；打断（`/api/conversation/interrupt`）的半成品**不写缓存**，再问同问题应重新调 AI。

---

## 二、验证 2：限流

`userQuestionLimiter`：`limitForPeriod: 10 / 1m`，`@RateLimiter` 注解在 **Controller 入口**拦截，**不管缓存命中还是走 AI，每个 ask 都计数**。

```powershell
1..12 | ForEach-Object {
  $r = curl.exe -s -X POST http://localhost:8080/api/conversation/ask -H "Content-Type: application/json" -d "@ask.json"
  "req $_ : $r"
}
```

### 判定（实测结果）

- **req 1**：走 AI（`tokenUsage input:17 output:425`）→ 写缓存
- **req 2~10**：缓存命中秒回（`tokenUsage input:0 output:0`）→ 顺带验证缓存
- **req 11、12**：SSE `{"type":"error","message":"请求过于频繁，请稍后再试"}`（`event: message` 头与正常链路一致）

> 注意：限流是全局维度，窗口内（1 分钟）触发后所有 ask 都被拒。缓存"秒回"和限流 error 要区分：error 的 `data` 是 `{"type":"error",...}`。

---

## 三、验证 3：埋点（Prometheus / Micrometer）

### 关键点：指标是**按需注册**的

`conversation.ttft` 等指标在**第一次触发前不存在**。所以顺序必须是：**先触发、后查询**。

```powershell
# Step 1：用一个没问过的新问题走一次 AI（产生 ttft / full 计数）
curl.exe -N -X POST http://localhost:8080/api/conversation/ask -H "Content-Type: application/json" -d "{\"userId\":1001,\"question\":\"什么是微积分\",\"sceneType\":\"general_qa\"}"

# Step 2：查 Prometheus 文本
curl.exe -s http://localhost:8080/actuator/prometheus | Select-String "conversation_"

# Step 3：单查 TTFT timer（JSON）
curl.exe -s "http://localhost:8080/actuator/metrics/conversation.ttft"
```

### 指标清单（Prometheus 名称 = 点号转下划线）

| 指标 | 含义 | 触发方式 |
|------|------|----------|
| `conversation_ttft_seconds_count/sum/max` | 首字延迟 TTFT（走 AI 时） | 缓存未命中走 AI，`hookOnNext` 首个 chunk |
| `conversation_interrupt_count` | 打断次数 | `POST /api/conversation/interrupt?sessionId=...` |
| `conversation_stream_error` | AI 流错误次数 | AI 流抛错（`hookOnError`） |
| `conversation_stream_disconnect` | 用户提前断开次数 | 非正常完成时的 `onCancel`/`onDispose`（正常 `done` 不计） |
| `questions_processed_total{type=cache/full}` | 问答处理计数 | 缓存命中（`cache`）/ 完整 AI 回答（`full`），各带 `status=success/error` |

### TTFT 实测参考

```
conversation_ttft_seconds_count = 1.0
conversation_ttft_seconds_sum   = 0.9741129   # ≈ 0.97s 首字延迟
conversation_ttft_seconds_max   = 0.9741129
```

含义：用户从发起到看到第一个字约 0.97s——AI 对话体验最关键的 SLI。缓存命中时该指标不产生（秒回）。

### Grafana 面板建议

| 面板 | 查询（PromQL） | 用途 |
|------|----------------|------|
| TTFT 时序 | `rate(conversation_ttft_seconds_sum[5m]) / rate(conversation_ttft_seconds_count[5m])` | 首字延迟趋势，劣化告警 |
| 打断/错误计数 | `increase(conversation_interrupt_count[5m])`、`increase(conversation_stream_error[5m])` | 体验问题追踪 |
| 缓存效率 | `questions_processed_total{type="cache"}` / `questions_processed_total{type="full"}` | 缓存省了多少 AI 调用 |

---

## 四、常见坑

| 坑 | 现象 | 解决 |
|----|------|------|
| PowerShell `@ask.json` | splatting 报错/未生效 | 必须引号包裹：`-d "@ask.json"` |
| 限流阈值理解 | 连发 10 次后秒回内容也报错 | 缓存命中≠限流；`{"type":"error"}` 才是限流 |
| 指标查不到 | `/actuator/metrics/conversation.ttft` 404 或空 | 指标按需注册，**先走一次 AI 再查** |
| actuator 401 | `{"code":401,"message":"未认证"}` | Security 未放行 `/actuator/**`（已放行，见 `SecurityConfig.java:80`）；改了配置要重启 |
| 终端中文乱码 | 显示 `鍕捐偂瀹氱悊` | `[Console]::OutputEncoding = [Text.Encoding]::UTF8` |
| PowerShell `-Dtest=A,B` | 只跑第一个测试 | 必须加引号：`mvn -o test "-Dtest=A,B"` |

---

## 五、安全提醒（上生产前必读）

- `management.endpoints.web.exposure.include: "*"` 暴露了**所有** actuator 端点（含 `heapdump`/`env`/`beans`）。
- 当前 `/actuator/**` 是 `permitAll`，**仅限本地/内网验证**。
- 上生产建议：`include: health,info,prometheus,metrics` + 独立管理端口 + `/actuator/**` 鉴权。
