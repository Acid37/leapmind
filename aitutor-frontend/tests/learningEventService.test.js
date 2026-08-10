import { describe, expect, test } from '@jest/globals';
import {
  buildLearningEvent,
  buildLectureInteractionEvent,
  createDeterministicEventId,
} from '../src/services/learningEventService.js';

describe('统一画像事件服务', () => {
  test('没有真实用户 ID 时拒绝构建事件，禁止回退到用户 1', () => {
    expect(() => buildLearningEvent({
      eventId: 'm4-lecture:complete',
      userId: undefined,
      eventType: 'lecture_interact',
      sourceModule: 'M4',
      data: { lectureId: '1', chapterId: 'ch1', action: 'complete' },
    })).toThrow('画像事件缺少有效用户 ID');
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

  test('长业务标识生成稳定且不超过 64 字符的事件 ID', () => {
    const businessKey = `lecture-${'a'.repeat(100)}`;
    const first = createDeterministicEventId('m4-lecture', businessKey);
    const second = createDeterministicEventId('m4-lecture', businessKey);

    expect(first).toBe(second);
    expect(first.length).toBeLessThanOrEqual(64);
    expect(first).toMatch(/^m4-lecture:/);
  });

  test('M4 讲课交互使用标准字段且不再发送旧采集格式', () => {
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

  test('非数字知识点 ID 不写入标准事件顶层', () => {
    const event = buildLectureInteractionEvent({
      userId: 23,
      lectureId: 'lecture-7',
      chapterId: 'ch1',
      action: 'complete',
      interactionId: 'session-9:4',
      sessionId: 'session-9',
      kpId: 'fallback-pythagorean',
    });

    expect(event.kpId).toBeUndefined();
  });
});