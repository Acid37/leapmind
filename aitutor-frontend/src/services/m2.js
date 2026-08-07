import { post, get, del } from './api'

// ===================================================================
// 真实接口封装（对接后端时取消 mock 调用，切到这些函数）
// ===================================================================

/**
 * OCR 识别题目
 * 对接人：王圳 - POST /api/ocr/recognize-question
 * 文档：M2_接口文档.md 接口4
 * 响应 ApiResponse: { code, message, data: { ocrRecordId, structuredQuestion, confidence }, timestamp }
 */
export async function recognizeQuestion(image, subject) {
  const formData = new FormData()
  formData.append('file', image)
  if (subject) formData.append('subject', subject)
  const res = await fetch('/api/ocr/recognize-question', { method: 'POST', body: formData })
  const json = await res.json()
  if (json.code === 200) return json.data
  throw new Error(json.message || 'OCR 识别失败')
}

/**
 * 拍照答疑 SSE 流式
 * 对接人：王圳/孔维诚 - POST /api/explain/photo-qa
 * 文档：M2_接口文档.md 接口5
 * SSE 事件: start, thinking, step, answer, overview, knowledge, tip, similar, done, error
 */
export async function photoQA(params, onMessage, onError) {
  try {
    const res = await fetch('/api/explain/photo-qa', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(params)
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
        if (line.startsWith('data: ')) {
          try {
            const data = JSON.parse(line.slice(6))
            if (data.type === 'error') { onError?.(new Error(data.message)); return }
            onMessage(data)
          } catch { /* skip */ }
        }
      }
    }
  } catch (err) {
    onError?.(err)
  }
}

/**
 * 讲题生成 SSE 流式
 * 对接人：王圳 - POST /api/explain/generate
 * 文档：M2_接口文档.md 接口7
 * SSE 事件同 photo-qa，含 overview, step, tip, similar, done
 */
export async function generateExplain(params, onMessage, onError) {
  try {
    const res = await fetch('/api/explain/generate', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(params)
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
        if (line.startsWith('data: ')) {
          try {
            const data = JSON.parse(line.slice(6))
            if (data.type === 'error') { onError?.(new Error(data.message)); return }
            onMessage(data)
          } catch { /* skip */ }
        }
      }
    }
  } catch (err) {
    onError?.(err)
  }
}

/**
 * 题库匹配
 * 对接人：待确认 - POST /api/questions/match
 * 文档：M2_接口文档.md 题库匹配
 * 返回: ApiResponse → data: { matched, matchDegree, questionId, existingExplanation, candidates }
 */
export async function matchQuestion(stem, subject, type) {
  const res = await post('/api/questions/match', { stem, subject, type })
  return res.data
}

/**
 * 获取错题列表
 * 对接人：杜恩泽（M1 Java）- GET /api/wrong-questions
 * 文档：wrong-questions-api.ts
 * 查询参数: { status?, chapter?, knowledgePoint? }
 * 返回 ApiResponse → data: WrongQuestion[]
 */
export async function getWrongQuestions(params = {}) {
  const query = new URLSearchParams(params).toString()
  const res = await fetch(`/api/wrong-questions${query ? `?${query}` : ''}`)
  return res.json()
}

/**
 * 加入错题本（UI 本地状态）
 * 注意：后端没有独立"加入错题本"接口
 * 错题由 POST /api/practice/submit 提交答案时自动归入（答错自动进错题本）
 * 此处仅做本地状态切换，不做实际 API 调用
 */
export async function addToWrongBook(questionId) {
  await new Promise(r => setTimeout(r, 300))
  return { success: true, message: '已添加到错题本' }
}

/**
 * 提交答案（答错自动进错题本）
 * 对接人：杜恩泽（M1 Java）- POST /api/practice/submit
 * 文档：wrong-questions-api.ts
 */
export async function submitAnswer(data) {
  const res = await post('/api/practice/submit', data)
  return res.data
}

