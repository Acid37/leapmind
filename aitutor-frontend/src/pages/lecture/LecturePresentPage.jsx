/**
 * M4 讲课演示页 (4.2.3) —— 核心页面
 * 
 * 桌面端：三栏布局（幻灯片 60% | 追问面板 20% | 虚拟教师 20%）
 * 移动端：全屏幻灯片 + 底部 Tab 切换（幻灯片 / 追问 / 教师）
 * 
 * 复用现有组件：
 *  - SlideViewer（幻灯片渲染 + 翻页动画 + 音频 + 字幕）
 *  - TeacherPanel（虚拟教师形象 + 对话）
 * 
 * 新增：
 *  - ChatPanelPlaceholder（追问面板占位，联调时替换为 M7 ChatPanel）
 *  - 讲课结束时的总结面板
 *  - "做配套练习"按钮（跳转 M1 做题）
 */

import React, { useState, useCallback } from 'react';
import Header from '../../components/common/Header';
import SlideViewer from '../../components/lecture/SlideViewer';
import TeacherPanel from '../../components/teacher/TeacherPanel';
import { ChatPanel } from '../../components/chat';
import { Flag, BookOpen, MessageCircle, Monitor, User, ChevronLeft, ChevronRight } from 'lucide-react';

const TABS = [
  { key: 'slides', label: '幻灯片', icon: Monitor },
  { key: 'chat', label: '追问', icon: MessageCircle },
  { key: 'teacher', label: '老师', icon: User },
];

// ─── 简化版幻灯片渲染（用于 mock 数据，避免依赖远端 SlideViewer） ───
const renderSlideHTML = (slide) => {
  const c = slide?.content || {};
  const isCover = slide?.type === 'cover';
  const isEnding = slide?.type === 'ending';
  const body = Array.isArray(c.body) ? c.body : (c.body ? [c.body] : []);
  const points = c.highlightPoints || c.points || [];
  const bg = isCover
    ? 'linear-gradient(135deg, #7c3aed 0%, #4f46e5 100%)'
    : isEnding
      ? 'linear-gradient(135deg, #10b981 0%, #059669 100%)'
      : 'linear-gradient(135deg, #f8fafc 0%, #ffffff 100%)';
  const titleColor = (isCover || isEnding) ? '#ffffff' : '#1e293b';
  const bodyColor = (isCover || isEnding) ? '#e0e7ff' : '#475569';
  return `
    <div style="position:absolute;inset:0;background:${bg};padding:5%;box-sizing:border-box;font-family:system-ui,'PingFang SC','Microsoft YaHei',sans-serif;display:flex;flex-direction:column;justify-content:center;overflow:hidden;">
      <h1 style="font-size:3.2vmin;font-weight:700;color:${titleColor};margin:0 0 1.5vh;line-height:1.2;">${c.title || ''}</h1>
      ${c.subtitle ? `<p style="font-size:1.6vmin;color:${bodyColor};margin:0 0 2vh;opacity:0.9;">${c.subtitle}</p>` : ''}
      ${body.length > 0 ? `<ul style="margin:0;padding:0;list-style:none;">${body.map(b => `<li style="font-size:1.5vmin;line-height:1.6;color:${bodyColor};margin:0.6vh 0;padding-left:1.6vmin;position:relative;"><span style="position:absolute;left:0;top:0;color:${isCover || isEnding ? '#fbbf24' : '#7c3aed'};font-weight:700;">●</span>${b}</li>`).join('')}</ul>` : ''}
      ${c.formula ? `<div style="margin-top:2vh;padding:1.2vh 2vmin;background:${(isCover || isEnding) ? 'rgba(255,255,255,0.15)' : '#f1f5f9'};border-radius:1vmin;font-size:1.8vmin;color:${titleColor};font-family:'Courier New',monospace;text-align:center;">${c.formula}</div>` : ''}
      ${points.length > 0 ? `<div style="margin-top:1.6vh;padding:1.2vh 1.5vmin;background:${(isCover || isEnding) ? 'rgba(251,191,36,0.2)' : '#fef3c7'};border-radius:0.6vmin;border-left:0.3vmin solid #f59e0b;"><p style="margin:0 0 0.6vh;font-size:1.2vmin;color:${(isCover || isEnding) ? '#fbbf24' : '#92400e'};font-weight:600;">💡 重点</p>${points.map(p => `<p style="margin:0.3vh 0;font-size:1.3vmin;color:${(isCover || isEnding) ? '#ffffff' : '#78350f'};">• ${p}</p>`).join('')}</div>` : ''}
    </div>
  `;
};

