import { useState, useEffect, useCallback } from 'react'
import { ArrowLeft, Sparkles, Loader2, AlertTriangle, BookOpen } from 'lucide-react'
import { getUserInfo } from '../../utils/tokenManager'
import { getWeakPoints, triggerWeakPointsAnalysis } from '../../services/m3'
import WeakPointCard from '../../components/m3/WeakPointCard'

const SUBJECTS = ['全部', '数学', '语文', '英语', '物理', '化学', '生物']

const scrollbarStyles = `
  .m3-scroll::-webkit-scrollbar { width: 4px; }
  .m3-scroll::-webkit-scrollbar-track { background: transparent; }
  .m3-scroll::-webkit-scrollbar-thumb { background: rgba(255,255,255,0.15); border-radius: 4px; }
  .m3-scroll::-webkit-scrollbar-thumb:hover { background: rgba(255,255,255,0.25); }
`

export default function WeakPointListPage({ onBack, onDetail, onPractice }) {
  const [userId, setUserId] = useState(null)
  const [items, setItems] = useState([])
  const [loading, setLoading] = useState(true)
  const [subjectFilter, setSubjectFilter] = useState('全部')
  const [sortBy, setSortBy] = useState('level') // level | lastError
  const [showAnalysis, setShowAnalysis] = useState(false)
  const [analysisLoading, setAnalysisLoading] = useState(false)
  const [analysis, setAnalysis] = useState(null)

  const loadData = useCallback(async (uid) => {
    setLoading(true)
    try {
      const data = await getWeakPoints(uid)
      setItems(data || [])
    } catch (e) {
      console.error('加载薄弱点失败:', e)
    }
    setLoading(false)
  }, [])

  useEffect(() => {
    const user = getUserInfo()
    const uid = user?.id || 1
    setUserId(uid)
    loadData(uid)
  }, [loadData])

  // 过滤
  const filtered = items.filter(item => {
    if (subjectFilter !== '全部' && item.subject !== subjectFilter) return false
    return true
  })

  // 排序
  const sorted = [...filtered].sort((a, b) => {
    const order = { HIGH: 0, MEDIUM: 1, LOW: 2 }
    if (sortBy === 'level') {
      return (order[a.weaknessLevel] ?? 9) - (order[b.weaknessLevel] ?? 9)
    }
    // 按最近错题时间，新的在前
    return new Date(b.lastErrorTime || 0) - new Date(a.lastErrorTime || 0)
  })

  // 概况统计
  const highCount = items.filter(i => i.weaknessLevel === 'HIGH').length
  const avgAccuracy = items.length > 0
    ? items.reduce((s, i) => s + i.accuracyRate, 0) / items.length
    : 0

  const handleAnalysis = async () => {
    setAnalysisLoading(true)
    try {
      const result = await triggerWeakPointsAnalysis(userId || 1)
      setAnalysis(result)
      setShowAnalysis(true)
    } catch (e) {
      console.error('AI 分析失败:', e)
    }
    setAnalysisLoading(false)
  }

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
          <div className="flex items-center gap-3">
            <button onClick={onBack} className="flex items-center gap-2 text-white/80 hover:text-white transition-colors">
              <ArrowLeft className="w-5 h-5" />
              <span className="text-sm font-medium">返回</span>
            </button>
            <div className="flex items-center gap-2 ml-2">
              <AlertTriangle className="w-5 h-5 text-purple-200" />
              <h1 className="text-lg font-bold text-white">我的薄弱点</h1>
            </div>
          </div>
          <button
            onClick={handleAnalysis}
            disabled={analysisLoading}
            className="flex items-center gap-1.5 px-4 py-2.5 rounded-xl bg-gradient-to-r from-purple-500 to-blue-500 text-white text-sm font-medium hover:opacity-90 transition-all shadow-lg shadow-purple-500/20 disabled:opacity-50"
          >
            <Sparkles className="w-4 h-4" />
            {analysisLoading ? '分析中...' : 'AI 综合分析'}
          </button>
        </header>

        {/* 内容区 */}
        <div className="flex-1 overflow-y-auto m3-scroll">
          <div className="max-w-4xl mx-auto p-4 md:p-6">
            {/* 概况条 */}
            <div className="grid grid-cols-3 gap-3 mb-5">
              <div className="bg-white/10 backdrop-blur-md rounded-2xl p-4 border border-white/10">
                <p className="text-2xl font-bold text-white">{items.length}</p>
                <p className="text-xs text-purple-200/50 mt-1">薄弱知识点</p>
              </div>
              <div className="bg-white/10 backdrop-blur-md rounded-2xl p-4 border border-white/10">
                <p className="text-2xl font-bold text-red-300">{highCount}</p>
                <p className="text-xs text-purple-200/50 mt-1">高薄弱 (HIGH)</p>
              </div>
              <div className="bg-white/10 backdrop-blur-md rounded-2xl p-4 border border-white/10">
                <p className="text-2xl font-bold text-white">{avgAccuracy.toFixed(1)}%</p>
                <p className="text-xs text-purple-200/50 mt-1">平均正确率</p>
              </div>
            </div>

            {/* AI 分析结果 */}
            {showAnalysis && analysis && (
              <div className="mb-5 bg-gradient-to-r from-purple-500/15 to-blue-500/15 backdrop-blur-md rounded-2xl p-5 border border-purple-400/20 animate-fadeIn">
                <div className="flex items-center gap-2 mb-3">
                  <Sparkles className="w-4 h-4 text-purple-300" />
                  <h3 className="text-sm font-semibold text-white">AI 综合诊断</h3>
                  <button onClick={() => setShowAnalysis(false)} className="ml-auto text-purple-200/50 hover:text-white text-xs">关闭</button>
                </div>
                <div className="prose prose-sm prose-invert max-w-none text-purple-100/80 text-sm leading-relaxed whitespace-pre-wrap">
                  {analysis.comprehensiveAnalysis}
                </div>
                {analysis.recommendedPriority?.length > 0 && (
                  <div className="mt-3 flex flex-wrap gap-1.5">
                    {analysis.recommendedPriority.map((name, i) => (
                      <span key={i} className="text-[11px] px-2.5 py-1 rounded-full bg-red-500/20 text-red-200 border border-red-400/20">
                        优先：{name}
                      </span>
                    ))}
                  </div>
                )}
              </div>
            )}

            {/* 筛选/排序栏 */}
            <div className="flex items-center gap-3 flex-wrap mb-5">
              <div className="flex items-center gap-1 bg-white/5 rounded-xl p-1">
                {SUBJECTS.map(sub => (
                  <button
                    key={sub}
                    onClick={() => setSubjectFilter(sub)}
                    className={`px-3 py-1.5 rounded-lg text-xs transition-all ${
                      subjectFilter === sub
                        ? 'bg-white/15 text-white'
                        : 'text-purple-200/60 hover:text-white hover:bg-white/10'
                    }`}
                  >
                    {sub}
                  </button>
                ))}
              </div>
              <div className="ml-auto">
                <select
                  value={sortBy}
                  onChange={(e) => setSortBy(e.target.value)}
                  className="bg-white/5 border border-white/10 rounded-xl px-3 py-2 text-xs text-white outline-none focus:border-purple-400/40 transition-all appearance-none cursor-pointer"
                >
                  <option value="level" className="bg-[#5A1BCE]">按薄弱程度</option>
                  <option value="lastError" className="bg-[#5A1BCE]">按最近错题时间</option>
                </select>
              </div>
            </div>

            {/* 列表区 */}
            {loading ? (
              <div className="flex flex-col items-center justify-center py-24">
                <Loader2 className="w-10 h-10 text-purple-300 animate-spin mb-4" />
                <p className="text-purple-200 text-sm">正在加载薄弱点...</p>
              </div>
            ) : sorted.length === 0 ? (
              <div className="flex flex-col items-center justify-center py-24">
                <div className="w-16 h-16 rounded-2xl bg-white/10 flex items-center justify-center mb-4">
                  <BookOpen className="w-8 h-8 text-purple-300/60" />
                </div>
                <p className="text-white font-medium mb-1">暂无薄弱点</p>
                <p className="text-sm text-purple-200/50">继续保持，你的掌握情况很不错！</p>
              </div>
            ) : (
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                {sorted.map(item => (
                  <WeakPointCard
                    key={item.id}
                    item={item}
                    onDetail={() => onDetail?.(item)}
                    onPractice={() => onPractice?.(item)}
                  />
                ))}
              </div>
            )}
          </div>
        </div>
      </div>
    </>
  )
}
