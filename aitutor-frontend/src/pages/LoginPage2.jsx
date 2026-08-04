import React, { useState } from 'react'
import { login, register } from '../services/authService'
import { ApiError } from '../services/api'

const LoginPage2 = ({ onLoginSuccess }) => {
  const [mode, setMode] = useState('login') // 'login' 或 'register'
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')

  // 登录表单字段
  const [loginData, setLoginData] = useState({
    account: '',
    password: '',
  })

  // 注册表单字段
  const [registerData, setRegisterData] = useState({
    username: '',
    email: '',
    password: '',
    confirmPassword: '',
    realName: '',
    phone: '',
  })

  // 切换登录/注册模式
  const toggleMode = () => {
    setMode(mode === 'login' ? 'register' : 'login')
    setError('')
    setSuccess('')
  }

  // 处理登录
  const handleLogin = async (e) => {
    e.preventDefault()
    setError('')
    setSuccess('')
    
    // 验证输入
    if (!loginData.account || !loginData.password) {
      setError('请输入账号和密码')
      return
    }

    setIsSubmitting(true)
    
    try {
      // 调用登录 API
      const result = await login(loginData.account, loginData.password)
      
      console.log('登录成功:', result.user)
      
      // 登录成功，通知父组件或刷新页面
      if (onLoginSuccess) {
        onLoginSuccess(result.user)
      } else {
        // 如果没有回调函数，刷新页面
        window.location.reload()
      }
    } catch (err) {
      // 处理错误
      if (err instanceof ApiError) {
        switch (err.code) {
          case 401:
            setError('用户名或密码错误')
            break
          case 400:
            setError(err.message || '请求参数错误')
            break
          case 500:
            setError('服务器错误，请稍后重试')
            break
          default:
            setError(err.message || '登录失败，请稍后重试')
        }
      } else {
        setError(err?.message || '登录失败，请检查网络连接')
      }
      console.error('登录失败:', err)
    } finally {
      setIsSubmitting(false)
    }
  }

  // 处理注册
  const handleRegister = async (e) => {
    e.preventDefault()
    setError('')
    setSuccess('')
    
    // 验证输入
    if (!registerData.username || !registerData.email || !registerData.password) {
      setError('请填写用户名、邮箱和密码')
      return
    }

    // 验证邮箱格式
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
    if (!emailRegex.test(registerData.email)) {
      setError('请输入有效的邮箱地址')
      return
    }

    // 验证密码长度
    if (registerData.password.length < 6) {
      setError('密码长度至少为6位')
      return
    }

    // 验证密码确认
    if (registerData.password !== registerData.confirmPassword) {
      setError('两次输入的密码不一致')
      return
    }

    setIsSubmitting(true)
    
    try {
      // 调用注册 API
      const userData = {
        username: registerData.username,
        email: registerData.email,
        password: registerData.password,
      }

      // 添加可选字段
      if (registerData.realName) {
        userData.realName = registerData.realName
      }
      if (registerData.phone) {
        userData.phone = registerData.phone
      }

      const result = await register(userData)
      
      console.log('注册成功:', result)
      setSuccess('注册成功！请登录')
      
      // 2秒后自动切换到登录页面
      setTimeout(() => {
        setMode('login')
        setLoginData({ account: registerData.email, password: '' })
        setSuccess('')
      }, 2000)
      
    } catch (err) {
      // 处理错误
      console.error('注册失败详情:', err)
      
      if (err instanceof ApiError) {
        // 根据错误代码显示不同的错误信息
        switch (err.code) {
          case 400:
            // 400 错误通常是参数验证失败
            setError(err.message || '请求参数错误，请检查输入信息')
            break
          case 500:
            // 500 错误是服务器内部错误
            // 尝试显示更详细的错误信息
            const serverError = err.data?.message || err.message
            if (serverError && serverError !== 'Internal Server Error') {
              setError(`服务器错误: ${serverError}`)
            } else {
              setError('服务器内部错误，请稍后重试。如果问题持续，请联系管理员。')
            }
            break
          default:
            // 其他错误代码
            setError(err.message || `注册失败 (错误代码: ${err.code})`)
        }
      } else {
        // 非 ApiError 错误（通常是网络错误）
        setError(err?.message || '注册失败，请检查网络连接')
      }
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <div
      className="relative min-h-screen w-full overflow-hidden text-white"
      style={{
        backgroundImage:
          'linear-gradient(135deg, #861FCE 0%, #861FCE 16%, #731CCD 16%, #731CCD 32%, #6B1CCF 32%, #6B1CCF 48%, #631DCE 48%, #631DCE 64%, #5A1BCE 64%, #5A1BCE 80%, rgb(86,43,205) 80%, rgb(47,8,154) 100%)',
      }}
    >
      {/* 装饰性光晕 */}
      <div className="pointer-events-none absolute -top-32 -left-32 h-72 w-72 rounded-full bg-fuchsia-400/20 blur-3xl" />
      <div className="pointer-events-none absolute -bottom-32 -right-32 h-80 w-80 rounded-full bg-cyan-400/20 blur-3xl" />

      {/* 顶部品牌与返回 */}
      <header className="flex items-center justify-between px-8 py-6">
        <div className="flex items-center gap-3">
          <span className="text-sm font-semibold text-purple-200/80 tracking-wide">LeapMind</span>
        </div>
        <a
          href="/"
          className="rounded-full border border-white/20 bg-white/10 px-5 py-2 text-sm font-semibold text-white/90 backdrop-blur hover:bg-white/15 transition"
        >
          返回首页
        </a>
      </header>

      {/* 中心卡片 - 登录/注册 */}
      <div className="mx-auto flex max-w-6xl items-center justify-center px-6 pb-16">
        <div className="relative w-full max-w-xl rounded-3xl border border-purple-400/20 bg-gradient-to-b from-purple-900/40 to-purple-800/20 p-8 shadow-2xl backdrop-blur-xl">
          <div className="absolute -top-5 left-6 rounded-b-full bg-purple-500 px-4 py-6 text-lg font-bold shadow-lg">
            {mode === 'login' ? '登录' : '注册'}
          </div>

          <div className="mt-6 flex flex-col items-center">
            <h1 className="mb-2 text-3xl font-extrabold tracking-wide drop-shadow">
              {mode === 'login' ? '欢迎回来' : '创建账户'}
            </h1>
            <p className="mb-8 text-purple-200/80">
              {mode === 'login' ? '请使用账号登录以继续学习' : '填写信息开始您的学习之旅'}
            </p>

            {/* 登录表单 */}
            {mode === 'login' && (
              <form onSubmit={handleLogin} className="w-full space-y-5">
                <div>
                  <label htmlFor="login-account" className="mb-1 block text-sm font-semibold text-purple-200">
                    邮箱地址或用户名
                  </label>
                  <div className="relative">
                    <span className="pointer-events-none absolute left-4 top-1/2 -translate-y-1/2 text-purple-200/70">
                      <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor" className="h-5 w-5">
                        <path d="M1.5 8.67V18A3 3 0 0 0 4.5 21h15a3 3 0 0 0 3-3V8.67l-9.553 5.73a3 3 0 0 1-3.394 0L1.5 8.67Z" />
                        <path d="M22.5 6.944V6A3 3 0 0 0 19.5 3h-15A3 3 0 0 0 1.5 6v.944l9.947 5.965a1.5 1.5 0 0 0 1.606 0L22.5 6.944Z" />
                      </svg>
                    </span>
                    <input
                      id="login-account"
                      type="text"
                      value={loginData.account}
                      onChange={(e) => setLoginData({ ...loginData, account: e.target.value })}
                      placeholder="you@example.com"
                      className="w-full rounded-2xl border border-purple-400/30 bg-white/10 px-12 py-3 text-white placeholder-purple-200/50 outline-none backdrop-blur focus:border-purple-300 focus:ring-2 focus:ring-purple-300/50"
                    />
                  </div>
                </div>

                <div>
                  <div className="mb-1 flex items-baseline justify-between">
                    <label htmlFor="login-password" className="block text-sm font-semibold text-purple-200">
                      密码
                    </label>
                    <a href="#" className="text-sm text-purple-200/80 hover:text-white">忘记密码?</a>
                  </div>
                  <div className="relative">
                    <span className="pointer-events-none absolute left-4 top-1/2 -translate-y-1/2 text-purple-200/70">
                      <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor" className="h-5 w-5">
                        <path fillRule="evenodd" d="M12 1.5a5.25 5.25 0 0 0-5.25 5.25V9A3.75 3.75 0 0 0 3 12.75v4.5A3.75 3.75 0 0 0 6.75 21h10.5A3.75 3.75 0 0 0 21 17.25v-4.5A3.75 3.75 0 0 0 17.25 9V6.75A5.25 5.25 0 0 0 12 1.5Zm3.75 7.5V6.75a3.75 3.75 0 1 0-7.5 0V9h7.5Z" clipRule="evenodd" />
                      </svg>
                    </span>
                    <input
                      id="login-password"
                      type="password"
                      value={loginData.password}
                      onChange={(e) => setLoginData({ ...loginData, password: e.target.value })}
                      placeholder="请输入您的密码"
                      className="w-full rounded-2xl border border-purple-400/30 bg-white/10 px-12 py-3 text-white placeholder-purple-200/50 outline-none backdrop-blur focus:border-purple-300 focus:ring-2 focus:ring-purple-300/50"
                    />
                  </div>
                </div>

                {error && (
                  <div className="rounded-xl border border-red-400/30 bg-red-500/10 px-4 py-2 text-center text-sm text-red-200">
                    {error}
                  </div>
                )}

                {success && (
                  <div className="rounded-xl border border-green-400/30 bg-green-500/10 px-4 py-2 text-center text-sm text-green-200">
                    {success}
                  </div>
                )}

                <button
                  type="submit"
                  disabled={isSubmitting}
                  className="group relative flex w-full items-center justify-center gap-2 overflow-hidden rounded-full bg-gradient-to-r from-[#A286FF] via-[#896BFF] to-[#638AFF] px-6 py-3 text-lg font-bold text-white shadow-lg transition-all hover:shadow-purple-500/40 disabled:opacity-60"
                >
                  <span className="absolute inset-0 -z-10 bg-white/10 opacity-0 transition-opacity group-hover:opacity-100" />
                  {isSubmitting ? '登 录中...' : '登 录'}
                </button>

                <div className="mt-2 text-center text-sm text-purple-200/80">
                  没有账号？
                  <button type="button" onClick={toggleMode} className="ml-1 font-semibold text-white underline-offset-4 hover:underline">
                    立即注册
                  </button>
                </div>
              </form>
            )}

            {/* 注册表单 */}
            {mode === 'register' && (
              <form onSubmit={handleRegister} className="w-full space-y-4">
                <div>
                  <label htmlFor="register-username" className="mb-1 block text-sm font-semibold text-purple-200">
                    用户名 <span className="text-red-400">*</span>
                  </label>
                  <div className="relative">
                    <span className="pointer-events-none absolute left-4 top-1/2 -translate-y-1/2 text-purple-200/70">
                      <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor" className="h-5 w-5">
                        <path fillRule="evenodd" d="M7.5 6a4.5 4.5 0 1 1 9 0 4.5 4.5 0 0 1-9 0ZM3.751 20.105a8.25 8.25 0 0 1 16.498 0 .75.75 0 0 1-.437.695A18.683 18.683 0 0 1 12 22.5c-2.786 0-5.433-.608-7.812-1.7a.75.75 0 0 1-.437-.695Z" clipRule="evenodd" />
                      </svg>
                    </span>
                    <input
                      id="register-username"
                      type="text"
                      value={registerData.username}
                      onChange={(e) => setRegisterData({ ...registerData, username: e.target.value })}
                      placeholder="设置您的用户名"
                      className="w-full rounded-2xl border border-purple-400/30 bg-white/10 px-12 py-3 text-white placeholder-purple-200/50 outline-none backdrop-blur focus:border-purple-300 focus:ring-2 focus:ring-purple-300/50"
                    />
                  </div>
                </div>

                <div>
                  <label htmlFor="register-email" className="mb-1 block text-sm font-semibold text-purple-200">
                    邮箱地址 <span className="text-red-400">*</span>
                  </label>
                  <div className="relative">
                    <span className="pointer-events-none absolute left-4 top-1/2 -translate-y-1/2 text-purple-200/70">
                      <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor" className="h-5 w-5">
                        <path d="M1.5 8.67V18A3 3 0 0 0 4.5 21h15a3 3 0 0 0 3-3V8.67l-9.553 5.73a3 3 0 0 1-3.394 0L1.5 8.67Z" />
                        <path d="M22.5 6.944V6A3 3 0 0 0 19.5 3h-15A3 3 0 0 0 1.5 6v.944l9.947 5.965a1.5 1.5 0 0 0 1.606 0L22.5 6.944Z" />
                      </svg>
                    </span>
                    <input
                      id="register-email"
                      type="email"
                      value={registerData.email}
                      onChange={(e) => setRegisterData({ ...registerData, email: e.target.value })}
                      placeholder="you@example.com"
                      className="w-full rounded-2xl border border-purple-400/30 bg-white/10 px-12 py-3 text-white placeholder-purple-200/50 outline-none backdrop-blur focus:border-purple-300 focus:ring-2 focus:ring-purple-300/50"
                    />
                  </div>
                </div>

                <div>
                  <label htmlFor="register-password" className="mb-1 block text-sm font-semibold text-purple-200">
                    密码 <span className="text-red-400">*</span>
                  </label>
                  <div className="relative">
                    <span className="pointer-events-none absolute left-4 top-1/2 -translate-y-1/2 text-purple-200/70">
                      <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor" className="h-5 w-5">
                        <path fillRule="evenodd" d="M12 1.5a5.25 5.25 0 0 0-5.25 5.25V9A3.75 3.75 0 0 0 3 12.75v4.5A3.75 3.75 0 0 0 6.75 21h10.5A3.75 3.75 0 0 0 21 17.25v-4.5A3.75 3.75 0 0 0 17.25 9V6.75A5.25 5.25 0 0 0 12 1.5Zm3.75 7.5V6.75a3.75 3.75 0 1 0-7.5 0V9h7.5Z" clipRule="evenodd" />
                      </svg>
                    </span>
                    <input
                      id="register-password"
                      type="password"
                      value={registerData.password}
                      onChange={(e) => setRegisterData({ ...registerData, password: e.target.value })}
                      placeholder="至少6位密码"
                      className="w-full rounded-2xl border border-purple-400/30 bg-white/10 px-12 py-3 text-white placeholder-purple-200/50 outline-none backdrop-blur focus:border-purple-300 focus:ring-2 focus:ring-purple-300/50"
                    />
                  </div>
                </div>

                <div>
                  <label htmlFor="register-confirm-password" className="mb-1 block text-sm font-semibold text-purple-200">
                    确认密码 <span className="text-red-400">*</span>
                  </label>
                  <div className="relative">
                    <span className="pointer-events-none absolute left-4 top-1/2 -translate-y-1/2 text-purple-200/70">
                      <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor" className="h-5 w-5">
                        <path fillRule="evenodd" d="M12 1.5a5.25 5.25 0 0 0-5.25 5.25V9A3.75 3.75 0 0 0 3 12.75v4.5A3.75 3.75 0 0 0 6.75 21h10.5A3.75 3.75 0 0 0 21 17.25v-4.5A3.75 3.75 0 0 0 17.25 9V6.75A5.25 5.25 0 0 0 12 1.5Zm3.75 7.5V6.75a3.75 3.75 0 1 0-7.5 0V9h7.5Z" clipRule="evenodd" />
                      </svg>
                    </span>
                    <input
                      id="register-confirm-password"
                      type="password"
                      value={registerData.confirmPassword}
                      onChange={(e) => setRegisterData({ ...registerData, confirmPassword: e.target.value })}
                      placeholder="再次输入密码"
                      className="w-full rounded-2xl border border-purple-400/30 bg-white/10 px-12 py-3 text-white placeholder-purple-200/50 outline-none backdrop-blur focus:border-purple-300 focus:ring-2 focus:ring-purple-300/50"
                    />
                  </div>
                </div>

                <div>
                  <label htmlFor="register-realname" className="mb-1 block text-sm font-semibold text-purple-200">
                    真实姓名 <span className="text-purple-200/50 text-xs">(可选)</span>
                  </label>
                  <div className="relative">
                    <span className="pointer-events-none absolute left-4 top-1/2 -translate-y-1/2 text-purple-200/70">
                      <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor" className="h-5 w-5">
                        <path fillRule="evenodd" d="M7.5 6a4.5 4.5 0 1 1 9 0 4.5 4.5 0 0 1-9 0ZM3.751 20.105a8.25 8.25 0 0 1 16.498 0 .75.75 0 0 1-.437.695A18.683 18.683 0 0 1 12 22.5c-2.786 0-5.433-.608-7.812-1.7a.75.75 0 0 1-.437-.695Z" clipRule="evenodd" />
                      </svg>
                    </span>
                    <input
                      id="register-realname"
                      type="text"
                      value={registerData.realName}
                      onChange={(e) => setRegisterData({ ...registerData, realName: e.target.value })}
                      placeholder="您的真实姓名"
                      className="w-full rounded-2xl border border-purple-400/30 bg-white/10 px-12 py-3 text-white placeholder-purple-200/50 outline-none backdrop-blur focus:border-purple-300 focus:ring-2 focus:ring-purple-300/50"
                    />
                  </div>
                </div>

                <div>
                  <label htmlFor="register-phone" className="mb-1 block text-sm font-semibold text-purple-200">
                    手机号码 <span className="text-purple-200/50 text-xs">(可选)</span>
                  </label>
                  <div className="relative">
                    <span className="pointer-events-none absolute left-4 top-1/2 -translate-y-1/2 text-purple-200/70">
                      <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor" className="h-5 w-5">
                        <path fillRule="evenodd" d="M1.5 4.5a3 3 0 0 1 3-3h1.372c.86 0 1.61.586 1.819 1.42l1.105 4.423a1.875 1.875 0 0 1-.694 1.955l-1.293.97c-.135.101-.164.249-.126.352a11.285 11.285 0 0 0 6.697 6.697c.103.038.25.009.352-.126l.97-1.293a1.875 1.875 0 0 1 1.955-.694l4.423 1.105c.834.209 1.42.959 1.42 1.82V19.5a3 3 0 0 1-3 3h-2.25C8.552 22.5 1.5 15.448 1.5 6.75V4.5Z" clipRule="evenodd" />
                      </svg>
                    </span>
                    <input
                      id="register-phone"
                      type="tel"
                      value={registerData.phone}
                      onChange={(e) => setRegisterData({ ...registerData, phone: e.target.value })}
                      placeholder="138xxxx8888"
                      className="w-full rounded-2xl border border-purple-400/30 bg-white/10 px-12 py-3 text-white placeholder-purple-200/50 outline-none backdrop-blur focus:border-purple-300 focus:ring-2 focus:ring-purple-300/50"
                    />
                  </div>
                </div>

                {error && (
                  <div className="rounded-xl border border-red-400/30 bg-red-500/10 px-4 py-2 text-center text-sm text-red-200">
                    {error}
                  </div>
                )}

                {success && (
                  <div className="rounded-xl border border-green-400/30 bg-green-500/10 px-4 py-2 text-center text-sm text-green-200">
                    {success}
                  </div>
                )}

                <button
                  type="submit"
                  disabled={isSubmitting}
                  className="group relative flex w-full items-center justify-center gap-2 overflow-hidden rounded-full bg-gradient-to-r from-[#A286FF] via-[#896BFF] to-[#638AFF] px-6 py-3 text-lg font-bold text-white shadow-lg transition-all hover:shadow-purple-500/40 disabled:opacity-60"
                >
                  <span className="absolute inset-0 -z-10 bg-white/10 opacity-0 transition-opacity group-hover:opacity-100" />
                  {isSubmitting ? '注 册中...' : '注 册'}
                </button>

                <div className="mt-2 text-center text-sm text-purple-200/80">
                  已有账号？
                  <button type="button" onClick={toggleMode} className="ml-1 font-semibold text-white underline-offset-4 hover:underline">
                    立即登录
                  </button>
                </div>
              </form>
            )}
          </div>
        </div>
      </div>
    </div>
  )
}

export default LoginPage2


