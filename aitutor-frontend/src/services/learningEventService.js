import { post } from './api';

const EVENT_SOURCES = Object.freeze({
  answer_question: 'M1',
  finish_practice: 'M1',
  wrong_question_changed: 'M1',
  request_explanation: 'M2',
  explanation_feedback: 'M2',
  weak_point_changed: 'M3',
  lecture_interact: 'M4',
  lesson_material_used: 'M5',
  mark_reviewed: 'M6',
  preference_changed: 'M6',
  ask_doubt: 'M7',
});

const EVENT_ID_PATTERN = /^[A-Za-z0-9][A-Za-z0-9._:-]{0,63}$/;

/** 根据业务键生成稳定的短事件 ID，重试时继续使用相同幂等键。 */
export function createDeterministicEventId(prefix, businessKey) {
  if (!/^[A-Za-z0-9][A-Za-z0-9._-]{0,31}$/.test(prefix || '')) {
    throw new Error('画像事件 ID 前缀无效');
  }
  if (businessKey == null || String(businessKey) === '') {
    throw new Error('画像事件缺少业务幂等键');
  }

  let hash = 0xcbf29ce484222325n;
  for (const character of String(businessKey)) {
    hash ^= BigInt(character.codePointAt(0));
    hash = BigInt.asUintN(64, hash * 0x100000001b3n);
  }
  return `${prefix}:${hash.toString(36)}`;
}

/** 构建 M6 v1 标准画像事件。 */
export function buildLearningEvent({
  eventId,
  userId,
  eventType,
  sourceModule,
  occurredAt = new Date().toISOString(),
  sessionId,
  kpId,
  traceId,
  data,
}) {
  if (!Number.isSafeInteger(Number(userId)) || Number(userId) <= 0) {
    throw new Error('画像事件缺少有效用户 ID');
  }
  if (!EVENT_ID_PATTERN.test(eventId || '')) {
    throw new Error('画像事件缺少有效业务事件 ID');
  }
  if (EVENT_SOURCES[eventType] !== sourceModule) {
    throw new Error('画像事件类型与来源模块不匹配');
  }
  if (!data || typeof data !== 'object' || Array.isArray(data)) {
    throw new Error('画像事件数据必须是对象');
  }

  const event = {
    eventId,
    userId: Number(userId),
    eventType,
    sourceModule,
    occurredAt,
    schemaVersion: '1.0',
    data,
  };
  if (sessionId != null && sessionId !== '') event.sessionId = String(sessionId);
  if (Number.isSafeInteger(Number(kpId)) && Number(kpId) > 0) event.kpId = Number(kpId);
  if (traceId != null && traceId !== '') event.traceId = String(traceId);
  return event;
}

/** 构建 M4 讲课交互事件。 */
export function buildLectureInteractionEvent({
  userId,
  lectureId,
  chapterId,
  action,
  interactionId,
  occurredAt,
  sessionId,
  kpId,
  traceId,
}) {
  if (!['pause', 'resume', 'replay', 'ask', 'complete'].includes(action)) {
    throw new Error('M4 讲课交互动作无效');
  }
  return buildLearningEvent({
    eventId: createDeterministicEventId('m4-lecture', interactionId),
    userId,
    eventType: 'lecture_interact',
    sourceModule: 'M4',
    occurredAt,
    sessionId,
    kpId,
    traceId,
    data: {
      lectureId: String(lectureId),
      chapterId: String(chapterId),
      action,
    },
  });
}

/** 将标准事件提交到 M6 唯一公开入口。 */
export async function recordLearningEvent(params) {
  const event = buildLearningEvent(params);
  const response = await post(`/api/user-profile/${event.userId}/record-event`, event);
  return response?.data ?? response;
}

/** 提交 M4 讲课交互事件。 */
export async function recordLectureInteraction(params) {
  const event = buildLectureInteractionEvent(params);
  const response = await post(`/api/user-profile/${event.userId}/record-event`, event);
  return response?.data ?? response;
}

export default {
  buildLearningEvent,
  buildLectureInteractionEvent,
  createDeterministicEventId,
  recordLearningEvent,
  recordLectureInteraction,
};