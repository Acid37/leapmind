# M8 虚拟 AI 教师 Java 接口文档

负责人：欧俊濠
模块范围：虚拟教师形象管理、用户教师偏好、TTS 语音合成、音频缓存与存储。

## 1. 当前结论

M8 Java 后端已经按正式项目补齐核心工程能力。它具备数据库表、管理员形象 CRUD、用户偏好保存、TTS 合成、Redis 缓存、MinIO/本地音频存储、JWT 保护、参数校验、用户级限流、每日字符配额、审计日志、Micrometer 指标和单元测试。

当前仍保留同步 TTS 调用模式，适合课程演示、联调和中小规模使用。若后续要承载高并发或长文本批量合成，应继续升级为异步任务队列和真正低延迟流式 TTS。

## 2. 鉴权规则

除音频直读接口外，所有 `/api/virtual-teacher/**` 接口都需要 JWT。

请求头：

```http
Authorization: Bearer <token>
```

开放接口：

```http
GET /api/virtual-teacher/audio/{objectKey}
```

该接口用于浏览器直接播放已生成的 WAV 音频。

## 3. 用户接口

### 3.1 获取启用的教师形象

```http
GET /api/virtual-teacher/avatars
```

返回：

```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "id": "teacher-001",
      "name": "小跃",
      "description": "亲切活泼，适合语言与通识课程",
      "modelUrl": "/vrm/teacher001_girl.vrm",
      "thumbnailUrl": null,
      "voiceType": "zhixiaoxia",
      "accent": "普通话",
      "enabled": true,
      "sortOrder": 10,
      "speed": 1.0
    }
  ]
}
```

### 3.2 获取当前用户教师偏好

```http
GET /api/virtual-teacher/preference
```

返回当前用户已保存的教师形象、音色、语速。若用户未保存偏好，则返回默认启用形象。

### 3.3 保存当前用户教师偏好

```http
PUT /api/virtual-teacher/preference
Content-Type: application/json
```

请求：

```json
{
  "avatarId": "teacher-001",
  "voiceType": "zhixiaoxia",
  "speed": 1.0
}
```

字段约束：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| avatarId | string | 是 | 教师形象业务 ID，最大 64 字符 |
| voiceType | string | 否 | 音色，最大 100 字符 |
| speed | number | 否 | 语速，范围 0.50 到 2.00 |

### 3.4 TTS 合成并返回音频 URL

```http
POST /api/virtual-teacher/tts
Content-Type: application/json
```

请求：

```json
{
  "courseId": "course-1001",
  "text": "同学们，我们开始上课。",
  "voiceType": "zhixiaoxia",
  "speed": 1.0
}
```

字段约束：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| courseId | string | 否 | 课程 ID，最大 64 字符 |
| text | string | 是 | 待合成文本，最大 500 字符 |
| voiceType | string | 否 | 音色，最大 100 字符 |
| speed | number | 否 | 语速，范围 0.50 到 2.00 |

返回：

```json
{
  "code": 200,
  "message": "语音合成完成",
  "data": {
    "audioUrl": "/api/virtual-teacher/audio/0f8c...abcd.wav",
    "contentType": "audio/wav",
    "audioSize": 123456,
    "cacheHit": false,
    "cacheKey": "tts:audio:0f8c...abcd"
  }
}
```

限流：

- 默认每个用户每分钟最多 20 次 TTS 请求。
- 默认每个用户每天最多合成 20000 个字符。
- 超限返回 HTTP 429。

缓存规则：

```text
cacheKey = SHA-256(text + voiceType + speed)
Redis key = tts:audio:{sha256}
TTL = virtual-teacher.cache-ttl，默认 24h
```

### 3.5 TTS 合成并返回 WAV 流

```http
POST /api/virtual-teacher/tts/stream
Content-Type: application/json
Accept: audio/wav
```

请求体与 `/tts` 相同。

响应：

```http
Content-Type: audio/wav
Content-Disposition: inline; filename="teacher.wav"
X-TTS-Cache: HIT | MISS
```

注意：当前实现是“先完成 TTS 合成，再按 8192 字节分块写出”。它对浏览器是流式响应，但还不是真正的边合成边播放低延迟 TTS。

### 3.6 读取音频

```http
GET /api/virtual-teacher/audio/{objectKey}
```

说明：

- 本地存储模式下，`objectKey` 必须匹配 `{64位sha256}.wav`。
- 响应类型为 `audio/wav`。
- 浏览器可直接用该地址播放。

## 4. 管理员接口

管理员写接口使用现有 `@AdminRequired` 权限校验。

### 4.1 获取全部教师形象

```http
GET /api/virtual-teacher/avatars/admin
```

### 4.2 创建教师形象

```http
POST /api/virtual-teacher/avatars
Content-Type: application/json
```

请求：

