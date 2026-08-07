import { Target, AlertTriangle, BookOpen, Home } from 'lucide-react'
import RichTextEditor from './RichTextEditor'
import TagListEditor from './TagListEditor'
import TeachingProcessEditor from './TeachingProcessEditor'

// 各分区专属主题色配置
const THEMES = {
  header: {
    icon: 'from-amber-400 to-orange-500',
    text: 'text-amber-300',
    label: 'bg-amber-500/20 text-amber-200 border-amber-400/30',
    border: 'border-amber-400/20',
    bg: 'bg-gradient-to-br from-amber-500/5 to-transparent',
    dot: 'bg-amber-400',
  },
  content: {
    icon: 'from-sky-400 to-blue-500',
    text: 'text-sky-300',
    label: 'bg-sky-500/20 text-sky-200 border-sky-400/30',
    border: 'border-sky-400/20',
    bg: 'bg-gradient-to-br from-sky-500/5 to-transparent',
    dot: 'bg-sky-400',
  },
  goals: {
    icon: 'from-emerald-400 to-green-500',
    text: 'text-emerald-300',
    label: 'bg-emerald-500/20 text-emerald-200 border-emerald-400/30',
    border: 'border-emerald-400/20',
    bg: 'bg-gradient-to-br from-emerald-500/5 to-transparent',
    dot: 'bg-emerald-400',
  },
  keypoint: {
    icon: 'from-amber-400 to-yellow-500',
    text: 'text-amber-300',
    label: 'bg-amber-500/20 text-amber-200 border-amber-400/30',
    border: 'border-amber-400/20',
    bg: 'bg-gradient-to-br from-amber-500/5 to-transparent',
    dot: 'bg-amber-400',
  },
  difficult: {
    icon: 'from-rose-400 to-red-500',
    text: 'text-rose-300',
    label: 'bg-rose-500/20 text-rose-200 border-rose-400/30',
    border: 'border-rose-400/20',
    bg: 'bg-gradient-to-br from-rose-500/5 to-transparent',
    dot: 'bg-rose-400',
  },
  process: {
    icon: 'from-fuchsia-400 to-purple-500',
    text: 'text-fuchsia-300',
    label: 'bg-fuchsia-500/20 text-fuchsia-200 border-fuchsia-400/30',
    border: 'border-fuchsia-400/20',
    bg: 'bg-gradient-to-br from-fuchsia-500/5 to-transparent',
    dot: 'bg-fuchsia-400',
  },
  homework: {
    icon: 'from-teal-400 to-cyan-500',
    text: 'text-teal-300',
    label: 'bg-teal-500/20 text-teal-200 border-teal-400/30',
    border: 'border-teal-400/20',
    bg: 'bg-gradient-to-br from-teal-500/5 to-transparent',
    dot: 'bg-teal-400',
  },
}

function SectionCard({ icon: Icon, title, themeKey, subtitle, children }) {
  const theme = THEMES[themeKey] || THEMES.content
  return (
    <div className={`bg-white/10 backdrop-blur-md rounded-2xl p-5 border ${theme.border} overflow-hidden relative`}>
      {/* 顶部彩色渐变条 */}
      <div className={`absolute top-0 left-0 right-0 h-0.5 bg-gradient-to-r ${theme.icon} opacity-60`} />
      {/* 彩色背景晕染 */}
      <div className={`absolute -top-8 -right-8 w-32 h-32 rounded-full ${theme.bg} blur-2xl pointer-events-none`} />
      <div className="relative flex items-center gap-3 mb-4">
        <div className={`w-8 h-8 rounded-xl bg-gradient-to-br ${theme.icon} flex items-center justify-center shadow-lg shrink-0`}>
          <Icon className="w-4.5 h-4.5 text-white" />
        </div>
        <div>
          <h3 className="text-base font-semibold text-white leading-tight">{title}</h3>
          {subtitle && <span className="text-[11px] text-purple-200/50">{subtitle}</span>}
        </div>
        <span className={`ml-auto w-1.5 h-1.5 rounded-full ${theme.dot} shrink-0`} />
      </div>
      <div className="relative">{children}</div>
    </div>
  )
}

