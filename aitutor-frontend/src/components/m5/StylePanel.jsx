import { Palette, Type, LayoutTemplate, Check } from 'lucide-react'

const FONT_OPTIONS = [
  { value: 'PingFang SC', label: '苹方' },
  { value: 'Microsoft YaHei', label: '微软雅黑' },
  { value: 'SimSun', label: '宋体' },
  { value: 'Inter', label: 'Inter' },
  { value: 'Georgia', label: 'Georgia' },
]

const PRESET_COLORS = [
  { primary: '#2563EB', secondary: '#7C3AED', accent: '#F59E0B', name: '商务蓝' },
  { primary: '#059669', secondary: '#0D9488', accent: '#FBBF24', name: '清新绿' },
  { primary: '#E11D48', secondary: '#9333EA', accent: '#FDE047', name: '活力红' },
  { primary: '#7C3AED', secondary: '#6366F1', accent: '#F472B6', name: '梦幻紫' },
  { primary: '#F97316', secondary: '#F59E0B', accent: '#10B981', name: '暖阳橙' },
  { primary: '#0891B2', secondary: '#06B6D4', accent: '#F59E0B', name: '海洋青' },
]

export default function StylePanel({ templates = [], currentStyle = {}, onSelectTemplate, onColorChange, onFontChange }) {
  const colors = currentStyle.colorScheme || {}

  return (
    <div className="bg-white/10 backdrop-blur-md rounded-2xl border border-purple-400/20 flex flex-col overflow-hidden h-full">
      {/* 标题 */}
      <div className="px-3 py-2.5 border-b border-white/10 bg-gradient-to-r from-purple-500/10 to-transparent">
        <h3 className="text-sm font-semibold text-white">样式设置</h3>
      </div>

      <div className="flex-1 overflow-y-auto m5-list-scroll p-3 space-y-4">
        {/* 模板选择 */}
        <div>
          <div className="flex items-center gap-1.5 mb-2">
            <LayoutTemplate className="w-3.5 h-3.5 text-purple-300" />
            <span className="text-xs font-medium text-white">模板</span>
          </div>
          {templates.length === 0 ? (
            <p className="text-xs text-purple-200/40 text-center py-3">暂无模板</p>
          ) : (
            <div className="grid grid-cols-2 gap-2">
              {templates.map(template => {
                let cfg = {}
                try { cfg = JSON.parse(template.configJson || '{}') } catch (e) { cfg = {} }
                const primary = cfg.colorScheme?.primary || '#2563EB'
                const selected = currentStyle.templateId === template.id
                return (
                  <button
                    key={template.id}
                    onClick={() => onSelectTemplate(template)}
                    className={`
                      relative p-2.5 rounded-xl border transition-all text-left
                      ${selected ? 'border-purple-400 bg-purple-500/15' : 'border-white/10 bg-white/5 hover:bg-white/10'}
                    `}
                  >
                    {selected && (
                      <span className="absolute top-1 right-1 w-4 h-4 bg-purple-400 rounded-full flex items-center justify-center">
                        <Check className="w-2.5 h-2.5 text-white" />
                      </span>
                    )}
                    {/* 配色预览 */}
                    <div className="flex gap-1 mb-1.5">
                      {[cfg.colorScheme?.primary, cfg.colorScheme?.secondary, cfg.colorScheme?.accent].filter(Boolean).map((c, i) => (
                        <span key={i} className="w-5 h-4 rounded-sm" style={{ backgroundColor: c }} />
                      ))}
                    </div>
                    <p className="text-[11px] text-white/80 truncate">{template.name}</p>
                    {template.isSystem === 1 && (
                      <span className="text-[9px] text-purple-300/60">系统模板</span>
                    )}
                    <div className="mt-0.5 hidden" style={{ backgroundColor: primary }} />
                  </button>
                )
              })}
            </div>
          )}
        </div>

        {/* 配色方案 */}
        <div>
          <div className="flex items-center gap-1.5 mb-2">
            <Palette className="w-3.5 h-3.5 text-purple-300" />
            <span className="text-xs font-medium text-white">配色方案</span>
          </div>
          <div className="grid grid-cols-3 gap-2">
            {PRESET_COLORS.map(preset => {
              const selected = colors.primary === preset.primary
              return (
                <button
                  key={preset.name}
                  onClick={() => onColorChange(preset)}
                  className={`
                    p-2 rounded-xl border transition-all
                    ${selected ? 'border-purple-400 bg-purple-500/15' : 'border-white/10 bg-white/5 hover:bg-white/10'}
                  `}
                >
                  <div className="flex gap-1 mb-1">
                    <span className="w-4 h-4 rounded-full" style={{ backgroundColor: preset.primary }} />
                    <span className="w-4 h-4 rounded-full" style={{ backgroundColor: preset.secondary }} />
                    <span className="w-4 h-4 rounded-full" style={{ backgroundColor: preset.accent }} />
                  </div>
                  <p className="text-[10px] text-white/60 text-center">{preset.name}</p>
                </button>
              )
            })}
          </div>
        </div>

        {/* 字体选择 */}
        <div>
          <div className="flex items-center gap-1.5 mb-2">
            <Type className="w-3.5 h-3.5 text-purple-300" />
            <span className="text-xs font-medium text-white">字体</span>
          </div>
          <select
            value={currentStyle.fontFamily?.title || 'PingFang SC'}
            onChange={(e) => onFontChange(e.target.value)}
            className="w-full bg-white/5 border border-white/10 rounded-xl px-3 py-2.5 text-sm text-white outline-none focus:border-purple-400/40 transition-all appearance-none cursor-pointer"
          >
            {FONT_OPTIONS.map(font => (
              <option key={font.value} value={font.value} className="bg-[#5A1BCE]">{font.label}</option>
            ))}
          </select>
        </div>
      </div>
    </div>
  )
}
