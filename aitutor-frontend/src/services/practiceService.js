/**
 * M1 做题板块 —— API 服务
 *
 * ⚠️ 对接状态说明：
 *   ✅ = 已对接后端 API
 *   🔶 = 部分对接（数据转换中，部分字段暂缺）
 *   ❌ = 暂无后端接口，保留 Mock
 */

import { request } from './api';

// ============================================================================
// 常量映射表
// ============================================================================

const SUBJECT_MAP = { '数学': 'math', '英语': 'english', '计算机': 'computer', '语文': 'chinese', '物理': 'physics', '化学': 'chemistry', '生物': 'biology', '通用': 'general' };
const SUBJECT_REVERSE = Object.fromEntries(Object.entries(SUBJECT_MAP).map(([k, v]) => [v, k]));

const TYPE_BE_TO_FE = { 'SINGLE_CHOICE': 'single_choice', 'MULTIPLE_CHOICE': 'multi_choice', 'FILL_BLANK': 'fill_blank', 'SHORT_ANSWER': 'short_answer' };
const TYPE_FE_TO_BE = Object.fromEntries(Object.entries(TYPE_BE_TO_FE).map(([k, v]) => [v, k]));

const DIFF_BE_TO_FE = { 'BASIC': 1, 'ADVANCED': 3, 'HARD': 5 };
const DIFF_FE_TO_BE = { 1: 'BASIC', 2: 'BASIC', 3: 'ADVANCED', 4: 'ADVANCED', 5: 'HARD' };
const DIFF_LABEL = { 1: '★', 2: '★★', 3: '★★★', 4: '★★★★', 5: '★★★★★' };

const MISTAKE_STATUS_BE_TO_FE = { 'UNRESOLVED': 'unresolved', 'REVIEWING': 'reviewing', 'RESOLVED': 'resolved' };
const MISTAKE_STATUS_FE_TO_BE = Object.fromEntries(Object.entries(MISTAKE_STATUS_BE_TO_FE).map(([k, v]) => [v, k]));

// Mock 仅供显式开启的离线演示使用。正常运行时接口失败必须直接提示，
// 否则用户会误以为本地判题结果已经写入数据库。
const PRACTICE_MOCK_ENABLED = import.meta.env.VITE_ENABLE_PRACTICE_MOCK === 'true';

// ============================================================================
// 工具函数
// ============================================================================

function unwrap(res) {
  if (res && typeof res === 'object' && 'data' in res) return res.data;
  return res;
}

function persistentApiError(action, err) {
  let reason = err?.message || '请求失败';
  if (err?.code === 401) reason = '登录状态已失效，请重新登录';
  if (err?.code === 0) reason = '无法连接后端服务';
  const wrapped = new Error(`${action}失败：${reason}`);
  wrapped.code = err?.code;
  wrapped.cause = err;
  return wrapped;
}

function buildQuery(params) {
  const parts = [];
  for (const [k, v] of Object.entries(params)) {
    if (v !== undefined && v !== null && v !== '') parts.push(encodeURIComponent(k) + '=' + encodeURIComponent(v));
  }
  return parts.length ? '?' + parts.join('&') : '';
}

function frontendDifficulty(be) {
  const n = DIFF_BE_TO_FE[be] || 1;
  return { value: n, label: DIFF_LABEL[n] || '★' };
}

function shuffled(items = []) {
  const result = [...items];
  for (let index = result.length - 1; index > 0; index -= 1) {
    const target = Math.floor(Math.random() * (index + 1));
    [result[index], result[target]] = [result[target], result[index]];
  }
  return result;
}

function selectSessionQuestions(items, count, mixedSubjects) {
  if (!mixedSubjects) return shuffled(items).slice(0, count);
  const grouped = new Map();
  items.forEach((question) => {
    const subject = question.subject || 'general';
    if (!grouped.has(subject)) grouped.set(subject, []);
    grouped.get(subject).push(question);
  });
  const queues = shuffled([...grouped.values()].map((group) => shuffled(group)));
  const selected = [];
  while (selected.length < count && queues.some((queue) => queue.length > 0)) {
    queues.forEach((queue) => {
      if (selected.length < count && queue.length > 0) selected.push(queue.shift());
    });
  }
  return selected;
}

// ============================================================================
// Transform 函数 —— 将后端 JSON 转为前端期望的数据结构
// ============================================================================

/**
 * GET /filters → 前端 filterOptions
 */
