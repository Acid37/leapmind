import { useRef } from 'react'
import { Upload } from 'lucide-react'

export default function ImageUploader({ onUpload }) {
  const inputRef = useRef(null)

  const handleClick = () => inputRef.current?.click()

  const handleChange = (e) => {
    const file = e.target.files?.[0]
    if (file) onUpload(file)
    e.target.value = ''
  }

  return (
    <button
      onClick={handleClick}
      className="bg-white/10 backdrop-blur-md rounded-2xl p-8 text-center border border-white/10 hover:bg-white/15 transition-all duration-300 hover:scale-[1.02] group w-full cursor-pointer"
    >
      <input ref={inputRef} type="file" accept="image/*" onChange={handleChange} className="hidden" />
      <div className="w-14 h-14 mx-auto mb-3 bg-gradient-to-br from-pink-400 to-orange-300 rounded-xl flex items-center justify-center shadow-lg group-hover:shadow-pink-500/30 transition-shadow">
        <Upload className="w-7 h-7 text-white" />
      </div>
      <p className="text-white font-semibold">上传图片</p>
      <p className="text-xs text-purple-200/60 mt-1">从相册选择题目图片</p>
    </button>
  )
}
