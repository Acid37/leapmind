import { useState, useEffect, useRef } from 'react'
import { ArrowLeft, Sparkles, ChevronDown, AlertCircle, FileText, BookOpen, Loader2 } from 'lucide-react'
import { getUserInfo } from '../../utils/tokenManager'
import { getAllStages, getGradesByStage } from '../../services/educationService'
import { getWeakPoints, generateLessonPrep } from '../../services/m5'
import KnowledgePointSelector from '../../components/m5/KnowledgePointSelector'
import GoalEditor from '../../components/m5/GoalEditor'
import HoursPlanner from '../../components/m5/HoursPlanner'
import StyleSelector from '../../components/m5/StyleSelector'

const SUBJECTS = [
  { value: 'math', label: '数学' },
  { value: 'chinese', label: '语文' },
  { value: 'english', label: '英语' },
  { value: 'physics', label: '物理' },
  { value: 'chemistry', label: '化学' },
  { value: 'biology', label: '生物' },
]

// 学段/年级兜底数据（后端接口不可用时的 fallback）
const FALLBACK_STAGES = [
  { stageCode: 'PRIMARY', stageName: '小学' },
  { stageCode: 'JUNIOR', stageName: '初中' },
  { stageCode: 'SENIOR', stageName: '高中' },
]

const FALLBACK_GRADES = {
  PRIMARY: [
    { gradeCode: 'GRADE_1', gradeName: '一年级' },
    { gradeCode: 'GRADE_2', gradeName: '二年级' },
    { gradeCode: 'GRADE_3', gradeName: '三年级' },
    { gradeCode: 'GRADE_4', gradeName: '四年级' },
    { gradeCode: 'GRADE_5', gradeName: '五年级' },
    { gradeCode: 'GRADE_6', gradeName: '六年级' },
  ],
  JUNIOR: [
    { gradeCode: 'GRADE_7', gradeName: '七年级' },
    { gradeCode: 'GRADE_8', gradeName: '八年级' },
    { gradeCode: 'GRADE_9', gradeName: '九年级' },
  ],
  SENIOR: [
    { gradeCode: 'GRADE_10', gradeName: '高一' },
    { gradeCode: 'GRADE_11', gradeName: '高二' },
    { gradeCode: 'GRADE_12', gradeName: '高三' },
  ],
}

// 后端 gradeCode（GRADE_7）→ 备课接口需要的 grade（grade_7）
const normalizeGradeCode = (code) => {
  if (!code) return ''
  const match = String(code).match(/GRADE_(\d+)/)
  return match ? `grade_${match[1]}` : code
}

const scrollbarStyles = `
  .m5-scroll::-webkit-scrollbar { width: 4px; }
  .m5-scroll::-webkit-scrollbar-track { background: transparent; }
  .m5-scroll::-webkit-scrollbar-thumb { background: rgba(255,255,255,0.15); border-radius: 4px; }
  .m5-scroll::-webkit-scrollbar-thumb:hover { background: rgba(255,255,255,0.25); }
  .m5-list-scroll::-webkit-scrollbar { width: 3px; }
  .m5-list-scroll::-webkit-scrollbar-track { background: transparent; }
  .m5-list-scroll::-webkit-scrollbar-thumb { background: rgba(255,255,255,0.1); border-radius: 4px; }
`

