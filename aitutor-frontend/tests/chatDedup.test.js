/**
 * ChatPanel 本地智能去重逻辑验证
 *
 * 验证目标：`useChatSession.send()` 在生成中的去重行为
 *   - 首次发送            → undefined（正常发送）
 *   - 生成中相同问题连点   → 'duplicate'（本地忽略，不重复调 AI，省 token）
 *   - 生成中不同问题连点   → 'busy'（提示等待，避免并发流）
 *   - 流完成后再次发送相同问题 → undefined（可重新发送）
 *   - 去重生效时 askStream 只被调用 1 次
 */
/* global describe, test, expect */

import { jest } from '@jest/globals';

// ── mock chatService（ESM 模式需用 unstable_mockModule + 动态 import） ──
jest.unstable_mockModule('../src/services/chatService', () => ({
  askStream: jest.fn(),
  interrupt: jest.fn(),
  getSession: jest.fn(),
  ChatError: class ChatError extends Error {
    constructor(code, message) { super(message); this.code = code; }
  },
}));

const chatService = await import('../src/services/chatService');
const { useChatSession } = await import('../src/hooks/useChatSession');

import { renderToString } from 'react-dom/server';
import React from 'react';

// 受控 SSE 流：保持打开，测试手动调用 complete() 触发 done 事件
function makeControlledStream() {
  let controllerRef;
  const stream = new ReadableStream({
    start(controller) {
      controllerRef = controller;
      controllerRef.enqueue({ type: 'thinking', sessionId: 's1' });
    },
  });
  return {
    stream,
    complete() {
      controllerRef.enqueue({ type: 'done', callId: 'c1', sessionId: 's1', tokenUsage: {} });
      controllerRef.close();
    },
  };
}

describe('useChatSession 本地智能去重', () => {
  test('生成中相同问题连点返回 duplicate，且 askStream 只调用一次（省 token）', async () => {
    // 每次发送返回新的受控流（同一 ReadableStream 只能被 getReader 一次）
    const controlledStreams = [];
    chatService.askStream.mockImplementation(() => {
      const c = makeControlledStream();
      controlledStreams.push(c);
      return c.stream;
    });

    // 用 react-dom/server 渲染一个包装组件捕获 send 引用（node 环境无 DOM，SSR 可执行 hook 主体）
    const captureRef = { current: null };
    function Capture() {
      const { send } = useChatSession({
        sceneType: 'teaching',
        context: { lectureId: 1 },
        userId: 1001,
        autoRestore: false,
      });
      captureRef.current = send;
      return null;
    }
    renderToString(React.createElement(Capture));
    const send = captureRef.current;
    expect(send).toBeDefined();

    // 1. 首次发送 → undefined（正常发送）
    expect(send('为什么选B')).toBeUndefined();
    expect(chatService.askStream).toHaveBeenCalledTimes(1);

    // 2. 生成中（isGeneratingRef 同步置 true）相同问题 → duplicate
    expect(send('为什么选B')).toBe('duplicate');

    // 3. 生成中不同问题 → busy
    expect(send('那 C 选项呢？')).toBe('busy');

    // 去重生效：整个期间 askStream 只被调用 1 次
    expect(chatService.askStream).toHaveBeenCalledTimes(1);

    // 4. 流完成后（done 事件 → finishGenerating 解锁）再次发送相同问题 → undefined
    controlledStreams[0].complete();
    await new Promise((r) => setTimeout(r, 20)); // 等待 reader.read() 消费 done 事件
    expect(send('为什么选B')).toBeUndefined();
    expect(chatService.askStream).toHaveBeenCalledTimes(2);
  });

  test('空文本不发送', async () => {
    chatService.askStream.mockClear();
    chatService.askStream.mockImplementation(() => makeControlledStream().stream);

    const captureRef = { current: null };
    function Capture() {
      const { send } = useChatSession({
        sceneType: 'teaching',
        context: { lectureId: 1 },
        userId: 1001,
        autoRestore: false,
      });
      captureRef.current = send;
      return null;
    }
    renderToString(React.createElement(Capture));
    const send = captureRef.current;

    expect(send('   ')).toBeUndefined();
    expect(send('')).toBeUndefined();
    expect(chatService.askStream).not.toHaveBeenCalled();
  });
});
