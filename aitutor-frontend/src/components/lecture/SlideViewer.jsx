import React, { useCallback, useEffect, useMemo, useRef, useState } from "react";
import slidesData from "../../data/slideData";
import SlideCard from "./SlideCard";
import SubtitleBar from "./SubtitleBar";
import Navigation from "./Navigation";
import { getOrCreateCourseId, setCourseId } from "../../features/chat/pptSession";
import { fetchSegments } from "../../features/chat/pptApi";
import { showError, showSuccess } from "../../features/chat/pptUi";
import { state as playerState } from "../../features/chat/pptState";
import { startPlayback, updatePlaybackControls, updatePlaybackStatus } from "../../features/chat/pptController";
import { get } from "../../services/api";

const IFRAME_WIDTH = 1280;
const IFRAME_HEIGHT = 720;

const SlideViewer = ({ projectId, courseId: courseIdProp }) => {
    // 分支A：有 courseId，改为从后端拉取 slides-data 并用 iframe 渲染
    // 原写死路由: ${apiBase}/api/courses/${courseId}/slides-data
    // 改为相对路径以触发 Vite 代理
    // const apiBase = useMemo(() => {
    //     const raw = import.meta.env.VITE_API_BASE || '';
    //     return raw.endsWith('/') ? raw.slice(0, -1) : raw;
    // }, []);
    // 兼容旧入参：优先使用 courseId，其次回退到 projectId
    const courseId = useMemo(() => courseIdProp ?? projectId, [courseIdProp, projectId]);

    // 将路由/入参的 courseId 同步为全局会话的 source of truth，避免误用旧的 localStorage 值
    useEffect(() => {
        if (courseId) {
            try {
                setCourseId(String(courseId));
            } catch (_) {}
        }
    }, [courseId]);

    const [remoteSlides, setRemoteSlides] = useState([]);
    const [updatedAt, setUpdatedAt] = useState(0);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');
    const [currentRemoteIndex, setCurrentRemoteIndex] = useState(0);
    const [displayedRemoteIndex, setDisplayedRemoteIndex] = useState(0);
    const [transitionDirection, setTransitionDirection] = useState('next'); // 'next' | 'prev'
    const transitionTimerRef = useRef(null);
    const [isTransitioning, setIsTransitioning] = useState(false);
    const [incomingIndex, setIncomingIndex] = useState(null);
    const [transitioningFromIndex, setTransitioningFromIndex] = useState(null);
    const [enterActive, setEnterActive] = useState(false);
    const [remoteSubtitleText, setRemoteSubtitleText] = useState('<span class="text-slate-400">点击“播放”加载音频</span>');
    const remoteSubtitleTimeoutRef = useRef(null);
    // 使用全局播放器状态进行播放，这里不再维护本地片段状态

    const loadSlides = useCallback(async () => {
        if (!courseId) return;
        try {
            setError('');
            setLoading(true);
            // 使用统一的 API 服务，自动携带 JWT token
            const response = await get(`/api/courses/${courseId}/slides-data`);
            // 兼容后端多种返回格式：
            // 1. 统一 API 格式（数组直接返回）: { code: 200, data: [...] }
            // 2. 统一 API 格式（嵌套对象）: { code: 200, data: { slides_data: [...], updated_at: ... } }
            // 3. 直接格式: { status: 'success', slides_data: [...], updated_at: ... }
            const ok = response.code === 200;
            if (!ok) throw new Error(response.message || '拉取 slides 失败');
            // 处理数据：如果 response.data 是数组，直接使用；否则查找 slides_data 或 data 字段
            let rawSlides = [];
            if (Array.isArray(response.data)) {
                // 格式1: { code: 200, data: [...] }
                rawSlides = response.data;
            } else if (response.data && Array.isArray(response.data.slides_data)) {
                // 格式2: { code: 200, data: { slides_data: [...] } }
                rawSlides = response.data.slides_data;
            } else if (response.data && Array.isArray(response.data.data)) {
                // 格式2变体: { code: 200, data: { data: [...] } }
                rawSlides = response.data.data;
            } else if (Array.isArray(response.slides_data)) {
                // 格式3: { status: 'success', slides_data: [...] }
                rawSlides = response.slides_data;
            }
            const data = response.data || response;
            // 统一字段命名：将 htmlContent/html 等映射为 html_content，补充 page_number
            const slides = rawSlides.map((s, i) => ({
                ...s,
                html_content: s?.html_content || s?.htmlContent || s?.html || s?.content_html || '',
                page_number: Number(s?.page_number || s?.pageNumber || i + 1),
            }));
            setRemoteSlides(slides);
            setUpdatedAt(Number(data.updated_at || data.timestamp || 0));
            setCurrentRemoteIndex((prev) => {
                if (slides.length === 0) return 0;
                return Math.min(prev, slides.length - 1);
            });
            setDisplayedRemoteIndex((prev) => {
                if (slides.length === 0) return 0;
                return Math.min(prev, slides.length - 1);
            });
            // reset transition state
            setIsTransitioning(false);
            setIncomingIndex(null);
            setEnterActive(false);
        } catch (err) {
            setError(err?.message || '拉取 slides 失败');
        } finally {
            setLoading(false);
        }
    }, [courseId]);

    useEffect(() => { loadSlides(); }, [loadSlides]);

    useEffect(() => {
        if (!courseId) return undefined;
        const t = setInterval(async () => {
            try {
                // 使用统一的 API 服务，自动携带 JWT token
                const response = await get(`/api/courses/${courseId}/slides-data`);
                // 兼容后端多种返回格式
                const ok = response.code === 200;
                if (!ok) return;
                // 处理数据：如果 response.data 是数组，直接使用；否则查找 slides_data 或 data 字段
                let rawSlides = [];
                if (Array.isArray(response.data)) {
                    rawSlides = response.data;
                } else if (response.data && Array.isArray(response.data.slides_data)) {
                    rawSlides = response.data.slides_data;
                } else if (response.data && Array.isArray(response.data.data)) {
                    rawSlides = response.data.data;
                } else if (Array.isArray(response.slides_data)) {
                    rawSlides = response.slides_data;
                }
                const data = response.data || response;
                const nextUpdatedAt = Number(data.updated_at || data.timestamp || response.timestamp || 0);
                if (nextUpdatedAt > updatedAt) {
                    const slides = rawSlides.map((s, i) => ({
                        ...s,
                        html_content: s?.html_content || s?.htmlContent || s?.html || s?.content_html || '',
                        page_number: Number(s?.page_number || s?.pageNumber || i + 1),
                    }));
                    setRemoteSlides(slides);
                    setUpdatedAt(nextUpdatedAt);
                    setCurrentRemoteIndex((prev) => {
                        if (slides.length === 0) return 0;
                        return Math.min(prev, slides.length - 1);
                    });
                    setDisplayedRemoteIndex((prev) => {
                        if (slides.length === 0) return 0;
                        return Math.min(prev, slides.length - 1);
                    });
                    setIsTransitioning(false);
                    setIncomingIndex(null);
                    setEnterActive(false);
                    setRemoteSubtitleText('<span class="text-slate-400">点击“播放字幕”开始</span>');
                }
            } catch (_) {
                // ignore
            }
        }, 10000);
        return () => clearInterval(t);
    }, [courseId, updatedAt]);

    if (courseId) {
        const totalRemote = remoteSlides.length;
        const currentRemoteSlide = totalRemote > 0 ? remoteSlides[displayedRemoteIndex] : null;

        const transitionToIndex = (nextIndex, dir) => {
            if (isTransitioning) return; // 避免连击
            setTransitionDirection(dir);
            setTransitioningFromIndex(displayedRemoteIndex);
            setIncomingIndex(nextIndex);
            setIsTransitioning(true);
            // 在下一帧激活动画（从偏移到归位）
            requestAnimationFrame(() => {
                requestAnimationFrame(() => setEnterActive(true));
            });
            // 动画结束后完成切换
            transitionTimerRef.current = setTimeout(() => {
                setDisplayedRemoteIndex(nextIndex);
                setIsTransitioning(false);
                setIncomingIndex(null);
                setTransitioningFromIndex(null);
                setEnterActive(false);
                transitionTimerRef.current = null;
            }, 350);
        };

        const handleRemoteNext = () => {
            if (totalRemote === 0) return;
            const next = (currentRemoteIndex + 1) % totalRemote;
            setCurrentRemoteIndex(next);
            setRemoteSubtitleText('<span class="text-slate-400">点击“播放字幕”开始</span>');
            transitionToIndex(next, 'next');
        };
        const handleRemotePrev = () => {
            if (totalRemote === 0) return;
            const next = (currentRemoteIndex - 1 + totalRemote) % totalRemote;
            setCurrentRemoteIndex(next);
            setRemoteSubtitleText('<span class="text-slate-400">点击“播放字幕”开始</span>');
            transitionToIndex(next, 'prev');
        };

        const handleRemotePlay = async () => {
            if (!courseId) return;
            const totalRemote = remoteSlides.length;
            if (totalRemote === 0) return;
            const currentRemoteSlide = remoteSlides[displayedRemoteIndex];
            const pageNumber = Number(currentRemoteSlide?.page_number) || displayedRemoteIndex + 1;
            // 优先使用当前页面传入的 courseId，其次回退到全局/LocalStorage，避免误用旧课程ID
            const courseIdToken = String(courseId || playerState.currentCourseId || (typeof localStorage !== 'undefined' ? localStorage.getItem('currentCourseId') : '') || '');
            if (!courseIdToken) {
                showError('缺少有效 course_id，请先在列表页触发合成以获取 course_id');
                return;
            }
            setCourseId(courseIdToken);
            try {
                showSuccess('正在加载页面音频数据...');
                // 1) 获取片段信息
                const segments = await fetchSegments(courseIdToken, pageNumber);
                if (!segments || segments.length === 0) {
                    showError('未找到该页面的音频数据');
                    return;
                }
                setRemoteSubtitleText('正在处理音频数据...');
                // 2) 从返回的片段数据中提取音频数据（每个片段都有 audioData 字段，base64编码）
                const splitAudioBlobs = segments.map((segment) => {
                    if (!segment.audioData) {
                        throw new Error(`片段 ${segment.segmentIndex} 缺少音频数据`);
                    }
                    // 将 base64 字符串转换为 Blob
                    try {
                        // base64 解码
                        const binaryString = atob(segment.audioData);
                        const bytes = new Uint8Array(binaryString.length);
                        for (let i = 0; i < binaryString.length; i++) {
                            bytes[i] = binaryString.charCodeAt(i);
                        }
                        // 根据 audioFormat 确定 MIME 类型
                        const mimeType = segment.audioFormat === 'wav' ? 'audio/wav' : 
                                        segment.audioFormat === 'mp3' ? 'audio/mpeg' : 
                                        segment.audioFormat === 'ogg' ? 'audio/ogg' : 
                                        'audio/wav'; // 默认使用 wav
                        return new Blob([bytes], { type: mimeType });
                    } catch (error) {
                        console.error('音频数据解码失败:', error);
                        throw new Error(`片段 ${segment.segmentIndex} 音频数据解码失败: ${error.message}`);
                    }
                });
                
                if (splitAudioBlobs.length === 0) {
                    throw new Error('未找到有效的音频片段');
                }
                // 写入全局播放器状态并启动播放
                playerState.currentCourseId = courseIdToken;
                playerState.currentPageNumber = pageNumber;
                playerState.audioSegments = segments;
                playerState.splitAudioBlobs = splitAudioBlobs;
                playerState.currentSegmentIndex = 0;
                if (splitAudioBlobs.length !== segments.length) {
                    showError(`警告：处理得到 ${splitAudioBlobs.length} 个音频片段，但预期 ${segments.length} 个`);
                }
                showSuccess(`成功加载 ${segments.length} 个音频片段`);
                // 启动播放并更新UI
                startPlayback();
                updatePlaybackStatus();
                updatePlaybackControls();
                //下面这个setRemoteSubtitleText在startPlayback()后立即覆盖了字幕文本，应该去掉
                //setRemoteSubtitleText('<span class="text-slate-400">正在播放讲课...</span>');
            } catch (error) {
                console.error('加载音频数据失败:', error);
                showError('加载音频数据失败: ' + error.message);
                setRemoteSubtitleText('<span class="text-red-500">加载音频数据失败</span>');
            }
        };

        useEffect(() => {
            clearTimeout(remoteSubtitleTimeoutRef.current);
            setRemoteSubtitleText('<span class="text-slate-400">点击“播放”加载音频</span>');
        }, [displayedRemoteIndex]);

        useEffect(() => () => {
            if (transitionTimerRef.current) {
                clearTimeout(transitionTimerRef.current);
                transitionTimerRef.current = null;
            }
        }, []);

        // 监听全局字幕事件，实时显示当前片段文字
        useEffect(() => {
            const onSubtitle = (e) => {
                console.log('[调试]收到字幕事件：',e);
                const text = (e?.detail?.text || '').trim();
                console.log('[调试]提取字幕文本：',text);
                if (text) {
                    console.log('[调试]更新字幕显示：',text);
                    setRemoteSubtitleText(`<span class="subtitle-line">${text}</span>`);
                }else{
                    console.log('[调试]未提取到字幕文本');
                }
            };
            window.addEventListener('ppt-subtitle', onSubtitle);
            console.log('[调试]字幕事件监听器已注册')
            return () => {
                console.log('[调试]移除字幕事件监听器')
                window.removeEventListener('ppt-subtitle', onSubtitle);
            }
            
        }, []);

        useEffect(() => {
            console.log('[调试] remoteSubtitleText 状态变化:', remoteSubtitleText);
        }, [remoteSubtitleText]);
        

        return (
            <main className="flex-1 p-4 lg:p-6 flex flex-col overflow-hidden">
                <div className="w-full max-w-7xl mx-auto flex-1 flex flex-col">
                    {loading && <div className="text-slate-600">加载中…</div>}
                    {error && !loading && <div className="text-red-500 text-sm" role="alert">{error}</div>}
                    {!loading && !error && currentRemoteSlide && (
                        <div className="flex-1 flex flex-col min-h-0">
                            <div className="flex-1 min-h-0 flex items-center justify-center overflow-hidden" style={{ paddingBottom: '0px' }}>
                                <div
                                    className="relative flex-shrink-0 m-auto"
                                    style={{
                                        width: '100%',
                                        maxWidth: 1280,
                                        aspectRatio: '16 / 9',
                                        maxHeight: '100%'
                                    }}
                                >
                                    {/* 预渲染池：当前页与相邻页都常驻，切换时只做位移动画，避免空帧 */}
                                    {(() => {
                                        const around = 2;
                                        const indices = new Set();
                                        for (let d = -around; d <= around; d += 1) {
                                            const idx = displayedRemoteIndex + d;
                                            if (idx >= 0 && idx < totalRemote) indices.add(idx);
                                        }
                                        if (incomingIndex != null) indices.add(incomingIndex);
                                        if (transitioningFromIndex != null) indices.add(transitioningFromIndex);
                                        return Array.from(indices.values()).map((idx) => {
                                            const isActive = idx === displayedRemoteIndex;
                                            const isIncoming = isTransitioning && idx === incomingIndex;
                                            const isLeaving = isTransitioning && idx === transitioningFromIndex;
                                            const baseClass = 'absolute inset-0 transition-all duration-400 ease-out will-change-transform will-change-opacity';
                                            let motionClass = 'translate-x-0 opacity-0 pointer-events-none';
                                            if (!isTransitioning) {
                                                motionClass = isActive ? 'translate-x-0 opacity-100' : 'translate-x-0 opacity-0 pointer-events-none';
                                            } else if (isIncoming) {
                                                // 新页：从边缘外进入，避免覆盖导致的白屏
                                                motionClass = enterActive
                                                    ? 'translate-x-0 opacity-100'
                                                    : (transitionDirection === 'next' ? 'translate-x-full opacity-0' : '-translate-x-full opacity-0');
                                            } else if (isLeaving) {
                                                // 旧页：从中心滑出
                                                motionClass = enterActive
                                                    ? (transitionDirection === 'next' ? '-translate-x-full opacity-0' : 'translate-x-full opacity-0')
                                                    : 'translate-x-0 opacity-100';
                                            }
                                            return (
                                                <iframe
                                                    key={`pool-${idx}`}
                                                    title={`Slide ${idx + 1}`}
                                                    width={IFRAME_WIDTH}
                                                    height={IFRAME_HEIGHT}
                                                    className={`${baseClass} ${motionClass}`}
                                                    style={{ border: '1px solid #e5e7eb', borderRadius: 0, background: 'white' }}
                                                    srcDoc={remoteSlides[idx]?.html_content || ''}
                                                />
                                            );
                                        });
                                    })()}
                                </div>
                            </div>
                            <div className="flex-shrink-0 pt-0 pb-2" style={{ minHeight: '80px' }}>
                                <audio id="mainAudio" className="hidden" />
                                <SubtitleBar text={remoteSubtitleText} />
                                <Navigation
                                    onPrev={handleRemotePrev}
                                    onNext={handleRemoteNext}
                                    onPlay={handleRemotePlay}
                                    totalSlides={totalRemote}
                                    currentIndex={displayedRemoteIndex}
                                />
                            </div>
                        </div>
                    )}
                    {!loading && !error && !currentRemoteSlide && (
                        <div className="text-slate-500 text-sm">无幻灯片</div>
                    )}
                </div>
            </main>
        );
    }

    // 分支B：无 projectId，保留原有本地示例播放逻辑
    const [currentIndex, setCurrentIndex] = useState(0);
    const [subtitleText, setSubtitleText] = useState('<span class="text-slate-400">点击"播放字幕"开始</span>');
    const subtitleTimeoutRef = useRef(null);
    const currentSlide = slidesData[currentIndex];
    const hasHtmlContent = currentSlide?.html_content;//额外增加的部分，针对于没有后端返回时的死的PPT，后面如果有冲突可以去除这个变量

    const handleNext = () => setCurrentIndex((prev) => (prev + 1) % slidesData.length);
    const handlePrev = () => setCurrentIndex((prev) => (prev - 1 + slidesData.length) % slidesData.length);

    const handlePlaySubtitles = () => {
        const subtitleLines = currentSlide.subtitles;
        if (!subtitleLines || subtitleLines.length === 0) {
            setSubtitleText('<span class="subtitle-line">当前页面没有字幕。</span>');
            return;
        }
        let lineIndex = 0;
        const showNextLine = () => {
            if (lineIndex >= subtitleLines.length) {
                subtitleTimeoutRef.current = setTimeout(() => setSubtitleText('<span class="text-slate-400">字幕播放完毕。</span>'), 2000);
                return;
            }
            const line = subtitleLines[lineIndex];
            setSubtitleText(`<span class="subtitle-line">${line}</span>`);
            const lineDuration = line.length * 100 + 1500;
            lineIndex++;
            subtitleTimeoutRef.current = setTimeout(showNextLine, lineDuration);
        };
        showNextLine();
    };

    useEffect(() => {
        clearTimeout(subtitleTimeoutRef.current);
        setSubtitleText('<span class="text-slate-400">点击"播放字幕"开始</span>');
    }, [currentIndex]);
//额外增加的部分，针对于没有后端返回时的死的PPT，后面如果有冲突可以去除这个if部分
    // 如果有 html_content，使用 iframe 渲染
    if (hasHtmlContent) {
        return (
            <main className="flex-1 p-4 lg:p-6 flex flex-col overflow-hidden min-h-0">
                <div className="w-full max-w-7xl mx-auto flex-1 flex flex-col min-h-0">
                    <div className="flex items-center justify-center overflow-hidden min-h-0 flex-1" style={{ minHeight: 0, paddingBottom: '10px' }}>
                        <div 
                            className="relative flex-shrink-0 m-auto" 
                            style={{ 
                                width: '100%',
                                maxWidth: 1280,
                                aspectRatio: '16 / 9',
                                maxHeight: '100%'
                            }}
                        >
                            <iframe
                                key={currentIndex}
                                title={`Slide ${currentIndex + 1}`}
                                width={IFRAME_WIDTH}
                                height={IFRAME_HEIGHT}
                                className="w-full h-full"
                                style={{ border: '1px solid #e5e7eb', borderRadius: 8, background: 'white', display: 'block' }}
                                srcDoc={currentSlide.html_content}
                            />
                        </div>
                    </div>
                    <div className="flex-shrink-0 pt-4 pb-4" style={{ minHeight: '180px' }}>
                        <SubtitleBar text={subtitleText} />
                        <Navigation onPrev={handlePrev} onNext={handleNext} onPlay={handlePlaySubtitles} totalSlides={slidesData.length} currentIndex={currentIndex} />
                    </div>
                </div>
            </main>
        );
    }

    // 否则使用 SlideCard 组件
    return (
        <main className="flex-1 p-8 lg:p-12 flex flex-col justify-center items-center overflow-hidden">
            <div className="w-full max-w-7xl">
                <SlideCard slide={currentSlide} />
                <SubtitleBar text={subtitleText} />
                <Navigation onPrev={handlePrev} onNext={handleNext} onPlay={handlePlaySubtitles} totalSlides={slidesData.length} currentIndex={currentIndex} />
            </div>
        </main>
    );
};

export default SlideViewer;
