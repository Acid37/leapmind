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

import React, { useState, useCallback, useMemo, useEffect, useRef } from 'react';
import Header from '../../components/common/Header';
import SlideRenderer from '../../components/lecture/SlideRenderer';
import SlideViewer from '../../components/lecture/SlideViewer';
import M4VirtualTeacherPanel from './M4VirtualTeacherPanel';
import { ChatPanel } from '../../components/chat';
import { recordLectureInteraction } from '../../services/learningEventService';
import { synthesizeVirtualTeacherSpeech } from '../../services/virtualTeacherService';
import { sharedViewer } from '../../features/vrmViewer/viewerContext';
import { Flag, BookOpen, House, MessageCircle, Monitor, User, Pause, Play, Mic, MicOff } from 'lucide-react';

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
      bulletPoints: Array.isArray(c.body)
        ? c.body
        : c.body
          ? [c.body]
          : Array.isArray(s?.bulletPoints)
            ? s.bulletPoints
            : Array.isArray(s?.bullet_points)
              ? s.bullet_points
              : [],
      imageSuggestion: c.imageSuggestion || s?.imageSuggestion || undefined,
      formula: c.formula || s?.formula || undefined,
      highlightPoints: c.highlightPoints || s?.highlightPoints || [],
      interaction: s?.interaction || null,
      narrationText: s?.narrationText ?? s?.narration_text ?? c.narrationText ?? c.narration_text ?? '',
      estimatedDurationSec: s?.estimatedDurationSec ?? s?.estimated_duration_seconds,
    };
  });
}

const TABS = [
  { key: 'slides', label: '幻灯片', icon: Monitor },
  { key: 'chat', label: '追问', icon: MessageCircle },
  { key: 'teacher', label: '老师', icon: User },
];

