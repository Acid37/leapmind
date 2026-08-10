import { describe, expect, test } from '@jest/globals';
import { isCompleteLectureSlide, validateLectureSlides } from '../src/utils/lectureSlideValidation.js';

describe('M4 讲课幻灯片完整性校验', () => {
  test('拒绝内容生成中的占位页', () => {
    expect(isCompleteLectureSlide({
      pageNum: 4,
      title: '第4页',
      bulletPoints: ['内容生成中...'],
    })).toBe(false);
  });

  test('拒绝有空洞的八页数组', () => {
    const slides = Array.from({ length: 8 }, (_, index) => ({
      pageNum: index + 1,
      title: `第 ${index + 1} 页主题`,
      bulletPoints: [`第 ${index + 1} 页真实内容`],
    }));
    slides[3] = undefined;

    expect(validateLectureSlides(slides, 8)).toMatchObject({
      valid: false,
      missingPages: [4],
    });
  });

  test('按页码整理完整幻灯片', () => {
    const slides = [2, 1].map((pageNum) => ({
      pageNum,
      title: `第 ${pageNum} 页主题`,
      bulletPoints: [`第 ${pageNum} 页真实内容`],
    }));

    const result = validateLectureSlides(slides, 2);
    expect(result.valid).toBe(true);
    expect(result.slides.map((slide) => slide.pageNum)).toEqual([1, 2]);
  });
});