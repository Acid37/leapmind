# M5 备课辅助生成接口文档

## 概述

为前端编辑器提供独立的 AI 辅助生成接口，支持在创建/编辑备课内容时单独生成教学目标或教学过程。

---

## 1. 生成教学目标

### 接口信息

```
POST /api/lesson-prep/generate-goals
```

### 请求参数

```json
{
  "userId": 1001,
  "knowledgePointIds": [20, 21],
  "knowledgePointNames": ["勾股定理", "勾股定理的逆定理"],
  "subject": "math",
  "grade": "grade_9",
  "goalDirection": "理解概念并能灵活运用",
  "weakPointIds": []
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| userId | int | ✅ | 用户ID |
| knowledgePointIds | int[] | ✅ | 知识点ID列表 |
| knowledgePointNames | string[] | ❌ | 知识点名称列表（与ID一一对应） |
| subject | string | ✅ | 科目：math/chinese/english/physics |
| grade | string | ✅ | 年级：grade_7 ~ grade_12 |
| goalDirection | string | ❌ | 教学方向提示（如"理解概念"、"掌握方法"） |
| weakPointIds | int[] | ❌ | 薄弱知识点ID（需重点关注） |

### 响应

```json
{
  "goals": [
    "理解勾股定理的几何意义和代数形式",
    "能运用勾股定理计算直角三角形的边长",
    "能利用勾股定理的逆定理判断直角三角形",
    "在实际问题中灵活运用勾股定理解决应用问题"
  ]
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| goals | string[] | 3-5 条教学目标列表 |

### 错误响应

```json
{
  "detail": "知识点不能为空"
}
```

### 使用场景

用户在「创建备课页」输入知识点后，点击「AI 智能生成」按钮，获取建议的教学目标列表，用户可选择/编辑/删除后使用。

---

## 2. 生成教学过程

### 接口信息

```
POST /api/lesson-prep/generate-process
```

### 请求参数

```json
{
  "userId": 1001,
  "knowledgePointIds": [20],
  "knowledgePointNames": ["勾股定理"],
  "subject": "math",
  "grade": "grade_9",
  "teachingGoals": [
    "理解勾股定理的几何意义",
    "能运用勾股定理计算边长"
  ],
  "totalHours": 1,
  "sectionIndex": 1,
  "sectionTitle": "第一课时：勾股定理的定义与应用"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| userId | int | ✅ | 用户ID |
| knowledgePointIds | int[] | ✅ | 知识点ID列表 |
| knowledgePointNames | string[] | ❌ | 知识点名称列表 |
| subject | string | ✅ | 科目 |
| grade | string | ✅ | 年级 |
| teachingGoals | string[] | ✅ | 当前课时的教学目标（已编辑/AI生成的） |
| totalHours | int | ❌ | 课时数（默认1） |
| sectionIndex | int | ❌ | 当前课时序号（用于提示） |
| sectionTitle | string | ❌ | 当前课时标题（用于提示） |

### 响应

```json
{
  "teachingProcess": [
    {
      "step": "情境导入",
      "duration": "5min",
      "teacherActivity": "展示工人用角尺测量直角的场景，提问如何计算斜边长度",
      "studentActivity": "观察场景，思考直角三角形三边关系",
      "designIntent": "从真实情境引入，激发学习动机"
    },
    {
      "step": "新知讲授",
      "duration": "15min",
      "teacherActivity": "用方格纸验证勾股定理，推导公式 a²+b²=c²",
      "studentActivity": "在方格纸上动手验证，记录数据",
      "designIntent": "通过实验发现规律，培养归纳推理能力"
    },
    {
      "step": "例题示范",
      "duration": "10min",
      "teacherActivity": "已知直角边 a=3, b=4，求斜边 c，板书完整解题步骤",
      "studentActivity": "跟练，代入公式计算 c=√(9+16)=5",
      "designIntent": "规范解题格式，巩固公式运用"
    },
    {
      "step": "课堂练习",
      "duration": "10min",
      "teacherActivity": "布置3道由易到难的练习题，巡视指导",
      "studentActivity": "独立完成，小组互查",
      "designIntent": "分层练习，检测学习效果"
    },
    {
      "step": "课堂小结",
      "duration": "5min",
      "teacherActivity": "总结勾股定理的适用条件和公式，强调易错点",
      "studentActivity": "回顾知识点，记录笔记",
      "designIntent": "梳理知识结构，形成记忆"
    }
  ]
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| teachingProcess | object[] | 4-6 个教学环节 |
| teachingProcess[].step | string | 环节名称 |
| teachingProcess[].duration | string | 时长（如 "5min"） |
| teachingProcess[].teacherActivity | string | 教师活动描述（≥10字） |
| teachingProcess[].studentActivity | string | 学生活动描述（≥10字） |
| teachingProcess[].designIntent | string | 设计意图（≥10字） |

### 错误响应

```json
{
  "detail": "教学目标不能为空"
}
```

### 使用场景

用户在「编辑页」修改教学目标后，点击「AI 生成」按钮，根据新目标重新生成教学过程。

---

## 3. 与现有接口的关系

```
现有接口（已实现）:
  POST /api/lesson-prep/generate          → 三阶段完整备课（SSE流式）
  POST /api/lesson-prep/generate-ppt      → 单独生成PPT

新增接口:
  POST /api/lesson-prep/generate-goals    → 单独生成教学目标（3-5条）
  POST /api/lesson-prep/generate-process  → 单独生成教学过程（4-6个环节）

设计原则:
  - 新增接口独立、轻量，不依赖完整备课流程
  - 复用 Stage 1 的知识库和风格描述
  - 响应体简化，只返回需要的字段
  - 支持 knowledgePointNames 透传
```

---

## 4. 技术实现要点

| 接口 | Prompt 策略 | 输出格式 |
|------|-------------|----------|
| generate-goals | 轻量 prompt，只要求生成目标列表 | `{"goals": [...]}` |
| generate-process | 复用 Stage 1 的 teaching_process schema | `{"teachingProcess": [...]}` |

两个接口都使用 `temperature=0.4`（结构化输出），`json_mode=True`（强制 JSON 输出）。
