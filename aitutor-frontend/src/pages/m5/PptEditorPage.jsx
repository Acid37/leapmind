import { useState, useEffect, useCallback } from 'react'
import { ArrowLeft, Save, Download, Presentation, Loader2, Check, AlertCircle } from 'lucide-react'
import { getUserInfo } from '../../utils/tokenManager'
import {
  getPptTemplates, exportPptx, applyTemplate, updateLessonPrep,
} from '../../services/m5'
import SlideThumbnailList from '../../components/m5/SlideThumbnailList'
import SlideCanvas from '../../components/m5/SlideCanvas'
import StylePanel from '../../components/m5/StylePanel'

const DEFAULT_STYLE = {
  colorScheme: { primary: '#A78BFA', secondary: '#818CF8', background: 'rgba(255,255,255,0.06)', text: '#ffffff', accent: '#FBBF24' },
  fontFamily: { title: 'PingFang SC', body: 'PingFang SC' },
}

// 把 currentStyle（嵌套）转为 SlideRenderer 需要的扁平 styleVars
const toFlatStyleVars = (style) => {
  const cs = style?.colorScheme || {}
  const ff = style?.fontFamily || {}
  return {
    primaryColor: cs.primary || '#A78BFA',
    secondaryColor: cs.secondary || '#818CF8',
    backgroundColor: cs.background || 'rgba(255,255,255,0.06)',
    textColor: cs.text || '#ffffff',
    accentColor: cs.accent || '#FBBF24',
    fontFamily: ff.body || ff.title || 'Inter, sans-serif',
  }
}

const scrollbarStyles = `
  .m5-scroll::-webkit-scrollbar { width: 4px; }
  .m5-scroll::-webkit-scrollbar-track { background: transparent; }
  .m5-scroll::-webkit-scrollbar-thumb { background: rgba(255,255,255,0.15); border-radius: 4px; }
  .m5-list-scroll::-webkit-scrollbar { width: 3px; }
  .m5-list-scroll::-webkit-scrollbar-track { background: transparent; }
  .m5-list-scroll::-webkit-scrollbar-thumb { background: rgba(255,255,255,0.1); border-radius: 4px; }
`

