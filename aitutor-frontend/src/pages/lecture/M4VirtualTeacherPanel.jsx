import { useCallback, useEffect, useRef, useState } from 'react';
import CharacterViewer from '../../components/teacher/character/CharacterViewer';
import { synthesizeVirtualTeacherSpeech } from '../../services/virtualTeacherService';
import { sharedViewer } from '../../features/vrmViewer/viewerContext';

function fallbackNarration(slide) {
  const title = slide?.title || '当前内容';
  const points = Array.isArray(slide?.bulletPoints) ? slide.bulletPoints.filter(Boolean) : [];
  const highlights = Array.isArray(slide?.highlightPoints) ? slide.highlightPoints.filter(Boolean) : [];
  const type = slide?.type || 'content';
  const opening = type === 'cover'
    ? `同学们好，今天我们一起学习${title}。先不用急着记结论，我们会从现象出发，一步一步把它弄明白。`
    : type === 'interactive'
      ? `好，接下来我们用一个问题检验一下刚才的理解。题目围绕${title}展开，先自己想一想，再看每个条件之间有什么联系。`
      : type === 'summary'
        ? `学到这里，我们把${title}梳理一下。不要只记零散句子，关键是把前后的逻辑串起来。`
        : `好，我们接着来看${title}。这一部分不需要逐字背诵，先抓住它在整个知识结构里的作用。`;
  const explanations = points.slice(0, 3).map((point, index) => {
    const text = String(point).replace(/[。；;]+$/, '');
    if (index === 0) return `第一个关键点是${text}。换句话说，这是理解后面内容的基础。`;
    if (index === 1) return `再来看${text}。大家可以把它和刚才的关键点联系起来。`;
    return `最后注意${text}。这里经常容易混淆，判断时要结合具体条件。`;
  });
  const emphasis = highlights[0]
    ? `这里老师特别提醒一下：${String(highlights[0]).replace(/[。；;]+$/, '')}。`
    : '';
  const interaction = slide?.interaction?.question
    ? `现在请你想一想：${slide.interaction.question}`
    : '听到这里，你能用自己的话复述一下核心思路吗？';
  return [opening, ...explanations, emphasis, interaction].filter(Boolean).join('');
}

/**
 * M4 讲课页的 M8 虚拟教师适配层。
 *
 * 使用唯一的 sharedViewer 渲染教师形象，并通过 M8 TTS 驱动同一模型的
 * 口型、表情和动作。讲稿缺失时降级为基于当前幻灯片内容生成的短讲解词。
 */
