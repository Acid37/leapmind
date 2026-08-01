import { normalizePhonemeTimeline } from '@/features/virtualTeacher/animationCue.js';

const TIME_DOMAIN_DATA_LENGTH = 2048;
const DEFAULT_FFT_SIZE = 2048;

export class LipSync {
  constructor(audio) {
    this.audio = audio;

    this.analyser = audio.createAnalyser();
    this.analyser.fftSize = DEFAULT_FFT_SIZE;
    this.analyser.smoothingTimeConstant = 0.7;
    this.timeDomainData = new Float32Array(TIME_DOMAIN_DATA_LENGTH);
    this.frequencyData = new Uint8Array(this.analyser.frequencyBinCount);
    this.currentSource = null; // 跟踪当前音频源
    this.microphoneSource = null; // 麦克风音频源
    this.microphoneStream = null;
    this.isMicrophoneConnected = false; // 麦克风连接状态

    this._prevVolume = 0; // 音量平滑（包络）
    this._lastActive = false; // 上一帧是否处于说话/录音活跃
    this.mediaElementSource = null; // HTMLAudioElement 源（通过 AudioContext 输出）
    this.mediaElementGain = null; // 媒体元素的输出增益（通往 destination）
    this._mediaElementRef = null; // 保存引用用于判断播放状态
    this.phonemeTimeline = [];
    this.playbackStartedAt = 0;
  }

  update() {
    // 认为当前是否有活跃音源（播放中或麦克风连接）
    const mediaPlaying = !!(this._mediaElementRef && !this._mediaElementRef.paused && !this._mediaElementRef.ended);
    const active = !!this.currentSource || this.isMicrophoneConnected || mediaPlaying;

    // 在非活跃状态下快速衰减到0
    if (!active && !this._lastActive) {
      this._prevVolume *= 0.85;
      if (this._prevVolume < 0.001) this._prevVolume = 0;
      this._lastActive = active;
      return { volume: this._prevVolume, weights: undefined, active };
    }

    // 时域数据用于音量（包络）
    this.analyser.getFloatTimeDomainData(this.timeDomainData);

    let peak = 0.0;
    let rms = 0.0;
    for (let i = 0; i < TIME_DOMAIN_DATA_LENGTH; i++) {
      const sample = this.timeDomainData[i];
      peak = Math.max(peak, Math.abs(sample));
      rms += sample * sample;
    }
    rms = Math.sqrt(rms / TIME_DOMAIN_DATA_LENGTH);
    const combinedVolume = peak * 0.7 + rms * 0.3;

    // 非线性映射以增强动态范围
    let env = 1 / (1 + Math.exp(-35 * combinedVolume + 3));
    if (env < 0.03) env = 0; // 静音门限
    env = Math.pow(env, 0.8) * 1.2;
    env = Math.min(env, 1.0);

    // Attack/Release 平滑（独立上升/下降常数）
    const attack = 0.35; // 越大上升越快
    const release = 0.20; // 越大下降越快
    if (env > this._prevVolume) {
      this._prevVolume = this._prevVolume + (env - this._prevVolume) * attack;
    } else {
      this._prevVolume = this._prevVolume + (env - this._prevVolume) * release;
    }

    let weights = this._weightsFromPhonemeTimeline();
    if (!weights) {
      // 无音素时间轴时，继续使用频谱估算作为兼容回退
      this.analyser.getByteFrequencyData(this.frequencyData);
      weights = this._estimateVowelWeights(this.frequencyData, this._prevVolume);
    }

    this._lastActive = active;
    return { volume: this._prevVolume, weights, active };
  }

  async playFromArrayBuffer(buffer, onEnded, phonemes = []) {
    // 确保AudioContext处于运行状态（有些浏览器需用户交互触发后resume）
    if (this.audio.state === 'suspended') {
      try { await this.audio.resume(); } catch (_) {}
    }
    // 停止并清理之前的音频源
    if (this.currentSource) {
      try {
        this.currentSource.stop();
        this.currentSource.disconnect();
      } catch (e) {
        // 忽略已经停止的音频源错误
      }
    }

    const audioBuffer = await this.audio.decodeAudioData(buffer);

    const bufferSource = this.audio.createBufferSource();
    bufferSource.buffer = audioBuffer;
    this.currentSource = bufferSource;
    this.phonemeTimeline = normalizePhonemeTimeline(phonemes);

    bufferSource.connect(this.audio.destination);
    bufferSource.connect(this.analyser);
    bufferSource.start();
    this.playbackStartedAt = this.audio.currentTime;
    
    // 音频结束时清理引用
    bufferSource.addEventListener("ended", () => {
      this.currentSource = null;
      this.phonemeTimeline = [];
      if (onEnded) {
        onEnded();
      }
    });
  }

  async playFromURL(url, onEnded) {
    const res = await fetch(url);
    const buffer = await res.arrayBuffer();
    this.playFromArrayBuffer(buffer, onEnded);
  }

