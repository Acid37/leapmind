import * as THREE from "three";
import { VRMHumanBoneName } from "@pixiv/three-vrm";
import { ExpressionController } from "./expressionController.js";

/**
 * 头部动作与表情的搭配映射
 */
const MOTION_TO_EXPRESSION = {
  bigNod: "happy",       // 大幅点头 + 开心表情
  smallNod: "relaxed",   // 轻微点头 + 放松表情
  tiltHead: "surprised", // 歪头 + 惊讶表情
  bigShake: "angry",     // 大幅摆头 + 愤怒表情
  smallShake: "sad",     // 小幅摆头 + 悲伤表情
  lookUp: "surprised",   // 仰头 + 惊讶表情
};

/**
 * 情绪到头部动作的映射
 */
const EMOTION_TO_MOTION = {
  // 开心：倾向于轻快的点头和仰头
  happy: ["smallNod", "lookUp"],
  
  // 愤怒：倾向于摆头拒绝和大幅动作
  angry: ["bigShake", "smallShake"],
  
  // 悲伤：倾向于轻微摆头和歪头
  sad: ["smallShake", "tiltHead"],
  
  // 放松：倾向于轻微点头
  relaxed: ["smallNod"],
  
  // 中性/默认：根据关键词决定
  neutral: [], // 将完全依赖关键词检测
  
  // 对话风格映射
  surprised: ["lookUp", "tiltHead"],
  fear: ["tiltHead", "smallShake"],
  talk: [], // 依赖关键词检测
};

// 精确触发关键词（每个动作 5-10 个，避免过于宽泛）
const DEFAULT_TRIGGERS = {
  bigNod: [
    "完全同意",
    "非常赞同",
    "没错",
    "对极了",
    "确实如此",
    "当然",
    "毫无疑问",
    "正是如此",
  ],
  smallNod: [
    "好的",
    "嗯",
    "好啊",
    "行吧",
    "可以的",
    "明白了",
    "了解了",
    "没问题"
  ],
  bigShake: [
    "绝不",
    "绝对不行",
    "不同意",
    "强烈反对",
    "坚决不",
    "不可能",
    "别这样",
    "说错了",
  ],
  smallShake: [
    "不是吧",
    "不对",
    "先别",
    "算了吧",
    "先不要",
    "还是别",
    "不合适",
  ],
  tiltHead: [
    "?",
    "为什么",
    "怎么会",
    "什么意思",
    "不理解",
    "不明白",
    "有疑问",
    "奇怪",
    "真的吗",
  ],
  lookUp: [
    "天哪",
    "哇塞",
    "居然",
    "竟然",
    "不可思议",
    "难以置信",
    "太夸张了",
    "没想到",
  ],
};

/**
 * 头部动作控制器
 * 提供各种头部表情动画，能够与语音同步播放
 */
