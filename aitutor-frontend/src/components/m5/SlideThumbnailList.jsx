import { useMemo } from 'react'
import {
  DndContext, closestCenter, PointerSensor, useSensor, useSensors,
} from '@dnd-kit/core'
import {
  arrayMove, SortableContext, verticalListSortingStrategy, useSortable,
} from '@dnd-kit/sortable'
import { CSS } from '@dnd-kit/utilities'
import { Plus, Trash2, GripVertical } from 'lucide-react'
import SlideRenderer from '../shared/SlideRenderer'

function SortableThumb({ slide, index, active, onClick, onDelete, styleVars }) {
  const {
    attributes, listeners, setNodeRef, transform, transition, isDragging,
  } = useSortable({ id: String(slide.pageNum || index) })

  const style = {
    transform: CSS.Transform.toString(transform),
    transition,
    opacity: isDragging ? 0.4 : 1,
    zIndex: isDragging ? 50 : 'auto',
  }

  return (
    <div
      ref={setNodeRef}
      style={style}
      className={`
        group relative rounded-xl border-2 overflow-hidden transition-all cursor-pointer
        ${active ? 'border-purple-400 shadow-lg shadow-purple-500/20' : 'border-transparent hover:border-white/20'}
      `}
    >
      {/* 缩略图 */}
      <div onClick={onClick} className="bg-white/5">
        <SlideRenderer slide={slide} compact showTypeBadge={false} styleVars={styleVars} />
      </div>

      {/* 页码 */}
      <div className="flex items-center justify-between px-2 py-1 bg-black/30 backdrop-blur-sm">
        <span className={`text-[10px] ${active ? 'text-purple-300 font-semibold' : 'text-white/60'}`}>{index + 1}</span>
        <div className="flex items-center gap-0.5 opacity-0 group-hover:opacity-100 transition-opacity">
          <button
            onClick={(e) => { e.stopPropagation(); onDelete(slide.pageNum) }}
            className="p-0.5 rounded hover:bg-red-500/20 text-red-300"
          >
            <Trash2 className="w-3 h-3" />
          </button>
          <button {...attributes} {...listeners} className="p-0.5 rounded hover:bg-white/10 text-white/50 cursor-grab active:cursor-grabbing">
            <GripVertical className="w-3 h-3" />
          </button>
        </div>
      </div>
    </div>
  )
}

export default function SlideThumbnailList({ slides = [], activeIndex = 0, onSelect, onReorder, onAdd, onDelete, styleVars }) {
  const sensors = useSensors(useSensor(PointerSensor, { activationConstraint: { distance: 5 } }))

  const items = useMemo(() => slides.map(s => String(s.pageNum || s._idx)), [slides])

  const handleDragEnd = (event) => {
    const { active, over } = event
    if (!over || active.id === over.id) return
    const oldIndex = items.indexOf(active.id)
    const newIndex = items.indexOf(over.id)
    if (oldIndex === -1 || newIndex === -1) return
    onReorder?.(arrayMove(slides, oldIndex, newIndex))
  }

  return (
    <div className="bg-white/10 backdrop-blur-md rounded-2xl border border-purple-400/20 flex flex-col overflow-hidden h-full">
      <div className="px-3 py-2.5 border-b border-white/10 flex items-center justify-between bg-gradient-to-r from-purple-500/10 to-transparent">
        <h3 className="text-sm font-semibold text-white">幻灯片</h3>
        <span className="text-[10px] px-2 py-0.5 rounded-full bg-purple-500/20 text-purple-200 border border-purple-400/20">{slides.length} 页</span>
      </div>

      <div className="flex-1 overflow-y-auto m5-list-scroll p-2 space-y-2">
        <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={handleDragEnd}>
          <SortableContext items={items} strategy={verticalListSortingStrategy}>
            {slides.map((slide, index) => (
              <SortableThumb
                key={slide.pageNum || index}
                slide={slide}
                index={index}
                active={index === activeIndex}
                onClick={() => onSelect(index)}
                onDelete={onDelete}
                styleVars={styleVars}
              />
            ))}
          </SortableContext>
        </DndContext>
      </div>

      <div className="p-2 border-t border-white/10">
        <button
          onClick={onAdd}
          className="w-full py-2 rounded-xl bg-white/5 border border-dashed border-white/20 text-purple-200/60 text-xs hover:bg-white/10 hover:text-white transition-all flex items-center justify-center gap-1.5"
        >
          <Plus className="w-3.5 h-3.5" />
          添加幻灯片
        </button>
      </div>
    </div>
  )
}
