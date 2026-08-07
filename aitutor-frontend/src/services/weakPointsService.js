/**
 * M3 薄弱点分析模块 —— Service 层
 *
 * 接口文档：m3-api-guide（v1.1，2026-08-07）
 * 后端：M3 曾俊桥 / develop 分支 WeakPointsController + WeakPointsImprovementController
 *
 * 已对接（后端实际存在）：
 *   - GET  /api/weak-points                      薄弱点列表（List，非分页）
 *   - POST /api/weak-points/{userId}/analysis    AI 综合分析（24h 缓存）
 *   - GET  /api/exercises/recommend              推荐练习（自动去重 7 天）
 *   - POST /api/exercises/record                 记录练习结果（实时更新薄弱点）
 *   - GET  /api/weak-points/recommend-questions  推荐具体题目（按薄弱度匹配难度）
 *   - GET  /api/weak-points/knowledge-graph      知识图谱
 *   - GET  /api/weak-points/improvement-report   薄弱点改善报告（ImprovementController）
 *
 * ⚠️ 文档有但后端未实现（等 M3 补）：
 *   - GET  /api/weak-points/{id}/detail          薄弱点详情（文档 3.2）
 *   - POST /api/weak-points/generate-practice-plan 练习计划（文档 3.7）
 *
 * 统一响应 ApiResponse<T>：{ code, message, data, timestamp }
 */

import { get, post } from './api';

/**
 * 解包后端统一响应 ApiResponse：{ code, message, data, timestamp }
 * 与 lectureService / m2.js 的对接模式保持一致
 */
function unwrap(res) {
  if (res && res.code === 200) return res.data;
  return res;
}

// ─── 1. 薄弱点列表（分页参数支持，后端返回 List） ─────

/**
 * 查询用户薄弱点列表
 * GET /api/weak-points?userId=&subject=&status=&page=&size=
 *
 * @param {Object} params
 * @param {number} params.userId            用户 ID（必填）
 * @param {string} [params.subject]         学科过滤，如"数学"
 * @param {string} [params.status]          ACTIVE | RESOLVED | IMPROVING
 * @param {number} [params.page]            页码（默认 1）
 * @param {number} [params.size]            每页条数（默认 20）
 * @returns {Promise<Array>} 薄弱点记录数组（后端 List，文档字段见下）
 *
 * 记录字段（UserWeakPointVO）：
 *   id, userId, knowledgePoint, subject, weaknessLevel(HIGH/MEDIUM/LOW),
 *   errorCount, totalCount, accuracyRate, lastErrorTime, status,
 *   aiAnalysis, aiSuggestion, analyzedAt, createdAt
 */
export async function getWeakPointList(params = {}) {
  const { userId, subject, status, page = 1, size = 20 } = params;
  const query = new URLSearchParams();
  query.set('userId', userId);
  if (subject) query.set('subject', subject);
  if (status) query.set('status', status);
  query.set('page', page);
  query.set('size', size);

  const res = unwrap(await get(`/api/weak-points?${query.toString()}`));
  return Array.isArray(res) ? res : [];
}

// ─── 2. AI 综合分析（24h 缓存） ─────────────────────

/**
 * 触发/获取 AI 综合分析（同用户 24h 内命中缓存，不消耗 AI 资源）
 * POST /api/weak-points/{userId}/analysis
 *
 * @param {number} userId 用户 ID
 * @returns {Promise<Object>} WeakPointsAnalysisVO
 *   { comprehensiveAnalysis, learningSuggestions, detailAnalyses[], recommendedPriority[] }
 */
export async function analyzeWeakPoints(userId) {
  const res = unwrap(await post(`/api/weak-points/${userId}/analysis`));
  return res || null;
}

// ─── 3. 推荐练习（自动去重 7 天） ───────────────────

/**
 * 获取推荐练习列表（已解决错题优先，其次按薄弱度排序；自动排除 7 天内已做）
 * GET /api/exercises/recommend?userId=&subject=&knowledgePoint=&count=
 *
 * @param {Object} params
 * @param {number} params.userId          用户 ID（必填）
 * @param {string} [params.subject]       学科过滤
 * @param {string} [params.knowledgePoint] 指定知识点
 * @param {number} [params.count]         推荐数量（默认 5）
 * @returns {Promise<Array>} ExerciseVO[]
 *   { exerciseId, knowledgePoint, subject, sourceType(RESOLVED_WEAK_POINT/ACTIVE_WEAK_POINT), priority }
 */
