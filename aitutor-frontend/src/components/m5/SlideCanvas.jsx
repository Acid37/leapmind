import { useState, useRef, useCallback, useEffect } from 'react'
import { ChevronLeft, ChevronRight, Pencil, X, Plus, Trash2, Image as ImageIcon } from 'lucide-react'
import ReactMarkdown from 'react-markdown'
import remarkMath from 'remark-math'
import rehypeKatex from 'rehype-katex'
import 'katex/dist/katex.min.css'

const FONT_OPTIONS = [
  { value: 'PingFang SC', label: '苹方' },
  { value: 'Microsoft YaHei', label: '微软雅黑' },
  { value: 'SimSun', label: '宋体' },
  { value: 'KaiTi', label: '楷体' },
  { value: 'Inter', label: 'Inter' },
  { value: 'Georgia', label: 'Georgia' },
]

const FONT_SIZES = [10, 12, 14, 16, 18, 20, 24, 28, 32, 36, 40, 48]

const typeOptions = ['cover', 'content', 'interactive', 'summary', 'homework']
const typeLabels = { cover: '封面', content: '内容', interactive: '互动', summary: '总结', homework: '作业' }

const TYPE_BG = {
  cover: 'from-purple-600/40 via-purple-700/30 to-indigo-700/40',
  content: 'from-sky-600/40 via-blue-700/30 to-indigo-700/40',
  interactive: 'from-amber-600/40 via-orange-700/30 to-rose-700/40',
  summary: 'from-emerald-600/40 via-teal-700/30 to-cyan-700/40',
  homework: 'from-rose-600/40 via-pink-700/30 to-purple-700/40',
}

let blockIdCounter = 0
const nextId = () => `blk_${Date.now()}_${blockIdCounter++}`

/**
 * 根据 slide 内容生成默认布局 blocks
 */
function buildDefaultBlocks(slide) {
  const blocks = []
  let y = 10

  if (slide.title) {
    blocks.push({ id: nextId(), type: 'title', x: 8, y, w: 84, h: 12, content: slide.title })
    y += 16
  }

  if (slide.formula) {
    blocks.push({ id: nextId(), type: 'formula', x: 12, y, w: 50, h: 10, content: slide.formula })
    y += 14
  }

  if (slide.bulletPoints && slide.bulletPoints.length > 0) {
    blocks.push({ id: nextId(), type: 'list', x: 8, y, w: 84, h: Math.min(60, 8 + slide.bulletPoints.length * 8), content: slide.bulletPoints.join('\n') })
  }

  if (slide.highlightPoints && slide.highlightPoints.length > 0) {
    blocks.push({ id: nextId(), type: 'highlights', x: 8, y: 82, w: 84, h: 10, content: slide.highlightPoints.join('\n') })
  }

  if (slide.interaction && slide.interaction.type) {
    blocks.push({ id: nextId(), type: 'interaction', x: 8, y: 78, w: 84, h: 10, content: slide.interaction.question || '' })
  }

  return blocks
}

/**
 * 自由布局 PPT 画布编辑器
 * 每个内容元素是可拖拽/缩放的独立 block
 */
