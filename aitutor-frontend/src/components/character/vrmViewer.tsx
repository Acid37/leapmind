import { useContext, useCallback, useRef, useEffect } from "react";
import { ViewerContext } from "@/features/vrmViewer/viewerContext";
import { buildUrl } from "@/utils/buildUrl";

export default function VrmViewer() {
  const { viewer } = useContext(ViewerContext);
  const containerRef = useRef<HTMLDivElement>(null);
  const hasSetup = useRef(false);

  // 容器尺寸变化时重新拟合相机
  useEffect(() => {
    const container = containerRef.current;
    if (!container) return;

    const ro = new ResizeObserver(() => {
      if (viewer._renderer) {
        viewer.resize();
        viewer.fitToContainer?.();
      }
    });
    ro.observe(container);
    return () => ro.disconnect();
  }, [viewer]);

  const canvasRef = useCallback(
    (canvas: HTMLCanvasElement) => {
      if (!canvas || hasSetup.current) return;
      hasSetup.current = true;

      // 等容器有实际尺寸后再初始化
      const init = () => {
        const w = containerRef.current?.clientWidth || 0;
        const h = containerRef.current?.clientHeight || 0;
        if (w === 0 || h === 0) {
          requestAnimationFrame(init);
          return;
        }
        viewer.setup(canvas);
        viewer.loadVrm(buildUrl("/vrm/teacher003_girl.vrm"));
      };
      requestAnimationFrame(init);

      // Drag and DropでVRMを差し替え
      canvas.addEventListener("dragover", function (event) {
        event.preventDefault();
      });

      canvas.addEventListener("drop", function (event) {
        event.preventDefault();
        const files = event.dataTransfer?.files;
        if (!files) return;
        const file = files[0];
        if (!file) return;
        const file_type = file.name.split(".").pop();
        if (file_type === "vrm") {
          const blob = new Blob([file], { type: "application/octet-stream" });
          const url = window.URL.createObjectURL(blob);
          viewer.loadVrm(url);
        }
      });
    },
    [viewer]
  );

  return (
    <div ref={containerRef} className={"relative w-full h-full"}>
      <canvas ref={canvasRef} className={"h-full w-full"}></canvas>
    </div>
  );
}
