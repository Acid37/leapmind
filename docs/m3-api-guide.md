# M3 薄弱点分析模块 — 接口对接文档

> 版本：v1.1 | 最后更新：2026-08-07 | 联系人：M3 组

---

## 一、服务信息

| 项目 | 说明 |
|------|------|
| 接口文档（Knife4j） | `http://<host>:8080/doc.html` → 选择「**5-薄弱点分析**」分组 |
| OpenAPI JSON（按分组） | `http://<host>:8080/v3/api-docs/5-薄弱点分析` |
| 认证方式 | JWT Token，Header 携带 `Authorization: Bearer <token>` |
| Content-Type | `application/json` |

---

## 二、统一响应格式

所有接口返回统一包裹的 `ApiResponse<T>`，分页场景 data 内使用 `PageResult<T>`。

### 2.1 成功响应

```json
{
  "code": 200,
  "message": "查询成功",
  "data": { ... },
  "timestamp": 1723012345678
}
```

### 2.2 分页响应

```json
{
  "code": 200,
  "message": "查询成功",
  "data": {
    "total": 50,
    "pages": 3,
    "current": 1,
    "size": 20,
    "records": [ ... ]
  },
  "timestamp": 1723012345678
}
```

### 2.3 错误响应

```json
{
  "code": 400,
  "message": "错误原因描述",
  "data": null,
  "timestamp": 1723012345678
}
```

---

## 三、接口清单

---

### 3.1 薄弱点列表（分页）

```
GET /api/weak-points
```

**调用方**：M1（首页薄弱点概览）、M4（课程页薄弱点入口）、M5（测评结果页）

**请求参数**：

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `userId` | Long | 是 | — | 用户ID |
| `subject` | String | 否 | — | 学科过滤，如"数学""英语" |
| `status` | String | 否 | — | ACTIVE（活跃）/ RESOLVED（已解决）/ IMPROVING（改善中） |
| `page` | Integer | 否 | 1 | 页码 |
| `size` | Integer | 否 | 20 | 每页条数 |

**调用示例**：

```
GET /api/weak-points?userId=123&subject=数学&status=ACTIVE&page=1&size=10
```

**响应 data.records 字段**：

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | Long | 薄弱点记录ID |
| `userId` | Long | 用户ID |
| `knowledgePoint` | String | 知识点名称 |
| `subject` | String | 学科 |
| `weaknessLevel` | String | HIGH / MEDIUM / LOW |
| `weaknessScore` | BigDecimal | 薄弱度分数 0~1（Python 引擎计算，优先参考此值） |
| `errorCount` | Integer | 累计错误次数 |
| `totalCount` | Integer | 累计答题次数 |
| `accuracyRate` | BigDecimal | 正确率（%） |
| `errorRate` | BigDecimal | 历史错误率 0~1（Python 引擎计算） |
| `recentCorrectRate` | BigDecimal | 最近10次正确率（Python 引擎计算） |
| `confusionCount` | Integer | 提问困惑次数（Python 引擎多源融合） |
| `trend` | String | 趋势：improving / stable / declining（Python 引擎计算） |
| `status` | String | ACTIVE / RESOLVED / IMPROVING |
| `lastErrorTime` | String | 最近一次错误时间 |
| `createdAt` | String | 创建时间 |

---

### 3.2 薄弱点详情

```
GET /api/weak-points/{id}/detail
```

**调用方**：M4（课程详情页）、M5（测评报告详情）

**路径参数**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `id` | Long | 是 | 薄弱点记录ID（从列表接口获取） |

**调用示例**：

```
GET /api/weak-points/42/detail
```

**响应结构**：

```json
{
  "code": 200,
  "message": "查询成功",
  "data": {
    "id": 42,
    "userId": 123,
    "knowledgePoint": "二次函数",
    "subject": "数学",
    "weaknessLevel": "HIGH",
    "weaknessScore": 0.72,
    "errorCount": 8,
    "totalCount": 12,
    "accuracyRate": 33.33,
    "errorRate": 0.667,
    "recentCorrectRate": 0.25,
    "confusionCount": 3,
    "status": "ACTIVE",
    "lastErrorTime": "2026-08-06T15:30:00",
    "aiAnalysis": "该学生在二次函数对称轴和顶点坐标方面存在明显不足，经常混淆配方法和公式法...",
    "aiSuggestion": "建议从配方法求顶点坐标的基础练习开始，逐步过渡到综合应用...",
    "analyzedAt": "2026-08-06T16:00:00",
    "recentErrors": [
      {
        "id": 1001,
        "exerciseId": "EX_20240806_005",
        "isCorrect": 0,
        "completedAt": "2026-08-06T15:30:00"
      }
    ],
    "recentErrorRate": 66.67,
    "previousErrorRate": 25.00,
    "trend": "declining",
    "calculatedAt": "2026-08-07T02:00:00",
    "createdAt": "2026-07-20T10:00:00"
  }
}
```

