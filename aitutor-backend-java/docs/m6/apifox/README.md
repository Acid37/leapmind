# LeapMind M6 Apifox 演示包

这套文件用于演示 M6“上下文记忆 / 用户画像”的接口契约、事件幂等、批量隔离、画像查询和访问控制。

## 文件

- `leapmind-m6-demo.postman_collection.json`：可导入 Apifox 的演示集合，含请求脚本、断言和 Mock 响应示例。
- `preflight.ps1`：只读检查演示集合、OpenAPI、后端端口和 MySQL 端口。
- `validate-demo.cjs`：校验集合 JSON、示例响应、脚本语法和变量引用。
- `../user-profile-openapi.yaml`：M6 的 OpenAPI 3.0.3 契约，可单独导入 Apifox 查看模型和接口定义。

## 一次性导入

1. 在 Apifox 新建项目，进入“项目设置 → 导入数据”。
2. 选择 Postman 导入 `leapmind-m6-demo.postman_collection.json`；在高级设置中明确启用“导入接口用例”，并在预览页确认请求、前置 / 后置脚本和响应示例都将导入。
3. 在 Apifox 本地环境中手动创建 `baseUrl`、`username`、`password`、`accessToken`、`userId`、`otherUserId`、`kpId`、`kpId2`；不要提交或共享该环境文件。
4. 打开“登录并提取 Token/UserId”，确认后置脚本仍包含 `pm.environment.set('accessToken', ...)` 和 `pm.environment.set('userId', ...)`。若脚本缺失，不要继续演示，重新按“接口用例”方式导入。
5. `username`、`password` 只使用专用本地演示账号；不要填生产账号或真实生产 Token。
6. 确认 `baseUrl` 为 `http://localhost:8080`，并让 `otherUserId` 与演示账号的 ID 不同。

如需展示完整数据结构，可再导入 `../user-profile-openapi.yaml`。不要把 OpenAPI 导入结果和 Postman 集合重复覆盖到同一目录；建议分别放在“接口契约”和“现场演示”两个目录。

## 真实接口模式

真实调用前需要同时满足：

- Java 后端监听 `localhost:8080`；
- 本地 MySQL 与开发配置一致，并已准备演示账号；
- M6 所需表结构已经在本地安全落库；
- 演示账号状态正常，JWT 登录成功；
- `kpId`、`kpId2` 是本地演示数据允许使用的正整数。

先执行“00 登录与环境 / 登录并提取 Token/UserId”。后置脚本会自动保存 `accessToken` 和 `userId`，其余请求继承 Bearer Token。

演示前可在本目录运行：

```powershell
powershell -ExecutionPolicy Bypass -File .\preflight.ps1
```

退出码 `0` 表示基础进程检查通过，`2` 表示应切换到离线响应示例；脚本不会登录账号或修改数据库。

修改演示集合后可运行 `node .\validate-demo.cjs` 做离线结构校验。

当前仓库中的 M6 数据库迁移仍需独立解决版本冲突，画像生成器也未形成可声明为生产闭环的 Java → Python → CAS 回写链路。不要为一次演示临时改迁移版本、改生产配置或把离线响应示例说成实时计算结果。真实接口模式下，事件采集可以展示真实 ACK；画像查询可能合法返回 `NOT_READY`，或者展示数据库中已有的投影数据。

## 离线响应示例模式

如果现场机器无法启动数据库或后端：

1. 不要修改 `baseUrl`，也不要点击“发送”。
2. 按演示顺序打开每个请求下已导入的响应示例，静态展示请求与响应。
3. `READY` 与 `NOT_READY` 被拆成两个独立请求用例，避免导入时同状态码示例被覆盖。
4. 401 / 403 也只打开离线示例，不声称 Apifox 执行了真实 JWT 或 self-only 鉴权。
5. 演示时明确说：“这是契约响应示例；真实幂等、鉴权和状态流需要本地后端模式验证。”

不要把这套 Postman 响应示例直接当作“可按调用历史切换状态”的 Apifox Mock 服务。同一个 `POST /record-event` 同时承载 `ACCEPTED`、`DUPLICATE` 和 `409`；没有额外 Mock 期望或匹配脚本时，单纯替换 Mock URL 不能稳定复现该状态流。

集合内已准备这些关键示例：

- 登录成功；
- 首次事件 `ACCEPTED`；
- 原样重试 `DUPLICATE`；
- 同 ID 改内容 `409 PROFILE_IDEMPOTENCY_CONFLICT`；
- 敏感 / 未知字段 `400 PROFILE_EVENT_INVALID`；
- 批量事件 item 级部分失败；
- 完整画像 `READY` 与 `NOT_READY`；
- 场景化摘要与知识点状态；
- 未认证 401 与越权 403。

## 建议的 7 分钟演示顺序

1. 打开 OpenAPI 契约，说明 M6 统一接收学习事件并向其他模块提供最小化上下文。
2. 执行登录请求，展示脚本自动写入 `accessToken` 与 `userId`。
3. 依次执行“首次写入”“原样重试”“同 ID 改内容”，突出 `ACCEPTED → DUPLICATE → 409`。
4. 执行“敏感字段拒绝”，说明事件 payload 采用严格白名单，不允许把认证信息混入学习数据。
5. 执行“批量事件 - 部分失败”，说明单条坏数据不会抹掉同批已接受的合法事件。
6. 执行完整画像、讲解摘要、知识点状态，说明下游模块按场景读取，不需要直接访问内部表。
7. 执行 401 与 403，用一句话收尾：“未登录不能读，登录后也只能读自己的画像。”

## 彩排检查

- 首次写入请求会生成新的 `eventId`；第二、三步必须紧接着执行，否则幂等演示上下文会丢失。
- 不要手动修改第二步的 body；第三步只故意改变 `isCorrect`。
- `otherUserId` 必须与登录后自动写入的 `userId` 不同。
- 真机演示前至少完整跑一遍真实模式，并保留已导入的离线响应示例作为备份。
- 屏幕录制或截图前隐藏环境变量面板中的密码和 Token。
- 若画像返回 `NOT_READY`，直接解释为“事件已接收，但尚无可发布的画像投影”，不要伪装成错误。