export class HeadMotionController {
  constructor(vrm, expressionController) {
    this._vrm = vrm;
    this._expressionController = expressionController;
    this._currentMotion = null;
    this._clock = new THREE.Clock();
    this._originalHeadRotation = new THREE.Euler();
    this._headBone = null;
    this._triggers = { ...DEFAULT_TRIGGERS };

    this._headBone = vrm.humanoid.getNormalizedBoneNode(VRMHumanBoneName.Head);
    if (this._headBone) {
      this._originalHeadRotation.copy(this._headBone.rotation);
    }
    this._clock.start();

    // 头部动作数据库
    this._motionDatabase = {
      bigNod: {
        duration: 1.2,
        keyframes: [
          { time: 0.0, rotation: new THREE.Euler(0, 0, 0) },
          { time: 0.3, rotation: new THREE.Euler(-0.4, 0, 0) },
          { time: 0.6, rotation: new THREE.Euler(0.1, 0, 0) },
          { time: 0.9, rotation: new THREE.Euler(-0.2, 0, 0) },
          { time: 1.2, rotation: new THREE.Euler(0, 0, 0) }
        ]
      },
      smallNod: {
        duration: 0.8,
        keyframes: [
          { time: 0.0, rotation: new THREE.Euler(0, 0, 0) },
          { time: 0.4, rotation: new THREE.Euler(-0.15, 0, 0) },
          { time: 0.8, rotation: new THREE.Euler(0, 0, 0) }
        ]
      },
      tiltHead: {
        duration: 1.8, // 延长歪头动作的停顿时间
        keyframes: [
          { time: 0.0, rotation: new THREE.Euler(0, 0, 0) },
          { time: 0.4, rotation: new THREE.Euler(0.1, 0.1, 0.3) },
          { time: 1.2, rotation: new THREE.Euler(0.1, 0.1, 0.3) }, // 延长停顿
          { time: 1.8, rotation: new THREE.Euler(0, 0, 0) }
        ]
      },
      bigShake: {
        duration: 1.4,
        keyframes: [
          { time: 0.0, rotation: new THREE.Euler(0, 0, 0) },
          { time: 0.2, rotation: new THREE.Euler(0, -0.5, 0) },
          { time: 0.5, rotation: new THREE.Euler(0, 0.5, 0) },
          { time: 0.8, rotation: new THREE.Euler(0, -0.3, 0) },
          { time: 1.1, rotation: new THREE.Euler(0, 0.2, 0) },
          { time: 1.4, rotation: new THREE.Euler(0, 0, 0) }
        ]
      },
      smallShake: {
        duration: 0.6,
        keyframes: [
          { time: 0.0, rotation: new THREE.Euler(0, 0, 0) },
          { time: 0.2, rotation: new THREE.Euler(0, -0.2, 0) },
          { time: 0.4, rotation: new THREE.Euler(0, 0.2, 0) },
          { time: 0.6, rotation: new THREE.Euler(0, 0, 0) }
        ]
      },
      lookUp: {
        duration: 1.0,
        keyframes: [
          { time: 0.0, rotation: new THREE.Euler(0, 0, 0) },
          { time: 0.3, rotation: new THREE.Euler(0.3, 0, 0) },
          { time: 0.7, rotation: new THREE.Euler(0.2, 0, 0) },
          { time: 1.0, rotation: new THREE.Euler(0, 0, 0) }
        ]
      }
    };
  }

  /**
   * 配置某个动作的触发关键词
   */
  setTriggers(motion, triggers) {
    this._triggers[motion] = [...triggers];
  }

  /**
   * 获取当前全部触发关键词
   */
  getTriggers() {
    return { ...this._triggers };
  }

  /**
   * 播放头部动作（搭配对应表情）
   */
  playHeadMotion(motionType) {
    const motionData = this._motionDatabase[motionType];
    if (!motionData || !this._headBone) return;

    // 播放对应的表情
    const expression = MOTION_TO_EXPRESSION[motionType];
    if (this._expressionController && expression) {
      this._expressionController.playEmotion(expression);
    }

    this._currentMotion = {
      type: motionType,
      startTime: this._clock.getElapsedTime(),
      duration: motionData.duration,
      keyframes: motionData.keyframes
    };
  }

  /**
   * 根据情绪（或对话风格）选择头部动作。
   * emotionOrStyle 来自 Screenplay.expression（neutral/happy/angry/sad/relaxed）
   * 或 Talk.style（talk/happy/sad/angry/fear/surprised）
   */
  playMotionByEmotion(emotionOrStyle) {
    const candidates = EMOTION_TO_MOTION[emotionOrStyle] || [];
    if (candidates.length > 0) {
      // 简单随机其一，避免动作重复显得机械
      const choice = candidates[Math.floor(Math.random() * candidates.length)];
      this.playHeadMotion(choice);
    }
  }

