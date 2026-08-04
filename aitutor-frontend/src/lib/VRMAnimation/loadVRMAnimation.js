import { GLTFLoader } from 'three/examples/jsm/loaders/GLTFLoader';
import { VRMAnimation } from './VRMAnimation';
import { VRMAnimationLoaderPlugin } from './VRMAnimationLoaderPlugin';

const loader = new GLTFLoader();
loader.register((parser) => new VRMAnimationLoaderPlugin(parser));

export async function loadVRMAnimation(url) {
  const gltf = await loader.loadAsync(url);

  const vrmAnimations = gltf.userData.vrmAnimations;
  const vrmAnimation = vrmAnimations[0];

  return vrmAnimation ?? null;
}