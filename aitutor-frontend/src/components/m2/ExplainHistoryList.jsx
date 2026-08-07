import { BookOpen, ChevronLeft, ChevronRight } from 'lucide-react'
import ExplainCard from './ExplainCard'

export default function ExplainHistoryList({ items, total, page, pageSize, onPageChange, onReplay, onDelete }) {
  const totalPages = Math.ceil(total / pageSize)

  if (!items || items.length === 0) {
    return (
      <div className="text-center py-16 text-purple-200/50">
        <BookOpen className="w-12 h-12 mx-auto mb-3 opacity-30" />
        <p className="text-sm">暂无讲题记录</p>
        <p className="text-xs text-purple-200/30 mt-1">去拍照答疑或做几道题，讲题记录会出现在这里</p>
      </div>
    )
  }

  return (
    <div>
      <div className="space-y-3">
        {items.map(item => (
          <ExplainCard key={item.id} item={item} onReplay={onReplay} onDelete={onDelete} />
        ))}
      </div>
      {totalPages > 1 && (
        <div className="mt-4 flex items-center justify-center gap-3">
          <button
            onClick={() => onPageChange(page - 1)}
            disabled={page <= 1}
            className="w-8 h-8 rounded-lg bg-white/10 border border-white/10 flex items-center justify-center text-white/60 hover:bg-white/20 hover:text-white disabled:opacity-30 disabled:cursor-not-allowed transition-all"
          >
            <ChevronLeft className="w-4 h-4" />
          </button>
          <div className="flex items-center gap-1.5">
            {Array.from({ length: totalPages }, (_, i) => i + 1).map(p => (
              <button
                key={p}
                onClick={() => onPageChange(p)}
                className={`w-8 h-8 rounded-lg text-xs font-medium transition-all ${
                  p === page
                    ? 'bg-gradient-to-r from-purple-500 to-blue-500 text-white shadow-sm'
                    : 'bg-white/5 text-white/50 hover:bg-white/15'
                }`}
              >
                {p}
              </button>
            ))}
          </div>
          <button
            onClick={() => onPageChange(page + 1)}
            disabled={page >= totalPages}
            className="w-8 h-8 rounded-lg bg-white/10 border border-white/10 flex items-center justify-center text-white/60 hover:bg-white/20 hover:text-white disabled:opacity-30 disabled:cursor-not-allowed transition-all"
          >
            <ChevronRight className="w-4 h-4" />
          </button>
        </div>
      )}
    </div>
  )
}