**关键字段说明**：

| 字段 | 说明 |
|------|------|
| `recentErrors` | 最近 20 条该知识点的练习记录 |
| `recentErrorRate` | 近 7 天错误率（%） |
| `previousErrorRate` | 前 7 天错误率（%） |
| `trend` | improving（改善中）/ stable（稳定）/ declining（恶化中） |

> 趋势判定：近 7 天错误率比前 7 天变化超过 5 个百分点才判定为 improving 或 declining

---

### 3.3 AI 综合分析

```
POST /api/weak-points/{userId}/analysis
```

**调用方**：M1（首页智能分析卡片）、M4（课程前诊断）

**路径参数**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `userId` | Long | 是 | 用户ID |

**调用示例**：

```
POST /api/weak-points/123/analysis
```

> ⚠️ **缓存机制**：同用户 24 小时内重复调用返回缓存结果，不消耗额外 AI 资源。
> 缓存命中时 `comprehensiveAnalysis` 内容会带有"（24h缓存）"标记。

**响应结构**：

```json
{
  "code": 200,
  "message": "分析完成",
  "data": {
    "comprehensiveAnalysis": "## 综合分析\n\n该学生在数学学科存在3个薄弱点：\n- **二次函数**：错误率较高，主要问题集中在...\n- **三角函数**：...",
    "learningSuggestions": "建议优先攻克二次函数，掌握后再进行三角函数的复习。每天保持30分钟的针对性练习...",
    "detailAnalyses": [
      {
        "knowledgePoint": "二次函数",
        "analysis": "学生对配方法和顶点式互化掌握不熟练，导致对称轴判断经常出错...",
        "suggestion": "建议从以下方面入手：1. 回顾配方法基本步骤 2. 通过图像直观理解顶点坐标..."
      },
      {
        "knowledgePoint": "三角函数",
        "analysis": "学生对诱导公式的记忆不够牢固，特别是在符号判断上容易出错...",
        "suggestion": "建议使用"奇变偶不变，符号看象限"的口诀辅助记忆..."
      }
    ],
    "recommendedPriority": ["二次函数", "三角函数", "立体几何"]
  }
}
```

---

### 3.4 推荐练习

```
GET /api/exercises/recommend
```

**调用方**：M4（课程页推荐练习）、M5（测评后巩固练习）

**请求参数**：

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `userId` | Long | 是 | — | 用户ID |
| `subject` | String | 否 | — | 学科过滤 |
| `knowledgePoint` | String | 否 | — | 指定知识点 |
| `count` | Integer | 否 | 5 | 推荐数量 |

**调用示例**：

```
GET /api/exercises/recommend?userId=123&subject=数学&count=5
```

**推荐优先级**（自动）：

1. **优先**：已解决错题的知识点（复习巩固）
2. **其次**：活跃薄弱点，按严重程度排序（HIGH → MEDIUM → LOW）
3. **去重**：自动排除 7 天内已做过的练习

**响应结构**：

```json
{
  "code": 200,
  "message": "推荐成功",
  "data": [
    {
      "exerciseId": "RESOLVED_123_勾股定理",
      "knowledgePoint": "勾股定理",
      "subject": "数学",
      "sourceType": "RESOLVED_WEAK_POINT",
      "priority": 1
    },
    {
      "exerciseId": "ACTIVE_123_二次函数",
      "knowledgePoint": "二次函数",
      "subject": "数学",
      "sourceType": "ACTIVE_WEAK_POINT",
      "priority": 2
    }
  ]
}
```

| 字段 | 说明 |
|------|------|
| `sourceType` | RESOLVED_WEAK_POINT（复习巩固题）/ ACTIVE_WEAK_POINT（薄弱点攻克题） |
| `priority` | 1（最高优先级，先做）/ 2（次优先级） |

