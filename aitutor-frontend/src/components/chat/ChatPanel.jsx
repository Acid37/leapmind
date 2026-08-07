import React, { useState, useRef, useEffect } from 'react';
import { useChatSession } from '../../hooks/useChatSession';
import MessageBubble from './MessageBubble';
import InterruptButton from './InterruptButton';
import ContextIndicator from './ContextIndicator';
import { createOneShotRecognition } from '../../features/chat/pptSpeech';

/**
 * ChatPanel — 可嵌入各模块的共享对话组件
 *
 * 使用方式：
 *   <ChatPanel
 *     sceneType="doing_exercise"          // 必有
 *     context={{ questionId: 456 }}       // 场景上下文
 *     userId={1001}                       // 用户 ID
 *     visible={true}                      // 控制显示/隐藏
 *     onClose={() => setVisible(false)}   // 关闭回调
 *     title="AI 助手"                     // 可选标题
 *   />
 *
 * 嵌入场景（对齐文档 M7 → 4.2.1）：
 *   - M1 做题页:   sceneType="doing_exercise"  context={{ questionId }}
 *   - M2 讲题页:   sceneType="explaining"      context={{ wrongQuestionId }}
 *   - M4 讲课页:   sceneType="teaching"        context={{ lectureId, slide }}
 *   - M5 备课页:   sceneType="lesson_prep"     context={{ prepId }}
 *
 * @param {{
 *   sceneType: string,
 *   context: Object,
 *   userId: number,
 *   visible?: boolean,
 *   onClose?: () => void,
 *   title?: string,
 *   className?: string,
 * }} props
 */
