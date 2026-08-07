/**
 * 做题页 —— 题目导航侧边栏
 * 显示所有题号，可点击跳转
 */
import { Check, X, Minus } from "lucide-react";

export default function QuestionNav({
  total,
  currentIndex,
  answerStatuses,
  onJump,
}) {
  return (
    <div className="bg-white rounded-2xl shadow-sm border border-slate-100 p-4">
      <h3 className="text-sm font-semibold text-slate-500 mb-3">题目导航</h3>
      <div className="grid grid-cols-5 gap-2">
        {Array.from({ length: total }, (_, i) => {
          const status = answerStatuses[i];
          let bg = "bg-slate-100 text-slate-500 hover:bg-slate-200";
          let icon = null;
          if (status === "correct") {
            bg = "bg-emerald-50 text-emerald-600 border border-emerald-200";
            icon = <Check size={12} />;
          }
          if (status === "wrong") {
            bg = "bg-red-50 text-red-500 border border-red-200";
            icon = <X size={12} />;
          }
          if (i === currentIndex) {
            bg = "bg-indigo-500 text-white shadow-sm";
          }

          return (
            <button
              key={i}
              onClick={() => onJump(i)}
              className={`w-9 h-9 rounded-lg flex items-center justify-center text-xs font-bold transition-all duration-200 cursor-pointer
                ${bg} ${i === currentIndex ? "ring-2 ring-indigo-300" : ""}`}
            >
              {icon || i + 1}
            </button>
          );
        })}
      </div>
    </div>
  );
}
