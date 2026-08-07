import { get } from './api'

/**
 * Mock 知识点数据
 */
const MOCK_KNOWLEDGE_POINTS = {
  math: [
    { id: 10, name: '勾股定理', subject: 'math', grade: 'grade_8' },
    { id: 11, name: '相似三角形', subject: 'math', grade: 'grade_8' },
    { id: 12, name: '全等三角形', subject: 'math', grade: 'grade_8' },
    { id: 13, name: '一次函数', subject: 'math', grade: 'grade_8' },
    { id: 14, name: '反比例函数', subject: 'math', grade: 'grade_9' },
    { id: 15, name: '二次函数', subject: 'math', grade: 'grade_9' },
    { id: 16, name: '一元二次方程', subject: 'math', grade: 'grade_9' },
    { id: 17, name: '圆', subject: 'math', grade: 'grade_9' },
    { id: 18, name: '实数', subject: 'math', grade: 'grade_7' },
    { id: 19, name: '整式运算', subject: 'math', grade: 'grade_7' },
    { id: 20, name: '概率初步', subject: 'math', grade: 'grade_9' },
  ],
  physics: [
    { id: 30, name: '光的反射', subject: 'physics', grade: 'grade_8' },
    { id: 31, name: '凸透镜成像', subject: 'physics', grade: 'grade_8' },
    { id: 32, name: '牛顿第一定律', subject: 'physics', grade: 'grade_8' },
    { id: 33, name: '压强', subject: 'physics', grade: 'grade_8' },
    { id: 34, name: '浮力', subject: 'physics', grade: 'grade_9' },
    { id: 35, name: '简单机械', subject: 'physics', grade: 'grade_9' },
  ],
  english: [
    { id: 40, name: '一般现在时', subject: 'english', grade: 'grade_7' },
    { id: 41, name: '现在进行时', subject: 'english', grade: 'grade_7' },
    { id: 42, name: '一般过去时', subject: 'english', grade: 'grade_8' },
    { id: 43, name: '现在完成时', subject: 'english', grade: 'grade_9' },
    { id: 44, name: '被动语态', subject: 'english', grade: 'grade_9' },
  ],
  chinese: [
    { id: 50, name: '记叙文阅读', subject: 'chinese', grade: 'grade_7' },
    { id: 51, name: '说明文阅读', subject: 'chinese', grade: 'grade_8' },
    { id: 52, name: '议论文阅读', subject: 'chinese', grade: 'grade_9' },
    { id: 53, name: '文言文虚词', subject: 'chinese', grade: 'grade_8' },
    { id: 54, name: '古诗词鉴赏', subject: 'chinese', grade: 'grade_9' },
  ],
  chemistry: [
    { id: 60, name: '化学方程式', subject: 'chemistry', grade: 'grade_9' },
    { id: 61, name: '质量守恒定律', subject: 'chemistry', grade: 'grade_9' },
    { id: 62, name: '溶液', subject: 'chemistry', grade: 'grade_9' },
    { id: 63, name: '酸碱盐', subject: 'chemistry', grade: 'grade_9' },
  ],
  biology: [
    { id: 70, name: '细胞结构', subject: 'biology', grade: 'grade_7' },
    { id: 71, name: '光合作用', subject: 'biology', grade: 'grade_7' },
    { id: 72, name: '遗传与变异', subject: 'biology', grade: 'grade_8' },
  ],
}

// ===================================================================
// 真实接口封装
// ===================================================================

/**
 * 获取薄弱点列表（M3接口）
 * GET /api/weak-points?userId={userId}
 * 文档: weak-points-api(3).json WP-001
 */
export async function getWeakPoints(userId) {
  const res = await get(`/api/weak-points`, { userId })
  return res.data
}

