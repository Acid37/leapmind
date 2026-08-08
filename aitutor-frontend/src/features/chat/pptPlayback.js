// Playback helpers and DOM wiring for audio segments

export function wirePlaybackDom() {
  return {
    mainAudio: document.getElementById('mainAudio'),
    volumeSlider: document.getElementById('volumeSlider'),
    segmentItemsEl: document.getElementById('segmentItems'),
    playbackStatus: document.getElementById('playbackStatus'),
    currentSegmentEl: document.getElementById('currentSegment'),
    totalSegmentsEl: document.getElementById('totalSegments'),
    playbackProgress: document.getElementById('playbackProgress'),
    progressFill: document.getElementById('progressFill'),
    audioSegmentStatus: document.getElementById('audioSegmentStatus'),
  };
}

export function renderSegments(container, segments, blobs, onClickIndex) {
  if (!container) return;
  container.innerHTML = '';
  segments.forEach((segment, index) => {
    const div = document.createElement('div');
    div.className = 'segment-item';
    div.onclick = () => onClickIndex(index);
    const size = blobs[index] ? `${(blobs[index].size / 1024).toFixed(1)}KB` : '未加载';
    div.innerHTML = `
      <div class="segment-info">
        <div class="segment-title">片段 ${index + 1} (${size})</div>
        <div class="segment-text">${segment.textContent || '无文本内容'}</div>
      </div>
      <div class="segment-duration">${formatDuration(segment.duration || 0)}</div>
    `;
    container.appendChild(div);
  });
}

export function updateSegmentFlags(container, currentIndex) {
  if (!container) return;
  const items = container.querySelectorAll('.segment-item');
  items.forEach((item, i) => {
    item.classList.remove('current', 'played');
    if (i === currentIndex) item.classList.add('current');
    else if (i < currentIndex) item.classList.add('played');
  });
}

export function formatDuration(milliseconds) {
  const seconds = Math.floor(milliseconds / 1000);
  const minutes = Math.floor(seconds / 60);
  const remainingSeconds = seconds % 60;
  return `${minutes}:${remainingSeconds.toString().padStart(2, '0')}`;
}


