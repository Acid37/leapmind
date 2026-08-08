import { Edit3 } from 'lucide-react'

export default function OCRResultCard({ result, onEdit }) {
  const { recognizedText, structuredQuestion, confidence } = result

  const handleStemEdit = (text) => {
    onEdit?.({
      recognizedText: text,
      stem: text,
      options: structuredQuestion.options
    })
  }

  const handleOptionEdit = (index, text) => {
    const newOptions = [...(structuredQuestion.options || [])]
    newOptions[index] = text
    onEdit?.({
      recognizedText: recognizedText,
      stem: structuredQuestion.stem,
      options: newOptions
    })
  }

  return (
    <div className="bg-white/10 backdrop-blur-md rounded-2xl p-5 border border-white/10 mb-4">
      <div className="flex items-center justify-between mb-3">
        <h3 className="font-semibold text-white text-sm flex items-center gap-2">
          <Edit3 className="w-4 h-4 text-purple-300" />
          识别结果
        </h3>
        <span className={`text-xs px-2.5 py-1 rounded-full font-medium ${
          confidence > 0.8
            ? 'bg-green-500/20 text-green-300 border border-green-400/20'
            : 'bg-yellow-500/20 text-yellow-300 border border-yellow-400/20'
        }`}>
          置信度 {(confidence * 100).toFixed(0)}%
        </span>
      </div>

      <div className="mb-3">
        <label className="block text-xs text-purple-200/60 mb-1.5">识别文本</label>
        <textarea
          className="w-full bg-white/5 border border-white/10 rounded-xl p-3 text-sm text-white/90 placeholder-purple-200/30 focus:outline-none focus:border-purple-400/50 resize-none"
          rows={3}
          value={recognizedText}
          onChange={(e) => handleStemEdit(e.target.value)}
        />
      </div>

      <div className="bg-white/5 rounded-xl p-4 space-y-3">
        <div className="flex items-center gap-2 text-sm">
          <span className="text-purple-200/60 w-12 shrink-0">题型</span>
          <span className="text-white/90">{structuredQuestion.type === 'single_choice' ? '单选题' : structuredQuestion.type}</span>
        </div>
        <div className="flex items-center gap-2 text-sm">
          <span className="text-purple-200/60 w-12 shrink-0">科目</span>
          <span className="text-white/90">{structuredQuestion.subject}</span>
        </div>
        <div>
          <span className="text-purple-200/60 text-sm">题干</span>
          <textarea
            className="w-full bg-white/5 border border-white/10 rounded-xl p-2.5 mt-1 text-sm text-white/90 focus:outline-none focus:border-purple-400/50 resize-none"
            rows={2}
            value={structuredQuestion.stem}
            onChange={(e) => onEdit?.({
              recognizedText: e.target.value,
              stem: e.target.value,
              options: structuredQuestion.options
            })}
          />
        </div>
        {structuredQuestion.options && (
          <div>
            <span className="text-purple-200/60 text-sm">选项</span>
            <div className="grid grid-cols-2 gap-2 mt-1.5">
              {structuredQuestion.options.map((opt, i) => (
                <div key={i} className="bg-white/5 border border-white/5 rounded-lg overflow-hidden flex items-center">
                  <span className="text-purple-300 font-medium text-sm px-2 py-2 shrink-0">{String.fromCharCode(65 + i)}.</span>
                  <input
                    className="flex-1 bg-transparent text-sm text-white/80 py-2 pr-2 focus:outline-none placeholder-white/30"
                    value={opt}
                    onChange={(e) => handleOptionEdit(i, e.target.value)}
                  />
                </div>
              ))}
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
