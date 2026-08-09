import { useState } from 'react'
import { Send, MessageCircle, X, AlertCircle } from 'lucide-react'
import { useChatSession } from '../../hooks/useChatSession'
import { getUserInfo } from '../../utils/tokenManager'

export default function AskMoreButton({ questionContext }) {
  const [open, setOpen] = useState(false)
  const [input, setInput] = useState('')

  const userId = getUserInfo()?.userId || 1
  const context = questionContext
    ? { questionId: questionContext.questionId || questionContext.id, relatedKpId: questionContext.knowledgePoints?.[0]?.id }
    : {}

  const { messages, isGenerating, error, send, clear } = useChatSession({
    sceneType: 'explaining',
    context,
    userId,
    autoRestore: false,
  })

  const handleSubmit = () => {
    if (!input.trim() || isGenerating) return
    send(input.trim())
    setInput('')
  }

  const handleKeyDown = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      handleSubmit()
    }
  }

  return (
    <div>
      {!open ? (
        <button
          onClick={() => setOpen(true)}
          className="w-full py-2.5 bg-white/10 border border-white/10 rounded-xl text-sm text-white/70 hover:bg-white/20 hover:text-white transition-all flex items-center justify-center gap-2"
        >
          <MessageCircle className="w-4 h-4" />
          还有疑问？点击追问
        </button>
      ) : (
        <div className="bg-white/10 backdrop-blur-md rounded-xl border border-white/10 overflow-hidden">
          <div className="flex items-center justify-between px-4 py-2.5 border-b border-white/10">
            <span className="text-sm font-medium text-white/80 flex items-center gap-1.5">
              <MessageCircle className="w-4 h-4 text-purple-300" />
              追问
            </span>
            <div className="flex items-center gap-2">
              {messages.length > 0 && (
                <button onClick={clear} className="text-[10px] text-white/30 hover:text-white/60 transition-colors">清空</button>
              )}
              <button onClick={() => setOpen(false)} className="text-white/40 hover:text-white/70 transition-colors">
                <X className="w-4 h-4" />
              </button>
            </div>
          </div>

          {error && (
            <div className="px-4 py-2 bg-red-500/10 border-b border-red-400/10 flex items-center gap-1.5">
              <AlertCircle className="w-3 h-3 text-red-300" />
              <span className="text-[11px] text-red-200/80">{error}</span>
            </div>
          )}

          {messages.length > 0 && (
            <div className="px-4 py-3 max-h-40 overflow-y-auto space-y-2">
              {messages.map((msg, i) => (
                <div key={i} className={`flex ${msg.role === 'user' ? 'justify-end' : 'justify-start'}`}>
                  <div
                    className={`max-w-[85%] px-3 py-2 rounded-xl text-xs leading-relaxed ${
                      msg.role === 'user'
                        ? 'bg-gradient-to-r from-purple-500 to-blue-500 text-white'
                        : msg.error
                          ? 'bg-red-500/10 text-red-200/80 border border-red-400/10'
                          : 'bg-white/10 text-white/70'
                    }`}
                  >
                    {msg.content || (msg.isStreaming ? '' : '生成中断')}
                    {msg.isStreaming && (
                      <span className="inline-flex gap-0.5 ml-1">
                        <span className="w-1 h-1 bg-white/40 rounded-full animate-bounce" style={{ animationDelay: '0s' }} />
                        <span className="w-1 h-1 bg-white/40 rounded-full animate-bounce" style={{ animationDelay: '0.15s' }} />
                        <span className="w-1 h-1 bg-white/40 rounded-full animate-bounce" style={{ animationDelay: '0.3s' }} />
                      </span>
                    )}
                  </div>
                </div>
              ))}
            </div>
          )}

          <div className="flex items-center gap-2 px-3 py-2 border-t border-white/10">
            <input
              value={input}
              onChange={e => setInput(e.target.value)}
              onKeyDown={handleKeyDown}
              placeholder="输入你的问题..."
              className="flex-1 bg-white/5 border border-white/10 rounded-lg px-3 py-1.5 text-sm text-white/90 placeholder-white/30 focus:outline-none focus:border-purple-400/50"
            />
            <button
              onClick={handleSubmit}
              disabled={!input.trim() || isGenerating}
              className="w-8 h-8 rounded-lg bg-gradient-to-r from-purple-500 to-blue-500 text-white flex items-center justify-center disabled:opacity-40 hover:shadow-lg hover:shadow-purple-500/20 transition-all shrink-0"
            >
              <Send className="w-3.5 h-3.5" />
            </button>
          </div>
        </div>
      )}
    </div>
  )
}
