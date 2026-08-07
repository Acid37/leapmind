import { BookOpen, AlignLeft, MessageSquare } from 'lucide-react'

const STYLES = [
  {
    id: 'standard',
    name: '标准教学',
    desc: '内容详实、逻辑清晰、节奏适中，适合常规课堂',
    icon: BookOpen,
    gradient: 'from-blue-400 to-cyan-400',
  },
  {
    id: 'detailed',
    name: '详细讲解',
    desc: '充分展开、大量示例，适合基础薄弱学生',
    icon: AlignLeft,
    gradient: 'from-purple-400 to-pink-400',
  },
  {
    id: 'interactive',
    name: '互动启发',
    desc: '多设问、多讨论，引导学生自主思考',
    icon: MessageSquare,
    gradient: 'from-amber-400 to-orange-400',
  },
]

export default function StyleSelector({ value = 'standard', onChange }) {
  return (
    <div className="bg-white/10 backdrop-blur-md rounded-2xl p-5 border border-white/10">
      <h3 className="text-base font-semibold text-white mb-4">备课风格</h3>

      <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
        {STYLES.map(style => {
          const Icon = style.icon
          const selected = value === style.id
          return (
            <button
              key={style.id}
              onClick={() => onChange(style.id)}
              className={`
                relative text-left p-4 rounded-xl border transition-all duration-300
                ${selected
                  ? 'bg-white/15 border-purple-400/50 shadow-lg shadow-purple-500/10'
                  : 'bg-white/5 border-white/10 hover:bg-white/10 hover:border-white/20'
                }
              `}
            >
              {selected && (
                <div className="absolute top-2 right-2 w-5 h-5 bg-gradient-to-r from-purple-400 to-blue-400 rounded-full flex items-center justify-center">
                  <svg className="w-3 h-3 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="3" d="M5 13l4 4L19 7" />
                  </svg>
                </div>
              )}
              <div className={`w-10 h-10 rounded-xl bg-gradient-to-br ${style.gradient} flex items-center justify-center mb-3 shadow-lg`}>
                <Icon className="w-5 h-5 text-white" />
              </div>
              <p className="text-sm font-semibold text-white mb-1">{style.name}</p>
              <p className="text-xs text-purple-200/60 leading-relaxed">{style.desc}</p>
            </button>
          )
        })}
      </div>
    </div>
  )
}
