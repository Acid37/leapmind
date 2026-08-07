import { Pencil, Presentation, Download, Trash2, Play, FileText, Clock, BookOpen } from 'lucide-react'
import SlideRenderer from '../shared/SlideRenderer'

const SUBJECT_COLORS = {
  数学: 'from-blue-400 to-indigo-500',
  物理: 'from-amber-400 to-orange-500',
  英语: 'from-emerald-400 to-teal-500',
  化学: 'from-rose-400 to-pink-500',
  语文: 'from-purple-400 to-fuchsia-500',
  生物: 'from-green-400 to-emerald-500',
}

export default function PrepContentCard({ item, onEdit, onPreviewPpt, onExport, onDelete, onTeach }) {
  const subjectGrad = SUBJECT_COLORS[item.subject] || 'from-purple-400 to-blue-500'

  // 解析封面（第一张幻灯片）
  let coverSlide = null
  let slideCount = 0
  try {
    const parsed = typeof item.pptStructure === 'string' ? JSON.parse(item.pptStructure) : item.pptStructure
    if (parsed?.slides) {
      slideCount = parsed.slides.length
      coverSlide = parsed.slides[0]
    } else if (parsed?.pages) {
      slideCount = parsed.pages.length
      coverSlide = parsed.pages[0]
    }
  } catch (e) { /* ignore */ }

  const formatDate = (str) => {
    if (!str) return ''
    const d = new Date(str)
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
  }

  return (
    <div className="bg-white/10 backdrop-blur-md rounded-2xl border border-white/10 overflow-hidden hover:border-purple-400/30 hover:shadow-xl hover:shadow-purple-500/10 transition-all duration-300 flex flex-col">
      {/* 封面区域 */}
      <div className="relative h-36 bg-white/5 overflow-hidden group">
        {coverSlide ? (
          <div className="absolute inset-0 p-2">
            <SlideRenderer slide={coverSlide} compact showTypeBadge={false} />
          </div>
        ) : (
          <div className="w-full h-full flex items-center justify-center bg-gradient-to-br from-purple-500/10 to-blue-500/10">
            <FileText className="w-10 h-10 text-purple-300/40" />
          </div>
        )}
        {/* 科目角标 */}
        <div className={`absolute bottom-2 left-2 w-7 h-7 rounded-lg bg-gradient-to-br ${subjectGrad} flex items-center justify-center shadow-lg`}>
          <BookOpen className="w-3.5 h-3.5 text-white" />
        </div>
      </div>

      {/* 内容区 */}
      <div className="p-4 flex-1 flex flex-col">
        <h3 className="text-sm font-semibold text-white truncate mb-2">{item.title || '未命名备课'}</h3>

        {/* 元信息 */}
        <div className="flex flex-wrap items-center gap-3 text-[11px] text-purple-200/50 mb-3">
          <span className="flex items-center gap-1">
            <span className={`px-1.5 py-0.5 rounded bg-gradient-to-r ${subjectGrad} text-white text-[10px]`}>{item.subject || '未知'}</span>
          </span>
          <span className="flex items-center gap-1">
            <Clock className="w-3 h-3" /> {item.totalHours || 0} 课时
          </span>
          <span className="flex items-center gap-1">
            <Presentation className="w-3 h-3" /> {slideCount} 页
          </span>
          <span className="ml-auto">{formatDate(item.createdAt)}</span>
        </div>

        {/* 操作按钮 */}
        <div className="grid grid-cols-2 gap-1.5 mt-auto">
          <button
            onClick={onEdit}
            className="flex items-center justify-center gap-1.5 px-2 py-2 rounded-lg bg-white/10 border border-white/10 text-white/80 text-xs hover:bg-purple-500/20 hover:text-purple-100 hover:border-purple-400/30 transition-all"
          >
            <Pencil className="w-3.5 h-3.5" /> 编辑
          </button>
          <button
            onClick={onPreviewPpt}
            className="flex items-center justify-center gap-1.5 px-2 py-2 rounded-lg bg-white/10 border border-white/10 text-white/80 text-xs hover:bg-purple-500/20 hover:text-purple-100 hover:border-purple-400/30 transition-all"
          >
            <Presentation className="w-3.5 h-3.5" /> 预览PPT
          </button>
          <button
            onClick={onExport}
            className="flex items-center justify-center gap-1.5 px-2 py-2 rounded-lg bg-white/10 border border-white/10 text-white/80 text-xs hover:bg-purple-500/20 hover:text-purple-100 hover:border-purple-400/30 transition-all"
          >
            <Download className="w-3.5 h-3.5" /> 导出
          </button>
          <button
            onClick={onDelete}
            className="flex items-center justify-center gap-1.5 px-2 py-2 rounded-lg bg-white/10 border border-white/10 text-white/80 text-xs hover:bg-red-500/20 hover:text-red-300 hover:border-red-400/30 transition-all"
          >
            <Trash2 className="w-3.5 h-3.5" /> 删除
          </button>
        </div>

        {/* 讲课按钮 */}
        <button
          onClick={onTeach}
          className="mt-1.5 flex items-center justify-center gap-1.5 px-2 py-2 rounded-lg bg-gradient-to-r from-purple-500 to-blue-500 text-white text-xs hover:opacity-90 transition-all"
        >
          <Play className="w-3.5 h-3.5" /> 用于讲课
        </button>
      </div>
    </div>
  )
}
