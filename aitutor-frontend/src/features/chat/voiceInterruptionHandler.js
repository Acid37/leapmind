// VoiceInterruptionHandler: 结合现有 API/状态/UI 的封装版本
import { askQuestion, synthesizeSpeech } from './pptApi';

export class VoiceInterruptionHandler {
  constructor(options = {}) {
    this.options = {
      minConfidence: options.minConfidence ?? 0.6,
      recognitionTimeout: options.recognitionTimeout ?? 800,
      maxNetworkErrors: options.maxNetworkErrors ?? 3,
      wakeWords: options.wakeWords ?? ['小思老师', '老师', '小思'],
      enableWakeWordDetection: options.enableWakeWordDetection !== false,
      resumeMode: options.resumeMode ?? 'fromBeginning',
      ...options,
    };

    this.isActive = false;
    this.isRecognizing = false;
    this.isInterrupted = false;
    this.speechRecognition = null;
    this.networkErrorCount = 0;
    this.recognitionTimeout = null;
    this.finalTranscript = '';
    this.interimTranscript = '';
    this.interruptedContext = null;
    // 标记是否已在中间结果阶段触发过“早期打断”
    this.earlyInterrupted = false;

    this.callbacks = {
      onInterruption: null, // async (question) => context
      onResponse: null, // (question, answer) => void
      onResume: null, // async (context, resumeMode) => void
      onStatusUpdate: null, // (status, isActive) => void
      onError: null, // (message) => void
    };
  }

  setCallbacks(callbacks) { this.callbacks = { ...this.callbacks, ...callbacks }; }

  initializeSpeechRecognition() {
    const SR = window.SpeechRecognition || window.webkitSpeechRecognition;
    if (!SR) {
      this.handleError('您的浏览器不支持语音识别功能，请使用Chrome、Edge或Safari浏览器');
      return false;
    }
    if (this.speechRecognition) return true;
    this.speechRecognition = new SR();
    this.speechRecognition.continuous = true;
    this.speechRecognition.interimResults = true;
    this.speechRecognition.lang = 'zh-CN';
    this.speechRecognition.maxAlternatives = 1;

    this.speechRecognition.onstart = () => this.handleRecognitionStart();
    this.speechRecognition.onresult = (e) => this.handleRecognitionResult(e);
    this.speechRecognition.onerror = (e) => this.handleRecognitionError(e);
    this.speechRecognition.onend = () => this.handleRecognitionEnd();
    try { console.log('[vih] 初始化 SpeechRecognition 成功'); } catch {}
    return true;
  }

  startVoiceDetection() {
    if (!this.initializeSpeechRecognition()) return false;
    if (this.isRecognizing || this.isActive) {
      try { console.log('[vih] 已在监听'); } catch {}
      return true;
    }
    try {
      this.isActive = true;
      this.networkErrorCount = 0;
      this.finalTranscript = '';
      this.interimTranscript = '';
      this.earlyInterrupted = false;
      this.speechRecognition.start();
      this.updateStatus('等待语音输入...', true);
      try { console.log('[vih] 启动语音检测'); } catch {}
      return true;
    } catch (error) {
      this.isActive = false;
      this.handleError('启动语音检测失败: ' + error.message);
      return false;
    }
  }

  stopVoiceDetection() {
    try { console.log('[vih] 停止语音检测'); } catch {}
    if (this.speechRecognition && this.isRecognizing) {
      try { this.speechRecognition.stop(); } catch {}
    }
    this.isActive = false;
    this.isRecognizing = false;
    this.finalTranscript = '';
    this.interimTranscript = '';
    this.earlyInterrupted = false;
    this.clearRecognitionTimeout();
    this.updateStatus('语音检测已停止', false);
  }

  restartRecognition() {
    if (!this.isActive || !this.speechRecognition || this.isRecognizing) return;
    try { this.speechRecognition.start(); try { console.log('[vih] 重启语音识别'); } catch {} } catch (e) {
      setTimeout(() => { if (this.isActive) this.restartRecognition(); }, 2000);
    }
  }

  handleRecognitionStart() {
    this.isRecognizing = true;
    this.updateStatus('正在监听语音...', true);
    try { console.log('[vih] onstart'); } catch {}
  }

  async handleRecognitionResult(event) {
    this.finalTranscript = '';
    this.interimTranscript = '';
    try { console.log('[vih] onresult'); } catch {}
    for (let i = event.resultIndex; i < event.results.length; i++) {
      const transcript = event.results[i][0].transcript || '';
      const confidence = event.results[i][0].confidence ?? 0;
      if (!transcript.trim()) continue;
      if (event.results[i].isFinal) {
        if ((confidence >= this.options.minConfidence) || this.containsWakeWord(transcript)) {
          this.finalTranscript += transcript;
          try { console.log('[vih] final:', this.finalTranscript); } catch {}
          this.processVoiceInput(this.finalTranscript.trim());
        }
      } else {
        this.interimTranscript += transcript;
        this.updateStatus(`正在识别: ${this.interimTranscript}`, true);
        // 中间结果阶段：如检测到唤醒词，则立即暂停讲解（调用 onInterruption 捕获上下文），但不停止识别
        if (!this.earlyInterrupted && this.containsWakeWord(this.interimTranscript)) {
          this.earlyInterrupted = true;
          try {
            if (this.callbacks.onInterruption) {
              const placeholderQuestion = this.extractQuestion(this.interimTranscript) || this.interimTranscript;
              this.interruptedContext = await this.callbacks.onInterruption(placeholderQuestion);
            }
          } catch (_) {}
          this.updateStatus('已检测到唤醒词，暂停讲解，等待您说完问题…', true);
        }
      }
    }
    this.resetRecognitionTimeout();
  }

