# 教育阶段和用户认证 API 文档

### 基础信息

- **基础 URL**: `/api`
- **内容类型**: `application/json`
- **字符编码**: `UTF-8`

### 通用响应格式

所有接口都使用统一的响应格式：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {},
  "timestamp": "2024-01-01T12:00:00"
}
```

**响应字段说明：**

- `code`: 状态码（200 表示成功，其他表示错误）
- `message`: 响应消息
- `data`: 响应数据
- `timestamp`: 响应时间戳

---

## 1. 教育阶段管理接口

### 1.1 查询所有教育阶段

**接口描述**: 获取系统中所有可用的教育阶段列表

**请求信息**:

- **URL**: `/api/education/stages`
- **方法**: `GET`
- **认证**: 无需认证

**请求参数**: 无

**响应示例**:

```json
{
  "code": 200,
  "message": "查询教育阶段成功",
  "data": [
    {
      "stageCode": "PRIMARY",
      "stageName": "小学",
      "description": "小学教育阶段",
      "sortOrder": 1
    },
    {
      "stageCode": "MIDDLE",
      "stageName": "初中",
      "description": "初中教育阶段",
      "sortOrder": 2
    },
    {
      "stageCode": "HIGH",
      "stageName": "高中",
      "description": "高中教育阶段",
      "sortOrder": 3
    }
  ],
  "timestamp": "2024-01-01T12:00:00"
}
```

**错误响应**:

```json
{
  "code": 400,
  "message": "查询失败的具体原因",
  "data": null,
  "timestamp": "2024-01-01T12:00:00"
}
```

### 1.2 根据阶段代码查询年级列表

**接口描述**: 根据指定的教育阶段代码获取该阶段下的所有年级

**请求信息**:

- **URL**: `/api/education/stages/{stageCode}/grades`
- **方法**: `GET`
- **认证**: 无需认证

**路径参数**:
| 参数名 | 类型 | 必填 | 描述 |
|--------|------|------|------|
| stageCode | String | 是 | 教育阶段代码（如：PRIMARY、MIDDLE、HIGH） |

**请求示例**:

```
GET /api/education/stages/PRIMARY/grades
```

**响应示例**:

```json
{
  "code": 200,
  "message": "查询年级列表成功",
  "data": [
    {
      "gradeCode": "GRADE_1",
      "gradeName": "一年级",
      "stageCode": "PRIMARY",
      "sortOrder": 1
    },
    {
      "gradeCode": "GRADE_2",
      "gradeName": "二年级",
      "stageCode": "PRIMARY",
      "sortOrder": 2
    },
    {
      "gradeCode": "GRADE_3",
      "gradeName": "三年级",
      "stageCode": "PRIMARY",
      "sortOrder": 3
    }
  ],
  "timestamp": "2024-01-01T12:00:00"
}
```

**错误响应**:

```json
{
  "code": 400,
  "message": "无效的阶段代码或查询失败",
  "data": null,
  "timestamp": "2024-01-01T12:00:00"
}
```

---

## 2. 用户认证接口

### 2.1 用户注册

**接口描述**: 新用户注册账号

**请求信息**:

- **URL**: `/api/auth/register`
- **方法**: `POST`
- **认证**: 无需认证
- **内容类型**: `application/json`

**请求参数**:

```json
{
  "username": "string",
  "password": "string",
  "email": "string",
  "phone": "string",
  "realName": "string",
  "stageCode": "string",
  "gradeCode": "string"
}
```

**参数说明**:
| 参数名 | 类型 | 必填 | 描述 |
|--------|------|------|------|
| username | String | 是 | 用户名（唯一） |
| password | String | 是 | 密码 |
| email | String | 是 | 邮箱地址 |
| phone | String | 否 | 手机号码 |
| realName | String | 否 | 真实姓名 |
| stageCode | String | 否 | 教育阶段代码 |
| gradeCode | String | 否 | 年级代码 |

**请求示例**:

```json
{
  "username": "student001",
  "password": "password123",
  "email": "student001@example.com",
  "phone": "13800138000",
  "realName": "张三",
  "stageCode": "PRIMARY",
  "gradeCode": "GRADE_3"
}
```

**响应示例**:

```json
{
  "code": 200,
  "message": "注册成功",
  "data": {
    "id": 1,
    "username": "student001",
    "email": "student001@example.com",
    "phone": "13800138000",
    "realName": "张三",
    "stageCode": "PRIMARY",
    "gradeCode": "GRADE_3",
    "createTime": "2024-01-01T12:00:00",
    "updateTime": "2024-01-01T12:00:00"
  },
  "timestamp": "2024-01-01T12:00:00"
}
```

**错误响应**:

```json
{
  "code": 400,
  "message": "用户名已存在或参数验证失败",
  "data": null,
  "timestamp": "2024-01-01T12:00:00"
}
```

### 2.2 用户登录

**接口描述**: 用户登录获取访问令牌

**请求信息**:

- **URL**: `/api/auth/login`
- **方法**: `POST`
- **认证**: 无需认证
- **内容类型**: `application/json`

**请求参数**:

```json
{
  "username": "string",
  "password": "string"
}
```

**参数说明**:
| 参数名 | 类型 | 必填 | 描述 |
|--------|------|------|------|
| username | String | 是 | 用户名或邮箱 |
| password | String | 是 | 密码 |

**请求示例**:

```json
{
  "username": "student001",
  "password": "password123"
}
```

**响应示例**:

```json
{
  "code": 200,
  "message": "登录成功",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "tokenType": "Bearer",
    "expiresIn": 3600,
    "user": {
      "id": 1,
      "username": "student001",
      "email": "student001@example.com",
      "realName": "张三",
      "stageCode": "PRIMARY",
      "gradeCode": "GRADE_3"
    }
  },
  "timestamp": "2024-01-01T12:00:00"
}
```

**错误响应**:

```json
{
  "code": 401,
  "message": "用户名或密码错误",
  "data": null,
  "timestamp": "2024-01-01T12:00:00"
}
```

### 2.3 获取用户信息

**接口描述**: 获取当前登录用户的详细信息

**请求信息**:

- **URL**: `/api/auth/profile`
- **方法**: `GET`
- **认证**: 需要 Bearer Token
- **请求头**: `Authorization: Bearer {token}`

**请求参数**: 无

**请求示例**:

```
GET /api/auth/profile
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

