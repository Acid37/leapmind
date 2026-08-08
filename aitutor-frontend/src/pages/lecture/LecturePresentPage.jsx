/**
 * M4 讲课演示页 (4.2.3) —— 核心页面
 * 
 * 桌面端：左右两栏（幻灯片 75% | 追问 25%）
 * 移动端：全屏幻灯片 + 底部 Tab 切换（幻灯片 / 追问 / 教师）
 * 
 * 复用组件：
 *  - SlideRenderer（共享幻灯片渲染，M4/M5 共用）
 *  - TeacherPanel（虚拟教师形象）
 *  - ChatPanel（追问对话，M7 共享组件）
 * 
 * 讲课结束时可跳转 M1 做配套练习。
 */

import React, { useState, useCallback, useMemo, useRef } from 'react';
import Header from '../../components/common/Header';
import SlideRenderer from '../../components/lecture/SlideRenderer';
import SlideViewer from '../../components/lecture/SlideViewer';
import TeacherPanel from '../../components/teacher/TeacherPanel';
import { ChatPanel } from '../../components/chat';
import { submitLectureEvent } from '../../services/lectureService';
import { Flag, BookOpen, MessageCircle, Monitor, User, Pause, Play } from 'lucide-react';

// ─── 数据类型转换：旧 mock 嵌套格式 → 统一 SlideData ───

/** 旧 type → 新 type 映射 */
const TYPE_MAP = { cover: 'cover', content: 'content', example: 'interactive', ending: 'summary' };

/**
 * 将 mockLecture 的嵌套格式 slides 转为统一 SlideData
 * 旧: { pageNum, type, content: { title, body, ... }, interaction }
 * 新: { pageNum, type, title, bulletPoints, imageSuggestion, formula, highlightPoints, interaction }
 */
function normalizeSlides(slides) {
  if (!Array.isArray(slides)) return [];
  return slides.map((s, i) => {
    const c = s?.content || {};
    return {
      pageNum: s?.pageNum ?? i + 1,
      type: TYPE_MAP[s?.type] || 'content',
      title: c.title || s?.title || '',
      subtitle: c.subtitle || s?.subtitle || '',
      bulletPoints: Array.isArray(c.body) ? c.body : (c.body ? [c.body] : []),
      imageSuggestion: c.imageSuggestion || s?.imageSuggestion || undefined,
      formula: c.formula || s?.formula || undefined,
      highlightPoints: c.highlightPoints || s?.highlightPoints || [],
      interaction: s?.interaction || null,
    };
  });
}

const TABS = [
  { key: 'slides', label: '幻灯片', icon: Monitor },
  { key: 'chat', label: '追问', icon: MessageCircle },
  { key: 'teacher', label: '老师', icon: User },
];

