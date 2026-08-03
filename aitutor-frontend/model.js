import * as THREE from "three";
import { VRM, VRMLoaderPlugin, VRMUtils } from "@pixiv/three-vrm";
import { GLTFLoader } from "three/examples/jsm/loaders/GLTFLoader";
import { VRMAnimation } from "../../lib/VRMAnimation/VRMAnimation";
import { VRMLookAtSmootherLoaderPlugin } from "@/lib/VRMLookAtSmootherLoaderPlugin/VRMLookAtSmootherLoaderPlugin.js";

import { LipSync } from "../lipSync/lipSync.js";
import { EmoteController } from "../emoteController/emoteController.js";
import { IdleAnimationManager } from "./idleAnimationManager.js";

/**
 * 3Dキャラクターを管理するクラス（JS 版本）
 * 从 model.ts 无损转换，移除了类型标注
 */
export class Model {
  vrm; // VRM | null | undefined
  mixer; // THREE.AnimationMixer | undefined
  emoteController; // EmoteController | undefined
  idleAnimationManager; // IdleAnimationManager | undefined

  _lookAtTargetParent; // THREE.Object3D
  _lipSync; // LipSync
  _simulatedSpeech = false; // speechSynthesis 模拟口型标志

  constructor(lookAtTargetParent) {
    this._lookAtTargetParent = lookAtTargetParent;
    this._lipSync = new LipSync(new AudioContext());
  }



  async loadVRM(url) {
    const loader = new GLTFLoader();
    loader.register(
      (parser) =>
        new VRMLoaderPlugin(parser, {
          lookAtPlugin: new VRMLookAtSmootherLoaderPlugin(parser),
        })
    );

    const gltf = await loader.loadAsync(url);

    const vrm = (this.vrm = gltf.userData.vrm);
    vrm.scene.name = "VRMRoot";

    VRMUtils.rotateVRM0(vrm);
    this.mixer = new THREE.AnimationMixer(vrm.scene);

    // 修复材质颜色空间，确保vRoid Studio创建的模型肤色正常显示
    this.fixMaterialColorSpace(vrm);

    this.emoteController = new EmoteController(vrm, this._lookAtTargetParent);
    this.idleAnimationManager = new IdleAnimationManager(this);
    

  }

  /**
   * 修复VRM材质颜色空间，解决vRoid Studio模型肤色变灰的问题
   */
  fixMaterialColorSpace(vrm) {
    if (!vrm || !vrm.scene) return;

    vrm.scene.traverse((object) => {
      if (object.isMesh && object.material) {
        // 处理单个材质
        if (Array.isArray(object.material)) {
          object.material.forEach((mat) => this.fixMaterial(mat));
        } else {
          this.fixMaterial(object.material);
        }
      }
    });
  }

  /**
   * 修复单个材质的颜色空间设置
   */
  fixMaterial(material) {
    if (!material) return;

    // 确保材质使用正确的颜色空间
    if (material.map) {
      material.map.colorSpace = THREE.SRGBColorSpace;
    }
    if (material.emissiveMap) {
      material.emissiveMap.colorSpace = THREE.SRGBColorSpace;
    }
    if (material.normalMap) {
      material.normalMap.colorSpace = THREE.SRGBColorSpace;
    }
    if (material.roughnessMap) {
      material.roughnessMap.colorSpace = THREE.SRGBColorSpace;
    }
    if (material.metalnessMap) {
      material.metalnessMap.colorSpace = THREE.SRGBColorSpace;
    }
    if (material.aoMap) {
      material.aoMap.colorSpace = THREE.SRGBColorSpace;
    }

    // 对于MToon材质（vRoid Studio常用），确保主纹理颜色空间正确
    if (material.userData && material.userData.vrmMToon) {
      const mtoon = material.userData.vrmMToon;
      if (mtoon.mainTexture) {
        mtoon.mainTexture.colorSpace = THREE.SRGBColorSpace;
      }
    }

    // 确保材质本身使用SRGB颜色空间
    material.colorSpace = THREE.SRGBColorSpace;
  }

  unLoadVrm() {
    if (this.idleAnimationManager) {
      this.idleAnimationManager.dispose();
      this.idleAnimationManager = undefined;
    }
    if (this.vrm) {
      VRMUtils.deepDispose(this.vrm.scene);
      this.vrm = null;
    }
  }

  /**
   * VRMアニメーションを読み込む
   *
   * https://github.com/vrm-c/vrm-specification/blob/master/specification/VRMC_vrm_animation-1.0/README.ja.md
   */
  async loadAnimation(vrmAnimation) {
    const { vrm, mixer } = this;
    if (vrm == null || mixer == null) {
      throw new Error("You have to load VRM first");
    }

    const clip = vrmAnimation.createAnimationClip(vrm);
    const action = mixer.clipAction(clip);
    action.play();
  }

  /**
   * 音声を再生し、リップシンクを行う
   */
  async speak(buffer, screenplay) {
    // 开始说话时重置用户活动状态，停止待机动作
    this.idleAnimationManager?.resetUserActivity();
    
    // 设置表情
    this.emoteController?.playEmotion(screenplay.expression);
    
    // 根据说话内容与"情绪"优先触发头部动作（稍微延迟，避免与表情切换冲突）
    setTimeout(() => {
      this.emoteController?.playHeadMotionByMessage(screenplay.talk.message, screenplay.expression);
    }, 300);

    await new Promise((resolve) => {
      this._lipSync?.playFromArrayBuffer(buffer, () => {
        resolve(true);
      });
    });
  }

  /**
   * 停止当前的语音播放（用于讲课暂停/停止）
   */
  stopSpeaking() {
    try {
      if (this._lipSync?.currentSource) {
        this._lipSync.currentSource.stop();
      }
    } catch (_) {}
  }

