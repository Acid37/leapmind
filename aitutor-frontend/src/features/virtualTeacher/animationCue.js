const EXPRESSION_MAP = {
  smiling: 'happy',
  curious: 'relaxed',
  serious: 'neutral',
  happy: 'happy',
  angry: 'angry',
  sad: 'sad',
  relaxed: 'relaxed',
  neutral: 'neutral',
};

const GESTURE_MAP = {
  emphasizing: 'bigNod',
  pointing_board: 'lookUp',
  explaining: 'smallNod',
  encouraging: 'smallNod',
  thinking: 'tiltHead',
  greeting: 'lookUp',
  bigNod: 'bigNod',
  smallNod: 'smallNod',
  tiltHead: 'tiltHead',
  bigShake: 'bigShake',
  smallShake: 'smallShake',
  lookUp: 'lookUp',
};

const DIRECT_VISEMES = new Set(['aa', 'ee', 'ih', 'oh', 'ou']);

export function normalizeExpression(value) {
  const type = typeof value === 'object' ? value?.type : value;
  return EXPRESSION_MAP[String(type || '').toLowerCase()] ?? 'neutral';
}

export function gestureToHeadMotion(value) {
  const type = typeof value === 'object' ? value?.type : value;
  return GESTURE_MAP[type] ?? null;
}

export function phonemeToViseme(value) {
  const phoneme = String(value || '').trim().toLowerCase();
  if (DIRECT_VISEMES.has(phoneme)) return phoneme;
  if (/^(a|ai|an|ang|ao|ia|ian|iang|ua)$/.test(phoneme)) return 'aa';
  if (/^(i|yi|e|ei|ie|ye)$/.test(phoneme)) return 'ih';
  if (/^(u|wu|ü|yu|ui|uei)$/.test(phoneme)) return 'ou';
  if (/^(o|ou|ong|iong|uo)$/.test(phoneme)) return 'oh';
  return null;
}

export function normalizePhonemeTimeline(items) {
  if (!Array.isArray(items)) return [];

  return items
    .map((item) => {
      const startMs = Number(item.startMs ?? item.start ?? 0);
      const endMs = Number(item.endMs ?? item.end ?? startMs);
      return {
        viseme: phonemeToViseme(item.viseme ?? item.value ?? item.phoneme),
        startMs,
        endMs,
      };
    })
    .filter((item) => item.viseme && Number.isFinite(item.startMs) && Number.isFinite(item.endMs) && item.endMs > item.startMs)
    .sort((a, b) => a.startMs - b.startMs);
}

export function normalizeAnimationPayload(payload = {}) {
  const animation = payload.animation ?? payload;
  return {
    expression: normalizeExpression(animation.expression ?? animation.emotion),
    gestures: Array.isArray(animation.gestures) ? animation.gestures : [],
    phonemes: normalizePhonemeTimeline(animation.phonemes ?? animation.phonemeTimeline),
  };
}
