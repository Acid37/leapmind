import { useState } from 'react'
import { Plus, X, ArrowUp, ArrowDown, ListOrdered, Sparkles } from 'lucide-react'
import RichTextEditor from './RichTextEditor'
import { generateProcess } from '../../services/m5'

const STEP_PRESETS = [
  '课堂导入', '新知讲授', '互动探究', '巩固练习', '课堂小结',
]

export default function TeachingProcessEditor({
  value = [], onChange,
  userId, subject, grade, knowledgePointIds = [], knowledgePointNames = [],
  teachingGoals = [], sectionIndex = 1, sectionTitle = '',
}) {
  const [aiLoading, setAiLoading] = useState(false)
  const [expanded, setExpanded] = useState(0)

  const updateStep = (index, field, val) => {
    onChange(value.map((s, i) => (i === index ? { ...s, [field]: val } : s)))
  }

  const addStep = (preset) => {
    onChange([
      ...value,
      { step: preset || `教学环节${value.length + 1}`, duration: '10min', teacherActivity: '', studentActivity: '', designIntent: '' },
    ])
    setExpanded(value.length)
  }

  const removeStep = (index) => {
    onChange(value.filter((_, i) => i !== index))
  }

  const moveStep = (index, dir) => {
    const target = index + dir
    if (target < 0 || target >= value.length) return
    const next = [...value]
    ;[next[index], next[target]] = [next[target], next[index]]
    onChange(next)
  }

  const handleAIGenerate = async () => {
    setAiLoading(true)
    try {
      const steps = await generateProcess({
        userId: userId || 1,
        knowledgePointIds,
        knowledgePointNames,
        subject,
        grade,
        teachingGoals,
        totalHours: 1,
        sectionIndex,
        sectionTitle,
      })
      if (steps && steps.length > 0) {
        onChange(steps)
        setExpanded(0)
      }
    } catch (err) {
      console.error('AI 生成教学过程失败:', err)
    }
    setAiLoading(false)
  }

  return (
    <div className="bg-white/10 backdrop-blur-md rounded-2xl p-5 border border-fuchsia-400/20 overflow-hidden relative">
      {/* 顶部彩色渐变条 + 背景晕染 */}
      <div className="absolute top-0 left-0 right-0 h-0.5 bg-gradient-to-r from-fuchsia-400 to-purple-500 opacity-60" />
      <div className="absolute -top-8 -right-8 w-32 h-32 rounded-full bg-gradient-to-br from-fuchsia-500/5 to-transparent blur-2xl pointer-events-none" />
      <div className="relative flex items-center justify-between mb-4">
        <div className="flex items-center gap-3">
          <div className="w-8 h-8 rounded-xl bg-gradient-to-br from-fuchsia-400 to-purple-500 flex items-center justify-center shadow-lg">
            <ListOrdered className="w-4.5 h-4.5 text-white" />
          </div>
          <h3 className="text-base font-semibold text-white">教学过程</h3>
        </div>
        <button
          onClick={handleAIGenerate}
          disabled={aiLoading}
          className="flex items-center gap-1.5 text-xs px-3 py-1.5 rounded-lg bg-gradient-to-r from-purple-500 to-blue-500 text-white hover:opacity-90 transition-all disabled:opacity-50"
        >
          <Sparkles className="w-3.5 h-3.5" />
          {aiLoading ? '生成中...' : 'AI 生成教学过程'}
        </button>
      </div>

      {/* 预设快速添加 */}
      <div className="flex flex-wrap gap-1.5 mb-4">
        {STEP_PRESETS.map((preset, i) => {
          const presetColors = ['hover:bg-sky-500/20 hover:text-sky-100 hover:border-sky-400/30', 'hover:bg-emerald-500/20 hover:text-emerald-100 hover:border-emerald-400/30', 'hover:bg-amber-500/20 hover:text-amber-100 hover:border-amber-400/30', 'hover:bg-rose-500/20 hover:text-rose-100 hover:border-rose-400/30', 'hover:bg-fuchsia-500/20 hover:text-fuchsia-100 hover:border-fuchsia-400/30']
          return (
            <button
              key={preset}
              onClick={() => addStep(preset)}
              className={`text-[11px] px-2.5 py-1 rounded-full bg-white/5 border border-white/10 text-purple-200/70 transition-all ${presetColors[i % presetColors.length]}`}
            >
              + {preset}
            </button>
          )
        })}
      </div>

      {/* 步骤列表 */}
      <div className="space-y-2">
        {value.length === 0 && (
          <p className="text-sm text-purple-200/40 text-center py-4">暂无教学环节，点击上方按钮添加</p>
        )}
        {value.map((step, index) => {
          const isOpen = expanded === index
          const stepColors = ['from-sky-400 to-blue-500', 'from-emerald-400 to-teal-500', 'from-amber-400 to-orange-500', 'from-rose-400 to-pink-500', 'from-fuchsia-400 to-purple-500']
          const gradient = stepColors[index % stepColors.length]
          return (
            <div key={index} className={`bg-white/5 border rounded-xl overflow-hidden transition-all ${isOpen ? 'border-fuchsia-400/20' : 'border-white/5 hover:border-white/10'}`}>
              {/* 步骤头 */}
              <div
                onClick={() => setExpanded(isOpen ? -1 : index)}
                className="flex items-center gap-2 px-3 py-2.5 cursor-pointer"
              >
                <span className={`text-[10px] w-5 h-5 flex items-center justify-center rounded-full bg-gradient-to-br ${gradient} text-white shrink-0`}>{index + 1}</span>
                <input
                  type="text"
                  value={step.step}
                  onClick={(e) => e.stopPropagation()}
                  onChange={(e) => updateStep(index, 'step', e.target.value)}
                  placeholder="环节名称"
                  className="flex-1 bg-transparent text-sm text-white outline-none placeholder-purple-200/30"
                />
                <span className="text-[10px] text-purple-200/40 shrink-0">{step.duration || '10min'}</span>
                <div className="flex items-center gap-0.5 shrink-0">
                  <button onClick={(e) => { e.stopPropagation(); moveStep(index, -1) }} disabled={index === 0} className="p-1 rounded-md hover:bg-white/10 text-white/40 hover:text-white disabled:opacity-20">
                    <ArrowUp className="w-3.5 h-3.5" />
                  </button>
                  <button onClick={(e) => { e.stopPropagation(); moveStep(index, 1) }} disabled={index === value.length - 1} className="p-1 rounded-md hover:bg-white/10 text-white/40 hover:text-white disabled:opacity-20">
                    <ArrowDown className="w-3.5 h-3.5" />
                  </button>
                  <button onClick={(e) => { e.stopPropagation(); removeStep(index) }} className="p-1 rounded-md hover:bg-red-500/20 text-red-300">
                    <X className="w-3.5 h-3.5" />
                  </button>
                </div>
              </div>

              {/* 步骤详情 */}
              {isOpen && (
                <div className="px-3 pb-3 space-y-2 animate-fadeIn">
                  <div className="flex items-center gap-2">
                    <span className="text-[10px] text-purple-200/50 shrink-0">时长</span>
                    <input
                      type="text"
                      value={step.duration || ''}
                      onChange={(e) => updateStep(index, 'duration', e.target.value)}
                      placeholder="如 10min"
                      className="w-24 bg-white/5 border border-white/10 rounded-lg px-2 py-1.5 text-xs text-white outline-none focus:border-purple-400/40 placeholder-purple-200/30"
                    />
                  </div>
                  <div>
                    <p className="text-[10px] text-purple-200/50 mb-1">教师活动</p>
                    <RichTextEditor
                      value={step.teacherActivity || ''}
                      onChange={(html) => updateStep(index, 'teacherActivity', html)}
                      placeholder="描述教师在这一环节的活动..."
                      minHeight={60}
                    />
                  </div>
                  <div>
                    <p className="text-[10px] text-purple-200/50 mb-1">学生活动</p>
                    <RichTextEditor
                      value={step.studentActivity || ''}
                      onChange={(html) => updateStep(index, 'studentActivity', html)}
                      placeholder="描述学生在这一环节的活动..."
                      minHeight={60}
                    />
                  </div>
                  <div>
                    <p className="text-[10px] text-purple-200/50 mb-1">设计意图</p>
                    <RichTextEditor
                      value={step.designIntent || ''}
                      onChange={(html) => updateStep(index, 'designIntent', html)}
                      placeholder="说明该环节的设计意图..."
                      minHeight={60}
                    />
                  </div>
                </div>
              )}
            </div>
          )
        })}
      </div>
    </div>
  )
}
