/**
 * M4 生成等待页 (4.2.2)
 * 
 * 功能：
 *  - SSE 流式接收讲课生成进度
 *  - 进度指示（大纲 → 逐页生成）
 *  - 实时展示已生成的幻灯片缩略图
 *  - 全部生成完成后 → "开始讲课"按钮
 */

import React, { useEffect, useState, useRef } from 'react';
import { generateLecture } from '../../services/lectureService';
import SlidePreview from '../../components/shared/SlideRenderer';
import { validateLectureSlides } from '../../utils/lectureSlideValidation';
import { Loader2, CheckCircle2, FileText, Play, ArrowLeft, Sparkles, Layers3, WandSparkles, Clock3 } from 'lucide-react';

const GENERATION_STEPS = [
  { key: 'source', label: '分析素材', icon: FileText },
  { key: 'outline', label: '生成大纲', icon: Sparkles },
  { key: 'slides', label: '逐页制作', icon: Layers3 },
  { key: 'finish', label: '整理完成', icon: WandSparkles },
];

const formatOutline = (content, fallbackTitle) => {
  if (typeof content === 'string') return content;
  if (Array.isArray(content)) {
    const titles = content
      .map((item) => typeof item === 'string' ? item : item?.title)
      .filter(Boolean);
    if (titles.length > 0) return titles.join(' · ');
  }
  if (content && typeof content === 'object') {
    return content.title || content.summary || fallbackTitle || '讲课大纲已生成';
  }
  return fallbackTitle || '正在生成讲课大纲…';
};

// ─── 幻灯片缩略图 ──────────────────────────────────

const SlideThumbnail = ({ slide, index, isNew }) => {
  // 真实 M5 SSE 为扁平结构（title 在顶层），旧 Mock 使用 content.title。
  const title = slide.title || slide.content?.title || '未命名幻灯片';
  const previewSlide = {
    ...slide,
    pageNum: slide.pageNum ?? slide.page_num ?? index + 1,
    type: slide.type === 'example'
      ? 'interactive'
      : slide.type === 'ending'
        ? 'summary'
        : slide.type || 'content',
    title,
    subtitle: slide.subtitle || slide.content?.subtitle || '',
    bulletPoints: slide.bulletPoints ?? slide.bullet_points ?? slide.content?.body ?? [],
    formula: slide.formula || slide.content?.formula,
    highlightPoints: slide.highlightPoints ?? slide.highlight_points ?? slide.content?.highlightPoints ?? [],
    interaction: slide.interaction || null,
  };
  return (
    <div
      className={`rounded-xl border-2 transition-all overflow-hidden ${
        isNew
          ? 'border-purple-400 shadow-md animate-pulse'
          : 'border-slate-200 bg-white hover:border-purple-200 hover:shadow-md'
      }`}
    >
      {/* 复用 M4/M5 的共享 SlideData 渲染规则，展示真实标题、要点与公式。 */}
      <div className="relative bg-slate-900">
        <SlidePreview slide={previewSlide} compact />
        <span className="absolute bottom-1 right-1.5 rounded bg-slate-950/55 px-1 py-0.5 text-[10px] font-bold text-white backdrop-blur-sm">
          p.{index + 1}
        </span>
      </div>
    </div>
  );
};

// ─── 主页面 ─────────────────────────────────────────

