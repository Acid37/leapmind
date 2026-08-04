# LeapMind - AI 教育平台

面向未来的 AI 互动教育产品，支持 PPT 智能课件生成、AI 语音讲解、实时互动问答。

## 📂 项目结构

| 目录 | 技术栈 | 说明 |
|------|--------|------|
| [aitutor-frontend](aitutor-frontend/) | React + Vite | 前端界面 |
| [aitutor-backend-java](aitutor-backend-java/) | Spring Boot 3 + MyBatis-Plus | Java 后端主服务 |
| [aitutor-backend-python](aitutor-backend-python/) | FastAPI + Python | Python AI 服务 |

## 🚀 快速启动

### 1. Java 后端

**前置要求**：JDK 17+、Maven 3.9+、MySQL 8.0

```bash
cd aitutor-backend-java
# 需要本地 MySQL，先创建数据库
# CREATE DATABASE IF NOT EXISTS `leapmind-voice` DEFAULT CHARSET utf8mb4;
# Flyway 会自动建表
mvn spring-boot:run
```

启动后访问：
- 后端地址：http://localhost:8080
- **API 文档 (Knife4j)**：http://localhost:8080/doc.html

### 2. 前端

**前置要求**：Node.js 18+

```bash
cd aitutor-frontend
npm install
npm run dev
```

启动后访问：http://localhost:5173

### 3. Python AI 服务

**前置要求**：Python 3.11+、uv

```bash
# 安装 uv（Python 包管理器）
pip install uv

cd aitutor-backend-python

# 配置环境变量（首次运行必须）
cp .env.example .env
# 编辑 .env，至少填写一个 AI Provider 的 API Key（如 OPENAI_API_KEY）

# 安装依赖
uv sync

# 启动服务
python run.py
```

启动后访问：
- Web 界面：http://localhost:8000/web
- API 文档：http://localhost:8000/docs

> ⚠️ Python 服务依赖较重（torch、transformers 等），首次 `uv sync` 可能需要较长时间。如不需要 AI PPT 生成功能，可暂不启动。

### 服务端口汇总

| 服务 | 端口 | 文档地址 |
|------|------|----------|
| Java 后端 | 8080 | http://localhost:8080/doc.html |
| Python AI | 8000 | http://localhost:8000/docs |
| React 前端 | 5173 | http://localhost:5173 |


