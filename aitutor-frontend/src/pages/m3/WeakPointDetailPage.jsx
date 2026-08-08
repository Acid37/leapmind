import { useState, useEffect, useCallback } from 'react'
import { ArrowLeft, Lightbulb, Loader2, Play, TrendingUp, TrendingDown, Minus } from 'lucide-react'
import { getUserInfo } from '../../utils/tokenManager'
import {
  getWeakPointDetail, getRecommendQuestions, getKnowledgeGraph,
} from '../../services/m3'
import WeaknessTrendChart from '../../components/m3/WeaknessTrendChart'
import KpKnowledgeGraph from '../../components/m3/KpKnowledgeGraph'
import ErrorQuestionList from '../../components/m3/ErrorQuestionList'
import RecommendQuestionList from '../../components/m3/RecommendQuestionList'

const LEVEL_CONFIG = {
  HIGH: { label: '薄弱', color: '#ff4d4f', bg: 'bg-red-500/20', text: 'text-red-300', border: 'border-red-400/30' },
  MEDIUM: { label: '一般', color: '#faad14', bg: 'bg-amber-500/20', text: 'text-amber-300', border: 'border-amber-400/30' },
  LOW: { label: '轻微', color: '#52c41a', bg: 'bg-green-500/20', text: 'text-green-300', border: 'border-green-400/30' },
}

const TREND_CONFIG = {
  improving: { icon: TrendingUp, label: '改善中', color: '#52c41a' },
  stable: { icon: Minus, label: '稳定', color: '#8c8c8c' },
  declining: { icon: TrendingDown, label: '退步', color: '#ff4d4f' },
}

const scrollbarStyles = `
  .m3-scroll::-webkit-scrollbar { width: 4px; }
  .m3-scroll::-webkit-scrollbar-track { background: transparent; }
  .m3-scroll::-webkit-scrollbar-thumb { background: rgba(255,255,255,0.15); border-radius: 4px; }
`

