import { useState } from 'react'
import { Sparkles, ChevronRight, ChevronDown, BookOpen, Play, Lightbulb, Clock, TrendingUp, TrendingDown, Minus } from 'lucide-react'

const LEVEL_CONFIG = {
  HIGH: { label: '薄弱', color: '#ff4d4f', bg: 'bg-red-500/20', text: 'text-red-300', border: 'border-red-400/30', bar: 'from-red-500 to-orange-500' },
  MEDIUM: { label: '一般', color: '#faad14', bg: 'bg-amber-500/20', text: 'text-amber-300', border: 'border-amber-400/30', bar: 'from-amber-500 to-yellow-500' },
  LOW: { label: '轻微', color: '#52c41a', bg: 'bg-green-500/20', text: 'text-green-300', border: 'border-green-400/30', bar: 'from-green-500 to-emerald-500' },
}

const TREND_CONFIG = {
  improving: { icon: TrendingUp, label: '改善中', color: '#52c41a' },
  stable: { icon: Minus, label: '稳定', color: '#8c8c8c' },
  declining: { icon: TrendingDown, label: '退步', color: '#ff4d4f' },
}

const SUBJECT_GRAD = {
  数学: 'from-blue-400 to-indigo-500',
  语文: 'from-purple-400 to-fuchsia-500',
  英语: 'from-emerald-400 to-teal-500',
  物理: 'from-amber-400 to-orange-500',
  化学: 'from-rose-400 to-pink-500',
  生物: 'from-green-400 to-emerald-500',
}