  handleRecognitionError(event) {
    try { console.log('[vih] onerror:', event?.error); } catch {}
    this.isRecognizing = false;
    let shouldRetry = false;
    switch (event?.error) {
      case 'network':
        this.networkErrorCount++; shouldRetry = this.networkErrorCount < this.options.maxNetworkErrors; break;
      case 'no-speech': shouldRetry = true; break;
      case 'aborted': return;
      default: shouldRetry = true;
    }
    this.updateStatus('语音识别错误: ' + (event?.error || 'unknown'), false);
    if (shouldRetry && this.isActive) {
      const delay = Math.min(1000 * Math.pow(2, this.networkErrorCount), 10000);
      setTimeout(() => this.restartRecognition(), delay);
    }
  }

  handleRecognitionEnd() {
    try { console.log('[vih] onend'); } catch {}
    this.isRecognizing = false;
    this.updateStatus('语音识别已停止', false);
    if (this.isActive && !this.isInterrupted) {
      setTimeout(() => { if (this.isActive && !this.isInterrupted && !this.isRecognizing) this.restartRecognition(); }, 500);
    }
  }

  processVoiceInput(recognizedText) {
    if (!this.isValidInput(recognizedText)) return;
    if (!this.containsWakeWord(recognizedText)) return;
    const question = this.extractQuestion(recognizedText);
    if (!question) return;
    this.handleVoiceInterruption(question);
  }

  isValidInput(text) {
    const t = String(text || '').trim();
    if (t.length < 2) return false;
    if (/^[0-9\s\.,!?]+$/.test(t)) return false;
    return true;
  }

  containsWakeWord(transcript) {
    if (!this.options.enableWakeWordDetection) return true;
    const text = String(transcript || '').toLowerCase().trim();
    return this.options.wakeWords.some((w) => text.includes(String(w).toLowerCase()));
  }

  extractQuestion(transcript) {
    let text = String(transcript || '').trim();
    for (const w of this.options.wakeWords) {
      const patterns = [ new RegExp(`^${w}[，,。.！!？?\\s]*`, 'i'), new RegExp(`^${w}`, 'i') ];
      for (const p of patterns) { if (p.test(text)) { text = text.replace(p, '').trim(); break; } }
    }
    return text;
  }

  async handleVoiceInterruption(question) {
    try { console.log('[vih] interruption:', question); } catch {}
    this.isInterrupted = true;
    this.stopVoiceDetection();
    // 若中间结果阶段已触发过“早期打断”，避免重复调用 onInterruption
    if (!this.earlyInterrupted) {
      if (this.callbacks.onInterruption) {
        this.interruptedContext = await this.callbacks.onInterruption(question);
      }
    } else if (!this.interruptedContext && this.callbacks.onInterruption) {
      // 兜底：确保有上下文
      this.interruptedContext = await this.callbacks.onInterruption(question);
    }
    this.updateStatus('正在处理您的问题...', true);
    try {
      const answer = await this.sendQuestionToAI(question);
      if (this.callbacks.onResponse) this.callbacks.onResponse(question, answer);
      await this.playAIResponse(answer);
      await this.resumePlayback();
    } catch (e) {
      this.handleError('处理问题失败: ' + e.message);
      setTimeout(() => this.resumePlayback(), 2000);
    }
  }

  async sendQuestionToAI(question) {
    const courseId = this.interruptedContext?.courseId || this.interruptedContext?.sessionId || '';
    const result = await askQuestion(courseId, question);
    return result?.answer || '（无回答）';
  }

  async playAIResponse(answer) {
    try {
      const courseId = this.interruptedContext?.courseId || this.interruptedContext?.sessionId || '';
      const tts = await synthesizeSpeech(courseId, answer);
      if (!tts) return;
      return new Promise(async (resolve) => {
        const url = URL.createObjectURL(tts);
        const audio = new Audio(url);
        audio.onended = resolve; audio.onerror = resolve; audio.play();
      });
    } catch (_) {}
  }

  async resumePlayback() {
    if (!this.isInterrupted || !this.interruptedContext) return;
    if (this.callbacks.onResume) await this.callbacks.onResume(this.interruptedContext, this.options.resumeMode);
    this.isInterrupted = false;
    this.interruptedContext = null;
    this.earlyInterrupted = false;
    setTimeout(() => { if (!this.isInterrupted) this.startVoiceDetection(); }, 1000);
    this.updateStatus('已恢复播放', false);
  }

  resetRecognitionTimeout() {
    this.clearRecognitionTimeout();
    this.recognitionTimeout = setTimeout(() => {
      if (this.isRecognizing && !this.interimTranscript && !this.finalTranscript) {
        try { console.log('[vih] 识别超时，重启'); } catch {}
        try { this.speechRecognition.stop(); } catch {}
      }
    }, this.options.recognitionTimeout);
  }

  clearRecognitionTimeout() { if (this.recognitionTimeout) { clearTimeout(this.recognitionTimeout); this.recognitionTimeout = null; } }

  updateStatus(message, isActive) {
    if (this.callbacks.onStatusUpdate) this.callbacks.onStatusUpdate(message, isActive);
    try { console.log('[vih] status:', message, isActive ? '(active)' : '(inactive)'); } catch {}
  }

  handleError(message) { if (this.callbacks.onError) this.callbacks.onError(message); try { console.error('[vih] error:', message); } catch {} }
}


