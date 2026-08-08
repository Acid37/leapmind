// Lightweight pub/sub for player events to decouple UI and logic

const topics = new Map();

export function on(event, handler) {
  if (!topics.has(event)) topics.set(event, new Set());
  topics.get(event).add(handler);
  return () => off(event, handler);
}

export function off(event, handler) {
  const set = topics.get(event);
  if (set) set.delete(handler);
}

export function emit(event, payload) {
  const set = topics.get(event);
  if (!set) return;
  for (const handler of Array.from(set)) {
    try { handler(payload); } catch (e) { /* noop */ }
  }
}

// Suggested events
export const PlayerEvents = {
  LoadedSegments: 'LoadedSegments',
  StartPlayback: 'StartPlayback',
  PausePlayback: 'PausePlayback',
  StopPlayback: 'StopPlayback',
  SegmentChanged: 'SegmentChanged',
  Interrupted: 'Interrupted',
  Resumed: 'Resumed',
  AIAnswerReady: 'AIAnswerReady',
  AIAnswerAudioReady: 'AIAnswerAudioReady',
  Error: 'Error',
};


