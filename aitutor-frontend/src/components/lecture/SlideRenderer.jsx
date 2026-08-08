import React, { useState, useCallback, useEffect, useRef } from 'react';
import katex from 'katex';
import 'katex/dist/katex.min.css';
import { ChevronLeft, ChevronRight, X, Send } from 'lucide-react';

// ═══════════════════════════════════════════════
// SlideRenderer — M4/M5 共享幻灯片渲染组件
// ═══════════════════════════════════════════════

const TYPE_STYLES = {
  cover: {
    bg: 'linear-gradient(135deg, #7c3aed 0%, #4f46e5 100%)',
    titleColor: '#ffffff',
    bodyColor: '#e0e7ff',
    accentColor: '#fbbf24',
  },
  content: {
    bg: 'linear-gradient(135deg, #f8fafc 0%, #ffffff 100%)',
    titleColor: '#1e293b',
    bodyColor: '#475569',
    accentColor: '#7c3aed',
  },
  interactive: {
    bg: 'linear-gradient(135deg, #fefce8 0%, #fffbeb 100%)',
    titleColor: '#1e293b',
    bodyColor: '#475569',
    accentColor: '#f59e0b',
  },
  summary: {
    bg: 'linear-gradient(135deg, #10b981 0%, #059669 100%)',
    titleColor: '#ffffff',
    bodyColor: '#d1fae5',
    accentColor: '#fbbf24',
  },
  homework: {
    bg: 'linear-gradient(135deg, #eff6ff 0%, #dbeafe 100%)',
    titleColor: '#1e293b',
    bodyColor: '#475569',
    accentColor: '#3b82f6',
  },
};

/**
 * 渲染 KaTeX 公式为 HTML
 */
function renderFormula(formula) {
  if (!formula) return null;
  try {
    return katex.renderToString(formula, {
      throwOnError: false,
      displayMode: true,
      trust: false,
    });
  } catch {
    return `<code>${formula}</code>`;
  }
}

/**
 * 单页幻灯片内容渲染（纯展示，不含导航）
 */
function SlideContent({ slide, styleVars }) {
  const type = slide?.type || 'content';
  const c = slide || {};
  const st = { ...TYPE_STYLES[type], ...styleVars };

  const bullets = Array.isArray(c.bulletPoints) ? c.bulletPoints : [];
  const highlights = Array.isArray(c.highlightPoints) ? c.highlightPoints : [];
  const formulaHtml = renderFormula(c.formula);

  return (
    <div
      className="w-full h-full flex flex-col justify-center overflow-hidden"
      style={{
        background: st.bg,
        padding: '6% 7%',
        boxSizing: 'border-box',
        fontFamily: st.fontFamily || "system-ui, 'PingFang SC', 'Microsoft YaHei', sans-serif",
      }}
    >
      {/* 标题 */}
      <h1
        className="font-bold leading-tight"
        style={{
          fontSize: 'clamp(1.2rem, 3.5vmin, 2.4rem)',
          color: st.titleColor,
          marginBottom: type === 'cover' ? '2vh' : '1.5vh',
        }}
      >
        {c.title || ''}
      </h1>

      {/* 副标题（封面/结尾） */}
      {c.subtitle && (
        <p
          style={{
            fontSize: 'clamp(0.8rem, 1.8vmin, 1.2rem)',
            color: st.bodyColor,
            opacity: 0.85,
            marginBottom: '2vh',
          }}
        >
          {c.subtitle}
        </p>
      )}

      {/* 正文要点 */}
      {bullets.length > 0 && (
        <ul style={{ margin: 0, padding: 0, listStyle: 'none' }}>
          {bullets.map((b, i) => (
            <li
              key={i}
              style={{
                fontSize: 'clamp(0.7rem, 1.6vmin, 1.1rem)',
                lineHeight: 1.7,
                color: st.bodyColor,
                marginBottom: '0.5vh',
                paddingLeft: '1.8vmin',
                position: 'relative',
              }}
            >
              <span
                style={{
                  position: 'absolute',
                  left: 0,
                  top: 0,
                  color: st.accentColor,
                  fontWeight: 700,
                  fontSize: '1.2em',
                }}
              >
                ●
              </span>
              {b}
            </li>
          ))}
        </ul>
      )}

      {/* KaTeX 公式 */}
      {formulaHtml && (
        <div
          className="katex-display-wrapper"
          style={{
            marginTop: '1.5vh',
            padding: '1vh 2vmin',
            background: type === 'cover' || type === 'summary'
              ? 'rgba(255,255,255,0.12)'
              : '#f1f5f9',
            borderRadius: '0.6vmin',
            textAlign: 'center',
            overflow: 'auto',
          }}
          dangerouslySetInnerHTML={{ __html: formulaHtml }}
        />
      )}

      {/* 高亮/重点提示 */}
      {highlights.length > 0 && (
        <div
          style={{
            marginTop: '1.5vh',
            padding: '1vh 1.5vmin',
            background: type === 'cover' || type === 'summary'
              ? 'rgba(251,191,36,0.2)'
              : '#fef3c7',
            borderRadius: '0.5vmin',
            borderLeft: `0.3vmin solid ${st.accentColor}`,
          }}
        >
          <p
            style={{
              margin: '0 0 0.4vh',
              fontSize: 'clamp(0.6rem, 1.2vmin, 0.85rem)',
              color: type === 'cover' || type === 'summary' ? '#fbbf24' : '#92400e',
              fontWeight: 600,
            }}
          >
            💡 重点
          </p>
          {highlights.map((hp, i) => (
            <p
              key={i}
              style={{
                margin: '0.3vh 0',
                fontSize: 'clamp(0.65rem, 1.35vmin, 0.9rem)',
                color: type === 'cover' || type === 'summary' ? '#ffffff' : '#78350f',
              }}
            >
              • {hp}
            </p>
          ))}
        </div>
      )}

      {/* 配图建议（文字提示，等有真实图片后替换为 <img>） */}
      {c.imageSuggestion && (
        <p
          style={{
            marginTop: '1.5vh',
            fontSize: 'clamp(0.55rem, 1vmin, 0.75rem)',
            color: st.bodyColor,
            opacity: 0.5,
            fontStyle: 'italic',
          }}
        >
          📷 {c.imageSuggestion}
        </p>
      )}
    </div>
  );
}

