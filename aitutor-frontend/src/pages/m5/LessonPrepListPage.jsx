import { useState, useEffect, useCallback } from 'react'
import { ArrowLeft, Plus, FileText, Loader2, Check, AlertCircle, Download, ChevronLeft, ChevronRight } from 'lucide-react'
import { getUserInfo } from '../../utils/tokenManager'
import { getLessonPrepContents, deleteLessonPrep, exportPptx } from '../../services/m5'
import PrepContentCard from '../../components/m5/PrepContentCard'

const SUBJECTS = ['全部', '数学', '语文', '英语', '物理', '化学', '生物']

const PAGE_SIZE = 6

const scrollbarStyles = `
  .m5-scroll::-webkit-scrollbar { width: 4px; }
  .m5-scroll::-webkit-scrollbar-track { background: transparent; }
  .m5-scroll::-webkit-scrollbar-thumb { background: rgba(255,255,255,0.15); border-radius: 4px; }
  .m5-list-scroll::-webkit-scrollbar { width: 3px; }
  .m5-list-scroll::-webkit-scrollbar-track { background: transparent; }
  .m5-list-scroll::-webkit-scrollbar-thumb { background: rgba(255,255,255,0.1); border-radius: 4px; }
`

export default function LessonPrepListPage({ onBack, onCreate, onEdit, onPreviewPpt }) {
  const [userId, setUserId] = useState(null)
  const [items, setItems] = useState([])
  const [loading, setLoading] = useState(true)
  const [subjectFilter, setSubjectFilter] = useState('全部')
  const [page, setPage] = useState(1)
  const [toast, setToast] = useState(null)
  const [deleteTarget, setDeleteTarget] = useState(null)
  const [exportingId, setExportingId] = useState(null)

  const showToast = useCallback((msg, type = 'success') => {
    setToast({ msg, type })
    setTimeout(() => setToast(null), 2500)
  }, [])

  const loadData = useCallback(async (uid) => {
    setLoading(true)
    try {
      const data = await getLessonPrepContents(uid)
      setItems(data || [])
    } catch (e) {
      console.error('加载备课列表失败:', e)
    }
    setLoading(false)
  }, [])

  useEffect(() => {
    const user = getUserInfo()
    if (user?.id) setUserId(user.id)
    loadData(user?.id || 1)
  }, [loadData])

  // 筛选
  const filtered = items.filter(item => {
    if (subjectFilter !== '全部' && item.subject !== subjectFilter) return false
    return true
  })

  const totalPages = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE))
  const safePage = Math.min(page, totalPages)
  const pagedItems = filtered.slice((safePage - 1) * PAGE_SIZE, safePage * PAGE_SIZE)

  const handleDelete = async () => {
    if (!deleteTarget) return
    try {
      await deleteLessonPrep(deleteTarget.prepId, userId || 1)
      setItems(prev => prev.filter(i => i.prepId !== deleteTarget.prepId))
      setDeleteTarget(null)
      showToast('备课已删除')
    } catch (e) {
      showToast('删除失败，请重试', 'error')
    }
  }

  const handleExport = async (item) => {
    setExportingId(item.prepId)
    try {
      const url = await exportPptx(item.prepId)
      if (url) {
        const a = document.createElement('a')
        a.href = url
        a.download = `lesson-prep-${item.prepId}.pptx`
        a.click()
      }
      showToast('PPTX 已导出')
    } catch (e) {
      showToast('导出失败，请重试', 'error')
    }
    setExportingId(null)
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
          <div className="flex items-center gap-3">
            <button onClick={onBack} className="flex items-center gap-2 text-white/80 hover:text-white transition-colors">
              <ArrowLeft className="w-5 h-5" />
              <span className="text-sm font-medium">返回</span>
            </button>
            <div className="flex items-center gap-2 ml-2">
              <FileText className="w-5 h-5 text-purple-200" />
              <h1 className="text-lg font-bold text-white">我的备课</h1>
              <span className="text-xs text-purple-200/50">共 {items.length} 条</span>
            </div>
          </div>
          <button
            onClick={onCreate}
            className="flex items-center gap-1.5 px-4 py-2.5 rounded-xl bg-gradient-to-r from-purple-500 to-blue-500 text-white text-sm font-medium hover:opacity-90 transition-all shadow-lg shadow-purple-500/20"
          >
            <Plus className="w-4 h-4" />
            新建备课
          </button>
        </header>

        {/* 筛选栏 */}
        <div className="shrink-0 px-6 py-3 flex items-center gap-3 flex-wrap border-b border-purple-400/10">
          {/* 科目下拉 */}
          <div className="flex items-center gap-1 bg-white/5 rounded-xl p-1">
            {SUBJECTS.slice(0, 4).map(sub => (
              <button
                key={sub}
                onClick={() => { setSubjectFilter(sub); setPage(1) }}
                className={`px-3 py-1.5 rounded-lg text-xs transition-all ${
                  subjectFilter === sub
                    ? 'bg-white/15 text-white'
                    : 'text-purple-200/60 hover:text-white hover:bg-white/10'
                }`}
              >
                {sub}
              </button>
            ))}
            <select
              value={SUBJECTS.includes(subjectFilter) && subjectFilter !== '全部' ? subjectFilter : '全部'}
              onChange={(e) => { setSubjectFilter(e.target.value); setPage(1) }}
              className="bg-transparent text-purple-200/60 text-xs outline-none cursor-pointer px-1"
            >
              {SUBJECTS.slice(4).map(sub => (
                <option key={sub} value={sub} className="bg-[#5A1BCE] text-white">{sub}</option>
              ))}
            </select>
          </div>
        </div>

        {/* 内容区 */}
        <div className="flex-1 overflow-y-auto m5-scroll">
          <div className="max-w-5xl mx-auto p-4 md:p-6">
            {loading ? (
              <div className="flex flex-col items-center justify-center py-24">
                <Loader2 className="w-10 h-10 text-purple-300 animate-spin mb-4" />
                <p className="text-purple-200 text-sm">正在加载备课...</p>
              </div>
            ) : filtered.length === 0 ? (
              <div className="flex flex-col items-center justify-center py-24">
                <div className="w-16 h-16 rounded-2xl bg-white/10 flex items-center justify-center mb-4">
                  <FileText className="w-8 h-8 text-purple-300/60" />
                </div>
                <p className="text-white font-medium mb-1">还没有备课内容</p>
                <p className="text-sm text-purple-200/50 mb-6">点击右上角「新建备课」开始你的第一节 AI 备课</p>
                <button
                  onClick={onCreate}
                  className="flex items-center gap-2 px-5 py-2.5 rounded-xl bg-gradient-to-r from-purple-500 to-blue-500 text-white text-sm hover:opacity-90 transition-all"
                >
                  <Plus className="w-4 h-4" />
                  新建备课
                </button>
              </div>
            ) : (
              <>
                {/* 卡片网格 */}
                <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
                  {pagedItems.map(item => (
                    <PrepContentCard
                      key={item.prepId}
                      item={item}
                      onEdit={() => onEdit(item.prepId)}
                      onPreviewPpt={() => onPreviewPpt(item)}
                      onExport={() => handleExport(item)}
                      onDelete={() => setDeleteTarget(item)}
                      onTeach={() => showToast('「用于讲课」即将开放', 'error')}
                    />
                  ))}
                </div>

                {/* 分页 */}
                {totalPages > 1 && (
                  <div className="flex items-center justify-center gap-3 mt-6">
                    <button
                      onClick={() => setPage(p => Math.max(1, p - 1))}
                      disabled={safePage === 1}
                      className="w-8 h-8 rounded-lg bg-white/10 border border-white/10 flex items-center justify-center text-white/70 hover:bg-white/20 disabled:opacity-30 disabled:cursor-not-allowed transition-all"
                    >
                      <ChevronLeft className="w-4 h-4" />
                    </button>
                    <span className="text-sm text-purple-200/70">{safePage} / {totalPages}</span>
                    <button
                      onClick={() => setPage(p => Math.min(totalPages, p + 1))}
                      disabled={safePage === totalPages}
                      className="w-8 h-8 rounded-lg bg-white/10 border border-white/10 flex items-center justify-center text-white/70 hover:bg-white/20 disabled:opacity-30 disabled:cursor-not-allowed transition-all"
                    >
                      <ChevronRight className="w-4 h-4" />
                    </button>
                  </div>
                )}
              </>
            )}
          </div>
        </div>
      </div>

      {/* 删除确认弹窗 */}
      {deleteTarget && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm">
          <div className="bg-gradient-to-br from-[#6B1CCF] to-[#4A0E8F] rounded-2xl p-6 w-full max-w-sm mx-4 border border-purple-400/20 shadow-2xl">
            <div className="flex items-center gap-3 mb-4">
              <div className="w-10 h-10 rounded-xl bg-red-500/20 flex items-center justify-center">
                <AlertCircle className="w-5 h-5 text-red-300" />
              </div>
              <div>
                <h3 className="text-white font-semibold">删除备课</h3>
                <p className="text-xs text-purple-200/50">删除后不可恢复</p>
              </div>
            </div>
            <p className="text-sm text-white/80 mb-6">
              确定要删除「<span className="text-white font-medium">{deleteTarget.title}</span>」吗？
            </p>
            <div className="flex justify-end gap-2">
              <button
                onClick={() => setDeleteTarget(null)}
                className="px-4 py-2 rounded-xl bg-white/10 text-white/70 text-sm hover:bg-white/15 transition-all"
              >
                取消
              </button>
              <button
                onClick={handleDelete}
                className="px-4 py-2 rounded-xl bg-red-500 text-white text-sm hover:bg-red-600 transition-all"
              >
                确认删除
              </button>
            </div>
          </div>
        </div>
      )}

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
