import { startPlayback, pausePlayback, stopPlayback, playSegment } from './pptController';
import { startBulkSynthesis, copySessionId, loadSampleData, validateJsonFormat, clearJsonInput } from './pptBulkSynthesis';

export function setupEventListeners() {
  const playBtn = document.getElementById('playBtn');
  const pauseBtn = document.getElementById('pauseBtn');
  const stopBtn = document.getElementById('stopBtn');
  const testAudioBtn = document.getElementById('testAudioBtn');
  const validateBtn = document.getElementById('validateBtn');
  const copyBtn = document.getElementById('copySessionIdBtn');
  const loadSampleBtn = document.getElementById('loadSampleBtn');
  const validateJsonBtn = document.getElementById('validateJsonBtn');
  const clearJsonBtn = document.getElementById('clearJsonBtn');

  if (playBtn) playBtn.addEventListener('click', startPlayback);
  if (pauseBtn) pauseBtn.addEventListener('click', pausePlayback);
  if (stopBtn) stopBtn.addEventListener('click', stopPlayback);
  if (testAudioBtn) testAudioBtn.addEventListener('click', startBulkSynthesis);
  if (validateBtn) validateBtn.addEventListener('click', () => {});
  if (copyBtn) copyBtn.addEventListener('click', copySessionId);
  if (loadSampleBtn) loadSampleBtn.addEventListener('click', loadSampleData);
  if (validateJsonBtn) validateJsonBtn.addEventListener('click', validateJsonFormat);
  if (clearJsonBtn) clearJsonBtn.addEventListener('click', clearJsonInput);

  // segmentItems 点击事件在渲染时指定，这里无需额外绑定
}



