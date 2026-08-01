import * as THREE from "three";
import { Model } from "./model.js";
import { loadVRMAnimation } from "@/lib/VRMAnimation/loadVRMAnimation.js";
import { buildUrl } from "@/utils/buildUrl.js";
import { OrbitControls } from "three/examples/jsm/controls/OrbitControls";

/**
 * three.jsを使った3Dビューワー（JS 版本）
 * 从 viewer.ts 无损转换，移除了类型标注
 */
export class Viewer {
  isReady;
  model;

  _renderer;
  _clock;
  _scene;
  _camera;
  _cameraControls;
  _animationFrameId;
  _resizeHandler;
  _loadRequestId;

  constructor() {
    this.isReady = false;
    this._animationFrameId = null;
    this._resizeHandler = null;
    this._loadRequestId = 0;

    // scene
    const scene = new THREE.Scene();
    this._scene = scene;

    // light
    // 主光（方向光）：提供明确的方向性阴影与高光，略偏上右，增强立体感
    const directionalLight = new THREE.DirectionalLight(0xffffff, 1.2);
    directionalLight.position.set(0.6, 1.2, 0.8).normalize();
    scene.add(directionalLight);

    // 环境光：整体抬亮场景，填充阴影区域，避免过暗（过强会"洗白"颜色）
    const ambientLight = new THREE.AmbientLight(0xffffff, 0.8);
    scene.add(ambientLight);

    // 半球光：模拟天空与地面反射的漫射光，平衡上下方向的亮度与色温
    // 使用更温暖的光照颜色以保持自然肤色
    const hemiLight = new THREE.HemisphereLight(0xfff5e1, 0x555555, 0.7);
    hemiLight.position.set(0, 1, 0);
    scene.add(hemiLight);

    // // 正面补光（方向光）：柔化面部阴影，避免五官区域偏暗
    // const fillLight = new THREE.DirectionalLight(0xffffff, 0.6);
    // fillLight.position.set(0, 0.8, 1.0).normalize();
    // scene.add(fillLight);

    // animate
    this._clock = new THREE.Clock();
    this._clock.start();
  }

  async loadVrm(url) {
    const requestId = ++this._loadRequestId;
    if (this.model?.vrm) {
      this.unloadVRM();
    }

    // gltf and vrm
    const model = new Model(this._camera || new THREE.Object3D());
    this.model = model;

    try {
      await model.loadVRM(url);
      if (requestId !== this._loadRequestId || this.model !== model || !model.vrm) {
        model.unLoadVrm();
        return null;
      }

      model.vrm.scene.traverse((obj) => {
        obj.frustumCulled = false;
      });
      this._scene.add(model.vrm.scene);

      if (!model.idleAnimationManager) {
        const vrma = await loadVRMAnimation(buildUrl("/idle_loop.vrma"));
        if (vrma && requestId === this._loadRequestId) model.loadAnimation(vrma);
      }

      requestAnimationFrame(() => {
        if (requestId === this._loadRequestId) this.fitToModel(0.9, 0.16);
      });
      return model;
    } catch (error) {
      if (this.model === model) this.model = undefined;
      model.unLoadVrm();
      throw error;
    }
  }

  unloadVRM() {
    if (this.model?.vrm) {
      this._scene.remove(this.model.vrm.scene);
    }
    this.model?.unLoadVrm();
  }

  /**
   * Reactで管理しているCanvasを後から設定する
   */
  setup(canvas) {
    if (this._renderer) {
      this.dispose();
    }
    const parentElement = canvas.parentElement;
    const width = parentElement?.clientWidth || canvas.width;
    const height = parentElement?.clientHeight || canvas.height;
    // renderer
    this._renderer = new THREE.WebGLRenderer({
      canvas: canvas,
      alpha: true,
      antialias: true,
    });
    this._renderer.outputColorSpace = THREE.SRGBColorSpace;
    // 对于VRM模型（特别是vRoid Studio创建的），使用更温和的色调映射或禁用
    // ACESFilmicToneMapping可能导致肤色变灰，改用LinearToneMapping或ReinhardToneMapping
    this._renderer.toneMapping = THREE.LinearToneMapping; // 或 THREE.ReinhardToneMapping
    this._renderer.toneMappingExposure = 1.15; // 稍微提高曝光值以增加亮度
    this._renderer.setSize(width, height);
    this._renderer.setPixelRatio(window.devicePixelRatio);

    // camera
    this._camera = new THREE.PerspectiveCamera(20.0, width / height, 0.1, 20.0);
    this._camera.position.set(0, 1.3, 1.5);
    this._cameraControls?.target.set(0, 1.3, 0);
    this._cameraControls?.update();
    // camera controls
    this._cameraControls = new OrbitControls(
      this._camera,
      this._renderer.domElement
    );
    this._cameraControls.screenSpacePanning = true;
    this._cameraControls.update();

    this._resizeHandler = () => this.resize();
    window.addEventListener("resize", this._resizeHandler);
    this.isReady = true;
    this._clock.start();
    this.update();
  }

