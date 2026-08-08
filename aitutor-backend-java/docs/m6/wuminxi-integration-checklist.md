# M6 与吴敏希负责模块的协作清单

> 更新日期：2026-08-08  
> 用途：后端内部对齐，不需要整份发送给前端。

## 1. 当前责任边界

根据当前代码作者和提交记录，吴敏希负责的主要范围是：

- `EventCollectionController` / `EventCollectionService`：旧 `/api/events/**` 和 `event_collections` 表；
- `UserProfileController` 中复习提醒接口：`review-reminders`、`mark-reviewed`、`review-history`；
- `ReviewReminderService`、Mapper、Entity、VO；
- `ReviewCalculationTask`：02:00 全量计算、03:00 事件处理、04:00 逾期检查；
- `PythonServiceProperties` 及 Java 调用 Python 复习接口的配置。

你当前负责的主要范围是：

- 新 M6 `user_events` 事件契约、严格校验、鉴权、幂等；
- 完整画像、场景摘要、知识点状态查询；
- 画像引擎 Java/Python 契约；
- 平台事件发布、画像上下文 provider、默认拒绝策略；
- M6 OpenAPI、Apifox 演示和前端联调文档。

## 2. 必须共同确认的 P0 问题

### 2.0 跨机器联调地址与 CORS

`localhost` 只能给同机联调使用。后端 CORS 已支持通过 `APP_CORS_ALLOWED_ORIGINS` 配置精确允许来源（含 `*` 的配置会被拒绝），并已暴露 `X-Request-Id`、`Deprecation` 和 `Link`；默认仍只允许同机的 localhost/127.0.0.1 前端。把文档发给前端之前，需要共同确认：

1. 前端实际 Origin（局域网 IP、开发域名和端口）；
2. 后端实际对外 baseUrl；
3. 开发环境通过配置项维护允许来源，不把生产环境永久放成任意 Origin；
4. Windows 防火墙/部署安全组是否允许 8080 或统一网关端口；
5. 实际部署环境已设置前端精确 Origin，未使用任意来源通配符。

### 2.1 新旧事件表的边界

当前同时存在：

- 旧链路：`/api/events/**` → `event_collections`
- 新链路：`/api/user-profile/{userId}/record-event` → `user_events`

需要与吴敏希确认并形成明确结论：

1. 新画像是否统一只读取 `user_events`；建议答案为“是”。
2. `event_collections` 是否只保留旧复习排期兼容，不再接收新 M6 画像事件。
3. 禁止前端或业务模块双写两张表，否则会出现重复计数和状态不一致。
4. 旧 `/api/events/**` 的停用计划、兼容期限和调用方名单。

建议对外口径：前端新代码只使用 M6 新接口，旧 `/api/events/**` 不写进前端联调文档。

### 2.2 `mark-reviewed` 如何产生 M6 事件

> 状态更新（2026-08-08）：张梓鸿已在 `ReviewReminderServiceImpl.markAsReviewed` 成功后自动发布 `mark_reviewed` 事件（后端发布，问题 1 已定）；事件无 kpId（`KnowledgePointRef.none()`），payload 保守默认 `result="correct_without_hint", timeSpentSec=0, hintCount=0`；发布失败（含默认 Disabled 的 NOT_CONNECTED）仅记录日志，不阻断标记已复习。此改动尚未提交，**归属确认前由张梓鸿持有**。剩余问题 2/3/4 仍需吴敏希决定。

原待共同决定项：

当前 `POST /api/user-profile/{userId}/mark-reviewed` 只更新 `review_reminders.is_reviewed`，没有产生新契约中的 `mark_reviewed` 事件。

需要共同决定：

1. 事件由前端额外调用 `record-event`，还是由 `ReviewReminderService` 在事务成功后发布；建议由后端发布，避免前端双请求不一致。
2. 新事件需要 `kpId`，但 `review_reminders` 当前只有 `courseId`，没有稳定的知识点 ID；需要补充 `kpId` 或建立正式映射。
3. `mark_reviewed` 需要 `result/timeSpentSec/hintCount`，而当前 `MarkReviewedRequest` 只有 `reminderId/notes`；需要确认是否扩展 DTO 和页面交互。
4. 当前 `notes` 没有持久化。选择：删除该字段、正式落库，或明确仅前端临时使用。
5. 事件发布失败时是否回滚“已复习”状态，还是使用 outbox 事务后投递。

在上述问题确定前，前端只能联调“标记提醒已完成”，不能声称该操作已经更新用户画像。

### 2.3 定时任务与 Python 路由

吴敏希的 `ReviewCalculationTask` 当前调用：

- `/api/review/calculate-all`
- `/api/events/process`