export default function SectionEditor({ section, index, onChange }) {
  const update = (field, val) => {
    onChange({ ...section, [field]: val })
  }

  const updateHomework = (field, val) => {
    update('homework', { ...(section.homework || {}), [field]: val })
  }

  const headerTheme = THEMES.header

  return (
    <div className="space-y-4">
      {/* 课时头部 */}
      <div className={`bg-white/10 backdrop-blur-md rounded-2xl p-5 border ${headerTheme.border} overflow-hidden relative`}>
        <div className={`absolute top-0 left-0 right-0 h-0.5 bg-gradient-to-r ${headerTheme.icon} opacity-60`} />
        <div className={`absolute -top-8 -right-8 w-32 h-32 rounded-full ${headerTheme.bg} blur-2xl pointer-events-none`} />
        <div className="relative flex items-center gap-2 mb-4">
          <span className={`text-xs px-2.5 py-1 rounded-lg border ${headerTheme.label} shrink-0 font-medium`}>第 {index + 1} 课时</span>
          <h3 className="text-base font-semibold text-white truncate">{section.title}</h3>
        </div>
        <div className="relative flex items-center gap-2">
          <span className={`text-xs ${headerTheme.text} shrink-0`}>课时标题</span>
          <input
            type="text"
            value={section.title || ''}
            onChange={(e) => update('title', e.target.value)}
            placeholder="输入课时标题..."
            className="flex-1 bg-white/5 border border-white/10 rounded-xl px-3 py-2.5 text-sm text-white outline-none focus:border-amber-400/40 focus:bg-white/10 transition-all placeholder-purple-200/30"
          />
        </div>
      </div>

      {/* 核心内容 - 富文本 */}
      <SectionCard icon={BookOpen} title="核心内容" themeKey="content">
        <RichTextEditor
          value={section.coreContent || ''}
          onChange={(html) => update('coreContent', html)}
          placeholder="输入本课时的核心知识点讲解内容..."
          minHeight={160}
          accentColor="sky"
        />
      </SectionCard>

      {/* 教学目标 */}
      <SectionCard icon={Target} title="教学目标" themeKey="goals">
        <TagListEditor
          value={section.teachingGoals || []}
          onChange={(goals) => update('teachingGoals', goals)}
          placeholder="输入教学目标，回车添加..."
          color="emerald"
        />
      </SectionCard>

      {/* 重难点 */}
      <SectionCard icon={AlertTriangle} title="重难点" themeKey="keypoint" subtitle="重点 / 难点">
        <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
          <div className="bg-white/5 border border-white/10 rounded-xl p-3">
            <p className="text-xs text-amber-300 mb-2 flex items-center gap-1.5">
              <span className="w-1.5 h-1.5 rounded-full bg-amber-400 inline-block" />
              重点
            </p>
            <TagListEditor
              value={section.keyPoints || []}
              onChange={(kps) => update('keyPoints', kps)}
              placeholder="输入重点，回车添加..."
              color="amber"
            />
          </div>
          <div className="bg-white/5 border border-white/10 rounded-xl p-3">
            <p className="text-xs text-rose-300 mb-2 flex items-center gap-1.5">
              <span className="w-1.5 h-1.5 rounded-full bg-rose-400 inline-block" />
              难点
            </p>
            <TagListEditor
              value={section.difficultPoints || []}
              onChange={(dps) => update('difficultPoints', dps)}
              placeholder="输入难点，回车添加..."
              color="rose"
            />
          </div>
        </div>
      </SectionCard>

      {/* 教学过程 */}
      <TeachingProcessEditor
        value={section.teachingProcess || []}
        onChange={(process) => update('teachingProcess', process)}
      />

      {/* 课后作业 */}
      <SectionCard icon={Home} title="课后作业" themeKey="homework">
        <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
          <div className="bg-white/5 border border-white/10 rounded-xl p-3">
            <p className="text-xs text-teal-300 mb-2 flex items-center gap-1.5">
              <span className="w-1.5 h-1.5 rounded-full bg-teal-400 inline-block" />
              基础题
            </p>
            <TagListEditor
              value={(section.homework?.basic) || []}
              onChange={(items) => updateHomework('basic', items)}
              placeholder="回车添加..."
              color="teal"
            />
          </div>
          <div className="bg-white/5 border border-white/10 rounded-xl p-3">
            <p className="text-xs text-cyan-300 mb-2 flex items-center gap-1.5">
              <span className="w-1.5 h-1.5 rounded-full bg-cyan-400 inline-block" />
              提高题
            </p>
            <TagListEditor
              value={(section.homework?.advanced) || []}
              onChange={(items) => updateHomework('advanced', items)}
              placeholder="回车添加..."
              color="cyan"
            />
          </div>
          <div className="bg-white/5 border border-white/10 rounded-xl p-3">
            <p className="text-xs text-indigo-300 mb-2 flex items-center gap-1.5">
              <span className="w-1.5 h-1.5 rounded-full bg-indigo-400 inline-block" />
              拓展题
            </p>
            <TagListEditor
              value={(section.homework?.optional) || []}
              onChange={(items) => updateHomework('optional', items)}
              placeholder="回车添加..."
              color="indigo"
            />
          </div>
        </div>
      </SectionCard>
    </div>
  )
}
