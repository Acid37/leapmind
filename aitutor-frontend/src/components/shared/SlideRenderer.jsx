import ReactMarkdown from 'react-markdown'
import remarkMath from 'remark-math'
import rehypeKatex from 'rehype-katex'
import 'katex/dist/katex.min.css'

const TYPE_BADGES = {
  cover: { label: '封面', cls: 'bg-purple-500/20 text-purple-200 border-purple-400/30' },
  content: { label: '内容', cls: 'bg-sky-500/20 text-sky-200 border-sky-400/30' },
  interactive: { label: '互动', cls: 'bg-amber-500/20 text-amber-200 border-amber-400/30' },
  summary: { label: '总结', cls: 'bg-emerald-500/20 text-emerald-200 border-emerald-400/30' },
  homework: { label: '作业', cls: 'bg-rose-500/20 text-rose-200 border-rose-400/30' },
}

const TYPE_BG = {
  cover: 'from-purple-600/40 via-purple-700/30 to-indigo-700/40',
  content: 'from-sky-600/40 via-blue-700/30 to-indigo-700/40',
  interactive: 'from-amber-600/40 via-orange-700/30 to-rose-700/40',
  summary: 'from-emerald-600/40 via-teal-700/30 to-cyan-700/40',
  homework: 'from-rose-600/40 via-pink-700/30 to-purple-700/40',
}

/**
 * 共享幻灯片渲染组件（M4/M5 共用格式基准）
 * 依据 SlideRenderer_接口约定 v4 的 SlideData 格式
 */