/**
 * 获取讲题历史列表
 * 对接人：王圳（M2 Java）- GET /api/explain/history
 */
export async function getExplainHistory(page, size) {
  const res = await fetch(`/api/explain/history?page=${page}&size=${size}`)
  return res.json()
}

/**
 * 获取讲题详情（回放）
 * 对接人：王圳（M2 Java）- GET /api/explain/{explainId}
 */
export async function getExplainDetail(explainId) {
  const res = await fetch(`/api/explain/${explainId}`)
  return res.json()
}

/**
 * 删除讲题记录
 * 对接人：王圳（M2 Java）- DELETE /api/explain/{explainId}
 */
export async function deleteExplain(explainId) {
  await fetch(`/api/explain/${explainId}`, { method: 'DELETE' })
}

// ===================================================================
// Mock 数据（接口未就绪时使用）
// ===================================================================

export async function mockRecognizeQuestion(image, subject) {
  await new Promise(r => setTimeout(r, 1500))
  return {
    ocrRecordId: 501,
    imageUrl: URL.createObjectURL(image),
    recognizedText: '在直角三角形ABC中，∠C=90°，AC=3，BC=4，则AB=？ A. 5 B. 6 C. 7 D. 8',
    structuredQuestion: {
      stem: '在直角三角形ABC中，∠C=90°，AC=3，BC=4，则AB=？',
      options: ['5', '6', '7', '8'],
      type: 'single_choice',
      subject: subject || 'math'
    },
    confidence: 0.95,
    matchedQuestion: { matched: true, matchDegree: 0.95, questionId: 456, existingExplanation: '勾股定理：a²+b²=c²，3²+4²=25，c=5' }
  }
}

export async function mockMatchQuestion(stem, subject) {
  await new Promise(r => setTimeout(r, 300))
  return { matched: true, matchDegree: 0.95, questionId: 456, knowledgePoints: [{ id: 10, name: '勾股定理' }, { id: 11, name: '平方根运算' }], similarQuestions: [{ id: 457, stem: '直角三角形两直角边分别为5和12，求斜边长。' }, { id: 458, stem: '判断边长为3,4,5的三角形是否为直角三角形。' }, { id: 459, stem: '等腰直角三角形的直角边为1，求斜边长。' }] }
}

export async function mockAddToWrongBook(questionId) {
  await new Promise(r => setTimeout(r, 500))
  return { success: true, message: '已添加到错题本' }
}

export async function mockPhotoQA(ocrRecordId, question, onMessage, onError) {
  const mockChunks = [
    { type: 'answer', content: '正确答案是 **5**（选项A）\n\n' },
    { type: 'step', content: '**第一步：识别题型**\n这是一道直角三角形求斜边长的题目。' },
    { type: 'step', content: '**第二步：套用公式**\n根据勾股定理：$a^2 + b^2 = c^2$' },
    { type: 'step', content: '**第三步：代入计算**\n$3^2 + 4^2 = 9 + 16 = 25$，$c = \\sqrt{25} = 5$' },
    { type: 'knowledge', content: JSON.stringify([{ id: 10, name: '勾股定理' }, { id: 11, name: '平方根运算' }, { id: 12, name: '直角三角形性质' }]) },
    { type: 'similar', content: JSON.stringify([{ id: 457, stem: '直角三角形两直角边分别为5和12，求斜边长。' }, { id: 458, stem: '判断边长为3,4,5的三角形是否为直角三角形。' }, { id: 459, stem: '等腰直角三角形的直角边为1，求斜边长。' }]) },
    { type: 'done', content: '', contentId: 999 }
  ]
  for (const chunk of mockChunks) {
    await new Promise(r => setTimeout(r, 600))
    onMessage(chunk)
  }
}