  /**
   * 根据语音内容智能选择头部动作（优先使用情绪/风格映射，其次使用关键词映射）
   */
  playMotionByContent(message, emotionOrStyle) {
    // 1) 先试着根据情绪/风格触发（若传入）
    if (emotionOrStyle && EMOTION_TO_MOTION[emotionOrStyle]?.length) {
      this.playMotionByEmotion(emotionOrStyle);
      return;
    }
    // 2) 再使用关键词映射
    const type = this._detectMotionFromText(message);
    if (type) {
      this.playHeadMotion(type);
    }
  }

  /**
   * 更新头部动作
   */
  update(delta) {
    if (!this._currentMotion || !this._headBone) return;

    const currentTime = this._clock.getElapsedTime();
    const elapsed = currentTime - this._currentMotion.startTime;

    if (elapsed >= this._currentMotion.duration) {
      // 动作结束，重置头部位置
      this._headBone.rotation.copy(this._originalHeadRotation);
      this._currentMotion = null;
      return;
    }

    // 计算当前旋转
    const progress = elapsed / this._currentMotion.duration;
    const rotation = this._interpolateRotation(progress, this._currentMotion.keyframes);
    
    // 应用旋转（叠加到原始旋转上）
    this._headBone.rotation.copy(this._originalHeadRotation);
    this._headBone.rotation.x += rotation.x;
    this._headBone.rotation.y += rotation.y;
    this._headBone.rotation.z += rotation.z;
  }

  /**
   * 插值计算旋转
   */
  _interpolateRotation(progress, keyframes) {
    // 找到当前时间对应的关键帧区间
    let startFrame = keyframes[0];
    let endFrame = keyframes[keyframes.length - 1];

    for (let i = 0; i < keyframes.length - 1; i++) {
      const currentFrame = keyframes[i];
      const nextFrame = keyframes[i + 1];
      
      const currentTime = currentFrame.time / this._currentMotion.duration;
      const nextTime = nextFrame.time / this._currentMotion.duration;

      if (progress >= currentTime && progress <= nextTime) {
        startFrame = currentFrame;
        endFrame = nextFrame;
        break;
      }
    }

    // 计算区间内的插值进度
    const startTime = startFrame.time / this._currentMotion.duration;
    const endTime = endFrame.time / this._currentMotion.duration;
    const localProgress = endTime > startTime ? (progress - startTime) / (endTime - startTime) : 0;

    // 使用平滑插值
    const smoothProgress = this._smoothstep(localProgress);

    // 插值旋转
    const result = new THREE.Euler();
    result.x = THREE.MathUtils.lerp(startFrame.rotation.x, endFrame.rotation.x, smoothProgress);
    result.y = THREE.MathUtils.lerp(startFrame.rotation.y, endFrame.rotation.y, smoothProgress);
    result.z = THREE.MathUtils.lerp(startFrame.rotation.z, endFrame.rotation.z, smoothProgress);

    return result;
  }

  /**
   * 平滑插值函数
   */
  _smoothstep(t) {
    return t * t * (3 - 2 * t);
  }

  /**
   * 文本匹配触发动作（强信号优先）：bigShake -> bigNod -> lookUp -> tiltHead -> smallShake -> smallNod
   */
  _detectMotionFromText(text) {
    const content = text.trim();
    if (!content) return null;

    const order = [
      "bigShake",
      "bigNod",
      "lookUp",
      "tiltHead",
      "smallShake",
      "smallNod",
    ];

    for (const type of order) {
      const list = this._triggers[type];
      if (list && list.some((kw) => content.includes(kw))) {
        return type;
      }
    }
    return null;
  }

  /**
   * 停止当前头部动作
   */
  stopCurrentMotion() {
    if (this._headBone) {
      this._headBone.rotation.copy(this._originalHeadRotation);
    }
    this._currentMotion = null;
  }

  /**
   * 销毁控制器
   */
  dispose() {
    this.stopCurrentMotion();
  }
}