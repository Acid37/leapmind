# 从 sessionId 到 course_id 的迁移说明

## 概述
本次更新将前端所有与后端交互的标识符从 `sessionId` 统一改为 `course_id`，以提高代码可读性并与后端命名保持一致。

## 主要修改文件

### 1. 核心状态管理
- **`src/features/chat/pptState.js`**
  - `currentSessionId` → `currentCourseId`
  
- **`src/features/chat/pptSession.js`**
  - `getOrCreateSessionId()` → `getOrCreateCourseId()`
  - `setSessionId()` → `setCourseId()`
  - localStorage 键: `voiceChatSessionId` → `currentCourseId`
  - ID 生成前缀: `sess_` → `course_`
  - 保留向后兼容别名（旧函数名仍可用）

### 2. API 接口层
- **`src/features/chat/pptApi.js`**
  - 所有函数参数 `sessionId` → `courseId`
  - `fetchSegments(courseId, pageNumber)`
  - `fetchMergedAudio(courseId, pageNumber)`
  - `askQuestion(courseId, question)` - 请求体字段改为 `course_id`
  - `synthesizeSpeech(courseId, text)` - 请求体字段改为 `course_id`

### 3. 加载器与控制器
- **`src/features/chat/pptLoader.js`**
  - 参数名统一为 `courseId`
  - 支持从 `courseId` 或 `sessionId` DOM 元素读取（兼容旧 HTML）
  - 错误提示: "请输入会话ID" → "请输入课程ID"
  - `state.currentSessionId` → `state.currentCourseId`

- **`src/features/chat/pptController.js`**
  - UI 提示文案: "请先输入会话ID和页码" → "请先输入课程ID和页码"

### 4. UI 组件
- **`src/components/lecture/SlideViewer.jsx`**
  - 导入: `getOrCreateSessionId, setSessionId` → `getOrCreateCourseId, setCourseId`
  - localStorage 读取键: `voiceChatSessionId` → `currentCourseId`
  - 状态访问: `playerState.currentSessionId` → `playerState.currentCourseId`
  - 错误提示更新为 "缺少有效 course_id"

- **`src/components/teacher/TeacherPanel.jsx`**
  - 所有 `state.currentSessionId` → `state.currentCourseId`
  - 局部变量 `sessionId` → `courseId`

- **`src/pages/LecturePage.jsx` & `LecturePage2.jsx`**
  - 组件参数支持 `courseId` 和 `projectId`（向后兼容）
  - Header 显示文案改为 "课程 {courseId}"

### 5. 语音交互
- **`src/features/chat/voiceInterruptionHandler.js`**
  - 中断上下文字段: `sessionId` → `courseId`
  - 向后兼容：同时检查 `courseId` 和 `sessionId`

- **`src/features/chat/pptVoiceControl.js`**
  - 中断上下文: `sessionId: state.currentSessionId` → `courseId: state.currentCourseId`

### 6. 项目列表与批量合成
- **`src/pages/ProjectListPage.jsx`**
  - 导入: `setSessionId` → `setCourseId`
  - 后端响应处理优先读取 `res.course_id`，回退到 `res.sessionId`（兼容）

- **`src/features/chat/pptBulkSynthesis.js`**
  - `showSessionId()` → `showCourseId()`
  - `copySessionId()` → `copyCourseId()`
  - 成功提示: "会话ID" → "课程ID"
  - 保留向后兼容别名

## 后端接口对应关系

### 幻灯片数据
```
GET /api/courses/{courseId}/slides-data
```

### 语音相关
```
GET  /api/speech/ppt/{courseId}/page/{pageNumber}
GET  /api/speech/ppt/{courseId}/page/{pageNumber}/audio
POST /api/voice-chat/ask
     Body: { course_id: string, question: string }
POST /api/voice-chat/synthesize
     Body: { course_id: string, text: string }
```

### 批量合成
```
POST /api/speech/bulk-synthesis
     Response: { course_id: string, ... }
```

## localStorage 存储键变更

| 旧键名 | 新键名 | 说明 |
|--------|--------|------|
| `voiceChatSessionId` | `currentCourseId` | 当前课程ID |
| `projectVoiceSessions` | （已移除） | 不再需要项目映射 |

## 向后兼容性

为确保平滑过渡，以下措施已实施：

1. **函数别名**: `pptSession.js` 中保留 `getOrCreateSessionId` 和 `setSessionId` 作为新函数的别名
2. **DOM 元素兼容**: `pptLoader.js` 同时支持读取 `courseId` 和 `sessionId` 输入框
3. **后端响应兼容**: `ProjectListPage.jsx` 优先读取 `course_id`，回退到 `sessionId`
4. **中断上下文兼容**: `voiceInterruptionHandler.js` 同时检查两个字段

## 测试检查清单

- [ ] 幻灯片加载与轮询正常
- [ ] 音频播放功能正常
- [ ] 语音问答交互正常
- [ ] 语音打断与恢复正常
- [ ] 批量合成触发与 course_id 存储正常
- [ ] 项目列表进入课程正常
- [ ] localStorage 正确存储 `currentCourseId`
- [ ] 所有 UI 提示文案已更新

## 注意事项

1. **清理旧数据**: 用户首次使用新版本时，旧的 `voiceChatSessionId` 不会自动迁移到 `currentCourseId`，需要重新触发合成
2. **后端同步**: 确保后端已将所有接口的 `sessionId` 改为 `course_id`
3. **API 文档**: 需要同步更新 API 文档中的参数名称

## 回滚方案

如需回滚，可以：
1. 恢复 `pptSession.js` 中的主函数名
2. 恢复 localStorage 键名
3. 恢复 API 请求体字段名
4. 恢复 UI 文案

所有向后兼容别名可在确认稳定后移除。