export async function mockGetWrongQuestions() {
  await new Promise(r => setTimeout(r, 500))
  return { total: 12, items: [{ id: 1, questionId: 101, questionContent: { stem: '在直角三角形ABC中，∠C=90°，AC=3，BC=4，则AB=？', options: ['5', '6', '7', '8'], type: 'single_choice' }, userAnswer: { selected: 'B' }, correctAnswer: 'A', wrongReasonTag: 'concept_unclear', knowledgePoints: [{ id: 10, name: '勾股定理' }], createdAt: '2026-07-20T10:30:00' }, { id: 2, questionId: 102, questionContent: { stem: '已知 $f(x) = x^2 + 2x + 1$，求 $f(3)$ 的值。', type: 'short_answer' }, userAnswer: { text: '14' }, correctAnswer: '16', wrongReasonTag: 'formula_wrong', knowledgePoints: [{ id: 20, name: '二次函数' }], createdAt: '2026-07-20T11:00:00' }, { id: 3, questionId: 103, questionContent: { stem: '平行四边形的对角线互相平分。下列哪个条件不能判定四边形是平行四边形？', options: ['两组对边分别相等', '两组对边分别平行', '一组对边平行且相等', '一组对边平行，另一组对边相等'], type: 'single_choice' }, userAnswer: { selected: 'C' }, correctAnswer: 'D', wrongReasonTag: 'concept_unclear', knowledgePoints: [{ id: 30, name: '平行四边形' }], createdAt: '2026-07-21T09:15:00' }] }
}

