# M6 Python模块测试报告

## 1. 测试环境

| 项 | 说明 |
| --- | --- |
| 操作系统 | Windows，PowerShell 5.1 |
| Python | 3.13.3（系统解释器，`C:\Users\Lenovo\AppData\Local\Programs\Python\Python313\python.exe`） |
| 测试框架 | pytest 9.1.1 |
| 辅助库 | httpx 0.28.1（FastAPI TestClient 依赖）、fastapi 0.140.0、starlette 1.3.1、pydantic 2.13.4、PyMySQL 1.2.0 |
| 代码版本 | 本地分支 `feat/m6-chenfuming`，与远程 `origin/backend-M6` 完全同步（HEAD = `3881fc4`，差异 0/0） |
| 数据库 | 未使用真实 MySQL；全部通过 mock 隔离 |
| 运行命令 | `PYTHONPATH=aitutor-backend-python\src python -m pytest aitutor-backend-python\tests/` |

> 环境注记：系统 Python 初始未安装 pytest，已通过 `pip install pytest` 补齐；项目无 `.venv`，`uv` 不可用，依赖安装到基础解释器。

## 2. M6模块范围

M6 为"复习排期 + 用户画像"模块，位于 `aitutor-backend-python/src/landppt/m6/`：

| 文件 | 职责 |
| --- | --- |
| `router.py` | FastAPI 路由（4 个端点：全量计算、增量事件处理、在线画像、知识状态/时间线查询） |
| `profile_engine.py` | 用户画像引擎：增量更新、学习风格/节奏推断、摘要生成、掌握度同步、在线画像计算 |
| `review_scheduler.py` | 遗忘曲线复习排期（间隔 1/3/7/30 天）、薄弱分计算、提醒同步 |
| `config.py` | MySQL 连接配置、复习间隔、学习风格推断阈值 |
| `db.py` | 数据库连接与建表 |
| `schemas.py` | Pydantic 请求/响应模型 |

**测试范围约束**：只修改 `tests/`；业务代码、数据库结构、Java 代码、前端代码均不改动（唯一例外见 Bug 记录）。

## 3. 测试文件列表

| 文件 | 覆盖内容 | 用例数 |
| --- | --- | --- |
| `tests/conftest.py` | 共享 fixtures：`make_conn`（mock 连接/游标）、`sql_asserts`（SQL 断言辅助） | — |
| `tests/test_profile_engine.py` | `build_summary`、`compute_profile_from_events`、`infer_learning_style`、`_infer_learning_pace`、`_write_user_profiles_v5` | 21 |
| `tests/test_review_scheduler.py` | 间隔计算、弱点分、优先级、`on_learned`、`advance_stage`、`build_all_schedules`、`_sync_reminder` | 22 |
| `tests/test_event_processor.py` | `incremental_update`、`_sync_to_user_knowledge_mastery`、`process_new_events`、`build_all_profiles` | 19 |
| `tests/user_profile/`（原有） | 既有 user_profile 模块测试 | 38 |

**M6 新增测试小计：62**；**总计：100**。

## 4. 测试数量

| 分组 | 数量 |
| --- | --- |
| M6 新增（test_profile_engine / test_review_scheduler / test_event_processor） | 62 |
| 原有 user_profile 测试 | 38 |
| **合计** | **100** |

## 5. 测试结果

**最终状态：100 passed，0 failed。**

首轮运行曾出现 14 个失败，分类如下（修复详情见 Bug 记录）：

| 分类 | 数量 | 说明 |
| --- | --- | --- |
| 测试代码问题 | 6 | SQL 字面量参数个数/顺序误判、execute 次数误判 |
| 业务逻辑问题 | 7 | `compute_profile_from_events` 必抛 `KeyError('kpId')`（真实 bug） |
| 数据结构/测试数据问题 | 3 | mock 行键名 `name` 与代码读取 `kp_name` 不一致 |
| 数据库连接问题 | 0 | 全程 mock，未触真实库 |
| 环境问题 | 0 | pytest 缺失已通过 pip 解决，不计入用例失败 |

## 6. Bug记录

### Bug #1：`compute_profile_from_events` 对非空事件必抛 `KeyError('kpId')`