```json
{
  "avatarCode": "teacher-004",
  "name": "知行",
  "description": "适合课程讲解的大学生风格虚拟教师",
  "modelUrl": "/vrm/teacher004.vrm",
  "thumbnailUrl": "/image/teacher004.png",
  "voiceType": "zhixiaoxia",
  "accent": "普通话",
  "enabled": true,
  "sortOrder": 40
}
```

字段约束：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| avatarCode | string | 是 | 只允许字母、数字、下划线、中划线，最大 64 字符 |
| name | string | 是 | 最大 100 字符 |
| description | string | 否 | 最大 500 字符 |
| modelUrl | string | 是 | 以 `http://`、`https://` 或 `/` 开头，最大 500 字符 |
| thumbnailUrl | string | 否 | 以 `http://`、`https://` 或 `/` 开头，最大 500 字符 |
| voiceType | string | 是 | 最大 100 字符 |
| accent | string | 否 | 最大 50 字符 |
| enabled | boolean | 否 | 默认 true |
| sortOrder | number | 否 | 默认 0 |

### 4.3 更新教师形象

```http
PUT /api/virtual-teacher/avatars/{id}
Content-Type: application/json
```

请求体同创建教师形象。

### 4.4 删除教师形象

```http
DELETE /api/virtual-teacher/avatars/{id}
```

若该形象仍被用户偏好引用，接口会拒绝删除。建议生产环境优先使用 `enabled=false` 做停用。

## 5. 配置项

```yaml
virtual-teacher:
  cache-ttl: 24h
  synthesis-timeout: 125s
  rate-limit:
    enabled: true
    requests-per-minute: 20
    daily-characters: 20000
  storage:
    type: ${VIRTUAL_TEACHER_STORAGE_TYPE:local}
    local-dir: ${VIRTUAL_TEACHER_LOCAL_DIR:${java.io.tmpdir}/leapmind-tts}
    public-base-url: ${VIRTUAL_TEACHER_PUBLIC_BASE_URL:}
    endpoint: ${MINIO_ENDPOINT:http://localhost:9000}
    access-key: ${MINIO_ACCESS_KEY:minioadmin}
    secret-key: ${MINIO_SECRET_KEY:minioadmin}
    bucket: ${MINIO_BUCKET:leapmind-tts}
```

本地开发默认使用 local 存储。部署时建议：

```text
VIRTUAL_TEACHER_STORAGE_TYPE=minio
MINIO_ENDPOINT=http://localhost:9000
MINIO_ACCESS_KEY=<生产访问密钥>
MINIO_SECRET_KEY=<生产访问密钥>
MINIO_BUCKET=leapmind-tts
```

Redis：

```text
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=
```

Redis 不可用时服务不会中断，会降级到当前 Java 进程内缓存；但进程重启后缓存丢失。

限流环境变量：

```text
VIRTUAL_TEACHER_RATE_LIMIT_ENABLED=true
VIRTUAL_TEACHER_REQUESTS_PER_MINUTE=20
VIRTUAL_TEACHER_DAILY_CHARACTERS=20000
```

限流优先使用 Redis 计数。Redis 不可用时降级为当前 Java 进程内计数。

## 6. 数据库

迁移文件：

```text
src/main/resources/db/migration/V4__add_virtual_teacher.sql
src/main/resources/db/migration/V5__add_virtual_teacher_audit.sql
```

新增表：

| 表名 | 说明 |
|---|---|
| teacher_avatars | 虚拟教师形象表 |
| user_teacher_preferences | 用户教师偏好表 |
| virtual_teacher_tts_audit_logs | TTS 合成审计日志表 |

初始化形象：

| avatarCode | 名称 | 默认音色 |
|---|---|---|
| teacher-001 | 小跃 | zhixiaoxia |
| teacher-002 | 知夏 | zhixiaobai |
| teacher-003 | 星澜 | zhixiaoxia |

## 7. 工业化程度评估

已具备：

- Spring Security + JWT 接口保护
- Bean Validation 参数校验
- Flyway 数据库迁移
- Redis 缓存和本地缓存降级
- MinIO 对象存储和本地存储降级
- 音频对象 key 白名单校验，避免任意路径读取
- TTS 超时配置化
- 用户级 TTS 请求限流
- 用户级每日合成字符配额
- TTS 成功/失败审计日志
- Micrometer 指标：请求数、缓存命中、音频大小、合成耗时
- TTS 缓存命中/未命中单元测试
- 限流和审计调用单元测试

后续增强项：

- TTS 异步任务队列，避免长文本合成占用 HTTP 请求线程
- 第三方 TTS 失败重试和熔断
- 真正低延迟流式 TTS，而不是合成完成后再分块返回
- 管理端更细粒度权限，当前依赖现有 `@AdminRequired`
- 更完整的集成测试：数据库迁移、MinIO、Redis、Controller 鉴权

