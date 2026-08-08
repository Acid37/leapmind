import { defineConfig, loadEnv } from 'vite'
import { fileURLToPath, URL } from 'node:url'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const javaApi = env.JAVA_API_TARGET || 'http://localhost:8080'

  const javaProxy = {
    target: javaApi,
    changeOrigin: true,
    secure: false,
  }

  return {
    plugins: [react(), tailwindcss()],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url)),
      },
    },
    server: {
      proxy: {
        '/api/speech': javaProxy,
        '/api/auth': javaProxy,
        '/api/education': javaProxy,
        '/api/courses': javaProxy,
        '/api/voice-chat': javaProxy,
        '/api/virtual-teacher': javaProxy,
        '/api/conversation': javaProxy,
        '/api/ocr': javaProxy,
        '/api/explain': javaProxy,
        '/api/practice': javaProxy,
        '/api/wrong-questions': javaProxy,
        '/api/user-profile': javaProxy,
      },
    },
  }
})
