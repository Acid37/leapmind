import { get } from './api'

// ===================================================================
// 真实接口封装
// ===================================================================

/**
 * 获取薄弱点列表（M3接口）
 * GET /api/weak-points?userId={userId}&subject={subject}&page=&size=
 * 文档: m3-api-guide 3.1（分页 PageResult，data.records）
 * 兼容旧格式：若 data 是数组直接返回
 */
export async function getWeakPoints(userId, params = {}) {
  const res = await get(`/api/weak-points`, { userId, ...params })
  const data = res.data
  if (Array.isArray(data)) return data
  if (data && Array.isArray(data.records)) return data.records
  return data || []
}

/**
 * 触发AI综合分析
 * POST /api/weak-points/{userId}/analysis
 * 文档: weak-points-api(3).json WP-003
 */
export async function triggerWeakPointsAnalysis(userId) {
  const res = await fetch(`/api/weak-points/${userId}/analysis`, { method: 'POST' })
  const json = await res.json()
  if (json.code === 200) return json.data
  throw new Error(json.message || 'AI 分析失败')
}

/**
 * 获取薄弱点详情
 * GET /api/weak-points/{id}/detail
 * 文档: m3-api-guide 3.2
 */
export async function getWeakPointDetail(id, userId) {
  const res = await get(`/api/weak-points/${id}/detail`, { userId })
  return res.data
}

/**
 * 获取推荐题目（薄弱点详情页）
 * GET /api/weak-points/recommend-questions?userId=&knowledgePoint=&count=
 * 文档: m3-api-guide 3.5
 */
export async function getRecommendQuestions(userId, knowledgePoint, count = 5) {
  const res = await get(`/api/weak-points/recommend-questions`, { userId, knowledgePoint, count })
  return res.data || []
}

/**
 * 获取知识图谱
 * GET /api/weak-points/knowledge-graph?userId=&subject=
 * 文档: m3-api-guide 3.8
 */
export async function getKnowledgeGraph(userId, subject = '') {
  const params = { userId }
  if (subject) params.subject = subject
  const res = await get(`/api/weak-points/knowledge-graph`, params)
  return res.data || { nodes: [], edges: [] }
}

// ===================================================================
// Mock 实现
// ===================================================================

/**
 * Mock 获取薄弱点列表
 */
export async function mockGetWeakPoints() {
  await new Promise(r => setTimeout(r, 600))
  return [
    { id: 11, knowledgePoint: '圆的面积', subject: '数学', weaknessLevel: 'HIGH', errorCount: 9, totalCount: 15, accuracyRate: 40.0, status: 'ACTIVE', trend: 'declining', lastErrorTime: '2026-08-06T18:20:00', aiAnalysis: '圆的面积公式掌握不牢，计算中容易混淆半径与直径。', aiSuggestion: '建议先巩固圆的周长与面积公式，再做综合应用题。' },
    { id: 8, knowledgePoint: '分数加减法', subject: '数学', weaknessLevel: 'HIGH', errorCount: 8, totalCount: 12, accuracyRate: 33.33, status: 'ACTIVE', trend: 'stable', lastErrorTime: '2026-08-07T09:15:00', aiAnalysis: '通分过程容易出错，异分母分数加减法需要加强。', aiSuggestion: '多做异分母加减法专项练习，注意通分步骤。' },
    { id: 5, knowledgePoint: '勾股定理', subject: '数学', weaknessLevel: 'MEDIUM', errorCount: 5, totalCount: 18, accuracyRate: 72.22, status: 'IMPROVING', trend: 'improving', lastErrorTime: '2026-08-02T14:30:00', aiAnalysis: '基本掌握勾股定理，但在识别斜边和实际应用中有待提高。', aiSuggestion: '重点练习实际应用题型，识别直角边与斜边。', subWeakPoints: [{ kpId: 51, name: '勾股定理逆定理', weaknessLevel: 'MEDIUM' }, { kpId: 52, name: '勾股数', weaknessLevel: 'LOW' }] },
    { id: 7, knowledgePoint: '一般过去时', subject: '英语', weaknessLevel: 'MEDIUM', errorCount: 6, totalCount: 14, accuracyRate: 57.14, status: 'ACTIVE', trend: 'declining', lastErrorTime: '2026-08-07T11:40:00', aiAnalysis: '动词过去式不规则变化记忆不牢。', aiSuggestion: '整理不规则动词过去式表，配合例句记忆。' },
    { id: 9, knowledgePoint: '凸透镜成像', subject: '物理', weaknessLevel: 'LOW', errorCount: 3, totalCount: 10, accuracyRate: 70.0, status: 'ACTIVE', trend: 'improving', lastErrorTime: '2026-07-30T10:05:00', aiAnalysis: '成像规律记忆基本清晰，个别场景容易混淆。', aiSuggestion: '通过光路图辅助理解成像规律。' },
    { id: 12, knowledgePoint: '质量守恒定律', subject: '化学', weaknessLevel: 'MEDIUM', errorCount: 4, totalCount: 11, accuracyRate: 63.64, status: 'ACTIVE', trend: 'stable', lastErrorTime: '2026-08-05T16:50:00', aiAnalysis: '对质量守恒定律的应用场景理解不足。', aiSuggestion: '结合化学方程式练习，理解反应前后质量不变。' },
    { id: 13, knowledgePoint: '现在完成时', subject: '英语', weaknessLevel: 'LOW', errorCount: 2, totalCount: 9, accuracyRate: 77.78, status: 'IMPROVING', trend: 'improving', lastErrorTime: '2026-07-28T08:25:00', aiAnalysis: '整体掌握良好，个别动词变化需注意。', aiSuggestion: '保持练习，注意现在完成时与过去时的区分。' },
  ]
}

