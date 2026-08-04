import { state, resetOnStartPlayback, resetOnStopPlayback } from './pptState';
import { sharedViewer } from '@/features/vrmViewer/viewerContext.js';
import { initializeSpeechRecognition, startVoiceDetection } from './pptVoiceControl';
import { showError } from './pptUi';
import { updateSegmentFlags } from './pptPlayback';

// Internal guard for preventing double onSegmentEnded handling
let onSegmentEndedProcessing = false;

function getMainAudio() {
  return document.getElementById('mainAudio');
}

function getSegmentContainer() {
  return document.getElementById('segmentItems');
}

export function startPlayback() {
  if (state.audioSegments.length === 0) {
    showError('请先加载音频数据');
    return;
  }
  resetOnStartPlayback();
  onSegmentEndedProcessing = false;
  playCurrentSegment();
  // 确保连续识别模块已初始化并启动（即使初始化早于此处，也可重复调用）
  try { initializeSpeechRecognition(); } catch {}
  try { startVoiceDetectionSafe(); } catch {}
  try { startVoiceDetection(); } catch {}
  updatePlaybackControls();
  updatePlaybackStatus();
}

export function pausePlayback() {
  state.isPaused = true;
  state.isPlaying = false;
  const mainAudio = getMainAudio();
  if (mainAudio) mainAudio.pause();
  // 同步停止通过 WebAudio 播放的讲课
  try { sharedViewer?.model?.stopSpeaking(); } catch {}
  stopVoiceDetectionSafe();
  updatePlaybackControls();
  updatePlaybackStatus();
}

export function stopPlayback() {
  resetOnStopPlayback();
  const mainAudio = getMainAudio();
  if (mainAudio) {
    mainAudio.pause();
    mainAudio.currentTime = 0;
    mainAudio.onended = null;
    mainAudio.onerror = null;
  }
  try { sharedViewer?.model?.stopLipSyncFromMediaElement(); } catch {}
  // 确保也停止 WebAudio 的播放源
  try { sharedViewer?.model?.stopSpeaking(); } catch {}
  stopVoiceDetectionSafe();
  updatePlaybackControls();
  updatePlaybackStatus();
  updateSegmentDisplay();
}

export function playSegment(index) {
  if (
    index >= 0 &&
    index < state.audioSegments.length &&
    index < state.splitAudioBlobs.length
  ) {
    state.currentSegmentIndex = index;
    if (state.isPlaying) playCurrentSegment();
    updateSegmentDisplay();
    updatePlaybackStatus();
  } else {
    console.error('片段索引无效:', index, '/', state.audioSegments.length, '/', state.splitAudioBlobs.length);
    showError('无法播放指定片段，索引无效');
  }
}

export async function playCurrentSegment() {
  const currentIndex = state.currentSegmentIndex;
  //console.log(`🎯 [playCurrentSegment] 尝试播放片段 ${currentIndex + 1}/${state.audioSegments.length}`);
  console.log("currentIndex:",currentIndex);

  if (currentIndex >= state.audioSegments.length) {
    stopPlayback();
    return;
  }

  if (state.splitAudioBlobs.length === 0) {
    console.error('❌ [playCurrentSegment] 音频片段未加载');
    showError('音频片段未加载，请先加载页面音频');
    return;
  }

  if (currentIndex >= state.splitAudioBlobs.length) {
    console.error(`❌ [playCurrentSegment] 片段索引超出范围: ${currentIndex + 1} > ${state.splitAudioBlobs.length}`);
    showError('音频片段索引错误');
    return;
  }

  const segment = state.audioSegments[currentIndex];
  const audioBlob = state.splitAudioBlobs[currentIndex];
  console.log("segment:",segment);
  console.log("audioBlob",audioBlob);
  console.log('[调试]当前片段数据：',segment);
  console.log('[调试]textContent值：',segment?.textContent);

  if (!audioBlob || audioBlob.size === 0) {
    console.warn(`⚠️ [playCurrentSegment] 片段 ${currentIndex + 1} 音频为空，跳过`);
    onSegmentEnded();
    return;
  }

  try {
    // 广播当前字幕到页面（供 SlideViewer 捕获并显示）
    try {
      const subtitleText = String(segment?.textContent || '').trim();
      if (subtitleText && typeof window !== 'undefined' && typeof window.dispatchEvent === 'function') {
        window.dispatchEvent(new CustomEvent('ppt-subtitle', { detail: { text: subtitleText } }));
      }
    } catch {}

    // 完全用 WebAudio 播放，避免 DOM <audio> 与 WebAudio 双路引起回声/无声
    const arrayBuf = await audioBlob.arrayBuffer();
    const screenplay = {
      expression: 'neutral',
      talk: { message: segment?.textContent || '' },
    };
    await sharedViewer?.model?.speak(arrayBuf, screenplay);
    console.log(`🎵 [playCurrentSegment] 成功开始并结束播放片段 ${currentIndex + 1}`);
    updateSegmentDisplay();
    updatePlaybackStatus();
    onSegmentEnded();
  } catch (error) {
    console.error(`❌ [playCurrentSegment] 播放片段 ${currentIndex + 1} 失败:`, error);
    if (error.name !== 'AbortError') {
      showError(`播放音频片段 ${currentIndex + 1} 失败: ${error.message}`);
      setTimeout(() => {
        if (state.isPlaying && !state.isInterrupted) onSegmentEnded();
      }, 1000);
    }
  }
}

