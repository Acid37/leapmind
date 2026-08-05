import React, { useEffect, useRef } from 'react';

const ChatDialog = ({ messages, isVisible, onClose, showContinueOptions, onContinueLecture, onContinueAsk }) => {
    const scrollRef = useRef(null);

    useEffect(() => {
        if (!isVisible) return;
        const el = scrollRef.current;
        if (el) el.scrollTop = el.scrollHeight;
    }, [isVisible]);

    useEffect(() => {
        if (!isVisible) return;
        const el = scrollRef.current;
        if (el) el.scrollTop = el.scrollHeight;
    }, [messages, isVisible]);

    return (
        <div id="chat-dialog" className={`absolute bottom-full left-2 right-2 mb-3 h-[50vh] rounded-xl flex flex-col transform transition-all duration-300 bg-white/95 backdrop-blur-sm shadow-xl border border-white/20 ${isVisible ? 'opacity-100 pointer-events-auto translate-y-0' : 'opacity-0 pointer-events-none translate-y-4'}`}>
            <div className="p-3 flex justify-between items-center flex-shrink-0 border-b border-slate-100">
                <h5 className="font-semibold text-slate-800 text-sm">与老师对话</h5>
                <button onClick={onClose} className="interactive-btn text-slate-400 hover:text-slate-700">
                    <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M6 18L18 6M6 6l12 12"></path></svg>
                </button>
            </div>
            <div ref={scrollRef} className="flex-1 px-3 pb-3 overflow-y-auto space-y-3">
                {messages.map((msg, index) => {
                    // "继续" 选项按钮
                    if (msg.isOptions) {
                        const canResume = msg.wasNarrating !== false;
                        return (
                            <div key={index} className="flex justify-start">
                                <div className="bg-slate-100 p-2.5 rounded-xl max-w-[85%] space-y-2 shadow-sm">
                                    <p className="text-xs text-slate-600">需要我继续吗？</p>
                                    <div className="flex gap-2">
                                        {canResume && (
                                            <button
                                                onClick={onContinueLecture}
                                                className="flex-1 px-3 py-1.5 bg-gradient-to-r from-purple-500 to-indigo-500 text-white text-xs rounded-lg hover:from-purple-600 hover:to-indigo-600 transition-colors font-medium"
                                            >
                                                📖 继续讲课
                                            </button>
                                        )}
                                        <button
                                            onClick={onContinueAsk}
                                            className="flex-1 px-3 py-1.5 bg-white text-slate-700 text-xs rounded-lg border border-slate-200 hover:bg-slate-50 transition-colors font-medium"
                                        >
                                            💬 继续提问
                                        </button>
                                    </div>
                                </div>
                            </div>
                        );
                    }

                    // 正常消息
                    return (
                        <div key={index} className={`flex items-start gap-2 ${msg.sender === 'user' ? 'justify-end' : 'justify-start'}`}>
                            {msg.sender === 'ai' && (
                                <div className="w-7 h-7 rounded-full bg-gradient-to-br from-indigo-600 to-indigo-700 flex-shrink-0 flex items-center justify-center text-white shadow-sm">
                                    <svg className="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor">
                                        <g strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                                            <rect x="9" y="7" width="6" height="8" rx="3" />
                                            <path d="M12 15v3" />
                                            <path d="M9 18h6" />
                                        </g>
                                    </svg>
                                </div>
                            )}
                            <div className={`${msg.sender === 'user' ? 'bg-gradient-to-br from-blue-500 to-blue-600 text-white rounded-br-sm' : 'bg-slate-100 text-slate-800 rounded-bl-sm'} p-2.5 rounded-xl max-w-[85%] break-words shadow-sm`}>
                                {msg.isTyping ? (
                                    <div className="flex items-center space-x-1">
                                        <div className="typing-dot"></div>
                                        <div className="typing-dot"></div>
                                        <div className="typing-dot"></div>
                                    </div>
                                ) : (
                                    <p className="text-xs leading-relaxed">{msg.text}</p>
                                )}
                            </div>
                            {msg.sender === 'user' && (
                                <div className="w-7 h-7 rounded-full bg-gradient-to-br from-blue-500 to-blue-600 flex-shrink-0 flex items-center justify-center text-white shadow-sm">
                                    <svg className="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor">
                                        <g strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                                            <path d="M9 9a3 3 0 1 1 6 0c0 2-3 2-3 4" />
                                            <circle cx="12" cy="17" r="1" fill="currentColor" stroke="none" />
                                        </g>
                                    </svg>
                                </div>
                            )}
                        </div>
                    );
                })}
            </div>
        </div>
    );
};

export default ChatDialog;
