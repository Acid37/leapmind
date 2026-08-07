import ReactMarkdown from 'react-markdown'
import remarkMath from 'remark-math'
import rehypeKatex from 'rehype-katex'
import 'katex/dist/katex.min.css'
import { Sparkles } from 'lucide-react'

export default function ExplainContent({ steps, currentStep, loading }) {
  const step = steps[currentStep]

  if (loading && !step) {
    return (
      <div className="text-center py-12">
        <div className="w-10 h-10 mx-auto mb-3 relative">
          <div className="w-10 h-10 border-3 border-purple-300/30 border-t-purple-400 rounded-full animate-spin" />
        </div>
        <p className="text-purple-200 text-sm">AI 正在生成讲题内容...</p>
        <div className="mt-3 flex justify-center gap-1.5">
          {[0,1,2].map(i => (
            <div key={i} className="w-1.5 h-1.5 bg-purple-300/50 rounded-full animate-bounce" style={{ animationDelay: `${i * 0.15}s` }} />
          ))}
        </div>
      </div>
    )
  }

  if (!step) {
    return (
      <div className="text-center py-12 text-purple-200/50">
        <Sparkles className="w-8 h-8 mx-auto mb-2 opacity-30" />
        <p className="text-sm">点击下方按钮开始讲题</p>
      </div>
    )
  }

  return (
    <div className="animate-fadeIn">
      <div className="flex items-center gap-2 mb-4">
        <div className="w-7 h-7 rounded-full bg-gradient-to-r from-purple-400 to-blue-400 flex items-center justify-center text-xs font-bold text-white shadow-sm">
          {currentStep + 1}
        </div>
        <h3 className="text-base font-bold text-white">{step.title}</h3>
      </div>
      <div className="bg-white/5 rounded-xl p-5 prose prose-sm max-w-none text-white/90 border border-white/5">
        <ReactMarkdown
          remarkPlugins={[remarkMath]}
          rehypePlugins={[rehypeKatex]}
          components={{
            strong: ({ children }) => <span className="text-amber-300 font-bold bg-amber-500/10 px-0.5 rounded">{children}</span>,
            p: ({ children }) => <p className="text-white/80 leading-relaxed mb-2">{children}</p>,
            code: ({ children }) => <code className="bg-white/10 text-cyan-200 px-1.5 py-0.5 rounded text-sm">{children}</code>,
          }}
        >
          {step.content}
        </ReactMarkdown>
      </div>
    </div>
  )
}