function transformFilterOptions(data) {
  const subjects = (data.subjects || []).map(s => ({ value: SUBJECT_MAP[s] || 'general', label: s }));
  const grades = (data.gradeLevels || []).map(g => ({ value: g, label: g }));
  // 后端返回扁平章节列表，前端期望按科目分组；此处铺到所有科目 + general 兜底
  const chapterItems = (data.chapters || []).map(ch => ({ value: ch, label: ch }));
  const chapters = { general: chapterItems };
  subjects.forEach(s => { chapters[s.value] = chapterItems; });
  const typeLabels = { single_choice: '单选题', multi_choice: '多选题', fill_blank: '填空题', short_answer: '简答题' };
  const types = (data.questionTypes || []).map(t => {
    const fe = TYPE_BE_TO_FE[t];
    return { value: fe || t.toLowerCase(), label: (fe && typeLabels[fe]) || t };
  });
  const difficulties = (data.difficulties || []).map(d => frontendDifficulty(d));
  return { subjects, grades, chapters, types, difficulties };
}

/**
 * 单题转换
 */
function transformQuestion(q, includeAnswer = true) {
  if (!q) return null;
  const options = q.options || {};
  const optionList = Object.entries(options).map(([k, v]) => k + '. ' + v);
  const fe = {
    questionId: q.id,
    title: q.title || '',
    type: TYPE_BE_TO_FE[q.questionType] || 'single_choice',
    difficulty: DIFF_BE_TO_FE[q.difficulty] || 1,
    subject: SUBJECT_MAP[q.subject] || q.subject,
    grade: q.gradeLevel || '',
    chapter: q.chapter || '',
    content: {
      stem: q.content || q.title || '',
      options: optionList,
      images: [],
    },
    knowledgePoints: q.knowledgePoint ? [{ id: 0, name: q.knowledgePoint }] : [],
    answerSteps: [],
    explanation: q.analysis || '',
    status: q.status || 'ENABLED',
  };
  if (includeAnswer) {
    fe.correctAnswer = q.correctAnswer || '';
  }
  return fe;
}

/**
 * 题目列表分页转换
 */
function transformQuestionList(data) {
  return {
    total: data.total || 0,
    page: data.page || 1,
    size: data.pageSize || 12,
    items: (data.records || []).map(q => transformQuestion(q, false)),
  };
}

/**
 * POST /submit → 前端 submitAnswer 结果
 */
function transformSubmitResult(data) {
  const q = data.question || {};
  const record = data.record || {};
  const dashboard = data.dashboard || {};
  return {
    recordId: record.id,
    isCorrect: Boolean(data.correct),
    correctAnswer: { selected: q.correctAnswer || '' },
    explanation: q.analysis || '',
    knowledgePoints: q.knowledgePoint ? [{ id: 0, name: q.knowledgePoint }] : [],
    answerSteps: [],
    pointsEarned: data.points || 0,
    totalPoints: dashboard.totalPoints || 0,
    judgeScore: data.judgeScore,
    judgeFeedback: data.judgeFeedback,
  };
}

/**
 * GET /mistakes 单条错题转换
 */
function transformMistake(m) {
  return {
    id: m.id,
    questionId: m.questionId,
    questionContent: {
      stem: m.content || m.questionTitle || '',
      options: [],
      images: [],
    },
    correctAnswer: m.correctAnswer || '',
    userAnswer: { selected: '' },    // 🔶 同上，需额外查询答题记录
    wrongReasonTag: '',              // 🔶 后端暂无错因标签
    explanation: m.analysis || '',
    knowledgePoints: m.knowledgePoint ? [{ id: 0, name: m.knowledgePoint }] : [],
    status: MISTAKE_STATUS_BE_TO_FE[m.status] || 'unresolved',
    isKeyFocus: Boolean(m.doubtful),
    subject: SUBJECT_MAP[m.subject] || m.subject || SUBJECT_MAP[m.track] || 'general',
    createdAt: m.lastWrongAt || '',
    reviewCount: m.reviewCount || 0,
    lastReviewAt: m.lastReviewAt || null,
    chapter: m.chapter || '',
    difficulty: DIFF_BE_TO_FE[m.difficulty] || 1,
  };
}

/**
 * GET /statistics → 前端 statistics
 */