  /**
   * 停止当前说话（如果有）
   */
  stopSpeaking() {
    try {
      const src = this._lipSync?.currentSource;
      if (src) {
        src.stop();
      }
    } catch (_) {}
  }

  /**
   * 使用现有 <audio> 元素作为音频输出，仅用于分析驱动口型/头动，避免双重播放造成回声
   */
  startLipSyncFromMediaElement(audioElement, screenplay) {
    // 停止待机
    this.idleAnimationManager?.resetUserActivity();

    // 设置表情
    if (screenplay?.expression) {
      this.emoteController?.playEmotion(screenplay.expression);
    }

    // 结合内容触发头部动作
    setTimeout(() => {
      if (screenplay?.talk?.message) {
        this.emoteController?.playHeadMotionByMessage(
          screenplay.talk.message,
          screenplay.expression
        );
      }
    }, 300);

    // 仅作为分析源，不输出到 destination
    this._lipSync?.attachMediaElement(audioElement);

    // 在音频结束时自动解除绑定
    const detachOnEnded = () => {
      try { this._lipSync?.detachMediaElement(); } catch {}
      audioElement?.removeEventListener?.("ended", detachOnEnded);
    };
    audioElement?.addEventListener?.("ended", detachOnEnded);
  }

  stopLipSyncFromMediaElement() {
    try { this._lipSync?.detachMediaElement(); } catch {}
  }



  /**
   * 开启/关闭模拟口型（speechSynthesis 无音频流时用）
   */
  setSimulatedSpeech(active) {
    this._simulatedSpeech = !!active;
    if (active) this.idleAnimationManager?.resetUserActivity();
  }

  /**
   * 老师伸手指向 PPT（左手轻微抬起，右手指向左前方）
   */
  _pointingTimer = null;

  pointAtPPT(active) {
    const vrm = this.vrm;
    if (!vrm) return;
    clearTimeout(this._pointingTimer);

    if (!active) {
      this._resetArmPose();
      return;
    }

    // 间歇性指向 PPT：避免一直举着太僵硬
    const pointOnce = () => {
      if (!this._simulatedSpeech) return;
      this._setArmPose();
      this._pointingTimer = setTimeout(() => {
        if (!this._simulatedSpeech) { this._resetArmPose(); return; }
        this._resetArmPose();
        this._pointingTimer = setTimeout(pointOnce, 1500 + Math.random() * 1000);
      }, 2500 + Math.random() * 1500);
    };
    pointOnce();
  }

  _setArmPose() {
    const vrm = this.vrm;
    if (!vrm) return;
    const rightUpperArm = vrm.humanoid?.getRawBoneNode('rightUpperArm');
    const rightLowerArm = vrm.humanoid?.getRawBoneNode('rightLowerArm');
    const rightHand = vrm.humanoid?.getRawBoneNode('rightHand');
    const leftUpperArm = vrm.humanoid?.getRawBoneNode('leftUpperArm');

    if (rightUpperArm) {
      rightUpperArm.rotation.set(0.4, 0.1, 1.2, 'YXZ'); // 外展 + 前抬
    }
    if (rightLowerArm) {
      rightLowerArm.rotation.set(-0.6, 1.2, 0.3, 'YXZ'); // 前臂伸展指向左前
    }
    if (rightHand) {
      rightHand.rotation.set(-0.2, 0, 0.5, 'YXZ'); // 手掌平展
    }
    if (leftUpperArm) {
      leftUpperArm.rotation.set(-0.2, 0.6, -0.3, 'YXZ'); // 左臂自然微抬
    }
  }

  _resetArmPose() {
    const vrm = this.vrm;
    if (!vrm) return;
    ['rightUpperArm', 'rightLowerArm', 'rightHand', 'leftUpperArm'].forEach(name => {
      const bone = vrm.humanoid?.getRawBoneNode(name);
      if (bone) bone.rotation.set(0, 0, 0);
    });
  }

  update(delta) {
    if (this._lipSync) {
      const { volume, weights } = this._lipSync.update();
      let effectiveVolume = volume;
      let effectiveWeights = weights;

      // speechSynthesis 无音频流 → 模拟口型
      if (this._simulatedSpeech && effectiveVolume < 0.05) {
        const t = performance.now() * 0.001;
        const pulse = Math.abs(Math.sin(t * 5.0)) * 0.6 + Math.abs(Math.sin(t * 3.3)) * 0.3;
        effectiveVolume = 0.25 + pulse * 0.75;
        effectiveWeights = {
          aa: effectiveVolume * (0.5 + 0.5 * Math.sin(t * 2.7)),
          ee: effectiveVolume * (0.3 + 0.3 * Math.sin(t * 4.1 + 1.0)) * 0.6,
          ih: effectiveVolume * (0.3 + 0.3 * Math.sin(t * 4.1 + 1.0)),
          oh: effectiveVolume * (0.4 + 0.4 * Math.sin(t * 3.5 + 2.0)),
          ou: effectiveVolume * (0.35 + 0.35 * Math.sin(t * 3.5 + 2.0)) * 0.9,
        };
      }

      if (effectiveWeights) {
        this.emoteController?.lipSyncWeights(effectiveWeights);
      } else {
        // 回退：至少用音量驱动 aa
        this.emoteController?.lipSync("aa", effectiveVolume);
      }
    }

    // 更新动画混合器与待机管理器
    this.mixer?.update(delta);
    this.idleAnimationManager?.update(delta);

    // 应用表情控制器的更新
    this.emoteController?.update(delta);

    // 更新VRM（包含lookAt等内部姿态）
    this.vrm?.update(delta);
  }
}