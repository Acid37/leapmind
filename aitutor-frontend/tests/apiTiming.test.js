import { describe, test, expect, beforeEach, afterEach, jest } from '@jest/globals';


async function askQuestionMock() {
  const res = await fetch('http://localhost:8080/api/voice-chat/ask', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ sessionId: 'test-session', question: 'test-question' }),
  });
  return res.json();
}

describe('API响应时间 - 边界值（单变量）测试&健壮值测试', () => {
  //前置操作与后置操作
  beforeEach(() => {
    jest.useRealTimers();     
    global.fetch = jest.fn();
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  //边界值测试
  const boundaryValueCase = [
    {delay:0, label: '0ms(最小值）'},
    {delay:1, label: '1ms(次小值）'},
    {delay:1500, label: '1500ms(中值）'},
    {delay:2999, label: '2999ms(次大值）'},
    {delay:3000, label: '3000ms(最大值）'},

  ]

  //健壮值测试
  const robustnessCase = [
    {delay:-1, label: '-1ms(异常小值）'},
    {delay:0, label: '0ms(最小值）'},
    {delay:1, label: '1ms(次小值）'},
    {delay:1500, label: '1500ms(中值）'},
    {delay:2999, label: '2999ms(次大值）'},
    {delay:3000, label: '3000ms(最大值）'},
    {delay:3001, label: '3001ms(异常大值）'},
  ]

  test.each(boundaryValueCase)('边界值测试 - 应在$delay内返回成功',async({delay})=>{
    const delayForTimer = Math.max(0, delay);
    fetch.mockImplementationOnce(() =>
        new Promise(resolve => {
          setTimeout(() => resolve({ ok: true, json: async () => ({ answer: 'ok' }) }), delayForTimer);
        })
      );
      const t0 = performance.now();
      const data = await askQuestionMock();
      const elapsed = performance.now() - t0;

      expect(data.answer).toBe('ok');
      const expectedMin = Math.max(0, delay - 5);
      console.log(`[单变量-边界] delay=${delay}ms expected=[${expectedMin}, ${delayForTimer + 250}) elapsed=${elapsed.toFixed(2)}ms`);
      expect(elapsed).toBeGreaterThanOrEqual(expectedMin);
      expect(elapsed).toBeLessThan(delayForTimer + 250);
  })

  test.each(robustnessCase)('健壮值测试 - 应在$delay内返回成功',async({delay})=>{
    const delayForTimer = Math.max(0, delay);
    fetch.mockImplementationOnce(() =>
        new Promise(resolve => {
          setTimeout(() => resolve({ ok: true, json: async () => ({ answer: 'ok' }) }), delayForTimer);
        })
      );
      const t0 = performance.now();
      const data = await askQuestionMock();
      const elapsed = performance.now() - t0;

      expect(data.answer).toBe('ok');
      const expectedMin = Math.max(0, delay - 5);
      console.log(`[单变量-健壮] delay=${delay}ms expected=[${expectedMin}, ${delayForTimer + 250}) elapsed=${elapsed.toFixed(2)}ms`);
      expect(elapsed).toBeGreaterThanOrEqual(expectedMin);
      expect(elapsed).toBeLessThan(delayForTimer + 250);
  })

});

describe('接口延时x并发数（双变量）边界值测试', () => {
  //前置操作与后置操作
  beforeEach(() => {
    jest.useRealTimers();
    global.fetch = jest.fn();
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  // 边界值分析
  // delay边界值: 最小值(0), 次小值(1), 中值(1500), 次大值(2999), 最大值(3000)
  // concurrency边界值: 最小值(1), 次小值(2), 中值(5), 次大值(9), 最大值(10)
  
  // 单缺陷假设测试用例：2*4+1=9个
  // - delay取极值(最小值、次小值、次大值、最大值，共4个)，concurrency取中值(5)
  // - concurrency取极值(最小值、次小值、次大值、最大值，共4个)，delay取中值(1500)
  // - 两个都取中值：1个
  const delayConcurrencyBoundaryCases = [
    // delay取极值，concurrency取中值
    { delay: 0, concurrency: 5, label: 'delay=0ms(最小值) × concurrency=5(中值)' },
    { delay: 1, concurrency: 5, label: 'delay=1ms(次小值) × concurrency=5(中值)' },
    { delay: 2999, concurrency: 5, label: 'delay=2999ms(次大值) × concurrency=5(中值)' },
    { delay: 3000, concurrency: 5, label: 'delay=3000ms(最大值) × concurrency=5(中值)' },
    // concurrency取极值，delay取中值
    { delay: 1500, concurrency: 1, label: 'delay=1500ms(中值) × concurrency=1(最小值)' },
    { delay: 1500, concurrency: 2, label: 'delay=1500ms(中值) × concurrency=2(次小值)' },
    { delay: 1500, concurrency: 9, label: 'delay=1500ms(中值) × concurrency=9(次大值)' },
    { delay: 1500, concurrency: 10, label: 'delay=1500ms(中值) × concurrency=10(最大值)' },
    // 两个都取中值
    { delay: 1500, concurrency: 5, label: 'delay=1500ms(中值) × concurrency=5(中值)' },
  ];

  const rows = [];

  test.each(delayConcurrencyBoundaryCases)('边界值测试 - $label', async ({ delay, concurrency, label }) => {
    const delayForTimer = Math.max(0, delay);
    fetch.mockImplementation(() =>
      new Promise(resolve => {
        setTimeout(() => resolve({ ok: true, json: async () => ({ answer: 'ok' }) }), delayForTimer);
      })
    );

    const t0 = performance.now();
    const tasks = Array.from({ length: concurrency }, () => askQuestionMock());
    const results = await Promise.all(tasks);
    const elapsed = performance.now() - t0;

    // 所有响应均成功
    results.forEach(r => expect(r.answer).toBe('ok'));
    // 命中次数等于并发数
    expect(fetch).toHaveBeenCalledTimes(concurrency);

    // 总耗时应接近单次延迟（并发执行），允许调度开销和计时抖动
    const expectedMin = Math.max(0, delay - 5);
    const slack = 400; // 允许并发调度/计时抖动
    const expectedMax = delayForTimer + slack;
    const pass = elapsed >= expectedMin && elapsed < expectedMax;
    const errorType = pass ? '无' : (elapsed < expectedMin ? 'Underflow' : (elapsed >= expectedMax ? 'Overflow' : 'AssertionError'));
    const expectedStr = `ok × ${concurrency}, elapsed∈[${expectedMin}, ${expectedMax})`;
    const actualStr = `ok × ${concurrency}, elapsed=${elapsed.toFixed(2)}ms`;
    rows.push({ 用例: label, 预计输出: expectedStr, 实际输出: actualStr, 错误类型: errorType });
    expect(elapsed).toBeGreaterThanOrEqual(expectedMin);
    expect(elapsed).toBeLessThan(expectedMax);
  });

  afterAll(() => {
    if (rows.length) console.table(rows);
  });
});

describe('接口延时x并发数（双变量） 健壮性测试', () => {
  //前置操作与后置操作
  beforeEach(() => {
    jest.useRealTimers();
    global.fetch = jest.fn();
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  // 健壮值扩展（边界值 + 无效输入）
  // delay: min-(-1), min(0), 次小(1), 中值(1500), 次大(2999), max(3000), max+(3001)
  // concurrency: min-(0), min(1), 次小(2), 中值(5), 次大(9), max(10), max+(11)
  // 单缺陷：2*6 + 1 = 13 个用例
  const delayExtremes = [
    { delay: -1, label: 'delay=-1ms(min-)' },
    { delay: 0, label: 'delay=0ms(min)' },
    { delay: 1, label: 'delay=1ms(次小)' },
    { delay: 2999, label: 'delay=2999ms(次大)' },
    { delay: 3000, label: 'delay=3000ms(max)' },
    { delay: 3001, label: 'delay=3001ms(max+)' },
  ];
  const concurrencyExtremes = [
    { concurrency: 0, label: 'concurrency=0(min-)' },
    { concurrency: 1, label: 'concurrency=1(min)' },
    { concurrency: 2, label: 'concurrency=2(次小)' },
    { concurrency: 9, label: 'concurrency=9(次大)' },
    { concurrency: 10, label: 'concurrency=10(max)' },
    { concurrency: 11, label: 'concurrency=11(max+)' },
  ];

  const middle = { delay: 1500, concurrency: 5 };

  const robustnessCases = [
    ...delayExtremes.map(d => ({ delay: d.delay, concurrency: middle.concurrency, label: `${d.label} × concurrency=5(中值)` })),
    ...concurrencyExtremes.map(c => ({ delay: middle.delay, concurrency: c.concurrency, label: `delay=1500ms(中值) × ${c.label}` })),
    { delay: middle.delay, concurrency: middle.concurrency, label: 'delay=1500ms(中值) × concurrency=5(中值)' },
  ];

  const rows = [];

  test.each(robustnessCases)('健壮性测试 - $label', async ({ delay, concurrency, label }) => {
    const delayForTimer = Math.max(0, delay);
    fetch.mockImplementation(() =>
      new Promise(resolve => {
        setTimeout(() => resolve({ ok: true, json: async () => ({ answer: 'ok' }) }), delayForTimer);
      })
    );

    const t0 = performance.now();
    const tasks = Array.from({ length: Math.max(0, concurrency) }, () => askQuestionMock());
    const results = await Promise.all(tasks);
    const elapsed = performance.now() - t0;

    if (concurrency <= 0) {
      const called = fetch.mock.calls.length;
      const pass = called === 0 && results.length === 0 && elapsed < 50;
      const expectedStr = '不发请求，0 次调用，快速返回(<50ms)';
      const actualStr = `调用数=${called} 次，返回数=${results.length}，elapsed=${elapsed.toFixed(2)}ms`;
      const errorType = pass ? '无' : 'InvalidInputHandling';
      rows.push({ 用例: label, 预计输出: expectedStr, 实际输出: actualStr, 错误类型: errorType });
      expect(fetch).toHaveBeenCalledTimes(0);
      expect(results.length).toBe(0);
      expect(elapsed).toBeLessThan(50);
      return;
    }

    results.forEach(r => expect(r.answer).toBe('ok'));
    expect(fetch).toHaveBeenCalledTimes(concurrency);

    const expectedMin = Math.max(0, delay - 5);
    const slack = 450;
    const expectedMax = delayForTimer + slack;
    const pass = elapsed >= expectedMin && elapsed < expectedMax;
    const errorType = pass ? '无' : (elapsed < expectedMin ? 'Underflow' : (elapsed >= expectedMax ? 'Overflow' : 'AssertionError'));
    const expectedStr = `ok × ${concurrency}, elapsed∈[${expectedMin}, ${expectedMax})`;
    const actualStr = `ok × ${concurrency}, elapsed=${elapsed.toFixed(2)}ms`;
    rows.push({ 用例: label, 预计输出: expectedStr, 实际输出: actualStr, 错误类型: errorType });
    expect(elapsed).toBeGreaterThanOrEqual(expectedMin);
    expect(elapsed).toBeLessThan(expectedMax);
  });

  afterAll(() => {
    if (rows.length) console.table(rows);
  });
});