function transformStatistics(data) {
  const transformDistribution = (items = []) => items.map(item => ({
    name: item.name || '未分类',
    total: item.count || 0,
    correct: item.correct || 0,
    accuracy: (item.accuracy || 0) / 100,
    avgTimeSpent: item.averageDurationSeconds || 0,
  }));

  return {
    summary: {
      totalQuestions: data.totalAnswers || 0,
      correctQuestions: data.correctAnswers || 0,
      incorrectQuestions: data.incorrectAnswers || 0,
      correctRate: (data.accuracy || 0) / 100,
      previousCorrectRate: (data.previousAccuracy || 0) / 100,
      accuracyChange: data.accuracyChange || 0,
      hasPreviousPeriod: Boolean(data.hasPreviousPeriod),
      avgTimeSpent: data.averageDurationSeconds || 0,
      totalTimeSpent: data.totalDurationSeconds || 0,
      activeDays: data.activeDays || 0,
      bestStreak: data.bestStreak || 0,
      streakDays: data.currentStreak || 0,
    },
    dailyTrend: (data.trend || []).map(t => ({
      date: (t.date || '').slice(5),
      count: t.count || 0,
      correctRate: (t.accuracy || 0) / 100,
      avgTimeSpent: t.averageDurationSeconds || 0,
    })),
    byKnowledgePoint: (data.knowledgeDistribution || []).map(k => ({
      kpId: k.id || k.name || k.knowledgePoint || 'unknown',
      kpName: k.name || k.knowledgePoint || '',
      total: k.count || 0,
      correct: k.correct || 0,
      accuracy: (k.accuracy || 0) / 100,
      recentAccuracy: (k.recentAccuracy || 0) / 100,
      recentTrend: k.recentTrend || 0,
      sampleConfidence: (k.sampleConfidence || 0) / 100,
      masteryScore: k.masteryScore || 0,
      masteryLevel: k.masteryLevel || '数据不足',
      needsReview: Boolean(k.needsReview),
      recommendation: k.recommendation || '',
      rate: (k.masteryScore || 0) / 100,
    })),
    byChapter: transformDistribution(data.chapterDistribution),
    byQuestionType: transformDistribution(data.questionTypeDistribution),
    byDifficulty: transformDistribution(data.difficultyDistribution),
    wrongReasons: (data.wrongReasonDistribution || []).map(item => ({
      reason: item.reason || 'unknown',
      label: item.label || '其他错误',
      description: item.description || '',
      count: item.count || 0,
      inferred: Boolean(item.inferred),
    })),
    recommendations: data.recommendations || [],
  };
}

/**
 * GET /leaderboards → 前端 ranking 列表
 */
function transformRanking(data) {
  return (data.trackRanking || []).map(r => ({
    rank: r.rank,
    userId: r.userId,
    nickname: r.studentName || r.username || r.nickname || '',
    avatar: r.avatar || '',
    points: r.totalPoints ?? r.points ?? 0,
    streakDays: r.streakDays || 0,
  }));
}

// ============================================================================
// ✅ 已对接后端 API
// ============================================================================

/**
 * 获取筛选选项  ✅ GET /api/practice/filters
 * 🔶 后端不可用时回退到 Mock 数据
 */
export async function getFilterOptions() {
  try {
    const res = await request('/api/practice/filters');
    return transformFilterOptions(unwrap(res));
  } catch (err) {
    if (!PRACTICE_MOCK_ENABLED) throw persistentApiError('加载科目', err);
    console.warn('[M1] getFilterOptions 后端不可用，使用 Mock:', err.message);
    await _loadMock();
    return _mockFilterOptions;
  }
}

/**
 * 查询题目列表  ✅ GET /api/practice/questions
 */
