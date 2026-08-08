# M5 备课质量检测接口文档

## 概述

备课内容生成完成后，系统**自动执行**多层级质量检测，质量报告随生成结果一并返回并持久化存储。前端无需额外触发，直接从生成接口的返回中获取质量报告；也可通过查询接口查看历史备课的质量报告。

### 检测层级

| 层级 | 名称 | 成本 | 说明 | 触发时机 |
|------|------|:----:|------|---------|
| Layer 1 | 硬性指标检测 | 零成本 | 纯 Python 检查：页数、字段非空、占位符比例等（"有没有"） | 生成后自动 |
| Layer 2 | LLM 语义评判 | 1 次 AI 调用 | AI 评判内容准确性、逻辑性等（"好不好"） | PPT 生成后自动 |

### 执行流程

```
Stage 1: 大纲生成 → Layer 1 大纲检测
   ↓
Stage 2: PPT 生成 → Layer 1 PPT检测 → 阻断性问题？→ 重试（最多3次）
   ↓
Layer 2: LLM 语义评判（大纲 + PPT，自动执行）
   ↓
Stage 3: 讲解词生成
   ↓
返回完整结果（含质量报告）+ 持久化存储
```

> LLM Judge 在 Layer 1 通过后自动执行，约增加 3-8 秒延迟。评判失败时降级（返回 null），不阻断生成流程。

---

## 1. 生成接口中的质量报告

### 1.1 Java 内部调用（generate_and_return）

生成完成后返回中自动包含完整质量报告：

```json
{
  "prep_id": 301,
  "total_pages": 8,
  "syllabus": { ... },
  "slides": [ ... ],
  "quality": {
    "layer1": {
      "score": 85.0,
      "is_blocking": false,
      "is_low_quality": false,
      "issues": [
        "第2页要点数8超过7上限(7±2原则)",
        "第3课时缺少提高作业(advanced)"
      ],
      "blocking_issues": null,
      "retries": 0
    },
    "llm_judge": {
      "score": 78,
      "needs_review": false,
      "syllabus": {
        "score": 80,
        "dimensions": {
          "goal_relevance": { "score": 18, "comment": "目标匹配九年级数学，可衡量" },
          "content_accuracy": { "score": 17, "comment": "知识点准确，重点定位合理" },
          "process_logic": { "score": 14, "comment": "第3课时缺少课堂练习环节" },
          "activity_effectiveness": { "score": 16, "comment": "师生活动可执行" },
          "homework_layering": { "score": 15, "comment": "拓展题偏少" }
        },
        "issues": [
          "第3课时教学过程缺少课堂练习环节",
          "拓展作业题目偏少，建议增加1-2道应用题"
        ],
        "suggestions": [
          "在第3课时讲授和小结之间增加10分钟课堂练习",
          "拓展作业增加1道实际问题应用题"
        ]
      },
      "slides": {
        "score": 76,
        "dimensions": {
          "content_relevance": { "score": 18, "comment": "内容紧扣勾股定理" },
          "information_density": { "score": 13, "comment": "第2页信息过载，8个要点" },
          "structure_completeness": { "score": 16, "comment": "缺少互动练习页" },
          "visual_clarity": { "score": 15, "comment": "配图建议较合理" },
          "teaching_usability": { "score": 14, "comment": "第4页从讲授直接跳总结" }
        },
        "issues": [
          "第2页信息过载，8个要点超出7±2原则",
          "缺少互动练习页，学生无课堂练习"
        ],
        "suggestions": [
          "第2页拆分为两页，每页4个要点",
          "在总结页前增加1页课堂练习"
        ]
      }
    }
  }
}
```

> 注意：Java 内部调用返回使用 snake_case，Java 侧转 camelCase 后返回给前端。

### 1.2 SSE 流式接口（generate）

SSE 流式生成时，在 PPT 生成完成后发送 `quality` 事件：

```
data: {"type":"outline","content":{...}}
data: {"type":"section","index":1,"title":"...","content":{...}}
data: {"type":"slide","pageNum":1,...}
data: {"type":"slidesDone","totalPages":8,...}
data: {"type":"quality","layer1":{...},"llmJudge":{...}}     ← 质量报告事件
data: {"type":"narration","pageNum":1,...}
data: {"type":"done","prepId":301}
```

#### quality 事件结构

```json
{
  "type": "quality",
  "layer1": {
    "score": 85.0,
    "isBlocking": false,
    "issues": ["第2页要点数8超过7上限(7±2原则)"],
    "blockingIssues": null
  },
  "llmJudge": {
    "score": 78,
    "needsReview": false,
    "syllabus": { "score": 80, "issues": [...], "suggestions": [...] },
    "slides": { "score": 76, "issues": [...], "suggestions": [...] }
  }
}
```

> LLM Judge 评判失败时 `llmJudge` 为 `null`，前端应做容错处理。

---

## 2. 质量报告查询接口

### 接口信息

```
GET /api/lesson-prep/{prepId}/quality
```

查询已保存的备课内容质量报告。质量报告在生成时已自动生成并持久化，此接口直接读取返回。

### 路径参数

