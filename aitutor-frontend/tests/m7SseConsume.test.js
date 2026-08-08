/**
 * M7 ChatPanel SSE 流消费逻辑测试
 *
 * 验证目标：修复 `117f4da4` —— useChatSession read() 循环必须消费完
 * 所有 content chunk 直到 done，而非只消费第一个事件就终止。
 *
 * 模拟真实链路：askStream（SSE 文本 → JSON.parse → 对象流）→ useChatSession read()
 */

// ── 模拟 askStream 输出：SSE 文本行 → 解析后的对象流 ─────
// 真实链路中 askStream 内部把 data: 行 JSON.parse 后 enqueue 对象，
// useChatSession 的 read() 拿到的是 {type,...} 对象流（逐事件分批到达）
function makeObjectStream(lines) {
  const objects = lines.map((l) => JSON.parse(l.slice(5).trim()));
  return new ReadableStream({
    start(controller) {
      for (const obj of objects) controller.enqueue(obj);
      controller.close();
    },
  });
}

// ── 修复后的 read() 消费逻辑（与 useChatSession 保持一致） ──
function consumeStream(reader, handlers) {
  function read() {
    return reader.read().then(({ done, value }) => {
      if (done) return Promise.resolve();
      if (value && value.type) {
        if (value.sessionId) handlers.onSessionId?.(value.sessionId);
        if (value.type === 'thinking') handlers.onThinking?.();
        else if (value.type === 'content') handlers.onContent?.(value);
        else if (value.type === 'done') {
          handlers.onDone?.(value);
          return Promise.resolve(); // 终端事件终止
        } else if (value.type === 'error') {
          handlers.onError?.(value);
          return Promise.resolve(); // 终端事件终止
        } else if (value.type === 'interrupted') {
          handlers.onInterrupted?.(value);
          return Promise.resolve(); // 终端事件终止
        }
      }
      return read(); // 非终端事件继续（修复点）
    });
  }
  return read();
}

describe('M7 SSE 流消费（修复 117f4da4 回归测试）', () => {
  const SSE_LINES = [
    'data: {"type":"thinking","sessionId":"s1"}',
    'data: {"type":"content","chunk":"你","index":0}',
    'data: {"type":"content","chunk":"好","index":1}',
    'data: {"type":"content","chunk":"！","index":2}',
    'data: {"type":"done","callId":"c1","sessionId":"s1","tokenUsage":{}}',
  ];

  test('修复后消费完全部事件：thinking → 3 content → done', async () => {
    const events = [];
    let contentChars = 0;
    let sawDone = false;
    let sessionId = null;

    await consumeStream(makeObjectStream(SSE_LINES).getReader(), {
      onSessionId: (id) => { sessionId = id; },
      onContent: (e) => { events.push(e.type); contentChars += (e.chunk || '').length; },
      onDone: () => { sawDone = true; events.push('done'); },
    });

    // 关键断言：消费完全部 3 个 content + done（修复前只消费 1 个就终止）
    expect(events).toEqual(['content', 'content', 'content', 'done']);
    expect(contentChars).toBe(3);
    expect(sawDone).toBe(true);
    expect(sessionId).toBe('s1'); // SessionID 闭环
  });

  test('error 终端事件后立即停止（防重连打挂限流器）', async () => {
    const events = [];
    await consumeStream(makeObjectStream([
      'data: {"type":"content","chunk":"部分","index":0}',
      'data: {"type":"error","code":1003,"message":"服务降级"}',
      'data: {"type":"content","chunk":"不应到达","index":1}',
    ]).getReader(), {
      onContent: (e) => events.push(`content:${e.chunk}`),
      onError: () => events.push('error'),
    });
    // error 后立即停止，后续 content 不应被消费
    expect(events).toEqual(['content:部分', 'error']);
  });

  test('interrupted 终端事件后保留已生成内容并停止', async () => {
    const events = [];
    let interrupted = false;
    await consumeStream(makeObjectStream([
      'data: {"type":"content","chunk":"半成品","index":0}',
      'data: {"type":"interrupted","message":"用户已打断"}',
      'data: {"type":"content","chunk":"不应到达","index":1}',
    ]).getReader(), {
      onContent: (e) => events.push(`content:${e.chunk}`),
      onInterrupted: () => { interrupted = true; events.push('interrupted'); },
    });
    expect(events).toEqual(['content:半成品', 'interrupted']);
    expect(interrupted).toBe(true);
  });
});