export async function getQuestions(params = {}) {
  try {
    const beParams = {};
    if (params.subject) beParams.subject = SUBJECT_REVERSE[params.subject] || params.subject;
    if (params.grade) beParams.gradeLevel = params.grade;
    if (params.chapter) beParams.chapter = params.chapter;
    if (params.type) beParams.questionType = TYPE_FE_TO_BE[params.type] || params.type;
    if (params.difficulty) beParams.difficulty = DIFF_FE_TO_BE[params.difficulty] || params.difficulty;
    if (params.keyword) beParams.keyword = params.keyword;
    beParams.page = params.page || 1;
    beParams.pageSize = params.size || 12;
    const res = await request('/api/practice/questions' + buildQuery(beParams));
    return transformQuestionList(unwrap(res));
  } catch (err) {
    if (!PRACTICE_MOCK_ENABLED) throw persistentApiError('加载题库', err);
    // 🔶 后端不可用，回退到 Mock
    console.warn('[M1] getQuestions 后端不可用，使用 Mock:', err.message);
    await _loadMock();
    let list = [..._mockQuestions];
    if (params.subject) list = list.filter(q => q.subject === params.subject);
    if (params.grade) list = list.filter(q => q.grade === params.grade);
    if (params.chapter) list = list.filter(q => q.chapter === params.chapter);
    if (params.type) list = list.filter(q => q.type === params.type);
    if (params.difficulty) list = list.filter(q => q.difficulty === Number(params.difficulty));
    if (params.keyword) list = list.filter(q => (q.content?.stem || '').includes(params.keyword) || q.chapter?.includes(params.keyword));
    const page = params.page || 1;
    const size = params.size || 20;
    const start = (page - 1) * size;
    return { total: list.length, page, size, items: list.slice(start, start + size) };
  }
}

/**
 * 获取单题详情  ✅ GET /api/practice/questions/{id}
 */
export async function getQuestionDetail(questionId) {
  const res = await request('/api/practice/questions/' + questionId);
  return transformQuestion(unwrap(res), true);
}

/**
 * 获取题目管理表单所需的完整原始字段
 */
export async function getQuestionForEditing(questionId) {
  const res = await request('/api/practice/questions/' + questionId);
  const q = unwrap(res) || {};
  const options = q.options || {};
  return {
    id: q.id,
    subject: q.subject || '',
    gradeLevel: q.gradeLevel || '',
    track: q.track || '',
    chapter: q.chapter || '',
    knowledgePoint: q.knowledgePoint || '',
    questionType: q.questionType || 'SINGLE_CHOICE',
    difficulty: q.difficulty || 'BASIC',
    title: q.title || '',
    content: q.content || '',
    optionA: options.A || '',
    optionB: options.B || '',
    optionC: options.C || '',
    optionD: options.D || '',
    correctAnswer: q.correctAnswer || '',
    answerKeywords: q.answerKeywords || '',
    analysis: q.analysis || '',
    lessonId: q.lessonId || '',
    status: q.status || 'ENABLED',
  };
}

/**
 * 新增题目
 */
export async function createQuestion(payload) {
  const res = await request('/api/practice/questions', {
    method: 'POST',
    body: JSON.stringify(payload),
  });
  return unwrap(res);
}

/**
 * 修改题目
 */
export async function updateQuestion(questionId, payload) {
  const res = await request('/api/practice/questions/' + questionId, {
    method: 'PUT',
    body: JSON.stringify(payload),
  });
  return unwrap(res);
}

/**
 * 删除题目。存在答题记录时由后端归档，以保留历史统计。
 */
export async function deleteQuestion(questionId) {
  await request('/api/practice/questions/' + questionId, { method: 'DELETE' });
  return { success: true };
}

/**
 * 提交答案  ✅ POST /api/practice/submit
 * 💡 会自动检测：真实 DB ID → 后端判题；Mock ID（有 _originalId）→ 本地判题
 */
export async function submitAnswer(params = {}) {
  if (params._originalId && !PRACTICE_MOCK_ENABLED) {
    throw new Error('这组练习来自旧的临时数据，答题记录无法保存。请重新开始生成练习。');
  }

  let userAnswer = '';
  if (typeof params.answer?.selected === 'string') {
    userAnswer = params.answer.selected;
  } else if (Array.isArray(params.answer?.selected)) {
    userAnswer = params.answer.selected.join(',');
  } else if (params.answer?.text) {
    userAnswer = params.answer.text;
  }
  const body = {
    questionId: params.questionId,
    userAnswer,
    durationSeconds: params.timeSpent || 0,
    mode: params.mode || 'SEQUENTIAL',
  };

  try {
    const res = await request('/api/practice/submit', {
      method: 'POST',
      body: JSON.stringify(body),
    });
    return transformSubmitResult(unwrap(res));
  } catch (err) {
    // Mock 会话回退：generateSession 使用 Mock 时 questionId 为假 ID，后端会返回 400/404
    if (params._originalId && PRACTICE_MOCK_ENABLED) {
      console.warn('[M1] submitAnswer 后端不可用，使用本地 Mock 判题');
      return _mockJudge(params);
    }
    throw persistentApiError('保存答题记录', err);
  }
}

