import { describe, expect, test } from '@jest/globals';
import {
  SUBJECT_REVERSE,
  transformKnowledgePointOptions,
  transformKnowledgePointsBySubject,
  transformSubjectOptions,
} from '../src/utils/practiceSubjects.js';

describe('M1 动态科目选项', () => {
  test('预置科目保持兼容，并支持化学', () => {
    expect(transformSubjectOptions(['数学', '化学'])).toEqual([
      { value: 'math', label: '数学' },
      { value: 'chemistry', label: '化学' },
    ]);
    expect(SUBJECT_REVERSE.chemistry).toBe('化学');
  });

  test('自定义科目不再被合并成通用科目', () => {
    expect(transformSubjectOptions(['历史', '  地理  ', '历史', '通用', 'general', '', null])).toEqual([
      { value: '历史', label: '历史' },
      { value: '地理', label: '地理' },
    ]);
  });

  test('知识点选项去重并保留自定义内容', () => {
    expect(transformKnowledgePointOptions(['函数', ' 函数 ', '化学键', '', null])).toEqual([
      { value: '函数', label: '函数' },
      { value: '化学键', label: '化学键' },
    ]);
  });

  test('按科目同步知识点时转换预置科目并保留自定义科目', () => {
    expect(transformKnowledgePointsBySubject({
      数学: ['函数'],
      化学: ['化学键'],
      历史: ['古代史'],
    })).toEqual({
      math: [{ value: '函数', label: '函数' }],
      chemistry: [{ value: '化学键', label: '化学键' }],
      历史: [{ value: '古代史', label: '古代史' }],
    });
  });
});