export function onSegmentEnded() {
  console.log(`🔊 [onSegmentEnded] 音频片段 ${state.currentSegmentIndex + 1} 播放结束`);
  if (onSegmentEndedProcessing) return;
  onSegmentEndedProcessing = true;

  if (!state.isPlaying || state.isInterrupted) {
    onSegmentEndedProcessing = false;
    return;
  }

  const completedSegment = state.audioSegments[state.currentSegmentIndex];
  console.log(
    `✅ 完成播放片段 ${state.currentSegmentIndex + 1}: "${completedSegment?.textContent?.substring(0, 30) || '无文本'}..."`
  );

  const prev = state.currentSegmentIndex;
  state.currentSegmentIndex++;
  console.log(`📍 片段索引更新: ${prev + 1} -> ${state.currentSegmentIndex + 1}`);

  if (state.currentSegmentIndex >= state.audioSegments.length) {
    stopPlayback();
    onSegmentEndedProcessing = false;
    return;
  }

  if (state.currentSegmentIndex >= state.splitAudioBlobs.length) {
    console.error(
      `❌ 下一个片段索引超出音频Blob范围: ${state.currentSegmentIndex + 1} > ${state.splitAudioBlobs.length}`
    );
    stopPlayback();
    onSegmentEndedProcessing = false;
    return;
  }

  const nextBlob = state.splitAudioBlobs[state.currentSegmentIndex];
  if (!nextBlob || nextBlob.size === 0) {
    console.warn(`⚠️ [onSegmentEnded] 片段 ${state.currentSegmentIndex + 1} 的音频为空，跳过到下一片段`);
    onSegmentEndedProcessing = false;
    setTimeout(() => onSegmentEnded(), 100);
    return;
  }

  setTimeout(() => {
    if (state.isPlaying && !state.isInterrupted) {
      console.log(`🎵 [onSegmentEnded] 开始播放片段 ${state.currentSegmentIndex + 1}`);
      playCurrentSegment();
    }
    onSegmentEndedProcessing = false;
  }, 200);
}

export async function resumePlayback() {
  if (state.isInterrupted && state.interruptedSegmentIndex >= 0) {
    // 将当前片段设置回被打断的位置（目前仅支持从片段起点恢复）
    const idx = state.interruptedSegmentIndex;
    state.currentSegmentIndex = idx;
    // 清理中断状态并恢复播放标志
    state.isInterrupted = false;
    state.isPaused = false;
    state.isPlaying = true;
    state.interruptedSegmentIndex = -1;
    state.interruptedPosition = 0;

    // 确保 DOM 音频链路与口型绑定解除，统一走 WebAudio 播放
    try { sharedViewer?.model?.stopLipSyncFromMediaElement(); } catch {}
    try { sharedViewer?.model?.stopSpeaking(); } catch {}

    // 使用 WebAudio 继续播放当前片段
    try {
      await playCurrentSegment();
    } catch (e) {
      console.error('恢复播放失败:', e);
    }

    updatePlaybackStatus();
    updatePlaybackControls();
    const transcriptDisplay = document.getElementById('transcriptDisplay');
    if (transcriptDisplay) {
      transcriptDisplay.textContent = '等待语音输入...';
      transcriptDisplay.classList.remove('has-content');
    }

    // 恢复连续语音检测
    try { startVoiceDetectionSafe(); } catch {}
  }
}

export function updateSegmentDisplay() {
  const container = getSegmentContainer();
  updateSegmentFlags(container, state.currentSegmentIndex);
}