| 字段 | 类型 | 必填 | 说明 |
|------|------|:----:|------|
| prepId | int | ✅ | 备课内容ID（teaching_contents 表主键） |

### 响应

```json
{
  "prepId": 301,
  "title": "勾股定理备课",
  "subject": "math",
  "grade": "grade_9",
  "quality": {
    "layer1": {
      "score": 85.0,
      "isBlocking": false,
      "isLowQuality": false,
      "issues": [
        "第2页要点数8超过7上限(7±2原则)",
        "第3课时缺少提高作业(advanced)"
      ],
      "blockingIssues": null,
      "syllabusReport": {
        "score": 90.0,
        "checkedItems": 18,
        "issues": ["第3课时缺少提高作业(advanced)"]
      },
      "slidesReport": {
        "score": 80.0,
        "checkedItems": 12,
        "issues": ["第2页要点数8超过7上限(7±2原则)"]
      }
    },
    "llmJudge": {
      "score": 78,
      "needsReview": false,
      "syllabus": {
        "score": 80,
        "dimensions": {
          "goalRelevance": { "score": 18, "comment": "目标匹配九年级数学，可衡量" },
          "contentAccuracy": { "score": 17, "comment": "知识点准确，重点定位合理" },
          "processLogic": { "score": 14, "comment": "第3课时缺少课堂练习环节" },
          "activityEffectiveness": { "score": 16, "comment": "师生活动可执行" },
          "homeworkLayering": { "score": 15, "comment": "拓展题偏少" }
        },
        "issues": [
          "第3课时教学过程缺少课堂练习环节",
          "拓展作业题目偏少，建议增加1-2道应用题"
        ],
        "suggestions": [
          "在第3课时讲授和小结之间增加10分钟课堂练习",
          "拓展作业增加1道实际问题应用题"
        ]
      },
      "slides": {
        "score": 76,
        "dimensions": {
          "contentRelevance": { "score": 18, "comment": "内容紧扣勾股定理" },
          "informationDensity": { "score": 13, "comment": "第2页信息过载，8个要点" },
          "structureCompleteness": { "score": 16, "comment": "缺少互动练习页" },
          "visualClarity": { "score": 15, "comment": "配图建议较合理" },
          "teachingUsability": { "score": 14, "comment": "第4页从讲授直接跳总结" }
        },
        "issues": [
          "第2页信息过载，8个要点超出7±2原则",
          "缺少互动练习页，学生无课堂练习"
        ],
        "suggestions": [
          "第2页拆分为两页，每页4个要点",
          "在总结页前增加1页课堂练习"
        ]
      }
    }
  }
}
```

### 错误响应

```json
{
  "detail": "备课记录 301 不存在"
}
```

| HTTP 状态码 | 说明 |
|:-----------:|------|
| 404 | 备课记录不存在 |
| 400 | 备课记录无质量报告（尚未生成或生成失败） |
| 500 | 查询异常 |

### 使用场景

1. **生成后展示**：前端从生成接口返回/SSE 事件中直接获取质量报告，无需额外请求
2. **历史备课查看**：用户打开历史备课时，调用此接口获取已保存的质量报告
3. **列表页质量标记**：备课列表中展示质量分数徽标（前端可只取 `quality.llmJudge.score`）

---

## 3. 质量报告字段说明

### 顶层字段

| 字段 | 类型 | 说明 |
|------|------|------|
| prepId | int | 备课内容ID |
| title | string | 备课标题 |
| subject | string | 科目 |
| grade | string | 年级 |
| quality | object | 质量报告 |
| quality.layer1 | object | Layer 1 硬性指标检测报告 |
| quality.llmJudge | object\|null | Layer 2 LLM 语义评判报告（评判失败时为 null） |

### Layer 1 硬性指标

| 字段 | 类型 | 说明 |
|------|------|------|
| score | float | 总分 0-100（syllabus + slides 平均） |
| isBlocking | boolean | 是否有阻断性问题（必须处理后才能用） |
| isLowQuality | boolean | 是否低质量（score < 60） |
| issues | string[] | 所有警告性问题列表 |
| blockingIssues | string[]\|null | 阻断性问题列表（无则为 null） |
| syllabusReport | object | 教学大纲检测结果（score, checkedItems, issues） |
| slidesReport | object | PPT 检测结果（score, checkedItems, issues） |

#### Layer 1 检测规则

**教学大纲**（`syllabusReport`）：

| 检查项 | 阈值 | 阻断？ |
|--------|------|:------:|
| sections 非空 | ≥1 课时 | ✅ |
| 每课时教学目标 | ≥2 条 | 警告 |
| 每课时教学环节 | ≥4 个 | 警告 |
| 活动/意图描述字数 | ≥10 字 | 警告 |
| 核心内容字数 | ≥10 字 | 警告 |
| 基础作业 | 非空 | 警告 |
| 重点/难点 | 非空 | 警告 |
| 占位符黑名单 | "无"/"待补充"/"TODO" | 警告 |

**PPT**（`slidesReport`）：