const LecturePresentPage = ({ lectureData, userId = 1, onBack, onFinish }) => {
  const { lectureId, title = '在线课堂', courseId, slides: rawSlides } = lectureData || {};
  const slides = useMemo(() => normalizeSlides(rawSlides), [rawSlides]);
  const hasSlides = slides.length > 0;
  const [currentSlide, setCurrentSlide] = useState(1);
  const currentSlideData = slides[currentSlide - 1];
  const [showEndPanel, setShowEndPanel] = useState(false);
  const [mobileTab, setMobileTab] = useState('slides');
  const [isPaused, setIsPaused] = useState(false);
  const prevSlideRef = useRef(1);

  // M6 事件上报辅助
  const fireEvent = useCallback((action, extra = {}) => {
    if (!lectureId) return;
    const chapterId = extra.chapterId || `ch${currentSlide}`;
    submitLectureEvent({
      lectureId: String(lectureId),
      chapterId,
      action,
      sessionId: extra.sessionId,
      kpId: extra.kpId,
    });
  }, [lectureId, currentSlide]);

  const handleSlideChange = useCallback((pageNum) => {
    const prev = prevSlideRef.current;
    prevSlideRef.current = pageNum;
    setCurrentSlide(pageNum);
    // 回到上一页视为 replay
    if (pageNum < prev) {
      fireEvent('replay', { chapterId: `ch${pageNum}` });
    }
  }, [fireEvent]);

  const handleTogglePause = useCallback(() => {
    setIsPaused(prev => {
      const next = !prev;
      fireEvent(next ? 'pause' : 'resume');
      return next;
    });
  }, [fireEvent]);

  const handleEndLecture = () => {
    fireEvent('complete');
    setShowEndPanel(true);
  };

  const handleGoPractice = () => {
    onFinish?.({ lectureId, knowledgePoints: lectureData?.knowledgePoints });
  };

  // M6: ChatPanel 消息发送时上报 ask 事件
  const handleMessageSent = useCallback(() => {
    fireEvent('ask');
  }, [fireEvent]);

  const bgGradient = {
    backgroundImage: "linear-gradient(135deg, #861FCE 0%, #861FCE 16%, #731CCD 16%, #731CCD 32%, #6B1CCF 32%, #6B1CCF 48%, #631DCE 48%, #631DCE 64%, #5A1BCE 64%, #5A1BCE 80%, rgb(86,43,205) 80%, rgb(47,8,154) 100%)",
  };

  return (
    <div className="w-full h-[100dvh] min-h-[36rem] flex flex-col lg:flex-row bg-gradient-to-br from-purple-700 via-purple-600 via-blue-600 to-blue-700" style={bgGradient}>
      {/* ═══════════════ 桌面端：左右两栏布局 ═══════════════ */}
      {/* 左侧：以 PPT 为视觉焦点的主舞台 */}
      <div className="hidden lg:flex lg:flex-1 lg:min-w-0 flex-col overflow-hidden">
        <div className="bg-slate-950/20 backdrop-blur-md border-b border-white/15">
          <Header lessonSubtitle={title} dark={true} onBack={onBack} />
        </div>
        <main className="flex-1 min-h-0 p-3 xl:p-5">
          <div className="h-full min-h-0 rounded-2xl overflow-hidden bg-slate-950/20 ring-1 ring-white/15 shadow-[0_24px_70px_rgba(15,23,42,0.28)]">
            {hasSlides ? (
              <SlideRenderer
                slides={slides}
                initialPage={1}
                mode="play"
                showNavigator={true}
                showProgress={true}
                transition="slide"
                onPageChange={handleSlideChange}
              />
            ) : (
              <div className="h-full overflow-hidden">
                <SlideViewer courseId={courseId || lectureId} projectId={lectureId} onSlideChange={handleSlideChange} />
              </div>
            )}
          </div>
        </main>
      </div>

      {/* 右侧：课堂控制与追问侧栏 */}
      <aside className="hidden lg:flex lg:w-[22rem] xl:w-[25rem] flex-shrink-0 flex-col p-3 xl:p-4 gap-3 bg-slate-950/15 border-l border-white/15 backdrop-blur-sm">
        <div className="flex items-center justify-between px-1 text-white/80">
          <div>
            <p className="text-sm font-semibold">课堂追问</p>
            <p className="text-xs text-white/50 mt-0.5">正在讲解 · 第 {currentSlide} 页</p>
          </div>
          <span className="rounded-full bg-white/10 px-2.5 py-1 text-xs font-medium">PPT</span>
        </div>
        {/* 暂停/恢复按钮 */}
        <button
          onClick={handleTogglePause}
          className={`flex-shrink-0 w-full flex items-center justify-center gap-2 py-2.5 rounded-xl text-sm font-medium shadow-lg transition-colors ${
            isPaused
              ? 'bg-amber-500/90 hover:bg-amber-500 text-white'
              : 'bg-white/20 hover:bg-white/30 text-white'
          }`}
        >
          {isPaused ? <Play className="w-4 h-4" /> : <Pause className="w-4 h-4" />}
          {isPaused ? '继续讲课' : '暂停讲课'}
        </button>
        <div className="flex-1 min-h-0 overflow-hidden rounded-2xl bg-white shadow-xl ring-1 ring-white/20">
          <ChatPanel title="向老师提问" sceneType="teaching" context={{ lectureId, slide: currentSlide, slideContent: currentSlideData?.bulletPoints?.join('\n') || '', title: currentSlideData?.title || '' }} userId={userId} visible={true} onMessageSent={handleMessageSent} />
        </div>
        <button
          onClick={handleEndLecture}
          className="flex-shrink-0 w-full flex items-center justify-center gap-2 py-2.5 rounded-xl bg-red-500/90 hover:bg-red-500 text-white text-sm font-medium shadow-lg transition-colors"
        >
          <Flag className="w-4 h-4" />结束讲课
        </button>
      </aside>

      {/* ═══════════════ 移动端：全屏 + 底部 Tab ═══════════════ */}
      {/* 幻灯片视图 */}
      <div className={`lg:hidden flex-1 flex flex-col overflow-hidden ${mobileTab !== 'slides' ? 'hidden' : ''}`}>
        <div className="bg-white/10 backdrop-blur-md border-b border-white/20 flex-shrink-0">
          <Header lessonSubtitle={title} dark={true} onBack={onBack} />
        </div>
        {hasSlides ? (
          <div className="flex-1 flex flex-col min-h-0">
            <SlideRenderer
              slides={slides}
              initialPage={1}
              mode="play"
              showNavigator={true}
              showProgress={true}
              transition="slide"
              onPageChange={handleSlideChange}
            />
          </div>
        ) : (
          <div className="flex-1 overflow-hidden">
            <SlideViewer courseId={courseId || lectureId} projectId={lectureId} onSlideChange={handleSlideChange} />
          </div>
        )}
        {/* 移动端结束按钮（幻灯片页底部） */}
        <div className="flex-shrink-0 px-4 py-2 bg-white/5 backdrop-blur-sm border-t border-white/10 flex gap-2">
          <button
            onClick={handleTogglePause}
            className={`flex-1 flex items-center justify-center gap-1.5 py-2 rounded-lg text-sm transition-all ${
              isPaused
                ? 'bg-amber-500/30 text-amber-200'
                : 'bg-white/10 text-white/60'
            }`}
          >
            {isPaused ? <Play className="w-4 h-4" /> : <Pause className="w-4 h-4" />}
            {isPaused ? '继续' : '暂停'}
          </button>
          <button
            onClick={handleEndLecture}
            className="flex-1 flex items-center justify-center gap-1.5 py-2 rounded-lg bg-white/10 text-white/70 text-sm active:bg-red-500/30 active:text-red-200 transition-all"
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
          <ChatPanel sceneType="teaching" context={{ lectureId, slide: currentSlide, slideContent: currentSlideData?.bulletPoints?.join('\n') || '', title: currentSlideData?.title || '' }} userId={userId} visible={true} onMessageSent={handleMessageSent} />
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
      {showEndPanel && <EndPanel title={title} onPractice={handleGoPractice} onContinue={() => setShowEndPanel(false)} />}
    </div>
  );
};

// ─── 结束面板（提取为模块级组件，避免每次渲染重建） ──
function EndPanel({ title, onPractice, onContinue }) {
  return (
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
            onClick={onPractice}
            className="w-full flex items-center justify-center gap-2 py-2.5 sm:py-3 bg-purple-600 text-white rounded-xl font-semibold hover:bg-purple-700 transition-colors text-sm sm:text-base"
          >
            <BookOpen className="w-4 h-4 sm:w-5 sm:h-5" />
            做配套练习
          </button>
          <button
            onClick={onContinue}
            className="w-full py-2 sm:py-2.5 text-slate-500 text-sm hover:text-slate-700 transition-colors"
          >
            继续讲课
          </button>
        </div>
      </div>
    </div>
  );
}

export default LecturePresentPage;