export async function mockGenerateExplain(params, onMessage, onError) {
  const { wrongReasonTag } = params
  const reasonBased = {
    concept_unclear: { overview: '这道题考察的是**勾股定理**的核心应用。你选择了 $6$，说明对 "斜边是最长边" 这个条件还不够熟悉，我们一起来梳理一下。', steps: [{ title: '审题分析', content: '题目给出的是直角三角形 $\\triangle ABC$，其中 $\\angle C = 90^\\circ$，$AC = 3$，$BC = 4$，求 $AB$。\n\n**关键信息提取：**\n- 直角顶点是 $C$，所以 $AB$ 是**斜边**\n- $AC$ 和 $BC$ 是两条**直角边**\n- 已知两直角边求斜边 → 用**勾股定理**' }, { title: '套用公式', content: '**勾股定理公式：** $a^2 + b^2 = c^2$\n\n其中 $a$、$b$ 是直角边，$c$ 是斜边。\n\n代入本题：\n- $a = AC = 3$\n- $b = BC = 4$\n- $c = AB$（未知）\n\n$$3^2 + 4^2 = AB^2$$' }, { title: '计算求解', content: '逐步计算：\n\n$$3^2 = 9$$\n$$4^2 = 16$$\n$$9 + 16 = 25$$\n$$AB^2 = 25$$\n$$AB = \\sqrt{25} = 5$$\n\n正确答案是 **A. 5**。' }, { title: '验证检查', content: '**验证：** 斜边 $5$ 是否大于两条直角边 $3$ 和 $4$？$5 > 4 > 3$ ✅\n\n**常见错误分析：**\n- 你选了 $6$，可能是将 $3+4$ 直接相加得到 $7$ 再近似\n- 或者把 $AB$ 误当作直角边计算\n\n**记忆技巧：** 斜边对着直角，是三角形中最长的那条边！' }, { title: '总结拓展', content: '**核心考点梳理：**\n1. ✅ 识别直角三角形的直角顶点 → 确定斜边\n2. ✅ 正确代入勾股定理公式 $a^2 + b^2 = c^2$\n3. ✅ 开平方运算\n\n**易错点提醒：** ⚠️\n- 斜边 $c$ 一定是直角所对的边\n- $c$ 的数值一定大于任意一条直角边\n\n**同类题练习：** 一个直角三角形，两条直角边分别是 $5$ 和 $12$，斜边是多少？' }], tip: '💡 **一句话记忆：** 勾股定理就是「直边的平方和 = 斜边的平方」，记住斜边永远最长！' },
    formula_wrong: { overview: '这道题考察**二次函数求值**。你计算得到 $14$，正确答案是 $16$，说明在代入公式时出现了计算失误，我们一起来检查一下。', steps: [{ title: '审题分析', content: '已知 $f(x) = x^2 + 2x + 1$，求 $f(3)$ 的值。\n\n$f(3)$ 表示将 $x = 3$ 代入函数表达式。' }, { title: '代入计算', content: '将 $x = 3$ 逐项代入：\n\n$$f(3) = 3^2 + 2 \\times 3 + 1$$\n\n分步：\n- $3^2 = 9$\n- $2 \\times 3 = 6$\n- $+1$' }, { title: '求和结果', content: '$$f(3) = 9 + 6 + 1 = 16$$\n\n正确答案是 **16**。\n\n你算出的 $14$，很可能是将 $2 \\times 3$ 算成了 $4$，或者漏加了某一项。' }, { title: '检查验证', content: '**代入验证：**\n还可以将 $f(x)$ 因式分解：$f(x) = (x+1)^2$\n\n那么 $f(3) = (3+1)^2 = 4^2 = 16$ ✅\n\n**检查步骤：**\n1. 检查每一项是否都代入正确\n2. 检查乘法运算是否正确\n3. 检查加法是否遗漏' }, { title: '总结拓展', content: '**函数求值三步法：**\n1. 写出表达式\n2. 逐项代入，每步写出中间结果\n3. 合并计算，最后验证\n\n**练习：** $g(x) = 2x^2 - 3x + 5$，求 $g(2)$。' }], tip: '💡 **检查技巧：** 代入求值时，每算完一步先心算验证再继续，能有效避免粗心错误。' }
  }
  const defaultData = { overview: '让我们一起来分析这道题，理清解题思路。', steps: [{ title: '审题分析', content: '仔细阅读题目，提取关键条件和要求。' }, { title: '解题思路', content: '根据题目类型选择合适的解题方法。' }, { title: '逐步推导', content: '按照逻辑顺序逐步推导答案。' }, { title: '验证答案', content: '检查计算过程和最终答案是否正确。' }, { title: '总结提升', content: '归纳解题方法，举一反三。' }], tip: '💡 多练习是提高成绩的最好方法！' }
  const data = reasonBased[wrongReasonTag] || defaultData
  const chunks = [
    { type: 'overview', content: data.overview },
    ...data.steps.map((s, i) => ({ type: 'step', stepNumber: i + 1, title: s.title, content: s.content })),
    { type: 'tip', content: data.tip },
    { type: 'similar', content: '推荐同类题：勾股定理应用题、勾股定理逆定理判断、勾股定理在实际问题中的应用' },
    { type: 'done', explainId: 888 }
  ]
  for (const chunk of chunks) { await new Promise(r => setTimeout(r, 600)); onMessage(chunk) }
}

export async function mockGetExplainHistory(page, size) {
  await new Promise(r => setTimeout(r, 400))
  const allItems = [
    { id: 1, questionSummary: '在直角三角形ABC中，∠C=90°，AC=3，BC=4，则AB=？', subject: 'math', createdAt: '2026-07-20T10:30:00', knowledgePoints: [{ id: 10, name: '勾股定理' }] },
    { id: 2, questionSummary: '已知 f(x) = x² + 2x + 1，求 f(3) 的值。', subject: 'math', createdAt: '2026-07-20T11:00:00', knowledgePoints: [{ id: 20, name: '二次函数' }] },
    { id: 3, questionSummary: '平行四边形的对角线互相平分。下列哪个条件不能判定四边形是平行四边形？', subject: 'math', createdAt: '2026-07-21T09:15:00', knowledgePoints: [{ id: 30, name: '平行四边形' }] },
    { id: 4, questionSummary: '已知圆的半径为 5，求圆的面积。', subject: 'math', createdAt: '2026-07-21T14:00:00', knowledgePoints: [{ id: 40, name: '圆的面积' }, { id: 41, name: '圆周率' }] },
    { id: 5, questionSummary: '解方程：2x + 5 = 15', subject: 'math', createdAt: '2026-07-22T08:20:00', knowledgePoints: [{ id: 50, name: '一元一次方程' }] },
    { id: 6, questionSummary: 'What is the past tense of "go"?', subject: 'english', createdAt: '2026-07-22T09:30:00', knowledgePoints: [{ id: 60, name: '动词过去式' }] },
    { id: 7, questionSummary: '质量为 2kg 的物体，受重力大小为多少？（g=10N/kg）', subject: 'physics', createdAt: '2026-07-22T10:00:00', knowledgePoints: [{ id: 70, name: '重力计算' }] },
    { id: 8, questionSummary: '化简：(a+b)² - (a-b)²', subject: 'math', createdAt: '2026-07-22T11:15:00', knowledgePoints: [{ id: 80, name: '完全平方公式' }] }
  ]
  const start = (page - 1) * size
  return { total: allItems.length, items: allItems.slice(start, start + size), page, size }
}

