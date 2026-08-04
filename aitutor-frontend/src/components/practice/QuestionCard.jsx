/**
 * 做题页 —— 题目卡片组件
 * 显示题干、选项（单选/多选/填空/简答）
 */
import { Check, X, Lightbulb, BookOpen } from "lucide-react";

export default function QuestionCard({
  question,
  selectedAnswer,
  onSelectAnswer,
  isSubmitted,
  isCorrect,
}) {
  if (!question) return null;

  const { content, type } = question;

  // 渲染选项
  const renderOptions = () => {
    if (type === "single_choice" || type === "multi_choice") {
      return (
        <div className="mt-5 space-y-2.5">
          {content.options.map((opt, idx) => {
            const label = String.fromCharCode(65 + idx); // A, B, C, D
            const isSelected =
              type === "single_choice"
                ? selectedAnswer?.selected === label
                : selectedAnswer?.selected?.includes(label);
            const isCorrectOption = isSubmitted && question.correctAnswer === label;
            const isWrongSelected = isSubmitted && isSelected && !isCorrectOption;

            let borderColor = "border-slate-200 hover:border-indigo-400";
            let bgColor = "bg-white";
            if (!isSubmitted && isSelected) {
              borderColor = "border-indigo-500";
              bgColor = "bg-indigo-50";
            }
            if (isSubmitted && isCorrectOption) {
              borderColor = "border-emerald-500";
              bgColor = "bg-emerald-50";
            }
            if (isWrongSelected) {
              borderColor = "border-red-400";
              bgColor = "bg-red-50";
            }

            return (
              <button
                key={idx}
                disabled={isSubmitted}
                onClick={() => {
                  if (type === "single_choice") {
                    onSelectAnswer({ selected: label });
                  } else {
                    // 多选：toggle 当前选项
                    const prev = selectedAnswer?.selected || [];
                    const next = prev.includes(label)
                      ? prev.filter((x) => x !== label)
                      : [...prev, label];
                    onSelectAnswer({ selected: next });
                  }
                }}
                className={`w-full flex items-center gap-3 p-3.5 rounded-xl border-2 text-left transition-all duration-200 cursor-pointer
                  ${borderColor} ${bgColor}
                  ${isSubmitted ? "cursor-default" : "hover:shadow-sm active:scale-[0.99]"}`}
              >
                <span
                  className={`flex-shrink-0 w-8 h-8 rounded-full flex items-center justify-center text-sm font-bold
                    ${isCorrectOption ? "bg-emerald-500 text-white" : ""}
                    ${isWrongSelected ? "bg-red-400 text-white" : ""}
                    ${!isSubmitted && isSelected ? "bg-indigo-500 text-white" : ""}
                    ${!isSubmitted && !isSelected ? "bg-slate-100 text-slate-600" : ""}`}
                >
                  {isCorrectOption ? <Check size={16} /> : isWrongSelected ? <X size={16} /> : label}
                </span>
                <span className="text-base text-slate-700">{opt.replace(/^[A-D]\.\s*/, "")}</span>
              </button>
            );
          })}
        </div>
      );
    }

    if (type === "fill_blank") {
      return (
        <div className="mt-5">
          <input
            type="text"
            disabled={isSubmitted}
            value={selectedAnswer?.text || ""}
            onChange={(e) => onSelectAnswer({ text: e.target.value })}
            placeholder="请输入你的答案..."
            className={`w-full p-3.5 rounded-xl border-2 text-base outline-none transition-colors
              ${isSubmitted && isCorrect ? "border-emerald-500 bg-emerald-50" : ""}
              ${isSubmitted && !isCorrect ? "border-red-400 bg-red-50" : ""}
              ${!isSubmitted ? "border-slate-200 focus:border-indigo-500 bg-white" : ""}`}
          />
        </div>
      );
    }

    if (type === "short_answer") {
      return (
        <div className="mt-5">
          <textarea
            disabled={isSubmitted}
            value={selectedAnswer?.text || ""}
            onChange={(e) => onSelectAnswer({ text: e.target.value })}
            placeholder="请输入你的解答过程..."
            rows={3}
            className={`w-full p-3.5 rounded-xl border-2 text-base outline-none resize-none transition-colors
              ${isSubmitted && isCorrect ? "border-emerald-500 bg-emerald-50" : ""}
              ${isSubmitted && !isCorrect ? "border-red-400 bg-red-50" : ""}
              ${!isSubmitted ? "border-slate-200 focus:border-indigo-500 bg-white" : ""}`}
          />
        </div>
      );
    }

    return null;
  };

  return (
    <div className="bg-white rounded-2xl shadow-sm border border-slate-100 p-6">
      {/* 题型 & 难度标签 */}
      <div className="flex items-center gap-2 mb-4">
        <span className="px-2.5 py-1 rounded-full text-xs font-medium bg-indigo-50 text-indigo-600">
          {type === "single_choice"
            ? "单选题"
            : type === "multi_choice"
              ? "多选题"
              : type === "fill_blank"
                ? "填空题"
                : "简答题"}
        </span>
        <span className="px-2.5 py-1 rounded-full text-xs font-medium bg-amber-50 text-amber-600">
          {"★".repeat(question.difficulty)}
        </span>
        {question.chapter && (
          <span className="px-2.5 py-1 rounded-full text-xs font-medium bg-slate-100 text-slate-500">
            {question.chapter}
          </span>
        )}
      </div>

      {/* 题干 */}
      <div className="text-lg text-slate-800 leading-relaxed font-medium">
        {content.stem}
      </div>

      {/* 配图（如有） */}
      {content.images?.length > 0 && (
        <div className="mt-4 flex gap-3">
          {content.images.map((img, i) => (
            <img
              key={i}
              src={img}
              alt={`题目配图 ${i + 1}`}
              className="max-w-full rounded-xl border border-slate-100"
            />
          ))}
        </div>
      )}

      {/* 选项 / 填空 / 简答 */}
      {renderOptions()}

      {/* 提交后：解析面板 */}
      {isSubmitted && question.explanation && (
        <div className="mt-5 p-4 rounded-xl border border-slate-200 bg-slate-50">
          <div className="flex items-center gap-2 mb-3">
            <Lightbulb size={18} className="text-amber-500" />
            <span className="font-semibold text-slate-700">
              {isCorrect ? "回答正确！" : "答案解析"}
            </span>
          </div>
          <p className="text-slate-600 text-sm leading-relaxed mb-3">
            {question.explanation}
          </p>

          {/* 解题步骤 */}
          {question.answerSteps?.length > 0 && (
            <div className="mt-3 space-y-1.5">
              <span className="text-xs font-semibold text-slate-500 uppercase tracking-wide">
                解题步骤
              </span>
              {question.answerSteps.map((step, i) => (
                <div key={i} className="flex items-start gap-2 text-sm text-slate-600">
                  <span className="flex-shrink-0 w-5 h-5 rounded-full bg-indigo-100 text-indigo-600 text-xs flex items-center justify-center font-bold mt-0.5">
                    {i + 1}
                  </span>
                  <span>{step}</span>
                </div>
              ))}
            </div>
          )}

          {/* 知识点标签 */}
          {question.knowledgePoints?.length > 0 && (
            <div className="mt-3 flex items-center gap-2 flex-wrap">
              <BookOpen size={14} className="text-slate-400" />
              {question.knowledgePoints.map((kp) => (
                <span
                  key={kp.id}
                  className="px-2 py-0.5 rounded-md text-xs font-medium bg-indigo-50 text-indigo-600"
                >
                  {kp.name}
                </span>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  );
}
