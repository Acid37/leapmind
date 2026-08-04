/**
 * 做题页 —— 计时器组件
 * 每道题单独计时
 */
import { useState, useEffect, useRef } from "react";
import { Clock } from "lucide-react";

export default function Timer({ isRunning, onTick, resetKey }) {
  const [seconds, setSeconds] = useState(0);
  const intervalRef = useRef(null);
  const onTickRef = useRef(onTick);
  onTickRef.current = onTick; // 始终保持最新回调

  useEffect(() => {
    setSeconds(0);
  }, [resetKey]);

  useEffect(() => {
    if (isRunning) {
      intervalRef.current = setInterval(() => {
        setSeconds((prev) => {
          const next = prev + 1;
          onTickRef.current?.(next);
          return next;
        });
      }, 1000);
    } else {
      clearInterval(intervalRef.current);
    }
    return () => clearInterval(intervalRef.current);
  }, [isRunning]);

  const formatTime = (s) => {
    const mm = Math.floor(s / 60);
    const ss = s % 60;
    return `${mm.toString().padStart(2, "0")}:${ss.toString().padStart(2, "0")}`;
  };

  return (
    <div className="flex items-center gap-1.5 text-sm text-slate-400">
      <Clock size={15} />
      <span className="font-mono tabular-nums">{formatTime(seconds)}</span>
    </div>
  );
}
