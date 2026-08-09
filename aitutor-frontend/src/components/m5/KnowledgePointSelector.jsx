import { useState, useEffect, useCallback } from 'react'
import { Search, X, BookOpen, ChevronDown } from 'lucide-react'
import { mockGetKnowledgePoints } from '../../services/m5'

const SUBJECT_MAP = {
  math: '数学',
  chinese: '语文',
  english: '英语',
  physics: '物理',
  chemistry: '化学',
  biology: '生物',
}

export default function KnowledgePointSelector({ subject, value = [], onChange }) {
  const [allPoints, setAllPoints] = useState([])
  const [search, setSearch] = useState('')
  const [loading, setLoading] = useState(false)
  const [expanded, setExpanded] = useState(true)

  useEffect(() => {
    if (!subject) return
    const load = async () => {
      setLoading(true)
      try {
        const data = await mockGetKnowledgePoints(subject)
        setAllPoints(data)
      } catch (err) {
        console.error('加载知识点失败:', err)
      }
      setLoading(false)
    }
    load()
  }, [subject])

  const filtered = search
    ? allPoints.filter(p => p.name.includes(search))
    : allPoints

  const togglePoint = (point) => {
    const exists = value.find(v => v.id === point.id)
    if (exists) {
      onChange(value.filter(v => v.id !== point.id))
    } else {
      onChange([...value, point])
    }
  }

  const removePoint = (pointId) => {
    onChange(value.filter(v => v.id !== pointId))
  }

  return (
    <div className="bg-white/10 backdrop-blur-md rounded-2xl p-5 border border-white/10">
      <button
        onClick={() => setExpanded(!expanded)}
        className="w-full flex items-center justify-between mb-3"
      >
        <div className="flex items-center gap-2">
          <BookOpen className="w-5 h-5 text-purple-300" />
          <h3 className="text-base font-semibold text-white">选择知识点</h3>
        </div>
        <ChevronDown className={`w-4 h-4 text-purple-300/50 transition-transform ${expanded ? 'rotate-180' : ''}`} />
      </button>

      {/* 已选标签 */}
      {value.length > 0 && (
        <div className="flex flex-wrap gap-1.5 mb-3 pb-3 border-b border-white/5">
          {value.map(point => (
            <span
              key={point.id}
              className="inline-flex items-center gap-1 text-xs px-2.5 py-1 rounded-full bg-gradient-to-r from-purple-500/30 to-blue-500/30 text-purple-100 border border-purple-400/20"
            >
              {point.name}
              <button onClick={() => removePoint(point.id)} className="hover:text-white transition-colors">
                <X className="w-3 h-3" />
              </button>
            </span>
          ))}
        </div>
      )}

      {expanded && (
        <>
          {/* 搜索框 */}
          <div className="relative mb-3">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-purple-300/40" />
            <input
              type="text"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="搜索知识点..."
              className="w-full bg-white/5 border border-white/10 rounded-xl pl-9 pr-4 py-2.5 text-sm text-white placeholder-purple-200/30 outline-none focus:border-purple-400/40 focus:bg-white/10 transition-all"
            />
          </div>

          {/* 知识点列表 */}
          <div className="max-h-52 overflow-y-auto space-y-1 pr-1 scrollbar-thin">
            {loading ? (
              <div className="flex items-center justify-center py-6">
                <div className="w-6 h-6 border-2 border-purple-300/30 border-t-purple-400 rounded-full animate-spin" />
              </div>
            ) : filtered.length === 0 ? (
              <p className="text-sm text-purple-200/40 text-center py-6">
                {subject ? '没有匹配的知识点' : '请先选择科目'}
              </p>
            ) : (
              filtered.map(point => {
                const selected = value.some(v => v.id === point.id)
                return (
                  <div
                    key={point.id}
                    role="button"
                    tabIndex={0}
                    onClick={() => togglePoint(point)}
                    onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); togglePoint(point) } }}
                    className={`
                      flex items-center gap-3 px-3 py-2.5 rounded-xl cursor-pointer transition-all select-none
                      ${selected
                        ? 'bg-purple-500/20 border border-purple-400/30'
                        : 'bg-white/5 border border-transparent hover:bg-white/10'
                      }
                    `}
                  >
                    <div
                      className={`w-4 h-4 rounded-md border-2 flex items-center justify-center transition-all shrink-0 ${
                        selected ? 'bg-purple-400 border-purple-400' : 'border-white/30'
                      }`}
                    >
                      {selected && (
                        <svg className="w-3 h-3 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="3" d="M5 13l4 4L19 7" />
                        </svg>
                      )}
                    </div>
                    <div className="flex-1 min-w-0">
                      <p className="text-sm text-white/90 truncate">{point.name}</p>
                      <p className="text-[10px] text-purple-200/40">{SUBJECT_MAP[point.subject] || point.subject} · {point.grade}</p>
                    </div>
                  </div>
                )
              })
            )}
          </div>
        </>
      )}
    </div>
  )
}
