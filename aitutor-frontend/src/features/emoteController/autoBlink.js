import { BLINK_CLOSE_MAX, BLINK_OPEN_MAX } from "./emoteConstants.js";

/**
 * 自動瞬きを制御するクラス (JavaScript 版)
 */
export class AutoBlink {
  constructor(expressionManager) {
    this._expressionManager = expressionManager;
    this._remainingTime = 0;
    this._isAutoBlink = true;
    this._isOpen = true;
  }

  /**
   * 自動瞬きをON/OFFする。
   * 目が閉じている(blinkが1の)時に感情表現を入れてしまうと不自然になるので、
   * 目が開くまでの秒を返し、その時間待ってから感情表現を適用する。
   * @param {boolean} isAuto
   * @returns {number} 目が開くまでの秒
   */
  setEnable(isAuto) {
    this._isAutoBlink = isAuto;

    // 目が閉じている場合、目が開くまでの時間を返す
    if (!this._isOpen) {
      return this._remainingTime;
    }

    return 0;
  }

  /**
   * 更新処理
   * @param {number} delta 経過秒
   */
  update(delta) {
    if (this._remainingTime > 0) {
      this._remainingTime -= delta;
      return;
    }

    if (this._isOpen && this._isAutoBlink) {
      this.close();
      return;
    }

    this.open();
  }

  close() {
    this._isOpen = false;
    this._remainingTime = BLINK_CLOSE_MAX;
    this._expressionManager.setValue("blink", 1);
  }

  open() {
    this._isOpen = true;
    this._remainingTime = BLINK_OPEN_MAX;
    this._expressionManager.setValue("blink", 0);
  }
}