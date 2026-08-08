/* global describe, test, expect */

import {
  isSessionComplete,
  migrateSessionAnswers,
  withSessionQuestionKeys,
} from '../src/utils/practiceSession.js';

describe('M1 练习会话题位状态', () => {
  test('10 道题即使意外使用相同 questionId，答对第 1 道也不能完成整组练习', () => {
    const session = withSessionQuestionKeys({
      questions: Array.from({ length: 10 }, () => ({ questionId: 9 })),
    });
    const firstKey = session.questions[0].sessionQuestionKey;
    const answers = {
      [firstKey]: { submitted: true, isCorrect: true },
    };

    expect(session.questions.map((question) => question.sessionQuestionKey)).toEqual([
      'slot:0:9', 'slot:1:9', 'slot:2:9', 'slot:3:9', 'slot:4:9',
      'slot:5:9', 'slot:6:9', 'slot:7:9', 'slot:8:9', 'slot:9:9',
    ]);
    expect(isSessionComplete(session, answers)).toBe(false);
  });

  test('只有每个会话题位都提交后，才视为练习完成', () => {
    const session = withSessionQuestionKeys({
      questions: [{ questionId: 9 }, { questionId: 9 }],
    });
    const answers = Object.fromEntries(session.questions.map((question) => [
      question.sessionQuestionKey,
      { submitted: true, isCorrect: true },
    ]));

    expect(isSessionComplete(session, answers)).toBe(true);
  });

  test('恢复旧版 questionId key 的缓存时，迁移到独立会话题位', () => {
    const session = withSessionQuestionKeys({
      questions: [{ questionId: 9 }, { questionId: 10 }],
    });
    const migrated = migrateSessionAnswers(session, {
      9: { submitted: true, isCorrect: true },
      10: { submitted: false, isCorrect: null },
    });

    expect(migrated['slot:0:9'].submitted).toBe(true);
    expect(migrated['slot:1:10'].submitted).toBe(false);
    expect(isSessionComplete(session, migrated)).toBe(false);
  });
});
