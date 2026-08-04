import { onSegmentEnded } from './pptController';

export function initializeAudio() {
  const mainAudio = document.getElementById('mainAudio');
  const volumeSlider = document.getElementById('volumeSlider');
  if (mainAudio) {
    if (typeof mainAudio.volume === 'number') mainAudio.volume = 0.8;
    // Controller will set onended/onerror when playing each segment
  }
  if (volumeSlider && mainAudio) {
    volumeSlider.addEventListener('input', function () {
      mainAudio.volume = this.value / 100;
    });
  }
}