## 8. 验证方式

```powershell
$env:JAVA_HOME='E:\application2\Java\jdk-17'
mvn test
```

当前单元测试覆盖：

- 缓存命中时不调用第三方 TTS
- 缓存未命中时合成、存储并写入缓存
- 用户请求会触发限流检查和审计日志记录

## 9. 前端对接说明

M8 前端当前对接文件：

```text
aitutor-frontend/src/services/virtualTeacherService.js
aitutor-frontend/src/pages/TeacherAvatarPage.jsx
aitutor-frontend/src/components/virtualTeacher/VirtualTeacherViewer.jsx
```

字段兼容规则：

| 后端字段 | 前端使用 | 说明 |
|---|---|---|
| id | avatar.id | 教师形象业务 ID，用于保存偏好 |
| name | avatar.name | 页面展示名称 |
| description | avatar.description | 教师设定说明 |
| modelUrl | avatar.modelUrl | VRM 模型地址 |
| voiceType | avatar.voiceType | TTS 音色 |
| accent | avatar.accent | 口音/语言说明 |
| speed | avatar.speed | 语速，后续可接入页面调节 |

前端容错：

- `/avatars` 不可用时，使用内置三套 VRM 演示形象。
- `/preference` 不可用时，读取浏览器本地偏好。
- `/preference` 保存失败但非 401 时，偏好保存在本地浏览器，并提示“后端接口尚未连通”。
- `/tts` 不可用时，页面保留 3D 教师动作演示，并在课堂互动消息中提示语音接口暂不可用。

课堂互动已改为真实后端链路，不再由前端拼接固定答案：

```http
POST /api/voice-chat/ask
Authorization: Bearer <token>
Content-Type: application/json

{
  "courseId": "limit",
  "question": "为什么极限存在不要求函数在该点有定义？"
}
```

成功响应：

```json
{
  "answer": "AI 教师回答内容",
  "courseId": "limit",
  "status": "SUCCESS"
}
```

用户提问同时通过 M6 正式事件接口记录为 `lecture_interact` / `M4` / `ask`，
用于后续更新学习画像。M8 是形象与呈现层，因此事件归属 M4 讲课模块。

登录对接注意事项：

- Java 登录响应字段为 `data.userInfo`，前端兼容旧字段 `data.user`。
- Java `expiresIn` 当前为毫秒，前端会归一化为秒后保存。
- TTS 返回相对 `audioUrl` 时，前端会自动拼接 `VITE_API_BASE`；MinIO 预签名绝对地址直接使用。

当前 M8 页面已包含：

- VRM 3D 教师预览
- 教师形象选择
- 用户教师偏好保存
- 表情与头部动作预览
- 教学内容展示
- 开始讲解按钮
- 课堂提问与教师反馈
- `/api/voice-chat/ask` 真实 AI 答疑
- M6 `record-event` 提问行为写入
- TTS 语音合成调用与失败降级

### 9.1 正式课程课件接入

从首页课程卡片点击“AI 教师”时，前端会把该课程的 `courseId` 传入 M8 页面，并请求：

```http
GET /api/courses/{courseId}/slides-data
Authorization: Bearer <JWT>
```

返回的每一页课件包含：

| 字段 | 类型 | 用途 |
|---|---|---|
| `courseId` | string | 课程/会话 ID |
| `slideIndex` | integer | 页面顺序，后端按升序返回 |
| `slideId` | string | 页面业务 ID，用于 M6 行为记录 |
| `title` | string | 课件标题 |
| `contentType` | string | 页面类型 |
| `htmlContent` | string | 课件正文，前端提取讲稿文本并保留原始 HTML |

正式数据链路：

```text
首页课程卡片
  -> onOpenTeacherAvatar(courseId)
  -> TeacherAvatarPage(courseId)
  -> GET /api/courses/{courseId}/slides-data
  -> VRM 3D 场景内展示课件
  -> POST /api/voice-chat/ask
  -> POST /api/virtual-teacher/tts
  -> POST /api/user-profile/{userId}/record-event
```

如果课程没有课件或接口暂时不可用，页面会明确显示“当前为演示”，并使用内置课件降级；不会把演示数据标记为正式课程。

### 9.2 生产安全配置

`application.yml` 不再保存 AI、阿里云、百度云等明文凭据，部署时必须通过环境变量注入。曾提交到 Git 历史中的旧凭据不能仅靠删除文件保证安全，必须在对应云平台禁用并轮换。

生产环境数据库同样不再提供默认账号密码，启动前至少需要设置 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD`。AI/TTS 按实际供应商设置 `AI_API_KEY`、`ALIYUN_ACCESS_KEY_ID`、`ALIYUN_ACCESS_KEY_SECRET`、`TTS_APP_KEY`，以及百度语音相关环境变量。
