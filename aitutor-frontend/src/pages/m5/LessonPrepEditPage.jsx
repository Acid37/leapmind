import { useState, useEffect, useRef, useCallback } from 'react'
import { ArrowLeft, Save, Presentation, Check, Loader2, AlertCircle } from 'lucide-react'
import { getUserInfo } from '../../utils/tokenManager'
import { getLessonPrepDetail, updateLessonPrep, generatePpt } from '../../services/m5'
import OutlineEditor from '../../components/m5/OutlineEditor'
import SectionEditor from '../../components/m5/SectionEditor'

const EMPTY_SYLLABUS = {
  title: '',
  totalHours: 1,
  sections: [{
    hourIndex: 1,
    title: '第一课时',
    coreContent: '',
    teachingGoals: [],
    keyPoints: [],
    difficultPoints: [],
    teachingProcess: [],
    homework: { basic: [], advanced: [], optional: [] },
  }],
}

// 从详情数据中提取 syllabus（兼容后端返回结构）
const extractSyllabus = (data) => {
  if (data?.syllabus) return data.syllabus
  // 尝试从 pptStructure 中解析
  try {
    const parsed = typeof data?.pptStructure === 'string' ? JSON.parse(data.pptStructure) : data?.pptStructure
    if (parsed?.syllabus) return parsed.syllabus
  } catch (e) { /* ignore */ }
  return null
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

export default function LessonPrepEditPage({ prepId, onBack, onGeneratedPpt }) {
  const [userId, setUserId] = useState(null)
  const [detail, setDetail] = useState(null)
  const [syllabus, setSyllabus] = useState(null)
  const [activeIndex, setActiveIndex] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  const [saving, setSaving] = useState(false)
  const [generating, setGenerating] = useState(false)
  const [toast, setToast] = useState(null)
  const abortRef = useRef(false)

  const showToast = useCallback((msg, type = 'success') => {
    setToast({ msg, type })
    setTimeout(() => setToast(null), 2500)
  }, [])

  useEffect(() => {
    const user = getUserInfo()
    if (user?.id) setUserId(user.id)

    const load = async () => {
      setLoading(true)
      try {
        const data = await getLessonPrepDetail(prepId, userId || 1)
        setDetail(data)
        // 提取 syllabus，若后端未返回则用空大纲兜底
        const extracted = extractSyllabus(data)
        setSyllabus(extracted || { ...EMPTY_SYLLABUS, title: data?.title || '' })
      } catch (err) {
        setError('加载备课详情失败，请重试')
      }
      setLoading(false)
    }
    load()
  }, [prepId, userId])

  const updateTitle = (title) => {
    setDetail(prev => prev ? { ...prev, title } : prev)
    setSyllabus(prev => prev ? { ...prev, title } : prev)
  }

  const updateSection = (index, section) => {
    setSyllabus(prev => prev ? {
      ...prev,
      sections: prev.sections.map((s, i) => (i === index ? section : s)),
    } : prev)
  }

  const addSection = () => {
    setSyllabus(prev => prev ? {
      ...prev,
      totalHours: prev.totalHours + 1,
      sections: [...prev.sections, {
        hourIndex: prev.sections.length + 1,
        title: `第 ${prev.sections.length + 1} 课时`,
        coreContent: '',
        teachingGoals: [],
        keyPoints: [],
        difficultPoints: [],
        teachingProcess: [],
        homework: { basic: [], advanced: [], optional: [] },
      }],
    } : prev)
    setActiveIndex((prev) => (prev === null ? 0 : Math.max(0, prev)))
  }

  const deleteSection = (index) => {
    setSyllabus(prev => prev ? {
      ...prev,
      totalHours: Math.max(1, prev.totalHours - 1),
      sections: prev.sections
        .filter((_, i) => i !== index)
        .map((s, i) => ({ ...s, hourIndex: i + 1 })),
    } : prev)
    setActiveIndex(prev => {
      if (prev >= index) return Math.max(0, prev - 1)
      return prev
    })
  }

  const handleSave = async () => {
    if (!detail || !syllabus) return
    setSaving(true)
    try {
      const payload = {
        title: detail.title,
        pptStructure: JSON.stringify({ syllabus }),
      }
      await updateLessonPrep(prepId, userId || 1, payload)
      showToast('备课内容已保存', 'success')
    } catch (err) {
      showToast('保存失败，请重试', 'error')
    }
    setSaving(false)
  }

  const handleGeneratePpt = async () => {
    if (!detail) return
    setGenerating(true)
    try {
      const result = await generatePpt(prepId)
      showToast(`PPT 已生成，共 ${result.slides.length} 页`, 'success')
      setTimeout(() => {
        setGenerating(false)
        onGeneratedPpt?.(result)
      }, 800)
    } catch (err) {
      showToast('生成PPT失败，请重试', 'error')
      setGenerating(false)
    }
  }

  if (loading) {
    return (
      <div className="fixed inset-0 flex flex-col items-center justify-center"
        style={{ backgroundImage: "linear-gradient(135deg, #861FCE 0%, #861FCE 16%, #731CCD 16%, #731CCD 32%, #6B1CCF 32%, #6B1CCF 48%, #631DCE 48%, #631DCE 64%, #5A1BCE 64%, #5A1BCE 80%, rgb(86, 43, 205) 80%, rgb(47, 8, 154) 100%)" }}
      >
        <Loader2 className="w-10 h-10 text-purple-300 animate-spin mb-4" />
        <p className="text-purple-200 text-sm">正在加载备课内容...</p>
      </div>
    )
  }

  if (error) {
    return (
      <div className="fixed inset-0 flex flex-col items-center justify-center"
        style={{ backgroundImage: "linear-gradient(135deg, #861FCE 0%, #861FCE 16%, #731CCD 16%, #731CCD 32%, #6B1CCF 32%, #6B1CCF 48%, #631DCE 48%, #631DCE 64%, #5A1BCE 64%, #5A1BCE 80%, rgb(86, 43, 205) 80%, rgb(47, 8, 154) 100%)" }}
      >
        <AlertCircle className="w-10 h-10 text-red-300 mb-4" />
        <p className="text-white text-sm mb-4">{error}</p>
        <button onClick={onBack} className="px-4 py-2 rounded-xl bg-white/10 text-white hover:bg-white/20 transition-all">返回</button>
      </div>
    )
  }

  const activeSection = syllabus?.sections?.[activeIndex]

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
        <header className="shrink-0 px-4 md:px-6 py-3 flex items-center justify-between border-b border-purple-400/20 gap-3">
          <div className="flex items-center gap-3 min-w-0">
            <button onClick={onBack} className="flex items-center gap-2 text-white/80 hover:text-white transition-colors shrink-0">
              <ArrowLeft className="w-5 h-5" />
              <span className="text-sm font-medium">返回</span>
            </button>
          </div>

          <div className="flex items-center gap-2 min-w-0 flex-1 justify-center max-w-md">
            <input
              type="text"
              value={detail?.title || ''}
              onChange={(e) => updateTitle(e.target.value)}
              placeholder="备课标题"
              className="w-full bg-white/5 border border-transparent hover:border-white/10 focus:border-purple-400/40 rounded-xl px-3 py-1.5 text-sm text-white font-medium outline-none focus:bg-white/10 transition-all text-center placeholder-purple-200/30"
            />
          </div>

          <div className="flex items-center gap-2 shrink-0">
            <button
              onClick={handleSave}
              disabled={saving}
              className="flex items-center gap-1.5 px-3 py-2 rounded-xl bg-white/10 border border-white/10 text-white text-sm hover:bg-white/20 transition-all disabled:opacity-50"
            >
              {saving ? <Loader2 className="w-4 h-4 animate-spin" /> : <Save className="w-4 h-4" />}
              {saving ? '保存中...' : '保存'}
            </button>
            <button
              onClick={handleGeneratePpt}
              disabled={generating}
              className="flex items-center gap-1.5 px-3 py-2 rounded-xl bg-gradient-to-r from-purple-500 to-blue-500 text-white text-sm font-medium hover:opacity-90 transition-all disabled:opacity-50 shadow-lg shadow-purple-500/20"
            >
              {generating ? <Loader2 className="w-4 h-4 animate-spin" /> : <Presentation className="w-4 h-4" />}
              {generating ? '生成中...' : '生成PPT'}
            </button>
          </div>
        </header>

        {/* 主体：左树右编 */}
        <div className="flex-1 overflow-hidden flex gap-3 p-3 md:p-4">
          {/* 左树 */}
          <div className="w-64 md:w-72 shrink-0">
            <OutlineEditor
              sections={syllabus?.sections || []}
              activeIndex={activeIndex}
              onSelect={setActiveIndex}
              onAdd={addSection}
              onDelete={deleteSection}
            />
          </div>

          {/* 右编辑区 */}
          <div className="flex-1 overflow-y-auto m5-scroll">
            {activeSection ? (
              <SectionEditor
                key={activeIndex}
                section={activeSection}
                index={activeIndex}
                onChange={(section) => updateSection(activeIndex, section)}
              />
            ) : (
              <div className="h-full flex flex-col items-center justify-center text-purple-200/40">
                <Presentation className="w-12 h-12 mb-3" />
                <p className="text-sm">请选择或添加课时</p>
              </div>
            )}
          </div>
        </div>
      </div>

      {/* Toast */}
      {toast && (
        <div className="fixed inset-0 flex items-center justify-center z-50 pointer-events-none">
          <div className={`flex items-center gap-2 bg-gradient-to-br text-white px-6 py-3.5 rounded-full shadow-2xl border-2 backdrop-blur-md font-medium text-sm max-w-xs ${
            toast.type === 'success'
              ? 'from-emerald-500 to-teal-500 border-emerald-300/50'
              : 'from-red-500 to-rose-500 border-red-300/50'
          }`}>
            {toast.type === 'success' ? <Check className="w-4 h-4" /> : <AlertCircle className="w-4 h-4" />}
            {toast.msg}
          </div>
        </div>
      )}
    </>
  )
}
