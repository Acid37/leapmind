/**
 * ChatPanel 对话服务
 *
 * 对应后端接口（文档：LeapMind教育网站.md → M7 → 4.1）：
 *   POST /api/conversation/ask          SSE 流式对话
 *   POST /api/conversation/interrupt    打断生成
 *   GET  /api/conversation/sessions/{id} 会话恢复
 *
 * ✅ 已对接真实后端（backend-M7 ConversationController，SSE 流式）
 */

import { getToken } from '../utils/tokenManager';

/**
 * 结构化错误，携带错误码便于前端差异化处理
 *
 * 错误码对应后端 exception/BizErrorCode.java：
 *   1001 RATE_LIMITED     — 限流，不显示重试
 *   1003 SERVICE_DEGRADED — 降级，显示重试
 *   2001 AI_TIMEOUT       — 超时，显示重试
 */
export class ChatError extends Error {
  constructor(code, message) {
    super(message);
    this.name = 'ChatError';
    this.code = code;
  }
}

/**
 * 构建带认证的请求头
 */
function buildAuthHeaders(extra = {}) {
  const headers = {
    'Content-Type': 'application/json',
    ...extra,
  };
  const token = getToken();
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }
  return headers;
}

/**
 * SSE 流式对话请求
 *
 * @param {Object} params
 * @param {number} params.userId
 * @param {string}  [params.sessionId]   不传则后端新建
 * @param {string}  params.question      用户问题文本
 * @param {string}  params.sceneType     doing_exercise | explaining | teaching | lesson_prep | general_qa
 * @param {Object}  [params.context]     场景上下文 { questionId?, currentSlide?, relatedKpId?, ... }
 * @param {string}  [params.inputType]   text | voice | image
 * @param {string[]}[params.attachmentUrls]
 *
 * @returns {ReadableStream} 流式响应，每个 chunk 为 SSE data 行解析后的 JSON 对象
 *
 * SSE 事件格式（后端 ConversationService 输出）：
 *   data: {"type":"thinking","content":"","sessionId":"..."}
 *   data: {"type":"content","chunk":"文","index":0}
 *   data: {"type":"done","callId":"...","sessionId":"...","tokenUsage":{...}}
 *   data: {"type":"error","message":"..."}
 */
export function askStream({ userId, sessionId, question, sceneType, context }) {
  // ---- 真实 SSE 调用：返回 ReadableStream，内部异步 fetch + pipe SSE ----
  let fetchReader = null;

  return new ReadableStream({
    async start(controller) {
      try {
        const url = `/api/conversation/ask`;
        const res = await fetch(url, {
          method: 'POST',
          headers: buildAuthHeaders({
            'Accept': 'text/event-stream',
          }),
          body: JSON.stringify({
            userId,
            sessionId,
            question,
            sceneType,
            context: context || {},
            inputType: 'text',
            attachmentUrls: [],
          }),
        });
        if (!res.ok) {
          if (res.status === 429) {
            controller.error(new ChatError(1001, '您提问的频率有点快，请稍等片刻后再试。'));
          } else if (res.status === 503) {
            controller.error(new ChatError(1003, '服务暂时降级，请稍后重新尝试。'));
          } else {
            controller.error(new Error(`SSE error: ${res.status}`));
          }
          return;
        }
        fetchReader = res.body.getReader();
        const decoder = new TextDecoder('utf-8');
        let buffer = '';
        while (true) {
          const { done, value } = await fetchReader.read();
          if (done) {
            controller.close();
            return;
          }
          buffer += decoder.decode(value, { stream: true });
          const lines = buffer.split('\n');
          buffer = lines.pop() || '';
          for (const line of lines) {
            if (line.startsWith('data:')) {
              const data = line.slice(5).trim();
              if (data && data !== '[DONE]') {
                try { controller.enqueue(JSON.parse(data)); } catch { /* skip malformed */ }
              }
            }
          }
        }
      } catch (err) {
        controller.error(err);
      }
    },
    cancel() {
      if (fetchReader) {
        try { fetchReader.cancel(); } catch { /* ignore */ }
      }
    },
  });
}

/**
 * 打断当前生成
 *
 * @param {string} sessionId
 * @returns {Promise<void>}
 *
 */
export async function interrupt(sessionId) {
  await fetch(`/api/conversation/interrupt?sessionId=${sessionId}`, {
    method: 'POST',
    headers: buildAuthHeaders(),
  });
}

/**
 * 恢复会话历史
 *
 * @param {string} sessionId
 * @returns {Promise<{ sessionId:string, sceneType:string, context:Object, messages:Array }>}
 *
 */
export async function getSession(sessionId) {
  const res = await fetch(`/api/conversation/sessions/${sessionId}`, {
    headers: buildAuthHeaders(),
  });
  if (!res.ok) throw new Error(`Session error: ${res.status}`);
  return res.json();
}