/**
 * 备课生成（SSE 流式）— 真实接口
 * POST /api/lesson-prep/generate
 * 文档：lesson-prep-generate.md
 * 请求体（camelCase）：{ userId, title, subject, grade, knowledgePointIds, teachingGoals, totalHours, style, weakPointIds, userProfileSummary, parallel }
 * SSE 事件（统一 data: 行，type 字段区分）：
 *   syllabusChunk / outline / slide / slidesDone / narration / warn / finalCheck / done / error
 */
export async function generateLessonPrep(params, onEvent) {
  try {
    const res = await fetch('/api/lesson-prep/generate', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        userId: params.userId,
        title: params.title,
        subject: params.subject,
        grade: params.grade,
        knowledgePointIds: params.knowledgePointIds || [],
        teachingGoals: params.teachingGoals || [],
        totalHours: params.totalHours || 1,
        style: params.style || 'standard',
        weakPointIds: params.weakPointIds || [],
        userProfileSummary: params.userProfileSummary || null,
        parallel: params.parallel || false,
      }),
    })
    const reader = res.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop() || ''
      for (const line of lines) {
        const trimmed = line.trim()
        if (!trimmed.startsWith('data:')) continue
        try {
          const data = JSON.parse(trimmed.slice(5).trim())
          if (data.type === 'error') {
            onEvent('error', data)
            return
          }
          onEvent(data.type, data)
        } catch { /* 忽略解析失败的行 */ }
      }
    }
  } catch (err) {
    onEvent('error', { stage: 'global', message: err.message || '备课生成失败' })
  }
}

// ===================================================================
// Mock 实现
// ===================================================================

/**
 * Mock 获取知识点列表（按科目）
 */
export async function mockGetKnowledgePoints(subject) {
  await new Promise(r => setTimeout(r, 300))
  return MOCK_KNOWLEDGE_POINTS[subject] || []
}

/**
 * Mock 获取薄弱点列表
 */
export async function mockGetWeakPoints() {
  await new Promise(r => setTimeout(r, 500))
  return [
    { id: 11, knowledgePoint: '圆的面积', subject: '数学', weaknessLevel: 'HIGH', errorCount: 9, totalCount: 15, accuracyRate: 40.0, status: 'ACTIVE' },
    { id: 8, knowledgePoint: '分数加减法', subject: '数学', weaknessLevel: 'HIGH', errorCount: 8, totalCount: 12, accuracyRate: 33.33, status: 'ACTIVE' },
    { id: 5, knowledgePoint: '勾股定理', subject: '数学', weaknessLevel: 'MEDIUM', errorCount: 5, totalCount: 18, accuracyRate: 72.22, status: 'IMPROVING' },
    { id: 7, knowledgePoint: '一般过去时', subject: '英语', weaknessLevel: 'MEDIUM', errorCount: 6, totalCount: 14, accuracyRate: 57.14, status: 'ACTIVE' },
    { id: 9, knowledgePoint: '凸透镜成像', subject: '物理', weaknessLevel: 'LOW', errorCount: 3, totalCount: 10, accuracyRate: 70.0, status: 'ACTIVE' },
  ]
}

/**
 * Mock AI 自动生成教学目标
 */
export async function mockAIGenerateGoals(knowledgePointNames) {
  await new Promise(r => setTimeout(r, 1500))
  const defaultGoals = [
    '理解并掌握核心概念',
    '能运用相关公式进行正确计算',
    '能解决实际应用题',
    '培养逻辑推理能力',
  ]
  if (knowledgePointNames.length > 0) {
    return [
      `理解「${knowledgePointNames[0]}」的核心概念`,
      `掌握「${knowledgePointNames[0]}」的解题方法`,
      `能运用所学知识解决实际问题`,
    ]
  }
  return defaultGoals
}

/**
 * Mock 备课生成（SSE 流式模拟）
 * POST /api/lesson-prep/generate
 */
