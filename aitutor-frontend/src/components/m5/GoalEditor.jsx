import { useState } from 'react'
import { Plus, X, Sparkles, GripVertical } from 'lucide-react'
import { generateGoals } from '../../services/m5'

export default function GoalEditor({ userId, subject, grade, knowledgePointIds = [], knowledgePointNames = [], value = [], onChange }) {
  const [input, setInput] = useState('')
  const [aiLoading, setAiLoading] = useState(false)

  const addGoal = () => {
    const trimmed = input.trim()
    if (!trimmed) return
    onChange([...value, trimmed])
    setInput('')
  }

  const removeGoal = (index) => {
    onChange(value.filter((_, i) => i !== index))
  }

  const handleKeyDown = (e) => {
    if (e.key === 'Enter') {
      e.preventDefault()
      addGoal()
    }
  }

  const handleAIGenerate = async () => {
    setAiLoading(true)
    try {
      const goals = await generateGoals({
        userId: userId || 1,
        knowledgePointIds,
        knowledgePointNames,
        subject,
        grade,
      })
      if (goals && goals.length > 0) onChange(goals)
    } catch (err) {
      console.error('AI 生成目标失败:', err)
    }
    setAiLoading(false)
  }

  return (
    <div className="bg-white/10 backdrop-blur-md rounded-2xl p-5 border border-white/10">
      <div className="flex items-center justify-between mb-4">
        <div className="flex items-center gap-2">
          <Sparkles className="w-5 h-5 text-purple-300" />
          <h3 className="text-base font-semibold text-white">教学目标</h3>
        </div>
        <button
          onClick={handleAIGenerate}
          disabled={aiLoading}
          className="flex items-center gap-1.5 text-xs px-3 py-1.5 rounded-lg bg-gradient-to-r from-purple-500 to-blue-500 text-white hover:opacity-90 transition-all disabled:opacity-50"
        >
          <Sparkles className="w-3.5 h-3.5" />
          {aiLoading ? '生成中...' : 'AI 智能生成'}
        </button>
      </div>

      {/* 目标列表 */}
      <div className="space-y-2 mb-3 max-h-48 overflow-y-auto pr-1">
        {value.length === 0 && (
          <p className="text-sm text-purple-200/40 text-center py-4">暂无教学目标，请在下方添加或使用 AI 生成</p>
        )}
        {value.map((goal, index) => (
          <div
            key={index}
            className="flex items-center gap-2 bg-white/5 rounded-xl px-3 py-2.5 group border border-white/5 hover:border-white/10 transition-all"
          >
            <GripVertical className="w-4 h-4 text-purple-300/30 shrink-0 cursor-grab" />
            <span className="flex-1 text-sm text-white/90">{goal}</span>
            <button
              onClick={() => removeGoal(index)}
              className="opacity-0 group-hover:opacity-100 w-6 h-6 rounded-full bg-red-500/20 flex items-center justify-center hover:bg-red-500/30 transition-all"
            >
              <X className="w-3.5 h-3.5 text-red-300" />
            </button>
          </div>
        ))}
      </div>

      {/* 添加输入框 */}
      <div className="flex gap-2">
        <input
          type="text"
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder="输入教学目标，按回车添加..."
          className="flex-1 bg-white/5 border border-white/10 rounded-xl px-4 py-2.5 text-sm text-white placeholder-purple-200/30 outline-none focus:border-purple-400/40 focus:bg-white/10 transition-all"
        />
        <button
          onClick={addGoal}
          disabled={!input.trim()}
          className="px-3 py-2.5 rounded-xl bg-white/10 border border-white/10 text-white hover:bg-white/20 transition-all disabled:opacity-30 disabled:cursor-not-allowed"
        >
          <Plus className="w-5 h-5" />
        </button>
      </div>
    </div>
  )
}