export async function recommendExercises({ userId, subject, knowledgePoint, count = 5 } = {}) {
  const query = new URLSearchParams();
  query.set('userId', userId);
  if (subject) query.set('subject', subject);
  if (knowledgePoint) query.set('knowledgePoint', knowledgePoint);
  query.set('count', count);

  const res = unwrap(await get(`/api/exercises/recommend?${query.toString()}`));
  return Array.isArray(res) ? res : [];
}

// ─── 4. 记录练习结果（实时更新薄弱点 + 上报 M6） ─────

/**
 * 学生每答完一道题回调（M3 实时更新正确率/错误次数/状态）
 * POST /api/exercises/record
 *
 * @param {Object} data
 * @param {number} data.userId         用户 ID
 * @param {string} data.exerciseId     练习记录唯一标识
 * @param {string} data.knowledgePoint 知识点名称（中文）
 * @param {string} data.subject        学科
 * @param {number} data.isCorrect      1=正确，0=错误
 * @returns {Promise<string>} "ok"
 */
export async function recordExercise(data) {
  return unwrap(await post('/api/exercises/record', data));
}

// ─── 5. 推荐具体题目（按薄弱度匹配难度） ─────────────

/**
 * 获取指定知识点的推荐题目（HIGH→EASY / MEDIUM→MEDIUM / LOW→HARD）
 * GET /api/weak-points/recommend-questions?userId=&knowledgePoint=&count=
 *
 * @param {Object} params
 * @param {number} params.userId          用户 ID（必填）
 * @param {string} params.knowledgePoint  知识点名称（必填）
 * @param {number} [params.count]         推荐数量（默认 5）
 * @returns {Promise<Array>} RecommendQuestionVO[]
 *   { questionId, knowledgePoint, subject, difficulty, questionType, questionTitle, reason }
 */
export async function recommendQuestions({ userId, knowledgePoint, count = 5 } = {}) {
  const query = new URLSearchParams();
  query.set('userId', userId);
  query.set('knowledgePoint', knowledgePoint);
  query.set('count', count);

  const res = unwrap(await get(`/api/weak-points/recommend-questions?${query.toString()}`));
  return Array.isArray(res) ? res : [];
}

// ─── 6. 知识图谱 ────────────────────────────────────

/**
 * 获取用户知识图谱（节点 + 边，可直接喂 ECharts/D3/Cytoscape）
 * GET /api/weak-points/knowledge-graph?userId=&subject=
 *
 * @param {Object} params
 * @param {number} params.userId     用户 ID（必填）
 * @param {string} [params.subject]  学科过滤，为空返回所有学科
 * @returns {Promise<Object>} KnowledgeGraphVO
 *   { nodes: [{ id, name, subject, weaknessLevel, masteryRate, group }],
 *     edges: [{ source, target, relation }] }
 */
export async function getKnowledgeGraph({ userId, subject } = {}) {
  const query = new URLSearchParams();
  query.set('userId', userId);
  if (subject) query.set('subject', subject);

  const res = unwrap(await get(`/api/weak-points/knowledge-graph?${query.toString()}`));
  return res || { nodes: [], edges: [] };
}

// ─── 7. 薄弱点改善报告 ──────────────────────────────

/**
 * 获取薄弱点改善报告（WeakPointsImprovementController）
 * GET /api/weak-points/improvement-report?userId=&...
 *
 * @param {Object} params 查询参数（userId 等）
 * @returns {Promise<Object>} WeakPointsImprovementVO
 */
export async function getImprovementReport(params = {}) {
  const query = new URLSearchParams();
  Object.entries(params).forEach(([k, v]) => {
    if (v != null) query.set(k, v);
  });
  const qs = query.toString();
  const res = unwrap(await get(`/api/weak-points/improvement-report${qs ? `?${qs}` : ''}`));
  return res || null;
}