**响应示例**:

```json
{
  "code": 200,
  "message": "获取用户信息成功",
  "data": {
    "id": 1,
    "username": "student001",
    "email": "student001@example.com",
    "phone": "13800138000",
    "realName": "张三",
    "stageCode": "PRIMARY",
    "gradeCode": "GRADE_3",
    "createTime": "2024-01-01T12:00:00",
    "updateTime": "2024-01-01T12:00:00"
  },
  "timestamp": "2024-01-01T12:00:00"
}
```

**错误响应**:

```json
{
  "code": 401,
  "message": "未授权访问",
  "data": null,
  "timestamp": "2024-01-01T12:00:00"
}
```

### 2.4 更新用户信息

**接口描述**: 更新当前登录用户的信息

**请求信息**:

- **URL**: `/api/auth/profile`
- **方法**: `PUT`
- **认证**: 需要 Bearer Token
- **请求头**: `Authorization: Bearer {token}`
- **内容类型**: `application/json`

**请求参数**:

```json
{
  "email": "string",
  "phone": "string",
  "realName": "string",
  "stageCode": "string",
  "gradeCode": "string"
}
```

**参数说明**:
| 参数名 | 类型 | 必填 | 描述 |
|--------|------|------|------|
| email | String | 否 | 邮箱地址 |
| phone | String | 否 | 手机号码 |
| realName | String | 否 | 真实姓名 |
| stageCode | String | 否 | 教育阶段代码 |
| gradeCode | String | 否 | 年级代码 |

**请求示例**:

```json
{
  "email": "newemail@example.com",
  "phone": "13900139000",
  "realName": "张三丰",
  "stageCode": "MIDDLE",
  "gradeCode": "GRADE_7"
}
```

**响应示例**:

```json
{
  "code": 200,
  "message": "更新用户信息成功",
  "data": {
    "id": 1,
    "username": "student001",
    "email": "newemail@example.com",
    "phone": "13900139000",
    "realName": "张三丰",
    "stageCode": "MIDDLE",
    "gradeCode": "GRADE_7",
    "createTime": "2024-01-01T12:00:00",
    "updateTime": "2024-01-01T13:00:00"
  },
  "timestamp": "2024-01-01T13:00:00"
}
```

