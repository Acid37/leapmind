// Orchestrator for PPT Interactive Player
// This file only wires modular pieces together. All heavy logic lives in modules below 500 lines.

import { requestMicrophonePermission } from './pptPermissions';
import { initializeAudio } from './pptAudioInit';
import { setupHotkeys } from './pptHotkeys';
import { setupEventListeners } from './pptDomWiring';
import { bindLoaderHooks, loadPageAudio } from './pptLoader';
import { state } from './pptState';
import {
  startBulkSynthesis,
  copySessionId,
  loadSampleData,
  validateJsonFormat,
  clearJsonInput,
} from './pptBulkSynthesis';
import {
  startPlayback,
  pausePlayback,
  stopPlayback,
  resumePlayback,
  playSegment,
  updatePlaybackControls,
  updatePlaybackStatus,
  bindVoiceControlHooks,
} from './pptController';
import { initializeSpeechRecognition, startVoiceDetection, stopVoiceDetection, restartSpeechRecognition } from './pptVoiceControl';
import { getOrCreateSessionId } from './pptSession';

export function initPptInteractivePlayer() {
  try { console.log('[ppt] initPptInteractivePlayer()'); } catch {}
  // Audio & speech
  initializeAudio();
  try { console.log('[ppt] calling initializeSpeechRecognition'); } catch {}
  initializeSpeechRecognition();
  bindVoiceControlHooks({ startVoiceDetection, stopVoiceDetection });

  // 保障首次用户交互后可正常启动/恢复连续识别（规避浏览器策略）
  const ensureVoiceOnce = () => {
    try { console.log('[ppt] pointerdown -> ensureVoiceOnce'); } catch {}
    try {
      if (state.isPlaying && !state.isInterrupted) {
        if (!document.hidden) {
          // 若未在识别中，尝试启动/重启
          try { startVoiceDetection(); } catch {}
          try { restartSpeechRecognition(); } catch {}
        }
      }
    } catch {}
    window.removeEventListener('pointerdown', ensureVoiceOnce, true);
  };
  window.addEventListener('pointerdown', ensureVoiceOnce, true);

  // UI & DOM events
  setupHotkeys();
  setupEventListeners();

  // Loader hooks to control playback from loader module
  bindLoaderHooks({
    playSegment,
    updatePlaybackStatus,
    enablePlaybackControls: updatePlaybackControls,
  });

  // Permissions
  requestMicrophonePermission();
  // Ensure sessionId exists for QA endpoints
  try { getOrCreateSessionId(); } catch {}

  // Expose minimal globals for legacy triggers/buttons
  Object.assign(window, {
    // Data loading
    loadPageAudio,
    // Playback
    startPlayback,
    pausePlayback,
    stopPlayback,
    resumePlayback,
    playSegment,
    // Bulk synthesis utilities
    startBulkSynthesis,
    copySessionId,
    loadSampleData,
    validateJsonFormat,
    clearJsonInput,
  });
}

// Auto-init when script is loaded after DOM ready
if (document.readyState !== 'loading') {
  try { initPptInteractivePlayer(); } catch {}
} else {
  document.addEventListener('DOMContentLoaded', () => {
    try { initPptInteractivePlayer(); } catch {}
  });
}



