const PLACEHOLDER_TEXTS = new Set(['内容生成中...', '内容生成中…']);

export const isCompleteLectureSlide = (slide) => {
  if (!slide || typeof slide !== 'object' || slide.isPlaceholder || slide.status === 'failed') return false;
  const pageNum = Number(slide.pageNum ?? slide.page_num);
  const title = String(slide.title ?? slide.content?.title ?? '').trim();
  const bullets = slide.bulletPoints ?? slide.bullet_points ?? slide.content?.body ?? [];
  const meaningfulBullets = (Array.isArray(bullets) ? bullets : [bullets])
    .map((item) => String(item || '').trim())
    .filter((item) => item && !PLACEHOLDER_TEXTS.has(item));
  const hasOtherContent = Boolean(
    String(slide.subtitle ?? slide.content?.subtitle ?? '').trim()
    || String(slide.formula ?? slide.content?.formula ?? '').trim()
    || slide.interaction,
  );
  const genericTitleOnly = /^第\s*\d+\s*页$/.test(title) && meaningfulBullets.length === 0 && !hasOtherContent;
  return Number.isInteger(pageNum) && pageNum > 0 && Boolean(title) && !genericTitleOnly
    && (meaningfulBullets.length > 0 || hasOtherContent);
};

export const validateLectureSlides = (candidateSlides, expectedTotal) => {
  const total = Number(expectedTotal);
  if (!Array.isArray(candidateSlides) || !Number.isInteger(total) || total <= 0) {
    return { valid: false, slides: [], missingPages: [] };
  }
  const ordered = Array.from({ length: total }, (_, index) => candidateSlides.find((slide) =>
    Number(slide?.pageNum ?? slide?.page_num) === index + 1
  ) ?? candidateSlides[index]);
  const missingPages = ordered
    .map((slide, index) => isCompleteLectureSlide(slide) ? null : index + 1)
    .filter(Boolean);
  return { valid: missingPages.length === 0, slides: ordered, missingPages };
};