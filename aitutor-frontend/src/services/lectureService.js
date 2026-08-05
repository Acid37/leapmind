/**
 * M4 讲课模块 —— Service 层
 * 
 * 已对接真实后端（2026-08-03）：
 *   - 讲课内容 CRUD（历史列表/详情/删除/发布）→ Java GET/PUT/DELETE /api/lesson-prep/contents
 *   - 薄弱知识点查询 → GET /api/weak-points（M3 曾俊桥 / develop 分支）
 *   - 讲课文件解析 / 讲课生成 SSE → 等许沣睿的 /api/lecture/*（backend-M4 尚未实现）
 * 
 * 模式说明：
 *   - CRUD + 薄弱点：默认走真实后端（设置 VITE_LECTURE_MOCK=true 回退 Mock）
 *   - 文件解析 / 讲课生成：仍走 Mock（许沣睿 backend-M4 /api/lecture/* 未实现）
 */

import { mockParseResult, mockPPTStructure, mockGenerationEvents, mockHistoryList } from '../data/mockLecture';
import { get, request } from './api';

// ─── 模式开关 ──────────────────────────────────────

/**
 * Mock 开关
 * - 默认走真实后端（VITE_LECTURE_MOCK 不设或为 false）。
 * - 开发期如需回退 Mock：设置环境变量 VITE_LECTURE_MOCK=true。
 * - 文件解析 / 讲课生成目前始终走 Mock（等许沣睿 /api/lecture/* 就绪后移除）。
 */
const USE_MOCK = import.meta.env.VITE_LECTURE_MOCK === 'true';

/**
 * 解包后端统一响应 ApiResponse：{ code, message, data, timestamp }
 * 与 m2.js 的对接模式保持一致
 */
function unwrap(res) {
  if (res && res.code === 200) return res.data;
  return res;
}

/**
 * 后端 TeachingContentVO（prepId 等）→ 前端历史卡片（lectureId 等）
 */
function toLectureItem(vo = {}) {
  return {
    lectureId: vo.prepId ?? vo.id,
    title: vo.title || '',
    status: vo.status || 'draft',
    createdAt: vo.createdAt || '',
    // pptStructure 是 JSON 字符串，解析为 slides
    slides: parsePptStructure(vo.pptStructure),
  };
}

/**
 * 后端 pptStructure（JSON 字符串）→ 前端 slides 数组
 * 格式见 SlideRenderer_接口约定.md：page_num/bullet_points/...（snake_case 扁平）
 */
function parsePptStructure(pptStructure) {
  if (!pptStructure) return [];
  try {
    const parsed = typeof pptStructure === 'string' ? JSON.parse(pptStructure) : pptStructure;
    const slides = Array.isArray(parsed) ? parsed : parsed?.slides;
    if (!Array.isArray(slides)) return [];
    // 后端 snake_case → 前端 SlideData 驼峰
    return slides.map((s) => ({
      pageNum: s.page_num ?? s.pageNum,
      type: s.type || 'content',
      title: s.title || '',
      bulletPoints: s.bullet_points ?? s.bulletPoints ?? [],
      imageSuggestion: s.image_suggestion ?? s.imageSuggestion,
      formula: s.formula,
      highlightPoints: s.highlight_points ?? s.highlightPoints ?? [],
      interaction: s.interaction ?? null,
    }));
  } catch {
    return [];
  }
}

// ─── 0. 薄弱知识点查询（M3 曾俊桥 / develop 分支，始终走真实） ──

/**
 * 查询用户薄弱知识点列表
 * GET /api/weak-points?userId=&subject=&status=
 * 
 * 后端返回 ApiResponse<List<UserWeakPointVO>>
 * UserWeakPointVO: { id, knowledgePoint, subject, weaknessLevel, errorCount, accuracyRate, status }
 * 
 * 映射为前端 WeakPoint：{ kpId, kpName, weaknessScore }
 * - kpId            ← id（数据库主键，唯一标识该薄弱点记录）
 * - kpName          ← knowledgePoint
 * - weaknessScore   ← 1 - accuracyRate（准确率越低薄弱度越高）
 *                     若 accuracyRate 为空则按 weaknessLevel 估算：HIGH=0.75, MEDIUM=0.5, LOW=0.25
 */
export async function getWeakPoints(userId) {
  try {
    const res = unwrap(await get(`/api/weak-points?userId=${userId}&status=ACTIVE`));
    const list = Array.isArray(res) ? res : [];
    return list
      .map((wp) => ({
        kpId: wp.id,
        kpName: wp.knowledgePoint || '',
        weaknessScore: wp.accuracyRate != null
          ? Math.round((1 - parseFloat(wp.accuracyRate)) * 100) / 100
          : { HIGH: 0.75, MEDIUM: 0.50, LOW: 0.25 }[wp.weaknessLevel] ?? 0.30,
        subject: wp.subject || '',
      }))
      .sort((a, b) => b.weaknessScore - a.weaknessScore); // 最薄弱排最前
  } catch (err) {
    console.warn('获取薄弱知识点失败，返回空列表:', err);
    return [];
  }
}

// ─── 1. 文件解析 ───────────────────────────────────