export default function WeakPointCard({ item, onDetail, onPractice }) {
  const [expanded, setExpanded] = useState(false)
  const level = LEVEL_CONFIG[item.weaknessLevel] || LEVEL_CONFIG.MEDIUM
  const subjectGrad = SUBJECT_GRAD[item.subject] || 'from-purple-400 to-blue-500'
  const weakRate = item.totalCount > 0 ? (100 - item.accuracyRate) : 0
  const trend = TREND_CONFIG[item.trend]
  const TrendIcon = trend?.icon || TrendingUp

  return (
    <div className="bg-white/10 backdrop-blur-md rounded-2xl border border-white/10 overflow-hidden hover:border-purple-400/30 hover:shadow-xl hover:shadow-purple-500/10 transition-all duration-300 flex flex-col">
      {/* 顶部：名称 + 等级 */}
      <div className="p-4 pb-3">
        <div className="flex items-start justify-between gap-2 mb-3">
          <button
            onClick={() => setExpanded(!expanded)}
            className="flex items-center gap-2 min-w-0 cursor-pointer group"
            title="点击展开详情"
          >
            <span className={`px-1.5 py-0.5 rounded bg-gradient-to-r ${subjectGrad} text-white text-[10px] shrink-0`}>{item.subject}</span>
            <h3 className="text-sm font-semibold text-white truncate group-hover:text-purple-200 transition-colors">{item.knowledgePoint}</h3>
            {expanded ? <ChevronDown className="w-3.5 h-3.5 text-purple-300/60 shrink-0" /> : <ChevronRight className="w-3.5 h-3.5 text-purple-300/60 shrink-0" />}
          </button>
          <div className="flex items-center gap-1.5 shrink-0">
            {trend && (
              <span className="flex items-center gap-1 text-[10px]" style={{ color: trend.color }} title={trend.label}>
                <TrendIcon className="w-3 h-3" />
                {trend.label}
              </span>
            )}
            <span className={`text-[10px] px-2 py-0.5 rounded-full border ${level.bg} ${level.text} ${level.border}`}>
              {level.label}
            </span>
          </div>
        </div>

        {/* 薄弱程度进度条 */}
        <div className="mb-3">
          <div className="flex items-center justify-between text-[11px] mb-1">
            <span className="text-purple-200/50">薄弱程度</span>
            <span className="text-white/70">{Math.round(weakRate)}%</span>
          </div>
          <div className="h-2 rounded-full bg-white/10 overflow-hidden">
            <div
              className={`h-full rounded-full bg-gradient-to-r ${level.bar} transition-all duration-700`}
              style={{ width: `${Math.max(8, weakRate)}%` }}
            />
          </div>
        </div>

        {/* 数据 */}
        <div className="grid grid-cols-3 gap-2 text-center mb-3">
          <div className="bg-white/5 rounded-lg py-1.5">
            <p className="text-sm font-semibold text-white">{item.errorCount}</p>
            <p className="text-[10px] text-purple-200/40">错题</p>
          </div>
          <div className="bg-white/5 rounded-lg py-1.5">
            <p className="text-sm font-semibold text-white">{item.totalCount}</p>
            <p className="text-[10px] text-purple-200/40">做题</p>
          </div>
          <div className="bg-white/5 rounded-lg py-1.5">
            <p className="text-sm font-semibold text-white">{item.accuracyRate.toFixed(0)}%</p>
            <p className="text-[10px] text-purple-200/40">正确率</p>
          </div>
        </div>

        {/* AI 建议 */}
        {(item.aiSuggestion || item.aiAnalysis) && (
          <div className="bg-gradient-to-r from-purple-500/10 to-blue-500/10 rounded-lg p-2.5 border border-purple-400/10 mb-3">
            <div className="flex items-start gap-1.5">
              <Lightbulb className="w-3.5 h-3.5 text-amber-300 mt-0.5 shrink-0" />
              <p className="text-[11px] text-purple-100/70 leading-relaxed line-clamp-2">
                {item.aiSuggestion || item.aiAnalysis}
              </p>
            </div>
          </div>
        )}

        {/* 展开详情 */}
        {expanded && (
          <div className="mb-3 space-y-2 animate-fadeIn">
            {/* 子知识点 */}
            {item.subWeakPoints && item.subWeakPoints.length > 0 ? (
              <div>
                <p className="text-[11px] text-purple-200/50 mb-1">关联子知识点</p>
                <div className="flex flex-wrap gap-1.5">
                  {item.subWeakPoints.map(sp => {
                    const spLevel = LEVEL_CONFIG[sp.weaknessLevel] || LEVEL_CONFIG.MEDIUM
                    return (
                      <span key={sp.kpId || sp.name} className={`text-[10px] px-2 py-0.5 rounded-full border ${spLevel.bg} ${spLevel.text} ${spLevel.border}`}>
                        {sp.name || sp.knowledgePoint}
                      </span>
                    )
                  })}
                </div>
              </div>
            ) : (
              <div className="bg-white/5 rounded-lg p-2.5 border border-white/5">
                <p className="text-[11px] text-purple-100/60 leading-relaxed">
                  {item.aiAnalysis || '暂无更多分析，点击「查看详情」查看完整信息'}
                </p>
              </div>
            )}

            {/* 最近错题时间 */}
            {item.lastErrorTime && (
              <div className="flex items-center gap-1.5 text-[11px] text-purple-200/40">
                <Clock className="w-3 h-3" />
                最近错题时间：{item.lastErrorTime.slice(0, 16).replace('T', ' ')}
              </div>
            )}
          </div>
        )}
      </div>

      {/* 操作按钮 */}
      <div className="px-4 pb-4 mt-auto grid grid-cols-2 gap-2">
        <button
          onClick={onDetail}
          className="flex items-center justify-center gap-1.5 px-2 py-2 rounded-xl bg-white/10 border border-white/10 text-white/80 text-xs hover:bg-purple-500/20 hover:text-purple-100 hover:border-purple-400/30 transition-all"
        >
          <BookOpen className="w-3.5 h-3.5" /> 查看详情
        </button>
        <button
          onClick={onPractice}
          className="flex items-center justify-center gap-1.5 px-2 py-2 rounded-xl bg-gradient-to-r from-purple-500 to-blue-500 text-white text-xs hover:opacity-90 transition-all"
        >
          <Play className="w-3.5 h-3.5" /> 去练习
        </button>
      </div>
    </div>
  )
}
