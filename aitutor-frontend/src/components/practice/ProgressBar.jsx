/**
 * 做题页 —— 进度条组件
 * 显示当前进度 + 正确/错误/未答统计
 */
import { Check, X, Minus } from "lucide-react";

export default function ProgressBar({ current, total, answerStatuses }) {
  const correctCount = answerStatuses.filter((s) => s === "correct").length;
  const wrongCount = answerStatuses.filter((s) => s === "wrong").length;

  return (
    <div className="w-full">
      {/* 进度文字 */}
      <div className="flex items-center justify-between mb-2">
        <span className="text-sm font-semibold text-slate-600">
          第 {current} / {total} 题
        </span>
        <div className="flex items-center gap-3 text-xs">
          <span className="flex items-center gap-1 text-emerald-600">
            <Check size={14} /> {correctCount}
          </span>
          <span className="flex items-center gap-1 text-red-400">
            <X size={14} /> {wrongCount}
          </span>
          <span className="flex items-center gap-1 text-slate-400">
            <Minus size={14} /> {total - correctCount - wrongCount}
          </span>
        </div>
      </div>

      {/* 进度条本体 */}
      <div className="flex gap-1">
        {answerStatuses.map((status, i) => {
          let bg = "bg-slate-200";
          if (status === "correct") bg = "bg-emerald-400";
          if (status === "wrong") bg = "bg-red-400";
          if (i === current - 1 && !status) bg = "bg-indigo-400";

          return (
            <div
              key={i}
              title={`第${i + 1}题`}
              className={`flex-1 h-2 rounded-full transition-colors duration-300 ${bg}`}
            />
          );
        })}
      </div>
    </div>
  );
}
