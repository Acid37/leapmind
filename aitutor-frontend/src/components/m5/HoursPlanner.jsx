import { useState } from 'react'
import { Minus, Plus, Clock } from 'lucide-react'

export default function HoursPlanner({ value = 1, onChange }) {
  const decrease = () => {
    if (value > 1) onChange(value - 1)
  }
  const increase = () => {
    if (value < 10) onChange(value + 1)
  }

  return (
    <div className="bg-white/10 backdrop-blur-md rounded-2xl p-5 border border-white/10">
      <div className="flex items-center gap-2 mb-4">
        <Clock className="w-5 h-5 text-purple-300" />
        <h3 className="text-base font-semibold text-white">课时数</h3>
        <span className="text-xs text-purple-200/50 ml-1">设置本节课需要的课时</span>
      </div>

      <div className="flex items-center justify-center gap-6">
        <button
          onClick={decrease}
          disabled={value <= 1}
          className="w-10 h-10 rounded-xl bg-white/10 border border-white/10 flex items-center justify-center text-white hover:bg-white/20 transition-all disabled:opacity-30 disabled:cursor-not-allowed"
        >
          <Minus className="w-5 h-5" />
        </button>

        <div className="flex flex-col items-center">
          <span className="text-4xl font-bold text-white tabular-nums">{value}</span>
          <span className="text-xs text-purple-200/50 mt-1">课时</span>
        </div>

        <button
          onClick={increase}
          disabled={value >= 10}
          className="w-10 h-10 rounded-xl bg-white/10 border border-white/10 flex items-center justify-center text-white hover:bg-white/20 transition-all disabled:opacity-30 disabled:cursor-not-allowed"
        >
          <Plus className="w-5 h-5" />
        </button>
      </div>

      {/* 课时进度条 */}
      <div className="mt-4 flex gap-1.5 justify-center">
        {Array.from({ length: value }, (_, i) => (
          <div
            key={i}
            className="w-8 h-1.5 rounded-full bg-gradient-to-r from-purple-400 to-blue-400 transition-all"
          />
        ))}
        {Array.from({ length: 10 - value }, (_, i) => (
          <div
            key={i}
            className="w-8 h-1.5 rounded-full bg-white/10 transition-all"
          />
        ))}
      </div>
    </div>
  )
}