**错误响应**:

```json
{
  "code": 401,
  "message": "未授权访问",
  "data": null,
  "timestamp": "2024-01-01T12:00:00"
}
```

---

## 3. 课程管理接口

### 3.1 获取课本章节

**接口描述**: 根据教育阶段、年级和学期获取对应的课本章节列表

**请求信息**:

- **URL**: `/api/courses/section`
- **方法**: `GET`
- **认证**: 无需认证
- **内容类型**: `application/json`

**请求参数**:

```json
{
  "subject": "string",
  "stageName": "string",
  "gradeName": "string",
  "semester": "string"
}
```

**参数说明**:

| 参数名 | 类型 | 必填 | 描述 |
|--------|------|------|------|
| subject | String | 是 | 课程学科名（数学、语文） |
| stageName | String | 是 | 教育阶段名称（如：小学、初中、高中） |
| gradeName | String | 是 | 年级名称（如：一年级、二年级等） |
| semester | String | 是 | 学期枚举值（SEMESTER_1：第一学期，SEMESTER_2：第二学期） |

**请求示例**:

```json
{
  "subject": "数学"
  "stageName": "小学",
  "gradeName": "三年级",
  "semester": "SEMESTER_1"
}
```

**响应示例**:

```json
{
  "code": 200,
  "message": "获取课本章节成功",
  "data": [
    {
      "subject": "数学",
      "sectionNumber": 1,
      "sectionTitle": "第一章 数字的认识",
      "chapterNumber": 1.1,
      "chapterTitle": "认识数字",
      "chapterContent": "本章主要介绍数字的基本概念和认识方法..."
    },
    {
      "subject": "数学",
      "sectionNumber": 2,
      "sectionTitle": "第二章 数字的书写",
      "chapterNumber": 2.1,
      "chapterTitle": "认识数字",
      "chapterContent": "本节学习数字的正确书写方法..."
    },
    {
      "subject": "数学",
      "sectionNumber": 3,
      "sectionTitle": "第三章 简单加法运算",
      "chapterNumber": 3.1,
      "chapterTitle": "简单加法",
      "chapterContent": "本节学习简单的加法运算..."
    }
  ],
  "timestamp": "2024-01-01T12:00:00"
}
```

**响应数据字段说明**:
| 字段名 | 类型 | 描述 |
|--------|------|------|
| sectionNumber | Integer | 章节编号 |
| sectionTitle | String | 章节标题 |
| chapterNumber | Float | 章节序号（支持小数，如1.1, 1.2等） |
| chapterTitle | String | 章节所属章标题 |
| chapterContent | String | 章节内容 |

**错误响应**:

```json
{
  "code": 400,
  "message": "参数验证失败或查询失败的具体原因",
  "data": null,
  "timestamp": "2024-01-01T12:00:00"
}
```

**学期枚举值说明**:
| 枚举值 | 中文名称 | 描述 |
|--------|----------|------|
| SEMESTER_1 | 第一学期 | 第一学期 |
| SEMESTER_2 | 第二学期 | 第二学期 |

**注意事项**:
- 前端传递枚举值（如 SEMESTER_1），后端会自动转换为对应的中文名称（第一学期）进行数据库查询
- chapterNumber 字段支持浮点数，可以表示更细粒度的章节划分（如 1.1, 1.2, 2.1 等）

---

## 4. 状态码说明

| 状态码 | 描述                                |
| ------ | ----------------------------------- |
| 200    | 请求成功                            |
| 400    | 请求参数错误或业务逻辑错误          |
| 401    | 未授权访问（需要登录或 token 无效） |
| 403    | 禁止访问（权限不足）                |
| 404    | 资源不存在                          |
| 500    | 服务器内部错误                      |

## 5. 认证说明

### JWT Token 认证

- 登录成功后会返回 JWT token
- 需要认证的接口必须在请求头中携带 token：`Authorization: Bearer {token}`
- Token 有效期为 1 小时（3600 秒）
- Token 过期后需要重新登录获取新的 token

### 请求头示例

```
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c
```