  /**
   * canvasの親要素を参照してサイズを変更する
   */
  resize() {
    if (!this._renderer) return;

    const parentElement = this._renderer.domElement.parentElement;
    if (!parentElement) return;

    this._renderer.setPixelRatio(window.devicePixelRatio);
    this._renderer.setSize(
      parentElement.clientWidth,
      parentElement.clientHeight
    );

    if (!this._camera) return;
    this._camera.aspect =
      parentElement.clientWidth / parentElement.clientHeight;
    this._camera.updateProjectionMatrix();
  }

  /**
   * VRMのheadノードを参照してカメラ位置を調整する
   */
  resetCamera() {
    const headNode = this.model?.vrm?.humanoid.getNormalizedBoneNode("head");

    if (headNode) {
      const headWPos = headNode.getWorldPosition(new THREE.Vector3());
      this._camera?.position.set(
        this._camera.position.x,
        headWPos.y,
        this._camera.position.z
      );
      this._cameraControls?.target.set(headWPos.x, headWPos.y, headWPos.z);
      this._cameraControls?.update();
    }
  }

  /**
   * 根据模型包围盒调整相机距离与目标点，使角色在容器中居中且完整显示
   * padding: 留白系数，>1 会稍微拉远
   */
  fitToModel(padding = 1.2, verticalOffsetRatio = 0) {
    if (!this.model?.vrm || !this._camera || !this._cameraControls) return;

    const sceneObject = this.model.vrm.scene;
    const box = new THREE.Box3().setFromObject(sceneObject);
    if (box.isEmpty()) return;

    const size = new THREE.Vector3();
    const center = new THREE.Vector3();
    box.getSize(size);
    box.getCenter(center);

    const fovRad = THREE.MathUtils.degToRad(this._camera.fov);
    const halfHeight = (size.y * padding) / 2;
    const halfWidth = (size.x * padding) / 2;
    const distForHeight = halfHeight / Math.tan(fovRad / 2);
    const distForWidth = halfWidth / (Math.tan(fovRad / 2) * this._camera.aspect);
    const distance = Math.max(distForHeight, distForWidth);

    // 计算垂直偏移后的观测目标点（正值使角色在屏幕中更靠下）
    const target = new THREE.Vector3(center.x, center.y + size.y * verticalOffsetRatio, center.z);

    // 放置到Z轴正方向，略微上移视角
    const eye = new THREE.Vector3(target.x, target.y + size.y * 0.05, target.z + distance);
    this._camera.position.copy(eye);
    this._cameraControls.target.copy(target);
    this._cameraControls.update();
  }

  update = () => {
    if (!this.isReady) return;
    this._animationFrameId = requestAnimationFrame(this.update);
    const delta = this._clock.getDelta();
    // update vrm components
    if (this.model) {
      this.model.update(delta);
    }

    if (this._renderer && this._camera) {
      this._renderer.render(this._scene, this._camera);
    }
  };

  dispose() {
    this.isReady = false;
    this._loadRequestId += 1;

    if (this._animationFrameId !== null) {
      cancelAnimationFrame(this._animationFrameId);
      this._animationFrameId = null;
    }
    if (this._resizeHandler) {
      window.removeEventListener("resize", this._resizeHandler);
      this._resizeHandler = null;
    }

    this.unloadVRM();
    this.model = undefined;
    this._cameraControls?.dispose();
    this._cameraControls = undefined;

    if (this._renderer) {
      this._renderer.dispose();
      this._renderer.forceContextLoss();
      this._renderer = undefined;
    }
    this._camera = undefined;
  }
}