// ═══════════════════════════════════════════════
// 互动题弹窗
// ═══════════════════════════════════════════════

function InteractionModal({ interaction, onSubmit, onClose }) {
  const [value, setValue] = useState('');
  const isChoice = interaction?.type === 'choice_question';

  const handleSubmit = () => {
    if (!value.trim()) return;
    onSubmit(value.trim());
    setValue('');
  };

  return (
    <div className="absolute inset-0 z-30 flex items-center justify-center bg-black/40 backdrop-blur-sm">
      <div className="bg-white rounded-2xl shadow-2xl p-5 sm:p-6 max-w-md w-full mx-4">
        <div className="flex items-start justify-between mb-3">
          <h3 className="text-base sm:text-lg font-bold text-slate-800">
            {isChoice ? '📝 选择题' : '💭 思考题'}
          </h3>
          <button onClick={onClose} className="text-slate-400 hover:text-slate-600">
            <X className="w-5 h-5" />
          </button>
        </div>

        <p className="text-sm sm:text-base text-slate-600 mb-4">{interaction.question}</p>

        {isChoice && interaction.options?.length > 0 && (
          <div className="space-y-2 mb-4">
            {interaction.options.map((opt, i) => (
              <button
                key={i}
                onClick={() => setValue(opt)}
                className={`w-full text-left px-4 py-2.5 rounded-xl border-2 text-sm transition-all ${
                  value === opt
                    ? 'border-purple-500 bg-purple-50 text-purple-700'
                    : 'border-slate-200 hover:border-slate-300 text-slate-600'
                }`}
              >
                {opt}
              </button>
            ))}
          </div>
        )}

        {!isChoice && (
          <textarea
            value={value}
            onChange={(e) => setValue(e.target.value)}
            placeholder="输入你的答案…"
            className="w-full px-4 py-3 border border-slate-200 rounded-xl text-sm resize-none focus:outline-none focus:ring-2 focus:ring-purple-500 mb-4"
            rows={3}
            onKeyDown={(e) => {
              if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault();
                handleSubmit();
              }
            }}
          />
        )}

        <button
          onClick={handleSubmit}
          disabled={!value.trim()}
          className="w-full flex items-center justify-center gap-2 py-2.5 bg-purple-600 text-white rounded-xl font-semibold hover:bg-purple-700 disabled:opacity-40 disabled:cursor-not-allowed transition-colors text-sm"
        >
          <Send className="w-4 h-4" />
          提交
        </button>
      </div>
    </div>
  );
}

// ═══════════════════════════════════════════════
// 缩略图导航条（play 模式底部 / edit 模式左侧）
// ═══════════════════════════════════════════════

