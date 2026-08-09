/**
 * M4 讲课模块 —— Service 层
 * 
 * Mock 模式：返回 mock 数据（前端独立开发用）
 * 真实模式：调用后端 API（联调时切换）
 * 
 * 搜索 TODO-REAL 切换为真实接口
 */

import { mockParseResult, mockPPTStructure, mockGenerationEvents, mockHistoryList } from '../data/mockLecture';
import { get, post, request } from './api';

// ─── 模式开关 ──────────────────────────────────────

/** 是否使用 Mock 数据（联调时改为 false） */
const USE_MOCK = import.meta.env.VITE_LECTURE_MOCK !== 'false';

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
 * GET /api/lecture/contents
 */
export async function getLectureList(userId, { page = 1, size = 20 } = {}) {
  if (USE_MOCK) {
    await new Promise(r => setTimeout(r, 500));
    return { total: mockHistoryList.length, items: mockHistoryList };
  }
  return get(`/api/lecture/contents?userId=${userId}&page=${page}&size=${size}`);
}

/**
 * 获取讲课内容详情（含完整 PPT 结构）
 * GET /api/lecture/contents/{lectureId}
 */
export async function getLectureDetail(lectureId) {
  if (USE_MOCK) {
    await new Promise(r => setTimeout(r, 300));
    const item = mockHistoryList.find(l => l.lectureId === lectureId);
    return item
      ? { ...item, pptStructure: mockPPTStructure }
      : null;
  }
  return get(`/api/lecture/contents/${lectureId}`);
}

/**
 * 删除讲课内容
 * DELETE /api/lecture/contents/{lectureId}
 */
export async function deleteLecture(lectureId) {
  if (USE_MOCK) {
    await new Promise(r => setTimeout(r, 300));
    const idx = mockHistoryList.findIndex(l => l.lectureId === lectureId);
    if (idx !== -1) mockHistoryList.splice(idx, 1);
    return { success: true };
  }
  return request(`/api/lecture/contents/${lectureId}`, { method: 'DELETE' });
}

/**
 * 发布讲课内容
 * POST /api/lecture/contents/{lectureId}/publish
 */
export async function publishLecture(lectureId) {
  if (USE_MOCK) {
    await new Promise(r => setTimeout(r, 300));
    return { success: true };
  }
  return post(`/api/lecture/contents/${lectureId}/publish`);
}