export default function LessonPrepCreatePage({ onBack, onPrepCreated }) {
  const [userId, setUserId] = useState(null)

  // 基础信息
  const [title, setTitle] = useState('')
  const [subject, setSubject] = useState('math')
  const [grade, setGrade] = useState('')
  const [grades, setGrades] = useState([])
  const [stages, setStages] = useState([])
  const [selectedStage, setSelectedStage] = useState('')

  // 各组件状态
  const [selectedKps, setSelectedKps] = useState([])
  const [goals, setGoals] = useState([])
  const [totalHours, setTotalHours] = useState(2)
  const [style, setStyle] = useState('standard')
  const [weakPoints, setWeakPoints] = useState([])
  const [selectedWpIds, setSelectedWpIds] = useState([])
  const [wpLoading, setWpLoading] = useState(false)
  const [showWeakPoints, setShowWeakPoints] = useState(false)

  // 生成状态
  const [generating, setGenerating] = useState(false)
  const [genProgress, setGenProgress] = useState('')
  const [genStage, setGenStage] = useState('idle') // idle | syllabus | slides | narration | done | error
  const [genSyllabus, setGenSyllabus] = useState(null)
  const [genSlidesCount, setGenSlidesCount] = useState(0)
  const [genSyllabusText, setGenSyllabusText] = useState('')
  const abortRef = useRef(false)

  useEffect(() => {
    const user = getUserInfo()
    if (user?.id) setUserId(user.id)

    const loadStages = async () => {
      try {
        const data = await getAllStages()
        if (data && data.length > 0) {
          setStages(data)
        } else {
          setStages(FALLBACK_STAGES)
        }
      } catch (e) {
        console.error('加载教育阶段失败，使用兜底数据:', e)
        setStages(FALLBACK_STAGES)
      }
    }
    loadStages()
  }, [])

  useEffect(() => {
    if (!selectedStage) { setGrades([]); setGrade(''); return }
    const loadGrades = async () => {
      try {
        const data = await getGradesByStage(selectedStage)
        if (data && data.length > 0) {
          setGrades(data)
        } else {
          setGrades(FALLBACK_GRADES[selectedStage] || [])
        }
      } catch (e) {
        console.error('加载年级失败，使用兜底数据:', e)
        setGrades(FALLBACK_GRADES[selectedStage] || [])
      }
    }
    loadGrades()
  }, [selectedStage])

  useEffect(() => {
    if (!showWeakPoints || weakPoints.length > 0) return
    setWpLoading(true)
    getWeakPoints(userId || 1).then(data => {
      setWeakPoints(data || [])
      setWpLoading(false)
    }).catch(() => setWpLoading(false))
  }, [showWeakPoints])

  const canGenerate = title.trim() && subject && grade && selectedKps.length > 0 && goals.length > 0

  const handleGenerate = async () => {
    if (!canGenerate) return
    setGenerating(true)
    setGenProgress('正在生成教学大纲...')
    setGenStage('syllabus')
    setGenSyllabus(null)
    setGenSlidesCount(0)
    setGenSyllabusText('')
    abortRef.current = false

    const params = {
      userId: userId || 1,
      title: title.trim(),
      subject,
      grade: normalizeGradeCode(grade),
      knowledgePointIds: selectedKps.map(kp => kp.id),
      teachingGoals: goals,
      totalHours,
      style,
      weakPointIds: selectedWpIds,
      userProfileSummary: '',
    }

    await generateLessonPrep(params, (event, data) => {
      if (abortRef.current) return

      switch (event) {
        case 'syllabusChunk':
          setGenSyllabusText(prev => prev + (data.chunk || ''))
          break
        case 'outline':
          setGenSyllabus(data.content)
          setGenProgress('教学大纲已生成，正在生成PPT...')
          setGenStage('slides')
          break
        case 'slide':
          setGenSlidesCount(prev => prev + 1)
          break
        case 'slidesDone':
          setGenProgress('PPT结构已生成，正在生成讲解词...')
          setGenStage('narration')
          break
        case 'narration':
          break
        case 'finalCheck':
          setGenProgress('质量检查通过，即将完成...')
          break
        case 'done':
          setGenProgress('备课生成完成！')
          setGenStage('done')
          setTimeout(() => {
            setGenerating(false)
            onPrepCreated?.(data.prepId || data.prep_id)
          }, 1000)
          break
        case 'error':
          setGenProgress(`生成失败: ${data.message}`)
          setGenStage('error')
          break
      }
    })
  }

  const handleCancelGenerate = () => {
    abortRef.current = true
    setGenerating(false)
    setGenStage('idle')
  }

  return (
    <>
      <style>{scrollbarStyles}</style>
      <div
        className="fixed inset-0 flex flex-col"
        style={{
          backgroundImage: "linear-gradient(135deg, #861FCE 0%, #861FCE 16%, #731CCD 16%, #731CCD 32%, #6B1CCF 32%, #6B1CCF 48%, #631DCE 48%, #631DCE 64%, #5A1BCE 64%, #5A1BCE 80%, rgb(86, 43, 205) 80%, rgb(47, 8, 154) 100%)",
          backgroundAttachment: "fixed",
        }}
      >
        {/* Header */}
        <header className="shrink-0 px-6 py-4 flex items-center justify-between border-b border-purple-400/20">
          <button onClick={onBack} className="flex items-center gap-2 text-white/80 hover:text-white transition-colors">
            <ArrowLeft className="w-5 h-5" />
            <span className="text-sm font-medium">返回</span>
          </button>
          <div className="flex items-center gap-2">
            <FileText className="w-5 h-5 text-purple-200" />
            <h1 className="text-lg font-bold text-white">创建备课</h1>
          </div>
          <div className="w-16" />
        </header>

        {/* Content */}
        <div className="flex-1 overflow-y-auto m5-scroll">
          <div className="max-w-2xl mx-auto p-4 md:p-6 space-y-4 pb-32">

            {/* 标题 + 科目 + 年级 */}
            <div className="bg-white/10 backdrop-blur-md rounded-2xl p-5 border border-white/10">
              <h3 className="text-base font-semibold text-white mb-4">基本信息</h3>
              <div className="space-y-3">
                <input
                  type="text"
                  value={title}
                  onChange={e => setTitle(e.target.value)}
                  placeholder="输入备课标题，如：勾股定理"
                  className="w-full bg-white/5 border border-white/10 rounded-xl px-4 py-3 text-sm text-white placeholder-purple-200/30 outline-none focus:border-purple-400/40 focus:bg-white/10 transition-all"
                />
                <div className="grid grid-cols-2 gap-3">
                  <select
                    value={subject}
                    onChange={e => setSubject(e.target.value)}
                    className="bg-white/5 border border-white/10 rounded-xl px-4 py-3 text-sm text-white outline-none focus:border-purple-400/40 focus:bg-white/10 transition-all appearance-none cursor-pointer"
                  >
                    <option value="" disabled className="bg-[#5A1BCE]">选择科目</option>
                    {SUBJECTS.map(s => (
                      <option key={s.value} value={s.value} className="bg-[#5A1BCE]">{s.label}</option>
                    ))}
                  </select>

                  <div className="grid grid-cols-2 gap-2">
                    <select
                      value={selectedStage}
                      onChange={e => setSelectedStage(e.target.value)}
                      className="bg-white/5 border border-white/10 rounded-xl px-3 py-3 text-sm text-white outline-none focus:border-purple-400/40 focus:bg-white/10 transition-all appearance-none cursor-pointer"
                    >
                      <option value="" disabled className="bg-[#5A1BCE]">学段</option>
                      {stages.map(s => (
                        <option key={s.stageCode || s} value={s.stageCode || s} className="bg-[#5A1BCE]">{s.stageName || s}</option>
                      ))}
                    </select>
                    <select
                      value={grade}
                      onChange={e => setGrade(e.target.value)}
                      className="bg-white/5 border border-white/10 rounded-xl px-3 py-3 text-sm text-white outline-none focus:border-purple-400/40 focus:bg-white/10 transition-all appearance-none cursor-pointer"
                    >
                      <option value="" disabled className="bg-[#5A1BCE]">年级</option>
                      {grades.map(g => (
                        <option key={g.gradeCode || g} value={g.gradeCode || g} className="bg-[#5A1BCE]">{g.gradeName || g}</option>
                      ))}
                    </select>
                  </div>
                </div>
              </div>
            </div>

            {/* 知识点选择 */}
            <KnowledgePointSelector
              subject={subject}
              value={selectedKps}
              onChange={setSelectedKps}
            />

            {/* 教学目标 */}
            <GoalEditor
              knowledgePointNames={selectedKps.map(kp => kp.name)}
              value={goals}
              onChange={setGoals}
            />

            {/* 课时数 */}
            <HoursPlanner value={totalHours} onChange={setTotalHours} />

            {/* 备课风格 */}
            <StyleSelector value={style} onChange={setStyle} />

            {/* 薄弱点导入（折叠） */}
            <div className="bg-white/10 backdrop-blur-md rounded-2xl border border-white/10 overflow-hidden">
              <button
                onClick={() => setShowWeakPoints(!showWeakPoints)}
                className="w-full flex items-center justify-between p-5 text-left"
              >
                <div className="flex items-center gap-2">
                  <AlertCircle className="w-5 h-5 text-purple-300" />
                  <h3 className="text-base font-semibold text-white">导入薄弱点</h3>
                  <span className="text-xs text-purple-200/40">（可选）</span>
                </div>
                <ChevronDown className={`w-4 h-4 text-purple-300/50 transition-transform ${showWeakPoints ? 'rotate-180' : ''}`} />
              </button>

              {showWeakPoints && (
                <div className="px-5 pb-5">
                  {wpLoading ? (
                    <div className="flex items-center justify-center py-4">
                      <Loader2 className="w-5 h-5 text-purple-300 animate-spin" />
                      <span className="ml-2 text-sm text-purple-200/60">加载薄弱点...</span>
                    </div>
                  ) : weakPoints.length === 0 ? (
                    <p className="text-sm text-purple-200/40 text-center py-4">暂无薄弱点数据</p>
                  ) : (
                    <div className="max-h-48 overflow-y-auto m5-list-scroll space-y-1.5">
                      {weakPoints.map(wp => {
                        const selected = selectedWpIds.includes(wp.id)
                        const levelColors = {
                          HIGH: 'bg-red-500/20 text-red-300 border-red-400/20',
                          MEDIUM: 'bg-amber-500/20 text-amber-300 border-amber-400/20',
                          LOW: 'bg-green-500/20 text-green-300 border-green-400/20',
                        }
                        return (
                          <label
                            key={wp.id}
                            className={`flex items-center gap-3 px-3 py-2.5 rounded-xl cursor-pointer transition-all ${
                              selected ? 'bg-white/15 border border-white/20' : 'bg-white/5 border border-transparent hover:bg-white/10'
                            }`}
                          >
                            <input
                              type="checkbox"
                              checked={selected}
                              onChange={() => {
                                setSelectedWpIds(prev =>
                                  prev.includes(wp.id) ? prev.filter(id => id !== wp.id) : [...prev, wp.id]
                                )
                              }}
                              className="w-4 h-4 rounded border-white/30 bg-white/5 accent-purple-400"
                            />
                            <div className="flex-1 min-w-0">
                              <p className="text-sm text-white/90 truncate">{wp.knowledgePoint}</p>
                              <p className="text-xs text-purple-200/40">{wp.subject}</p>
                            </div>
                            <span className={`text-[10px] px-2 py-0.5 rounded-full border ${levelColors[wp.weaknessLevel] || 'bg-white/10 text-white/50'}`}>
                              {wp.weaknessLevel === 'HIGH' ? '薄弱' : wp.weaknessLevel === 'MEDIUM' ? '一般' : '轻微'}
                            </span>
                          </label>
                        )
                      })}
                    </div>
                  )}
                </div>
              )}
            </div>
          </div>
        </div>

        {/* 底部固定按钮 */}
        <div className="shrink-0 px-6 py-4 border-t border-purple-400/20 bg-purple-900/30 backdrop-blur-md">
          <button
            onClick={handleGenerate}
            disabled={!canGenerate || generating}
            className="w-full py-3.5 rounded-2xl bg-gradient-to-r from-purple-500 to-blue-500 text-white font-semibold text-base shadow-lg shadow-purple-500/20 hover:shadow-purple-500/40 hover:scale-[1.01] transition-all duration-300 disabled:opacity-40 disabled:cursor-not-allowed disabled:hover:scale-100 flex items-center justify-center gap-2"
          >
            <Sparkles className="w-5 h-5" />
            {generating ? '生成中...' : '生成备课内容'}
          </button>
          {!canGenerate && !generating && (
            <p className="text-xs text-purple-200/40 text-center mt-2">
              {!title.trim() ? '请填写备课标题' : !grade ? '请选择年级' : selectedKps.length === 0 ? '请选择至少一个知识点' : goals.length === 0 ? '请添加至少一个教学目标' : ''}
            </p>
          )}
        </div>

        {/* 生成进度遮罩 */}
        {generating && (
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-purple-900/60 backdrop-blur-sm">
            <div className="bg-gradient-to-br from-[#6B1CCF] to-[#4A0E8F] rounded-3xl p-8 max-w-md w-full mx-4 border border-purple-400/20 shadow-2xl">
              {/* 阶段图标 */}
              <div className="flex items-center justify-center gap-2 mb-6">
                <div className={`w-3 h-3 rounded-full ${genStage === 'syllabus' ? 'bg-purple-400 animate-pulse' : 'bg-green-400'}`} />
                <div className="w-8 h-0.5 rounded bg-white/20">
                  <div className={`h-full bg-gradient-to-r from-purple-400 to-blue-400 rounded transition-all ${genStage === 'slides' ? 'w-full' : genStage === 'idle' ? 'w-0' : 'w-full'}`} />
                </div>
                <div className={`w-3 h-3 rounded-full ${genStage === 'slides' ? 'bg-purple-400 animate-pulse' : genStage === 'syllabus' ? 'bg-white/30' : 'bg-green-400'}`} />
                <div className="w-8 h-0.5 rounded bg-white/20">
                  <div className={`h-full bg-gradient-to-r from-purple-400 to-blue-400 rounded transition-all ${genStage === 'narration' ? 'w-full' : genStage === 'slides' || genStage === 'done' ? 'w-full' : 'w-0'}`} />
                </div>
                <div className={`w-3 h-3 rounded-full ${genStage === 'narration' ? 'bg-purple-400 animate-pulse' : genStage === 'done' ? 'bg-green-400' : 'bg-white/30'}`} />
              </div>

              <div className="text-center mb-6">
                <Loader2 className="w-10 h-10 text-purple-300 animate-spin mx-auto mb-4" />
                <p className="text-white font-semibold text-lg">{genProgress}</p>
                {genStage === 'slides' && genSlidesCount > 0 && (
                  <p className="text-sm text-purple-200/60 mt-1">已生成 {genSlidesCount} 页幻灯片</p>
                )}
              </div>

              {/* 大纲实时预览 */}
              {genSyllabusText && genStage !== 'done' && (
                <div className="bg-white/5 rounded-xl p-4 max-h-36 overflow-y-auto mb-4 border border-white/5">
                  <div className="flex items-center gap-1.5 mb-2">
                    <BookOpen className="w-3.5 h-3.5 text-purple-300" />
                    <span className="text-[10px] text-purple-200/50 uppercase tracking-wider">大纲预览</span>
                  </div>
                  <pre className="text-xs text-purple-200/70 whitespace-pre-wrap font-sans leading-relaxed">{genSyllabusText}</pre>
                </div>
              )}

              {genStage === 'done' && (
                <div className="text-center mb-4">
                  <div className="w-14 h-14 mx-auto mb-3 bg-green-500/20 rounded-full flex items-center justify-center border border-green-400/30">
                    <svg className="w-7 h-7 text-green-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.5" d="M5 13l4 4L19 7" />
                    </svg>
                  </div>
                  <p className="text-green-300 font-medium">备课生成成功！</p>
                  <p className="text-xs text-purple-200/50 mt-1">即将跳转到备课编辑页</p>
                </div>
              )}

              {genStage === 'error' && (
                <div className="text-center mb-4">
                  <p className="text-red-300 text-sm">{genProgress}</p>
                  <button onClick={handleCancelGenerate} className="mt-3 text-xs px-4 py-2 rounded-lg bg-white/10 text-white hover:bg-white/20 transition-all">关闭</button>
                </div>
              )}

              {genStage !== 'done' && genStage !== 'error' && (
                <button
                  onClick={handleCancelGenerate}
                  className="w-full py-2.5 rounded-xl bg-white/10 text-white/70 text-sm hover:bg-white/15 transition-all"
                >
                  取消生成
                </button>
              )}
            </div>
          </div>
        )}
      </div>
    </>
  )
}