---

### 3.5 推荐具体题目

```
GET /api/weak-points/recommend-questions
```

**调用方**：M4（薄弱点详情页 → 开始练习按钮）

**请求参数**：

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `userId` | Long | 是 | — | 用户ID |
| `knowledgePoint` | String | 是 | — | 知识点名称 |
| `count` | Integer | 否 | 5 | 推荐数量 |

**调用示例**：

```
GET /api/weak-points/recommend-questions?userId=123&knowledgePoint=二次函数&count=5
```

**难度自动匹配规则**：

| 薄弱程度 | 推荐难度 | 说明 |
|----------|----------|------|
| HIGH | EASY | 错误率高，从基础题开始巩固 |
| MEDIUM | MEDIUM | 掌握一般，进行中等难度练习 |
| LOW | HARD | 掌握较好，挑战提高题 |

**响应结构**：

```json
{
  "code": 200,
  "message": "推荐成功",
  "data": [
    {
      "questionId": "Q_123_二次函数_1",
      "knowledgePoint": "二次函数",
      "subject": "数学",
      "difficulty": "EASY",
      "questionType": "选择题",
      "questionTitle": "关于「二次函数」的基础练习题",
      "reason": "该知识点错误率较高，建议从基础题开始巩固"
    }
  ]
}
```

---

### 3.6 记录练习结果

```
POST /api/exercises/record
```

**调用方**：M5（学生每答完一道题后回调）

> 📌 **核心接口**：M3 收到练习结果后实时更新薄弱点的正确率、错误次数和状态，不需要等待定时任务。

**请求体**：

