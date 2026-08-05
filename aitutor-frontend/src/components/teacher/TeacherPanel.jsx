import React, { useState, useEffect, useRef, useCallback } from 'react';
import ChatInput from '../chat/ChatInput';
import ChatDialog from '../chat/ChatDialog';
import CharacterViewer from './character/CharacterViewer.jsx';
import { initPptInteractivePlayer } from '@/features/chat/ppt-interactive-player.js';
import { state } from '@/features/chat/pptState.js';
import { sharedViewer } from '@/features/vrmViewer/viewerContext.js';

const TeacherPanel = ({ dark = false, lectureId, slides, currentSlide, onSlideChange }) => {
    const [messages, setMessages] = useState([]);
    const [isChatVisible, setIsChatVisible] = useState(false);
    const [isNarrating, setIsNarrating] = useState(false);
    const [narrationTrigger, setNarrationTrigger] = useState(0); // 强制触发讲课的计数器
    const utteranceRef = useRef(null);
    const narrationPausedRef = useRef(false);
    const onSlideChangeRef = useRef(onSlideChange);
    const isNarratingRef = useRef(isNarrating);
    const currentSlideRef = useRef(currentSlide);
    onSlideChangeRef.current = onSlideChange;
    isNarratingRef.current = isNarrating;
    currentSlideRef.current = currentSlide;

    useEffect(() => {
        if (lectureId) state.currentCourseId = String(lectureId);
        state.currentPageNumber = currentSlide;
    }, [lectureId, currentSlide]);

    const getSlideNarrationText = useCallback((slideIndex) => {
        const slide = slides?.[slideIndex];
        if (!slide?.content) return '';
        const c = slide.content;
        if (c.narration) return c.narration;
        let parts = [];
        if (c.title) parts.push(c.title);
        if (c.subtitle) parts.push(c.subtitle);
        if (Array.isArray(c.body)) parts.push(c.body.filter(Boolean).join('。'));
        if (c.highlightPoints?.length) parts.push(c.highlightPoints.filter(Boolean).join('，'));
        return parts.filter(Boolean).join('。');
    }, [slides]);

    // 朗读文本，返回 cleanup 函数
    const speakText = useCallback((text, onEnd) => {
        window.speechSynthesis.cancel();
        const u = new SpeechSynthesisUtterance(text);
        u.lang = 'zh-CN';
        u.rate = 0.9;
        u.onend = () => { utteranceRef.current = null; onEnd?.(); };
        u.onerror = (e) => {
            utteranceRef.current = null;
            if (e.error !== 'canceled' && e.error !== 'interrupted') onEnd?.();
        };
        utteranceRef.current = u;
        window.speechSynthesis.speak(u);
        return () => {
            window.speechSynthesis.cancel();
            utteranceRef.current = null;
        };
    }, []);

    // 当前页变化 → 讲课（同时控制口型和手势）
    useEffect(() => {
        if (!slides?.length || !isNarrating) return;
        const idx = currentSlide - 1;
        if (idx < 0 || idx >= slides.length) { setIsNarrating(false); return; }
        const text = getSlideNarrationText(idx);
        if (!text) {
            const next = idx + 2;
            if (next <= slides.length) onSlideChangeRef.current?.(next);
            return;
        }

        setMessages(prev => {
            const last = prev[prev.length - 1];
            const msg = { sender: 'ai', text: `📖 第${currentSlide}页：${text.substring(0, 30)}…` };
            if (!last || last.isTyping || last.isOptions) return [...prev, msg];
            return prev;
        });

        // 讲课开始 → 打开模拟口型 + 手势
        sharedViewer?.model?.setSimulatedSpeech(true);
        sharedViewer?.model?.pointAtPPT(true);

        const cancel = speakText(text, () => {
            if (narrationPausedRef.current) return;
            const next = idx + 2;
            if (next <= slides.length) {
                onSlideChangeRef.current?.(next);
            } else {
                setIsNarrating(false);
                setMessages(prev => [...prev, { sender: 'ai', text: '🎉 讲课结束！有疑问可以随时问我哦。' }]);
            }
        });

        return () => {
            sharedViewer?.model?.setSimulatedSpeech(false);
            sharedViewer?.model?.pointAtPPT(false);
            cancel?.();
        };
    }, [currentSlide, isNarrating, narrationTrigger, slides, getSlideNarrationText, speakText]);

    // 学生提问
    const handleSendMessage = async (text) => {
        setIsChatVisible(true);
        setMessages(prev => [...prev, { sender: 'user', text }, { sender: 'ai', isTyping: true }]);

        const wasNarrating = isNarratingRef.current;
        if (wasNarrating) {
            narrationPausedRef.current = true;
            sharedViewer?.model?.setSimulatedSpeech(false);
            sharedViewer?.model?.pointAtPPT(false);
            window.speechSynthesis.cancel();
            utteranceRef.current = null;
        }

        // 简单 fallback：后端不可用时直接模拟回复
        let answer;
        try {
            const { askQuestion } = await import('@/features/chat/pptApi.js');
            const slideContext = slides?.[(currentSlideRef.current || 1) - 1];
            const slideTitle = slideContext?.content?.title || '';
            const result = await askQuestion(
                state.currentCourseId || '',
                `[第${currentSlideRef.current}页《${slideTitle}》] ${text}`
            );
            answer = result?.answer || '';
        } catch (_) {}

        if (!answer) {
            answer = '这是一个很好的问题！让我来为你解答：' + text.replace(/[?？]/g, '') + '。在当前的课程内容中，我们可以从这些知识点入手来理解这个问题。如果你还有疑问，随时可以问我哦。';
        }

        setMessages(prev => {
            const next = [...prev];
            const idx = next.findIndex(m => m.isTyping);
            if (idx !== -1) next[idx] = { sender: 'ai', text: answer };
            else next.push({ sender: 'ai', text: answer });
            next.push({ sender: 'ai', isOptions: true, wasNarrating });
            return next;
        });

        // 老师出声朗读回答（带口型）
        setTimeout(() => {
            sharedViewer?.model?.setSimulatedSpeech(true);
            speakText(answer, () => {
                sharedViewer?.model?.setSimulatedSpeech(false);
            });
        }, 200);
    };

    const handleContinueLecture = () => {
        setMessages(prev => [...prev, { sender: 'ai', text: '好的，我们继续讲课 👩‍🏫' }]);
        narrationPausedRef.current = false;
        setNarrationTrigger(t => t + 1); // 触发当前页重新讲
    };

    const handleContinueAsk = () => {
        sharedViewer?.model?.setSimulatedSpeech(false);
        sharedViewer?.model?.pointAtPPT(false);
        window.speechSynthesis.cancel(); // 停止当前回答朗读
        utteranceRef.current = null;
        narrationPausedRef.current = true;
        setMessages(prev => [...prev, { sender: 'ai', text: '请继续提问，我会一一解答 🙋' }]);
    };

    const toggleNarration = () => {
        if (isNarrating) {
            sharedViewer?.model?.setSimulatedSpeech(false);
            sharedViewer?.model?.pointAtPPT(false);
            window.speechSynthesis.cancel();
            utteranceRef.current = null;
            setIsNarrating(false);
            narrationPausedRef.current = false;
        } else {
            narrationPausedRef.current = false;
            setIsNarrating(true);
            setNarrationTrigger(t => t + 1); // 从当前页继续讲
        }
    };

    useEffect(() => {
        if (messages.length > 0 && !messages[messages.length - 1]?.isOptions) {
            setIsChatVisible(true);
        }
    }, [messages]);
    useEffect(() => { initPptInteractivePlayer(); }, []);

    const slide = slides?.[currentSlide - 1];
    const title = slide?.content?.title || '';

    return (
        <aside className={`w-full h-full flex flex-col ${dark ? 'bg-transparent' : 'bg-slate-50'}`}>
            <div className="flex-shrink-0 flex items-center justify-between px-2 py-1 bg-white/5 border-b border-white/10">
                <span className="text-white/50 text-xs">
                    第 {currentSlide}/{slides?.length || '?'} 页
                </span>
                <button
                    onClick={toggleNarration}
                    className={`text-xs px-2 py-0.5 rounded-full transition-colors ${
                        isNarrating
                            ? 'text-green-300 bg-green-500/20 hover:bg-green-500/30'
                            : 'text-white/40 bg-white/10 hover:bg-white/20'
                    }`}
                >
                    {isNarrating ? '⏸ 暂停讲课' : '▶ 开始讲课'}
                </button>
            </div>

            <div className="flex-1 min-h-0 relative">
                <CharacterViewer />
            </div>

            {title && (
                <div className="flex-shrink-0 px-2 py-1 text-white/40 text-[10px] truncate bg-white/5">
                    📄 {title}
                </div>
            )}

            <div className="flex-shrink-0 relative p-2">
                <audio id="responseAudio" className="hidden" />
                <ChatDialog
                    messages={messages}
                    isVisible={isChatVisible}
                    onClose={() => setIsChatVisible(false)}
                    onContinueLecture={handleContinueLecture}
                    onContinueAsk={handleContinueAsk}
                />
                <ChatInput onSendMessage={handleSendMessage} setIsTeacherListening={() => {}} />
            </div>
        </aside>
    );
};

export default TeacherPanel;