export async function mockGenerateLessonPrep(params, onEvent) {
  const mockSyllabusChunks = [
    '{\n  "title": "' + params.title + '",\n',
    '  "total_hours": ' + params.totalHours + ',\n',
    '  "sections": [\n',
    '    {\n',
    '      "hour_index": 1,\n',
    '      "title": "第一课时：基础知识",\n',
    '      "core_content": "核心概念与基本方法",\n',
    '      "key_points": ["概念定义", "基本公式", "典型例题"]\n',
    '    },\n',
    '    {\n',
    '      "hour_index": 2,\n',
    '      "title": "第二课时：综合应用",\n',
    '      "core_content": "综合题型与解题技巧",\n',
    '      "key_points": ["综合题型", "解题技巧", "易错点分析"]\n',
    '    }\n',
    '  ]\n',
    '}\n',
  ]

  const mockSlides = [
    { pageNum: 1, type: 'cover', title: params.title, bulletPoints: [`${params.grade} · ${params.subject}`, params.title], imageSuggestion: '封面图' },
    { pageNum: 2, type: 'content', title: '教学目标', bulletPoints: params.teachingGoals, imageSuggestion: '' },
    { pageNum: 3, type: 'content', title: '知识回顾', bulletPoints: ['温故知新，回顾相关旧知'], imageSuggestion: '' },
    { pageNum: 4, type: 'content', title: '新课讲授（一）', bulletPoints: ['核心概念讲解', '公式推导', '典型例题分析'], formula: 'a^2 + b^2 = c^2' },
    { pageNum: 5, type: 'interactive', title: '课堂互动', bulletPoints: ['想一想：这个公式还能怎么用？'], interaction: { type: 'think_question', question: '请思考生活中哪些场景用到了这个知识点？' } },
    { pageNum: 6, type: 'content', title: '新课讲授（二）', bulletPoints: ['进阶应用', '变式训练'], imageSuggestion: '示意图' },
    { pageNum: 7, type: 'summary', title: '课堂总结', bulletPoints: ['本节课重点回顾', '知识框架梳理'], imageSuggestion: '' },
    { pageNum: 8, type: 'homework', title: '课后作业', bulletPoints: ['基础题：1-3题', '提高题：4-5题', '拓展题：第6题'], imageSuggestion: '' },
  ]

  // Stage 1: syllabus_chunk（逐 token 模拟）
  for (const chunk of mockSyllabusChunks) {
    await new Promise(r => setTimeout(r, 100))
    onEvent('syllabus_chunk', { chunk })
  }

  await new Promise(r => setTimeout(r, 300))

  // syllabus_done
  onEvent('syllabus_done', {
    syllabus: {
      title: params.title,
      totalHours: params.totalHours,
      sections: [
        {
          hourIndex: 1, title: '第一课时：基础知识', coreContent: '核心概念与基本方法',
          teachingGoals: params.teachingGoals, keyPoints: ['概念定义', '基本公式', '典型例题'],
          difficultPoints: ['易混淆概念辨析'], teachingProcess: [
            { step: '课堂导入', duration: '5min', teacherActivity: '通过实际问题引入', studentActivity: '思考并回答', designIntent: '激发兴趣' },
            { step: '新知讲授', duration: '20min', teacherActivity: '讲解核心概念', studentActivity: '记笔记并提问', designIntent: '掌握新知' },
            { step: '巩固练习', duration: '10min', teacherActivity: '布置练习并巡视', studentActivity: '独立完成练习', designIntent: '及时巩固' },
            { step: '课堂小结', duration: '5min', teacherActivity: '总结本节课内容', studentActivity: '回顾整理', designIntent: '形成体系' },
          ],
          homework: { basic: ['课后习题1-3'], advanced: ['习题4'], optional: ['思考题'] },
        },
        {
          hourIndex: 2, title: '第二课时：综合应用', coreContent: '综合题型与解题技巧',
          teachingGoals: ['能解决综合题型', '掌握解题技巧'], keyPoints: ['综合题型', '解题技巧', '易错点分析'],
          difficultPoints: ['综合题的多步骤推理'], teachingProcess: [
            { step: '复习导入', duration: '5min', teacherActivity: '回顾上节课内容', studentActivity: '回答问题', designIntent: '温故知新' },
            { step: '例题精讲', duration: '20min', teacherActivity: '讲解综合例题', studentActivity: '跟随思路', designIntent: '举一反三' },
            { step: '变式训练', duration: '10min', teacherActivity: '出示变式题', studentActivity: '小组讨论', designIntent: '灵活运用' },
            { step: '总结提升', duration: '5min', teacherActivity: '归纳解题方法', studentActivity: '整理笔记', designIntent: '能力提升' },
          ],
          homework: { basic: ['课后习题5-7'], advanced: ['习题8-9'], optional: ['实践题'] },
        },
      ],
    },
    sectionsCount: params.totalHours,
  })

  await new Promise(r => setTimeout(r, 500))

  // Stage 2: slide 逐页
  for (const slide of mockSlides) {
    await new Promise(r => setTimeout(r, 200))
    onEvent('slide', { pageNum: slide.pageNum, totalPages: '?', slide })
  }

  await new Promise(r => setTimeout(r, 300))

  // slides_done
  onEvent('slides_done', { totalPages: mockSlides.length })

  await new Promise(r => setTimeout(r, 500))

  // Stage 3: narration
  for (const slide of mockSlides) {
    await new Promise(r => setTimeout(r, 150))
    onEvent('narration', {
      pageNum: slide.pageNum,
      totalPages: mockSlides.length,
      narration_text: `同学们好，我们来看第${slide.pageNum}页：${slide.title}。${slide.bulletPoints.join('，')}。`,
      estimated_duration_seconds: 45,
      quality_score: 0.9,
      needs_review: false,
    })
  }

  await new Promise(r => setTimeout(r, 200))

  // final_check
  onEvent('final_check', { passed: true, overallScore: 0.88, warnings: [] })

  // done
  onEvent('done', { prep_id: Date.now(), total_pages: mockSlides.length, total_duration_seconds: mockSlides.length * 45 })
}