新画像冻结契约使用：

- `/api/internal/ai/build-profile`

需要共同确认：

1. 复习排期计算和用户画像计算是否继续作为两条独立链路。
2. 03:00 的旧 `event_collections` 处理任务是否保留、迁移或停用。
3. 谁负责实现/注册 Python M6 router，谁负责 Java client。
4. Java→Python 的 service bearer、超时、重试和错误码约定。
5. Python 是否只负责计算，Java 是否统一负责 V5/V6 CAS 写入；建议采用这一边界，避免双写数据库。

### 2.4 Flyway 迁移冲突

当前仓库存在重复 V3～V6 版本。吴敏希的复习提醒/事件采集迁移和 M6 新画像迁移发生版本冲突。

需要一起完成：

1. 收集开发、测试环境的 `flyway_schema_history`；
2. 确认哪些迁移已经真实执行；
3. 从最新主分支设计唯一、只向前的新版本号；
4. 不修改已执行 migration 的内容，不手工改历史表；
5. 增加空库迁移和既有库升级测试。

在 Flyway 问题解决前，手工建表或关闭 Flyway只能用于临时演示，不能作为联调部署方案。

## 3. 建议双方各自负责的任务

### 你负责

- 冻结并维护 `user-profile-openapi.yaml` 和 `profile-engine-contract.yaml`；
- 提供前端 M6 联调文档、Apifox 集合和错误码说明；
- 保证 `user_events` 的校验、幂等、self-only 和查询契约；
- 实现或整理 Java 画像引擎 client、响应校验、CAS 投影边界；
- 给吴敏希提供 `mark_reviewed` 事件的固定 schema 和测试样例；
- 明确新接口不再调用 `event_collections`。

### 吴敏希负责

- 确认复习提醒三接口的最终字段和前端展示语义；
- 决定并实现 `notes` 的保留/删除/持久化；
- 为 review reminder 增加稳定 `kpId` 或正式映射来源；
- 配合扩展 `MarkReviewedRequest`，提供复习结果、耗时和提示次数；
- 梳理 `ReviewCalculationTask` 与旧 Python 路由的去留；
- 提供复习提醒表和旧 `event_collections` 的实际迁移历史；
- 维护复习提醒/旧事件模块的回归测试。

### 共同完成

- 定义 `mark-reviewed` 更新与 M6 事件发布的事务/outbox 语义；
- 确认画像只消费 `user_events`，避免双写和重复计算；
- 解决 Flyway 版本并验证空库与升级路径；
- 跑通一次真实联调：登录 → 查询提醒 → 标记复习 → 产生 `mark_reviewed` → 画像版本更新 → 查询新画像；
- 与前端确认 `NOT_READY`、`STALE`、空列表和错误状态的 UI 行为。

## 4. 建议发给吴敏希的消息

> 吴敏希，我这边正在收尾 M6 用户画像和前端联调。我们现在有两个重叠点需要一起确认：一是你负责的 `event_collections`/定时任务与新的 `user_events` 画像链路怎么分工；二是 `mark-reviewed` 成功后怎样生成 M6 的 `mark_reviewed` 事件。  
> 目前 review reminder 只有 `courseId`，没有 `kpId`，请求也只有 `reminderId/notes`，但新事件需要 `kpId + result + timeSpentSec + hintCount`。我们需要确认是扩展提醒表和 DTO，还是做正式映射，并决定事件发布失败时走同事务还是 outbox。  
> 另外当前 Flyway V3～V6 有重复版本，请你把你这部分已经执行过的 migration 和环境历史发我，我们一起定新的只向前版本。前端新代码我会统一让他们调用 `/api/user-profile/**`，不再接旧 `/api/events/**`。你确认后我们再分别改各自负责的模块并跑一次完整联调。

## 5. 双方确认记录模板

| 决策 | 结论 | 负责人 | 截止时间 |
| --- | --- | --- | --- |
| 新画像唯一事件源是否为 `user_events` | 待确认 | 双方 |  |
| 旧 `/api/events/**` 的停用/兼容计划 | 待确认 | 吴敏希 |  |
| `mark_reviewed` 由前端还是后端发布 | 待确认 | 双方 |  |
| reminderId/courseId 到 kpId 的映射 | 待确认 | 吴敏希 |  |
| `MarkReviewedRequest` 是否扩展结果字段 | 待确认 | 吴敏希 |  |
| `notes` 是否持久化 | 待确认 | 吴敏希 |  |
| Python route 与 Java client 负责人 | 待确认 | 双方 |  |
| Java 统一 CAS 还是 Python 直接写库 | 待确认 | 双方 |  |
| Flyway 新版本和升级路径 | 待确认 | 双方 |  |