export default function SlideRenderer({
  slide,
  styleVars = {},
  showTypeBadge = true,
  compact = false,
  onClick,
}) {
  if (!slide) return null

  const {
    pageNum, type = 'content', title = '', bulletPoints = [],
    imageSuggestion, formula, highlightPoints = [], interaction,
    _blocks,
  } = slide

  const {
    primaryColor = '#A78BFA',
    secondaryColor = '#818CF8',
    backgroundColor = 'rgba(255,255,255,0.06)',
    textColor = '#ffffff',
    accentColor = '#FBBF24',
    fontFamily = 'Inter, sans-serif',
  } = styleVars

  const badge = TYPE_BADGES[type] || TYPE_BADGES.content
  const bgGrad = TYPE_BG[type] || TYPE_BG.content

  // 是否使用模板实色背景（模板提供 solid 背景色时，去掉类型渐变）
  const hasCustomBg = backgroundColor && backgroundColor !== 'rgba(255,255,255,0.06)' && backgroundColor !== 'transparent'

  // 渲染正文要点（支持 markdown + 公式）
  const renderPoints = (points) => {
    if (!points || points.length === 0) return null
    return (
      <div className={compact ? 'space-y-0.5' : 'space-y-2'}>
        {points.map((point, i) => (
          <div key={i} className="flex items-start gap-2">
            <span className="mt-1.5 w-1.5 h-1.5 rounded-full shrink-0" style={{ backgroundColor: accentColor }} />
            <div className={`${compact ? 'text-[9px]' : 'text-sm'} leading-relaxed`} style={{ color: textColor }}>
              <ReactMarkdown remarkPlugins={[remarkMath]} rehypePlugins={[rehypeKatex]}>
                {String(point)}
              </ReactMarkdown>
            </div>
          </div>
        ))}
      </div>
    )
  }

  // 渲染自由布局 blocks（编辑后保存的布局）
  const renderBlocks = () => {
    if (!_blocks || _blocks.length === 0) return null
    return (
      <div className="absolute inset-0">
        {_blocks.map(block => {
          const blockFontStyle = {}
          if (block.fontFamily) blockFontStyle.fontFamily = block.fontFamily
          if (block.fontSize) blockFontStyle.fontSize = `${block.fontSize * 0.5}px`
          return (
            <div
              key={block.id}
              className="absolute overflow-hidden"
              style={{ left: `${block.x}%`, top: `${block.y}%`, width: `${block.w}%`, height: `${block.h}%`, ...blockFontStyle }}
            >
              {block.type === 'image' && (
                <img src={block.content} alt="" className="w-full h-full object-cover rounded-sm" draggable={false} />
              )}
              {block.type === 'title' && (
                <div className="font-bold leading-tight truncate" style={{ color: textColor }}>{block.content}</div>
              )}
              {block.type === 'formula' && (
                <div className="text-[9px] leading-tight overflow-hidden" style={{ color: textColor }}>
                  <ReactMarkdown remarkPlugins={[remarkMath]} rehypePlugins={[rehypeKatex]}>
                    {`$${block.content}$`}
                  </ReactMarkdown>
                </div>
              )}
              {block.type === 'list' && (
                <div className="space-y-1">
                  {block.content.split('\n').filter(Boolean).map((point, i) => (
                    <div key={i} className="flex items-start gap-1">
                      <span className="mt-1 w-1 h-1 rounded-full shrink-0" style={{ backgroundColor: accentColor }} />
                      <span className="text-[10px] leading-snug truncate" style={{ color: textColor }}>{point}</span>
                    </div>
                  ))}
                </div>
              )}
              {(block.type === 'highlights' || block.type === 'interaction') && (
                <span className="text-[9px] px-1.5 py-0.5 rounded-full border border-amber-400/40 bg-amber-500/15 text-amber-200 truncate inline-block max-w-full">
                  {block.type === 'highlights' ? '★ ' : '💬 '}{block.content}
                </span>
              )}
              {block.type === 'text' && (
                <span className="text-[10px] leading-snug truncate block" style={{ color: textColor }}>{block.content}</span>
              )}
            </div>
          )
        })}
      </div>
    )
  }

  return (
    <div
      onClick={onClick}
      className={`
        relative w-full rounded-xl overflow-hidden select-none
        ${!hasCustomBg ? `bg-gradient-to-br ${bgGrad}` : ''}
        ${!compact ? 'cursor-pointer' : ''}
      `}
      style={{
        aspectRatio: '16/9',
        backgroundColor: hasCustomBg ? backgroundColor : undefined,
        color: hasCustomBg ? textColor : undefined,
        fontFamily,
      }}
    >
      {/* 背景装饰 */}
      <div className="absolute -top-16 -right-16 w-48 h-48 rounded-full blur-3xl opacity-30" style={{ backgroundColor: primaryColor }} />
      <div className="absolute -bottom-16 -left-16 w-48 h-48 rounded-full blur-3xl opacity-20" style={{ backgroundColor: secondaryColor }} />

      {/* 自由布局 blocks（优先渲染编辑后的布局） */}
      {renderBlocks()}

      {/* 无自定义布局时，用默认排版渲染 */}
      {(!_blocks || _blocks.length === 0) && (
      <div className={`relative h-full flex flex-col ${compact ? 'p-3' : 'p-6 md:p-10'}`}>
        {/* 顶部：类型徽章 + 页码 */}
        {(showTypeBadge || !compact) && (
          <div className="flex items-center justify-between shrink-0">
            {showTypeBadge && (
              <span className={`text-[10px] px-2 py-0.5 rounded-full border ${badge.cls}`}>{badge.label}</span>
            )}
            {!compact && (
              <span className="text-[10px] text-white/40">第 {pageNum} 页</span>
            )}
          </div>
        )}

        {/* 标题 */}
        {title && (
          <h2
            className={`font-bold text-white ${compact ? 'text-xs mt-1.5' : 'text-2xl md:text-3xl mt-4'}`}
            style={{ color: textColor }}
          >
            {title}
          </h2>
        )}

        {/* 公式 */}
        {formula && (
          <div className={`${compact ? 'text-[8px] mt-1' : 'text-xl md:text-2xl mt-3'} bg-black/20 rounded-lg px-3 py-2 inline-block self-start max-w-full overflow-x-auto`}>
            <ReactMarkdown remarkPlugins={[remarkMath]} rehypePlugins={[rehypeKatex]}>
              {`$${formula}$`}
            </ReactMarkdown>
          </div>
        )}

        {/* 正文要点 */}
        <div className={`${compact ? 'mt-2' : 'mt-5'} flex-1 overflow-hidden`}>
          {renderPoints(bulletPoints)}
        </div>

        {/* 高亮词 */}
        {highlightPoints && highlightPoints.length > 0 && !compact && (
          <div className="shrink-0 mt-3 flex flex-wrap gap-1.5">
            {highlightPoints.map((hp, i) => (
              <span key={i} className="text-[10px] px-2 py-0.5 rounded-full border" style={{ color: accentColor, borderColor: `${accentColor}55`, backgroundColor: `${accentColor}15` }}>
                ★ {hp}
              </span>
            ))}
          </div>
        )}

        {/* 互动题提示 */}
        {interaction && interaction.type && !compact && (
          <div className="shrink-0 mt-3 flex items-center gap-2 bg-amber-500/15 border border-amber-400/25 rounded-lg px-3 py-2">
            <span className="text-[10px] px-1.5 py-0.5 rounded bg-amber-500/20 text-amber-200 shrink-0">
              {interaction.type === 'choice_question' ? '选择题' : '思考题'}
            </span>
            <span className="text-xs text-amber-100/80 truncate">{interaction.question}</span>
          </div>
        )}

        {/* 配图提示 */}
        {imageSuggestion && !compact && (
          <div className="shrink-0 mt-2 text-[10px] text-white/40 flex items-center gap-1.5">
            <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z" />
            </svg>
            <span className="truncate">配图建议：{imageSuggestion}</span>
          </div>
        )}
      </div>
      )}
    </div>
  )
}
