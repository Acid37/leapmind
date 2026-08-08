import { X, BookOpen, ChevronRight } from 'lucide-react'

const LEVEL_CONFIG = {
  HIGH: { label: '薄弱', color: '#ff4d4f', bg: 'bg-red-500/20', text: 'text-red-300', border: 'border-red-400/30' },
  MEDIUM: { label: '一般', color: '#faad14', bg: 'bg-amber-500/20', text: 'text-amber-300', border: 'border-amber-400/30' },
  LOW: { label: '轻微', color: '#52c41a', bg: 'bg-green-500/20', text: 'text-green-300', border: 'border-green-400/30' },
  MASTERED: { label: '掌握', color: '#1890ff', bg: 'bg-blue-500/20', text: 'text-blue-300', border: 'border-blue-400/30' },
  UNKNOWN: { label: '未学习', color: '#8c8c8c', bg: 'bg-white/10', text: 'text-white/60', border: 'border-white/15' },
}

/**
 * 知识图谱节点详情弹窗
 * 展示节点基本信息 + 前置/后置关系
 */
export default function NodeDetailModal({ node, edges, onClose, onViewDetail }) {
  if (!node) return null

  const level = LEVEL_CONFIG[node.weaknessLevel] || LEVEL_CONFIG.UNKNOWN
  const nodeId = String(node.id ?? node.name)

  // 找出与该节点相连的边
  const relatedEdges = (edges || []).filter(
    e => String(e.source) === nodeId || String(e.target) === nodeId
  )

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm" onClick={onClose}>
      <div
        className="bg-gradient-to-br from-[#6B1CCF] to-[#4A0E8F] rounded-2xl p-6 w-full max-w-md mx-4 border border-purple-400/20 shadow-2xl"
        onClick={(e) => e.stopPropagation()}
      >
        {/* 头部 */}
        <div className="flex items-start justify-between mb-4">
          <div className="flex items-center gap-3">
            <div className="w-12 h-12 rounded-xl flex items-center justify-center text-2xl font-bold" style={{ backgroundColor: level.color + '22', color: level.color }}>
              {node.name?.[0]}
            </div>
            <div>
              <h3 className="text-white font-semibold text-lg">{node.name}</h3>
              <span className={`text-[10px] px-2 py-0.5 rounded-full border ${level.bg} ${level.text} ${level.border}`}>
                {level.label}
              </span>
            </div>
          </div>
          <button onClick={onClose} className="p-1 rounded-lg hover:bg-white/10 text-white/60">
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* 信息 */}
        <div className="grid grid-cols-2 gap-3 mb-4">
          <div className="bg-white/5 rounded-xl p-3 text-center">
            <p className="text-xl font-bold text-white">{(node.masteryRate ?? 0).toFixed(0)}%</p>
            <p className="text-[11px] text-purple-200/40">掌握度</p>
          </div>
          <div className="bg-white/5 rounded-xl p-3 text-center">
            <p className="text-xl font-bold text-white truncate">{node.group || node.subject || '—'}</p>
            <p className="text-[11px] text-purple-200/40">学科</p>
          </div>
        </div>

        {/* 关联关系 */}
        {relatedEdges.length > 0 && (
          <div className="bg-white/5 rounded-xl p-3 mb-4">
            <p className="text-[11px] text-purple-200/50 mb-2">知识点关联（{relatedEdges.length}）</p>
            <div className="space-y-1.5 max-h-28 overflow-y-auto">
              {relatedEdges.map((e, i) => {
                const isSource = String(e.source) === nodeId
                const other = isSource ? e.target : e.source
                return (
                  <div key={i} className="flex items-center gap-2 text-xs text-purple-100/70">
                    <span className={`px-1.5 py-0.5 rounded text-[10px] ${isSource ? 'bg-amber-500/20 text-amber-200' : 'bg-sky-500/20 text-sky-200'}`}>
                      {isSource ? '前置' : '后置'}
                    </span>
                    <span className="truncate">{other}</span>
                  </div>
                )
              })}
            </div>
          </div>
        )}

        {/* 操作 */}
        <button
          onClick={() => onViewDetail?.(node)}
          className="w-full py-2.5 rounded-xl bg-gradient-to-r from-purple-500 to-blue-500 text-white text-sm hover:opacity-90 transition-all flex items-center justify-center gap-1.5"
        >
          <BookOpen className="w-4 h-4" />
          查看薄弱点详情
          <ChevronRight className="w-4 h-4" />
        </button>
      </div>
    </div>
  )
}
