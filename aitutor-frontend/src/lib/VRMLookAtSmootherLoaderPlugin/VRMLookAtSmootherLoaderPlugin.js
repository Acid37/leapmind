import { VRMLookAtLoaderPlugin } from "@pixiv/three-vrm";
import { VRMLookAtSmoother } from "./VRMLookAtSmoother.js";

export class VRMLookAtSmootherLoaderPlugin extends VRMLookAtLoaderPlugin {
  get name() {
    return "VRMLookAtSmootherLoaderPlugin";
  }

  async afterRoot(gltf) {
    await super.afterRoot(gltf);

    const humanoid = gltf.userData.vrmHumanoid;
    const lookAt = gltf.userData.vrmLookAt;

    if (humanoid != null && lookAt != null) {
      const lookAtSmoother = new VRMLookAtSmoother(humanoid, lookAt.applier);
      lookAtSmoother.copy(lookAt);
      gltf.userData.vrmLookAt = lookAtSmoother;
    }
  }
}