export default function WeakPointDetailPage({ item, onBack, onPractice, onExplain }) {
  const [userId, setUserId] = useState(null)
  const [detail, setDetail] = useState(null)
  const [recommend, setRecommend] = useState([])
  const [graph, setGraph] = useState(null)
  const [loading, setLoading] = useState(true)

  // 优先使用列表页传入的薄弱点对象，否则用 detail 兜底
  const kpName = item?.knowledgePoint || detail?.knowledgePoint || ''

  useEffect(() => {
    const user = getUserInfo()
    setUserId(user?.id || 1)
  }, [])

  const loadData = useCallback(async (uid) => {
    setLoading(true)
    try {
      const [d, rec, g] = await Promise.all([
        getWeakPointDetail(item?.id, uid),
        getRecommendQuestions(uid, item?.knowledgePoint || ''),
        getKnowledgeGraph(uid, item?.subject || ''),
      ])
      setDetail(d)
      setRecommend(rec)
      setGraph(g)
    } catch (e) {
      console.error('加载薄弱点详情失败:', e)
    }
    setLoading(false)
  }, [item?.id, item?.knowledgePoint, item?.subject])

  useEffect(() => {
    loadData(userId || 1)
  }, [loadData, userId])

  if (loading) {
    return (
      <div className="fixed inset-0 flex flex-col items-center justify-center"
        style={{ backgroundImage: "linear-gradient(135deg, #861FCE 0%, #861FCE 16%, #731CCD 16%, #731CCD 32%, #6B1CCF 32%, #6B1CCF 48%, #631DCE 48%, #631DCE 64%, #5A1BCE 64%, #5A1BCE 80%, rgb(86, 43, 205) 80%, rgb(47, 8, 154) 100%)" }}
      >
        <Loader2 className="w-10 h-10 text-purple-300 animate-spin mb-4" />
        <p className="text-purple-200 text-sm">正在加载薄弱点详情...</p>
      </div>
    )
  }

  const level = LEVEL_CONFIG[detail?.weaknessLevel] || LEVEL_CONFIG.MEDIUM
  const trend = TREND_CONFIG[detail?.trend]
  const TrendIcon = trend?.icon || TrendingUp
  const base = detail || item || {}

  return (
    <>
      <style>{scrollbarStyles}</style>
      <div
        className="fixed inset-0 flex flex-col"
        style={{
          backgroundImage: "linear-gradient(135deg, #861FCE 0%, #861FCE 16%, #731CCD 16%, #731CCD 32%, #6B1CCF 32%, #6B1CCF 48%, #631DCE 48%, #631DCE 64%, #5A1BCE 64%, #5A1BCE 80%, rgb(86, 43, 205) 80%, rgb(47, 8, 154) 100%)",
          backgroundAttachment: "fixed",
        }}
      >
        {/* Header */}
        <header className="shrink-0 px-6 py-4 flex items-center justify-between border-b border-purple-400/20">
          <div className="flex items-center gap-3 min-w-0">
            <button onClick={onBack} className="flex items-center gap-2 text-white/80 hover:text-white transition-colors shrink-0">
              <ArrowLeft className="w-5 h-5" />
              <span className="text-sm font-medium">返回</span>
            </button>
            <h1 className="text-lg font-bold text-white truncate">薄弱点详情</h1>
          </div>
          <button
            onClick={() => onPractice?.(base)}
            className="flex items-center gap-1.5 px-4 py-2.5 rounded-xl bg-gradient-to-r from-purple-500 to-blue-500 text-white text-sm font-medium hover:opacity-90 transition-all shadow-lg shadow-purple-500/20 shrink-0"
          >
            <Play className="w-4 h-4" />
            针对性练习
          </button>
        </header>

        {/* 内容区 */}
        <div className="flex-1 overflow-y-auto m3-scroll">
          <div className="max-w-4xl mx-auto p-4 md:p-6 space-y-4">
            {/* ① 薄弱概况条 */}
            <div className="bg-white/10 backdrop-blur-md rounded-2xl p-5 border border-white/10">
              <div className="flex items-center justify-between mb-4">
                <div className="flex items-center gap-2">
                  <span className={`text-xs px-2 py-0.5 rounded-full border ${level.bg} ${level.text} ${level.border}`}>{level.label}</span>
                  <h2 className="text-xl font-bold text-white">{kpName}</h2>
                  <span className="text-xs text-purple-200/50">{base.subject}</span>
                </div>
                {trend && (
                  <span className="flex items-center gap-1 text-sm" style={{ color: trend.color }}>
                    <TrendIcon className="w-4 h-4" /> {trend.label}
                  </span>
                )}
              </div>
              <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
                <div className="bg-white/5 rounded-xl p-3 text-center">
                  <p className="text-lg font-bold text-white">{base.weaknessScore ? Math.round(base.weaknessScore * 100) + '%' : '—'}</p>
                  <p className="text-[11px] text-purple-200/40">薄弱度</p>
                </div>
                <div className="bg-white/5 rounded-xl p-3 text-center">
                  <p className="text-lg font-bold text-white">{base.errorCount}/{base.totalCount}</p>
                  <p className="text-[11px] text-purple-200/40">错题/做题</p>
                </div>
                <div className="bg-white/5 rounded-xl p-3 text-center">
                  <p className="text-lg font-bold text-white">{base.accuracyRate?.toFixed(0) || 0}%</p>
                  <p className="text-[11px] text-purple-200/40">正确率</p>
                </div>
                <div className="bg-white/5 rounded-xl p-3 text-center">
                  <p className="text-lg font-bold text-white">{base.confusionCount || 0}</p>
                  <p className="text-[11px] text-purple-200/40">困惑次数</p>
                </div>
              </div>
            </div>

            {/* ② AI 诊断 */}
            {(detail?.aiAnalysis || detail?.aiSuggestion) && (
              <div className="bg-gradient-to-r from-purple-500/15 to-blue-500/15 backdrop-blur-md rounded-2xl p-5 border border-purple-400/20">
                <div className="flex items-center gap-2 mb-3">
                  <Lightbulb className="w-4 h-4 text-amber-300" />
                  <h3 className="text-sm font-semibold text-white">AI 诊断建议</h3>
                </div>
                {detail?.aiAnalysis && (
                  <p className="text-sm text-purple-100/80 leading-relaxed mb-2">{detail.aiAnalysis}</p>
                )}
                {detail?.aiSuggestion && (
                  <div className="bg-black/20 rounded-lg p-3">
                    <p className="text-sm text-emerald-200/90 leading-relaxed">{detail.aiSuggestion}</p>
                  </div>
                )}
              </div>
            )}

            {/* ③④ 趋势图 + 局部图谱 */}
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
              <div className="bg-white/10 backdrop-blur-md rounded-2xl p-5 border border-white/10">
                <h3 className="text-sm font-semibold text-white mb-3">错误率趋势</h3>
                <WeaknessTrendChart
                  recentErrorRate={detail?.recentErrorRate}
                  previousErrorRate={detail?.previousErrorRate}
                  errorRate={detail?.errorRate}
                />
                <p className="text-[11px] text-purple-200/40 text-center mt-1">
                  近7天 {detail?.recentErrorRate ?? 0}% vs 前7天 {detail?.previousErrorRate ?? 0}%
                </p>
              </div>
              <div className="bg-white/10 backdrop-blur-md rounded-2xl p-5 border border-white/10">
                <h3 className="text-sm font-semibold text-white mb-3">关联知识图谱</h3>
                <KpKnowledgeGraph
                  data={graph}
                  onNodeClick={(node) => { /* 点击节点：后续可跳转对应知识点详情 */ }}
                />
                <div className="flex items-center justify-center gap-3 mt-1">
                  <span className="text-[10px] text-red-300 flex items-center gap-1"><span className="w-2 h-2 rounded-full bg-red-500 inline-block" />薄弱</span>
                  <span className="text-[10px] text-amber-300 flex items-center gap-1"><span className="w-2 h-2 rounded-full bg-amber-400 inline-block" />一般</span>
                  <span className="text-[10px] text-green-300 flex items-center gap-1"><span className="w-2 h-2 rounded-full bg-green-500 inline-block" />掌握</span>
                  <span className="text-[10px] text-slate-300 flex items-center gap-1"><span className="w-2 h-2 rounded-full bg-slate-400 inline-block" />未学习</span>
                </div>
              </div>
            </div>

            {/* ⑤ 错误题目 */}
            <div className="bg-white/10 backdrop-blur-md rounded-2xl p-5 border border-white/10">
              <h3 className="text-sm font-semibold text-white mb-3">错误记录（点击可去讲题）</h3>
              <ErrorQuestionList items={detail?.recentErrors || []} onExplain={onExplain} />
            </div>

            {/* ⑥ 推荐题目 */}
            <div className="bg-white/10 backdrop-blur-md rounded-2xl p-5 border border-white/10">
              <h3 className="text-sm font-semibold text-white mb-3">推荐练习</h3>
              <RecommendQuestionList items={recommend} onPractice={() => onPractice?.(base)} />
            </div>
          </div>
        </div>
      </div>
    </>
  )
}
