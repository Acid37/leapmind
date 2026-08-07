/**
 * ChatPanel 对话服务
 *
 * 对应后端接口（文档：LeapMind教育网站.md → M7 → 4.1）：
 *   POST /api/conversation/ask          SSE 流式对话
 *   POST /api/conversation/interrupt    打断生成
 *   GET  /api/conversation/sessions/{id} 会话恢复
 *
 * ⚠️ 当前为 Mock 实现，标记 TODO-MOCK 处待后端就绪后替换
 */

import { getToken } from '../utils/tokenManager';

// ============================================================
// TODO-MOCK: 替换为真实后端 URL
// const API_BASE = import.meta.env.VITE_API_BASE || '';
// ============================================================

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
 * ⚠️ 对接后端步骤：
 *    1. 取消 import.meta.env.VITE_CHAT_MOCK 判断，直接走真实分支
 *    2. 确认后端 SSE event 格式（event: message / event: interrupt / event: error）
 *    3. 确认 content chunk 的 JSON 结构（{type, chunk, index} 或 {type, content, ...}）
 */
export function askStream({ userId, sessionId, question, sceneType, context }) {
  // ============================================================
  // TODO-MOCK: 整段替换为真实 fetch + SSE 解析
  // ============================================================
  const useMock = import.meta.env.VITE_CHAT_MOCK !== 'false';

  if (!useMock) {
    // ---- 真实 SSE 调用 ----
    // 返回 ReadableStream，内部异步 fetch + pipe SSE
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
            controller.error(new Error(`SSE error: ${res.status}`));
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

  // ---- Mock 实现 ----
  // eslint-disable-next-line no-console
  console.log(`[ChatPanel Mock] askStream: sceneType=${sceneType} question="${question.slice(0, 30)}..."`);

  const mockSessionId = sessionId || `mock-sess-${Date.now()}`;
  const mockReply = buildMockReply(sceneType, question);

  let timer = null;
  let aborted = false;

  return new ReadableStream({
    start(controller) {
      let index = 0;

      function push() {
        if (aborted) return;
        if (index < mockReply.length) {
          controller.enqueue({
            type: 'content',
            chunk: mockReply[index],
            index,
          });
          index++;
          const delay = 30 + Math.random() * 50;
          timer = setTimeout(push, delay);
        } else {
          controller.enqueue({
            type: 'done',
            callId: `mock-call-${Date.now()}`,
            sessionId: mockSessionId,
            tokenUsage: { input: question.length, output: mockReply.length },
          });
          controller.close();
        }
      }

      controller.enqueue({ type: 'thinking', content: '' });
      timer = setTimeout(push, 400);
    },
    cancel() {
      aborted = true;
      if (timer) { clearTimeout(timer); timer = null; }
    },
  });
}

/**
 * 打断当前生成
 *
 * @param {string} sessionId
 * @returns {Promise<void>}
 *
 * ⚠️ 对接后端步骤：
 *    取消 import.meta.env.VITE_CHAT_MOCK 判断
 */
export async function interrupt(sessionId) {
  const useMock = import.meta.env.VITE_CHAT_MOCK !== 'false';

  if (!useMock) {
    await fetch(`/api/conversation/interrupt?sessionId=${sessionId}`, {
      method: 'POST',
      headers: buildAuthHeaders(),
    });
    return;
  }

  // Mock: 无操作
  // eslint-disable-next-line no-console
  console.log(`[ChatPanel Mock] interrupt sessionId=${sessionId}`);
}

/**
 * 恢复会话历史
 *
 * @param {string} sessionId
 * @returns {Promise<{ sessionId:string, sceneType:string, context:Object, messages:Array }>}
 *
 * ⚠️ 对接后端步骤：
 *    取消 import.meta.env.VITE_CHAT_MOCK 判断
 */
export async function getSession(sessionId) {
  const useMock = import.meta.env.VITE_CHAT_MOCK !== 'false';

  if (!useMock) {
    const res = await fetch(`/api/conversation/sessions/${sessionId}`, {
      headers: buildAuthHeaders(),
    });
    if (!res.ok) throw new Error(`Session error: ${res.status}`);
    return res.json();
  }

  // Mock: 返回空会话
  // eslint-disable-next-line no-console
  console.log(`[ChatPanel Mock] getSession sessionId=${sessionId}`);
  return {
    sessionId,
    sceneType: 'general_qa',
    context: {},
    messages: [],
  };
}

// ============================================================
// Mock 工具
// ============================================================

/**
 * Mock: 根据场景生成模拟回复
 */
function buildMockReply(sceneType, question) {
  const templates = {
    doing_exercise: [
      '这道题考察的是',
      '我们来看一下已知条件：',
      question.slice(0, 10) + '...',
      '首先，分析题目类型，',
      '然后回忆相关公式和定理，',
      '接着代入已知数据，',
      '经过计算得出答案。',
    ],
    explaining: [
      '好的，我来给你讲解这道题。',
      '先看题目问的是什么？',
      '这道题的解题关键是：',
      '第一步，理清已知条件；',
      '第二步，套用正确的公式；',
      '第三步，仔细计算；',
      '最后检验答案是否合理。',
    ],
    teaching: [
      '这是一个很好的问题！',
      '我们结合刚才讲的内容来理解：',
      '回顾一下这个知识点：',
      '举个类似的例子帮助理解：',
      '所以总结来说就是：',
      '你理解了吗？可以继续提问。',
    ],
    lesson_prep: [
      '关于这节课的设计，',
      '我建议按照以下思路：',
      '教学目标应该聚焦在：',
      '重难点方面需要注意：',
      '可以设计一个互动环节来巩固：',
    ],
    general_qa: [
      '很好的问题！',
      '让我来帮你分析一下：',
      '从几个角度来看：',
      '第一，',
      '第二，',
      '综上所述，',
      '还有什么想进一步了解的吗？',
    ],
  };

  return templates[sceneType] || templates.general_qa;
}
