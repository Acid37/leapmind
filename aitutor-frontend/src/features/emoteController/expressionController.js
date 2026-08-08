import * as THREE from "three";
import { AutoLookAt } from "./autoLookAt.js";
import { AutoBlink } from "./autoBlink.js";

// Expressionを管理するクラス
// 主に前の表情を保持しておいて次の表情を適用する際に0に戻す作業や、
// 前の表情が終わるまで待ってから表情適用する役割を持っている。
export class ExpressionController {
  constructor(vrm, camera) {
    this._autoLookAt = new AutoLookAt(vrm, camera);
    this._autoBlink = undefined;
    this._expressionManager = undefined;
    this._currentEmotion = "neutral";
    this._currentLipSync = null; // 兼容单嘴型输入
    this._currentVisemeWeights = null; // 多嘴型输入 { aa, ih, ou, ee, oh }
    this._prevVisemeWeights = { aa: 0, ih: 0, ou: 0, ee: 0, oh: 0 };
    this._lipRise = 0.5; // 上升平滑系数
    this._lipFall = 0.25; // 下降平滑系数

    this._emotionTimeout = null; // 添加表情超时管理

    if (vrm && vrm.expressionManager) {
      this._expressionManager = vrm.expressionManager;
      this._autoBlink = new AutoBlink(vrm.expressionManager);
    }
  }

  playEmotion(preset) {
    // 清理之前的表情超时
    if (this._emotionTimeout) {
      clearTimeout(this._emotionTimeout);
      this._emotionTimeout = null;
    }

    // 重置之前的表情
    if (this._currentEmotion !== "neutral") {
      this._expressionManager?.setValue(this._currentEmotion, 0);
    }

    if (preset === "neutral") {
      this._autoBlink?.setEnable(true);
      this._currentEmotion = preset;
      return;
    }

    const t = this._autoBlink?.setEnable(false) || 0;
    this._currentEmotion = preset;
    
    // 设置表情并添加自动重置机制
    this._emotionTimeout = setTimeout(() => {
      this._expressionManager?.setValue(preset, 1);
      
      // 5秒后自动重置为neutral，避免表情锁定
      this._emotionTimeout = setTimeout(() => {
        if (this._currentEmotion === preset) {
          this.playEmotion("neutral");
        }
      }, 5000);
    }, t * 1000);
  }

  lipSync(preset, value) {
    if (!this._expressionManager) {
      return;
    }
    
    // 只存储嘴型数据，不直接设置权重，让update方法统一处理
    this._currentLipSync = { preset, value };
    this._currentVisemeWeights = null; // 清空多嘴型，保持互斥
  }

  // 多嘴型权重输入（优先于单嘴型）
  lipSyncWeights(weights) {
    if (!this._expressionManager) {
      return;
    }
    // 规范化键集合，缺失则按0处理
    this._currentVisemeWeights = {
      aa: Math.max(0, Math.min(1, weights?.aa ?? 0)),
      ih: Math.max(0, Math.min(1, weights?.ih ?? 0)),
      ou: Math.max(0, Math.min(1, weights?.ou ?? 0)),
      ee: Math.max(0, Math.min(1, weights?.ee ?? 0)),
      oh: Math.max(0, Math.min(1, weights?.oh ?? 0)),
    };
    this._currentLipSync = null; // 清空单嘴型输入
  }

  update(delta) {
    if (this._autoBlink) {
      this._autoBlink.update(delta);
    }

    // 处理嘴型同步（优先使用多嘴型）
    if (this._expressionManager) {
      let targetWeights = null;
      if (this._currentVisemeWeights) {
        targetWeights = this._currentVisemeWeights;
      } else if (this._currentLipSync) {
        // 单嘴型回退为权重对象
        const { preset, value } = this._currentLipSync;
        targetWeights = { aa: 0, ih: 0, ou: 0, ee: 0, oh: 0 };
        if (preset in targetWeights) {
          targetWeights[preset] = value;
        } else {
          // 若预设不是标准键，默认映射到 aa
          targetWeights.aa = value;
        }
      } else {
        // 无输入：全部衰减为0
        targetWeights = { aa: 0, ih: 0, ou: 0, ee: 0, oh: 0 };
      }

      // 平滑到目标权重
      const smoothed = {};
      let mouthActivity = 0;
      for (const key of ["aa", "ih", "ou", "ee", "oh"]) {
        const prev = this._prevVisemeWeights[key] ?? 0;
        const tgt = targetWeights[key] ?? 0;
        const k = tgt > prev ? this._lipRise : this._lipFall;
        const next = prev + (tgt - prev) * k;
        smoothed[key] = next;
        this._expressionManager.setValue(key, next);
        if (next > mouthActivity) mouthActivity = next;
      }
      this._prevVisemeWeights = smoothed;

      // 嘴型活跃时，压低当前表情以避免冲突
      if (mouthActivity > 0 && this._currentEmotion !== "neutral") {
        this._expressionManager.setValue(
          this._currentEmotion,
          Math.max(0, 1 - mouthActivity * 0.5)
        );
      }
    }
  }
}