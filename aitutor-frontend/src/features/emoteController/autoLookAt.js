import * as THREE from "three";

// 目線を制御するクラス
// サッケードはVRMLookAtSmootherの中でやっているので、
// より目線を大きく動かしたい場合はここに実装する。
export class AutoLookAt {
  constructor(vrm, camera) {
    this._lookAtTarget = new THREE.Object3D();
    camera.add(this._lookAtTarget);

    if (vrm && vrm.lookAt) {
      vrm.lookAt.target = this._lookAtTarget;
    }
  }
}