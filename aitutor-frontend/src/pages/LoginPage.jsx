import React, { useState } from 'react';
import { login } from '../services/authService';
import { ApiError } from '../services/api';

const LoginPage = ({ onLoginSuccess }) => {
  const [account, setAccount] = useState('');
  const [password, setPassword] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState('');

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    
    // 验证输入
    if (!account || !password) {
      setError('请输入账号和密码');
      return;
    }

    setIsSubmitting(true);
    
    try {
      // 调用登录 API
      const result = await login(account, password);
      
      console.log('登录成功:', result.user);
      
      // 登录成功，通知父组件或刷新页面
      if (onLoginSuccess) {
        onLoginSuccess(result.user);
      } else {
        // 如果没有回调函数，刷新页面
        window.location.reload();
      }
    } catch (err) {
      // 处理错误
      if (err instanceof ApiError) {
        switch (err.code) {
          case 401:
            setError('用户名或密码错误');
            break;
          case 400:
            setError(err.message || '请求参数错误');
            break;
          case 500:
            setError('服务器错误，请稍后重试');
            break;
          default:
            setError(err.message || '登录失败，请稍后重试');
        }
      } else {
        setError(err?.message || '登录失败，请检查网络连接');
      }
      console.error('登录失败:', err);
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div
      className="relative w-full h-full min-h-screen flex items-center justify-center bg-cover bg-center overflow-x-hidden"
      style={{
        backgroundImage: "url('./login/1.jpg')",
      }}
    >
      {/* 背景遮罩 */}
      <div className="absolute inset-0 bg-black opacity-20"></div>

      {/* 登录卡片 */}
      <div className="relative bg-white p-8 rounded-lg shadow-2xl w-full max-w-md m-4 translate-x-[85%] transition-transform duration-300">
        <div className="w-full flex flex-col items-center">
          {/* 标题 */}
          <h1 className="text-4xl font-bold text-blue-800 mb-6">登录</h1>

          {/* 表单 */}
          <form onSubmit={handleSubmit} className="w-full space-y-4">
            {/* 邮箱/账号 */}
          <div>
              <label htmlFor="email" className="block text-sm font-medium text-gray-700 mb-1">
                邮箱地址
              </label>
              <div className="relative">
                <span className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-gray-400">
                  {/* 邮箱图标 */}
                  <svg
                    xmlns="http://www.w3.org/2000/svg"
                    fill="none"
                    viewBox="0 0 24 24"
                    strokeWidth="2"
                    stroke="currentColor"
                    className="w-5 h-5"
                  >
                    <path
                      strokeLinecap="round"
                      strokeLinejoin="round"
                      d="M21.75 6.75v10.5a2.25 2.25 0 0 1-2.25 2.25h-15a2.25 2.25 0 0 1-2.25-2.25V6.75m19.5 0A2.25 2.25 0 0 0 19.5 4.5h-15a2.25 2.25 0 0 0-2.25 2.25m19.5 0v.243a2.25 2.25 0 0 1-1.07 1.916l-7.5 4.615a2.25 2.25 0 0 1-2.36 0L3.32 8.91A2.25 2.25 0 0 1 2.25 6.993v-.243"
                    />
                  </svg>
                </span>
            <input
                  type="email"
                  id="email"
              value={account}
              onChange={(e) => setAccount(e.target.value)}
                  placeholder="you@example.com"
                  className="w-full pl-10 pr-4 py-3 rounded-md border border-gray-300 focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
              </div>
          </div>

            {/* 密码 */}
          <div>
              <div className="flex justify-between items-baseline">
                <label htmlFor="password" className="block text-sm font-medium text-gray-700 mb-1">
                  密码
                </label>
                <a href="#" className="text-sm text-blue-600 hover:underline">
                  忘记密码?
                </a>
              </div>
              <div className="relative">
                <span className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-gray-400">
                  {/* 锁图标 */}
                  <svg
                    xmlns="http://www.w3.org/2000/svg"
                    fill="none"
                    viewBox="0 0 24 24"
                    strokeWidth="2"
                    stroke="currentColor"
                    className="w-5 h-5"
                  >
                    <path
                      strokeLinecap="round"
                      strokeLinejoin="round"
                      d="M16.5 10.5V6.75a4.5 4.5 0 0 0-9 0v3.75m-.75 11.25h10.5a2.25 2.25 0 0 0 2.25-2.25v-6.75a2.25 2.25 0 0 0-2.25-2.25H6.75a2.25 2.25 0 0 0-2.25 2.25v6.75a2.25 2.25 0 0 0 2.25 2.25Z"
                    />
                  </svg>
                </span>
            <input
              type="password"
                  id="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
                  placeholder="请输入您的密码"
                  className="w-full pl-10 pr-4 py-3 rounded-md border border-gray-300 focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
              </div>
          </div>

            {/* 错误提示 */}
          {error && (
              <div className="text-sm text-red-500 text-center" role="alert">
              {error}
            </div>
          )}

            {/* 登录按钮 */}
            <div>
          <button
            type="submit"
            disabled={isSubmitting}
                className="w-full bg-blue-600 text-white font-bold py-3 px-6 rounded-md hover:bg-blue-700 transition-colors duration-300 mt-4 disabled:opacity-50"
          >
                {isSubmitting ? '登 录中...' : '登 录'}
          </button>
            </div>
        </form>

          {/* 注册链接 */}
          <p className="mt-6 text-center text-gray-600">
            还没有账户?{' '}
            <a href="#" className="text-blue-600 hover:underline font-medium">
              立即注册
            </a>
          </p>
        </div>
      </div>
    </div>
  );
};

export default LoginPage;


