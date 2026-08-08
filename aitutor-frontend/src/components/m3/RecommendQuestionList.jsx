import { Play, ChevronRight } from 'lucide-react'

const DIFF_COLOR = {
  EASY: 'bg-green-500/20 text-green-300 border-green-400/20',
  MEDIUM: 'bg-amber-500/20 text-amber-300 border-amber-400/20',
  HARD: 'bg-red-500/20 text-red-300 border-red-400/20',
}

const DIFF_LABEL = { EASY: '基础', MEDIUM: '中等', HARD: '提高' }

/**
 * 推荐题目列表
 */
export default function RecommendQuestionList({ items = [], onPractice }) {
  if (!items || items.length === 0) {
    return <p className="text-sm text-purple-200/40 text-center py-6">暂无推荐题目</p>
  }

  return (
    <div className="space-y-2">
      {items.map(item => (
        <div
          key={item.questionId}
          className="flex items-center gap-3 px-3 py-2.5 rounded-xl bg-white/5 border border-white/5 hover:border-purple-400/20 transition-all"
        >
          <div className="flex-1 min-w-0">
            <p className="text-sm text-white/85 leading-snug">{item.questionTitle}</p>
            <div className="flex items-center gap-2 mt-1">
              <span className={`text-[10px] px-1.5 py-0.5 rounded-full border ${DIFF_COLOR[item.difficulty] || 'bg-white/10 text-white/60'}`}>
                {DIFF_LABEL[item.difficulty] || item.difficulty}
              </span>
              <span className="text-[10px] text-purple-200/40">{item.questionType}</span>
              {item.reason && <span className="text-[10px] text-purple-200/40 truncate">· {item.reason}</span>}
            </div>
          </div>
          <button
            onClick={() => onPractice?.(item)}
            className="shrink-0 flex items-center gap-1 text-[11px] px-2.5 py-1.5 rounded-lg bg-gradient-to-r from-purple-500 to-blue-500 text-white hover:opacity-90 transition-all"
          >
            <Play className="w-3 h-3" /> 练习
          </button>
        </div>
      ))}
    </div>
  )
}