const ChatPanel = ({
  sceneType = 'general_qa',
  context = {},
  userId,
  visible = true,
  onClose,
  title = 'AI 助手',
  className = '',
}) => {
  const { messages, isGenerating, sessionId, error, send, abort, retry, clear } = useChatSession({
    sceneType,
    context,
    userId,
    autoRestore: true,
  });

  const [inputValue, setInputValue] = useState('');
  const [isVoiceListening, setIsVoiceListening] = useState(false);
  const scrollRef = useRef(null);
  const inputRef = useRef(null);

  // 消息更新时自动滚到底部
  useEffect(() => {
    if (!visible) return;
    const el = scrollRef.current;
    if (el) {
      requestAnimationFrame(() => {
        el.scrollTop = el.scrollHeight;
      });
    }
  }, [messages, visible]);

  // 打开面板时聚焦输入框
  useEffect(() => {
    if (visible && inputRef.current) {
      inputRef.current.focus();
    }
  }, [visible]);

  // -------- 发送文本 --------
  const handleSend = (e) => {
    e?.preventDefault();
    const text = inputValue.trim();
    if (!text || isGenerating) return;
    send(text);
    setInputValue('');
  };

  // -------- 语音输入 --------
  const handleVoiceInput = async () => {
    if (isVoiceListening || isGenerating) return;

    // 检测浏览器支持（赋值到下划线变量避免 ESLint no-unused-vars）
    const _SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
    if (!_SpeechRecognition) {
      setInputValue('当前浏览器不支持语音识别');
      return;
    }

    setIsVoiceListening(true);
    const sr = createOneShotRecognition('zh-CN');
    if (!sr) {
      setIsVoiceListening(false);
      return;
    }

    // 累积所有 final 片段（中文语音常分多段返回）
    let finalText = '';
    let interimText = '';

    sr.onresult = (event) => {
      // 重新计算 interim（只取当前未定稿部分），final 持续累积
      interimText = '';
      for (let i = event.resultIndex; i < event.results.length; i++) {
        const transcript = event.results[i][0].transcript || '';
        if (event.results[i].isFinal) {
          finalText += transcript;
        } else {
          interimText += transcript;
        }
      }
      // 实时显示：已定稿 + 当前临时
      const display = finalText + interimText;
      if (display) setInputValue(display);
    };

    sr.onerror = () => {
      setIsVoiceListening(false);
      setInputValue('');
    };

    sr.onend = () => {
      const text = (finalText + interimText).trim();
      setIsVoiceListening(false);
      if (text) {
        send(text);
        setInputValue('');
      }
    };

    try { sr.start(); } catch { setIsVoiceListening(false); }
  };

  // -------- 图片上传（占位） --------
  const handleImageUpload = () => {
    // TODO-IMAGE: 接入拍照/图片上传 → OCR 识别 → send(recognizedText)
    // eslint-disable-next-line no-console
    console.log('[ChatPanel] image upload not yet implemented');
  };

  // ==================== 渲染 ====================

  if (!visible) return null;

  return (
    <div className={`flex flex-col h-full bg-gradient-to-b from-slate-50 to-white ${className}`}>
      {/* ===== 顶部标题栏 ===== */}
      <div className="flex-shrink-0 flex items-center justify-between px-4 py-3 border-b border-slate-100">
        <div className="flex items-center gap-2">
          <div className="w-8 h-8 rounded-full bg-gradient-to-br from-indigo-500 to-purple-600 flex items-center justify-center shadow-sm">
            <svg className="w-5 h-5 text-white" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2" />
            </svg>
          </div>
          <div>
            <h4 className="text-sm font-semibold text-slate-800">{title}</h4>
            {sessionId && (
              <p className="text-[10px] text-slate-400 font-mono truncate max-w-[120px]">
                {sessionId.slice(0, 12)}...
              </p>
            )}
          </div>
        </div>

        <div className="flex items-center gap-1">
          {/* 清空按钮 */}
          {messages.length > 0 && (
            <button
              onClick={clear}
              className="p-1.5 text-slate-400 hover:text-slate-600 rounded-lg hover:bg-slate-100 transition-colors"
              title="清空对话"
            >
              <svg className="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <polyline points="3 6 5 6 21 6" />
                <path d="M19 6v14a2 2 0 01-2 2H7a2 2 0 01-2-2V6m3 0V4a2 2 0 012-2h4a2 2 0 012 2v2" />
              </svg>
            </button>
          )}
          {/* 关闭按钮 */}
          {onClose && (
            <button
              onClick={onClose}
              className="p-1.5 text-slate-400 hover:text-slate-600 rounded-lg hover:bg-slate-100 transition-colors"
              title="关闭"
            >
              <svg className="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
              </svg>
            </button>
          )}
        </div>
      </div>

      {/* ===== 上下文提示条 ===== */}
      <div className="flex-shrink-0 px-4 py-2">
        <ContextIndicator sceneType={sceneType} context={context} />
      </div>

      {/* ===== 消息列表 ===== */}
      <div
        ref={scrollRef}
        className="flex-1 px-4 py-2 overflow-y-auto space-y-0.5"
      >
        {messages.length === 0 && (
          <div className="flex flex-col items-center justify-center h-full text-slate-400">
            <svg className="w-12 h-12 mb-3 opacity-30" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
              <path d="M21 15a2 2 0 01-2 2H7l-4 4V5a2 2 0 012-2h14a2 2 0 012 2z" />
            </svg>
            <p className="text-sm">有什么想问的？我随时都在</p>
          </div>
        )}
        {messages.map((msg, i) => (
          <MessageBubble
            key={i}
            role={msg.role}
            content={msg.content}
            isStreaming={msg.isStreaming}
            error={msg.error}
          />
        ))}
        {/* 错误 + 重试 */}
        {error && (
          <div className="flex justify-center my-2">
            <div className="flex items-center gap-2 px-3 py-2 text-xs text-red-600 bg-red-50 rounded-lg">
              <svg className="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <circle cx="12" cy="12" r="10" /><line x1="12" y1="8" x2="12" y2="12" /><line x1="12" y1="16" x2="12.01" y2="16" />
              </svg>
              <span>{error}</span>
              <button onClick={retry} className="font-medium underline hover:no-underline">重试</button>
            </div>
          </div>
        )}
      </div>

      {/* ===== 打断按钮 ===== */}
      {isGenerating && (
        <div className="flex-shrink-0 flex justify-center pb-1">
          <InterruptButton onClick={abort} />
        </div>
      )}

      {/* ===== 底部输入区 ===== */}
      <div className="flex-shrink-0 p-3 border-t border-slate-100">
        <form onSubmit={handleSend} className="flex items-end gap-2">
          {/* 图片上传按钮 */}
          <button
            type="button"
            onClick={handleImageUpload}
            className="flex-shrink-0 w-9 h-9 flex items-center justify-center text-slate-400 hover:text-indigo-500 bg-slate-100 hover:bg-indigo-50 rounded-xl transition-colors"
            title="上传图片提问"
          >
            <svg className="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <rect x="3" y="3" width="18" height="18" rx="2" ry="2" />
              <circle cx="8.5" cy="8.5" r="1.5" />
              <polyline points="21 15 16 10 5 21" />
            </svg>
          </button>

          {/* 语音输入按钮 */}
          <button
            type="button"
            onClick={handleVoiceInput}
            disabled={isGenerating}
            className={`flex-shrink-0 w-9 h-9 flex items-center justify-center rounded-xl transition-all ${
              isVoiceListening
                ? 'bg-red-500 text-white shadow-sm animate-pulse'
                : 'text-slate-400 hover:text-blue-500 bg-slate-100 hover:bg-blue-50'
            }`}
            title="语音输入"
          >
            <svg className="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M12 1a3 3 0 00-3 3v8a3 3 0 006 0V4a3 3 0 00-3-3z" />
              <path d="M19 10v2a7 7 0 01-14 0v-2" />
              <line x1="12" y1="19" x2="12" y2="23" />
              <line x1="8" y1="23" x2="16" y2="23" />
            </svg>
          </button>

          {/* 文本输入框 */}
          <textarea
            ref={inputRef}
            value={inputValue}
            onChange={(e) => setInputValue(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault();
                handleSend();
              }
            }}
            placeholder={isGenerating ? 'AI 正在回复...' : '输入你的问题...'}
            disabled={isGenerating}
            rows={1}
            className="flex-1 px-3 py-2 text-sm bg-slate-100 border border-transparent rounded-xl resize-none outline-none focus:border-indigo-300 focus:bg-white transition-colors placeholder:text-slate-400 disabled:opacity-50"
            style={{ minHeight: '36px', maxHeight: '100px' }}
            onInput={(e) => {
              e.target.style.height = 'auto';
              e.target.style.height = Math.min(e.target.scrollHeight, 100) + 'px';
            }}
          />

          {/* 发送按钮 */}
          <button
            type="submit"
            disabled={!inputValue.trim() || isGenerating}
            className="flex-shrink-0 w-9 h-9 flex items-center justify-center bg-indigo-500 text-white rounded-xl hover:bg-indigo-600 disabled:bg-slate-300 disabled:cursor-not-allowed transition-colors shadow-sm"
            title="发送"
          >
            <svg className="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <line x1="22" y1="2" x2="11" y2="13" />
              <polygon points="22 2 15 22 11 13 2 9 22 2" />
            </svg>
          </button>
        </form>
      </div>
    </div>
  );
};

export default ChatPanel;
