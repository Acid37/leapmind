import { useState, useEffect, useCallback } from 'react'
import {
  ArrowLeft, Sparkles, ChevronDown, ChevronUp, ChevronLeft, ChevronRight,
  RefreshCw, Target, BookOpen, ThumbsUp, HelpCircle, Clock, History
} from 'lucide-react'
import StepProgress from '../../components/m2/StepProgress'
import ExplainContent from '../../components/m2/ExplainContent'
import VoicePlayButton from '../../components/m2/VoicePlayButton'
import AskMoreButton from '../../components/m2/AskMoreButton'
import { mockGenerateExplain, mockGetWrongQuestions, mockGetExplainDetail } from '../../services/m2'

const scrollbarStyles = `
  .explain-scroll::-webkit-scrollbar { width: 4px; }
  .explain-scroll::-webkit-scrollbar-track { background: transparent; }
  .explain-scroll::-webkit-scrollbar-thumb { background: rgba(255,255,255,0.15); border-radius: 4px; }
  .explain-scroll::-webkit-scrollbar-thumb:hover { background: rgba(255,255,255,0.25); }
  .explain-msg-scroll::-webkit-scrollbar { width: 3px; }
  .explain-msg-scroll::-webkit-scrollbar-track { background: transparent; }
  .explain-msg-scroll::-webkit-scrollbar-thumb { background: rgba(255,255,255,0.1); border-radius: 3px; }
`

const WRONG_REASON_LABELS = {
  concept_unclear: '概念不清',
  careless: '粗心大意',
  formula_wrong: '公式错误',
  method_wrong: '方法错误'
}

const WRONG_REASON_COLORS = {
  concept_unclear: 'bg-orange-500/20 text-orange-300 border-orange-400/20',
  careless: 'bg-yellow-500/20 text-yellow-300 border-yellow-400/20',
  formula_wrong: 'bg-red-500/20 text-red-300 border-red-400/20',
  method_wrong: 'bg-blue-500/20 text-blue-300 border-blue-400/20'
}

const STEP_LABELS = [
  { title: '审题' },
  { title: '分析' },
  { title: '求解' },
  { title: '验证' },
  { title: '总结' }
]

