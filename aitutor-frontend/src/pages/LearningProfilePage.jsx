import React, { useCallback, useEffect, useMemo, useState } from 'react'
import {
  ArrowLeft,
  BookOpenCheck,
  BrainCircuit,
  CalendarClock,
  Clock3,
  Flame,
  RefreshCw,
  Sparkles,
  Target,
  TrendingUp,
} from 'lucide-react'
import RadarChart from '../components/m6/RadarChart'
import KnowledgeTree from '../components/m6/KnowledgeTree'
import { getLearningProfile } from '../services/learningProfileService'
import { getUserInfo } from '../utils/tokenManager'

const statIcons = [Clock3, BookOpenCheck, Flame, TrendingUp]
const M6_BACKGROUND = 'linear-gradient(135deg, #861FCE 0%, #861FCE 16%, #731CCD 16%, #731CCD 32%, #6B1CCF 32%, #6B1CCF 48%, #631DCE 48%, #631DCE 64%, #5A1BCE 64%, #5A1BCE 80%, rgb(86,43,205) 80%, rgb(47,8,154) 100%)'

const formatDate = (value) => {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
  return new Intl.DateTimeFormat('zh-CN', {
    month: 'short',
    day: 'numeric',
  }).format(date)
}

const formatChange = (value) => {
  const change = Number(value)
  if (!Number.isFinite(change) || change === 0) return ''
  return `${change > 0 ? '+' : ''}${change}%`
}

const LearningProfileSkeleton = () => (
  <div className="mx-auto max-w-7xl animate-pulse space-y-6 px-5 py-8 lg:px-8">
    <div className="h-40 rounded-3xl bg-white/10" />
    <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
      {[0, 1, 2, 3].map((item) => <div key={item} className="h-28 rounded-2xl bg-white/10" />)}
    </div>
    <div className="grid gap-6 xl:grid-cols-5">
      <div className="h-96 rounded-3xl bg-white/10 xl:col-span-2" />
      <div className="h-96 rounded-3xl bg-white/10 xl:col-span-3" />
    </div>
  </div>
)