export default function SlideCanvas({ slide, total, currentIndex, onPrev, onNext, onChange, styleVars = {} }) {
  const [selectedId, setSelectedId] = useState(null)
  const [editing, setEditing] = useState(null) // { block, value }
  const [editValue, setEditValue] = useState('')
  const [editFont, setEditFont] = useState('')
  const [editSize, setEditSize] = useState(16)
  const [dragState, setDragState] = useState(null) // { id, mode, startX, startY, orig }
  const [dragPos, setDragPos] = useState(null) // 拖拽中本地预览位置 { x, y, w, h }
  const canvasRef = useRef(null)
  const fileInputRef = useRef(null)
  const pendingImageIdRef = useRef(null)

  const {
    colorScheme = {},
    fontFamily = {},
  } = styleVars

  const canvasStyle = {
    backgroundColor: colorScheme.background && colorScheme.background !== 'rgba(255,255,255,0.06)' ? colorScheme.background : undefined,
    color: colorScheme.text,
    fontFamily: fontFamily.body || fontFamily.title || 'Inter, sans-serif',
  }
  const accentColor = colorScheme.accent || '#FBBF24'
  const primaryColor = colorScheme.primary || '#A78BFA'

  // 确保 slide 有 blocks（首次进入自动生成）
  const blocks = slide._blocks || buildDefaultBlocks(slide)

  // 用 ref 持有最新拖拽相关值，避免 listener 频繁重建
  const dragStateRef = useRef(null)
  const dragPosRef = useRef(null)
  const blocksRef = useRef(blocks)
  const slideRef = useRef(slide)
  const onChangeRef = useRef(onChange)
  useEffect(() => {
    dragStateRef.current = dragState
    dragPosRef.current = dragPos
    blocksRef.current = blocks
    slideRef.current = slide
    onChangeRef.current = onChange
  })

  // 拖拽逻辑（仅绑定一次，内部读 ref）
  const onPointerDown = useCallback((e, block, mode) => {
    e.stopPropagation()
    const rect = canvasRef.current?.getBoundingClientRect()
    if (!rect) return
    setSelectedId(block.id)
    const state = {
      id: block.id,
      mode,
      startX: e.clientX,
      startY: e.clientY,
      orig: { x: block.x, y: block.y, w: block.w, h: block.h },
      rectW: rect.width,
      rectH: rect.height,
    }
    dragStateRef.current = state
    setDragState(state)
  }, [])

  const handlePointerMove = useCallback((e) => {
    const state = dragStateRef.current
    if (!state) return
    const dx = e.clientX - state.startX
    const dy = e.clientY - state.startY
    const dxp = (dx / state.rectW) * 100
    const dyp = (dy / state.rectH) * 100

    let newBlock
    if (state.mode === 'move') {
      newBlock = {
        ...state.orig,
        x: Math.max(0, Math.min(100 - state.orig.w, state.orig.x + dxp)),
        y: Math.max(0, Math.min(100 - state.orig.h, state.orig.y + dyp)),
      }
    } else {
      newBlock = {
        ...state.orig,
        w: Math.max(8, Math.min(100 - state.orig.x, state.orig.w + dxp)),
        h: Math.max(5, Math.min(100 - state.orig.y, state.orig.h + dyp)),
      }
    }
    dragPosRef.current = newBlock
    setDragPos(newBlock)
  }, [])

  const handlePointerUp = useCallback(() => {
    const state = dragStateRef.current
    const pos = dragPosRef.current
    if (state && pos) {
      const nextBlocks = blocksRef.current.map(b => b.id === state.id ? { ...b, ...pos } : b)
      onChangeRef.current({ ...slideRef.current, _blocks: nextBlocks })
    }
    dragStateRef.current = null
    dragPosRef.current = null
    setDragState(null)
    setDragPos(null)
  }, [])

  // 只绑定一次 listener，不再随渲染重建
  useEffect(() => {
    window.addEventListener('pointermove', handlePointerMove)
    window.addEventListener('pointerup', handlePointerUp)
    return () => {
      window.removeEventListener('pointermove', handlePointerMove)
      window.removeEventListener('pointerup', handlePointerUp)
    }
  }, [handlePointerMove, handlePointerUp])

  // 键盘删除：Delete / Backspace 删除选中块
  useEffect(() => {
    const handleKeyDown = (e) => {
      // 输入框内不处理
      const tag = (e.target?.tagName || '').toLowerCase()
      if (tag === 'input' || tag === 'textarea' || tag === 'select') return
      if ((e.key === 'Delete' || e.key === 'Backspace') && selectedId) {
        e.preventDefault()
        const nextBlocks = blocks.filter(b => b.id !== selectedId)
        onChange({ ...slide, _blocks: nextBlocks })
        setSelectedId(null)
      }
    }
    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [selectedId, blocks, slide, onChange])

  if (!slide) return null

  const openEdit = (block) => {
    setEditing(block)
    setEditValue(block.content)
    setEditFont(block.fontFamily || '')
    setEditSize(block.fontSize || (block.type === 'title' ? 28 : block.type === 'text' ? 16 : block.type === 'list' ? 14 : 16))
  }

  const saveEdit = () => {
    if (!editing) return
    const patch = { content: editValue }
    if (editing.type !== 'image') {
      patch.fontFamily = editFont
      patch.fontSize = editSize
    }
    const nextBlocks = blocks.map(b => b.id === editing.id ? { ...b, ...patch } : b)
    onChange({ ...slide, _blocks: nextBlocks })
    setEditing(null)
  }

  const addBlock = (type) => {
    const defaults = {
      title: { type: 'title', content: '新标题', w: 50, h: 10, fontFamily: '', fontSize: 28 },
      text: { type: 'text', content: '新文本内容', w: 40, h: 8, fontFamily: '', fontSize: 16 },
      list: { type: 'list', content: '要点1\n要点2', w: 50, h: 16, fontFamily: '', fontSize: 14 },
      formula: { type: 'formula', content: 'a^2 + b^2 = c^2', w: 40, h: 8, fontFamily: '', fontSize: 20 },
      image: { type: 'image', content: '', w: 40, h: 25 },
    }
    const d = defaults[type]
    const newBlock = {
      id: nextId(),
      ...d,
      x: 30 + Math.random() * 20,
      y: 30 + Math.random() * 30,
    }
    const nextBlocks = [...blocks, newBlock]
    onChange({ ...slide, _blocks: nextBlocks })
    setSelectedId(newBlock.id)

    // 图片块：选择图片后填充内容（用 ref 记录目标 block，避免闭包旧值）
    if (type === 'image') {
      pendingImageIdRef.current = newBlock.id
      setTimeout(() => fileInputRef.current?.click(), 50)
    }
  }

  const handleImageUpload = (e) => {
    const file = e.target.files?.[0]
    if (!file) return
    const targetId = pendingImageIdRef.current
    if (!targetId) return

    // 图片压缩：限制最长边 1200px，避免超大 base64 导致卡顿
    const img = new Image()
    const objectUrl = URL.createObjectURL(file)
    img.onload = () => {
      const maxSize = 1200
      let { width, height } = img
      const scale = Math.min(1, maxSize / Math.max(width, height))
      width = Math.round(width * scale)
      height = Math.round(height * scale)

      const canvas = document.createElement('canvas')
      canvas.width = width
      canvas.height = height
      const ctx = canvas.getContext('2d')
      ctx.drawImage(img, 0, 0, width, height)

      // 压缩到 JPEG 质量 0.8
      const compressed = canvas.toDataURL('image/jpeg', 0.8)
      URL.revokeObjectURL(objectUrl)

      const nextBlocks = (slide._blocks || blocks).map(b => b.id === targetId
        ? { ...b, content: compressed }
        : b)
      onChange({ ...slide, _blocks: nextBlocks })
      pendingImageIdRef.current = null
    }
    img.onerror = () => {
      URL.revokeObjectURL(objectUrl)
      pendingImageIdRef.current = null
    }
    img.src = objectUrl
    e.target.value = ''
  }

  const deleteBlock = (id) => {
    const nextBlocks = blocks.filter(b => b.id !== id)
    onChange({ ...slide, _blocks: nextBlocks })
    setSelectedId(null)
  }

  const renderBlockContent = (block) => {
    // 每个块独立的字体/字号设置
    const blockFontStyle = {
      color: colorScheme.text || '#fff',
    }
    if (block.fontFamily) {
      blockFontStyle.fontFamily = block.fontFamily
    } else {
      blockFontStyle.fontFamily = fontFamily.body || fontFamily.title || 'Inter, sans-serif'
    }
    const fontSizePx = (block.fontSize || 16) * (typeof window !== 'undefined' && window.innerWidth < 768 ? 0.7 : 1)

    switch (block.type) {
      case 'image':
        return block.content ? (
          <img
            src={block.content}
            alt="图片"
            className="w-full h-full object-cover rounded-md"
            draggable={false}
          />
        ) : (
          <div className="w-full h-full flex flex-col items-center justify-center bg-white/5 border-2 border-dashed border-white/25 rounded-md text-white/40 hover:text-white/70 hover:bg-white/10 cursor-pointer" onClick={(e) => { e.stopPropagation(); setSelectedId(block.id); pendingImageIdRef.current = block.id; fileInputRef.current?.click() }}>
            <ImageIcon className="w-6 h-6 mb-1" />
            <span className="text-[10px]">上传图片</span>
          </div>
        )
      case 'title':
        return <div className="font-bold leading-tight" style={{ fontSize: fontSizePx, ...blockFontStyle }}>{block.content || '标题'}</div>
      case 'formula':
        return (
          <div className="h-full flex items-center bg-black/25 rounded-lg px-3 py-1 overflow-x-auto">
            <ReactMarkdown remarkPlugins={[remarkMath]} rehypePlugins={[rehypeKatex]}>
              {`$${block.content}$`}
            </ReactMarkdown>
          </div>
        )
      case 'list':
        return (
          <div className="space-y-1.5">
            {block.content.split('\n').filter(Boolean).map((point, i) => (
              <div key={i} className="flex items-start gap-2">
                <span className="mt-1.5 w-1.5 h-1.5 rounded-full shrink-0" style={{ backgroundColor: accentColor }} />
                <div className="leading-relaxed" style={{ fontSize: fontSizePx, ...blockFontStyle }}>{point}</div>
              </div>
            ))}
          </div>
        )
      case 'highlights':
        return (
          <div className="flex flex-wrap gap-1.5">
            {block.content.split('\n').filter(Boolean).map((hp, i) => (
              <span key={i} className="text-[10px] px-2 py-0.5 rounded-full border border-amber-400/40 bg-amber-500/15 text-amber-200">★ {hp}</span>
            ))}
          </div>
        )
      case 'interaction':
        return (
          <div className="flex items-center gap-2 bg-amber-500/15 border border-amber-400/25 rounded-lg px-3 py-2">
            <span className="text-[10px] px-1.5 py-0.5 rounded bg-amber-500/20 text-amber-200 shrink-0">互动</span>
            <span className="text-xs text-amber-100/80 truncate">{block.content}</span>
          </div>
        )
      default:
        return <div className="leading-relaxed" style={{ fontSize: fontSizePx, ...blockFontStyle }}>{block.content}</div>
    }
  }

  const bgGrad = TYPE_BG[slide.type] || TYPE_BG.content

  return (
    <div className="flex flex-col h-full">
      {/* 画布滚动区 */}
      <div className="flex-1 overflow-y-auto m5-scroll">
        <div className="flex flex-col items-center min-h-full p-3 md:p-4">
          {/* 16:9 画布 */}
          <div
            ref={canvasRef}
            className={`relative w-full max-w-4xl rounded-xl overflow-hidden shadow-2xl shadow-purple-500/10`}
            style={{ aspectRatio: '16/9', backgroundColor: canvasStyle.backgroundColor || 'transparent', color: canvasStyle.color, fontFamily: canvasStyle.fontFamily }}
            onClick={() => setSelectedId(null)}
          >
            {/* 类型渐变装饰层（半透明，不遮挡背景色） */}
            <div className={`absolute inset-0 bg-gradient-to-br ${bgGrad} opacity-40 pointer-events-none`} />
            {/* 背景装饰 */}
            <div className="absolute -top-16 -right-16 w-48 h-48 rounded-full blur-3xl opacity-30 pointer-events-none" style={{ backgroundColor: primaryColor }} />
            <div className="absolute -bottom-16 -left-16 w-48 h-48 rounded-full blur-3xl opacity-20 pointer-events-none" style={{ backgroundColor: primaryColor }} />

            {/* 顶部工具条 */}
            <div className="absolute top-1.5 left-1.5 right-1.5 z-20 flex items-center justify-between">
              <div className="flex items-center gap-1 flex-wrap">
                {typeOptions.map(t => (
                  <button
                    key={t}
                    onClick={(e) => { e.stopPropagation(); onChange({ ...slide, type: t }) }}
                    className={`text-[9px] px-1.5 py-0.5 rounded-full border transition-all ${
                      slide.type === t
                        ? 'bg-white/25 text-white border-white/40'
                        : 'bg-black/20 text-white/50 border-white/10 hover:bg-white/10'
                    }`}
                  >
                    {typeLabels[t]}
                  </button>
                ))}
              </div>
              <span className="text-[9px] text-white/50 shrink-0">{currentIndex + 1} / {total}</span>
            </div>

            {/* 内容 blocks */}
            {blocks.map(block => {
              const selected = selectedId === block.id
              // 拖拽中：被拖块用本地 dragPos 定位（仅本组件重渲染）
              const displayPos = (dragState && dragState.id === block.id && dragPos) ? dragPos : block
              return (
                <div
                  key={block.id}
                  onPointerDown={(e) => onPointerDown(e, block, 'move')}
                  onClick={(e) => e.stopPropagation()}
                  onDoubleClick={(e) => { e.stopPropagation(); openEdit(block) }}
                  className={`absolute group cursor-move ${selected ? 'z-10' : ''}`}
                  style={{
                    left: `${displayPos.x}%`,
                    top: `${displayPos.y}%`,
                    width: `${displayPos.w}%`,
                    height: `${displayPos.h}%`,
                  }}
                >
                  {/* 选中框 */}
                  <div className={`w-full h-full rounded-lg p-1.5 transition-all ${selected ? 'ring-2 ring-purple-400 bg-purple-400/5' : 'hover:ring-1 hover:ring-purple-400/30'}`}>
                    {renderBlockContent(block)}

                    {/* 选中工具栏 */}
                    {selected && (
                      <div className="absolute -top-6 left-0 flex items-center gap-0.5 bg-purple-500/95 rounded-lg px-1 py-0.5 shadow-lg">
                        <button onClick={() => openEdit(block)} className="p-0.5 rounded hover:bg-white/20 text-white" title="编辑">
                          <Pencil className="w-3 h-3" />
                        </button>
                        <button onClick={() => deleteBlock(block.id)} className="p-0.5 rounded hover:bg-red-500/40 text-white" title="删除">
                          <Trash2 className="w-3 h-3" />
                        </button>
                      </div>
                    )}

                    {/* 缩放手柄 */}
                    {selected && (
                      <div
                        onPointerDown={(e) => onPointerDown(e, block, 'resize')}
                        className="absolute -bottom-1.5 -right-1.5 w-3.5 h-3.5 rounded-sm bg-purple-400 border-2 border-white cursor-nwse-resize"
                        title="调整大小"
                      />
                    )}
                  </div>
                </div>
              )
            })}

            {/* 添加元素按钮（右下角） */}
            <div className="absolute bottom-2 right-2 z-20 flex gap-1.5">
              {[['title', '标题'], ['text', '文本'], ['list', '列表'], ['formula', '公式'], ['image', '图片']].map(([type, label]) => (
                <button
                  key={type}
                  onClick={(e) => { e.stopPropagation(); addBlock(type) }}
                  className="flex items-center gap-1 text-[9px] px-2 py-1 rounded-full bg-black/40 border border-white/15 text-white/70 hover:bg-black/60 hover:text-white transition-all"
                >
                  <Plus className="w-2.5 h-2.5" /> {label}
                </button>
              ))}
            </div>

            {/* 隐藏的文件选择器（图片上传） */}
            <input
              ref={fileInputRef}
              type="file"
              accept="image/*"
              className="hidden"
              onChange={handleImageUpload}
            />

            {/* 翻页按钮 */}
            <button
              onClick={(e) => { e.stopPropagation(); onPrev() }}
              disabled={currentIndex === 0}
              className="absolute left-1 top-1/2 -translate-y-1/2 w-7 h-7 rounded-full bg-black/40 border border-white/15 flex items-center justify-center text-white hover:bg-black/60 disabled:opacity-30 disabled:cursor-not-allowed transition-all z-20"
            >
              <ChevronLeft className="w-4 h-4" />
            </button>
            <button
              onClick={(e) => { e.stopPropagation(); onNext() }}
              disabled={currentIndex === total - 1}
              className="absolute right-1 top-1/2 -translate-y-1/2 w-7 h-7 rounded-full bg-black/40 border border-white/15 flex items-center justify-center text-white hover:bg-black/60 disabled:opacity-30 disabled:cursor-not-allowed transition-all z-20"
            >
              <ChevronRight className="w-4 h-4" />
            </button>
          </div>

          {/* 操作提示 */}
          <div className="mt-3 text-[11px] text-purple-200/50 text-center">
            单击选中 · 双击编辑 · Delete 删除 · 拖动移动 · 右下角拖拽缩放
          </div>
        </div>
      </div>

      {/* 编辑弹窗 */}
      {editing && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm">
          <div className="bg-gradient-to-br from-[#6B1CCF] to-[#4A0E8F] rounded-2xl p-6 w-full max-w-lg mx-4 border border-purple-400/20 shadow-2xl">
            <div className="flex items-center justify-between mb-4">
              <h3 className="text-white font-semibold">编辑元素</h3>
              <button onClick={() => setEditing(null)} className="p-1 rounded-lg hover:bg-white/10 text-white/60">
                <X className="w-4 h-4" />
              </button>
            </div>

            {/* 内容输入 */}
            <textarea
              value={editValue}
              onChange={(e) => setEditValue(e.target.value)}
              rows={editing.type === 'title' ? 2 : 5}
              autoFocus
              placeholder={editing.type === 'list' ? '每行一个要点' : '输入内容...'}
              className="w-full bg-white/5 border border-white/10 rounded-xl px-3 py-2.5 text-sm text-white outline-none focus:border-purple-400/40 transition-all resize-y"
            />

            {/* 非图片元素：字体 + 字号设置 */}
            {editing.type !== 'image' && (
              <div className="grid grid-cols-2 gap-3 mt-3">
                <div>
                  <p className="text-[10px] text-purple-200/50 mb-1">字体</p>
                  <select
                    value={editFont || ''}
                    onChange={(e) => setEditFont(e.target.value)}
                    className="w-full bg-white/5 border border-white/10 rounded-xl px-3 py-2 text-sm text-white outline-none focus:border-purple-400/40 transition-all appearance-none cursor-pointer"
                  >
                    <option value="" className="bg-[#5A1BCE]">跟随全局</option>
                    {FONT_OPTIONS.map(f => (
                      <option key={f.value} value={f.value} className="bg-[#5A1BCE]">{f.label}</option>
                    ))}
                  </select>
                </div>
                <div>
                  <p className="text-[10px] text-purple-200/50 mb-1">字号</p>
                  <select
                    value={editSize}
                    onChange={(e) => setEditSize(parseInt(e.target.value, 10))}
                    className="w-full bg-white/5 border border-white/10 rounded-xl px-3 py-2 text-sm text-white outline-none focus:border-purple-400/40 transition-all appearance-none cursor-pointer"
                  >
                    {FONT_SIZES.map(s => (
                      <option key={s} value={s} className="bg-[#5A1BCE]">{s}px</option>
                    ))}
                  </select>
                </div>
              </div>
            )}

            <div className="flex justify-end gap-2 mt-4">
              <button
                onClick={() => setEditing(null)}
                className="px-4 py-2 rounded-xl bg-white/10 text-white/70 text-sm hover:bg-white/15 transition-all"
              >
                取消
              </button>
              <button
                onClick={saveEdit}
                className="px-4 py-2 rounded-xl bg-gradient-to-r from-purple-500 to-blue-500 text-white text-sm hover:opacity-90 transition-all"
              >
                保存
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
