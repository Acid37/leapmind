# 课程导航功能实现说明

## 功能概述
实现了从课程列表页（TemHomePage）点击具体课程后跳转到课程详情页（LecturePage2）的功能。

## 修改文件

### 1. `src/pages/TemHomePage.jsx`
**修改内容：**
- 添加 `onEnterProject` 回调参数到组件 props
- 为每个课程数据添加 `courseId` 字段（构造规则：`学科_年级_学期_章节号_小节号`）
- 为课程卡片添加点击事件处理
- 添加 `cursor-pointer` 样式使卡片显示为可点击状态

**关键代码：**
```jsx
// 组件参数
export default function LearningApp({ onOpenProfile, onEnterProject }) {

// 直接使用后端返回的 courseId
courseId: item.courseId,
subject: item.subject,
sectionContent: item.sectionContent,

// 点击事件
onClick={() => {
  if (onEnterProject && section.courseId) {
    console.log('进入课程:', section.courseId, section)
    onEnterProject(section.courseId)
  } else {
    showFeatureToast('课程ID缺失，无法进入课程')
  }
}}
```

### 2. `src/App.jsx`
**修改内容：**
- 状态变量 `currentProjectId` → `currentCourseId`
- 传递 `courseId` 给 `LecturePage2` 组件
- 将 `onEnterProject` 回调传递给 `TemHomePage`

**关键代码：**
```jsx
const [currentCourseId, setCurrentCourseId] = useState('');

// 条件渲染
currentCourseId ? (
  <LecturePage2 courseId={currentCourseId} />
) : (
  <TemHomePage 
    onEnterProject={(courseId) => setCurrentCourseId(courseId)}
    onOpenProfile={handleOpenProfile}
  />
)
```

## 课程ID来源

课程ID **直接来自后端返回的数据**，格式由后端统一管理。

**后端返回数据示例：**
```json
{
  "code": 200,
  "message": "获取课本章节成功",
  "data": [
    {
      "courseId": "Grade1FirstSemesterChinese000001",
      "subject": "语文",
      "sectionNumber": 1.1,
      "sectionTitle": "入学教育",
      "chapterNumber": 1,
      "chapterTitle": "我上学了",
      "sectionContent": "学习汉语拼音，认识简单汉字，培养阅读兴趣"
    },
    {
      "courseId": "Grade1FirstSemesterChinese000002",
      "subject": "语文",
      "sectionNumber": 2.1,
      "sectionTitle": "声母认识",
      "chapterNumber": 2,
      "chapterTitle": "汉语拼音",
      "sectionContent": "学习声母、韵母、整体认读音节"
    }
  ]
}
```

**courseId 格式规则**（由后端定义）：
- 格式：`Grade{年级}{学期}{学科}{序号}`
- 示例：`Grade1FirstSemesterChinese000001`
  - `Grade1` - 一年级
  - `FirstSemester` - 第一学期
  - `Chinese` - 语文
  - `000001` - 序号

## 数据流程

```
用户点击课程卡片
    ↓
TemHomePage 触发 onClick
    ↓
调用 onEnterProject(courseId)
    ↓
App.jsx 接收 courseId
    ↓
setCurrentCourseId(courseId)
    ↓
条件渲染切换到 LecturePage2
    ↓
LecturePage2 接收 courseId prop
    ↓
SlideViewer 使用 courseId 加载课程数据
```

## 用户体验改进

1. **视觉反馈**：课程卡片添加了 `cursor-pointer` 样式，鼠标悬停时显示手型光标
2. **错误处理**：如果课程ID缺失，显示友好的提示信息
3. **调试信息**：点击时在控制台输出课程ID和完整数据，便于开发调试

## 后续优化建议

1. ✅ **后端集成**：后端已提供标准的 `courseId` 字段
2. **返回按钮**：在 `LecturePage2` 中添加返回课程列表的按钮
3. **状态持久化**：考虑将当前课程ID保存到 localStorage，刷新页面后可恢复
4. **路由管理**：未来可考虑使用 React Router 进行更规范的路由管理
5. **课程元数据传递**：当前已传递 courseId，后续可根据需要传递更多元数据
6. **加载状态**：在跳转到课程页面时添加加载动画

## 测试检查清单

- [x] 点击课程卡片能正常跳转到课程页面
- [x] courseId 正确传递给 LecturePage2
- [x] 课程卡片显示鼠标手型光标
- [ ] LecturePage2 能根据 courseId 正确加载课程数据
- [ ] 后端 API 支持通过 courseId 获取课程详情
- [ ] 错误情况下显示友好提示