// 简化的 MockSlideViewer：不走远端，只渲染传入的 slides
// 缩略图小卡：用于底部导航条
const getPreviewColor = (type) => {
  if (type === 'cover') return '#7c3aed';
  if (type === 'ending') return '#10b981';
  if (type === 'example') return '#f59e0b';
  return '#94a3b8';
};

const MockSlideViewer = ({ slides, currentIndex, onPrev, onNext, onSlideChange }) => {
  const slide = slides[currentIndex];
  const stripRef = React.useRef(null);
  if (!slide) return null;

  // 缩略图条自动滚动到当前
  React.useEffect(() => {
    if (stripRef.current) {
      const active = stripRef.current.querySelector('[data-active="true"]');
      if (active) {
        active.scrollIntoView({ behavior: 'smooth', inline: 'center', block: 'nearest' });
      }
    }
  }, [currentIndex]);

  // 键盘左右键切换
  React.useEffect(() => {
    const onKey = (e) => {
      if (e.key === 'ArrowLeft') onPrev();
      if (e.key === 'ArrowRight') onNext();
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onPrev, onNext]);

  // 滑动手势支持
  const touchStartX = React.useRef(0);
  const onTouchStart = (e) => { touchStartX.current = e.touches[0].clientX; };
  const onTouchEnd = (e) => {
    const dx = e.changedTouches[0].clientX - touchStartX.current;
    if (dx > 50) onPrev();
    else if (dx < -50) onNext();
  };

  return (
    <div className="flex-1 flex flex-col min-h-0" onTouchStart={onTouchStart} onTouchEnd={onTouchEnd}>
      <div className="flex-1 min-h-0 flex items-center justify-center p-1 sm:p-2 overflow-hidden">
        {/* PPT 16:9 容器：取容器宽和容器高×16/9 中较小的，确保不超出 */}
        <div
          className="relative bg-white shadow-2xl rounded-lg overflow-hidden"
          style={{
            aspectRatio: '16 / 9',
            width: 'min(100%, calc(100% * 16 / 9))',
            // 关键：让浏览器按父级 16:9 算出最合适的尺寸
            // 当父宽 100% = 高×16/9 时 100% 取父宽
            // 当父宽 < 高×16/9 时取父宽，aspect-ratio 决定高
            // 当父宽 > 高×16/9 时取高×16/9（=父宽），不溢出
          }}
        >
          <iframe
            title={`Slide ${currentIndex + 1}`}
            className="absolute inset-0 w-full h-full"
            style={{ border: 'none', display: 'block' }}
            srcDoc={renderSlideHTML(slide)}
          />
        </div>
      </div>
      {/* 缩略图条 + 翻页按钮：双行布局 */}
      <div className="flex-shrink-0 bg-white/5 border-t border-white/10">
        {/* 上下页按钮（桌面端） */}
        <div className="hidden sm:flex items-center justify-center gap-3 px-4 py-2">
          <button
            onClick={onPrev}
            className="flex items-center gap-1 px-3 py-1 text-white/80 text-sm hover:text-white hover:bg-white/10 rounded-lg transition-colors"
          >
            <ChevronLeft className="w-4 h-4" />上一页
          </button>
          <span className="text-white/60 text-xs">
            {currentIndex + 1} / {slides.length}
          </span>
          <button
            onClick={onNext}
            className="flex items-center gap-1 px-3 py-1 text-white/80 text-sm hover:text-white hover:bg-white/10 rounded-lg transition-colors"
          >
            下一页<ChevronRight className="w-4 h-4" />
          </button>
        </div>
        {/* 横向缩略图条 */}
        <div
          ref={stripRef}
          className="flex gap-2 overflow-x-auto py-2 px-3 sm:px-4"
          style={{ scrollbarWidth: 'thin' }}
        >
          {slides.map((s, i) => (
            <button
              key={i}
              data-active={i === currentIndex}
              onClick={() => onSlideChange?.(i + 1)}
              className={`flex-shrink-0 w-16 sm:w-20 rounded-md overflow-hidden border-2 transition-all ${
                i === currentIndex
                  ? 'border-white shadow-md scale-105'
                  : 'border-white/20 opacity-60 hover:opacity-100'
              }`}
            >
              <div
                className="h-10 sm:h-12 flex items-center justify-center text-[10px] text-white font-semibold"
                style={{ background: getPreviewColor(s?.type) }}
              >
                {s?.type === 'cover' ? '封面' : s?.type === 'ending' ? '结尾' : s?.type === 'example' ? '例题' : '内容'}
              </div>
              <div className="bg-slate-800 text-white text-[9px] text-center py-0.5">
                p.{i + 1}
              </div>
            </button>
          ))}
        </div>
      </div>
    </div>
  );
};

const LecturePresentPage = ({ lectureData, userId = 1, onBack, onFinish }) => {
  const { lectureId, title = '在线课堂', courseId, slides: mockSlides } = lectureData || {};
  const hasMockSlides = Array.isArray(mockSlides) && mockSlides.length > 0;
  const [currentSlide, setCurrentSlide] = useState(1);
  const [showEndPanel, setShowEndPanel] = useState(false);
  const [mobileTab, setMobileTab] = useState('slides');

  // 幻灯片切换回调（由 SlideViewer 内部翻页时触发）
  const handleSlideChange = useCallback((pageNum) => {
    setCurrentSlide(pageNum);
  }, []);

  // mock 模式下的翻页 handler
  const handleMockPrev = useCallback(() => {
    setCurrentSlide(prev => Math.max(1, prev - 1));
  }, []);
  const handleMockNext = useCallback(() => {
    setCurrentSlide(prev => Math.min((mockSlides?.length || 1), prev + 1));
  }, [mockSlides]);

  const handleEndLecture = () => setShowEndPanel(true);

  const handleGoPractice = () => {
    onFinish?.({ lectureId, knowledgePoints: lectureData?.knowledgePoints });
  };

  const bgGradient = {
    backgroundImage: "linear-gradient(135deg, #861FCE 0%, #861FCE 16%, #731CCD 16%, #731CCD 32%, #6B1CCF 32%, #6B1CCF 48%, #631DCE 48%, #631DCE 64%, #5A1BCE 64%, #5A1BCE 80%, rgb(86,43,205) 80%, rgb(47,8,154) 100%)",
  };

  // ─── 结束面板（共用） ──────────────────────────
  const EndPanel = () => (
    <div className="absolute inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm px-4">
      <div className="bg-white rounded-2xl shadow-2xl p-6 sm:p-8 max-w-md w-full text-center">
        <div className="w-14 h-14 sm:w-16 sm:h-16 bg-green-100 rounded-full flex items-center justify-center mx-auto mb-3 sm:mb-4">
          <span className="text-2xl sm:text-3xl">🎉</span>
        </div>
        <h2 className="text-xl sm:text-2xl font-bold text-slate-800 mb-2">讲课结束！</h2>
        <p className="text-sm sm:text-base text-slate-500 mb-5 sm:mb-6">
          你已完成「{title}」的学习，来检验一下掌握情况吧。
        </p>
        <div className="space-y-2.5 sm:space-y-3">
          <button
            onClick={handleGoPractice}
            className="w-full flex items-center justify-center gap-2 py-2.5 sm:py-3 bg-purple-600 text-white rounded-xl font-semibold hover:bg-purple-700 transition-colors text-sm sm:text-base"
          >
            <BookOpen className="w-4 h-4 sm:w-5 sm:h-5" />
            做配套练习
          </button>
          <button
            onClick={() => setShowEndPanel(false)}
            className="w-full py-2 sm:py-2.5 text-slate-500 text-sm hover:text-slate-700 transition-colors"
          >
            继续讲课
          </button>
        </div>
      </div>
    </div>
  );

  return (
    <div className="w-full h-screen flex flex-col lg:flex-row bg-gradient-to-br from-purple-700 via-purple-600 via-blue-600 to-blue-700" style={bgGradient}>
      {/* ═══════════════ 桌面端：左右两栏布局 ═══════════════ */}
      {/* 左侧：幻灯片区 (75%) - PPT 全图 + 缩略图条 */}
      <div className="hidden lg:flex lg:w-[75%] flex-col overflow-hidden">
        <div className="bg-white/10 backdrop-blur-md border-b border-white/20">
          <Header lessonSubtitle={title} dark={true} onBack={onBack} />
        </div>
        {hasMockSlides ? (
          <MockSlideViewer slides={mockSlides} currentIndex={currentSlide - 1} onPrev={handleMockPrev} onNext={handleMockNext} onSlideChange={handleSlideChange} />
        ) : (
          <div className="flex-1 overflow-hidden">
            <SlideViewer courseId={courseId || lectureId} projectId={lectureId} onSlideChange={handleSlideChange} />
          </div>
        )}
      </div>

      {/* 右侧：追问对话面板 (25%) - 含输入框 */}
      <div className="hidden lg:flex lg:w-[25%] flex-col p-3 gap-3">
        <div className="flex-1 min-h-0">
          <ChatPanel sceneType="teaching" context={{ lectureId, slide: currentSlide }} userId={userId} visible={true} />
        </div>
        <button
          onClick={handleEndLecture}
          className="flex-shrink-0 w-full flex items-center justify-center gap-2 py-2.5 rounded-lg bg-red-500/90 hover:bg-red-500 text-white text-sm font-medium shadow-lg transition-colors"
        >
          <Flag className="w-4 h-4" />结束讲课
        </button>
      </div>

      {/* ═══════════════ 移动端：全屏 + 底部 Tab ═══════════════ */}
      {/* 幻灯片视图 */}
      <div className={`lg:hidden flex-1 flex flex-col overflow-hidden ${mobileTab !== 'slides' ? 'hidden' : ''}`}>
        <div className="bg-white/10 backdrop-blur-md border-b border-white/20 flex-shrink-0">
          <Header lessonSubtitle={title} dark={true} onBack={onBack} />
        </div>
        {hasMockSlides ? (
          <MockSlideViewer slides={mockSlides} currentIndex={currentSlide - 1} onPrev={handleMockPrev} onNext={handleMockNext} onSlideChange={handleSlideChange} />
        ) : (
          <div className="flex-1 overflow-hidden">
            <SlideViewer courseId={courseId || lectureId} projectId={lectureId} onSlideChange={handleSlideChange} />
          </div>
        )}
        {/* 移动端结束按钮（幻灯片页底部） */}
        <div className="flex-shrink-0 px-4 py-2 bg-white/5 backdrop-blur-sm border-t border-white/10">
          <button
            onClick={handleEndLecture}
            className="w-full flex items-center justify-center gap-2 py-2 rounded-lg bg-white/10 text-white/70 text-sm active:bg-red-500/30 active:text-red-200 transition-all"
          >
            <Flag className="w-4 h-4" />结束讲课
          </button>
        </div>
      </div>

      {/* 追问面板视图 */}
      <div className={`lg:hidden flex-1 flex flex-col ${mobileTab !== 'chat' ? 'hidden' : ''}`}>
        <div className="bg-white/10 backdrop-blur-md border-b border-white/20 flex-shrink-0">
          <Header lessonSubtitle={title} dark={true} onBack={onBack} />
        </div>
        <div className="bg-white/5 backdrop-blur-sm border-b border-white/10 px-4 py-3 flex items-center justify-between flex-shrink-0">
          <div>
            <p className="text-white/80 text-sm font-medium">💬 课堂追问</p>
            <p className="text-white/40 text-xs">当前第 {currentSlide} 页</p>
          </div>
        </div>
        <div className="flex-1 p-3 overflow-hidden">
          <ChatPanel sceneType="teaching" context={{ lectureId, slide: currentSlide }} userId={userId} visible={true} />
        </div>
      </div>

      {/* 教师视图 */}
      <div className={`lg:hidden flex-1 flex flex-col ${mobileTab !== 'teacher' ? 'hidden' : ''}`}>
        <div className="bg-white/10 backdrop-blur-md border-b border-white/20 flex-shrink-0">
          <Header lessonSubtitle={title} dark={true} onBack={onBack} />
        </div>
        <div className="flex-1 overflow-hidden [&>aside]:w-full [&>aside]:h-full">
          <TeacherPanel dark={true} />
        </div>
      </div>

      {/* 移动端底部 Tab 栏 */}
      <div className="lg:hidden flex-shrink-0 flex bg-black/30 backdrop-blur-md border-t border-white/10">
        {TABS.map((tab) => (
          <button
            key={tab.key}
            onClick={() => setMobileTab(tab.key)}
            className={`flex-1 flex flex-col items-center justify-center gap-0.5 py-2 transition-colors ${
              mobileTab === tab.key
                ? 'text-white'
                : 'text-white/50 hover:text-white/70'
            }`}
          >
            <tab.icon className="w-5 h-5" />
            <span className="text-[10px] font-medium">{tab.label}</span>
          </button>
        ))}
      </div>

      {/* 讲课结束面板 */}
      {showEndPanel && <EndPanel />}
    </div>
  );
};

export default LecturePresentPage;
