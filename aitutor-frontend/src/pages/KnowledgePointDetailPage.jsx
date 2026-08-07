import React, { useCallback, useEffect, useMemo, useState } from 'react'
import {
  ArrowLeft,
  BookMarked,
  CheckCircle2,
  ChevronRight,
  CircleDot,
  Clock3,
  Dumbbell,
  RefreshCw,
  Sparkles,
  Target,
  TrendingUp,
} from 'lucide-react'
import { getKnowledgePointDetail } from '../services/learningProfileService'
import { getUserInfo } from '../utils/tokenManager'

const statusText = {
  mastered: '掌握良好',
  progressing: '正在巩固',
  learning: '学习中',
  review: '待复习',
  weak: '需要加强',
}

const difficultyText = {
  basic: '基础',
  medium: '进阶',
  advanced: '挑战',
}

const M6_BACKGROUND = 'linear-gradient(135deg, #861FCE 0%, #861FCE 16%, #731CCD 16%, #731CCD 32%, #6B1CCF 32%, #6B1CCF 48%, #631DCE 48%, #631DCE 64%, #5A1BCE 64%, #5A1BCE 80%, rgb(86,43,205) 80%, rgb(47,8,154) 100%)'

const metricCards = (metrics) => {
  if (Array.isArray(metrics)) return metrics
  if (!metrics || typeof metrics !== 'object') return []
  return [
    { label: '练习正确率', value: `${metrics.accuracy ?? 0}%` },
    { label: '累计学习', value: `${metrics.studyMinutes ?? 0}分钟` },
    { label: '完成练习', value: `${metrics.exercises ?? 0}题` },
    { label: '复习次数', value: `${metrics.reviewCount ?? 0}次` },
  ]
}

const colorForMastery = (value) => {
  if (value >= 80) return '#6ee7b7'
  if (value >= 60) return '#fcd34d'
  return '#fda4af'
}

function MasteryRing({ value = 0 }) {
  const safeValue = Math.max(0, Math.min(100, Number(value) || 0))
  const radius = 62
  const circumference = 2 * Math.PI * radius
  const offset = circumference * (1 - safeValue / 100)
  const color = colorForMastery(safeValue)
  return (
    <div className="relative grid h-40 w-40 place-items-center">
      <svg viewBox="0 0 150 150" className="absolute inset-0 h-full w-full -rotate-90" aria-hidden="true">
        <circle cx="75" cy="75" r={radius} fill="none" stroke="rgba(255,255,255,.1)" strokeWidth="10" />
        <circle cx="75" cy="75" r={radius} fill="none" stroke={color} strokeWidth="10" strokeLinecap="round" strokeDasharray={circumference} strokeDashoffset={offset} />
      </svg>
      <div className="text-center"><div className="text-4xl font-black">{safeValue}<span className="text-xl">%</span></div><div className="text-xs text-white/45">当前掌握度</div></div>
    </div>
  )
}

function TrendChart({ history = [] }) {
  const values = history.map((item) => Number(item.value ?? item.mastery) || 0)
  if (!values.length) return <div className="grid h-48 place-items-center text-sm text-white/40">暂无掌握度历史</div>
  const width = 640
  const height = 210
  const paddingX = 38
  const paddingY = 24
  const points = values.map((value, index) => {
    const x = values.length === 1 ? width / 2 : paddingX + (index / (values.length - 1)) * (width - paddingX * 2)
    const y = paddingY + (1 - value / 100) * (height - paddingY * 2)
    const rawLabel = history[index].label || history[index].date || ''
    const date = rawLabel ? new Date(rawLabel) : null
    const label = date && !Number.isNaN(date.getTime()) ? `${date.getMonth() + 1}/${date.getDate()}` : rawLabel
    return { x, y, value, label }
  })
  const polyline = points.map(({ x, y }) => `${x},${y}`).join(' ')
  return (
    <div className="overflow-x-auto">
      <svg viewBox={`0 0 ${width} ${height + 30}`} className="min-w-[520px]" role="img" aria-label="知识点掌握度趋势">
        {[20, 40, 60, 80, 100].map((tick) => {
          const y = paddingY + (1 - tick / 100) * (height - paddingY * 2)
          return <g key={tick}><line x1={paddingX} y1={y} x2={width - paddingX} y2={y} stroke="rgba(255,255,255,.08)" /><text x="4" y={y + 4} fill="rgba(255,255,255,.35)" fontSize="11">{tick}%</text></g>
        })}
        <polyline points={polyline} fill="none" stroke="#8be7ee" strokeWidth="4" strokeLinecap="round" strokeLinejoin="round" />
        {points.map((point, index) => <g key={`${point.label}-${index}`}><circle cx={point.x} cy={point.y} r="5" fill="#c4b5fd" stroke="#25105c" strokeWidth="3" /><text x={point.x} y={height + 18} textAnchor="middle" fill="rgba(255,255,255,.48)" fontSize="11">{point.label}</text></g>)}
      </svg>
    </div>
  )
}

