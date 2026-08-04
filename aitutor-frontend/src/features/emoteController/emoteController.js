import * as THREE from "three";
import { ExpressionController } from "./expressionController.js";
import { HeadMotionController } from "./headMotionController.js";

/**
 * 感情表現としてExpressionとMotionを操作する為のクラス
 * デモにはExpressionのみが含まれています
 */
export class EmoteController {
  constructor(vrm, camera) {
    this._expressionController = new ExpressionController(vrm, camera);
    this._headMotionController = new HeadMotionController(
      vrm,
      this._expressionController
    );
  }

  lipSync(preset, value) {
    this._expressionController.lipSync(preset, value);
  }

  lipSyncWeights(weights) {
    this._expressionController.lipSyncWeights(weights);
  }

  playEmotion(preset) {
    this._expressionController.playEmotion(preset);
  }





  // 根据消息内容触发合适的头部动作（可选结合情绪/风格）
  playHeadMotionByMessage(message, emotionOrStyle) {
    this._headMotionController.playMotionByContent(message, emotionOrStyle);
  }

  // 根据情绪/风格触发头部动作
  playHeadMotionByEmotion(emotionOrStyle) {
    this._headMotionController.playMotionByEmotion(emotionOrStyle);
  }

  // 直接触发某个头部动作
  playHeadMotion(type) {
    this._headMotionController.playHeadMotion(type);
  }

  // 获取所有头部动作类型（用于UI枚举）
  getHeadMotionTypes() {
    return [
      "bigNod",
      "smallNod",
      "tiltHead",
      "bigShake",
      "smallShake",
      "lookUp",
    ];
  }

  // 停止当前头部动作
  stopHeadMotion() {
    this._headMotionController.stopCurrentMotion();
  }

  // 设置某个动作的触发关键词
  setHeadMotionTriggers(motion, triggers) {
    this._headMotionController.setTriggers(motion, triggers);
  }

  // 获取当前所有触发关键词
  getHeadMotionTriggers() {
    return this._headMotionController.getTriggers();
  }

  update(delta) {
    this._expressionController.update(delta);
    this._headMotionController.update(delta);
  }
}