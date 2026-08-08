import { XCircle, ChevronRight } from 'lucide-react'

/**
 * 错误题目预览列表（点击跳转 M2 讲题列表模式）
 */
export default function ErrorQuestionList({ items = [], onExplain }) {
  if (!items || items.length === 0) {
    return <p className="text-sm text-purple-200/40 text-center py-6">暂无错误记录</p>
  }

  return (
    <div className="space-y-2">
      {items.map(item => (
        <button
          key={item.id}
          onClick={() => onExplain?.(item)}
          className="w-full text-left flex items-center gap-3 px-3 py-2.5 rounded-xl bg-white/5 border border-white/5 hover:bg-white/10 hover:border-purple-400/20 transition-all group"
        >
          <XCircle className="w-4 h-4 text-red-400 shrink-0" />
          <div className="flex-1 min-w-0">
            <p className="text-sm text-white/80 truncate">练习 {item.exerciseId}</p>
            <p className="text-[11px] text-purple-200/40">{(item.completedAt || '').slice(0, 10)} · 答错</p>
          </div>
          <span className="text-[11px] text-purple-300/70 shrink-0 opacity-0 group-hover:opacity-100 transition-opacity flex items-center gap-1">
            去讲题 <ChevronRight className="w-3 h-3" />
          </span>
        </button>
      ))}
    </div>
  )
}
