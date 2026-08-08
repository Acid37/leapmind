import { startPlayback, pausePlayback } from './pptController';

export function setupHotkeys() {
  document.addEventListener('keydown', function (e) {
    if (e.code === 'Space' && e.target.tagName !== 'INPUT') {
      e.preventDefault();
      const isPlaying = document.getElementById('playbackStatus')?.textContent === '播放中';
      if (isPlaying) pausePlayback(); else startPlayback();
    }
  });
}



