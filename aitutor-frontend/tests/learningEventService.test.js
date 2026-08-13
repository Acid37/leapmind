import { describe, expect, test } from '@jest/globals';
import {
  buildLearningEvent,
  buildLectureInteractionEvent,
  createDeterministicEventId,
} from '../src/services/learningEventService.js';

describe('统一画像事件服务', () => {
  test('构建M2请求讲解事件时保留业务幂等键和真实讲解ID', () => {
    const event = buildLearningEvent({
      eventId: 'm2-request:exp-888',
      userId: 23,
      eventType: 'request_explanation',
      sourceModule: 'M2',
      occurredAt: '2026-08-09T10:00:00.000Z',
      kpId: 42,
      data: { explainId: 'exp-888', reasonTag: 'USER_REQUEST' },
    });

    expect(event).toEqual({
      eventId: 'm2-request:exp-888',
      userId: 23,
      eventType: 'request_explanation',
      sourceModule: 'M2',
      occurredAt: '2026-08-09T10:00:00.000Z',
      schemaVersion: '1.0',
      kpId: 42,
      data: { explainId: 'exp-888', reasonTag: 'USER_REQUEST' },
    });
  });

  test('没有真实用户ID时拒绝构建事件，禁止回退到用户1', () => {
    expect(() => buildLearningEvent({
      eventId: 'm4-lecture:1:complete',
      userId: undefined,
      eventType: 'lecture_interact',
      sourceModule: 'M4',
      data: { lectureId: '1', chapterId: 'ch1', action: 'complete' },
    })).toThrow('画像事件缺少有效用户ID');
  });

  test('事件来源与类型不匹配时拒绝发送', () => {
    expect(() => buildLearningEvent({
      eventId: 'bad-event',
      userId: 23,
      eventType: 'lecture_interact',
      sourceModule: 'M2',
      data: { lectureId: '1', chapterId: 'ch1', action: 'complete' },
    })).toThrow('画像事件类型与来源模块不匹配');
  });

  test('长业务标识生成稳定且不超过64字符的事件ID', () => {
    const businessKey = 'exp-' + 'a'.repeat(100);
    const first = createDeterministicEventId('m2-feedback', businessKey);
    const second = createDeterministicEventId('m2-feedback', businessKey);

    expect(first).toBe(second);
    expect(first.length).toBeLessThanOrEqual(64);
    expect(first).toMatch(/^m2-feedback:/);
  });

  test('M4讲课交互使用标准字段且不再发送旧采集格式', () => {
    const event = buildLectureInteractionEvent({
      userId: 23,
      lectureId: 'lecture-7',
      chapterId: 'ch2',
      action: 'replay',
      interactionId: 'session-9:3',
      sessionId: 'session-9',
      kpId: 42,
      occurredAt: '2026-08-09T10:00:00.000Z',
    });

    expect(event.eventType).toBe('lecture_interact');
    expect(event.sourceModule).toBe('M4');
    expect(event.userId).toBe(23);
    expect(event.kpId).toBe(42);
    expect(event.sessionId).toBe('session-9');
    expect(event.data).toEqual({ lectureId: 'lecture-7', chapterId: 'ch2', action: 'replay' });
    expect(event.type).toBeUndefined();
  });
});
