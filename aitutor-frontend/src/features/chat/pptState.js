// Centralized player state with minimal getters/setters to reduce globals in the main file

export const state = {
  currentCourseId: '',
  currentPageNumber: 1,
  audioSegments: [],
  splitAudioBlobs: [],
  currentSegmentIndex: 0,
  isPlaying: false,
  isPaused: false,
  isInterrupted: false,
  interruptedSegmentIndex: -1,
  interruptedPosition: 0,
  isVoiceDetectionActive: false,
  isRecognizing: false,
  networkErrorCount: 0,
  enableVoiceDetectionFlag: true,
  resumeMode: 'fromBeginning',
  enableWakeWordDetection: true,
};

export function resetOnStartPlayback() {
  state.isPlaying = true;
  state.isPaused = false;
  state.isInterrupted = false;
  state.interruptedSegmentIndex = -1;
}

export function resetOnStopPlayback() {
  state.isPlaying = false;
  state.isPaused = false;
  state.isInterrupted = false;
  state.currentSegmentIndex = 0;
  state.interruptedSegmentIndex = -1;
}