```json
{
  "userId": 123,
  "exerciseId": "EX_20240807_001",
  "knowledgePoint": "二次函数",
  "subject": "数学",
  "isCorrect": 0
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `userId` | Long | 是 | 用户ID |
| `exerciseId` | String | 是 | 练习记录唯一标识 |
| `knowledgePoint` | String | 是 | 知识点名称 |
| `subject` | String | 是 | 学科 |
| `isCorrect` | Integer | 是 | 1=正确，0=错误 |

**M3 内部处理逻辑**：

- `isCorrect=1`：正确率上升；若正确率 ≥ 80% → 状态自动标记为 RESOLVED
- `isCorrect=0`：错误次数+1，状态标记为 ACTIVE，记录 `lastErrorTime`
- 同时上报 `weak_point_changed` 事件给 M6 画像引擎

**响应**：

```json
{
  "code": 200,
  "message": "记录成功",
  "data": "ok"
}
```

---

### 3.7 生成练习计划

```
POST /api/weak-points/generate-practice-plan
```

**调用方**：M4（练习计划页面）

**请求体**：

```json
{
  "userId": 123,
  "knowledgePoints": ["二次函数", "三角函数", "立体几何"]
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `userId` | Long | 是 | 用户ID |
| `knowledgePoints` | List\<String\> | 是 | 目标知识点列表 |

**响应结构**：

```json
{
  "code": 200,
  "message": "计划生成成功",
  "data": {
    "userId": 123,
    "targetKnowledgePoints": ["二次函数", "三角函数", "立体几何"],
    "totalQuestions": 15,
    "estimatedMinutes": 19,
    "questions": [
      {
        "questionId": "Q_123_二次函数_1",
        "knowledgePoint": "二次函数",
        "subject": "数学",
        "difficulty": "EASY",
        "questionType": "选择题",
        "questionTitle": "关于「二次函数」的基础练习题",
        "order": 1,
        "reason": "该知识点错误率较高，建议从基础题开始巩固"
      }
    ]
  }
}
```

| 字段 | 说明 |
|------|------|
| `totalQuestions` | 总题数（每个知识点 5 题 × 知识点数） |
| `estimatedMinutes` | 预计完成时间（每题约 75 秒，含答题 45s + 订正 30s） |
| `questions[].order` | 题目序号，按 EASY → MEDIUM → HARD 排序 |

---

### 3.8 知识图谱

```
GET /api/weak-points/knowledge-graph
```

**调用方**：M1（首页知识图谱可视化）、M4（课程知识地图）

**请求参数**：

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `userId` | Long | 是 | — | 用户ID |
| `subject` | String | 否 | — | 学科过滤，为空返回所有学科 |

**调用示例**：

```
GET /api/weak-points/knowledge-graph?userId=123&subject=数学
```

**响应结构**：

```json
{
  "code": 200,
  "message": "查询成功",
  "data": {
    "nodes": [
      {
        "id": "二次函数",
        "name": "二次函数",
        "subject": "数学",
        "weaknessLevel": "HIGH",
        "masteryRate": 33.33,
        "group": "数学"
      },
      {
        "id": "勾股定理",
        "name": "勾股定理",
        "subject": "数学",
        "weaknessLevel": "MASTERED",
        "masteryRate": 90.0,
        "group": "数学"
      },
      {
        "id": "概率初步",
        "name": "概率初步",
        "subject": "数学",
        "weaknessLevel": "UNKNOWN",
        "masteryRate": 0.0,
        "group": "数学"
      }
    ],
    "edges": [
      {
        "source": "二次函数",
        "target": "三角函数",
        "relation": "prerequisite"
      }
    ]
  }
}
```

**节点字段说明**：

| 字段 | 说明 |
|------|------|
| `id` / `name` | 知识点名称 |
| `weaknessLevel` | HIGH / MEDIUM / LOW / MASTERED（已掌握）/ UNKNOWN（未练习过） |
| `masteryRate` | 掌握率（%），0-100 |
| `group` | 学科分组，前端可用于着色 |

**边说明**：

| 字段 | 说明 |
|------|------|
| `source` | 前置知识点 |
| `target` | 后置知识点 |
| `relation` | 固定值 `"prerequisite"`（前置依赖） |

> 知识图谱数据可直接对接 ECharts / D3.js / Cytoscape.js 等前端可视化库。

---

### 3.9 M6 事件上报（薄弱点变化通知）

M3 在薄弱点数据发生变化时，自动向 M6 事件采集服务发送 `weak_point_changed` 事件。

**接收方**：M6 画像引擎

**服务地址**：`http://192.168.1.19:8080`

**调用接口**：`POST /api/events/collect`

**触发时机**：

| 场景 | reason | 说明 |
|------|--------|------|
| 用户答错题目 | `REPEATED_ERROR` | 同知识点反复出错 |
| 用户答对题目（正确率变化） | `ACCURACY_DROP` | 正确率下降 |
| 每日定时重算（02:30） | `RECALCULATED` | 薄弱度引擎重新计算 |

**请求格式**：

```json
POST /api/events/collect
Content-Type: application/json

{
  "module": "M3",
  "eventType": "weak_point_changed",
  "userId": 123,
  "eventData": "{\"oldScore\":0.50,\"newScore\":0.72,\"reason\":\"REPEATED_ERROR\",\"kpId\":\"二次函数\",\"sourceModule\":\"M3\"}",
  "eventTime": "2026-08-07T14:00:00+08:00"
}
```

**eventData 字段**（JSON 字符串）：

| 字段 | 类型 | 说明 |
|------|------|------|
| `oldScore` | float | 变化前薄弱度分数 0~1 |
| `newScore` | float | 变化后薄弱度分数 0~1 |
| `reason` | String | ACCURACY_DROP / REPEATED_ERROR / RECALCULATED |
| `kpId` | String | 知识点名称 |
| `sourceModule` | String | 固定值 `"M3"` |

**分数来源优先级**：

1. **Python 引擎** `weakness_score`（三维加权：错误率 + 近期正确率 + 困惑频率）— 权威值
2. **Java 简易公式** `1 - accuracy/100` — Python 尚未计算时的回退值
3. **薄弱等级估算** HIGH→0.80 / MEDIUM→0.50 / LOW→0.30 — 最终兜底

> 📌 **关键规则**：M6 画像引擎在 `newScore >= 0.6` 时触发 WEAK 标记，`newScore < 0.6` 时解除。M3 是唯一能触发 WEAK 状态的模块。

---

## 四、典型调用场景

### 场景 A：M1 首页加载

```
① GET /api/weak-points?userId=123&status=ACTIVE&page=1&size=5
   → 首页薄弱点概览卡片

② GET /api/weak-points/knowledge-graph?userId=123
   → 知识图谱可视化展示

③ POST /api/weak-points/123/analysis
   → 智能分析卡片（24h 内有缓存，基本不耗时）
```

### 场景 B：M4 课程学习流程

```
① POST /api/weak-points/123/analysis
   → 课前诊断，了解薄弱点

② GET /api/exercises/recommend?userId=123&subject=数学&count=10
   → 获取推荐练习

③ GET /api/weak-points/recommend-questions?userId=123&knowledgePoint=二次函数&count=5
   → 点击具体知识点，进入练习

④ 用户做练习 → M4 调 M5 判题 → M5 调 POST /api/exercises/record
   → 每道题实时反馈薄弱点变化

⑤ GET /api/weak-points/{id}/detail
   → 查看某个薄弱点的详细变化
```

### 场景 C：M5 测评完成

```
① 每道题判分后 → POST /api/exercises/record
   → M3 实时更新薄弱点数据

② GET /api/weak-points?userId=123&status=ACTIVE
   → 获取最新薄弱点列表，展示在测评报告中

③ GET /api/exercises/recommend?userId=123&subject=数学
   → 测评完成后推荐针对性练习
```

### 场景 D：练习计划

```
① 用户在 M4 选择多个薄弱点 → POST /api/weak-points/generate-practice-plan
   → 生成结构化练习计划

② 按返回的 plan.questions[].order 顺序引导用户逐个练习

③ 每题完成后调 POST /api/exercises/record
```

---

## 五、枚举值速查

| 枚举 | 取值 | 说明 |
|------|------|------|
| **薄弱点状态** | `ACTIVE` | 当前活跃薄弱点 |
| | `RESOLVED` | 已解决（正确率 ≥ 80%） |
| | `IMPROVING` | 改善中 |
| **薄弱程度** | `HIGH` | 高薄弱度（错误率 > 50%） |
| | `MEDIUM` | 中薄弱度（错误率 20%-50%） |
| | `LOW` | 低薄弱度（错误率 < 20%） |
| **趋势** | `improving` | 错误率在下降 |
| | `stable` | 变化不超过 5 个百分点 |
| | `declining` | 错误率在上升 |
| **题目难度** | `EASY` | 基础题 |
| | `MEDIUM` | 中等题 |
| | `HARD` | 提高题 |
| **题目类型** | `选择题` / `填空题` / `解答题` | — |
| **推荐来源** | `RESOLVED_WEAK_POINT` | 复习巩固（已解决错题的知识点） |
| | `ACTIVE_WEAK_POINT` | 薄弱点攻克（当前活跃薄弱点） |

---

## 六、注意事项

1. **知识点命名对齐**：`knowledgePoint` 字段使用中文名称（如"二次函数"而不是"quadratic_function"），各模块务必使用统一的知识点命名体系，否则薄弱点无法关联

2. **userId 必传**：所有接口均依赖 `userId`，M3 不做用户身份推断，缺失会返回错误

3. **AI 分析有 24h 缓存**：`POST /api/weak-points/{userId}/analysis` 同用户 24 小时内重复调用不消耗 AI 资源，前端可放心在页面加载时调用

4. **练习记录实时生效**：`POST /api/exercises/record` 立即更新薄弱点，无需等待定时任务

5. **自动去重**：推荐练习接口自动排除 7 天内已做过的练习，M4/M5 无需自己做去重逻辑

6. **趋势判定阈值 5%**：近 7 天与前 7 天错误率差值超过 5 个百分点才判定趋势变化

7. **接口文档实时为准**：本文档为对接指南，完整参数说明、在线调试请访问 Knife4j 文档页 `http://<host>:8080/doc.html`

---

## 附录：接口速查表

| 方法 | 路径 | 用途 | 主要调用方 |
|------|------|------|------------|
| GET | `/api/weak-points` | 薄弱点列表（分页） | M1, M4, M5 |
| GET | `/api/weak-points/{id}/detail` | 薄弱点详情+趋势 | M4, M5 |
| POST | `/api/weak-points/{userId}/analysis` | AI 综合分析 | M1, M4 |
| GET | `/api/exercises/recommend` | 推荐练习（去重） | M4, M5 |
| GET | `/api/weak-points/recommend-questions` | 推荐题目（按难度） | M4 |
| POST | `/api/exercises/record` | 记录练习结果 | M5 |
| POST | `/api/weak-points/generate-practice-plan` | 生成练习计划 | M4 |
| GET | `/api/weak-points/knowledge-graph` | 知识图谱 | M1, M4 |
| POST | `/api/events/collect` | 薄弱点变化事件（→ M6） | M6 画像引擎 |
