/* global describe, test, expect */

import { readFile } from 'node:fs/promises';

const SOURCE_PATH = new URL('../src/components/chat/ContextIndicator.jsx', import.meta.url);

async function loadBuildContextDetail() {
  const source = await readFile(SOURCE_PATH, 'utf8');
  const match = source.match(/function buildContextDetail\(sceneType, context\) \{([\s\S]*?)\n\}/);
  if (!match) throw new Error('未找到 buildContextDetail');
  return new Function('sceneType', 'context', match[1]);
}

describe('M1 ChatPanel 题号上下文', () => {
  test('优先显示练习会话题号，而不是题库数据库主键', async () => {
    const buildContextDetail = await loadBuildContextDetail();

    expect(buildContextDetail('doing_exercise', {
      questionId: 9,
      questionNumber: 1,
      totalQuestions: 15,
    })).toBe('第 1 / 15 题');
  });

  test('兼容旧调用：未传会话题号时仍展示题库主键', async () => {
    const buildContextDetail = await loadBuildContextDetail();

    expect(buildContextDetail('doing_exercise', { questionId: 9 })).toBe('题目 #9');
  });
});
