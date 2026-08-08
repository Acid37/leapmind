import { useState } from 'react'
import ReactMarkdown from 'react-markdown'
import remarkMath from 'remark-math'
import rehypeKatex from 'rehype-katex'
import 'katex/dist/katex.min.css'
import { Sparkles, ThumbsUp, HelpCircle, BookOpen, BookmarkPlus, BookmarkCheck, Send } from 'lucide-react'
import { mockPhotoQA, mockAddToWrongBook } from '../../services/m2'
import { useChatSession } from '../../hooks/useChatSession'
import { getUserInfo } from '../../utils/tokenManager'

export default function QAResultPanel({ ocrRecordId, question, onKnowledgePointClick }) {
  const [content, setContent] = useState('')
  const [loading, setLoading] = useState(false)
  const [done, setDone] = useState(false)
  const [knowledgePoints, setKnowledgePoints] = useState([])
  const [similarQuestions, setSimilarQuestions] = useState([])
  const [inWrongBook, setInWrongBook] = useState(false)
  const [addingToWrong, setAddingToWrong] = useState(false)
  const [followUp, setFollowUp] = useState('')

  const userId = getUserInfo()?.userId || 1
  const { messages: followUpMsgs, isGenerating: followUpLoading, send: sendFollowUp } = useChatSession({
    sceneType: 'doing_exercise',
    context: { questionId: ocrRecordId },
    userId,
    autoRestore: false,
  })

  const handleGenerate = async () => {
    setLoading(true)
    setContent('')
    setDone(false)
    setKnowledgePoints([])
    setSimilarQuestions([])

    await mockPhotoQA(
      ocrRecordId,
      question,
      (chunk) => {
        if (chunk.type === 'done') {
          setDone(true)
          setLoading(false)
        } else if (chunk.type === 'knowledge') {
          try {
            setKnowledgePoints(JSON.parse(chunk.content))
          } catch {
            // plain text fallback
          }
        } else if (chunk.type === 'similar') {
          try {
            setSimilarQuestions(JSON.parse(chunk.content))
          } catch {
            // plain text fallback
          }
        } else {
          setContent(prev => prev + chunk.content + '\n\n')
        }
      },
      (err) => {
        console.error('SSE error:', err)
        setLoading(false)
      }
    )
  }

  const handleAddToWrongBook = async () => {
    setAddingToWrong(true)
    await mockAddToWrongBook(ocrRecordId)
    setInWrongBook(true)
    setAddingToWrong(false)
  }

  const handleFollowUp = () => {
    if (!followUp.trim() || followUpLoading) return
    sendFollowUp(followUp.trim())
    setFollowUp('')
  }

  return (
    <div className="bg-white/10 backdrop-blur-md rounded-2xl p-5 border border-white/10">
      {/* 按钮区域 */}
      {!loading && !done && (
        <button
          onClick={handleGenerate}
          className="w-full py-3.5 bg-gradient-to-r from-purple-500 to-blue-500 text-white rounded-xl font-medium hover:shadow-lg hover:shadow-purple-500/30 transition-all flex items-center justify-center gap-2 group"
        >
          <Sparkles className="w-5 h-5 group-hover:animate-pulse" />
          开始答疑
        </button>
      )}

      {/* 加载动画 */}
      {loading && !done && (
        <div className="text-center py-6">
          <div className="w-12 h-12 mx-auto relative mb-4">
            <div className="w-12 h-12 border-4 border-purple-300/30 border-t-purple-400 rounded-full animate-spin" />
          </div>
          <p className="text-purple-200 text-sm font-medium">AI 思考中，请稍候...</p>
          <div className="mt-3 flex justify-center gap-1.5">
            {[0,1,2].map(i => (
              <div key={i} className="w-2 h-2 bg-purple-300/50 rounded-full animate-bounce" style={{ animationDelay: `${i * 0.15}s` }} />
            ))}
          </div>
        </div>
      )}

      {/* 内容展示 */}
      {content && (
        <div className="mt-2">
          <div className="flex items-center gap-2 mb-3">
            <Sparkles className="w-4 h-4 text-purple-300" />
            <span className="text-sm font-medium text-white">AI 解答</span>
            {!done && (
              <span className="text-xs text-purple-200/50 animate-pulse">生成中...</span>
            )}
          </div>
          <div className="bg-white/5 rounded-xl p-4 prose prose-sm max-w-none text-white/90 border border-white/5">
            <ReactMarkdown
              remarkPlugins={[remarkMath]}
              rehypePlugins={[rehypeKatex]}
              components={{
                strong: ({ children }) => <span className="text-amber-300 font-bold bg-amber-500/10 px-0.5 rounded">{children}</span>,
                p: ({ children }) => <p className="text-white/80 leading-relaxed mb-2">{children}</p>,
              }}
            >
              {content}
            </ReactMarkdown>
          </div>
        </div>
      )}

      {/* 关联知识点 */}
      {done && knowledgePoints.length > 0 && (
        <div className="mt-4">
          <p className="text-xs font-medium text-purple-200/70 mb-2">关联知识点</p>
          <div className="flex flex-wrap gap-1.5">
            {knowledgePoints.map(kp => (
              <button
                key={kp.id}
                onClick={() => onKnowledgePointClick?.(kp)}
                className="text-[11px] px-2.5 py-1 rounded-full bg-gradient-to-r from-amber-500/20 to-orange-500/20 text-amber-300 border border-amber-400/20 font-medium hover:from-amber-500/30 hover:to-orange-500/30 hover:border-amber-300/40 transition-all cursor-pointer"
                title="查看知识点详情"
              >
                {kp.name}
              </button>
            ))}
          </div>
        </div>
      )}

      {/* 同类题推荐 */}
      {done && similarQuestions.length > 0 && (
        <div className="mt-4 bg-white/5 rounded-xl p-4 border border-white/5">
          <div className="flex items-center gap-1.5 mb-2">
            <BookOpen className="w-3.5 h-3.5 text-blue-300" />
            <span className="text-xs font-medium text-blue-200/70">同类题推荐</span>
          </div>
          <div className="space-y-1.5">
            {similarQuestions.map(q => (
              <div key={q.id} className="text-xs text-white/60 leading-relaxed pl-3 border-l-2 border-blue-400/20">
                {q.stem}
              </div>
            ))}
          </div>
        </div>
      )}

      {/* 完成后的操作区 */}
      {done && (
        <div className="mt-4 space-y-3">
          {/* 反馈按钮 */}
          <div className="flex gap-3">
            <button className="flex-1 px-4 py-2 bg-green-500/20 text-green-300 rounded-xl text-sm font-medium hover:bg-green-500/30 transition-all flex items-center justify-center gap-1.5 border border-green-400/20">
              <ThumbsUp className="w-4 h-4" /> 懂了
            </button>
            <button className="flex-1 px-4 py-2 bg-yellow-500/20 text-yellow-300 rounded-xl text-sm font-medium hover:bg-yellow-500/30 transition-all flex items-center justify-center gap-1.5 border border-yellow-400/20">
              <HelpCircle className="w-4 h-4" /> 还有疑问
            </button>
          </div>

          {/* 添加到错题本 */}
          <button
            onClick={handleAddToWrongBook}
            disabled={inWrongBook || addingToWrong}
            className={`w-full py-2.5 rounded-xl text-sm font-medium transition-all flex items-center justify-center gap-2 ${
              inWrongBook
                ? 'bg-green-500/15 text-green-300 border border-green-400/20'
                : 'bg-white/10 text-white/70 hover:bg-white/20 border border-white/10'
            }`}
          >
            {addingToWrong ? (
              <div className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin" />
            ) : inWrongBook ? (
              <BookmarkCheck className="w-4 h-4" />
            ) : (
              <BookmarkPlus className="w-4 h-4" />
            )}
            {inWrongBook ? '已添加到错题本' : '添加到错题本'}
          </button>

          {/* 继续追问 */}
          <div className="bg-white/5 rounded-xl overflow-hidden border border-white/5">
            {followUpMsgs.length > 0 && (
              <div className="px-3 py-2 max-h-32 overflow-y-auto space-y-1.5 border-b border-white/5">
                {followUpMsgs.map((msg, i) => (
                  <div key={i} className={`flex ${msg.role === 'user' ? 'justify-end' : 'justify-start'}`}>
                    <div className={`max-w-[90%] px-2.5 py-1.5 rounded-lg text-xs leading-relaxed ${
                      msg.role === 'user'
                        ? 'bg-gradient-to-r from-purple-500 to-blue-500 text-white'
                        : 'bg-white/10 text-white/70'
                    }`}>
                      {msg.content}
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
            <div className="flex items-center gap-2 px-3 py-2">
              <input
                value={followUp}
                onChange={e => setFollowUp(e.target.value)}
                onKeyDown={e => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); handleFollowUp(); } }}
                placeholder="继续追问..."
                className="flex-1 bg-white/5 border border-white/10 rounded-lg px-3 py-1.5 text-sm text-white/90 placeholder-white/30 focus:outline-none focus:border-purple-400/50"
              />
              <button
                onClick={handleFollowUp}
                disabled={!followUp.trim() || followUpLoading}
                className="w-8 h-8 rounded-lg bg-gradient-to-r from-purple-500 to-blue-500 text-white flex items-center justify-center disabled:opacity-40 hover:shadow-lg hover:shadow-purple-500/20 transition-all shrink-0"
              >
                <Send className="w-3.5 h-3.5" />
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