export default function KnowledgePointDetailPage({ knowledgePointId, onBack, onHome }) {
  const [detail, setDetail] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const userInfo = useMemo(() => getUserInfo() || {}, [])
  const userId = userInfo.id ?? userInfo.userId ?? 'me'

  const loadDetail = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      setDetail(await getKnowledgePointDetail(userId, knowledgePointId))
    } catch (loadError) {
      console.error('[M6] 加载知识点详情失败:', loadError)
      setError(loadError.message || '知识点详情加载失败')
    } finally {
      setLoading(false)
    }
  }, [knowledgePointId, userId])

  useEffect(() => { loadDetail() }, [loadDetail])

  return (
    <main className="min-h-screen w-full overflow-auto text-white" style={{ backgroundImage: M6_BACKGROUND }}>
      <header className="sticky top-0 z-30 border-b border-purple-400/20 bg-[#4210A5]/45 px-5 py-4 backdrop-blur-md lg:px-8">
        <div className="mx-auto flex max-w-6xl items-center justify-between gap-4">
          <div className="flex items-center gap-3">
            <button type="button" onClick={onBack} className="grid h-10 w-10 place-items-center rounded-full border border-white/20 bg-white/10 shadow-md transition hover:scale-105 hover:bg-white/20" aria-label="返回学习档案"><ArrowLeft className="h-5 w-5" /></button>
            <div><div className="flex items-center gap-2 text-xs text-purple-200/70"><button type="button" onClick={onHome} className="hover:text-white">学习档案</button><ChevronRight className="h-3 w-3" /><span>知识点详情</span></div><h1 className="mt-0.5 text-xl font-bold">知识点掌握度</h1></div>
          </div>
          <button type="button" onClick={loadDetail} className="grid h-10 w-10 place-items-center rounded-full border border-white/20 bg-white/10 shadow-md transition hover:scale-105 hover:bg-white/20" aria-label="刷新"><RefreshCw className="h-4 w-4" /></button>
        </div>
      </header>

      {loading ? (
        <div className="mx-auto max-w-6xl animate-pulse space-y-6 px-5 py-8"><div className="h-64 rounded-3xl bg-white/10" /><div className="grid gap-6 lg:grid-cols-2"><div className="h-80 rounded-3xl bg-white/10" /><div className="h-80 rounded-3xl bg-white/10" /></div></div>
      ) : error || !detail ? (
        <div className="mx-auto grid min-h-[70vh] max-w-md place-items-center px-6 text-center"><div className="rounded-3xl border border-purple-300/30 bg-[#4210A5]/70 p-8 shadow-2xl backdrop-blur-lg"><Target className="mx-auto h-12 w-12 text-rose-200" /><h2 className="mt-4 text-2xl font-bold">详情加载失败</h2><p className="mt-2 text-white/55">{error}</p><button type="button" onClick={loadDetail} className="mt-5 rounded-full bg-gradient-to-r from-[#A286FF] to-[#638AFF] px-5 py-2 font-semibold text-white shadow-lg transition hover:-translate-y-0.5 hover:shadow-xl">重新加载</button></div></div>
      ) : (
        <div className="mx-auto max-w-6xl space-y-6 px-5 py-7 lg:px-8 lg:py-9">
          {detail.isDemo && <div className="flex gap-3 rounded-2xl border border-amber-200/30 bg-[#4210A5]/60 px-4 py-3 text-sm text-amber-50/90 shadow-lg backdrop-blur-md"><Sparkles className="h-4 w-4 shrink-0 text-amber-200" />联调示例数据，后端知识掌握度接口可用后将自动替换。</div>}

          <section className="grid items-center gap-7 overflow-hidden rounded-3xl border border-purple-500/20 bg-gradient-to-b from-purple-900/40 to-purple-800/20 p-6 shadow-2xl backdrop-blur-md sm:p-8 lg:grid-cols-[1fr_auto]">
            <div>
              <div className="flex flex-wrap items-center gap-2"><span className="rounded-full bg-cyan-300/15 px-3 py-1 text-xs font-semibold text-cyan-100">{detail.subject}</span><span className="rounded-full bg-white/10 px-3 py-1 text-xs text-white/65">{statusText[detail.status] || detail.status}</span></div>
              <h2 className="mt-4 text-3xl font-black sm:text-4xl">{detail.name}</h2>
              <p className="mt-3 max-w-3xl text-sm leading-7 text-white/60 sm:text-base">{detail.description}</p>
              <div className={`mt-5 flex items-center gap-2 text-sm ${detail.trend?.direction === 'down' ? 'text-rose-200' : 'text-emerald-200'}`}><TrendingUp className={`h-4 w-4 ${detail.trend?.direction === 'down' ? 'rotate-180' : ''}`} />{detail.trend?.label || detail.trend || '近期趋势稳定'}</div>
            </div>
            <MasteryRing value={detail.mastery} />
          </section>

          <section className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            {metricCards(detail.metrics).map((metric, index) => {
              const Icon = [CheckCircle2, Clock3, Target, BookMarked][index] || CircleDot
              return <article key={metric.label} className="rounded-2xl border border-purple-400/30 bg-[#4210A5]/60 p-5 shadow-xl backdrop-blur-lg transition duration-300 hover:-translate-y-1 hover:border-purple-300/50 hover:shadow-2xl"><Icon className="h-5 w-5 text-purple-200" /><div className="mt-3 text-2xl font-black">{metric.value}</div><div className="mt-1 text-sm text-white/55">{metric.label}</div></article>
            })}
          </section>

          <section className="grid gap-6 lg:grid-cols-5">
            <article className="rounded-3xl border border-purple-400/30 bg-gradient-to-b from-purple-900/40 to-purple-800/20 p-5 shadow-2xl backdrop-blur-lg sm:p-6 lg:col-span-3"><h3 className="text-xl font-bold">掌握度变化</h3><p className="mt-1 text-sm text-purple-100/55">基于近期答题、讲题反馈和复习结果</p><div className="mt-4"><TrendChart history={detail.history} /></div></article>
            <article className="rounded-3xl border border-purple-400/30 bg-[#4210A5]/60 p-5 shadow-xl backdrop-blur-lg sm:p-6 lg:col-span-2"><h3 className="text-xl font-bold">前置知识</h3><p className="mt-1 text-sm text-purple-100/55">建议先确保这些内容掌握稳定</p><div className="mt-5 space-y-3">{(detail.prerequisites || []).map((item) => <div key={item.id || item.name} className="flex items-center justify-between rounded-2xl border border-white/10 bg-white/[0.06] p-4"><span className="font-medium">{item.name}</span><span className="text-sm font-semibold" style={{ color: colorForMastery(item.mastery) }}>{item.mastery}%</span></div>)}</div></article>
          </section>

          <section className="grid gap-6 lg:grid-cols-2">
            <article className="rounded-3xl border border-purple-400/30 bg-[#4210A5]/60 p-5 shadow-xl backdrop-blur-lg sm:p-6">
              <div className="flex items-center gap-3"><div className="grid h-10 w-10 place-items-center rounded-xl bg-violet-300/15"><BookMarked className="h-5 w-5 text-violet-100" /></div><div><h3 className="text-xl font-bold">复习计划</h3><p className="text-sm text-white/45">按遗忘曲线安排</p></div></div>
              <div className="mt-6 space-y-3">
                {(detail.reviewPlan || []).map((step, index) => {
                  const completed = step.completed || step.status === 'completed'
                  const description = step.description || `${step.durationMinutes || 15}分钟 · ${step.type === 'quiz' ? '掌握度测验' : step.type === 'exercise' ? '针对性练习' : '知识回顾'}`
                  return <div key={step.id || `${step.title}-${index}`} className="flex gap-4 rounded-2xl border border-white/10 bg-white/[0.06] p-4"><div className={`grid h-8 w-8 shrink-0 place-items-center rounded-full text-sm font-bold ${completed ? 'bg-emerald-300 text-emerald-950' : 'bg-[#A286FF]/25 text-white/80'}`}>{completed ? '✓' : index + 1}</div><div><div className="font-semibold">{step.title}</div><div className="mt-1 text-sm text-white/55">{description}</div><div className="mt-2 text-xs text-purple-200/70">{step.dateLabel || step.date}</div></div></div>
                })}
              </div>
            </article>
            <article className="rounded-3xl border border-purple-400/30 bg-[#4210A5]/60 p-5 shadow-xl backdrop-blur-lg sm:p-6">
              <div className="flex items-center gap-3"><div className="grid h-10 w-10 place-items-center rounded-xl bg-cyan-300/15"><Dumbbell className="h-5 w-5 text-cyan-100" /></div><div><h3 className="text-xl font-bold">推荐练习</h3><p className="text-sm text-white/45">由易到难巩固知识点</p></div></div>
              <div className="mt-6 space-y-3">{(detail.recommendedExercises || []).map((exercise, index) => <div key={exercise.id || exercise.title} className="flex items-center justify-between gap-4 rounded-2xl border border-white/10 bg-white/[0.06] p-4 shadow-md transition hover:-translate-y-0.5 hover:border-purple-200/35 hover:bg-white/10"><div><div className="font-semibold">{exercise.title}</div><div className="mt-1 text-sm text-white/55">{exercise.questionCount ? `${exercise.questionCount}题 · ` : ''}约{exercise.estimatedMinutes}分钟</div></div><span className={`rounded-full px-3 py-1 text-xs ${index === 0 ? 'bg-emerald-300/15 text-emerald-100' : index === 1 ? 'bg-amber-300/15 text-amber-100' : 'bg-rose-300/15 text-rose-100'}`}>{difficultyText[exercise.difficulty] || exercise.difficulty}</span></div>)}</div>
            </article>
          </section>
        </div>
      )}
    </main>
  )
}
