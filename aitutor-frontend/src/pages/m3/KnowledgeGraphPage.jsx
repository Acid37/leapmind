import { useState, useEffect, useCallback } from 'react'
import { ArrowLeft, Loader2, MousePointerClick, Zap } from 'lucide-react'
import { getUserInfo } from '../../utils/tokenManager'
import { getKnowledgeGraph } from '../../services/m3'
import KnowledgeGraphCanvas from '../../components/m3/KnowledgeGraphCanvas'
import NodeDetailModal from '../../components/m3/NodeDetailModal'

const SUBJECTS = ['全部', '数学', '语文', '英语', '物理', '化学', '生物']

const scrollbarStyles = `
  .m3-scroll::-webkit-scrollbar { width: 4px; }
  .m3-scroll::-webkit-scrollbar-track { background: transparent; }
  .m3-scroll::-webkit-scrollbar-thumb { background: rgba(255,255,255,0.15); border-radius: 4px; }
`

export default function KnowledgeGraphPage({ onBack, onViewDetail, onPractice }) {
  const [userId, setUserId] = useState(null)
  const [subject, setSubject] = useState('全部')
  const [graph, setGraph] = useState(null)
  const [loading, setLoading] = useState(true)
  const [selectedNode, setSelectedNode] = useState(null)

  useEffect(() => {
    const user = getUserInfo()
    setUserId(user?.id || 1)
  }, [])

  const loadData = useCallback(async (subj) => {
    setLoading(true)
    try {
      const data = await getKnowledgeGraph(userId || 1, subj === '全部' ? '' : subj)
      setGraph(data)
    } catch (e) {
      console.error('加载知识图谱失败:', e)
    }
    setLoading(false)
  }, [userId])

  useEffect(() => {
    loadData(subject)
  }, [subject, loadData])

  const handleNodeClick = (node) => {
    setSelectedNode(node)
  }

  const handleNodeDoubleClick = (node) => {
    // 双击节点 → 跳转做题（携带知识点名）
    onPractice?.(node)
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
              <Zap className="w-5 h-5 text-purple-200" />
              <h1 className="text-lg font-bold text-white">知识图谱</h1>
            </div>
          </div>
          <select
            value={subject}
            onChange={(e) => setSubject(e.target.value)}
            className="bg-white/5 border border-white/10 rounded-xl px-3 py-2 text-sm text-white outline-none focus:border-purple-400/40 transition-all appearance-none cursor-pointer"
          >
            {SUBJECTS.map(s => (
              <option key={s} value={s} className="bg-[#5A1BCE]">{s === '全部' ? '全部学科' : s}</option>
            ))}
          </select>
        </header>

        {/* 内容区 */}
        <div className="flex-1 overflow-y-auto m3-scroll">
          <div className="max-w-5xl mx-auto p-4 md:p-6">
            {/* 交互提示 */}
            <div className="flex items-center gap-4 mb-4 text-[11px] text-purple-200/60 bg-white/5 rounded-xl px-4 py-2.5 border border-white/5">
              <span className="flex items-center gap-1.5">
                <MousePointerClick className="w-3.5 h-3.5 text-purple-300" /> 单击节点查看详情
              </span>
              <span className="flex items-center gap-1.5">
                <Zap className="w-3.5 h-3.5 text-amber-300" /> 双击节点去练习
              </span>
              <span className="ml-auto">拖动可调整布局 · 滚轮可缩放</span>
            </div>

            {/* 图谱主体 */}
            {loading ? (
              <div className="flex flex-col items-center justify-center py-32 bg-white/5 rounded-2xl border border-white/5">
                <Loader2 className="w-10 h-10 text-purple-300 animate-spin mb-4" />
                <p className="text-purple-200 text-sm">正在构建知识图谱...</p>
              </div>
            ) : graph && (graph.nodes || []).length > 0 ? (
              <div className="bg-white/5 backdrop-blur-md rounded-2xl border border-white/10 p-4">
                <KnowledgeGraphCanvas
                  data={graph}
                  onNodeClick={handleNodeClick}
                  onNodeDoubleClick={handleNodeDoubleClick}
                />
              </div>
            ) : (
              <div className="flex flex-col items-center justify-center py-32 bg-white/5 rounded-2xl border border-white/5">
                <Zap className="w-10 h-10 text-purple-300/50 mb-4" />
                <p className="text-white font-medium mb-1">该学科暂无知识点数据</p>
                <p className="text-sm text-purple-200/50">请切换其他学科查看</p>
              </div>
            )}

            {/* 图例 */}
            <div className="flex items-center justify-center gap-5 mt-4">
              <span className="flex items-center gap-1.5 text-[11px] text-green-300">
                <span className="w-2.5 h-2.5 rounded-full bg-[#52c41a] inline-block" /> 掌握 (&gt;80%)
              </span>
              <span className="flex items-center gap-1.5 text-[11px] text-amber-300">
                <span className="w-2.5 h-2.5 rounded-full bg-[#faad14] inline-block" /> 一般 (&gt;50%)
              </span>
              <span className="flex items-center gap-1.5 text-[11px] text-red-300">
                <span className="w-2.5 h-2.5 rounded-full bg-[#ff4d4f] inline-block" /> 薄弱
              </span>
              <span className="flex items-center gap-1.5 text-[11px] text-slate-300">
                <span className="w-2.5 h-2.5 rounded-full bg-[#d9d9d9] inline-block" /> 未学习
              </span>
            </div>
          </div>
        </div>
      </div>

      {/* 节点详情弹窗 */}
      <NodeDetailModal
        node={selectedNode}
        edges={graph?.edges || []}
        onClose={() => setSelectedNode(null)}
        onViewDetail={(node) => onViewDetail?.(node)}
      />
    </>
  )
}