/**
 * Mock 触发AI综合分析
 */
export async function mockTriggerWeakPointsAnalysis() {
  await new Promise(r => setTimeout(r, 800))
  return {
    comprehensiveAnalysis: '## 薄弱点分析\n\n当前共 7 个薄弱知识点，其中 2 个为高薄弱度。\n\n- **圆的面积**（数学）：错误 9 次，薄弱程度高\n- **分数加减法**（数学）：错误 8 次，薄弱程度高\n\n### 学习建议\n建议优先复习数学几何与运算模块，再逐步攻克其他科目。',
    learningSuggestions: '建议每天针对薄弱知识点进行专项练习，优先处理 HIGH 等级。',
    detailAnalyses: [],
    recommendedPriority: ['圆的面积', '分数加减法'],
  }
}

/**
 * Mock 获取薄弱点详情
 */
export async function mockGetWeakPointDetail(id) {
  await new Promise(r => setTimeout(r, 500))
  return {
    id,
    userId: 1,
    knowledgePoint: '圆的面积',
    subject: '数学',
    weaknessLevel: 'HIGH',
    weaknessScore: 0.72,
    errorCount: 9,
    totalCount: 15,
    accuracyRate: 40.0,
    errorRate: 0.6,
    recentCorrectRate: 0.2,
    confusionCount: 3,
    status: 'ACTIVE',
    lastErrorTime: '2026-08-06T18:20:00',
    aiAnalysis: '该学生在圆的面积公式和圆周率应用方面存在明显不足，经常混淆半径与直径，导致面积计算错误。',
    aiSuggestion: '建议先从圆的周长与面积公式辨析开始，通过大量图示题巩固半径/直径识别，再过渡到组合图形面积计算。',
    analyzedAt: '2026-08-07T02:00:00',
    recentErrors: [
      { id: 1001, exerciseId: 'EX_20260806_005', isCorrect: 0, completedAt: '2026-08-06T18:20:00' },
      { id: 1002, exerciseId: 'EX_20260805_012', isCorrect: 0, completedAt: '2026-08-05T15:10:00' },
      { id: 1003, exerciseId: 'EX_20260804_008', isCorrect: 0, completedAt: '2026-08-04T10:30:00' },
      { id: 1004, exerciseId: 'EX_20260802_003', isCorrect: 1, completedAt: '2026-08-02T09:00:00' },
      { id: 1005, exerciseId: 'EX_20260731_015', isCorrect: 0, completedAt: '2026-07-31T14:45:00' },
    ],
    recentErrorRate: 80.0,
    previousErrorRate: 40.0,
    trend: 'declining',
    calculatedAt: '2026-08-07T02:00:00',
    createdAt: '2026-07-20T10:00:00',
  }
}

/**
 * Mock 获取推荐题目
 */
export async function mockGetRecommendQuestions(knowledgePoint) {
  await new Promise(r => setTimeout(r, 400))
  return [
    { questionId: 'Q_1_circle_1', knowledgePoint, subject: '数学', difficulty: 'EASY', questionType: '选择题', questionTitle: '已知圆的半径 r=3，求圆的面积。', reason: '错误率较高，建议从基础题开始巩固' },
    { questionId: 'Q_1_circle_2', knowledgePoint, subject: '数学', difficulty: 'EASY', questionType: '选择题', questionTitle: '圆的直径 d=8，求圆的面积。', reason: '强化直径与半径的转换' },
    { questionId: 'Q_1_circle_3', knowledgePoint, subject: '数学', difficulty: 'MEDIUM', questionType: '填空题', questionTitle: '一个圆的周长是 18.84cm，求它的面积。', reason: '中等难度，综合周长求面积' },
    { questionId: 'Q_1_circle_4', knowledgePoint, subject: '数学', difficulty: 'MEDIUM', questionType: '解答题', questionTitle: '求图中阴影部分的面积（半圆+矩形组合）。', reason: '组合图形面积应用' },
    { questionId: 'Q_1_circle_5', knowledgePoint, subject: '数学', difficulty: 'HARD', questionType: '解答题', questionTitle: '已知圆面积求半径：面积 78.5 平方厘米，求半径。', reason: '提高题，逆向求解' },
  ]
}

/**
 * Mock 获取知识图谱（局部）
 */
export async function mockGetKnowledgeGraph(subject) {
  await new Promise(r => setTimeout(r, 400))
  return {
    nodes: [
      { id: '圆的面积', name: '圆的面积', subject: '数学', weaknessLevel: 'HIGH', masteryRate: 33.33, group: '数学' },
      { id: '圆的周长', name: '圆的周长', subject: '数学', weaknessLevel: 'MEDIUM', masteryRate: 55.0, group: '数学' },
      { id: '圆的认识', name: '圆的认识', subject: '数学', weaknessLevel: 'MASTERED', masteryRate: 85.0, group: '数学' },
      { id: '半径与直径', name: '半径与直径', subject: '数学', weaknessLevel: 'LOW', masteryRate: 70.0, group: '数学' },
      { id: '组合图形面积', name: '组合图形面积', subject: '数学', weaknessLevel: 'UNKNOWN', masteryRate: 0.0, group: '数学' },
    ],
    edges: [
      { source: '圆的认识', target: '圆的周长', relation: 'prerequisite' },
      { source: '圆的认识', target: '圆的面积', relation: 'prerequisite' },
      { source: '半径与直径', target: '圆的面积', relation: 'prerequisite' },
      { source: '圆的周长', target: '圆的面积', relation: 'prerequisite' },
      { source: '圆的面积', target: '组合图形面积', relation: 'prerequisite' },
    ],
  }
}
