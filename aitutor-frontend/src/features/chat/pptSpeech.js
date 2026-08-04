// Browser speech recognition utilities

export function createSpeechRecognition(lang = 'zh-CN') {
  const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
  if (!SpeechRecognition) return null;
  const sr = new SpeechRecognition();
  sr.continuous = true;
  sr.interimResults = true;
  sr.lang = lang;
  sr.maxAlternatives = 1;
  return sr;
}

export function createOneShotRecognition(lang = 'zh-CN') {
  const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
  if (!SpeechRecognition) return null;
  const sr = new SpeechRecognition();
  sr.continuous = false;
  sr.interimResults = true;
  sr.lang = lang;
  sr.maxAlternatives = 1;
  return sr;
}