| 检查项 | 阈值 | 阻断？ |
|--------|------|:------:|
| 页数 | ≥4 页 | ✅ |
| slides 为空 | — | ✅ |
| 占位页占比 | >50% | ✅ |
| 封面页 | 恰好 1 个 | 警告 |
| 每页要点数 | ≤7 条 | 警告 |
| 每条要点字数 | ≥5 字 | 警告 |

### LLM 语义评判（`llmJudge`）

| 字段 | 类型 | 说明 |
|------|------|------|
| score | int | 总分 0-100（大纲 + PPT 平均） |
| needsReview | boolean | 是否需要人工复查（score < 60 或 AI 判定） |
| syllabus | object | 教学大纲语义评判 |
| slides | object | PPT 语义评判 |

**教学大纲评判维度**（每项 0-20 分，满分 100）：

| 维度 | 字段名 | 评判要点 |
|------|--------|---------|
| 目标适切性 | goalRelevance | 目标匹配年级/科目、可衡量 |
| 内容准确性 | contentAccuracy | 知识点正确、重点难点合理 |
| 过程逻辑性 | processLogic | 环节顺序符合认知规律 |
| 活动有效性 | activityEffectiveness | 师生活动可执行、有互动 |
| 作业分层性 | homeworkLayering | 基础/提高/拓展层次分明 |

**PPT 评判维度**（每项 0-20 分，满分 100）：

| 维度 | 字段名 | 评判要点 |
|------|--------|---------|
| 内容相关性 | contentRelevance | 紧扣知识点和教学目标 |
| 信息适度性 | informationDensity | 要点精炼、符合 7±2 |
| 结构完整性 | structureCompleteness | 覆盖完整教学流程 |
| 视觉提示性 | visualClarity | 配图/公式/高亮合理 |
| 教学可用性 | teachingUsability | 可直接用于课堂讲授 |

每个维度返回 `{ score: int, comment: string }`。

---

## 4. 前端展示建议

### 质量分数展示

| 分数区间 | 展示 | 颜色 | 说明 |
|:--------:|------|:----:|------|
| ≥80 | 优秀 | 🟢 绿色 | 可直接使用 |
| 60-79 | 合格 | 🟡 黄色 | 可用，附改进建议 |
| <60 | 待优化 | 🔴 红色 | 建议重新生成或手动修改 |

### 展示示例

```
质量报告                              总分: 78 分 🟡
├── 硬性指标 (85 分)
│   ├── ⚠️ 第2页要点数8超过7上限(7±2原则)
│   └── ⚠️ 第3课时缺少提高作业(advanced)
│
└── AI 语义评判 (78 分)
    ├── 教学大纲 (80 分)
    │   ├── 目标适切性  18/20  目标匹配九年级数学
    │   ├── 内容准确性  17/20  知识点准确
    │   ├── 过程逻辑性  14/20  ⚠️ 第3课时缺少课堂练习
    │   ├── 活动有效性  16/20  师生活动可执行
    │   └── 作业分层性  15/20  拓展题偏少
    │
    └── PPT (76 分)
        ├── 内容相关性  18/20  紧扣勾股定理
        ├── 信息适度性  13/20  ⚠️ 第2页信息过载
        ├── 结构完整性  16/20  缺少互动练习页
        ├── 视觉提示性  15/20  配图建议合理
        └── 教学可用性  14/20  ⚠️ 第4页讲授直接跳总结

💡 改进建议
  • 第2页拆分为两页，每页4个要点
  • 在总结页前增加1页课堂练习
  • 拓展作业增加1道实际问题应用题
```

### 容错处理

- `llmJudge` 为 `null` 时，只展示 Layer 1 结果，隐藏 AI 评判区域
- `issues` / `suggestions` 为空数组时，显示"未检测到问题"

---

## 5. 与现有接口的关系

```
现有接口:
  POST /api/lesson-prep/generate          → 三阶段完整备课（SSE流式，含quality事件）
  POST /api/lesson-prep/generate-ppt      → 单独生成PPT
  POST /api/lesson-prep/generate-goals    → 单独生成教学目标
  POST /api/lesson-prep/generate-process  → 单独生成教学过程

新增接口:
  GET /api/lesson-prep/{prepId}/quality   → 查询已保存的质量报告

设计原则:
  - 质量检测在生成时自动执行（Layer 1 + LLM Judge），无需前端触发
  - 生成接口返回中已包含质量报告，多数场景无需额外请求
  - 查询接口用于历史备课内容的质量报告查看
  - LLM Judge 失败时降级为 null，不阻断生成流程
```

---

## 6. 技术实现要点

| 项目 | Layer 1 | LLM Judge |
|------|---------|-----------|
| 实现状态 | ✅ 已实现 | ⏳ 待实现 |
| 触发方式 | 生成后自动 | PPT生成后自动（Layer 1通过后） |
| 耗时 | <10ms | 3-8秒 |
| 成本 | 零 | 1次AI调用 |
| 温度 | — | 0.1（评判需稳定） |
| 输出格式 | 纯Python计算 | JSON（强制json_mode） |
| 失败处理 | 不可能失败 | 降级为null，不阻断生成 |
| 持久化 | 随备课内容存储 | 随备课内容存储 |