- **位置**：`profile_engine.py:511-516`（修复前）
- **现象**：函数内摘要构建段遍历 `kp_stats.values()` 并取 `s['kpId']`，但统计 dict 只含 `evidence_count / correct_count / first_seen / last_seen` 四个键，无 `kpId` 字段。
- **触发条件**：事件列表非空且存在 `kpId > 0` 的事件 → 100% 崩溃。
- **影响**：`POST /api/internal/ai/build-profile` 在线画像接口无法产出任何画像；`build_profile` 路由 try/except 兜底返回 `success=False`。定时任务链路（`calculate-all` / `events/process`）不走该函数，不受影响。
- **辅助发现**：`s_names` / `w_names` 为死变量——最终 `summary` 与 `profile` 均未引用它们，删除无副作用。

## 7. Bug修复方案

### 方案对比

| 方案 | 改动 | 风险评估 |
| --- | --- | --- |
| A：删除死代码 | 删除 `s_names`/`w_names` 两个赋值语句（6 行） | 最低：纯死代码消除，输出零变化 |
| B：补充 kpId 字段 | 在 `kp_stats` 值 dict 初始化处加 `"kpId": kp_id` | 低：修活死代码，但保留无用的排序遍历，且键名大小写（kpId vs kp_id）是隐藏陷阱 |

**选定方案 A（最小修改、最低风险）。**

### 已实施改动

- **`src/landppt/m6/profile_engine.py`**：删除 `compute_profile_from_events` 中 511-516 行的 `s_names`、`w_names` 死代码赋值；`summary` 与 `profile` 构造原样保留。
- **`tests/test_profile_engine.py`**：将 7 个固化崩溃现状的 `*_raises_keyerror` 用例改写为正常流程断言（学习风格/节奏/掌握度/recentFocus/摘要预期值），并移除不再使用的 `import pytest`。

### 修改前后逻辑变化

| 项 | 修改前 | 修改后 |
| --- | --- | --- |
| 执行行为 | 非空且含 `kpId>0` 事件 → 抛 `KeyError('kpId')` | 正常返回 `(profile, mastery_items)` |
| 输出 | 崩溃、无画像 | 与预期设计一致（summary/profile 本不引用删除变量） |
| 算法/库/Java/前端 | — | 均未改动 |

## 8. API验证结果

通过 FastAPI `TestClient` 走完整 HTTP 层验证 `POST /api/internal/ai/build-profile`，模拟存在学习事件的用户（user_id=7，3 条事件）。

**请求体示例**：
```json
{
  "user_id": 7,
  "events": [
    {"kpId": 5, "eventType": "answer_question", "data": {"isCorrect": true, "durationSeconds": 10}},
    {"kpId": 5, "eventType": "answer_question", "data": {"isCorrect": false, "durationSeconds": 25}},
    {"kpId": 9, "eventType": "request_explanation", "data": {}}
  ]
}
```

**响应结果**：

| 验证项 | 结果 |
| --- | --- |
| 状态码 | 200 |
| 生成画像 | `success: True`，`data` 非空（含 user_id / overall_mastery / knowledge_mastery） |
| 生成知识掌握结果 | `knowledge_mastery` 共 2 条：kp_id=5（level=0.5, review_count=2）、kp_id=9（level=0.0, review_count=1） |
| KeyError | 不再出现（`message=None`） |
| 数值校验 | `overall_mastery = (0.5 + 0.0) / 2 = 0.25`，符合算法预期 |

> 附带注记：TestClient 打印 `StarletteDeprecationWarning`（httpx → httpx2），仅为依赖库弃用提示，不影响功能。

## 9. 当前模块完成状态

| 项 | 状态 |
| --- | --- |
| M6 Python 自动化测试 | ✅ 完成，100/100 通过 |
| 在线画像接口 KeyError bug | ✅ 已修复并验证（200 + 正常画像 + 无 KeyError） |
| 业务代码改动 | 仅 1 处最小修复（删除死代码），算法逻辑未变 |
| 数据库结构 | 未修改 |
| Java / 前端代码 | 未修改 |
| git 提交 | 暂未提交（等待人工确认） |

**遗留待关注（非本次范围）**：
- 与契约 `profile-engine-contract.yaml` / Java V5/V6 表结构的历史对齐问题（先前代码分析报告中记录），不影响本模块测试通过。
- 其他 M6 函数中的已知死代码（如 `review_scheduler.advance_stage` / `on_learned` 无调用方），未在本次修改。
