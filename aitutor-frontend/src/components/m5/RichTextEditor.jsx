import { useEditor, EditorContent } from '@tiptap/react'
import StarterKit from '@tiptap/starter-kit'
import Image from '@tiptap/extension-image'
import Link from '@tiptap/extension-link'
import Placeholder from '@tiptap/extension-placeholder'
import {
  Bold, Italic, Strikethrough, Code, Heading1, Heading2,
  List, ListOrdered, Quote, Undo2, Redo2, Link as LinkIcon,
  Image as ImageIcon, SquareFunction,
} from 'lucide-react'

const toolbarStyles = `
  .rt-editor .ProseMirror {
    min-height: 140px;
    outline: none;
    color: rgba(255,255,255,0.92);
    font-size: 0.875rem;
    line-height: 1.75;
  }
  .rt-editor .ProseMirror p { margin: 0 0 0.5em; }
  .rt-editor .ProseMirror h1 { font-size: 1.35em; font-weight: 700; margin: 0.6em 0 0.4em; color: #fff; }
  .rt-editor .ProseMirror h2 { font-size: 1.15em; font-weight: 600; margin: 0.6em 0 0.4em; color: rgba(255,255,255,0.95); }
  .rt-editor .ProseMirror h3 { font-size: 1em; font-weight: 600; margin: 0.5em 0 0.3em; }
  .rt-editor .ProseMirror ul, .rt-editor .ProseMirror ol { padding-left: 1.4em; margin: 0.4em 0; }
  .rt-editor .ProseMirror ul { list-style: disc; }
  .rt-editor .ProseMirror ol { list-style: decimal; }
  .rt-editor .ProseMirror blockquote {
    border-left: 3px solid rgba(168,134,255,0.6);
    padding-left: 0.8em;
    margin: 0.5em 0;
    color: rgba(255,255,255,0.6);
  }
  .rt-editor .ProseMirror code {
    background: rgba(0,0,0,0.35);
    padding: 0.15em 0.4em;
    border-radius: 4px;
    font-size: 0.85em;
    color: #c4b5fd;
  }
  .rt-editor .ProseMirror a { color: #93c5fd; text-decoration: underline; }
  .rt-editor .ProseMirror img { max-width: 100%; border-radius: 8px; margin: 0.5em 0; }
  .rt-editor .ProseMirror p.is-editor-empty:first-child::before {
    content: attr(data-placeholder);
    color: rgba(255,255,255,0.25);
    float: left;
    height: 0;
    pointer-events: none;
  }
`

function ToolbarButton({ onClick, active, disabled, children, title, activeClass }) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      title={title}
      className={`p-1.5 rounded-lg transition-all ${
        active
          ? (activeClass || 'bg-purple-500/30 text-purple-200 border border-purple-400/30')
          : 'text-white/60 hover:bg-white/10 hover:text-white border border-transparent'
      } disabled:opacity-30 disabled:cursor-not-allowed`}
    >
      {children}
    </button>
  )
}

// 各强调色对应的 active 工具栏样式
const ACCENTS = {
  sky: { active: 'bg-sky-500/30 text-sky-200 border border-sky-400/30', border: 'focus-within:border-sky-400/40', toolbarBg: 'bg-sky-500/10' },
  purple: { active: 'bg-purple-500/30 text-purple-200 border border-purple-400/30', border: 'focus-within:border-purple-400/40', toolbarBg: 'bg-purple-500/10' },
  emerald: { active: 'bg-emerald-500/30 text-emerald-200 border border-emerald-400/30', border: 'focus-within:border-emerald-400/40', toolbarBg: 'bg-emerald-500/10' },
  amber: { active: 'bg-amber-500/30 text-amber-200 border border-amber-400/30', border: 'focus-within:border-amber-400/40', toolbarBg: 'bg-amber-500/10' },
  rose: { active: 'bg-rose-500/30 text-rose-200 border border-rose-400/30', border: 'focus-within:border-rose-400/40', toolbarBg: 'bg-rose-500/10' },
  fuchsia: { active: 'bg-fuchsia-500/30 text-fuchsia-200 border border-fuchsia-400/30', border: 'focus-within:border-fuchsia-400/40', toolbarBg: 'bg-fuchsia-500/10' },
  teal: { active: 'bg-teal-500/30 text-teal-200 border border-teal-400/30', border: 'focus-within:border-teal-400/40', toolbarBg: 'bg-teal-500/10' },
}