const LecturePresentPage = ({ lectureData, userId, onBack, onHome, onFinish }) => {
  const { lectureId, title = '在线课堂', courseId, slides: rawSlides } = lectureData || {};
  const slides = useMemo(() => normalizeSlides(rawSlides), [rawSlides]);
  const hasSlides = slides.length > 0;
  const [currentSlide, setCurrentSlide] = useState(1);
  const currentSlideData = slides[currentSlide - 1];
  const chatContext = useMemo(() => ({
    lectureId,
    slide: currentSlide,
    currentSlide,
    slideTitle: currentSlideData?.title || '',
    slideContent: currentSlideData?.bulletPoints?.join('\n') || '',
  }), [currentSlide, currentSlideData, lectureId]);
  const [showEndPanel, setShowEndPanel] = useState(false);
  const [mobileTab, setMobileTab] = useState('slides');
  const [isPaused, setIsPaused] = useState(false);
  const [, setIsTeacherSpeaking] = useState(false);
  const [answerCaption, setAnswerCaption] = useState('');
  const [isAnswerSpeaking, setIsAnswerSpeaking] = useState(false);
  const [voiceInterruptEnabled, setVoiceInterruptEnabled] = useState(false);
  const [voiceInterruptStatus, setVoiceInterruptStatus] = useState('实时打断未开启');
  const [externalQuestion, setExternalQuestion] = useState(null);
  const recognitionRef = useRef(null);
  const recognitionSuspendedRef = useRef(false);
  const interruptTriggeredRef = useRef(false);
  const answerAudioRef = useRef(null);
  const interactionSequenceRef = useRef(0);
  const interactionSessionRef = useRef(
    lectureData?.sessionId
      || `m4-${globalThis.crypto?.randomUUID?.() || `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`}`,
  );
  const [isDesktop, setIsDesktop] = useState(() =>
    typeof window !== 'undefined' && window.matchMedia('(min-width: 1024px)').matches
  );

  // M8 使用全局 sharedViewer；同一时间只能挂载一个 VRM 渲染容器。
  useEffect(() => {
    const mediaQuery = window.matchMedia('(min-width: 1024px)');
    const syncViewport = () => setIsDesktop(mediaQuery.matches);
    syncViewport();
    mediaQuery.addEventListener('change', syncViewport);
    return () => mediaQuery.removeEventListener('change', syncViewport);
  }, []);

  // M6 统一画像事件：每次页面挂载创建独立会话，序号在该会话内连续递增。
  const fireEvent = useCallback((action, extra = {}) => {
    if (!lectureId) return;
    interactionSequenceRef.current += 1;
    const sessionId = interactionSessionRef.current;
    const chapterId = extra.chapterId || `ch${currentSlide}`;
    recordLectureInteraction({
      userId,
      lectureId,
      chapterId,
      action,
      interactionId: `${sessionId}:${interactionSequenceRef.current}`,
      sessionId,
      kpId: extra.kpId ?? lectureData?.knowledgePoints?.[0]?.id ?? lectureData?.knowledgePoints?.[0]?.kpId,
    }).catch((error) => console.warn('[M4][M6] 讲课交互事件上报失败:', error.message));
  }, [lectureData?.knowledgePoints, lectureId, currentSlide, userId]);

  const handleSlideChange = useCallback((pageNum) => {
    const previousPage = currentSlide;
    setCurrentSlide(pageNum);
    if (pageNum < previousPage) fireEvent('replay', { chapterId: `ch${pageNum}` });
  }, [currentSlide, fireEvent]);

  const handleTogglePause = useCallback(() => {
    setIsPaused(prev => {
      const next = !prev;
      fireEvent(next ? 'pause' : 'resume');
      return next;
    });
  }, [fireEvent]);

  const handleEndLecture = () => {
    setIsPaused(true);
    setIsTeacherSpeaking(false);
    fireEvent('complete');
    setShowEndPanel(true);
  };

  const handleGoPractice = () => {
    onFinish?.({ lectureId, knowledgePoints: lectureData?.knowledgePoints });
  };

  const handleMessageSent = useCallback(() => {
    fireEvent('ask');
    sharedViewer?.model?.stopSpeaking();
    sharedViewer?.model?.resumeAudio?.().catch((error) => {
      console.warn('[M4][M8] 音频上下文预解锁失败：', error);
    });
    setIsTeacherSpeaking(false);
  }, [fireEvent]);

  const playNativeAudioFallback = useCallback(async (audioBlob) => {
    if (answerAudioRef.current) {
      answerAudioRef.current.pause();
      answerAudioRef.current = null;
    }
    const audioUrl = URL.createObjectURL(audioBlob);
    const audio = new Audio(audioUrl);
    answerAudioRef.current = audio;
    try {
      await audio.play();
      await new Promise((resolve, reject) => {
        audio.addEventListener('ended', resolve, { once: true });
        audio.addEventListener('error', () => reject(new Error('回答音频播放失败')), { once: true });
      });
    } finally {
      if (answerAudioRef.current === audio) answerAudioRef.current = null;
      URL.revokeObjectURL(audioUrl);
    }
  }, []);

  const handleAssistantComplete = useCallback(async (answer) => {
    const text = String(answer || '').trim();
    if (!text) return;
    setAnswerCaption(text);
    setIsAnswerSpeaking(true);
    setIsTeacherSpeaking(true);
    try {
      const result = await synthesizeVirtualTeacherSpeech({
        courseId: courseId || lectureId ? String(courseId || lectureId) : undefined,
        text,
      });
      if (!result?.audioBlob) return;
      const audioBuffer = await result.audioBlob.arrayBuffer();
      const model = sharedViewer?.model;
      if (model?.speak) {
        try {
          await model.speak(audioBuffer, {
            expression: result.animation?.expression || 'neutral',
            talk: { message: text },
            gestures: result.animation?.gestures || [],
            phonemes: result.animation?.phonemes || [],
          });
        } catch (playbackError) {
          console.warn('[M4][M8] VRM 回答音频播放失败，切换原生播放器：', playbackError);
          await playNativeAudioFallback(result.audioBlob);
        }
      } else {
        console.warn('[M4][M8] VRM 模型尚未挂载，使用原生播放器播报回答');
        await playNativeAudioFallback(result.audioBlob);
      }
    } catch (error) {
      console.warn('[M4][M7][M8] 回答语音播放失败：', error);
    } finally {
      setIsAnswerSpeaking(false);
      setIsTeacherSpeaking(false);
      if (interruptTriggeredRef.current) {
        interruptTriggeredRef.current = false;
        setIsPaused(false);
        recognitionSuspendedRef.current = false;
        setVoiceInterruptStatus('正在监听“小跃老师…”');
        try { recognitionRef.current?.start(); } catch { /* 已在运行或尚未就绪 */ }
      }
    }
  }, [courseId, lectureId, playNativeAudioFallback]);

  useEffect(() => () => {
    if (answerAudioRef.current) {
      answerAudioRef.current.pause();
      answerAudioRef.current = null;
    }
  }, []);

  const handleToggleVoiceInterrupt = useCallback(() => {
    sharedViewer?.model?.resumeAudio?.().catch((error) => {
      console.warn('[M4][M8] 实时打断音频预解锁失败：', error);
    });
    setVoiceInterruptEnabled((enabled) => !enabled);
  }, []);

  useEffect(() => {
    if (!voiceInterruptEnabled) {
      if (recognitionRef.current) {
        try { recognitionRef.current.stop(); } catch { /* ignore */ }
        recognitionRef.current = null;
      }
      setVoiceInterruptStatus('实时打断未开启');
      return undefined;
    }

    const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
    if (!SpeechRecognition) {
      setVoiceInterruptEnabled(false);
      setVoiceInterruptStatus('当前浏览器不支持实时语音打断');
      return undefined;
    }

    const recognition = new SpeechRecognition();
    recognition.continuous = true;
    recognition.interimResults = true;
    recognition.lang = 'zh-CN';
    recognitionRef.current = recognition;
    let active = true;

    recognition.onstart = () => setVoiceInterruptStatus('正在监听“小跃老师…”');
    recognition.onresult = (event) => {
      for (let i = event.resultIndex; i < event.results.length; i += 1) {
        const transcript = String(event.results[i][0]?.transcript || '').trim();
        if (!transcript.includes('小跃老师')) continue;

        if (!interruptTriggeredRef.current) {
          interruptTriggeredRef.current = true;
          sharedViewer?.model?.stopSpeaking();
          setIsPaused(true);
          setIsTeacherSpeaking(false);
          setVoiceInterruptStatus('已暂停讲解，请继续说完问题');
        }

        if (event.results[i].isFinal) {
          const question = transcript
            .replace(/^.*?小跃老师[，,。.!！?？\s]*/, '')
            .trim();
          if (question) {
            recognitionSuspendedRef.current = true;
            try { recognition.stop(); } catch { /* ignore */ }
            setExternalQuestion({ id: Date.now(), text: question });
            setVoiceInterruptStatus(`正在回答：${question}`);
          } else {
            interruptTriggeredRef.current = false;
            setIsPaused(false);
            setVoiceInterruptStatus('没有识别到问题，请说“小跃老师 + 问题”');
          }
        }
      }
    };
    recognition.onerror = (event) => {
      if (event.error === 'not-allowed' || event.error === 'service-not-allowed') {
        setVoiceInterruptEnabled(false);
        setVoiceInterruptStatus('请允许浏览器使用麦克风');
      } else if (event.error !== 'no-speech' && event.error !== 'aborted') {
        setVoiceInterruptStatus(`语音识别暂不可用：${event.error}`);
      }
    };
    recognition.onend = () => {
      if (active && voiceInterruptEnabled && !recognitionSuspendedRef.current) {
        try { recognition.start(); } catch { /* ignore */ }
      }
    };

    try { recognition.start(); } catch {
      setVoiceInterruptStatus('实时语音打断启动失败');
    }
    return () => {
      active = false;
      try { recognition.stop(); } catch { /* ignore */ }
      recognitionRef.current = null;
    };
  }, [voiceInterruptEnabled]);

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

      {/* 右侧：M8 数字教师讲课 + M7 课堂追问 */}
      <aside className="hidden lg:flex lg:w-[22rem] xl:w-[25rem] flex-shrink-0 flex-col p-3 xl:p-4 gap-3 bg-slate-950/15 border-l border-white/15 backdrop-blur-sm">
        <div className="flex items-center justify-between px-1 text-white/80">
          <div>
            <p className="text-sm font-semibold">课堂追问</p>
            <p className="text-xs text-white/50 mt-0.5">正在讲解 · 第 {currentSlide} 页</p>
          </div>
          <button
            type="button"
            onClick={handleToggleVoiceInterrupt}
            className={`flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-medium transition-colors ${
              voiceInterruptEnabled ? 'bg-emerald-400/20 text-emerald-100' : 'bg-white/10 text-white/70'
            }`}
            title="开启后说“小跃老师 + 问题”即可打断"
          >
            {voiceInterruptEnabled ? <Mic className="h-3.5 w-3.5" /> : <MicOff className="h-3.5 w-3.5" />}
            {voiceInterruptEnabled ? '实时打断' : '开启打断'}
          </button>
        </div>
        <p className="-mt-2 px-1 text-[10px] text-white/45">{voiceInterruptStatus}</p>
        {/* 同一张课堂卡片：数字教师出镜与 M7 追问无缝衔接，避免视觉上割裂成两个组件。 */}
        <div className="flex-1 min-h-0 overflow-hidden rounded-2xl bg-white shadow-xl ring-1 ring-white/20 flex flex-col">
          {isDesktop && (
            <div className="h-60 xl:h-72 flex-shrink-0 overflow-hidden border-b border-white/15">
              <M4VirtualTeacherPanel
                className="h-full"
                slide={currentSlideData}
                courseId={courseId || lectureId}
                isPaused={isPaused}
                onPlaybackChange={setIsTeacherSpeaking}
                externalCaption={answerCaption}
                externalSpeaking={isAnswerSpeaking}
              />
            </div>
          )}
          <div className="flex-1 min-h-0 overflow-hidden">
            {isDesktop && (
              <ChatPanel
                title="向老师提问"
                sceneType="teaching"
                context={chatContext}
                userId={userId}
                visible={true}
                onMessageSent={handleMessageSent}
                onAssistantComplete={handleAssistantComplete}
                externalQuestion={externalQuestion}
                autoRestore={false}
              />
            )}
          </div>
        </div>
        {/* 课堂控制：同一行左右分布，避免占用 ChatPanel 的垂直空间 */}
        <div className="flex-shrink-0 grid grid-cols-2 gap-2">
          <button
            onClick={handleTogglePause}
            className={`flex items-center justify-center gap-2 py-2.5 rounded-xl text-sm font-medium shadow-lg transition-colors ${
              isPaused
                ? 'bg-amber-500/90 hover:bg-amber-500 text-white'
                : 'bg-white/20 hover:bg-white/30 text-white'
            }`}
          >
            {isPaused ? <Play className="w-4 h-4" /> : <Pause className="w-4 h-4" />}
            {isPaused ? '继续讲课' : '暂停讲课'}
          </button>
          <button
            onClick={handleEndLecture}
            className="flex items-center justify-center gap-2 py-2.5 rounded-xl bg-red-500/90 hover:bg-red-500 text-white text-sm font-medium shadow-lg transition-colors"
          >
            <Flag className="w-4 h-4" />结束讲课
          </button>
        </div>
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
          {!isDesktop && (
            <ChatPanel sceneType="teaching" context={chatContext} userId={userId} visible={true} onMessageSent={handleMessageSent} onAssistantComplete={handleAssistantComplete} externalQuestion={externalQuestion} autoRestore={false} />
          )}
        </div>
      </div>

      {/* 教师视图 */}
      <div className={`lg:hidden flex-1 flex flex-col ${mobileTab !== 'teacher' ? 'hidden' : ''}`}>
        <div className="bg-white/10 backdrop-blur-md border-b border-white/20 flex-shrink-0">
          <Header lessonSubtitle={title} dark={true} onBack={onBack} />
        </div>
        <div className="flex-1 min-h-0 p-3">
          {!isDesktop && mobileTab === 'teacher' && (
            <M4VirtualTeacherPanel
              slide={currentSlideData}
              courseId={courseId || lectureId}
              isPaused={isPaused}
              onPlaybackChange={setIsTeacherSpeaking}
              externalCaption={answerCaption}
              externalSpeaking={isAnswerSpeaking}
            />
          )}
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
      {showEndPanel && (
        <EndPanel
          title={title}
          onPractice={handleGoPractice}
          onHome={onHome}
          onContinue={() => setShowEndPanel(false)}
        />
      )}
    </div>
  );
};

// ─── 结束面板（提取为模块级组件，避免每次渲染重建） ──
function EndPanel({ title, onPractice, onHome, onContinue }) {
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
            onClick={onHome}
            className="w-full flex items-center justify-center gap-2 py-2.5 sm:py-3 border border-slate-200 text-slate-600 rounded-xl font-medium hover:border-purple-200 hover:bg-purple-50 hover:text-purple-700 transition-colors text-sm sm:text-base"
          >
            <House className="w-4 h-4 sm:w-5 sm:h-5" />
            回到首页
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
