# 管理员后台管理系统

这是一个基于HTML、CSS和JavaScript开发的管理员后台管理系统，用于管理用户、阶段和课程数据。

## 功能特性

### 🔐 认证系统
- 管理员登录验证
- JWT Token认证
- 自动登录状态检查

### 👥 用户管理
- 用户列表查看
- 添加新用户
- 编辑用户信息
- 删除用户
- 按姓名搜索用户
- 按教育阶段筛选用户

### 📚 阶段管理
- 查看所有教育阶段
- 阶段统计信息
- 年级统计信息
- 阶段详情查看
- 年级列表管理

### 📖 课程管理
- 课程列表管理
- 添加新课程
- 编辑课程信息
- 删除课程
- 按阶段搜索课程
- 按学科搜索课程

## 技术栈

- **前端**: HTML5, CSS3, JavaScript (ES6+)
- **后端API**: Spring Boot REST API
- **认证**: JWT Token
- **样式**: 自定义CSS (无第三方框架)

## 设计特点

- ✅ 响应式设计，支持移动端
- ✅ 简洁的界面设计，避免蓝紫色调
- ✅ 最小化圆角使用
- ✅ 模块化JavaScript代码
- ✅ 统一的错误处理
- ✅ 友好的用户交互

## 文件结构

```
admin-system/
├── index.html          # 主页面
├── css/
│   └── style.css       # 样式文件
├── js/
│   ├── api.js          # API接口封装
│   ├── auth.js         # 认证管理
│   ├── users.js        # 用户管理
│   ├── stages.js       # 阶段管理
│   ├── courses.js      # 课程管理
│   └── main.js         # 主应用程序
└── README.md           # 说明文档
```

## 使用说明

### 1. 环境准备

确保后端API服务正在运行，默认地址为 `http://localhost:8080`

### 2. 配置API地址

如需修改API地址，请编辑 `js/api.js` 文件中的 `baseURL` 配置：

```javascript
this.baseURL = 'http://your-api-server:port/api';
```

### 3. 管理员账户

系统预设的管理员用户名包括：
- admin
- administrator

### 4. 启动系统

直接在浏览器中打开 `index.html` 文件即可使用。

## API接口说明

### 认证接口
- `POST /api/auth/login` - 用户登录

### 用户管理接口
- `GET /api/admin/users` - 获取用户列表
- `POST /api/admin/users` - 创建用户
- `GET /api/admin/users/{id}` - 获取用户详情
- `PUT /api/admin/users/{id}` - 更新用户
- `DELETE /api/admin/users/{id}` - 删除用户
- `GET /api/admin/users/search/name` - 按姓名搜索
- `GET /api/admin/users/search/stage` - 按阶段搜索

### 阶段管理接口
- `GET /api/admin/stages` - 获取阶段列表
- `GET /api/admin/stages/{code}` - 获取阶段详情
- `GET /api/admin/stages/statistics` - 获取阶段统计
- `GET /api/admin/grades` - 获取年级列表
- `GET /api/admin/grades/statistics` - 获取年级统计

### 课程管理接口
- `GET /api/admin/courses` - 获取课程列表
- `POST /api/admin/courses` - 创建课程
- `GET /api/admin/courses/{id}` - 获取课程详情
- `PUT /api/admin/courses/{id}` - 更新课程
- `DELETE /api/admin/courses/{id}` - 删除课程
- `GET /api/admin/courses/search/stage` - 按阶段搜索
- `GET /api/admin/courses/search/subject` - 按学科搜索

## 浏览器兼容性

- Chrome 60+
- Firefox 55+
- Safari 12+
- Edge 79+

## 注意事项

1. 所有管理员接口都需要JWT Token认证
2. 系统会自动处理Token过期和重新登录
3. 建议在HTTPS环境下使用以确保安全性
4. 大量数据时建议实现分页功能

## 自定义扩展

### 添加新的管理模块

1. 在 `js/` 目录下创建新的管理器文件
2. 在 `index.html` 中添加对应的导航和内容区域
3. 在 `main.js` 中注册新的管理器
4. 在 `api.js` 中添加相应的API方法

### 修改样式

所有样式都在 `css/style.css` 中定义，可以根据需要进行自定义修改。

## 许可证

MIT License