import { useEffect, useRef, useState } from 'react';
import { Viewer } from '@/features/vrmViewer/viewer.js';
import { buildUrl } from '@/utils/buildUrl.js';

export default function VirtualTeacherViewer({
  modelUrl,
  viewer: externalViewer,
  className = '',
  onReady,
  onError,
}) {
  const canvasRef = useRef(null);
  const viewerRef = useRef(externalViewer || new Viewer());
  const [status, setStatus] = useState('loading');

  useEffect(() => {
    const viewer = viewerRef.current;
    const canvas = canvasRef.current;
    if (!canvas || !modelUrl) return undefined;

    let active = true;
    setStatus('loading');

    try {
      viewer.setup(canvas);
      viewer
        .loadVrm(buildUrl(modelUrl))
        .then(() => {
          if (!active) return;
          setStatus('ready');
          onReady?.(viewer);
        })
        .catch((error) => {
          if (!active) return;
          setStatus('error');
          onError?.(error);
        });
    } catch (error) {
      setStatus('error');
      onError?.(error);
    }

    return () => {
      active = false;
      viewer.dispose();
    };
  }, [modelUrl, onError, onReady]);

  return (
    <div className={`relative h-full w-full overflow-hidden ${className}`}>
      <canvas
        ref={canvasRef}
        className="h-full w-full"
        aria-label="VRM 虚拟教师三维预览"
      />
      {status === 'loading' && (
        <div className="absolute inset-0 grid place-items-center bg-indigo-950/25 text-sm text-white/80 backdrop-blur-sm">
          <span className="rounded-full border border-white/20 bg-white/10 px-4 py-2">正在加载虚拟教师…</span>
        </div>
      )}
      {status === 'error' && (
        <div className="absolute inset-0 grid place-items-center bg-indigo-950/50 px-6 text-center text-sm text-rose-100">
          当前设备无法加载 3D 教师，请检查 WebGL 或模型资源。
        </div>
      )}
    </div>
  );
}
