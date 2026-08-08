import React, { useEffect, useRef } from 'react';  
const ChatDialog = ({ messages, isVisible, onClose }) => {
    const scrollRef = useRef(null);

    // 打开面板时滚动到底部
    useEffect(() => {
        if (!isVisible) return;
        const el = scrollRef.current;
        if (el) el.scrollTop = el.scrollHeight;
    }, [isVisible]);

    // 消息更新时滚动到底部
    useEffect(() => {
        if (!isVisible) return;
        const el = scrollRef.current;
        if (el) el.scrollTop = el.scrollHeight;
    }, [messages, isVisible]);

    return (
        <div id="chat-dialog" className={`absolute bottom-full left-4 right-4 mb-3 h-[50vh] rounded-2xl flex flex-col transform transition-all duration-300 ${isVisible ? 'opacity-100 pointer-events-auto translate-y-0' : 'opacity-0 pointer-events-none translate-y-4'}`}>
            <div className="p-4 flex justify-between items-center flex-shrink-0">
                <h5 className="font-semibold text-slate-800">与老师对话中</h5>
                <button onClick={onClose} className="interactive-btn text-slate-600 hover:text-slate-900">
                    <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M6 18L18 6M6 6l12 12"></path></svg>
                </button>
            </div>
            <div ref={scrollRef} className="flex-1 px-4 pb-4 overflow-y-auto space-y-4">
                {messages.map((msg, index) => (
                    <div key={index} className={`flex items-start gap-3 ${msg.sender === 'user' ? 'justify-end' : 'justify-start'}`}>
                        {msg.sender === 'ai' && (
                            <div className="w-8 h-8 rounded-full bg-gradient-to-br from-indigo-600 to-indigo-700 flex-shrink-0 flex items-center justify-center text-white shadow-md ring-1 ring-indigo-300 ring-opacity-40">
                                <svg className="w-6 h-6" viewBox="0 0 24 24" fill="none" stroke="currentColor">
                                    <g strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                                        <rect x="9" y="7" width="6" height="8" rx="3" />
                                        <path d="M12 15v3" />
                                        <path d="M9 18h6" />
                                        <path d="M8 11v1a4 4 0 0 0 8 0v-1" />
                                        <polygon points="12,4 7,6.5 12,9 17,6.5" />
                                        <path d="M12 9v2" />
                                        <line x1="17" y1="6.5" x2="17" y2="10" />
                                        <circle cx="17" cy="11.5" r="1" />
                                    </g>
                                </svg>
                            </div>
                        )}
                        <div className={`${msg.sender === 'user' ? 'bg-gradient-to-br from-blue-500 to-blue-600 text-white rounded-br-sm' : 'bg-white text-slate-800 rounded-bl-sm border border-slate-200'} p-3 rounded-xl max-w-xs break-words shadow-md`}>
                            {msg.isTyping ? (<div className="flex items-center space-x-1.5"><div className="typing-dot"></div><div className="typing-dot"></div><div className="typing-dot"></div></div>) : <p>{msg.text}</p>}
                        </div>
                        {msg.sender === 'user' && (
                            <div className="w-8 h-8 rounded-full bg-gradient-to-br from-blue-500 to-blue-600 ring-2 ring-white flex-shrink-0 flex items-center justify-center text-white shadow-sm">
                                <svg className="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor">
                                    <g strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                                        <path d="M9 9a3 3 0 1 1 6 0c0 2-3 2-3 4" />
                                        <circle cx="12" cy="17" r="1" fill="currentColor" stroke="none" />
                                    </g>
                                </svg>
                            </div>
                        )}
                    </div>
                ))}
            </div>
        </div>
    );
};
export default ChatDialog;