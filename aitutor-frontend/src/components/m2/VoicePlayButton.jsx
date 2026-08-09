import { useState, useRef, useCallback } from 'react'
import { Volume2, VolumeX } from 'lucide-react'

export default function VoicePlayButton({ text }) {
  const [playing, setPlaying] = useState(false)
  const [loading, setLoading] = useState(false)
  const audioRef = useRef(null)

  const doPlay = useCallback(async () => {
    try {
      setLoading(true)
      const res = await fetch('/api/virtual-teacher/tts', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          text: text.slice(0, 500),
          voiceType: 'zhixiaoxia',
          speed: 1.0
        }),
      })
      const json = await res.json()
      if (json.code === 200 && json.data?.audioUrl) {
        if (audioRef.current) {
          audioRef.current.src = json.data.audioUrl
          audioRef.current.play()
          setPlaying(true)
        }
      } else {
        throw new Error(json.message || 'TTS 合成失败')
      }
    } catch {
      // TTS 接口未就绪时 fallback 到 Web Speech API
      if ('speechSynthesis' in window) {
        const utterance = new SpeechSynthesisUtterance(text.replace(/[$\\{}[\]()_^#|]/g, ''))
        utterance.rate = 0.9
        utterance.lang = 'zh-CN'
        utterance.onend = () => setPlaying(false)
        setPlaying(true)
        speechSynthesis.speak(utterance)
      }
    } finally {
      setLoading(false)
    }
  }, [text])

  const handlePlay = () => {
    if (playing) {
      audioRef.current?.pause()
      if ('speechSynthesis' in window) speechSynthesis.cancel()
      setPlaying(false)
      return
    }
    doPlay()
  }

  const handleStop = () => {
    audioRef.current?.pause()
    if ('speechSynthesis' in window) speechSynthesis.cancel()
    setPlaying(false)
  }

  return (
    <>
      <button
        onClick={playing ? handleStop : handlePlay}
        disabled={loading}
        className={`inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium transition-all ${
          loading
            ? 'bg-white/5 text-white/40 border border-white/5'
            : playing
              ? 'bg-purple-400/20 text-purple-300 border border-purple-400/30 animate-pulse'
              : 'bg-white/10 text-white/60 hover:bg-white/20 hover:text-white/80 border border-white/10'
        }`}
      >
        {loading ? <span className="w-3.5 h-3.5 border-2 border-white/30 border-t-white rounded-full animate-spin" /> : playing ? <VolumeX className="w-3.5 h-3.5" /> : <Volume2 className="w-3.5 h-3.5" />}
        {loading ? '合成中...' : playing ? '停止朗读' : '语音朗读'}
      </button>
      <audio ref={audioRef} onEnded={() => setPlaying(false)} className="hidden" />
    </>
  )
}
