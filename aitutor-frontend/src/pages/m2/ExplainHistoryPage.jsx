import { useState, useEffect } from 'react'
import { ArrowLeft, Sparkles, Clock } from 'lucide-react'
import ExplainHistoryList from '../../components/m2/ExplainHistoryList'
import { mockGetExplainHistory, mockDeleteExplain } from '../../services/m2'

const scrollbarStyles = `
  .history-scroll::-webkit-scrollbar { width: 4px; }
  .history-scroll::-webkit-scrollbar-track { background: transparent; }
  .history-scroll::-webkit-scrollbar-thumb { background: rgba(255,255,255,0.15); border-radius: 4px; }
  .history-scroll::-webkit-scrollbar-thumb:hover { background: rgba(255,255,255,0.25); }
`

export default function ExplainHistoryPage({ onBack, onReplay }) {
  const [items, setItems] = useState([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [loading, setLoading] = useState(false)
  const [deleting, setDeleting] = useState(null)
  const pageSize = 5

  const loadData = async (p) => {
    setLoading(true)
    const data = await mockGetExplainHistory(p, pageSize)
    setItems(data.items)
    setTotal(data.total)
    setLoading(false)
  }

  useEffect(() => { loadData(page) }, [page])

  const handleDelete = async (id) => {
    setDeleting(id)
    await mockDeleteExplain(id)
    setItems(prev => prev.filter(i => i.id !== id))
    setTotal(prev => prev - 1)
    setDeleting(null)
  }

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
        <header className="shrink-0 px-6 py-4 flex items-center justify-between border-b border-purple-400/20">
          <button onClick={onBack} className="flex items-center gap-2 text-white/80 hover:text-white transition-colors">
            <ArrowLeft className="w-5 h-5" />
            <span className="text-sm font-medium">返回</span>
          </button>
          <div className="flex items-center gap-2">
            <Sparkles className="w-5 h-5 text-purple-200" />
            <h1 className="text-lg font-bold text-white">讲题历史</h1>
          </div>
          <div className="w-16" />
        </header>

        <div className="flex-1 overflow-y-auto history-scroll">
          <div className="max-w-2xl mx-auto p-4 md:p-6">
            <div className="bg-white/10 backdrop-blur-md rounded-2xl p-4 mb-5 border border-white/10 flex items-center gap-3">
              <Clock className="w-5 h-5 text-purple-300" />
              <div>
                <p className="text-sm font-medium text-white">讲题记录</p>
                <p className="text-xs text-purple-200/60">
                  {loading ? '加载中...' : `共 ${total} 条讲题记录`}
                </p>
              </div>
            </div>

            <ExplainHistoryList
              items={items}
              total={total}
              page={page}
              pageSize={pageSize}
              onPageChange={setPage}
              onReplay={(id) => onReplay?.(id)}
              onDelete={handleDelete}
            />
          </div>
        </div>
      </div>
    </>
  )
}