// ===================================================================
// 备课编辑页：真实接口封装 + Mock
// ===================================================================

/**
 * 获取备课详情
 * 对接人：苏杰（M5 Java）- GET /api/lesson-prep/contents/{prepId}
 * 文档：M5 AI备课--PPT模板和备课内容管理(1).md 2.2
 * 注意：文档中该接口只返回 title/status/pptStructure/templateId，
 * 不含 syllabus。若后端已扩展返回 syllabus 则直接用真实接口。
 */
export async function getLessonPrepDetail(prepId, userId) {
  const res = await get(`/api/lesson-prep/contents/${prepId}`, { userId })
  return res.data
}

/**
 * 更新备课内容
 * 对接人：苏杰（M5 Java）- PUT /api/lesson-prep/contents/{prepId}
 * 文档：M5 AI备课--PPT模板和备课内容管理(1).md 2.3
 */
export async function updateLessonPrep(prepId, userId, data) {
  const query = userId ? `?userId=${userId}` : ''
  const res = await fetch(`/api/lesson-prep/contents/${prepId}${query}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data),
  })
  const json = await res.json()
  if (json.code === 200) return json.data
  throw new Error(json.message || '更新备课失败')
}

/**
 * 基于已有备课生成PPT结构（非流式 JSON）
 * 对接人：王佳雯（M5 Java）- POST /api/lesson-prep/generate-ppt
 * 文档：lesson-prep-generate-ppt.md
 * 请求: { prepId, templateStyle, maxSlides }
 * 响应: { pptId, slides: SlideData[] }
 */
export async function generatePpt(prepId, options = {}) {
  const res = await fetch('/api/lesson-prep/generate-ppt', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ prepId, templateStyle: options.templateStyle || 'default', maxSlides: options.maxSlides || 20 }),
  })
  const json = await res.json()
  if (json.code === 200 || json.pptId) return json
  throw new Error(json.message || '生成PPT失败')
}

/**
 * Mock AI 生成教学过程
 */
export async function mockAIGenerateProcess() {
  await new Promise(r => setTimeout(r, 1500))
  return [
    { step: '课堂导入', duration: '5min', teacherActivity: '<p>通过生活实例或复习旧知导入新课，激发学生学习兴趣。</p>', studentActivity: '<p>观察实例，思考问题，主动回答。</p>', designIntent: '<p>自然过渡到新知学习，调动学生已有经验。</p>' },
    { step: '新知讲授', duration: '20min', teacherActivity: '<p>讲解核心概念，结合板书演示推导过程，设置启发式提问。</p>', studentActivity: '<p>认真听讲，记录笔记，参与课堂讨论。</p>', designIntent: '<p>让学生理解知识本质，掌握基本方法。</p>' },
    { step: '巩固练习', duration: '10min', teacherActivity: '<p>布置典型练习题，巡视学生作答情况，个别指导。</p>', studentActivity: '<p>独立完成练习，同桌互查互评。</p>', designIntent: '<p>及时巩固新知，检测掌握程度。</p>' },
    { step: '课堂小结', duration: '5min', teacherActivity: '<p>引导学生总结本节课知识框架，强调易错点。</p>', studentActivity: '<p>回顾梳理，补充完善笔记。</p>', designIntent: '<p>帮助学生构建知识体系。</p>' },
  ]
}

/**
 * Mock 获取备课详情（含 syllabus）
 */
export async function mockGetLessonPrepDetail(prepId) {
  await new Promise(r => setTimeout(r, 500))
  return {
    id: prepId,
    prepId,
    userId: 1,
    title: '勾股定理',
    status: 'draft',
    templateId: null,
    createdAt: '2026-07-25T10:00:00',
    updatedAt: '2026-07-25T10:00:00',
    syllabus: {
      title: '勾股定理',
      totalHours: 2,
      sections: [
        {
          hourIndex: 1,
          title: '第一课时：勾股定理的认识',
          coreContent: '<p>认识直角三角形，理解勾股定理内容：<strong>直角三角形两直角边的平方和等于斜边的平方</strong>。</p><p>公式：$a^2 + b^2 = c^2$</p>',
          teachingGoals: ['理解勾股定理的内容', '能识别直角三角形的斜边与直角边', '能直接用勾股定理求直角三角形的边长'],
          keyPoints: ['勾股定理公式 a²+b²=c²', '斜边是最长边', '直角边与斜边的区分'],
          difficultPoints: ['在实际图形中正确识别斜边', '灵活运用公式变形求直角边'],
          teachingProcess: [
            { step: '课堂导入', duration: '5min', teacherActivity: '<p>展示直角三角形图片，提出"已知两直角边求斜边"的问题。</p>', studentActivity: '<p>观察图形，尝试用测量的方法解决问题。</p>', designIntent: '<p>从实际问题出发，引出勾股定理。</p>' },
            { step: '新知讲授', duration: '20min', teacherActivity: '<p>讲解勾股定理定义与公式，通过拼图演示证明。</p>', studentActivity: '<p>理解定理，动手拼图验证，记笔记。</p>', designIntent: '<p>让学生理解定理的来龙去脉。</p>' },
            { step: '巩固练习', duration: '10min', teacherActivity: '<p>出示基础练习：已知两直角边求斜边。</p>', studentActivity: '<p>独立完成计算，展示解题过程。</p>', designIntent: '<p>巩固公式的直接应用。</p>' },
            { step: '课堂小结', duration: '5min', teacherActivity: '<p>总结勾股定理内容，强调斜边识别。</p>', studentActivity: '<p>回顾总结，完成知识框架。</p>', designIntent: '<p>帮助学生形成知识体系。</p>' },
          ],
          homework: { basic: ['课本习题第1-3题'], advanced: ['第4题：逆定理判断'], optional: ['思考题：构造直角三角形应用'] },
        },
        {
          hourIndex: 2,
          title: '第二课时：勾股定理的应用',
          coreContent: '<p>掌握勾股定理在实际问题中的应用，包括<strong>求高、求距离、折叠问题</strong>等。</p>',
          teachingGoals: ['能运用勾股定理解决实际应用题', '掌握勾股定理的变形公式'],
          keyPoints: ['勾股定理变形：a²=c²-b²', '实际问题的建模'],
          difficultPoints: ['从实际问题中抽象出直角三角形', '多步推理综合题'],
          teachingProcess: [
            { step: '复习导入', duration: '5min', teacherActivity: '<p>回顾勾股定理，快速口算练习。</p>', studentActivity: '<p>口算回答，复习旧知。</p>', designIntent: '<p>温故知新，为应用做准备。</p>' },
            { step: '例题精讲', duration: '20min', teacherActivity: '<p>讲解实际应用题，示范建模过程。</p>', studentActivity: '<p>跟随思路，理解建模方法。</p>', designIntent: '<p>掌握实际问题转化方法。</p>' },
            { step: '变式训练', duration: '10min', teacherActivity: '<p>出示变式题，组织小组讨论。</p>', studentActivity: '<p>小组合作完成变式练习。</p>', designIntent: '<p>举一反三，灵活运用。</p>' },
            { step: '总结提升', duration: '5min', teacherActivity: '<p>归纳勾股定理应用的三步法。</p>', studentActivity: '<p>整理笔记，总结方法。</p>', designIntent: '<p>提升解题能力。</p>' },
          ],
          homework: { basic: ['课后习题第5-7题'], advanced: ['第8题：折叠问题'], optional: ['探究题：勾股树'] },
        },
      ],
    },
  }
}

/**
 * Mock 保存备课
 */
export async function mockUpdateLessonPrep(prepId, data) {
  await new Promise(r => setTimeout(r, 600))
  return { success: true, message: '保存成功' }
}

/**
 * Mock 生成PPT
 */
export async function mockGeneratePpt(prepId) {
  await new Promise(r => setTimeout(r, 1200))
  const slides = [
    { pageNum: 1, type: 'cover', title: '勾股定理', bulletPoints: ['八年级数学', '勾股定理及其应用'], imageSuggestion: '直角三角形示意图', formula: '', highlightPoints: ['勾股定理'], interaction: null },
    { pageNum: 2, type: 'content', title: '教学目标', bulletPoints: ['理解勾股定理的内容', '能识别斜边与直角边', '能运用勾股定理解题'], imageSuggestion: '', formula: '', highlightPoints: [], interaction: null },
    { pageNum: 3, type: 'content', title: '勾股定理定义', bulletPoints: ['直角三角形两直角边的平方和等于斜边的平方', '斜边是最长边'], imageSuggestion: '标注abc的直角三角形', formula: 'a^2 + b^2 = c^2', highlightPoints: ['a²+b²=c²'], interaction: null },
    { pageNum: 4, type: 'interactive', title: '课堂互动', bulletPoints: ['已知两直角边为3和4，求斜边'], imageSuggestion: '', formula: '', highlightPoints: [], interaction: { type: 'choice_question', question: '斜边的长度是多少？', options: ['A. 5', 'B. 6', 'C. 7', 'D. 8'], answer: 'A' } },
    { pageNum: 5, type: 'content', title: '勾股定理的应用', bulletPoints: ['求高', '求距离', '折叠问题'], imageSuggestion: '实际应用场景图', formula: '', highlightPoints: ['实际应用'], interaction: null },
    { pageNum: 6, type: 'summary', title: '课堂总结', bulletPoints: ['勾股定理公式 a²+b²=c²', '识别斜边是关键', '实际问题的建模方法'], imageSuggestion: '', formula: 'a^2 + b^2 = c^2', highlightPoints: ['a²+b²=c²'], interaction: null },
    { pageNum: 7, type: 'homework', title: '课后作业', bulletPoints: ['课本习题第1-7题', '探究题：勾股树', '预习下节内容'], imageSuggestion: '', formula: '', highlightPoints: [], interaction: null },
  ]
  return { pptId: prepId, slides }
}

// ===================================================================
// PPT 编辑页：模板 / 导出 / 应用模板
// ===================================================================

/**
 * 获取PPT模板列表
 * 对接人：苏杰（M5 Java）- GET /api/lesson-prep/templates?userId=
 * 文档：M5 AI备课--PPT模板和备课内容管理(1).md 1.1
 */
export async function getPptTemplates(userId) {
  const res = await get(`/api/lesson-prep/templates`, { userId })
  return res.data || []
}

/**
 * 导出PPTX
 * 对接人：王佳雯（M5 Java）- POST /api/lesson-prep/contents/{prepId}/export-ppt
 * 文档：M5 AI备课--PPT模板和备课内容管理(1).md 2.5（当前桩实现返回占位URL）
 * 返回：data 为下载URL
 */
export async function exportPptx(prepId) {
  const res = await fetch(`/api/lesson-prep/contents/${prepId}/export-ppt`, { method: 'POST' })
  const json = await res.json()
  if (json.code === 200) return json.data
  throw new Error(json.message || '导出PPT失败')
}

/**
 * 应用模板到备课
 * 对接人：苏杰（M5 Java）- POST /api/lesson-prep/templates/{templateId}/apply?prepId=
 * 文档：M5 AI备课--PPT模板和备课内容管理(1).md 1.5
 */
export async function applyTemplate(templateId, prepId) {
  const res = await fetch(`/api/lesson-prep/templates/${templateId}/apply?prepId=${prepId}`, { method: 'POST' })
  const json = await res.json()
  if (json.code === 200) return json.data
  throw new Error(json.message || '应用模板失败')
}

/**
 * Mock 获取PPT模板列表
 */
export async function mockGetPptTemplates() {
  await new Promise(r => setTimeout(r, 400))
  return [
    { id: 1, name: '蓝色商务风', configJson: '{"colorScheme":{"primary":"#2563EB","secondary":"#7C3AED","background":"#F5F9FF","text":"#1F2937","accent":"#F59E0B"},"fontFamily":{"title":"PingFang SC","body":"PingFang SC"},"layout":{"titleAlign":"left","contentColumns":2,"showPageNumber":true}}', previewImageUrl: '', isSystem: 1, createdAt: '2026-07-23T10:00:00' },
    { id: 2, name: '清新教育风', configJson: '{"colorScheme":{"primary":"#059669","secondary":"#0D9488","background":"#F0FDFA","text":"#0F766E","accent":"#F59E0B"},"fontFamily":{"title":"Microsoft YaHei","body":"PingFang SC"},"layout":{"titleAlign":"left","contentColumns":1,"showPageNumber":true}}', previewImageUrl: '', isSystem: 1, createdAt: '2026-07-23T10:00:00' },
    { id: 3, name: '星空暗夜风', configJson: '{"colorScheme":{"primary":"#818CF8","secondary":"#A78BFA","background":"#1E1B4B","text":"#E0E7FF","accent":"#F472B6"},"fontFamily":{"title":"Microsoft YaHei","body":"PingFang SC"},"layout":{"titleAlign":"center","contentColumns":1,"showPageNumber":false}}', previewImageUrl: '', isSystem: 1, createdAt: '2026-07-23T10:00:00' },
  ]
}

/**
 * Mock 导出PPTX
 */
export async function mockExportPptx(prepId) {
  await new Promise(r => setTimeout(r, 800))
  return `/download/lesson-prep-${prepId}.pptx`
}

/**
 * Mock 应用模板
 */
export async function mockApplyTemplate(templateId, prepId) {
  await new Promise(r => setTimeout(r, 400))
  return { success: true }
}

// ===================================================================
// 备课列表页：列表查询 / 删除
// ===================================================================

/**
 * 获取备课内容列表
 * 对接人：苏杰（M5 Java）- GET /api/lesson-prep/contents?userId=
 * 文档：M5 AI备课--PPT模板和备课内容管理(1).md 2.1
 * 注意：接口不返回 subject/totalHours，前端补充展示字段
 */
export async function getLessonPrepContents(userId, params = {}) {
  const query = new URLSearchParams({ userId, ...params }).toString()
  const res = await fetch(`/api/lesson-prep/contents?${query}`)
  const json = await res.json()
  if (json.code === 200) return json.data
  throw new Error(json.message || '获取备课列表失败')
}

/**
 * 删除备课（仅草稿可删）
 * 对接人：苏杰（M5 Java）- DELETE /api/lesson-prep/contents/{prepId}?userId=
 * 文档：M5 AI备课--PPT模板和备课内容管理(1).md 2.4
 */
export async function deleteLessonPrep(prepId, userId) {
  const res = await fetch(`/api/lesson-prep/contents/${prepId}?userId=${userId}`, { method: 'DELETE' })
  const json = await res.json()
  if (json.code === 200) return json.data
  throw new Error(json.message || '删除备课失败')
}

/**
 * 从 pptStructure 解析 PPT 页数
 */
export const parsePptSlideCount = (pptStructure) => {
  try {
    const parsed = typeof pptStructure === 'string' ? JSON.parse(pptStructure) : pptStructure
    if (parsed?.slides) return parsed.slides.length
    if (parsed?.pages) return parsed.pages.length
  } catch (e) { /* ignore */ }
  return 0
}

/**
 * Mock 备课列表数据
 */
export async function mockGetLessonPrepContents() {
  await new Promise(r => setTimeout(r, 600))
  const mockSlides = (count) => Array.from({ length: count }, (_, i) => ({
    pageNum: i + 1,
    type: i === 0 ? 'cover' : i === count - 1 ? 'homework' : 'content',
    title: i === 0 ? '勾股定理' : i === count - 1 ? '课后作业' : `内容页 ${i + 1}`,
    bulletPoints: ['要点一', '要点二', '要点三'],
    imageSuggestion: '', formula: '', highlightPoints: [], interaction: null,
  }))
  return [
    { id: 1, prepId: 1, userId: 1, title: '勾股定理', subject: '数学', totalHours: 2, status: 'published', templateId: 1, pptStructure: JSON.stringify({ slides: mockSlides(8) }), createdAt: '2026-07-25T10:00:00', updatedAt: '2026-07-25T12:00:00' },
    { id: 2, prepId: 2, userId: 1, title: '一元二次方程', subject: '数学', totalHours: 3, status: 'draft', templateId: null, pptStructure: JSON.stringify({ slides: mockSlides(6) }), createdAt: '2026-07-24T09:30:00', updatedAt: '2026-07-24T09:30:00' },
    { id: 3, prepId: 3, userId: 1, title: '凸透镜成像', subject: '物理', totalHours: 2, status: 'draft', templateId: null, pptStructure: JSON.stringify({ slides: mockSlides(5) }), createdAt: '2026-07-23T15:20:00', updatedAt: '2026-07-23T15:20:00' },
    { id: 4, prepId: 4, userId: 1, title: '一般过去时', subject: '英语', totalHours: 1, status: 'archived', templateId: 2, pptStructure: JSON.stringify({ slides: mockSlides(4) }), createdAt: '2026-07-22T11:00:00', updatedAt: '2026-07-22T11:00:00' },
    { id: 5, prepId: 5, userId: 1, title: '质量守恒定律', subject: '化学', totalHours: 2, status: 'published', templateId: 3, pptStructure: JSON.stringify({ slides: mockSlides(10) }), createdAt: '2026-07-21T14:30:00', updatedAt: '2026-07-21T16:00:00' },
  ]
}

/**
 * Mock 删除备课
 */
export async function mockDeleteLessonPrep(prepId) {
  await new Promise(r => setTimeout(r, 400))
  return { success: true }
}
