/**
 * M1 会话题位状态工具。
 *
 * questionId 是题库主键，可能重复或缺失；作答状态必须以会话题位 key 保存，
 * 否则答第 1 题会覆盖同 ID 的其余题并误判会话完成。
 */
export function getSessionQuestionKey(question, index) {
  return question?.sessionQuestionKey ?? `slot:${index}:${question?.questionId ?? 'unknown'}`;
}

export function withSessionQuestionKeys(sessionData) {
  if (!sessionData?.questions) return sessionData;
  return {
    ...sessionData,
    questions: sessionData.questions.map((question, index) => ({
      ...question,
      sessionQuestionKey: getSessionQuestionKey(question, index),
    })),
  };
}

/** 将旧版按 questionId 存储的 answers 迁移为按会话题位 key 存储。 */
export function migrateSessionAnswers(session, answers = {}) {
  const migrated = {};
  (session?.questions || []).forEach((question, index) => {
    const key = getSessionQuestionKey(question, index);
    migrated[key] = answers[key] ?? answers[question.questionId] ?? {};
  });
  return migrated;
}

export function isSessionComplete(session, answers = {}) {
  const questions = session?.questions || [];
  return questions.length > 0 && questions.every((question, index) =>
    Boolean(answers[getSessionQuestionKey(question, index)]?.submitted)
  );
}