function ThumbnailStrip({ slides, current, onSelect, layout = 'horizontal' }) {
  const stripRef = useRef(null);

  useEffect(() => {
    if (stripRef.current && layout === 'horizontal') {
      const active = stripRef.current.querySelector('[data-active="true"]');
      if (active) {
        active.scrollIntoView({ behavior: 'smooth', inline: 'center', block: 'nearest' });
      }
    }
  }, [current, layout]);

  const previewColor = (type) => {
    switch (type) {
      case 'cover': return '#7c3aed';
      case 'summary': return '#10b981';
      case 'interactive': return '#f59e0b';
      case 'homework': return '#3b82f6';
      default: return '#94a3b8';
    }
  };

  const typeLabel = (type) => {
    switch (type) {
      case 'cover': return '封面';
      case 'summary': return '总结';
      case 'interactive': return '互动';
      case 'homework': return '作业';
      default: return '内容';
    }
  };

  return (
    <div
      ref={stripRef}
      className={
        layout === 'horizontal'
          ? 'flex gap-2 overflow-x-auto py-2 px-3'
          : 'flex flex-col gap-2 overflow-y-auto py-2 px-2'
      }
      style={layout === 'horizontal' ? { scrollbarWidth: 'thin' } : {}}
    >
      {slides.map((s, i) => (
        <button
          key={i}
          data-active={i === current}
          onClick={() => onSelect(i)}
          className={`flex-shrink-0 rounded-md overflow-hidden border-2 transition-all ${
            layout === 'horizontal' ? 'w-16 sm:w-20' : 'w-full'
          } ${
            i === current
              ? 'border-purple-500 shadow-md scale-105'
              : 'border-white/20 opacity-60 hover:opacity-100'
          }`}
        >
          <div
            className={`${layout === 'horizontal' ? 'h-10 sm:h-12' : 'h-8'} flex items-center justify-center text-[10px] text-white font-semibold`}
            style={{ background: previewColor(s.type) }}
          >
            {typeLabel(s.type)}
          </div>
          <div className="bg-slate-700 text-white text-[9px] text-center py-0.5">
            {i + 1}/{slides.length}
          </div>
        </button>
      ))}
    </div>
  );
}

// ═══════════════════════════════════════════════
// 主组件
// ═══════════════════════════════════════════════

