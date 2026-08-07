import { useState } from 'react'
import { Plus, X } from 'lucide-react'

const COLORS = {
  purple: {
    chip: 'bg-white/10 text-white/90 border-white/10',
    remove: 'text-white/40 hover:text-red-300',
    input: 'focus:border-purple-400/40',
    btn: 'bg-white/10 border-white/10 text-white hover:bg-white/20',
  },
  emerald: {
    chip: 'bg-emerald-500/10 text-emerald-100 border-emerald-400/20',
    remove: 'text-emerald-200/40 hover:text-emerald-100',
    input: 'focus:border-emerald-400/40',
    btn: 'bg-emerald-500/20 border-emerald-400/20 text-emerald-100 hover:bg-emerald-500/30',
  },
  amber: {
    chip: 'bg-amber-500/10 text-amber-100 border-amber-400/20',
    remove: 'text-amber-200/40 hover:text-amber-100',
    input: 'focus:border-amber-400/40',
    btn: 'bg-amber-500/20 border-amber-400/20 text-amber-100 hover:bg-amber-500/30',
  },
  rose: {
    chip: 'bg-rose-500/10 text-rose-100 border-rose-400/20',
    remove: 'text-rose-200/40 hover:text-rose-100',
    input: 'focus:border-rose-400/40',
    btn: 'bg-rose-500/20 border-rose-400/20 text-rose-100 hover:bg-rose-500/30',
  },
  teal: {
    chip: 'bg-teal-500/10 text-teal-100 border-teal-400/20',
    remove: 'text-teal-200/40 hover:text-teal-100',
    input: 'focus:border-teal-400/40',
    btn: 'bg-teal-500/20 border-teal-400/20 text-teal-100 hover:bg-teal-500/30',
  },
  cyan: {
    chip: 'bg-cyan-500/10 text-cyan-100 border-cyan-400/20',
    remove: 'text-cyan-200/40 hover:text-cyan-100',
    input: 'focus:border-cyan-400/40',
    btn: 'bg-cyan-500/20 border-cyan-400/20 text-cyan-100 hover:bg-cyan-500/30',
  },
  indigo: {
    chip: 'bg-indigo-500/10 text-indigo-100 border-indigo-400/20',
    remove: 'text-indigo-200/40 hover:text-indigo-100',
    input: 'focus:border-indigo-400/40',
    btn: 'bg-indigo-500/20 border-indigo-400/20 text-indigo-100 hover:bg-indigo-500/30',
  },
}

export default function TagListEditor({ value = [], onChange, placeholder = '输入后按回车添加...', color = 'purple' }) {
  const [input, setInput] = useState('')
  const theme = COLORS[color] || COLORS.purple

  const addItem = () => {
    const trimmed = input.trim()
    if (!trimmed) return
    onChange([...value, trimmed])
    setInput('')
  }

  const removeItem = (index) => {
    onChange(value.filter((_, i) => i !== index))
  }

  const handleKeyDown = (e) => {
    if (e.key === 'Enter') {
      e.preventDefault()
      addItem()
    }
  }

  return (
    <div>
      <div className="flex flex-wrap gap-1.5 mb-2">
        {value.length === 0 && (
          <p className="text-sm text-purple-200/40">暂无内容</p>
        )}
        {value.map((item, index) => (
          <span
            key={index}
            className={`inline-flex items-center gap-1.5 text-xs px-2.5 py-1 rounded-lg border group ${theme.chip}`}
          >
            {item}
            <button onClick={() => removeItem(index)} className={`transition-colors ${theme.remove}`}>
              <X className="w-3 h-3" />
            </button>
          </span>
        ))}
      </div>
      <div className="flex gap-2">
        <input
          type="text"
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder={placeholder}
          className={`flex-1 bg-white/5 border border-white/10 rounded-xl px-3 py-2 text-sm text-white placeholder-purple-200/30 outline-none focus:bg-white/10 transition-all ${theme.input}`}
        />
        <button
          onClick={addItem}
          disabled={!input.trim()}
          className={`px-3 py-2 rounded-xl border transition-all disabled:opacity-30 disabled:cursor-not-allowed ${theme.btn}`}
        >
          <Plus className="w-4 h-4" />
        </button>
      </div>
    </div>
  )
}
