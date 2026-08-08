import { loadVRMAnimation } from "@/lib/VRMAnimation/loadVRMAnimation.js";
import { buildUrl } from "@/utils/buildUrl";
import * as THREE from "three";

/**
 * 自动播放动作管理器（JS 版本）
 * 负责在用户无操作时自动播放 movement 文件夹中的动作
 * 从 idleAnimationManager.ts 无损转换，移除了类型标注
 */
export class IdleAnimationManager {
  model;
  idleAnimations = [];
  currentIdleAction = null;
  idleTimer = 0;
  isIdle = false;
  userActivityTimer = 0;
  IDLE_TIMEOUT = 10000; // 10秒无操作后开始播放动作
  ANIMATION_SWITCH_INTERVAL = 15000; // 15秒切换一次动作
  animationSwitchTimer = 0;
  currentAnimationIndex = 0;
  naturalIdleAction = null; // 自然待机动作

  // 动作文件列表
  ANIMATION_FILES = [
    "movement/VRMA_01.vrma",
    "movement/VRMA_02.vrma",
    "movement/VRMA_03.vrma",
    "movement/VRMA_04.vrma",
    "movement/VRMA_05.vrma",
    "movement/VRMA_06.vrma",
    "movement/VRMA_07.vrma",
  ];

  constructor(model) {
    this.model = model;
    this.loadIdleAnimations();
    this.setupUserActivityListeners();
  }

  /**
   * 加载所有待机动作文件
   */
  async loadIdleAnimations() {
    try {
      // 首先加载自然待机动作（idle_loop.vrma）
      try {
        const naturalIdleUrl = buildUrl("idle_loop.vrma");
        const naturalIdleAnimation = await loadVRMAnimation(naturalIdleUrl);
        if (naturalIdleAnimation && this.model.vrm && this.model.mixer) {
          const clip = naturalIdleAnimation.createAnimationClip(this.model.vrm);
          this.naturalIdleAction = this.model.mixer.clipAction(clip);
          this.naturalIdleAction.setLoop(THREE.LoopRepeat, Infinity);
          this.naturalIdleAction.setEffectiveWeight(1.0);
          this.naturalIdleAction.play();
          console.log("Loaded natural idle animation (idle_loop.vrma)");
        }
      } catch (error) {
        console.warn("Failed to load natural idle animation:", error);
      }

      // 然后加载 movement 动作
      for (const animationFile of this.ANIMATION_FILES) {
        try {
          const animationUrl = buildUrl(animationFile);
          const animation = await loadVRMAnimation(animationUrl);
          if (animation) {
            this.idleAnimations.push(animation);
            console.log(`Loaded idle animation: ${animationFile}`);
          }
        } catch (error) {
          console.warn(`Failed to load animation ${animationFile}:`, error);
        }
      }
      console.log(`Loaded ${this.idleAnimations.length} idle animations`);
    } catch (error) {
      console.error("Error loading idle animations:", error);
    }
  }

  /**
   * 设置用户活动监听器
   */
  setupUserActivityListeners() {
    const events = [
      "mousedown",
      "mousemove",
      "keypress",
      "scroll",
      "touchstart",
      "click",
      "keydown",
      "keyup",
      "touchmove",
      "touchend",
    ];

    const resetUserActivity = () => {
      this.userActivityTimer = Date.now();
      if (this.isIdle) {
        this.stopIdleAnimation();
      }
    };

    events.forEach((event) => {
      document.addEventListener(event, resetUserActivity, true);
    });

    // 初始化用户活动时间
    this.userActivityTimer = Date.now();
  }

  /**
   * 开始播放待机动作
   */
  startIdleAnimation() {
    if (this.idleAnimations.length === 0 || !this.model.vrm || !this.model.mixer) {
      return;
    }

    this.isIdle = true;
    this.playRandomIdleAnimation();
    console.log("Started idle animation");
  }