export async function mockGetExplainDetail(explainId) {
  await new Promise(r => setTimeout(r, 300))
  return { id: explainId, questionSummary: '在直角三角形ABC中，∠C=90°，AC=3，BC=4，则AB=？', questionContent: { stem: '在直角三角形ABC中，∠C=90°，AC=3，BC=4，则AB=？', options: ['5', '6', '7', '8'], type: 'single_choice' }, userAnswer: { selected: 'B' }, correctAnswer: 'A', wrongReasonTag: 'concept_unclear', knowledgePoints: [{ id: 10, name: '勾股定理' }, { id: 11, name: '平方根运算' }], steps: [{ title: '审题分析', content: '题目给出的是直角三角形 $\\triangle ABC$，其中 $\\angle C = 90^\\circ$，$AC = 3$，$BC = 4$，求 $AB$。\n\n**关键信息提取：**\n- 直角顶点是 $C$，所以 $AB$ 是**斜边**\n- $AC$ 和 $BC$ 是两条**直角边**\n- 已知两直角边求斜边 → 用**勾股定理**' }, { title: '套用公式', content: '**勾股定理公式：** $a^2 + b^2 = c^2$\n\n其中 $a$、$b$ 是直角边，$c$ 是斜边。\n\n代入本题：\n- $a = AC = 3$\n- $b = BC = 4$\n- $c = AB$（未知）\n\n$$3^2 + 4^2 = AB^2$$' }, { title: '计算求解', content: '逐步计算：\n\n$$3^2 = 9$$\n$$4^2 = 16$$\n$$9 + 16 = 25$$\n$$AB^2 = 25$$\n$$AB = \\sqrt{25} = 5$$\n\n正确答案是 **A. 5**。' }, { title: '验证检查', content: '**验证：** 斜边 $5$ 是否大于两条直角边 $3$ 和 $4$？$5 > 4 > 3$ ✅\n\n**常见错误分析：**\n- 你选了 $6$，可能是将 $3+4$ 直接相加得到 $7$ 再近似\n- 或者把 $AB$ 误当作直角边计算\n\n**记忆技巧：** 斜边对着直角，是三角形中最长的那条边！' }, { title: '总结拓展', content: '**核心考点梳理：**\n1. ✅ 识别直角三角形的直角顶点 → 确定斜边\n2. ✅ 正确代入勾股定理公式 $a^2 + b^2 = c^2$\n3. ✅ 开平方运算\n\n**同类题练习：** 一个直角三角形，两条直角边分别是 $5$ 和 $12$，斜边是多少？' }], tip: '💡 **一句话记忆：** 勾股定理就是「直边的平方和 = 斜边的平方」，记住斜边永远最长！', createdAt: '2026-07-20T10:30:00' }
}

export async function mockDeleteExplain(explainId) {
  await new Promise(r => setTimeout(r, 300))
  return { success: true }
}
