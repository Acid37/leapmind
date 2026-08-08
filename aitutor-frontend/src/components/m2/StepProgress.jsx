import { Check } from 'lucide-react'

export default function StepProgress({ steps, currentStep, onStepClick }) {
  return (
    <div className="relative">
      <div className="absolute top-4 left-6 right-6 h-0.5 bg-white/10" />
      <div
        className="absolute top-4 left-6 h-0.5 bg-gradient-to-r from-purple-400 to-blue-400 transition-all duration-500 ease-out"
        style={{ width: `${(currentStep / (steps.length - 1)) * 100}%`, maxWidth: 'calc(100% - 3rem)' }}
      />
      <div className="relative flex justify-between">
        {steps.map((step, i) => {
          const isCompleted = i < currentStep
          const isCurrent = i === currentStep
          return (
            <button
              key={i}
              onClick={() => onStepClick?.(i)}
              className="flex flex-col items-center gap-1.5 group"
            >
              <div
                className={`w-8 h-8 rounded-full flex items-center justify-center text-xs font-bold transition-all duration-300 ${
                  isCompleted
                    ? 'bg-gradient-to-r from-purple-400 to-blue-400 text-white shadow-lg shadow-purple-500/30'
                    : isCurrent
                      ? 'bg-white text-purple-700 shadow-lg ring-2 ring-purple-400/50'
                      : 'bg-white/10 text-white/40'
                }`}
              >
                {isCompleted ? <Check className="w-4 h-4" /> : i + 1}
              </div>
              <span
                className={`text-[10px] font-medium whitespace-nowrap transition-colors duration-300 ${
                  isCurrent ? 'text-white' : isCompleted ? 'text-purple-200/60' : 'text-white/30'
                }`}
              >
                {step.title}
              </span>
            </button>
          )
        })}
      </div>
    </div>
  )
}