/**
 * 获取排行榜  ✅ GET /api/practice/leaderboards
 * 🔶 后端不可用时回退到 Mock
 */
export async function getRanking(type = 'daily', limit = 20) {
  void type;
  try {
    const res = await request('/api/practice/leaderboards');
    const list = transformRanking(unwrap(res));
    return list.slice(0, limit);
  } catch (err) {
    if (!PRACTICE_MOCK_ENABLED) throw persistentApiError('加载排行榜', err);
    console.warn('[M1] getRanking 后端不可用，使用 Mock:', err.message);
    await _loadMock();
    return (_mockRanking?.daily || []).slice(0, limit);
  }
}

/**
 * 每日签到  ✅ POST /api/practice/checkin
 */
export async function dailyCheckin() {
  const res = await request('/api/practice/checkin', { method: 'POST', body: '{}' });
  const data = unwrap(res);
  return {
    pointsEarned: data.points || 0,
    totalPoints: 0,
    streakDays: 0,
    alreadyChecked: data.alreadyChecked || false,
  };
}

/**
 * 查询签到状态  ✅ GET /api/practice/checkin/status
 */
export async function getCheckinStatus() {
  try {
    const res = await request('/api/practice/checkin/status');
    const data = unwrap(res);
    return {
      checkedToday: data.checkedToday || false,
      streakDays: data.streakDays || 0,
      totalPoints: data.totalPoints || 0,
    };
  } catch (err) {
    console.warn('[M1] getCheckinStatus 后端不可用:', err.message);
    return { checkedToday: false, streakDays: 0, totalPoints: 0 };
  }
}

/**
 * 获取当前登录用户已到期的 AI 复习提醒。
 * 用户身份由后端从 JWT 中读取，前端不传 userId。
 */
export async function getReviewReminders() {
  const res = await request('/api/practice/review-reminders');
  return (unwrap(res) || []).map((item) => ({
    id: item.id,
    courseId: item.courseId || '',
    type: item.reminderType || 'REVIEW',
    content: item.content || '知识点复习',
    scheduledDate: item.scheduledDate || '',
    priority: Number(item.priority) || 0,
    isReviewed: Number(item.isReviewed) === 1,
  }));
}

/**
 * 完成一条 AI 复习提醒。
 */
export async function completeReviewReminder(reminderId, notes = '') {
  const res = await request(`/api/practice/review-reminders/${reminderId}/complete`, {
    method: 'POST',
    body: JSON.stringify({ notes }),
  });
  return unwrap(res);
}

// ============================================================================
// 🔶 部分对接（数据字段不全，后续需要完善）
// ============================================================================

/**
 * 获取错题本  🔶 GET /api/practice/mistakes
 * ⚠️ 后端返回不含 correctAnswer / userAnswer / wrongReasonTag，部分字段为兜底值
 */
export async function getWrongQuestions(params = {}) {
  try {
    const beParams = {};
    if (params.status) beParams.status = MISTAKE_STATUS_FE_TO_BE[params.status] || params.status;
    if (params.chapter) beParams.chapter = params.chapter;
    if (params.kpName) beParams.knowledgePoint = params.kpName;
    const res = await request('/api/practice/mistakes' + buildQuery(beParams));
    let list = (unwrap(res) || []).map(transformMistake);
    if (params.keyword) list = list.filter(q => q.questionContent?.stem?.includes(params.keyword));
    if (params.subject) list = list.filter(q => q.subject === params.subject);
    if (params.timeRange === 'week') list = list.filter(q => new Date(q.createdAt) > new Date(Date.now() - 7 * 86400000));
    if (params.timeRange === 'month') list = list.filter(q => new Date(q.createdAt) > new Date(Date.now() - 30 * 86400000));
    const page = params.page || 1;
    const size = params.size || 20;
    const start = (page - 1) * size;
    return { total: list.length, page, size, items: list.slice(start, start + size) };
  } catch (err) {
    if (!PRACTICE_MOCK_ENABLED) throw persistentApiError('加载错题本', err);
    // 🔶 后端不可用，回退到 Mock
    console.warn('[M1] getWrongQuestions 后端不可用，使用 Mock:', err.message);
    await _loadMock();
    let list = [..._mockWrongQuestions];
    if (params.status) list = list.filter(q => q.status === params.status);
    if (params.keyword) list = list.filter(q => q.questionContent?.stem?.includes(params.keyword));
    if (params.subject) list = list.filter(q => q.subject === params.subject);
    if (params.timeRange === 'week') list = list.filter(q => new Date(q.createdAt) > new Date(Date.now() - 7 * 86400000));
    if (params.timeRange === 'month') list = list.filter(q => new Date(q.createdAt) > new Date(Date.now() - 30 * 86400000));
    const page = params.page || 1;
    const size = params.size || 20;
    const start = (page - 1) * size;
    return { total: list.length, page, size, items: list.slice(start, start + size) };
  }
}

