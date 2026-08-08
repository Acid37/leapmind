import React, { useState, useRef, useEffect } from 'react';
import { Send, Mic, Sparkles } from 'lucide-react';
/**
 * ChatPanel 占位组件（已升级：含消息列表 + 输入框）
 *
 * 接口契约对齐 M7 ChatPanel：
 *   sceneType  : 'teaching'
 *   context    : { lectureId, slide }
 *   userId     : number
 *
 * 联调时替换为：
 *   import ChatPanel from '../chat/ChatPanel';
 *   <ChatPanel sceneType="teaching" context={{ lectureId, slide }} userId={userId} />
 *
 * 当前 mock：显示欢迎消息 + 几条随 slide 变化的智能问答，
 * 输入框可发送本地消息（无后端）以便联调时验证 UI。
 */
const MOCK_RESPONSES = [
  '这是一个好问题！可以试着把这个定理用面积法重新画一遍来理解。',
  '推荐查看讲义的例题 1，掌握直接套用公式的思路。',
  '注意斜边 c 一定是最长边，不要和直角边搞反。',
  '可以尝试做课后练习来巩固这个知识点。',
  '学习时记得回顾前面的定义，再看例题。',
];

const ChatPanelPlaceholder = ({ sceneType, context, userId }) => {
  const slide = context?.slide || 1;
  const [messages, setMessages] = useState(() => [
    { id: 'sys', sender: 'ai', text: '👋 你好！我是你的 AI 老师，有任何问题都可以随时问我。' },
    { id: 'slide-tip', sender: 'ai', text: `💡 当前是第 ${slide} 页，有疑问就问吧。` },
  ]);
  const [input, setInput] = useState('');
  const [isTyping, setIsTyping] = useState(false);
  const scrollRef = useRef(null);

  // slide 变化时插入一条提示：仅在最后一条不是同一 slide 的 tip 时才追加
  useEffect(() => {
    setMessages(prev => {
      const last = prev[prev.length - 1];
      if (last && last.tipFor === slide) return prev;
      return [...prev, { id: `tip-${Date.now()}-${slide}`, sender: 'ai', text: `📖 已切换到第 ${slide} 页，需要讲解哪个点？`, tipFor: slide }];
    });
  }, [slide]);

  // 消息更新后自动滚到底
  useEffect(() => {
    if (scrollRef.current) scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
  }, [messages, isTyping]);

  const handleSend = (e) => {
    e?.preventDefault();
    const text = input.trim();
    if (!text) return;
    setMessages(prev => [...prev, { id: `u-${Date.now()}`, sender: 'user', text }]);
    setInput('');
    setIsTyping(true);
    setTimeout(() => {
      const reply = MOCK_RESPONSES[Math.floor(Math.random() * MOCK_RESPONSES.length)];
      setMessages(prev => [...prev, { id: `a-${Date.now()}`, sender: 'ai', text: reply }]);
      setIsTyping(false);
    }, 800);
  };

  return (
    <div className="flex flex-col w-full h-full bg-white/10 backdrop-blur-sm rounded-xl border border-white/20 overflow-hidden">
      {/* 消息列表 */}
      <div ref={scrollRef} className="flex-1 overflow-y-auto p-3 space-y-2.5">
        {messages.map(m => (
          <div key={m.id} className={`flex ${m.sender === 'user' ? 'justify-end' : 'justify-start'}`}>
            <div
              className={`max-w-[85%] px-3 py-2 rounded-2xl text-xs leading-relaxed ${
                m.sender === 'user'
                  ? 'bg-purple-600 text-white rounded-br-sm'
                  : 'bg-white/95 text-slate-700 rounded-bl-sm shadow-sm'
              }`}
            >
              {m.text}
            </div>
          </div>
        ))}
        {isTyping && (
          <div className="flex justify-start">
            <div className="bg-white/95 text-slate-500 px-3 py-2 rounded-2xl rounded-bl-sm text-xs flex items-center gap-1">
              <Sparkles className="w-3 h-3 animate-pulse" />
              老师正在思考…
            </div>
          </div>
        )}
      </div>
      {/* 输入区 */}
      <form onSubmit={handleSend} className="flex-shrink-0 p-2 border-t border-white/10 bg-white/5 flex items-center gap-1.5">
        <button
          type="button"
          className="p-2 text-white/60 hover:text-white hover:bg-white/10 rounded-lg transition-colors"
          title="语音输入（待后端联调）"
        >
          <Mic className="w-4 h-4" />
        </button>
        <input
          type="text"
          value={input}
          onChange={(e) => setInput(e.target.value)}
          placeholder="向 AI 老师提问…"
          className="flex-1 min-w-0 bg-white/15 text-white placeholder-white/40 text-sm px-3 py-1.5 rounded-lg outline-none focus:bg-white/25 border border-white/10 focus:border-white/30"
        />
        <button
          type="submit"
          disabled={!input.trim()}
          className="p-2 bg-purple-600 text-white rounded-lg hover:bg-purple-700 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
        >
          <Send className="w-4 h-4" />
        </button>
      </form>
    </div>
  );
};

export default ChatPanelPlaceholder;