/**
 * 上传文件并解析内容
 * POST /api/lecture/parse-file (multipart/form-data)
 * 
 * @param {File} file - 上传的文件
 * @param {number} userId
 * @returns {Promise<Object>} { fileId, fileUrl, parsedContent }
 */
export async function parseLectureFile(file, userId) {
  if (USE_MOCK) {
    // 模拟网络延迟
    await new Promise(r => setTimeout(r, 1500));
    return { ...mockParseResult };
  }

  // TODO-REAL: 真实 multipart 上传
  const formData = new FormData();
  formData.append('file', file);
  formData.append('userId', String(userId));

  return request('/api/lecture/parse-file', {
    method: 'POST',
    body: formData,
    headers: {}, // 让浏览器自动设置 Content-Type: multipart/form-data
  });
}

// ─── 2. 讲课生成（SSE 流式） ───────────────────────

/**
 * 生成讲课内容（SSE 流式）
 * POST /api/lecture/generate → SSE
 * 
 * @param {Object} params
 * @param {number} params.userId
 * @param {string} params.sourceType   - file | text | from_weakpoint
 * @param {string} [params.sourceId]   - 文件 ID
 * @param {string} [params.textContent] - 文本内容
 * @param {number[]} [params.weakPointIds]
 * @param {string} [params.style]      - 讲课风格
 * @param {number} [params.duration]   - 期望时长（分钟）
 * @param {string} [params.grade]
 * @param {function} onEvent           - 回调: ({ type, ...data }) => void
 * @returns {Promise<Object>} 最终结果 { lectureId, totalPages, slides }
 */
export async function generateLecture(params, onEvent) {
  if (USE_MOCK) {
    // 模拟 SSE 事件流
    for (const event of mockGenerationEvents) {
      await new Promise(r => setTimeout(r, event.delay - (mockGenerationEvents[0].delay || 0) > 0
        ? 800 : event.delay)); // 压缩到约 800ms/事件
      // 重构播放延迟
    }

    // 逐个发送事件
    let lastDelay = 0;
    for (const event of mockGenerationEvents) {
      const wait = Math.max(200, event.delay - lastDelay);
      await new Promise(r => setTimeout(r, wait));
      lastDelay = event.delay;
      onEvent(event);
    }

    return {
      lectureId: mockPPTStructure.lectureId,
      totalPages: mockPPTStructure.totalPages,
      slides: mockPPTStructure.slides,
    };
  }

  // TODO-REAL: 真实 SSE 流式调用
  const response = await fetch('/api/lecture/generate', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(params),
  });

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';
  let result = {};

  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });
    const lines = buffer.split('\n');
    buffer = lines.pop() || '';
    for (const line of lines) {
      if (line.startsWith('data: ')) {
        try {
          const data = JSON.parse(line.slice(6));
          onEvent(data);
          if (data.type === 'done') result = data;
        } catch { /* ignore parse errors */ }
      }
    }
  }

  return result;
}

// ─── 3. 讲课内容管理 ───────────────────────────────

/**
 * 获取讲课内容列表
 * 后端 Java：GET /api/lesson-prep/contents?userId=
 */
export async function getLectureList(userId) {
  if (USE_MOCK) {
    await new Promise(r => setTimeout(r, 500));
    return { total: mockHistoryList.length, items: mockHistoryList };
  }
  // 后端 Java：GET /api/lesson-prep/contents?userId= &status=
  // 返回 ApiResponse<List<TeachingContentVO>>
  const res = unwrap(await get(`/api/lesson-prep/contents?userId=${userId}`));
  const list = Array.isArray(res) ? res : res?.list || [];
  return { total: list.length, items: list.map(toLectureItem) };
}

/**
 * 获取讲课内容详情（含完整 PPT 结构）
 * 后端 Java：GET /api/lesson-prep/contents/{prepId}
 */
export async function getLectureDetail(lectureId) {
  if (USE_MOCK) {
    await new Promise(r => setTimeout(r, 300));
    const item = mockHistoryList.find(l => l.lectureId === lectureId);
    return item
      ? { ...item, pptStructure: mockPPTStructure }
      : null;
  }
  const vo = unwrap(await get(`/api/lesson-prep/contents/${lectureId}`));
  return vo ? { ...toLectureItem(vo), pptStructure: vo.pptStructure } : null;
}

/**
 * 删除讲课内容
 * 后端 Java：DELETE /api/lesson-prep/contents/{prepId}
 */
export async function deleteLecture(lectureId) {
  if (USE_MOCK) {
    await new Promise(r => setTimeout(r, 300));
    const idx = mockHistoryList.findIndex(l => l.lectureId === lectureId);
    if (idx !== -1) mockHistoryList.splice(idx, 1);
    return { success: true };
  }
  return request(`/api/lesson-prep/contents/${lectureId}`, { method: 'DELETE' });
}

/**
 * 发布讲课内容
 * 后端 Java：PUT /api/lesson-prep/contents/{prepId}（status → published）
 */
export async function publishLecture(lectureId) {
  if (USE_MOCK) {
    await new Promise(r => setTimeout(r, 300));
    return { success: true };
  }
  return request(`/api/lesson-prep/contents/${lectureId}`, {
    method: 'PUT',
    body: JSON.stringify({ status: 'published' }),
  });
}
