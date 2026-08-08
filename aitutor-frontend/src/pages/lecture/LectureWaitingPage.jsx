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
import { Loader2, CheckCircle2, FileText, Play, ArrowLeft } from 'lucide-react';

// ─── 幻灯片缩略图 ──────────────────────────────────

// 缩略图卡片：根据 slide.type 显示不同色彩预览
const getPreviewStyle = (type) => {
  if (type === 'cover') return { bg: 'linear-gradient(135deg, #7c3aed 0%, #4f46e5 100%)', text: 'text-white' };
  if (type === 'ending') return { bg: 'linear-gradient(135deg, #10b981 0%, #059669 100%)', text: 'text-white' };
  if (type === 'example') return { bg: 'linear-gradient(135deg, #f59e0b 0%, #d97706 100%)', text: 'text-white' };
  return { bg: 'linear-gradient(135deg, #f1f5f9 0%, #e2e8f0 100%)', text: 'text-slate-600' };
};

const SlideThumbnail = ({ slide, index, isNew }) => {
  const style = getPreviewStyle(slide.type);
  return (
    <div
      className={`rounded-xl border-2 transition-all overflow-hidden ${
        isNew
          ? 'border-purple-400 shadow-md animate-pulse'
          : 'border-slate-200 bg-white hover:border-purple-200 hover:shadow-md'
      }`}
    >
      {/* 顶部预览色块（PPT 缩略图占位） */}
      <div
        className="h-14 lg:h-20 flex items-center justify-center relative"
        style={{ background: style.bg }}
      >
        <span className={`text-[10px] lg:text-xs font-semibold ${style.text} opacity-90`}>
          {slide.type === 'cover' ? '封面' :
           slide.type === 'ending' ? '结尾' :
           slide.type === 'example' ? '例题' : '内容'}
        </span>
        <span className={`absolute top-1 right-1.5 text-[9px] font-bold px-1 py-0.5 rounded ${style.text === 'text-white' ? 'bg-white/20 text-white' : 'bg-slate-200 text-slate-500'}`}>
          p.{index + 1}
        </span>
      </div>
      {/* 底部标题区 */}
      <div className="p-2 lg:p-2.5">
        <p className="text-[11px] lg:text-sm font-semibold text-slate-700 line-clamp-1 leading-tight" title={slide.content.title}>
          {slide.content.title}
        </p>
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
  const [error, setError] = useState('');
  const [newSlideIndices, setNewSlideIndices] = useState(new Set());
  const containerRef = useRef(null);

  useEffect(() => {
    if (!params) return;

    let cancelled = false;

    const run = async () => {
      setStatus('generating');
      try {
        await generateLecture(params, (event) => {
          if (cancelled) return;

          switch (event.type) {
            case 'outline':
              setOutline(event.content);
              break;
            case 'slide':
              setSlides(prev => {
                const next = [...prev];
                next[event.pageNum - 1] = event.slide;
                return next;
              });
              setProgress({ current: event.pageNum, total: event.totalPages || 0 });
              // 标记新生成的 slide 用于高亮动画
              setNewSlideIndices(prev => new Set([...prev, event.pageNum - 1]));
              setTimeout(() => {
                setNewSlideIndices(prev => {
                  const next = new Set(prev);
                  next.delete(event.pageNum - 1);
                  return next;
                });
              }, 2000);
              break;
            case 'done':
              setProgress({ current: event.totalPages, total: event.totalPages });
              setStatus('done');
              break;
            default:
              break;
          }

          // 自动滚动到最新缩略图（垂直滚动到可视区）
          if (containerRef.current) {
            const lastBtn = containerRef.current.querySelector('[data-active="true"]');
            if (lastBtn) lastBtn.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
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
      onComplete?.({ lectureId: params?.lectureId, slides, totalPages: slides.length });
    }
  };

  return (
    <div className="w-full min-h-screen bg-gradient-to-br from-purple-50 via-white to-blue-50">
      <div className="w-full max-w-screen-2xl mx-auto px-4 sm:px-6 lg:px-10 py-6 sm:py-10 min-h-screen flex flex-col">
        {/* 顶部：返回按钮居中下方一点的位置（桌面 lg 才显示，移动端隐藏以节省空间） */}
        <div className="flex items-center justify-between mb-2 sm:mb-4">
          <div className="hidden lg:block" />
          <button
            onClick={onBack}
            className="flex items-center gap-1.5 text-xs sm:text-sm text-slate-500 hover:text-slate-700 transition-colors"
          >
            <ArrowLeft className="w-3.5 h-3.5 sm:w-4 sm:h-4" />
            返回
          </button>
          <div className="w-0 lg:w-0" />
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

        {/* 幻灯片缩略图网格：可滚动容器，支持任意页数 */}
        {slides.length > 0 && (
          <div className="mb-3 sm:mb-6 flex-1 min-h-0 flex flex-col">
            <h3 className="text-xs sm:text-sm font-semibold text-slate-600 mb-1.5 sm:mb-2 flex-shrink-0">
              幻灯片预览（{slides.length} 页）
            </h3>
            <div
              ref={containerRef}
              className="grid grid-cols-3 sm:grid-cols-4 md:grid-cols-5 lg:grid-cols-6 gap-2 sm:gap-3 overflow-y-auto flex-1 min-h-0 pr-1"
              style={{ scrollbarWidth: 'thin' }}
            >
              {slides.map((slide, i) => (
                slide && <SlideThumbnail key={i} slide={slide} index={i} isNew={newSlideIndices.has(i)} />
              ))}
            </div>
          </div>
        )}

        {/* 操作按钮：移动端 sticky 固定在底，桌面端普通流式 */}
        <div className="sticky bottom-0 left-0 right-0 -mx-4 sm:-mx-6 lg:mx-0 px-4 sm:px-6 lg:px-0 py-3 sm:py-0 sm:mt-auto sm:pt-6 bg-gradient-to-t from-white via-white/95 to-transparent sm:bg-none sm:backdrop-blur-none">
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