export function updatePlaybackControls() {
  const playBtn = document.getElementById('playBtn');
  const pauseBtn = document.getElementById('pauseBtn');
  const stopBtn = document.getElementById('stopBtn');
  const testAudioBtn = document.getElementById('testAudioBtn');
  const validateBtn = document.getElementById('validateBtn');
  if (playBtn) playBtn.disabled = state.isPlaying || state.audioSegments.length === 0;
  if (pauseBtn) pauseBtn.disabled = !state.isPlaying;
  if (stopBtn) stopBtn.disabled = !state.isPlaying && !state.isPaused;
  if (testAudioBtn) testAudioBtn.disabled = state.splitAudioBlobs.length === 0;
  if (validateBtn) validateBtn.disabled = state.splitAudioBlobs.length === 0;
}

export function updatePlaybackStatus() {
  const playbackStatus = document.getElementById('playbackStatus');
  const currentSegment = document.getElementById('currentSegment');
  const totalSegments = document.getElementById('totalSegments');
  const playbackProgress = document.getElementById('playbackProgress');
  const progressFill = document.getElementById('progressFill');
  const audioSegmentStatus = document.getElementById('audioSegmentStatus');

  let status = '未开始';
  if (state.isInterrupted) status = '已打断';
  else if (state.isPlaying) status = '播放中';
  else if (state.isPaused) status = '已暂停';

  if (playbackStatus) playbackStatus.textContent = status;
  if (currentSegment) currentSegment.textContent = state.audioSegments.length > 0 ? `${state.currentSegmentIndex + 1}` : '-';
  if (totalSegments) totalSegments.textContent = String(state.audioSegments.length);

  const progress = state.audioSegments.length > 0 ? Math.round((state.currentSegmentIndex / state.audioSegments.length) * 100) : 0;
  if (playbackProgress) playbackProgress.textContent = `${progress}%`;
  if (progressFill) progressFill.style.width = `${progress}%`;

  if (audioSegmentStatus) {
    if (state.splitAudioBlobs.length === 0) audioSegmentStatus.textContent = '未加载';
    else if (state.splitAudioBlobs.length === state.audioSegments.length)
      audioSegmentStatus.textContent = `已拆分 ${state.splitAudioBlobs.length} 个片段`;
    else audioSegmentStatus.textContent = `拆分异常 (${state.splitAudioBlobs.length}/${state.audioSegments.length})`;
  }

  updateInteractionStatus();
}

export function updateInteractionStatus() {
  const interactionStatus = document.getElementById('interactionStatus');
  const interactionHint = document.getElementById('interactionHint');
  if (!interactionStatus || !interactionHint) return;
  if (state.isInterrupted) {
    interactionStatus.textContent = '🎤 语音打断中';
    interactionStatus.style.color = '#ff6b6b';
    interactionHint.textContent = '正在处理您的问题，请稍候...';
  } else if (state.isPlaying) {
    interactionStatus.textContent = '🎧 正在播放讲课 (可用唤醒词打断)';
    interactionStatus.style.color = '#28a745';
    if (state.enableWakeWordDetection) {
      interactionHint.textContent = '说"小思老师"、"老师"或"小思"即可打断讲课并提问';
    } else {
      interactionHint.textContent = '直接开口说话即可立即打断讲课并提问';
    }
  } else if (state.isPaused) {
    interactionStatus.textContent = '⏸️ 播放已暂停';
    interactionStatus.style.color = '#ffc107';
    interactionHint.textContent = '点击"开始播放"继续播放讲课';
  } else if (state.audioSegments.length > 0) {
    interactionStatus.textContent = '⏹️ 播放已停止';
    interactionStatus.style.color = '#6c757d';
    interactionHint.textContent = '点击"开始播放"开始播放讲课';
  } else {
    interactionStatus.textContent = '📋 等待加载音频';
    interactionStatus.style.color = '#6c757d';
    interactionHint.textContent = '请先输入课程ID和页码，然后加载音频数据';
  }
}

// Safe wrappers for voice detection start/stop (provided by voice module)
let startVoiceDetectionSafe = () => {};
let stopVoiceDetectionSafe = () => {};

export function bindVoiceControlHooks({ startVoiceDetection, stopVoiceDetection }) {
  startVoiceDetectionSafe = startVoiceDetection || (() => {});
  stopVoiceDetectionSafe = stopVoiceDetection || (() => {});
}



