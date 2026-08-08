import { BookOpen, MessageCircle, Trash2, ChevronRight } from 'lucide-react'

const SUBJECT_LABELS = {
  math: '数学', chinese: '语文', english: '英语',
  physics: '物理', chemistry: '化学', biology: '生物'
}

const SUBJECT_COLORS = {
  math: 'from-blue-500/20 to-cyan-500/20 text-blue-300 border-blue-400/20',
  english: 'from-pink-500/20 to-rose-500/20 text-pink-300 border-pink-400/20',
  physics: 'from-emerald-500/20 to-teal-500/20 text-emerald-300 border-emerald-400/20',
}

export default function ExplainCard({ item, onReplay, onDelete }) {
  return (
    <div className="group bg-white/10 backdrop-blur-md rounded-xl border border-white/10 hover:border-purple-400/30 transition-all">
      <button
        onClick={() => onReplay?.(item.id)}
        className="w-full text-left p-4"
      >
        <div className="flex items-start justify-between gap-3 mb-2">
          <div className="flex-1 min-w-0">
            <div className="flex items-center gap-2 mb-1.5">
              <MessageCircle className="w-3.5 h-3.5 text-purple-300 shrink-0" />
              <span className={`text-[10px] px-2 py-0.5 rounded-full border ${SUBJECT_COLORS[item.subject] || 'bg-white/10 text-white/60 border-white/10'}`}>
                {SUBJECT_LABELS[item.subject] || item.subject}
              </span>
            </div>
            <p className="text-sm text-white/80 leading-relaxed line-clamp-2">{item.questionSummary}</p>
          </div>
          <ChevronRight className="w-4 h-4 text-white/20 group-hover:text-white/50 transition-colors shrink-0 mt-1" />
        </div>
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-1.5 flex-wrap">
            {item.knowledgePoints?.map(kp => (
              <span key={kp.id} className="text-[10px] px-2 py-0.5 rounded-full bg-gradient-to-r from-amber-500/15 to-orange-500/15 text-amber-300/80 border border-amber-400/10">
                {kp.name}
              </span>
            ))}
          </div>
          <span className="text-[10px] text-white/30 shrink-0 ml-2">{item.createdAt?.slice(0, 10)}</span>
        </div>
      </button>
      <div className="px-4 pb-3 flex justify-end gap-2 border-t border-white/5 pt-2">
        <button
          onClick={() => onReplay?.(item.id)}
          className="text-[11px] px-3 py-1 rounded-lg bg-purple-500/15 text-purple-300 hover:bg-purple-500/25 transition-all flex items-center gap-1"
        >
          <BookOpen className="w-3 h-3" /> 查看回放
        </button>
        <button
          onClick={() => onDelete?.(item.id)}
          className="text-[11px] px-3 py-1 rounded-lg bg-red-500/10 text-red-300/70 hover:bg-red-500/20 hover:text-red-300 transition-all flex items-center gap-1"
        >
          <Trash2 className="w-3 h-3" /> 删除
        </button>
      </div>
    </div>
  )
}
