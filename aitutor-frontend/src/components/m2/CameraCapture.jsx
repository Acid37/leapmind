import { useState, useRef, useCallback } from 'react'
import { Camera, X } from 'lucide-react'

export default function CameraCapture({ onCapture }) {
  const videoRef = useRef(null)
  const [streaming, setStreaming] = useState(false)
  const streamRef = useRef(null)

  const startCamera = useCallback(async () => {
    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        video: { facingMode: 'environment' }
      })
      streamRef.current = stream
      if (videoRef.current) videoRef.current.srcObject = stream
      setStreaming(true)
    } catch (err) {
      console.error('摄像头访问失败:', err)
      alert('无法访问摄像头，请检查权限设置')
    }
  }, [])

  const capture = useCallback(() => {
    if (!videoRef.current) return
    const canvas = document.createElement('canvas')
    canvas.width = videoRef.current.videoWidth
    canvas.height = videoRef.current.videoHeight
    canvas.getContext('2d').drawImage(videoRef.current, 0, 0)
    canvas.toBlob(blob => {
      if (blob) onCapture(new File([blob], 'capture.jpg', { type: 'image/jpeg' }))
    }, 'image/jpeg', 0.9)
  }, [onCapture])

  const stopCamera = useCallback(() => {
    streamRef.current?.getTracks().forEach(t => t.stop())
    setStreaming(false)
  }, [])

  return (
    <div className="bg-white/10 backdrop-blur-md rounded-2xl overflow-hidden border border-white/10">
      {!streaming ? (
        <div className="p-8 text-center">
          <button
            onClick={startCamera}
            className="w-16 h-16 mx-auto mb-3 bg-gradient-to-br from-purple-400 to-blue-400 rounded-xl flex items-center justify-center shadow-lg hover:shadow-purple-500/30 transition-all hover:scale-105"
          >
            <Camera className="w-8 h-8 text-white" />
          </button>
          <p className="text-white font-medium">开启摄像头</p>
          <p className="text-xs text-purple-200/60 mt-1">允许浏览器访问摄像头权限</p>
        </div>
      ) : (
        <div>
          <div className="relative">
            <video ref={videoRef} autoPlay playsInline className="w-full max-h-72 object-cover" />
            <div className="absolute inset-0 border-2 border-purple-400/30 m-4 rounded-xl pointer-events-none" />
          </div>
          <div className="p-4 flex gap-3 justify-center bg-black/20">
            <button
              onClick={capture}
              className="px-6 py-2.5 bg-gradient-to-r from-purple-500 to-blue-500 text-white rounded-xl font-medium hover:shadow-lg hover:shadow-purple-500/30 transition-all"
            >
              拍照
            </button>
            <button
              onClick={stopCamera}
              className="px-6 py-2.5 bg-white/10 text-white/80 rounded-xl font-medium hover:bg-white/20 transition-all flex items-center gap-1.5"
            >
              <X className="w-4 h-4" /> 关闭
            </button>
          </div>
        </div>
      )}
    </div>
  )
}