export default function PptEditorPage({ pptId, initialSlides, onBack }) {
  const [userId, setUserId] = useState(null)
  const [title, setTitle] = useState('PPT 预览')
  const [slides, setSlides] = useState(initialSlides || [])
  const [activeIndex, setActiveIndex] = useState(0)
  const [templates, setTemplates] = useState([])
  const [currentStyle, setCurrentStyle] = useState(DEFAULT_STYLE)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [exporting, setExporting] = useState(false)
  const [toast, setToast] = useState(null)

  const showToast = useCallback((msg, type = 'success') => {
    setToast({ msg, type })
    setTimeout(() => setToast(null), 2500)
  }, [])

  useEffect(() => {
    const user = getUserInfo()
    if (user?.id) setUserId(user.id)

    const loadTemplates = async () => {
      try {
        const data = await getPptTemplates(user?.id || 1)
        setTemplates(data)
      } catch (e) {
        console.error('加载模板失败:', e)
      }
    }
    loadTemplates()

    // 如果没传初始slides，模拟从后端恢复
    if (!initialSlides || initialSlides.length === 0) {
      setTimeout(() => {
        setLoading(false)
      }, 500)
    } else {
      setLoading(false)
    }
  }, [initialSlides])

  const updateSlide = (index, slide) => {
    setSlides(prev => prev.map((s, i) => (i === index ? slide : s)))
  }

  const handleReorder = (nextSlides) => {
    // 重新编号 pageNum
    const renumbered = nextSlides.map((s, i) => ({ ...s, pageNum: i + 1 }))
    setSlides(renumbered)
  }

  const handleAddSlide = () => {
    setSlides(prev => {
      const next = [...prev, {
        pageNum: prev.length + 1,
        type: 'content',
        title: `新幻灯片 ${prev.length + 1}`,
        bulletPoints: ['新要点 1'],
        imageSuggestion: '',
        formula: '',
        highlightPoints: [],
        interaction: null,
      }]
      setActiveIndex(next.length - 1)
      return next
    })
  }

  const handleDeleteSlide = (pageNum) => {
    setSlides(prev => {
      const next = prev.filter(s => s.pageNum !== pageNum)
      if (next.length === 0) {
        setActiveIndex(0)
        return next
      }
      // 调整页码
      const renumbered = next.map((s, i) => ({ ...s, pageNum: i + 1 }))
      setActiveIndex(prevIdx => Math.max(0, Math.min(prevIdx, renumbered.length - 1)))
      return renumbered
    })
  }

  const handleSelectTemplate = async (template) => {
    // 解析模板配色 → 应用到当前样式
    let cfg = {}
    try { cfg = JSON.parse(template.configJson || '{}') } catch (e) { cfg = {} }
    const scheme = cfg.colorScheme || {}
    setCurrentStyle(prev => ({
      ...prev,
      templateId: template.id,
      colorScheme: {
        primary: scheme.primary || prev.colorScheme.primary,
        secondary: scheme.secondary || prev.colorScheme.secondary,
        accent: scheme.accent || prev.colorScheme.accent,
        background: scheme.background || prev.colorScheme.background,
        text: scheme.text || prev.colorScheme.text,
      },
      fontFamily: cfg.fontFamily || prev.fontFamily,
    }))
    try {
      await applyTemplate(template.id, pptId)
    } catch (e) {
      console.error('应用模板失败:', e)
    }
  }

  const handleColorChange = (preset) => {
    setCurrentStyle(prev => ({
      ...prev,
      colorScheme: {
        ...prev.colorScheme,
        primary: preset.primary,
        secondary: preset.secondary,
        accent: preset.accent,
      },
    }))
  }

  const handleFontChange = (font) => {
    setCurrentStyle(prev => ({
      ...prev,
      fontFamily: { ...prev.fontFamily, title: font, body: font },
    }))
  }

  const handleSave = async () => {
    setSaving(true)
    try {
      const pptStructure = JSON.stringify({ slides })
      await updateLessonPrep(pptId, userId || 1, { title, pptStructure })
      showToast('PPT 已保存')
    } catch (e) {
      showToast('保存失败，请重试', 'error')
    }
    setSaving(false)
  }

  const handleExport = async () => {
    setExporting(true)
    try {
      const url = await exportPptx(pptId)
      if (url) {
        const a = document.createElement('a')
        a.href = url
        a.download = `lesson-prep-${pptId}.pptx`
        a.click()
      }
      showToast('PPTX 已导出')
    } catch (e) {
      showToast('导出失败，请重试', 'error')
    }
    setExporting(false)
  }

  if (loading) {
    return (
      <div className="fixed inset-0 flex flex-col items-center justify-center"
        style={{ backgroundImage: "linear-gradient(135deg, #861FCE 0%, #861FCE 16%, #731CCD 16%, #731CCD 32%, #6B1CCF 32%, #6B1CCF 48%, #631DCE 48%, #631DCE 64%, #5A1BCE 64%, #5A1BCE 80%, rgb(86, 43, 205) 80%, rgb(47, 8, 154) 100%)" }}
      >
        <Loader2 className="w-10 h-10 text-purple-300 animate-spin mb-4" />
        <p className="text-purple-200 text-sm">正在加载 PPT...</p>
      </div>
    )
  }

  const activeSlide = slides[activeIndex]

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

          <div className="flex-1 min-w-0 flex justify-center max-w-md">
            <input
              type="text"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              placeholder="PPT 标题"
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
              onClick={handleExport}
              disabled={exporting}
              className="flex items-center gap-1.5 px-3 py-2 rounded-xl bg-white/10 border border-white/10 text-white text-sm hover:bg-white/20 transition-all disabled:opacity-50"
            >
              {exporting ? <Loader2 className="w-4 h-4 animate-spin" /> : <Download className="w-4 h-4" />}
              {exporting ? '导出中...' : '导出PPTX'}
            </button>
            <button
              className="flex items-center gap-1.5 px-3 py-2 rounded-xl bg-gradient-to-r from-purple-500 to-blue-500 text-white text-sm font-medium hover:opacity-90 transition-all shadow-lg shadow-purple-500/20"
            >
              <Presentation className="w-4 h-4" />
              开始讲课
            </button>
          </div>
        </header>

        {/* 三栏主体 */}
        <div className="flex-1 overflow-hidden flex gap-3 p-3 md:p-4">
          {/* 左：缩略图 */}
          <div className="w-44 md:w-52 shrink-0">
            <SlideThumbnailList
              slides={slides}
              activeIndex={activeIndex}
              onSelect={setActiveIndex}
              onReorder={handleReorder}
              onAdd={handleAddSlide}
              onDelete={handleDeleteSlide}
              styleVars={toFlatStyleVars(currentStyle)}
            />
          </div>

          {/* 中：预览 */}
          <div className="flex-1 flex flex-col h-full min-w-0">
            {activeSlide ? (
              <SlideCanvas
                slide={activeSlide}
                total={slides.length}
                currentIndex={activeIndex}
                onPrev={() => setActiveIndex(Math.max(0, activeIndex - 1))}
                onNext={() => setActiveIndex(Math.min(slides.length - 1, activeIndex + 1))}
                onChange={(slide) => updateSlide(activeIndex, slide)}
                styleVars={currentStyle}
              />
            ) : (
              <div className="h-full flex flex-col items-center justify-center text-purple-200/40">
                <Presentation className="w-12 h-12 mb-3" />
                <p className="text-sm">暂无幻灯片，请点击左侧添加</p>
              </div>
            )}
          </div>

          {/* 右：样式面板 */}
          <div className="w-56 md:w-64 shrink-0">
            <StylePanel
              templates={templates}
              currentStyle={currentStyle}
              onSelectTemplate={handleSelectTemplate}
              onColorChange={handleColorChange}
              onFontChange={handleFontChange}
            />
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