/**
 * 标记/取消重点  ✅ PUT /api/wrong-questions/{id}/focus
 */
export async function toggleFocus(wrongQuestionId, focused = true) {
  try {
    const res = await request('/api/wrong-questions/' + wrongQuestionId + '/focus', {
      method: 'PUT',
      body: JSON.stringify({ focused }),
    });
    return { success: true, data: unwrap(res) };
  } catch (err) {
    console.warn('[M1] toggleFocus 失败:', err.message);
    return { success: false, reason: err.message };
  }
}

/**
 * 获取练习统计  🔶 GET /api/practice/statistics
 * ⚠️ wrongReasons 字段暂空，streakDays 需再调 dashboard
 * 🔶 后端不可用时回退到 Mock
 */
export async function getStatistics(params = {}) {
  try {
    const rangeMap = { week: 'week', month: 'month', all: 'all' };
    const range = rangeMap[params.period || params.range] || 'week';
    const res = await request('/api/practice/statistics?range=' + range);
    return transformStatistics(unwrap(res));
  } catch (err) {
    if (!PRACTICE_MOCK_ENABLED) throw persistentApiError('加载统计数据', err);
    console.warn('[M1] getStatistics 后端不可用，使用 Mock:', err.message);
    await _loadMock();
    return _mockStatistics;
  }
}

// ============================================================================
// ❌ 暂无后端对应接口 / 🔶 待后续完善
// ============================================================================

// 保留 Mock 数据引用（仅 fallback 阶段使用）
let _mockQuestions = null;
let _mockPoints = null;
let _mockFilterOptions = null;
let _mockWrongQuestions = null;
let _mockStatistics = null;
let _mockRanking = null;
async function _loadMock() {
  if (!_mockQuestions) {
    const mod = await import('../data/mockPractice');
    _mockQuestions = mod.mockQuestions;
    _mockPoints = mod.mockPoints;
    _mockFilterOptions = mod.mockFilterOptions;
    _mockWrongQuestions = mod.mockWrongQuestions;
    _mockStatistics = mod.mockStatistics;
    _mockRanking = mod.mockRanking;
  }
}

/**
 * Mock 本地判题（当后端不可用时的降级方案）
 * 🔶 预期后续全部走后端，此函数仅作过渡
 */
async function _mockJudge(params) {
  await _loadMock();
  const { questionId, answer, _originalId } = params;
  const matchId = _originalId || questionId;
  const question = _mockQuestions.find((q) => q.questionId === matchId);
  if (!question) {
    return { recordId: Date.now(), isCorrect: false, error: '题目不存在' };
  }
  let isCorrect = false;
  if (question.type === 'single_choice') {
    isCorrect = answer?.selected === question.correctAnswer;
  } else if (question.type === 'multi_choice') {
    const selected = (answer?.selected || []).sort().join(',');
    const expected = question.correctAnswer.split(',').sort().join(',');
    isCorrect = selected === expected;
  } else {
    isCorrect = (answer?.text || '').trim().toLowerCase() === question.correctAnswer.trim().toLowerCase();
  }
  return {
    recordId: Date.now(),
    isCorrect,
    correctAnswer:
      question.type === 'single_choice' ? { selected: question.correctAnswer }
      : question.type === 'multi_choice' ? { selected: question.correctAnswer.split(',') }
      : { text: question.correctAnswer },
    explanation: question.explanation,
    knowledgePoints: question.knowledgePoints,
    answerSteps: question.answerSteps,
    pointsEarned: isCorrect ? 10 : 0,
    totalPoints: (_mockPoints?.totalPoints || 0) + (isCorrect ? 10 : 0),
  };
}

/**
 * 逐题获取下一题  ✅ GET /api/practice/next
 * 💡 后端支持 6 种模式：SEQUENTIAL / RANDOM / MISTAKES / FREE_PRACTICE / AFTER_CLASS / MISTAKE_REDO
 * 🔶 当前仅 generateSession 内部使用，后续可暴露给 PracticePage 做"流式按题拉取"交互
 */
