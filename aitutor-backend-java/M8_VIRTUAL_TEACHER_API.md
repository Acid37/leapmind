# M8 虚拟 AI 教师 Java 接口文档

负责人：欧俊濠
模块范围：虚拟教师形象管理、用户教师偏好、TTS 语音合成、音频缓存与存储。

## 1. 当前结论

M8 Java 后端已经实现了可联调的 MVP，不是空壳。它具备数据库表、管理员形象 CRUD、用户偏好保存、TTS 合成、Redis 缓存、MinIO/本地音频存储、JWT 保护、参数校验和基础单元测试。

但它还不能算完整工业化版本。当前更准确的定位是“工程化 MVP”：能支撑前端演示和基础联调，已经有生产化雏形，但还缺少异步任务、限流、审计日志、监控指标、失败重试、真正边生成边播放的流式 TTS、资源配额等正式上线能力。

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

## 6. 数据库

迁移文件：

```text
src/main/resources/db/migration/V3__add_virtual_teacher.sql
```

新增表：

| 表名 | 说明 |
|---|---|
| teacher_avatars | 虚拟教师形象表 |
| user_teacher_preferences | 用户教师偏好表 |

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
- TTS 缓存命中/未命中单元测试

仍需补齐：

- TTS 异步任务队列，避免长文本合成占用 HTTP 请求线程
- 接口限流和用户级配额，避免 TTS 成本失控
- Micrometer 指标：合成耗时、缓存命中率、失败率、音频大小
- 审计日志：谁在什么时候使用了哪个形象、合成了多少字符
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
