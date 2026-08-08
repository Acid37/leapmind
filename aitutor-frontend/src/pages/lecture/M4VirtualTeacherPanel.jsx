import { useCallback, useEffect, useRef, useState } from 'react';
import CharacterViewer from '../../components/teacher/character/CharacterViewer';
import { synthesizeVirtualTeacherSpeech } from '../../services/virtualTeacherService';
import { sharedViewer } from '../../features/vrmViewer/viewerContext';

function fallbackNarration(slide) {
  const title = slide?.title || '当前内容';
  const points = Array.isArray(slide?.bulletPoints) ? slide.bulletPoints.filter(Boolean) : [];
  const highlights = Array.isArray(slide?.highlightPoints) ? slide.highlightPoints.filter(Boolean) : [];
  const content = [...points, ...highlights].slice(0, 4).join('。');
  return content ? `同学们，我们来看${title}。${content}。` : `同学们，我们现在来看${title}。`;
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
  className = '',
}) {
  const [status, setStatus] = useState('loading'); // loading | speaking | paused | ready | error
  const [message, setMessage] = useState('正在准备讲解…');
  const [viewerReady, setViewerReady] = useState(false);
  const requestRef = useRef(0);
  const lastSlideRef = useRef(null);
  const handleViewerReady = useCallback(() => setViewerReady(true), []);
  const handleViewerError = useCallback(() => {
    setStatus('error');
    setMessage('数字教师加载失败，仍可继续查看课件');
  }, []);

  useEffect(() => {
    if (isPaused) {
      requestRef.current += 1;
      lastSlideRef.current = null;
      sharedViewer?.model?.stopSpeaking();
      setStatus('paused');
      setMessage('讲课已暂停');
      onPlaybackChange?.(false);
      return undefined;
    }

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
        await sharedViewer?.model?.speak(audioBuffer, {
          expression: result.animation?.expression || 'neutral',
          talk: { message: narration },
          gestures: result.animation?.gestures || [],
          phonemes: result.animation?.phonemes || [],
        });
        if (!active || requestId !== requestRef.current) return;
        setStatus('ready');
        setMessage('本页讲解完成');
      } catch (error) {
        if (!active || requestId !== requestRef.current) return;
        console.warn('[M4][M8] 教师播报失败：', error);
        setStatus('error');
        setMessage('语音暂不可用，仍可继续查看课件');
      } finally {
        if (active && requestId === requestRef.current) onPlaybackChange?.(false);
      }
    };

    speakCurrentSlide();
    return () => {
      active = false;
      requestRef.current += 1;
      sharedViewer?.model?.stopSpeaking();
    };
  }, [courseId, isPaused, onPlaybackChange, slide, viewerReady]);

  useEffect(() => () => {
    requestRef.current += 1;
    sharedViewer?.model?.stopSpeaking();
    onPlaybackChange?.(false);
  }, [onPlaybackChange]);

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
      <div className="pointer-events-none absolute inset-x-0 bottom-0 bg-gradient-to-t from-slate-950 via-slate-950/70 to-transparent px-4 pb-3 pt-10">
        <div className="flex items-center gap-2 text-white">
          <span className={`h-2 w-2 rounded-full ${statusStyle}`} />
          <span className="text-sm font-semibold">虚拟教师</span>
        </div>
        <p className="mt-1 text-xs text-white/70">{message}</p>
      </div>
    </section>
  );
}