  // 连接麦克风进行实时嘴型同步
  async connectMicrophone() {
    try {
      // 确保AudioContext处于运行状态
      if (this.audio.state === 'suspended') {
        await this.audio.resume();
        console.log('AudioContext resumed for microphone');
      }

      // 请求麦克风权限
      const stream = await navigator.mediaDevices.getUserMedia({ 
        audio: {
          echoCancellation: true,
          noiseSuppression: true,
          autoGainControl: true
        } 
      });
      
      // 断开之前的麦克风连接
      this.disconnectMicrophone();
      
      // 创建麦克风音频源
      this.microphoneSource = this.audio.createMediaStreamSource(stream);
      this.microphoneStream = stream;
      
      // 连接到分析器
      this.microphoneSource.connect(this.analyser);
      
      this.isMicrophoneConnected = true;
      console.log('Microphone connected for lip sync');
      
      return true;
    } catch (error) {
      console.error('Failed to connect microphone for lip sync:', error);
      this.isMicrophoneConnected = false;
      return false;
    }
  }

  // 断开麦克风连接
  disconnectMicrophone() {
    if (this.microphoneSource) {
      try {
        this.microphoneSource.disconnect();
        this.microphoneSource = null;
        console.log('Microphone disconnected');
      } catch (error) {
        console.warn('Error disconnecting microphone:', error);
      }
    }
    if (this.microphoneStream) {
      this.microphoneStream.getTracks().forEach((track) => track.stop());
      this.microphoneStream = null;
    }
    this.isMicrophoneConnected = false;
  }

  // 检查麦克风是否已连接
  isMicConnected() {
    return this.isMicrophoneConnected;
  }

  // 简易的频谱特征到元音嘴型的估计
  _estimateVowelWeights(freq, volume) {
    if (volume === 0) {
      return undefined;
    }

    // 计算频谱质心（spectral centroid）
    let num = 0;
    let den = 0;
    const n = freq.length;
    for (let i = 0; i < n; i++) {
      const mag = freq[i];
      num += i * mag;
      den += mag;
    }
    let centroid = den > 0 ? num / den / n : 0.0; // 0..1

    // 三角隶属近似：
    // 低质心 -> aa（张口大），中质心 -> oh/ou， 高质心 -> ee/ih
    const aa = this._clamp01(1.2 * (1.0 - centroid));
    const mid = this._clamp01(1.0 - Math.abs(centroid - 0.5) * 2.0);
    const ee = this._clamp01(1.2 * centroid - 0.1);

    // 归一并乘音量
    let sum = aa + mid + ee;
    if (sum < 1e-6) {
      return undefined;
    }
    const aaW = (aa / sum) * volume;
    const ohW = (mid / sum) * volume;
    const eeW = (ee / sum) * volume;

    // 映射到 five-visemes：aa, ee, ih(≈ee), oh, ou(≈oh)
    return {
      aa: aaW,
      ee: eeW * 0.6 + aaW * 0.1, // 略微混合避免突变
      ih: eeW,
      oh: ohW,
      ou: ohW * 0.9 + aaW * 0.1,
    };
  }

  _clamp01(v) {
    return Math.max(0, Math.min(1, v));
  }

  _weightsFromPhonemeTimeline() {
    if (!this.currentSource || this.phonemeTimeline.length === 0) return null;

    const elapsedMs = (this.audio.currentTime - this.playbackStartedAt) * 1000;
    const item = this.phonemeTimeline.find(
      (entry) => elapsedMs >= entry.startMs && elapsedMs < entry.endMs,
    );
    if (!item) return { aa: 0, ee: 0, ih: 0, oh: 0, ou: 0 };

    const weights = { aa: 0, ee: 0, ih: 0, oh: 0, ou: 0 };
    weights[item.viseme] = Math.max(0.25, this._prevVolume);
    return weights;
  }

  // 使用现有的 <audio> 元素作为输入源，仅连接到 analyser，不改变其播放设备
  attachMediaElement(audioElement) {
    try {
      if (!audioElement) return false;
      // resume 确保能创建节点
      if (this.audio.state === 'suspended') {
        this.audio.resume().catch(() => {});
      }
      // 如果先前已连接，先断开
      this.detachMediaElement();
      this.mediaElementSource = this.audio.createMediaElementSource(audioElement);
      // 路由到分析器
      this.mediaElementSource.connect(this.analyser);
      // 通过 WebAudio 输出到设备，避免浏览器实现差异造成静音
      this.mediaElementGain = this.audio.createGain();
      this.mediaElementGain.gain.value = 1.0;
      this.mediaElementSource.connect(this.mediaElementGain);
      this.mediaElementGain.connect(this.audio.destination);
      // 静音 DOM 元素本身，防止双路径回声
      try { audioElement.volume = 0; } catch {}
      this._mediaElementRef = audioElement;
      return true;
    } catch (e) {
      console.warn('attachMediaElement failed:', e);
      return false;
    }
  }

  detachMediaElement() {
    if (this.mediaElementSource) {
      try {
        this.mediaElementSource.disconnect();
      } catch {}
      this.mediaElementSource = null;
    }
    if (this.mediaElementGain) {
      try { this.mediaElementGain.disconnect(); } catch {}
      this.mediaElementGain = null;
    }
    if (this._mediaElementRef) {
      try { this._mediaElementRef.volume = 1.0; } catch {}
    }
    this._mediaElementRef = null;
  }

  dispose() {
    if (this.currentSource) {
      try { this.currentSource.stop(); } catch {}
      try { this.currentSource.disconnect(); } catch {}
      this.currentSource = null;
    }
    this.phonemeTimeline = [];
    this.disconnectMicrophone();
    this.detachMediaElement();
    try { this.analyser.disconnect(); } catch {}
    if (this.audio?.state !== "closed") {
      this.audio?.close().catch(() => {});
    }
  }
}