export async function getNextQuestion(params = {}) {
  const beParams = { mode: params.mode || 'SEQUENTIAL' };
  if (params.subject) beParams.subject = SUBJECT_REVERSE[params.subject] || params.subject;
  if (params.grade) beParams.gradeLevel = params.grade;
  if (params.chapter) beParams.chapter = params.chapter;
  if (params.knowledgePoint) beParams.knowledgePoint = params.knowledgePoint;
  if (params.questionType) beParams.questionType = TYPE_FE_TO_BE[params.questionType] || params.questionType;
  if (params.difficulty) beParams.difficulty = DIFF_FE_TO_BE[params.difficulty] || params.difficulty;
  if (params.lessonId) beParams.lessonId = params.lessonId;

  const res = await request('/api/practice/next' + buildQuery(beParams));
  const data = unwrap(res);
  // GET /next 返回 {question, mode, stats}，question 不含 correctAnswer
  return {
    question: transformQuestion(data.question, false),
    mode: data.mode,
    stats: data.stats, // dashboard 数据（含 totalPoints 等）
  };
}

/**
 * 生成练习会话  ✅ GET /api/practice/questions（批量拉取，含答案）
 * 💡 后端原始设计为逐题 GET /next，此处用 GET /questions 批量获取以适配前端现有交互
 * 💡 后端不可用时自动回退到 Mock 数据
 */
export async function generateSession(params = {}) {
  const count = Math.max(1, Math.min(50, Number(params.questionCount) || 10));
  const beParams = { page: 1, pageSize: 100, status: 'ENABLED' };
  if (params.subject) beParams.subject = SUBJECT_REVERSE[params.subject] || params.subject;
  if (params.grade) beParams.gradeLevel = params.grade;
  if (params.chapter) beParams.chapter = params.chapter;
  if (params.questionType) beParams.questionType = TYPE_FE_TO_BE[params.questionType] || params.questionType;
  if (params.difficulty) beParams.difficulty = DIFF_FE_TO_BE[params.difficulty] || params.difficulty;
  if (params.lessonId) beParams.lessonId = params.lessonId;
  // 课后题模式：传 AFTER_CLASS + lessonId 到 POST /submit 的 mode 字段
  if (params.mode) beParams.mode = params.mode;

  try {
    const res = await request('/api/practice/questions' + buildQuery(beParams));
    const data = unwrap(res);
    const items = (data.records || []).map(q => transformQuestion(q, true));
    if (items.length === 0) {
      const emptyError = new Error('当前选择范围内暂无可用题目');
      emptyError.skipMockFallback = true;
      throw emptyError;
    }
    const questions = selectSessionQuestions(items, count, !params.subject);
    return {
      sessionId: 'sess_' + Date.now(),
      questions,
      totalCount: questions.length,
      requestedCount: count,
      subject: params.subject || 'mixed',
      availabilityNotice: questions.length < count
        ? `当前范围只有 ${questions.length} 道可用题目，已按实际数量生成。`
        : '',
    };
  } catch (err) {
    if (err.skipMockFallback) throw err;
    if (!PRACTICE_MOCK_ENABLED) throw persistentApiError('生成练习', err);
    // 🔶 后端不可用，回退到 Mock 数据
    console.warn('[M1] 后端不可用，generateSession 回退到 Mock:', err.message);
    await _loadMock();
    let pool = [..._mockQuestions];
    if (params.subject) pool = pool.filter(q => q.subject === params.subject);
    if (params.knowledgePointIds?.length) {
      pool = pool.filter(q => q.knowledgePoints.some(kp => params.knowledgePointIds.includes(kp.id)));
    }
    if (pool.length === 0) throw new Error('当前选择范围内暂无可用题目');
    const questions = selectSessionQuestions(pool, count, !params.subject).map((src, index) => {
      // _originalId 标记为 Mock 来源，submitAnswer 会据此走本地判题
      return { ...src, questionId: 100 + index, _originalId: src.questionId };
    });
    return {
      sessionId: 'sess_' + Date.now(),
      questions,
      totalCount: questions.length,
      requestedCount: count,
      subject: params.subject || 'mixed',
      availabilityNotice: questions.length < count
        ? `当前范围只有 ${questions.length} 道可用题目，已按实际数量生成。`
        : '',
    };
  }
}