  /**
   * 播放随机待机动作
   */
  playRandomIdleAnimation() {
    if (this.idleAnimations.length === 0 || !this.model.vrm || !this.model.mixer) {
      return;
    }

    // 停止当前动作但保持自然待机动作
    if (this.currentIdleAction) {
      this.currentIdleAction.fadeOut(0.5);
      this.currentIdleAction.stop();
      this.currentIdleAction.enabled = false;
      this.currentIdleAction = null;
    }

    // 选择下一个动作（循环播放）
    this.currentAnimationIndex = (this.currentAnimationIndex + 1) % this.idleAnimations.length;
    const animation = this.idleAnimations[this.currentAnimationIndex];

    try {
      const clip = animation.createAnimationClip(this.model.vrm);
      this.currentIdleAction = this.model.mixer.clipAction(clip);

      // 设置动作属性
      this.currentIdleAction.reset();
      this.currentIdleAction.setLoop(THREE.LoopRepeat, Infinity);
      this.currentIdleAction.setEffectiveWeight(0.8); // 降低权重避免过于强烈
      this.currentIdleAction.setEffectiveTimeScale(1.0);
      this.currentIdleAction.clampWhenFinished = false;

      // 如果有自然待机动作，先降低其权重
      if (this.naturalIdleAction) {
        this.naturalIdleAction.setEffectiveWeight(0.3);
      }

      this.currentIdleAction.fadeIn(1.0); // 使用较长的淡入时间
      this.currentIdleAction.play();

      // 重置切换计时器
      this.animationSwitchTimer = 0;

      console.log(`Playing idle animation ${this.currentAnimationIndex + 1}/${this.idleAnimations.length}`);
    } catch (error) {
      console.error("Error playing idle animation:", error);
    }
  }

  /**
   * 停止待机动作，返回自然待机姿势而不是T-pose
   */
  stopIdleAnimation() {
    if (this.currentIdleAction) {
      this.currentIdleAction.fadeOut(1.0); // 使用较长的淡出时间

      // 延迟停止动作，给淡出效果时间
      setTimeout(() => {
        if (this.currentIdleAction) {
          this.currentIdleAction.stop();
          this.currentIdleAction.enabled = false;
          this.currentIdleAction = null;
        }
      }, 1000);
    }

    // 恢复自然待机动作的权重
    if (this.naturalIdleAction) {
      this.naturalIdleAction.setEffectiveWeight(1.0);
    }

    this.isIdle = false;
    this.animationSwitchTimer = 0;
    console.log("Stopped idle animation, returning to natural pose");
  }

  /**
   * 更新管理器状态，应在主循环中调用
   */
  update(delta) {
    const now = Date.now();
    const timeSinceLastActivity = now - this.userActivityTimer;

    // 检查是否应该开始待机动作
    if (!this.isIdle && timeSinceLastActivity > this.IDLE_TIMEOUT) {
      this.startIdleAnimation();
    }

    // 如果正在播放待机动作，检查是否需要切换动作
    if (this.isIdle && this.currentIdleAction) {
      this.animationSwitchTimer += delta * 1000; // 转换为毫秒

      if (this.animationSwitchTimer > this.ANIMATION_SWITCH_INTERVAL) {
        this.playRandomIdleAnimation();
      }
    }
  }

  /**
   * 手动重置用户活动状态
   */
  resetUserActivity() {
    this.userActivityTimer = Date.now();
    if (this.isIdle) {
      this.stopIdleAnimation();
    }
  }

  /**
   * 销毁管理器
   */
  dispose() {
    this.stopIdleAnimation();
    if (this.naturalIdleAction) {
      this.naturalIdleAction.stop();
      this.naturalIdleAction = null;
    }
    this.idleAnimations = [];
  }

  /**
   * 获取当前状态
   */
  getStatus() {
    return {
      isIdle: this.isIdle,
      animationCount: this.idleAnimations.length,
      currentAnimation: this.currentAnimationIndex,
    };
  }
}