export default function SlideRenderer({
  slides = [],
  initialPage = 1,
  mode = 'play',
  showNavigator = true,
  showProgress = true,
  transition = 'slide',
  onPageChange,
  onInteractionSubmit,
  styleVars,
  className = '',
}) {
  const [current, setCurrent] = useState(() => Math.max(0, Math.min(initialPage - 1, slides.length - 1)));
  const [animating, setAnimating] = useState(false);
  const [direction, setDirection] = useState('next');
  const [showInteraction, setShowInteraction] = useState(false);
  const touchStartX = useRef(0);
  const touchStartY = useRef(0);

  // Sync with initialPage prop
  useEffect(() => {
    setCurrent(Math.max(0, Math.min(initialPage - 1, slides.length - 1)));
  }, [initialPage, slides.length]);

  const goTo = useCallback((index) => {
    if (animating || index === current || index < 0 || index >= slides.length) return;
    setDirection(index > current ? 'next' : 'prev');
    setAnimating(true);
    setCurrent(index);
    onPageChange?.(index + 1);
    setTimeout(() => setAnimating(false), 300);
  }, [animating, current, slides.length, onPageChange]);

  const goNext = useCallback(() => goTo(current + 1), [goTo, current]);
  const goPrev = useCallback(() => goTo(current - 1), [goTo, current]);

  // Keyboard
  useEffect(() => {
    if (mode !== 'play') return;
    const onKey = (e) => {
      if (e.key === 'ArrowLeft') goPrev();
      if (e.key === 'ArrowRight') goNext();
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [goNext, goPrev, mode]);

  // Touch
  const handleTouchStart = (e) => {
    touchStartX.current = e.touches[0].clientX;
    touchStartY.current = e.touches[0].clientY;
  };
  const handleTouchEnd = (e) => {
    const dx = e.changedTouches[0].clientX - touchStartX.current;
    const dy = e.changedTouches[0].clientY - touchStartY.current;
    if (Math.abs(dx) > Math.abs(dy) && Math.abs(dx) > 50) {
      if (dx < -50) goNext();
      else if (dx > 50) goPrev();
    }
  };

  // Interaction
  const handleInteractionClick = () => setShowInteraction(true);
  const handleInteractionSubmit = (answer) => {
    onInteractionSubmit?.(current + 1, answer);
    setShowInteraction(false);
    // Auto-advance after answering
    setTimeout(() => goNext(), 500);
  };

  const slide = slides[current];
  if (!slide) {
    return <div className="flex items-center justify-center h-full text-slate-400">无幻灯片数据</div>;
  }

  // ─── edit 模式 ───
  if (mode === 'edit') {
    return (
      <div className={`flex h-full ${className}`}>
        {/* 左侧缩略图列表 */}
        {showNavigator && (
          <div className="w-28 sm:w-36 flex-shrink-0 bg-slate-50 border-r border-slate-200 overflow-hidden">
            <ThumbnailStrip
              slides={slides}
              current={current}
              onSelect={goTo}
              layout="vertical"
            />
          </div>
        )}
        {/* 右侧预览 */}
        <div
          className="flex-1 flex items-center justify-center bg-slate-100 p-4"
          onTouchStart={handleTouchStart}
          onTouchEnd={handleTouchEnd}
        >
          <div
            className="w-full bg-white shadow-lg rounded-lg overflow-hidden"
            style={{ aspectRatio: '16/9', maxWidth: '100%', maxHeight: '100%' }}
          >
            <SlideContent slide={slide} styleVars={styleVars} />
          </div>
        </div>
      </div>
    );
  }

  // ─── play 模式 ───
  const animStyle = {};
  if (transition === 'slide' && animating) {
    animStyle.transform = direction === 'next' ? 'translateX(-30px)' : 'translateX(30px)';
    animStyle.opacity = 0;
  } else if (transition === 'fade' && animating) {
    animStyle.opacity = 0;
  }

  return (
    <div className={`flex flex-col h-full min-h-0 ${className}`}>
      {/* 进度条 */}
      {showProgress && slides.length > 1 && (
        <div className="flex-shrink-0 flex items-center justify-center py-1.5 bg-white/5 text-white/60 text-xs">
          {current + 1} / {slides.length}
        </div>
      )}

      {/* 主体幻灯片 */}
      <div
        className="flex-1 min-h-0 flex items-center justify-center p-2 overflow-hidden relative"
        onTouchStart={handleTouchStart}
        onTouchEnd={handleTouchEnd}
      >
        {/* 幻灯片 16:9 */}
        <div
          className="relative bg-white shadow-2xl rounded-lg overflow-hidden"
          style={{
            aspectRatio: '16 / 9',
            width: 'min(100%, calc((100vh - 8rem) * 16 / 9))',
            transition: transition !== 'none' ? 'transform 0.3s ease, opacity 0.3s ease' : 'none',
            ...animStyle,
          }}
        >
          <SlideContent slide={slide} styleVars={styleVars} />

          {/* 互动题按钮（type=interactive 或 interaction 不为空时显示） */}
          {(slide.type === 'interactive' || slide.interaction) && (
            <button
              onClick={handleInteractionClick}
              className="absolute bottom-4 right-4 flex items-center gap-1.5 px-4 py-2 bg-amber-500 text-white rounded-full shadow-lg hover:bg-amber-600 active:scale-95 transition-all text-sm font-medium"
            >
              <span className="text-base">💡</span>
              答题
            </button>
          )}

          {/* 互动题弹窗 */}
          {showInteraction && slide.interaction && (
            <InteractionModal
              interaction={slide.interaction}
              onSubmit={handleInteractionSubmit}
              onClose={() => setShowInteraction(false)}
            />
          )}
        </div>
      </div>

      {/* 底部导航条 */}
      {showNavigator && slides.length > 1 && (
        <div className="flex-shrink-0 bg-white/5 border-t border-white/10">
          {/* 翻页按钮（桌面端） */}
          <div className="hidden sm:flex items-center justify-center gap-3 px-4 py-2">
            <button
              onClick={goPrev}
              disabled={current === 0}
              className="flex items-center gap-1 px-3 py-1 text-white/80 text-sm hover:text-white hover:bg-white/10 rounded-lg transition-colors disabled:opacity-30 disabled:cursor-not-allowed"
            >
              <ChevronLeft className="w-4 h-4" />上一页
            </button>
            <span className="text-white/60 text-xs min-w-[3rem] text-center">
              {current + 1} / {slides.length}
            </span>
            <button
              onClick={goNext}
              disabled={current === slides.length - 1}
              className="flex items-center gap-1 px-3 py-1 text-white/80 text-sm hover:text-white hover:bg-white/10 rounded-lg transition-colors disabled:opacity-30 disabled:cursor-not-allowed"
            >
              下一页<ChevronRight className="w-4 h-4" />
            </button>
          </div>
          {/* 缩略图条 */}
          <ThumbnailStrip
            slides={slides}
            current={current}
            onSelect={goTo}
            layout="horizontal"
          />
        </div>
      )}
    </div>
  );
}