export default function RichTextEditor({ value = '', onChange, placeholder = '请输入内容...', minHeight = 140, accentColor = 'purple' }) {
  const accent = ACCENTS[accentColor] || ACCENTS.purple
  const editor = useEditor({
    extensions: [
      StarterKit,
      Image.configure({ inline: true }),
      Link.configure({ openOnClick: false, autolink: true }),
      Placeholder.configure({ placeholder }),
    ],
    content: value,
    onUpdate: ({ editor }) => {
      onChange?.(editor.getHTML())
    },
    editorProps: {
      attributes: {
        class: `rt-content`,
        style: `min-height: ${minHeight}px;`,
      },
    },
  })

  const addLink = () => {
    if (!editor) return
    const url = window.prompt('输入链接地址：')
    if (url) {
      editor.chain().focus().extendMarkRange('link').setLink({ href: url }).run()
    }
  }

  const addImage = () => {
    if (!editor) return
    const url = window.prompt('输入图片地址：')
    if (url) {
      editor.chain().focus().setImage({ src: url }).run()
    }
  }

  const addFormula = () => {
    if (!editor) return
    const formula = window.prompt('输入公式（LaTeX，如 a^2 + b^2 = c^2）：')
    if (formula) {
      editor.chain().focus().insertContent(` $${formula}$ `).run()
    }
  }

  if (!editor) return null

  return (
    <>
      <style>{toolbarStyles}</style>
      <div className={`rt-editor bg-white/5 border border-white/10 rounded-xl overflow-hidden ${accent.border} transition-all`}>
        {/* 工具栏 */}
        <div className={`flex items-center flex-wrap gap-0.5 px-2 py-1.5 border-b border-white/10 ${accent.toolbarBg}`}>
          <ToolbarButton activeClass={accent.active} onClick={() => editor.chain().focus().toggleBold().run()} active={editor.isActive('bold')} title="加粗">
            <Bold className="w-4 h-4" />
          </ToolbarButton>
          <ToolbarButton activeClass={accent.active} onClick={() => editor.chain().focus().toggleItalic().run()} active={editor.isActive('italic')} title="斜体">
            <Italic className="w-4 h-4" />
          </ToolbarButton>
          <ToolbarButton activeClass={accent.active} onClick={() => editor.chain().focus().toggleStrike().run()} active={editor.isActive('strike')} title="删除线">
            <Strikethrough className="w-4 h-4" />
          </ToolbarButton>
          <ToolbarButton activeClass={accent.active} onClick={() => editor.chain().focus().toggleCode().run()} active={editor.isActive('code')} title="行内代码">
            <Code className="w-4 h-4" />
          </ToolbarButton>
          <div className="w-px h-5 bg-white/10 mx-1" />
          <ToolbarButton activeClass={accent.active} onClick={() => editor.chain().focus().toggleHeading({ level: 1 }).run()} active={editor.isActive('heading', { level: 1 })} title="大标题">
            <Heading1 className="w-4 h-4" />
          </ToolbarButton>
          <ToolbarButton activeClass={accent.active} onClick={() => editor.chain().focus().toggleHeading({ level: 2 }).run()} active={editor.isActive('heading', { level: 2 })} title="小标题">
            <Heading2 className="w-4 h-4" />
          </ToolbarButton>
          <div className="w-px h-5 bg-white/10 mx-1" />
          <ToolbarButton activeClass={accent.active} onClick={() => editor.chain().focus().toggleBulletList().run()} active={editor.isActive('bulletList')} title="无序列表">
            <List className="w-4 h-4" />
          </ToolbarButton>
          <ToolbarButton activeClass={accent.active} onClick={() => editor.chain().focus().toggleOrderedList().run()} active={editor.isActive('orderedList')} title="有序列表">
            <ListOrdered className="w-4 h-4" />
          </ToolbarButton>
          <ToolbarButton activeClass={accent.active} onClick={() => editor.chain().focus().toggleBlockquote().run()} active={editor.isActive('blockquote')} title="引用">
            <Quote className="w-4 h-4" />
          </ToolbarButton>
          <div className="w-px h-5 bg-white/10 mx-1" />
          <ToolbarButton activeClass={accent.active} onClick={addLink} active={editor.isActive('link')} title="插入链接">
            <LinkIcon className="w-4 h-4" />
          </ToolbarButton>
          <ToolbarButton activeClass={accent.active} onClick={addImage} title="插入图片">
            <ImageIcon className="w-4 h-4" />
          </ToolbarButton>
          <ToolbarButton activeClass={accent.active} onClick={addFormula} title="插入公式 (KaTeX)">
            <SquareFunction className="w-4 h-4" />
          </ToolbarButton>
          <div className="w-px h-5 bg-white/10 mx-1" />
          <ToolbarButton activeClass={accent.active} onClick={() => editor.chain().focus().undo().run()} disabled={!editor.can().undo()} title="撤销">
            <Undo2 className="w-4 h-4" />
          </ToolbarButton>
          <ToolbarButton activeClass={accent.active} onClick={() => editor.chain().focus().redo().run()} disabled={!editor.can().redo()} title="重做">
            <Redo2 className="w-4 h-4" />
          </ToolbarButton>
        </div>
        {/* 编辑区 */}
        <EditorContent editor={editor} className="px-3 py-2" />
      </div>
    </>
  )
}