export default function M4VirtualTeacherPanel({
  slide,
  courseId,
  isPaused,
  onPlaybackChange,
  externalCaption = '',
  externalSpeaking = false,
  className = '',
}) {
  const [status, setStatus] = useState('loading'); // loading | speaking | paused | ready | error
  const [message, setMessage] = useState('正在准备讲解…');
  const [viewerReady, setViewerReady] = useState(false);
  const [captionText, setCaptionText] = useState('');
  const [showFullCaption, setShowFullCaption] = useState(false);
  const requestRef = useRef(0);
  const lastSlideRef = useRef(null);
  const captionTimerRef = useRef(null);
  const compactCaptionRef = useRef(null);
  const captionPlaybackRef = useRef({
    narration: '',
    durationMs: 0,
    elapsedMs: 0,
    startedAt: 0,
  });
  const isPausedRef = useRef(isPaused);
  const handleViewerReady = useCallback(() => setViewerReady(true), []);
  const handleViewerError = useCallback(() => {
    setStatus('error');
    setMessage('数字教师加载失败，仍可继续查看课件');
  }, []);

  useEffect(() => {
    isPausedRef.current = isPaused;
  }, [isPaused]);

  const startCaptionTimer = useCallback(() => {
    const playback = captionPlaybackRef.current;
    if (!playback.narration || !playback.durationMs) return;
    playback.startedAt = Date.now();
    window.clearInterval(captionTimerRef.current);
    captionTimerRef.current = window.setInterval(() => {
      const elapsed = playback.elapsedMs + (Date.now() - playback.startedAt);
      const ratio = Math.min(1, elapsed / playback.durationMs);
      const end = Math.max(1, Math.ceil(playback.narration.length * ratio));
      setCaptionText(playback.narration.slice(0, end));
      if (ratio >= 1) window.clearInterval(captionTimerRef.current);
    }, 120);
  }, []);

  useEffect(() => {
    if (!viewerReady) return undefined;

    const narration = String(slide?.narrationText || fallbackNarration(slide)).trim();
    const slideKey = `${slide?.pageNum ?? ''}:${narration}`;
    if (!narration || !slideKey || lastSlideRef.current === slideKey) return undefined;

    lastSlideRef.current = slideKey;
    const requestId = ++requestRef.current;
    let active = true;

    const speakCurrentSlide = async () => {
      setStatus('loading');
      setMessage('老师正在准备讲解…');
      onPlaybackChange?.(true);
      try {
        const result = await synthesizeVirtualTeacherSpeech({
          courseId: courseId ? String(courseId) : undefined,
          text: narration,
        });
        if (!active || requestId !== requestRef.current) return;
        if (!result?.audioBlob) throw new Error('未获取到讲课音频');

        const audioBuffer = await result.audioBlob.arrayBuffer();
        if (!active || requestId !== requestRef.current) return;

        setStatus('speaking');
        setMessage('老师正在讲解');
        setCaptionText('');
        const durationMs = result.durationMs || Math.max(5000, narration.length * 230);
        captionPlaybackRef.current = {
          narration,
          durationMs,
          elapsedMs: 0,
          startedAt: Date.now(),
        };
        startCaptionTimer();
        const model = sharedViewer?.model;
        const speechPromise = model?.speak(audioBuffer, {
          expression: result.animation?.expression || 'neutral',
          talk: { message: narration },
          gestures: result.animation?.gestures || [],
          phonemes: result.animation?.phonemes || [],
        });
        if (isPausedRef.current) {
          await model?.pauseSpeaking?.();
        }
        await speechPromise;
        if (!active || requestId !== requestRef.current) return;
        setStatus('ready');
        setMessage('本页讲解完成');
        setCaptionText(narration);
      } catch (error) {
        if (!active || requestId !== requestRef.current) return;
        console.warn('[M4][M8] 教师播报失败：', error);
        setStatus('error');
        const statusCode = error?.code || error?.status;
        setMessage(statusCode
          ? `语音服务暂不可用（HTTP ${statusCode}），仍可继续查看课件`
          : '语音播放失败，仍可继续查看课件');
      } finally {
        if (active && requestId === requestRef.current) onPlaybackChange?.(false);
      }
    };

    speakCurrentSlide();
    return () => {
      active = false;
      requestRef.current += 1;
      window.clearInterval(captionTimerRef.current);
      sharedViewer?.model?.stopSpeaking();
    };
  }, [courseId, onPlaybackChange, slide, startCaptionTimer, viewerReady]);

  useEffect(() => {
    if (!viewerReady) return;
    const model = sharedViewer?.model;
    if (isPaused) {
      const playback = captionPlaybackRef.current;
      if (playback.startedAt) {
        playback.elapsedMs += Date.now() - playback.startedAt;
        playback.startedAt = 0;
      }
      window.clearInterval(captionTimerRef.current);
      model?.pauseSpeaking?.().catch((error) => {
        console.warn('[M4][M8] 暂停讲课音频失败：', error);
      });
      setStatus('paused');
      setMessage('讲课已暂停');
      onPlaybackChange?.(false);
      return;
    }

    if (model?.hasActiveSpeech?.()) {
      model.resumeSpeaking?.().then((resumed) => {
        if (!resumed) return;
        startCaptionTimer();
        setStatus('speaking');
        setMessage('老师正在讲解');
        onPlaybackChange?.(true);
      }).catch((error) => {
        console.warn('[M4][M8] 继续讲课音频失败：', error);
      });
    }
  }, [isPaused, onPlaybackChange, startCaptionTimer, viewerReady]);

  useEffect(() => () => {
    requestRef.current += 1;
    window.clearInterval(captionTimerRef.current);
    sharedViewer?.model?.stopSpeaking();
    onPlaybackChange?.(false);
  }, [onPlaybackChange]);

  const visibleCaption = externalSpeaking && externalCaption ? externalCaption : captionText;

  useEffect(() => {
    if (showFullCaption || !compactCaptionRef.current) return;
    compactCaptionRef.current.scrollTop = compactCaptionRef.current.scrollHeight;
  }, [showFullCaption, visibleCaption]);

  const statusStyle = {
    loading: 'bg-amber-400',
    speaking: 'bg-emerald-400 animate-pulse',
    paused: 'bg-amber-400',
    error: 'bg-rose-400',
    ready: 'bg-white/60',
  }[status] || 'bg-white/60';

  return (
    <section className={`relative min-h-0 overflow-hidden bg-gradient-to-b from-indigo-950/80 via-violet-900/55 to-slate-950/75 ${className}`}>
      <CharacterViewer
        onReady={handleViewerReady}
        onError={handleViewerError}
      />
      <div className="absolute inset-x-0 bottom-0 bg-gradient-to-t from-slate-950 via-slate-950/80 to-transparent px-4 pb-3 pt-12">
        <div className="flex items-center gap-2 text-white">
          <span className={`h-2 w-2 rounded-full ${statusStyle}`} />
          <span className="text-sm font-semibold">虚拟教师</span>
        </div>
        <p className="mt-1 text-xs text-white/70">{message}</p>
        {visibleCaption && (
          <div className="mt-2 rounded-lg border border-white/10 bg-black/30 px-3 py-2 text-xs leading-5 text-white/90 backdrop-blur-sm">
            <p
              ref={compactCaptionRef}
              className={showFullCaption ? 'max-h-28 overflow-y-auto' : 'max-h-10 overflow-hidden'}
            >
              {visibleCaption}
            </p>
            <button
              type="button"
              onClick={() => setShowFullCaption((value) => !value)}
              className="mt-1 text-[10px] font-medium text-violet-200 hover:text-white"
            >
              {showFullCaption ? '收起全文' : '查看完整字幕'}
            </button>
          </div>
        )}
      </div>
    </section>
  );
}
