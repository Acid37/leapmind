import { ChevronDown, ChevronRight, Plus, Trash2, BookOpen, Target, AlertTriangle, ListOrdered, Home } from 'lucide-react'

const STEP_LABELS = ['教学目标', '重难点', '教学过程', '课后作业']

export default function OutlineEditor({ sections, activeIndex, onSelect, onAdd, onDelete }) {
  return (
    <div className="bg-white/10 backdrop-blur-md rounded-2xl border border-purple-400/20 flex flex-col overflow-hidden h-full">
      <div className="px-4 py-3 border-b border-white/10 flex items-center justify-between bg-gradient-to-r from-purple-500/10 to-transparent">
        <div className="flex items-center gap-2">
          <div className="w-7 h-7 rounded-lg bg-gradient-to-br from-purple-400 to-blue-500 flex items-center justify-center">
            <BookOpen className="w-3.5 h-3.5 text-white" />
          </div>
          <h3 className="text-sm font-semibold text-white">教学大纲</h3>
        </div>
        <span className="text-[10px] px-2 py-0.5 rounded-full bg-purple-500/20 text-purple-200 border border-purple-400/20">{sections.length} 课时</span>
      </div>

      <div className="flex-1 overflow-y-auto m5-list-scroll p-2 space-y-1">
        {sections.length === 0 && (
          <p className="text-xs text-purple-200/40 text-center py-8">暂无课时</p>
        )}
        {sections.map((section, index) => {
          const active = index === activeIndex
          const gradColors = ['from-purple-400 to-blue-500', 'from-emerald-400 to-teal-500', 'from-amber-400 to-orange-500']
          const grad = gradColors[index % gradColors.length]
          return (
            <div
              key={section.hourIndex || index}
              onClick={() => onSelect(index)}
              className={`
                rounded-xl cursor-pointer transition-all group
                ${active
                  ? 'bg-white/15 border border-purple-400/30 shadow-lg shadow-purple-500/10'
                  : 'bg-white/5 border border-transparent hover:bg-white/10'
                }
              `}
            >
              {/* 课时标题行 */}
              <div className={`flex items-center gap-2 px-3 py-2.5 ${active ? 'border-l-2 border-purple-400' : 'border-l-2 border-transparent'}`}>
                <span className={`text-[10px] px-1.5 py-0.5 rounded bg-gradient-to-r ${grad} text-white shrink-0`}>
                  第{index + 1}课时
                </span>
                <span className="flex-1 text-xs text-white/90 truncate">{section.title || `课时 ${index + 1}`}</span>
                {sections.length > 1 && (
                  <button
                    onClick={(e) => { e.stopPropagation(); onDelete(index) }}
                    className="opacity-0 group-hover:opacity-100 p-1 rounded-md hover:bg-red-500/20 text-red-300 transition-all"
                  >
                    <Trash2 className="w-3.5 h-3.5" />
                  </button>
                )}
              </div>

              {/* 环节列表 */}
              {active && (
                <div className="px-3 pb-2 pt-0.5 space-y-0.5 animate-fadeIn">
                  {STEP_LABELS.map((label, i) => {
                    const icons = [Target, AlertTriangle, ListOrdered, Home]
                    const colors = ['text-emerald-300', 'text-amber-300', 'text-fuchsia-300', 'text-teal-300']
                    const Icon = icons[i]
                    return (
                      <div key={label} className="flex items-center gap-2 px-2 py-1 rounded-lg hover:bg-white/5">
                        <Icon className={`w-3 h-3 ${colors[i]} shrink-0`} />
                        <span className="text-[11px] text-purple-200/70">{label}</span>
                      </div>
                    )
                  })}
                </div>
              )}
            </div>
          )
        })}
      </div>

      <div className="p-2 border-t border-white/10">
        <button
          onClick={onAdd}
          className="w-full py-2 rounded-xl bg-white/5 border border-dashed border-white/20 text-purple-200/60 text-xs hover:bg-white/10 hover:text-white transition-all flex items-center justify-center gap-1.5"
        >
          <Plus className="w-3.5 h-3.5" />
          添加课时
        </button>
      </div>
    </div>
  )
}
