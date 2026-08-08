import { state } from './pptState';
import { sharedViewer } from '@/features/vrmViewer/viewerContext.js';
import { updateRecognitionStatus, showAIResponse, hideAIResponse, showError } from './pptUi';
import { askQuestion, synthesizeSpeech } from './pptApi';
import { resumePlayback } from './pptController';
import { VoiceInterruptionHandler } from './voiceInterruptionHandler';

let handler = null;

export function initializeSpeechRecognition() {
  if (handler) return true;
  handler = new VoiceInterruptionHandler();
  // 接入现有 UI 与状态
  handler.setCallbacks({
    onStatusUpdate: (status, isActive) => updateRecognitionStatus(status, isActive),
    onError: (msg) => showError(msg),
    onInterruption: async (question) => {
      // 停止当前 WebAudio 播放，记录上下文
      try { sharedViewer?.model?.stopSpeaking(); } catch {}
      const mainAudio = document.getElementById('mainAudio');
      const context = {
        courseId: state.currentCourseId,
        interruptedIndex: state.currentSegmentIndex,
        interruptedPosition: mainAudio && !mainAudio.paused ? mainAudio.currentTime : 0,
      };
      state.isInterrupted = true;
      state.interruptedSegmentIndex = state.currentSegmentIndex;
      state.interruptedPosition = context.interruptedPosition;
      return context;
    },
    onResponse: (question, answer) => {
      showAIResponse(answer, false);
    },
    onResume: async (context, resumeMode) => {
      try { await resumePlayback(); } catch {}
    },
  });
  try { console.log('[vih] handler initialized'); } catch {}
  return true;
}

export function startVoiceDetection() {
  if (!state.enableVoiceDetectionFlag) return;
  if (!state.isPlaying || state.isInterrupted) return;
  if (!handler && !initializeSpeechRecognition()) return;
  state.isVoiceDetectionActive = true;
  handler.startVoiceDetection();
}

export function stopVoiceDetection() {
  state.isVoiceDetectionActive = false;
  if (handler) handler.stopVoiceDetection();
  const transcriptDisplay = document.getElementById('transcriptDisplay');
  if (transcriptDisplay) {
    transcriptDisplay.textContent = '等待语音输入...';
    transcriptDisplay.classList.remove('has-content');
    transcriptDisplay.style.borderColor = '#dee2e6';
  }
  updateRecognitionStatus('语音检测已停止', false);
}

export function restartSpeechRecognition() {
  if (!state.isVoiceDetectionActive) return;
  if (!state.isPlaying || state.isInterrupted) return;
  if (!handler) return;
  handler.restartRecognition();
}

function resetRecognitionTimeout() {
  clearRecognitionTimeout();
  recognitionTimeout = setTimeout(() => {
    if (isRecognizing && !interimTranscript && !finalTranscript) {
      try { speechRecognition.stop(); } catch {}
    }
  }, RECOGNITION_TIMEOUT);
}

function clearRecognitionTimeout() {
  if (recognitionTimeout) {
    clearTimeout(recognitionTimeout);
    recognitionTimeout = null;
  }
}

// 保留 handleVoiceInterruption：供输入框/其他模块直接复用统一流程

export async function handleVoiceInterruption(question) {
  const mainAudio = document.getElementById('mainAudio');
  // 立即停止当前通过 WebAudio 播放的讲课语音，避免继续播导致“未暂停”的体验
  try { sharedViewer?.model?.stopSpeaking(); } catch {}
  if (mainAudio && !mainAudio.paused) {
    state.isInterrupted = true;
    state.interruptedSegmentIndex = state.currentSegmentIndex;
    state.interruptedPosition = mainAudio.currentTime;
    mainAudio.pause();
  } else {
    state.isInterrupted = true;
    state.interruptedSegmentIndex = state.currentSegmentIndex;
    state.interruptedPosition = 0;
  }
  stopVoiceDetection();
  showAIResponse('正在处理您的问题...', true);
  try {
    const result = await askQuestion(state.currentSessionId, question);
    showAIResponse(result.answer, false);
    const ttsBlob = await synthesizeSpeech(state.currentSessionId, result.answer);
    if (ttsBlob) {
      const audioUrl = URL.createObjectURL(ttsBlob);
      const responseAudio = document.getElementById('responseAudio');
      responseAudio.src = audioUrl;
      responseAudio.play();
      responseAudio.onended = function () { resumePlayback(); };
    } else {
      setTimeout(() => resumePlayback(), 3000);
    }
  } catch (error) {
    showError('处理问题失败: ' + error.message);
    setTimeout(() => resumePlayback(), 2000);
  }
}