export default function LearningProfilePage({ onBack, onOpenKnowledgePoint }) {
  const [profile, setProfile] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const userInfo = useMemo(() => getUserInfo() || {}, [])
  const userId = userInfo.id ?? userInfo.userId ?? 'me'

  const loadProfile = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      const result = await getLearningProfile(userId)
      setProfile(result)
    } catch (loadError) {
      console.error('[M6] 加载学习档案失败:', loadError)
      setError(loadError.message || '学习档案加载失败，请稍后重试')
    } finally {
      setLoading(false)
    }
  }, [userId])

  useEffect(() => {
    loadProfile()
  }, [loadProfile])

  if (loading) {
    return (
      <main className="min-h-screen w-full overflow-auto text-white" style={{ backgroundImage: M6_BACKGROUND }}>
        <LearningProfileSkeleton />
      </main>
    )
  }

  if (error || !profile) {
    return (
      <main className="flex min-h-screen w-full items-center justify-center px-6 text-white" style={{ backgroundImage: M6_BACKGROUND }}>
        <div className="max-w-md rounded-3xl border border-purple-300/30 bg-[#4210A5]/70 p-8 text-center shadow-2xl backdrop-blur-lg">
          <BrainCircuit className="mx-auto h-12 w-12 text-rose-200" />
          <h1 className="mt-4 text-2xl font-bold">学习档案暂时无法加载</h1>
          <p className="mt-2 text-sm text-white/65">{error || '没有可展示的数据'}</p>
          <div className="mt-6 flex justify-center gap-3">
            <button type="button" onClick={onBack} className="rounded-full border border-white/20 bg-white/10 px-5 py-2 text-sm transition hover:bg-white/20">返回</button>
            <button type="button" onClick={loadProfile} className="rounded-full bg-gradient-to-r from-[#A286FF] to-[#638AFF] px-5 py-2 text-sm font-semibold text-white shadow-lg transition hover:-translate-y-0.5 hover:shadow-xl">重新加载</button>
          </div>
        </div>
      </main>
    )
  }

  const displayName = profile.user?.name || userInfo.studentName || userInfo.realName || userInfo.username || '同学'

  return (
    <main
      className="min-h-screen w-full overflow-auto text-white"
      style={{ backgroundImage: M6_BACKGROUND }}
    >
      <header className="sticky top-0 z-30 border-b border-purple-400/20 bg-[#4210A5]/45 px-5 py-4 backdrop-blur-md lg:px-8">
        <div className="mx-auto flex max-w-7xl items-center justify-between gap-4">
          <div className="flex items-center gap-3">
            <button
              type="button"
              onClick={onBack}
              className="grid h-10 w-10 place-items-center rounded-full border border-white/20 bg-white/10 shadow-md transition hover:scale-105 hover:bg-white/20"
              aria-label="返回首页"
            >
              <ArrowLeft className="h-5 w-5" />
            </button>
            <div>
              <div className="text-xs font-semibold uppercase tracking-[0.22em] text-purple-200/75">LeapMind · M6</div>
              <h1 className="text-xl font-bold sm:text-2xl">我的学习档案</h1>
            </div>
          </div>
          <button
            type="button"
            onClick={loadProfile}
            className="flex items-center gap-2 rounded-full border border-white/20 bg-white/10 px-4 py-2 text-sm font-semibold text-white/90 shadow-md transition hover:scale-105 hover:bg-white/20 hover:shadow-lg"
          >
            <RefreshCw className="h-4 w-4" />
            <span className="hidden sm:inline">刷新数据</span>
          </button>
        </div>
      </header>

      <div className="mx-auto max-w-7xl space-y-6 px-5 py-7 lg:px-8 lg:py-9">
        {profile.isDemo && (
          <div className="flex items-start gap-3 rounded-2xl border border-amber-200/30 bg-[#4210A5]/60 px-4 py-3 text-sm text-amber-50/90 shadow-lg backdrop-blur-md">
            <Sparkles className="mt-0.5 h-4 w-4 shrink-0 text-amber-200" />
            <span>当前展示的是联调示例数据；M6 后端接口可用后会自动展示你的真实学习记录。</span>
          </div>
        )}

        <section className="relative overflow-hidden rounded-3xl border border-purple-500/20 bg-gradient-to-b from-purple-900/40 to-purple-800/20 p-6 shadow-2xl backdrop-blur-md lg:p-8">
          <div className="absolute -right-14 -top-20 h-64 w-64 rounded-full bg-cyan-300/10 blur-3xl" />
          <div className="relative grid gap-6 lg:grid-cols-[1fr_auto] lg:items-center">
            <div>
              <div className="flex flex-wrap items-center gap-3">
                <span className="rounded-full bg-violet-300/15 px-3 py-1 text-xs font-semibold text-violet-100 ring-1 ring-violet-200/20">持续学习画像</span>
                {profile.user?.major && <span className="text-sm text-white/55">{profile.user.major}</span>}
              </div>
              <h2 className="mt-4 text-3xl font-black tracking-tight sm:text-4xl">{displayName}，继续保持你的节奏</h2>
              <p className="mt-3 max-w-3xl text-sm leading-7 text-violet-100/75 sm:text-base">{profile.summary?.message || profile.summary || '继续保持稳定的学习节奏。'}</p>
            </div>
            <div className="grid min-w-48 grid-cols-2 gap-3">
              <div className="rounded-2xl border border-purple-300/25 bg-[#4210A5]/55 p-4 shadow-lg">
                <div className="text-xs text-white/50">优势方向</div>
                <div className="mt-1 font-bold text-cyan-200">{profile.preferences?.strength || [...(profile.dimensions || [])].sort((a, b) => b.value - a.value)[0]?.label || '逻辑推导'}</div>
              </div>
              <div className="rounded-2xl border border-purple-300/25 bg-[#4210A5]/55 p-4 shadow-lg">
                <div className="text-xs text-white/50">学习方式</div>
                <div className="mt-1 font-bold text-violet-200">{profile.preferences?.learningStyle || '图文结合'}</div>
              </div>
            </div>
          </div>
        </section>

        <section className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4" aria-label="学习概览">
          {(profile.stats || []).slice(0, 4).map((stat, index) => {
            const Icon = statIcons[index] || Target
            return (
              <article key={stat.key || stat.label} className="rounded-2xl border border-purple-400/30 bg-[#4210A5]/60 p-5 shadow-xl backdrop-blur-lg transition duration-300 hover:-translate-y-1 hover:border-purple-300/50 hover:shadow-2xl">
                <div className="flex items-center justify-between">
                  <div className="grid h-10 w-10 place-items-center rounded-xl bg-violet-300/15 text-violet-100"><Icon className="h-5 w-5" /></div>
                  {formatChange(stat.change) && <span className="text-xs font-semibold text-emerald-300">{formatChange(stat.change)}</span>}
                </div>
                <div className="mt-4 text-2xl font-black">{stat.value}<span className="ml-1 text-sm font-semibold text-white/50">{stat.unit}</span></div>
                <div className="mt-1 text-sm text-white/55">{stat.label}</div>
              </article>
            )
          })}
        </section>

        <section className="grid gap-6 xl:grid-cols-5">
          <article className="rounded-3xl border border-purple-400/30 bg-gradient-to-b from-purple-900/40 to-purple-800/20 p-5 shadow-2xl backdrop-blur-lg sm:p-6 xl:col-span-2">
            <div className="mb-3 flex items-start justify-between">
              <div>
                <p className="text-xs font-semibold uppercase tracking-[0.18em] text-purple-200/65">学习画像</p>
                <h3 className="mt-1 text-xl font-bold">学习能力雷达</h3>
              </div>
              <BrainCircuit className="h-6 w-6 text-cyan-200" />
            </div>
            <RadarChart dimensions={profile.dimensions || []} />
          </article>

          <article className="rounded-3xl border border-purple-400/30 bg-gradient-to-b from-purple-900/40 to-purple-800/20 p-5 shadow-2xl backdrop-blur-lg sm:p-6 xl:col-span-3">
            <div className="mb-5 flex flex-wrap items-end justify-between gap-3">
              <div>
                <p className="text-xs font-semibold uppercase tracking-[0.18em] text-purple-200/65">知识图谱</p>
                <h3 className="mt-1 text-xl font-bold">知识掌握树</h3>
                <p className="mt-1 text-sm text-white/50">点击知识点查看掌握度与复习计划</p>
              </div>
              <div className="flex gap-3 text-xs text-white/55">
                <span><i className="mr-1 inline-block h-2 w-2 rounded-full bg-emerald-300" />已掌握</span>
                <span><i className="mr-1 inline-block h-2 w-2 rounded-full bg-amber-300" />巩固中</span>
                <span><i className="mr-1 inline-block h-2 w-2 rounded-full bg-rose-300" />待加强</span>
              </div>
            </div>
            <KnowledgeTree knowledgeTree={profile.knowledgeTree || []} onSelect={onOpenKnowledgePoint} />
          </article>
        </section>

        <section className="grid gap-6 lg:grid-cols-5">
          <article className="rounded-3xl border border-purple-400/30 bg-[#4210A5]/60 p-5 shadow-xl backdrop-blur-lg sm:p-6 lg:col-span-3">
            <div className="flex items-center gap-3">
              <div className="grid h-10 w-10 place-items-center rounded-xl bg-cyan-300/15 text-cyan-100"><TrendingUp className="h-5 w-5" /></div>
              <div><h3 className="text-xl font-bold">近期学习轨迹</h3><p className="text-sm text-white/45">记录每一次进步</p></div>
            </div>
            <div className="relative mt-6 space-y-5 pl-6 before:absolute before:bottom-2 before:left-[5px] before:top-2 before:w-px before:bg-white/15">
              {(profile.timeline || []).map((event) => (
                <div key={event.id || `${event.title}-${event.date}`} className="relative">
                  <span className="absolute -left-6 top-2 h-3 w-3 rounded-full border-2 border-[#241054] bg-cyan-300" />
                  <div className="flex flex-col justify-between gap-2 rounded-2xl border border-white/10 bg-white/[0.06] p-4 sm:flex-row sm:items-center">
                    <div><div className="font-semibold">{event.title}</div><div className="mt-1 text-sm text-white/50">{event.description}</div></div>
                    <time className="shrink-0 text-xs text-violet-200/60">{formatDate(event.time || event.date)}</time>
                  </div>
                </div>
              ))}
            </div>
          </article>

          <article className="rounded-3xl border border-purple-400/30 bg-[#4210A5]/60 p-5 shadow-xl backdrop-blur-lg sm:p-6 lg:col-span-2">
            <div className="flex items-center gap-3">
              <div className="grid h-10 w-10 place-items-center rounded-xl bg-amber-300/15 text-amber-100"><CalendarClock className="h-5 w-5" /></div>
              <div><h3 className="text-xl font-bold">复习提醒</h3><p className="text-sm text-white/45">按记忆节奏及时巩固</p></div>
            </div>
            <div className="mt-6 space-y-3">
              {(profile.reminders || []).map((reminder) => (
                <button
                  type="button"
                  key={reminder.id}
                  onClick={() => reminder.knowledgePointId && onOpenKnowledgePoint?.(reminder.knowledgePointId)}
                  className="w-full rounded-2xl border border-white/10 bg-white/[0.06] p-4 text-left shadow-md transition duration-200 hover:-translate-y-0.5 hover:border-purple-200/40 hover:bg-white/10 hover:shadow-lg"
                >
                  <div className="flex items-center justify-between gap-3">
                    <span className="font-semibold">{reminder.title}</span>
                    <span className="rounded-full bg-amber-300/15 px-2.5 py-1 text-xs text-amber-100">{reminder.dueLabel || formatDate(reminder.dueAt)}</span>
                  </div>
                  <p className="mt-2 text-sm text-white/48">{reminder.reason}</p>
                </button>
              ))}
              {!profile.reminders?.length && <div className="rounded-2xl bg-black/10 p-5 text-center text-sm text-white/45">当前没有待复习内容</div>}
            </div>
          </article>
        </section>
      </div>
    </main>
  )
}