/**
 * 删除错题  ✅ DELETE /api/wrong-questions/{id}
 */
export async function deleteWrongQuestion(wrongQuestionId) {
  try {
    await request('/api/wrong-questions/' + wrongQuestionId, { method: 'DELETE' });
    return { success: true };
  } catch (err) {
    console.warn('[M1] deleteWrongQuestion 失败:', err.message);
    return { success: false, reason: err.message };
  }
}

/**
 * 获取积分明细  ✅ GET /api/practice/dashboard
 */
export async function getDashboard() {
  try {
    const res = await request('/api/practice/dashboard');
    const data = unwrap(res);
    return {
      totalPoints: data.totalPoints || 0,
      streakDays: data.streakDays || 0,
      todayQuestions: data.todayQuestions || 0,
      todayCorrect: data.todayCorrect || 0,
      rank: data.rank || null,
    };
  } catch (err) {
    console.warn('[M1] getDashboard 后端不可用:', err.message);
    return { totalPoints: 0, streakDays: 0, todayQuestions: 0, todayCorrect: 0, rank: null };
  }
}

/**
 * 获取答题记录  ✅ GET /api/practice/records
 */
export async function getRecords(params = {}) {
  const beParams = {};
  if (params.range) beParams.range = params.range;
  if (params.chapter) beParams.chapter = params.chapter;
  if (params.knowledgePoint) beParams.knowledgePoint = params.knowledgePoint;
  if (params.wrongOnly) beParams.wrongOnly = 'true';
  const res = await request('/api/practice/records' + buildQuery(beParams));
  return (unwrap(res) || []).map(r => ({
    recordId: r.id,
    questionId: r.questionId,
    questionTitle: r.questionTitle || r.content || '',
    userAnswer: r.userAnswer || '',
    correctAnswer: r.correctAnswer || '',
    isCorrect: Boolean(r.correct),
    timeSpent: r.durationSeconds || 0,
    createdAt: r.createdAt || '',
  }));
}

/**
 * 更新错题笔记  ✅ PATCH /api/practice/mistakes/{recordId}
 */
export async function updateMistake(recordId, body = {}) {
  const payload = {};
  if (body.doubtful !== undefined) payload.doubtful = body.doubtful;
  if (body.reviewNote) payload.reviewNote = body.reviewNote;
  if (body.status) payload.status = body.status;
  const res = await request('/api/practice/mistakes/' + recordId, {
    method: 'PATCH',
    body: JSON.stringify(payload),
  });
  return { success: true, data: unwrap(res) };
}

/**
 * 更新隐私设置  ✅ PATCH /api/practice/privacy
 */
export async function updatePrivacy(hidden) {
  await request('/api/practice/privacy', {
    method: 'PATCH',
    body: JSON.stringify({ hidden }),
  });
  return { success: true };
}

/**
 * 导出答题记录  ✅ GET /api/practice/export
 * @returns {Blob} 文本文件下载
 */
export async function exportRecords(wrongOnly = false) {
  const url = '/api/practice/export?wrongOnly=' + (wrongOnly ? 'true' : 'false');
  const res = await fetch(url);
  if (!res.ok) throw new Error('导出失败');
  const blob = await res.blob();
  const filename = wrongOnly ? 'mistakes.txt' : 'practice-records.txt';
  const a = document.createElement('a');
  a.href = URL.createObjectURL(blob);
  a.download = filename;
  a.click();
  URL.revokeObjectURL(a.href);
  return { success: true };
}

/**
 * 清空个人刷题数据  ✅ DELETE /api/practice/records
 */
export async function clearUserData() {
  await request('/api/practice/records', { method: 'DELETE' });
  return { success: true };
}

/**
 * 导入题目  ✅ POST /api/practice/questions/import (multipart)
 * @param {File} file - Excel、Word 或 PDF 题库文件
 */
export async function importQuestions(file) {
  const formData = new FormData();
  formData.append('file', file);
  const res = await request('/api/practice/questions/import', {
    method: 'POST',
    body: formData,
  });
  const data = unwrap(res);
  return {
    totalRows: data.totalRows || 0,
    inserted: data.inserted || 0,
    pendingReview: data.pendingReview || 0,
    failed: data.failed || 0,
    errors: data.errors || [],
    filename: data.filename || file.name,
    supportedFormats: data.supportedFormats || '',
  };
}