const LectureWaitingPage = ({ params, onComplete, onBack }) => {
  const [status, setStatus] = useState('connecting'); // connecting | generating | done | error
  const [outline, setOutline] = useState('');
  const [slides, setSlides] = useState([]);
  const [progress, setProgress] = useState({ current: 0, total: 0 });
  const [lectureId, setLectureId] = useState(null);
  const [error, setError] = useState('');
  const [newSlideIndices, setNewSlideIndices] = useState(new Set());
  const [elapsedSeconds, setElapsedSeconds] = useState(0);
  const containerRef = useRef(null);
  const slidesRef = useRef([]);

  useEffect(() => {
    slidesRef.current = slides;
  }, [slides]);

  useEffect(() => {
    if (status !== 'generating') return undefined;
    const startedAt = Date.now();
    setElapsedSeconds(0);
    const timer = window.setInterval(() => {
      setElapsedSeconds(Math.floor((Date.now() - startedAt) / 1000));
    }, 1000);
    return () => window.clearInterval(timer);
  }, [status]);

  const activeStep = status === 'done'
    ? 3
    : progress.current > 0
      ? 2
      : outline
        ? 1
        : 0;

  const currentTask = progress.current > 0
    ? `正在生成第 ${Math.min(progress.current + 1, progress.total || progress.current + 1)} 页内容`
    : outline
      ? '大纲已就绪，正在准备第一页'
      : '正在理解主题、年级与薄弱知识点';

  useEffect(() => {
    if (!params) return;

    let cancelled = false;

    const run = async () => {
      setStatus('generating');
      try {
        await generateLecture({
          userId: params?.userId,
          courseId: params?.courseId,
          sourceText: params?.textContent || params?.sourceText || '',
          sourceType: params?.sourceType,
          userProfile: params?.userProfile,
          userProfileSummary: params?.userProfileSummary,
          weakPointIds: params?.weakPointIds,
          selectedWeakPoints: params?.selectedWeakPoints,
        }, (event) => {
          if (cancelled) return;

          switch (event.type) {
            case 'outline':
              setOutline(formatOutline(event.content ?? event.outline, event.title));
              break;
            case 'slide':
              setSlides(prev => {
                const next = [...prev];
                const slideIndex = (event.pageNum ?? event.page_num ?? event.slide?.pageNum ?? event.slide?.page_num ?? 1) - 1;
                next[slideIndex] = { ...next[slideIndex], ...event.slide };
                slidesRef.current = next;
                return next;
              });
              setProgress({ current: event.pageNum ?? event.page_num ?? 0, total: event.totalPages ?? event.total_pages ?? 0 });
              // 标记新生成的 slide 用于高亮动画
              setNewSlideIndices(prev => new Set([...prev, (event.pageNum ?? event.page_num ?? 1) - 1]));
              setTimeout(() => {
                setNewSlideIndices(prev => {
                  const next = new Set(prev);
                  next.delete((event.pageNum ?? event.page_num ?? 1) - 1);
                  return next;
                });
              }, 2000);
              break;
            case 'narration':
              setSlides(prev => {
                const slideIndex = (event.pageNum ?? event.page_num ?? 1) - 1;
                const next = [...prev];
                if (slideIndex < 0 || !next[slideIndex]) return prev;
                next[slideIndex] = {
                  ...next[slideIndex],
                  narrationText: event.narrationText ?? event.narration_text ?? '',
                  estimatedDurationSec: event.estimatedDurationSec ?? event.estimated_duration_seconds,
                };
                return next;
              });
              break;
            case 'done':
              {
                const totalPages = Number(event.totalPages ?? event.total_pages ?? progress.total);
                const finalSlides = Array.isArray(event.slides) ? event.slides : slidesRef.current;
                const validation = validateLectureSlides(finalSlides, totalPages);
                if (!validation.valid) {
                  setError(`第 ${validation.missingPages.join('、')} 页生成不完整，请返回后重新生成。`);
                  setStatus('error');
                  break;
                }
                if (event.prepId != null) setLectureId(event.prepId);
                slidesRef.current = validation.slides;
                setSlides(validation.slides);
                setProgress({ current: totalPages, total: totalPages });
                setStatus('done');
              }
              break;
            case 'saved':
              if (event.prepId != null) setLectureId(event.prepId);
              break;
            default:
              break;
          }

          // 自动滚动到最新缩略图（横向滚动到最右侧，展示最新生成的卡片）
          if (containerRef.current) {
            containerRef.current.scrollTo({ left: containerRef.current.scrollWidth, behavior: 'smooth' });
          }
        });
      } catch (err) {
        if (!cancelled) {
          setError(err?.message || '生成失败，请重试');
          setStatus('error');
        }
      }
    };

    run();
    return () => { cancelled = true; };
  }, [params]);

  const handleStartLecture = () => {
    if (status === 'done') {
      const validation = validateLectureSlides(slides, progress.total || slides.length);
      if (!validation.valid) {
        setError(`第 ${validation.missingPages.join('、')} 页生成不完整，请返回后重新生成。`);
        setStatus('error');
        return;
      }
      onComplete?.({
        lectureId: lectureId ?? params?.lectureId,
        title: params?.textContent || '在线课堂',
        slides: validation.slides,
        totalPages: validation.slides.length,
        knowledgePoints: params?.selectedWeakPoints || [],
      });
    }
  };

  return (
    <div className="w-full min-h-screen bg-gradient-to-br from-purple-50 via-white to-blue-50">
      <div className="w-full max-w-screen-2xl mx-auto px-4 sm:px-6 lg:px-10 py-6 sm:py-10 min-h-screen flex flex-col">
        {/* 顶部：返回按钮位于左上角，尺寸适中便于点击 */}
        <div className="mb-2 sm:mb-4">
          <button
            onClick={onBack}
            className="inline-flex items-center gap-1.5 px-3 sm:px-4 py-1.5 sm:py-2 text-sm sm:text-base text-slate-600 bg-white border border-slate-200 rounded-lg hover:bg-slate-50 hover:border-slate-300 transition-colors shadow-sm"
          >
            <ArrowLeft className="w-4 h-4 sm:w-5 sm:h-5" />
            返回
          </button>
        </div>

        {/* 状态指示 */}
        <div className="text-center mb-3 sm:mb-6">
          {status === 'connecting' && (
            <>
              <Loader2 className="w-10 h-10 sm:w-12 sm:h-12 text-purple-400 animate-spin mx-auto mb-3 sm:mb-4" />
              <h2 className="text-lg sm:text-xl font-bold text-slate-700">正在连接…</h2>
            </>
          )}
          {status === 'generating' && (
            <>
              <div className="relative w-16 h-16 sm:w-20 sm:h-20 mx-auto mb-3 sm:mb-4">
                <svg className="w-16 h-16 sm:w-20 sm:h-20 -rotate-90" viewBox="0 0 80 80">
                  <circle cx="40" cy="40" r="34" fill="none" stroke="#e2e8f0" strokeWidth="6" />
                  <circle
                    cx="40" cy="40" r="34" fill="none" stroke="#8b5cf6" strokeWidth="6"
                    strokeLinecap="round"
                    strokeDasharray={`${(progress.current / Math.max(progress.total, 1)) * 213.6} 213.6`}
                    className="transition-all duration-700"
                  />
                </svg>
                <span className="absolute inset-0 flex items-center justify-center text-xs sm:text-sm font-bold text-purple-600">
                  {progress.total > 0 ? Math.round((progress.current / progress.total) * 100) : 0}%
                </span>
              </div>
              <h2 className="text-lg sm:text-xl font-bold text-slate-700">AI 正在生成讲课内容</h2>
              <p className="text-xs sm:text-sm text-slate-500 mt-1 sm:mt-2">
                已生成 {progress.current}/{progress.total || '?'} 页
              </p>
              <div className="mt-3 flex items-center justify-center gap-1.5 text-xs text-slate-400">
                <Clock3 className="h-3.5 w-3.5" />
                已用时 {elapsedSeconds} 秒 · AI 生成通常需要 1–2 分钟
              </div>
            </>
          )}
          {status === 'done' && (
            <>
              <CheckCircle2 className="w-10 h-10 sm:w-12 sm:h-12 text-green-500 mx-auto mb-3 sm:mb-4" />
              <h2 className="text-lg sm:text-xl font-bold text-slate-700">生成完成！</h2>
              <p className="text-xs sm:text-sm text-slate-500 mt-1 sm:mt-2">共 {slides.length} 页，准备开始讲课</p>
            </>
          )}
          {status === 'error' && (
            <>
              <div className="w-10 h-10 sm:w-12 sm:h-12 bg-red-100 rounded-full flex items-center justify-center mx-auto mb-3 sm:mb-4">
                <span className="text-xl sm:text-2xl">😞</span>
              </div>
              <h2 className="text-lg sm:text-xl font-bold text-slate-700">生成失败</h2>
              <p className="text-xs sm:text-sm text-red-500 mt-1 sm:mt-2">{error}</p>
            </>
          )}
        </div>

        {(status === 'generating' || status === 'done') && (
          <div className="mb-4 sm:mb-6 rounded-2xl border border-white/80 bg-white/75 p-4 shadow-sm backdrop-blur-sm">
            <div className="grid grid-cols-4 gap-2">
              {GENERATION_STEPS.map((step, index) => {
                const StepIcon = step.icon;
                const completed = status === 'done' || index < activeStep;
                const active = status !== 'done' && index === activeStep;
                return (
                  <div key={step.key} className="relative flex flex-col items-center text-center">
                    {index < GENERATION_STEPS.length - 1 && (
                      <div className={`absolute left-[58%] top-4 h-0.5 w-[84%] ${index < activeStep ? 'bg-purple-400' : 'bg-slate-200'}`} />
                    )}
                    <div className={`relative z-10 flex h-8 w-8 items-center justify-center rounded-full transition-colors ${
                      completed
                        ? 'bg-emerald-500 text-white'
                        : active
                          ? 'bg-purple-600 text-white shadow-md shadow-purple-200'
                          : 'bg-slate-100 text-slate-400'
                    }`}>
                      {completed ? <CheckCircle2 className="h-4 w-4" /> : <StepIcon className={`h-4 w-4 ${active ? 'animate-pulse' : ''}`} />}
                    </div>
                    <span className={`mt-2 text-[11px] sm:text-xs font-medium ${active ? 'text-purple-700' : completed ? 'text-emerald-700' : 'text-slate-400'}`}>
                      {step.label}
                    </span>
                  </div>
                );
              })}
            </div>
            {status === 'generating' && (
              <div className="mt-4 flex items-center justify-between gap-3 rounded-xl bg-purple-50 px-3 py-2.5 text-xs sm:text-sm">
                <span className="flex items-center gap-2 font-medium text-purple-700">
                  <Loader2 className="h-4 w-4 animate-spin" />
                  {currentTask}
                </span>
                <span className="shrink-0 text-slate-400">请保持页面开启</span>
              </div>
            )}
          </div>
        )}

        {/* 大纲预览（生成中） */}
        {outline && status === 'generating' && (
          <div className="mb-4 sm:mb-6 p-3 sm:p-4 bg-white border border-purple-200 rounded-xl shadow-sm">
            <div className="flex items-center gap-1.5 sm:gap-2 mb-1.5 sm:mb-2">
              <FileText className="w-3.5 h-3.5 sm:w-4 sm:h-4 text-purple-500" />
              <span className="text-xs sm:text-sm font-semibold text-purple-700">大纲</span>
            </div>
            <p className="text-xs sm:text-sm text-slate-600">{outline}</p>
          </div>
        )}

        {/* 幻灯片缩略图网格：固定约 2.5 行高度，超过后竖滚动条出现（禁止横滚动条） */}
        {slides.length > 0 && (
          <div className="mb-3 sm:mb-6 flex flex-col">
            <h3 className="text-xs sm:text-sm font-semibold text-slate-600 mb-1.5 sm:mb-2 flex-shrink-0">
              幻灯片预览（{slides.length} 页）
            </h3>
            <div
              ref={containerRef}
              className="grid grid-cols-5 gap-3 sm:gap-4 overflow-y-auto overflow-x-hidden max-h-[31rem] pr-1"
              style={{ scrollbarWidth: 'thin' }}
            >
              {slides.map((slide, i) => (
                slide && <SlideThumbnail key={i} slide={slide} index={i} isNew={newSlideIndices.has(i)} />
              ))}
            </div>
          </div>
        )}

        {/* 操作按钮：紧跟内容（不贴视口底部），移动端 sticky 固定在底 */}
        <div className="sticky bottom-0 left-0 right-0 -mx-4 sm:-mx-6 lg:mx-0 px-4 sm:px-6 lg:px-0 py-3 sm:py-0 sm:pt-6 bg-gradient-to-t from-white via-white/95 to-transparent sm:bg-none sm:backdrop-blur-none">
          <div className="flex justify-center gap-3 sm:gap-4">
            {status === 'error' && (
              <button
                onClick={onBack}
                className="px-5 sm:px-6 py-2.5 sm:py-3 bg-slate-100 text-slate-600 rounded-xl font-medium hover:bg-slate-200 transition-colors text-sm sm:text-base"
              >
                返回重试
              </button>
            )}
            {status === 'done' && (
              <button
                onClick={handleStartLecture}
                className="group flex items-center gap-3 px-8 sm:px-12 py-4 sm:py-5 bg-gradient-to-r from-purple-600 to-indigo-600 text-white rounded-2xl font-bold hover:from-purple-700 hover:to-indigo-700 shadow-xl shadow-purple-300/50 active:scale-[0.98] transition-all text-base sm:text-lg tracking-wide"
              >
                <Play className="w-5 h-5 sm:w-6 sm:h-6 fill-current" />
                开始讲课
              </button>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};

export default LectureWaitingPage;
