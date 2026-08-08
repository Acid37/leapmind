# LeapMind 接口文档

本文档根据当前项目 Java 后端实际 Controller 整理。

## 基础信息

- Java 后端地址：`http://localhost:8080`
- 前端开发地址：`http://localhost:5173`
- 前端代理：前端请求 `/api/**` 会代理到 `http://localhost:8080/api/**`
- Swagger UI：`http://localhost:8080/swagger-ui.html`
- OpenAPI JSON：`http://localhost:8080/v3/api-docs`
- Knife4j 文档地址：`http://localhost:8080/doc.html`

## 认证方式

登录成功后，后端返回 JWT Token。调用需要登录的接口时，在请求头中携带：

```http
Authorization: Bearer <token>
```

当前安全配置中：

- 公开接口：`/api/auth/**`、`/api/education/**`、Swagger/OpenAPI、静态资源、`/api/admin/**`
- 需要登录：`/api/practice/**`、`/api/courses/**`、`/api/speech/**`、`/api/voice-chat/**` 等

## 统一响应格式

除下载类接口外，大多数接口返回：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {},
  "timestamp": 1784970000000
}
```

错误示例：

```json
{
  "code": 400,
  "message": "错误信息",
  "data": null,
  "timestamp": 1784970000000
}
```

## 1. 用户认证接口

基础路径：`/api/auth`

### 注册

```http
POST /api/auth/register
```

请求体：

```json
{
  "username": "xiaoming1",
  "password": "123456",
  "grade": "GRADE_1",
  "stage": "PRIMARY",
  "studentName": "小明",
  "email": "xiaoming@test.com",
  "phone": "13800001001"
}
```

说明：

- `username`：3-50 个字符
- `password`：6-20 个字符
- `grade`：可用值如 `GRADE_1` 到 `GRADE_9`
- `phone`：中国大陆手机号格式

### 密码登录

```http
POST /api/auth/login
```

请求体：

```json
{
  "username": "xiaoming1",
  "password": "123456"
}
```

响应 `data` 示例：

```json
{
  "token": "jwt-token",
  "userInfo": {
    "id": 1,
    "username": "xiaoming1",
    "identify": "student",
    "grade": "GRADE_1",
    "stage": null,
    "studentName": "小明1",
    "email": "xiaoming1@test.com",
    "phone": "13800001001",
    "status": 1,
    "createdAt": "2026-07-17T12:30:25"
  },
  "expiresIn": 86400000
}
```

### 发送短信验证码

```http
GET /api/auth/login/sendCode?phoneNumber=13800001001
```

### 验证短信验证码登录

```http
POST /api/auth/login/verifyCode
```

### 获取当前用户资料

```http
GET /api/auth/profile
Authorization: Bearer <token>
```

### 更新当前用户资料

```http
PUT /api/auth/profile
Authorization: Bearer <token>
```

## 2. M1 练习模块接口

基础路径：`/api/practice`

本模块接口默认需要登录。

### 2.1 获取筛选条件

```http
GET /api/practice/filters
Authorization: Bearer <token>
```

响应 `data` 包含：

```json
{
  "subjects": ["数学", "英语", "计算机"],
  "gradeLevels": ["大学"],
  "tracks": ["高数期末"],
  "chapters": ["函数极限", "算法基础"],
  "knowledgePoints": ["CRUD", "动态规划"],
  "questionTypes": ["SINGLE_CHOICE", "MULTIPLE_CHOICE", "FILL_BLANK", "SHORT_ANSWER"],
  "difficulties": ["BASIC", "ADVANCED", "HARD"],
  "modes": ["FREE_PRACTICE", "AFTER_CLASS", "MISTAKE_REDO", "SEQUENTIAL", "RANDOM", "MISTAKES"],
  "mistakeStatuses": ["UNRESOLVED", "REVIEWING", "RESOLVED"]
}
```

### 2.2 查询题库列表

```http
GET /api/practice/questions?page=1&pageSize=20&subject=数学&gradeLevel=大学&chapter=函数极限&questionType=SINGLE_CHOICE&difficulty=BASIC&lessonId=xxx
Authorization: Bearer <token>
```

查询参数：

| 参数 | 必填 | 说明 |
|---|---:|---|
| `page` | 否 | 页码，默认 1 |
| `pageSize` / `size` | 否 | 每页数量 |
| `subject` | 否 | 科目 |
| `gradeLevel` | 否 | 年级 |
| `track` | 否 | 赛道/专题 |
| `chapter` | 否 | 章节 |
| `knowledgePoint` | 否 | 知识点 |
| `questionType` | 否 | 题型 |
| `difficulty` | 否 | 难度 |
| `lessonId` | 否 | 课程/课后题关联 ID |
| `status` | 否 | 题目状态 |
| `keyword` | 否 | 关键字，当前后端主要按查询包装器实现，前端也有部分本地过滤 |

响应 `data` 示例：

```json
{
  "total": 16,
  "page": 1,
  "pageSize": 20,
  "records": [
    {
      "id": 19,
      "subject": "数学",
      "gradeLevel": "大学",
      "questionType": "FILL_BLANK",
      "title": "接口验证填空",
      "content": "1+1=____",
      "options": {},
      "correctAnswer": "2",
      "answerKeywords": "2;二",
      "analysis": "基础加法",
      "chapter": "接口验证",
      "knowledgePoint": "CRUD",
      "difficulty": "BASIC",
      "track": "高数期末",
      "lessonId": null,
      "status": "ENABLED"
    }
  ]
}
```

### 2.3 获取题目详情

```http
GET /api/practice/questions/{questionId}
Authorization: Bearer <token>
```

### 2.4 创建题目

```http
POST /api/practice/questions
Authorization: Bearer <token>
Content-Type: application/json
```

请求体：

```json
{
  "subject": "数学",
  "gradeLevel": "大学",
  "questionType": "SINGLE_CHOICE",
  "title": "勾股定理",
  "content": "在直角三角形ABC中，∠C=90°，AC=3，BC=4，则AB长度是？",
  "optionA": "5",
  "optionB": "6",
  "optionC": "7",
  "optionD": "8",
  "correctAnswer": "A",
  "answerKeywords": "",
  "analysis": "3-4-5 直角三角形",
  "chapter": "勾股定理",
  "knowledgePoint": "勾股定理",
  "difficulty": "BASIC",
  "track": "数学练习",
  "lessonId": null,
  "status": "ENABLED"
}
```

题型：

- `SINGLE_CHOICE`：单选题
- `MULTIPLE_CHOICE`：多选题
- `FILL_BLANK`：填空题
- `SHORT_ANSWER`：简答题

难度：

- `BASIC`
- `ADVANCED`
- `HARD`

### 2.5 更新题目

```http
PUT /api/practice/questions/{questionId}
Authorization: Bearer <token>
Content-Type: application/json
```

请求体同创建题目。

### 2.6 删除题目

```http
DELETE /api/practice/questions/{questionId}
Authorization: Bearer <token>
```

### 2.7 批量导入题目

```http
POST /api/practice/questions/import
Authorization: Bearer <token>
Content-Type: multipart/form-data
```

表单字段：

| 字段 | 类型 | 说明 |
|---|---|---|
| `file` | File | Excel 或 CSV 文件 |

响应 `data`：

```json
{
  "totalRows": 10,
  "inserted": 9,
  "failed": 1,
  "errors": ["第 3 行：题干不能为空"]
}
```

### 2.8 下载导入模板

```http
GET /api/practice/questions/import-template
Authorization: Bearer <token>
```

返回 CSV 文件。

模板字段：

```text
subject,gradeLevel,track,chapter,knowledgePoint,questionType,difficulty,title,content,optionA,optionB,optionC,optionD,correctAnswer,answerKeywords,analysis,lessonId,status
```

### 2.9 获取下一题

```http
GET /api/practice/next?mode=SEQUENTIAL&subject=数学&gradeLevel=大学&chapter=函数极限&questionType=SINGLE_CHOICE&difficulty=BASIC&lessonId=xxx
Authorization: Bearer <token>
```

查询参数：

| 参数 | 说明 |
|---|---|
| `mode` | 出题模式 |
| `subject` | 科目 |
| `gradeLevel` | 年级 |
| `track` | 赛道/专题 |
| `chapter` | 章节 |
| `knowledgePoint` | 知识点 |
| `questionType` | 题型 |
| `difficulty` | 难度 |
| `lessonId` | 课程/课后题 ID |

`mode` 可选：

- `SEQUENTIAL`：顺序出题
- `RANDOM`：随机出题
- `MISTAKES`：错题模式
- `FREE_PRACTICE`：自由练习
- `AFTER_CLASS`：课后题
- `MISTAKE_REDO`：错题重做

响应 `data`：

```json
{
  "question": {
    "id": 1,
    "subject": "数学",
    "questionType": "SINGLE_CHOICE",
    "content": "题干",
    "options": {
      "A": "选项A",
      "B": "选项B"
    }
  },
  "mode": "SEQUENTIAL",
  "stats": {}
}
```

### 2.10 提交答案并判题

```http
POST /api/practice/submit
Authorization: Bearer <token>
Content-Type: application/json
```

请求体：

```json
{
  "questionId": 1,
  "userAnswer": "A",
  "durationSeconds": 45,
  "mode": "FREE_PRACTICE"
}
```

响应 `data`：

```json
{
  "record": {},
  "question": {},
  "correct": true,
  "judgeScore": 100.0,
  "judgeFeedback": "选择题精确匹配正确",
  "points": 10,
  "dailyBonus": 0,
  "attemptNumber": 1,
  "conquered": false,
  "surpassPercent": 80,
  "nextQuestion": {},
  "dashboard": {}
}
```

判题规则：

- 单选题：精确匹配
- 多选题：选项集合匹配
- 填空题：标准答案/关键词匹配
- 简答题：关键词覆盖 + 文本相似度，尝试 AI 语义反馈

### 2.11 查询答题记录

```http
GET /api/practice/records?range=week&chapter=函数极限&knowledgePoint=重要极限&wrongOnly=false
Authorization: Bearer <token>
```

参数：

| 参数 | 说明 |
|---|---|
| `range` | `week` / `month` / `all` |
| `chapter` | 章节 |
| `knowledgePoint` | 知识点 |
| `wrongOnly` | 是否只看错题 |

### 2.12 查询错题本

```http
GET /api/practice/mistakes?status=UNRESOLVED&chapter=函数极限&knowledgePoint=重要极限
Authorization: Bearer <token>
```

参数：

| 参数 | 说明 |
|---|---|
| `status` | `UNRESOLVED` / `REVIEWING` / `RESOLVED` |
| `chapter` | 章节 |
| `knowledgePoint` | 知识点 |

### 2.13 更新错题本状态

```http
PATCH /api/practice/mistake-book/{mistakeId}
Authorization: Bearer <token>
Content-Type: application/json
```

请求体：

```json
{
  "doubtful": true,
  "reviewNote": "这题需要重点复习",
  "status": "REVIEWING"
}
```

状态：

- `UNRESOLVED`：未解决
- `REVIEWING`：复习中
- `RESOLVED`：已解决

### 2.13.1 兼容错题本接口

为了兼容其他小组的错题本接口文档，当前项目额外提供 `/api/wrong-questions` 这一组路径。它们内部复用练习模块错题本能力。

#### 查询错题本列表

```http
GET /api/wrong-questions?status=UNRESOLVED&chapter=函数极限&knowledgePoint=重要极限
Authorization: Bearer <token>
```

等价于：

```http
GET /api/practice/mistakes
```

#### 标记/取消重点复习

```http
PUT /api/wrong-questions/{id}/focus
Authorization: Bearer <token>
Content-Type: application/json
```

请求体：

```json
{
  "focused": true
}
```

说明：

- `focused=true`：标记重点复习
- `focused=false`：取消重点复习
- 不传请求体时，后端会切换当前重点状态

#### 删除错题记录

```http
DELETE /api/wrong-questions/{id}
Authorization: Bearer <token>
```

说明：删除当前登录用户自己的错题本记录。

#### 生成错题重做练习会话

```http
POST /api/wrong-questions/batch-redo
Authorization: Bearer <token>
Content-Type: application/json
```

请求体：

```json
{
  "ids": [1, 2, 3]
}
```

说明：

- `ids` 为空或不传时，会使用当前用户全部未解决/复习中的错题。
- 返回 `sessionId`、`mode=MISTAKE_REDO`、`questions`、`totalCount`、`mistakeIds`。

### 2.14 获取成长看板

```http
GET /api/practice/dashboard
Authorization: Bearer <token>
```

响应 `data` 包含积分、今日答题数、错题数量、签到状态、周趋势等。

### 2.15 获取练习统计

```http
GET /api/practice/statistics?range=week
Authorization: Bearer <token>
```

参数：

| 参数 | 说明 |
|---|---|
| `range` | `week` / `month` / `all` |

响应 `data` 通常包含：

```json
{
  "totalAnswers": 256,
  "accuracy": 72,
  "averageDurationSeconds": 45,
  "trend": [],
  "knowledgeDistribution": []
}
```

### 2.16 获取排行榜

```http
GET /api/practice/leaderboards?track=高数期末
Authorization: Bearer <token>
```

说明：

- 后端优先使用 Redis Sorted Set；
- Redis 不可用时使用 MySQL 排行榜降级。

### 2.17 每日签到

```http
POST /api/practice/checkin
Authorization: Bearer <token>
Content-Type: application/json
```

请求体可为空 JSON：

```json
{}
```

### 2.18 获取签到状态

```http
GET /api/practice/checkin/status
Authorization: Bearer <token>
```

### 2.19 更新排行榜隐私

```http
PATCH /api/practice/privacy
Authorization: Bearer <token>
Content-Type: application/json
```

请求体：

```json
{
  "hidden": true
}
```

### 2.20 更新答题记录中的错题信息

```http
PATCH /api/practice/mistakes/{recordId}
Authorization: Bearer <token>
Content-Type: application/json
```

请求体：

```json
{
  "doubtful": true,
  "reviewNote": "审题不仔细",
  "status": "REVIEWING"
}
```

### 2.21 导出答题记录

```http
GET /api/practice/export?wrongOnly=false
Authorization: Bearer <token>
```

返回文本文件。

### 2.22 清空个人练习数据

```http
DELETE /api/practice/records
Authorization: Bearer <token>
```

## 3. 教育阶段接口

基础路径：`/api/education`

当前安全配置允许匿名访问。

### 查询所有阶段

```http
GET /api/education/stages
```

### 根据阶段查询年级

```http
GET /api/education/stages/{stageCode}/grades
```

示例：

```http
GET /api/education/stages/PRIMARY/grades
```

## 4. 课程接口

基础路径：`/api/courses`

课程接口需要登录。

### 创建/查询课程章节

```http
POST /api/courses/section
Authorization: Bearer <token>
```

### 获取课程 PPT 数据

```http
GET /api/courses/{courseId}/slides-data
Authorization: Bearer <token>
```

## 5. 语音问答接口

基础路径：`/api/voice-chat`

需要登录。

### AI 问答

```http
POST /api/voice-chat/ask
Authorization: Bearer <token>
Content-Type: application/json
```

### 语音合成

```http
POST /api/voice-chat/synthesize
Authorization: Bearer <token>
Content-Type: application/json
```

## 6. 语音合成批处理接口

基础路径：`/api/speech`

需要登录。

### 批量语音合成

```http
POST /api/speech/bulk-synthesis
Authorization: Bearer <token>
```

### 查询课程指定页音频

```http
GET /api/speech/ppt/{courseId}/page/{pageNumber}
Authorization: Bearer <token>
```

### 查询课程全部音频

```http
GET /api/speech/ppt/{courseId}
Authorization: Bearer <token>
```

### 查询课程指定页音频文件

```http
GET /api/speech/ppt/{courseId}/page/{pageNumber}/audio
Authorization: Bearer <token>
```

### 批量预处理

```http
POST /api/speech/bulk-preprocessing
Authorization: Bearer <token>
```

### 执行课程批量合成

```http
POST /api/speech/bulk-synthesis-execute/{courseId}
Authorization: Bearer <token>
```

## 7. 管理后台接口

当前安全配置中 `/api/admin/**` 被放行，但部分方法代码上有 `@AdminRequired` 注解。

### 用户管理

基础路径：`/api/admin`

```http
POST   /api/admin/users
GET    /api/admin/users
GET    /api/admin/users/{id}
GET    /api/admin/users/search/name?studentName=小明
GET    /api/admin/users/search/stage?stage=PRIMARY
PUT    /api/admin/users/{id}
DELETE /api/admin/users/{id}
```

### 阶段/年级管理

基础路径：`/api/admin`

```http
GET    /api/admin/stages
GET    /api/admin/stages/{stageCode}
GET    /api/admin/stages/statistics
GET    /api/admin/grades
GET    /api/admin/grades/{gradeCode}
GET    /api/admin/stages/{stageCode}/grades
GET    /api/admin/grades/statistics
POST   /api/admin/education-stages
PUT    /api/admin/education-stages/{id}
DELETE /api/admin/education-stages/{id}
GET    /api/admin/education-stages/{id}
GET    /api/admin/education-stages
```

### 课程管理

基础路径：`/api/admin`

```http
POST   /api/admin/courses
GET    /api/admin/courses/{courseId}
GET    /api/admin/courses
GET    /api/admin/courses/search/stage?stage=PRIMARY
GET    /api/admin/courses/search/subject?subject=数学
PUT    /api/admin/courses/{id}
DELETE /api/admin/courses/{id}
POST   /api/admin/courses/search
```

### 大纲管理

基础路径：`/api/admin/outline`

```http
POST   /api/admin/outline
GET    /api/admin/outline
GET    /api/admin/outline/{courseId}
GET    /api/admin/outline/project/{courseId}
PUT    /api/admin/outline
DELETE /api/admin/outline/{id}
```

### PPT 管理

基础路径：`/api/admin/ppt`

```http
GET    /api/admin/ppt/slides
GET    /api/admin/ppt/slides/project/{courseId}
GET    /api/admin/ppt/slides/{id}
POST   /api/admin/ppt/slides
PUT    /api/admin/ppt/slides/{id}
DELETE /api/admin/ppt/slides/{id}
GET    /api/admin/ppt/slides/exists/{courseId}
```

### 音频片段管理

基础路径：`/api/admin/audio-segments`

```http
GET    /api/admin/audio-segments
GET    /api/admin/audio-segments/course/{courseId}
GET    /api/admin/audio-segments/{id}
PUT    /api/admin/audio-segments/{id}
DELETE /api/admin/audio-segments/{id}
DELETE /api/admin/audio-segments/course/{courseId}
GET    /api/admin/audio-segments/course/{courseId}/stats
GET    /api/admin/audio-segments/course/{courseId}/session
GET    /api/admin/audio-segments/course/{courseId}/audio-status
GET    /api/admin/audio-segments/course/{courseId}/audio-synthesis
```

## 8. 页面入口

```http
GET /admin
GET /admin/login
```

都会跳转到：

```http
/admin/index-modern.html
```

## 9. 前端当前主要调用接口

当前 M1 前端主要调用：

```http
GET  /api/practice/filters
GET  /api/practice/questions
GET  /api/practice/questions/{questionId}
GET  /api/practice/next
POST /api/practice/submit
GET  /api/practice/mistakes
GET  /api/practice/statistics
GET  /api/practice/leaderboards
POST /api/practice/checkin
```

当前前端仍未完整接入的后端接口：

- `POST /api/practice/questions/import`：Excel/CSV 批量导入
- `PATCH /api/practice/mistake-book/{mistakeId}`：重点标记/状态修改前端逻辑未完全接好
- `DELETE` 错题删除：后端目前没有独立错题删除接口，前端是 Mock