function QuestionCard({ question, expanded, onToggle, onKnowledgePointClick }) {
  const q = question?.questionContent || {}
  const isChoice = q.type === 'single_choice' || q.type === 'multi_choice'

  return (
    <div className="bg-white/10 backdrop-blur-md rounded-2xl border border-white/10 overflow-hidden">
      <button
        onClick={onToggle}
        className="w-full px-5 py-3.5 flex items-center justify-between hover:bg-white/5 transition-colors"
      >
        <div className="flex items-center gap-2.5">
          <Target className="w-4 h-4 text-purple-300" />
          <span className="text-sm font-medium text-white">原题回顾</span>
          <span className={`text-xs px-2 py-0.5 rounded-full border ${WRONG_REASON_COLORS[question?.wrongReasonTag] || 'bg-white/10 text-white/60'}`}>
            {WRONG_REASON_LABELS[question?.wrongReasonTag] || question?.wrongReasonTag}
          </span>
        </div>
        {expanded ? <ChevronUp className="w-4 h-4 text-white/40" /> : <ChevronDown className="w-4 h-4 text-white/40" />}
      </button>
      {expanded && (
        <div className="px-5 pb-4 border-t border-white/5 pt-3 animate-fadeIn">
          <p className="text-sm text-white/80 leading-relaxed mb-3">{q.stem}</p>
          {isChoice && q.options && (
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-1.5 mb-3">
              {q.options.map((opt, i) => (
                <div key={i} className={`px-3 py-2 rounded-lg text-xs border ${
                  String.fromCharCode(65 + i) === question?.correctAnswer
                    ? 'bg-green-500/15 border-green-400/20 text-green-300'
                    : String.fromCharCode(65 + i) === question?.userAnswer?.selected
                      ? 'bg-red-500/15 border-red-400/20 text-red-300'
                      : 'bg-white/5 border-white/5 text-white/60'
                }`}>
                  <span className="font-medium mr-1">{String.fromCharCode(65 + i)}.</span>
                  {opt}
                  {String.fromCharCode(65 + i) === question?.correctAnswer && ' ✓'}
                </div>
              ))}
            </div>
          )}
          {question?.knowledgePoints && (
            <div className="flex flex-wrap gap-1.5">
              {question.knowledgePoints.map(kp => (
                <button
                  key={kp.id}
                  onClick={(e) => { e.stopPropagation(); onKnowledgePointClick?.(kp); }}
                  className="text-[11px] px-2.5 py-1 rounded-full bg-gradient-to-r from-amber-500/20 to-orange-500/20 text-amber-300 border border-amber-400/20 hover:from-amber-500/30 hover:to-orange-500/30 hover:border-amber-300/40 transition-all cursor-pointer font-medium"
                  title="查看知识点详情"
                >
                  {kp.name}
                </button>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  )
}

export default function ExplainPage({ onBack, replayId, onExplainHistory }) {
  const [wrongQuestions, setWrongQuestions] = useState([])
  const [selectedQuestion, setSelectedQuestion] = useState(null)
  const [questionExpanded, setQuestionExpanded] = useState(true)
  const [selectMode, setSelectMode] = useState(!replayId)
  const [replayData, setReplayData] = useState(null)

  const [explainSteps, setExplainSteps] = useState([])
  const [currentStep, setCurrentStep] = useState(0)
  const [loading, setLoading] = useState(false)
  const [generating, setGenerating] = useState(false)
  const [done, setDone] = useState(false)
  const [tip, setTip] = useState('')
  const [similar, setSimilar] = useState('')

  const [feedback, setFeedback] = useState(null)
  const [toast, setToast] = useState(null)
  const [replayLoading, setReplayLoading] = useState(false)

  // 回放模式：加载历史讲题数据
  useEffect(() => {
    if (!replayId) return
    const loadReplay = async () => {
      setReplayLoading(true)
      const data = await mockGetExplainDetail(replayId)
      setReplayData(data)
      setSelectedQuestion({
        id: data.id,
        questionContent: data.questionContent,
        userAnswer: data.userAnswer,
        correctAnswer: data.correctAnswer,
        wrongReasonTag: data.wrongReasonTag,
        knowledgePoints: data.knowledgePoints
      })
      setExplainSteps(data.steps || [])
      setTip(data.tip || '')
      setDone(true)
      setReplayLoading(false)
    }
    loadReplay()
  }, [replayId])

  useEffect(() => {
    if (toast) {
      const t = setTimeout(() => setToast(null), 2000)
      return () => clearTimeout(t)
    }
  }, [toast])

  useEffect(() => {
    const loadQuestions = async () => {
      const data = await mockGetWrongQuestions()
      setWrongQuestions(data.items || [])
    }
    loadQuestions()
  }, [])

  const handleSelectQuestion = useCallback((question) => {
    setSelectedQuestion(question)
    setSelectMode(false)
    setExplainSteps([])
    setCurrentStep(0)
    setLoading(true)
    setGenerating(false)
    setDone(false)
    setTip('')
    setSimilar('')
    setFeedback(null)
    setQuestionExpanded(true)
    setLoading(false)
  }, [])

  const handleBackToList = useCallback(() => {
    setSelectMode(true)
    setSelectedQuestion(null)
    setExplainSteps([])
  }, [])

  const handleGenerate = useCallback(async () => {
    if (!selectedQuestion) return
    setGenerating(true)
    setExplainSteps([])
    setCurrentStep(0)
    setDone(false)
    setTip('')
    setSimilar('')

    const steps = []

    await mockGenerateExplain(
      {
        wrongQuestionId: selectedQuestion.id,
        userAnswer: selectedQuestion.userAnswer,
        correctAnswer: selectedQuestion.correctAnswer,
        wrongReasonTag: selectedQuestion.wrongReasonTag,
        knowledgePoints: selectedQuestion.knowledgePoints || []
      },
      (chunk) => {
        if (chunk.type === 'overview') {
          steps.push({ title: '概述', content: chunk.content })
          setExplainSteps([...steps])
        } else if (chunk.type === 'step') {
          steps.push({ title: chunk.title, content: chunk.content })
          setExplainSteps([...steps])
          if (steps.length === 1) setCurrentStep(0)
        } else if (chunk.type === 'tip') {
          setTip(chunk.content)
        } else if (chunk.type === 'similar') {
          setSimilar(chunk.content)
        } else if (chunk.type === 'done') {
          setDone(true)
          setGenerating(false)
        }
      },
      () => {
        setGenerating(false)
      }
    )
  }, [selectedQuestion])

  useEffect(() => {
    if (selectedQuestion && !generating && explainSteps.length === 0 && !replayId) {
      handleGenerate()
    }
  }, [selectedQuestion])

  const allSteps = explainSteps.length >= 2 ? explainSteps : null
  const stepLabels = allSteps
    ? allSteps.map((s, i) => ({ title: s.title?.slice(0, 2) || `步骤${i + 1}` }))
    : STEP_LABELS

  return (
    <>
      <style>{scrollbarStyles}</style>
      <div
        className="fixed inset-0 flex flex-col"
        style={{
          backgroundImage: "linear-gradient(135deg, #861FCE 0%, #861FCE 16%, #731CCD 16%, #731CCD 32%, #6B1CCF 32%, #6B1CCF 48%, #631DCE 48%, #631DCE 64%, #5A1BCE 64%, #5A1BCE 80%, rgb(86, 43, 205) 80%, rgb(47, 8, 154) 100%)",
          backgroundAttachment: "fixed"
        }}
      >
        <header className="shrink-0 px-6 py-4 flex items-center border-b border-purple-400/20">
          <button onClick={replayId ? onBack : (selectMode ? onBack : handleBackToList)} className="flex items-center gap-2 text-white/80 hover:text-white transition-colors shrink-0">
            <ArrowLeft className="w-5 h-5" />
            <span className="text-sm font-medium">{replayId ? '返回' : (selectMode ? '返回' : '换一题')}</span>
          </button>
          <div className="flex items-center gap-2 flex-1 justify-center">
            <Sparkles className="w-5 h-5 text-purple-200" />
            <h1 className="text-lg font-bold text-white">{replayId ? '讲题回放' : 'AI 讲题'}</h1>
          </div>
          <button
            onClick={onExplainHistory}
            className="flex items-center gap-1.5 px-3.5 py-1.5 rounded-lg bg-white/15 text-white/85 hover:bg-white/25 hover:text-white border border-white/15 transition-all shrink-0"
            title="讲题历史"
          >
            <History className="w-3.5 h-3.5" />
            <span className="text-xs font-medium">历史记录</span>
          </button>
        </header>

        <div className="flex-1 overflow-y-auto explain-scroll">
          <div className="max-w-2xl mx-auto p-4 md:p-6">
            {selectMode ? (
              <>
                <div className="bg-white/10 backdrop-blur-md rounded-2xl p-5 mb-5 text-center border border-white/10">
                  <BookOpen className="w-10 h-10 mx-auto mb-2 text-purple-300" />
                  <h2 className="text-lg font-bold text-white mb-1">选择要讲解的错题</h2>
                  <p className="text-xs text-purple-200/60">共有 {wrongQuestions.length} 道待讲解的错题</p>
                </div>
                {wrongQuestions.length === 0 ? (
                  <div className="text-center py-10 text-purple-200/50">
                    <p className="text-sm">暂无待讲解的错题，去做几道题吧 🎯</p>
                  </div>
                ) : (
                  <div className="space-y-3">
                    {wrongQuestions.map(q => (
                      <button
                        key={q.id}
                        onClick={() => handleSelectQuestion(q)}
                        className="w-full text-left bg-white/10 backdrop-blur-md rounded-xl p-4 border border-white/10 hover:bg-white/15 hover:border-purple-400/30 transition-all group"
                      >
                        <div className="flex items-start justify-between gap-3 mb-2">
                          <p className="text-sm text-white/80 line-clamp-2 flex-1">{q.questionContent.stem}</p>
                          <span className={`shrink-0 text-[10px] px-2 py-0.5 rounded-full border ${WRONG_REASON_COLORS[q.wrongReasonTag] || 'bg-white/10 text-white/60'}`}>
                            {WRONG_REASON_LABELS[q.wrongReasonTag] || q.wrongReasonTag}
                          </span>
                        </div>
                        <div className="flex items-center gap-2">
                          {q.knowledgePoints?.map(kp => (
                            <span key={kp.id} className="text-[10px] px-2 py-0.5 rounded-full bg-purple-500/10 text-purple-300/70">{kp.name}</span>
                          ))}
                          <span className="text-[10px] text-white/30 ml-auto">{q.createdAt?.slice(0, 10)}</span>
                        </div>
                      </button>
                    ))}
                  </div>
                )}
              </>
            ) : selectedQuestion && (
              <>
                <QuestionCard
                  question={selectedQuestion}
                  expanded={questionExpanded}
                  onToggle={() => setQuestionExpanded(!questionExpanded)}
                  onKnowledgePointClick={(kp) => {
                    setToast(`"${kp.name}" 知识图谱（即将开放）`)
                  }}
                />

                {replayLoading && (
                  <div className="mt-4 bg-white/10 backdrop-blur-md rounded-2xl p-8 border border-white/10 text-center">
                    <div className="w-10 h-10 mx-auto mb-3">
                      <div className="w-10 h-10 border-3 border-purple-300/30 border-t-purple-400 rounded-full animate-spin" />
                    </div>
                    <p className="text-purple-200 text-sm font-medium">加载讲题记录...</p>
                  </div>
                )}

                {replayData && !replayLoading && (
                  <div className="mt-4 mb-2">
                    <span className="inline-flex items-center gap-1 text-[10px] px-2 py-0.5 rounded-full bg-blue-500/15 text-blue-300 border border-blue-400/15">
                      <Clock className="w-3 h-3" /> 历史回放 · {replayData.createdAt?.slice(0, 10)}
                    </span>
                  </div>
                )}

                {!replayId && generating && explainSteps.length === 0 && (
                  <div className="mt-4 bg-white/10 backdrop-blur-md rounded-2xl p-8 border border-white/10 text-center">
                    <div className="w-10 h-10 mx-auto mb-3">
                      <div className="w-10 h-10 border-3 border-purple-300/30 border-t-purple-400 rounded-full animate-spin" />
                    </div>
                    <p className="text-purple-200 text-sm font-medium">AI 正在分析你的错题...</p>
                    <p className="text-xs text-purple-200/50 mt-1.5">根据你的错误原因定制讲解内容</p>
                  </div>
                )}

                {explainSteps.length > 0 && (
                  <div className="mt-4 bg-white/10 backdrop-blur-md rounded-2xl p-5 border border-white/10">
                    <StepProgress
                      steps={stepLabels}
                      currentStep={allSteps ? currentStep : 0}
                      onStepClick={(i) => allSteps && currentStep !== i && setCurrentStep(i)}
                    />
                  </div>
                )}

                {explainSteps.length > 0 && (
                  <div className="mt-4">
                    <ExplainContent
                      steps={allSteps || explainSteps}
                      currentStep={currentStep}
                      loading={false}
                    />
                    <div className="mt-3 flex items-center justify-between">
                      <VoicePlayButton text={allSteps?.[currentStep]?.content || explainSteps[currentStep]?.content || ''} />
                      <div className="flex items-center gap-2">
                        <button
                          onClick={() => setCurrentStep(s => Math.max(0, s - 1))}
                          disabled={currentStep === 0}
                          className="w-8 h-8 rounded-lg bg-white/10 border border-white/10 flex items-center justify-center text-white/60 hover:bg-white/20 hover:text-white disabled:opacity-30 disabled:cursor-not-allowed transition-all"
                        >
                          <ChevronLeft className="w-4 h-4" />
                        </button>
                        <span className="text-xs text-white/40">
                          {currentStep + 1} / {allSteps?.length || explainSteps.length}
                        </span>
                        <button
                          onClick={() => setCurrentStep(s => Math.min((allSteps?.length || explainSteps.length) - 1, s + 1))}
                          disabled={currentStep === (allSteps?.length || explainSteps.length) - 1}
                          className="w-8 h-8 rounded-lg bg-white/10 border border-white/10 flex items-center justify-center text-white/60 hover:bg-white/20 hover:text-white disabled:opacity-30 disabled:cursor-not-allowed transition-all"
                        >
                          <ChevronRight className="w-4 h-4" />
                        </button>
                      </div>
                    </div>
                  </div>
                )}

                {generating && explainSteps.length > 0 && (
                  <div className="mt-3 bg-white/5 rounded-xl px-4 py-2.5 border border-white/5">
                    <div className="flex items-center gap-2">
                      <div className="w-1.5 h-1.5 rounded-full bg-purple-400 animate-pulse" />
                      <span className="text-xs text-purple-200/60">AI 正在继续生成内容...</span>
                    </div>
                  </div>
                )}

                {tip && (
                  <div className="mt-4 bg-gradient-to-r from-purple-500/10 to-blue-500/10 backdrop-blur-sm rounded-xl p-4 border border-purple-400/15">
                    <div className="flex items-start gap-2.5">
                      <Sparkles className="w-4 h-4 text-purple-300 mt-0.5 shrink-0" />
                      <p className="text-sm text-white/80 leading-relaxed">{tip}</p>
                    </div>
                  </div>
                )}

                {similar && done && (
                  <div className="mt-3 bg-white/5 backdrop-blur-sm rounded-xl p-4 border border-white/5">
                    <div className="flex items-start gap-2.5">
                      <BookOpen className="w-4 h-4 text-blue-300 mt-0.5 shrink-0" />
                      <div>
                        <p className="text-xs font-medium text-blue-200/70 mb-1">同类题推荐</p>
                        <p className="text-sm text-white/60">{similar}</p>
                      </div>
                    </div>
                  </div>
                )}

                {done && !replayId && (
                  <div className="mt-4 space-y-3">
                    <div className="flex gap-3">
                      <button
                        onClick={() => setFeedback('understood')}
                        className={`flex-1 py-3 rounded-xl text-sm font-medium transition-all flex items-center justify-center gap-2 ${
                          feedback === 'understood'
                            ? 'bg-green-500/30 text-green-300 border border-green-400/30'
                            : 'bg-white/10 text-white/70 hover:bg-white/20 border border-white/10'
                        }`}
                      >
                        <ThumbsUp className="w-4 h-4" />
                        懂了
                      </button>
                      <button
                        onClick={() => setFeedback('confused')}
                        className={`flex-1 py-3 rounded-xl text-sm font-medium transition-all flex items-center justify-center gap-2 ${
                          feedback === 'confused'
                            ? 'bg-yellow-500/30 text-yellow-300 border border-yellow-400/30'
                            : 'bg-white/10 text-white/70 hover:bg-white/20 border border-white/10'
                        }`}
                      >
                        <HelpCircle className="w-4 h-4" />
                        还有疑问
                      </button>
                    </div>

                    <button
                      onClick={handleGenerate}
                      className="w-full py-2.5 bg-white/5 border border-white/10 rounded-xl text-sm text-white/50 hover:bg-white/10 hover:text-white/70 transition-all flex items-center justify-center gap-2"
                    >
                      <RefreshCw className="w-3.5 h-3.5" />
                      重新讲解
                    </button>

                    <button className="w-full py-2.5 bg-gradient-to-r from-amber-500/20 to-orange-500/20 border border-amber-400/20 rounded-xl text-sm text-amber-300 hover:from-amber-500/30 hover:to-orange-500/30 hover:border-amber-300/40 transition-all flex items-center justify-center gap-2 font-medium">
                      <BookOpen className="w-4 h-4" />
                      同类题练习
                    </button>

                    <AskMoreButton questionContext={selectedQuestion} />
                  </div>
                )}
              </>
            )}
          </div>
        </div>
      </div>
      {toast && (
        <div className="fixed inset-0 flex items-center justify-center z-50 pointer-events-none">
          <div className="bg-gradient-to-br from-[#7B5ADB] via-[#6B47D0] to-[#4E7FDB] text-white px-8 py-5 rounded-full shadow-2xl border-2 border-purple-300/50 backdrop-blur-md font-bold text-base text-center max-w-xs">
            ✨ {toast}
          </div>
        </div>
      )}
    </>
  )
}
