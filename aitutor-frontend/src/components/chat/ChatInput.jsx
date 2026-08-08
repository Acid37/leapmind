import React, { useState } from 'react';
import { createOneShotRecognition } from '@/features/chat/pptSpeech.js';
import { state } from '@/features/chat/pptState.js';
import { stopVoiceDetection, startVoiceDetection } from '@/features/chat/pptVoiceControl.js';
import { requestMicrophonePermission } from '@/features/chat/pptPermissions.js';

const ChatInput = ({ onSendMessage, setIsTeacherListening }) => {
    const [inputValue, setInputValue] = useState('');
    const [isVoiceListening, setIsVoiceListening] = useState(false);

    const handleSubmit = async (e) => {
        e.preventDefault();
        const text = inputValue.trim();
        if (!text) return;
        onSendMessage(text);
        setInputValue('');
    };

    const handleVoiceInput = async () => {
        if (isVoiceListening) return;
        const input = document.getElementById('chat-input');
        const voiceBtn = document.getElementById('voice-input-btn');
        try { await requestMicrophonePermission(); } catch {}

        const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
        if (!SpeechRecognition) {
            if (input) input.placeholder = '当前浏览器不支持语音识别';
            return;
        }

        setIsVoiceListening(true);
        setIsTeacherListening(true);
        if (voiceBtn) voiceBtn.classList.add('is-listening');
        if (input) {
            input.placeholder = '正在聆听...';
            input.disabled = true;
        }

        const sr = createOneShotRecognition('zh-CN');
        if (!sr) {
            if (input) {
                input.placeholder = '语音识别初始化失败';
                input.disabled = false;
            }
            setIsVoiceListening(false);
            setIsTeacherListening(false);
            return;
        }

        let finalText = '';
        let interimText = '';

        sr.onstart = () => {
            if (input) input.placeholder = '正在聆听...';
        };

        sr.onresult = (event) => {
            finalText = '';
            interimText = '';
            for (let i = event.resultIndex; i < event.results.length; i += 1) {
                const transcript = event.results[i][0].transcript || '';
                if (!transcript.trim()) continue;
                if (event.results[i].isFinal) finalText += transcript;
                else interimText += transcript;
            }
            // 实时展示中间结果
            if (interimText && input) setInputValue(interimText);
        };

        sr.onerror = () => {
            if (input) {
                input.placeholder = '语音识别出错';
                input.disabled = false;
            }
            if (voiceBtn) voiceBtn.classList.remove('is-listening');
            setIsVoiceListening(false);
            setIsTeacherListening(false);
        };

        sr.onend = async () => {
            const text = (finalText || interimText || '').trim();
            if (text) {
                try { console.debug('[voice->chat] text:', text); } catch {}
                onSendMessage(text);
                setInputValue('');
            }
            if (input) {
                input.placeholder = '输入您的问题...';
                input.disabled = false;
                input.focus();
            }
            if (voiceBtn) voiceBtn.classList.remove('is-listening');
            setIsVoiceListening(false);
            setIsTeacherListening(false);
            // 结束一次性识别后，如还在讲课且允许持续监听，则恢复持续识别
            if (state.isPlaying && !state.isInterrupted && state.enableVoiceDetectionFlag) {
                try { startVoiceDetection(); } catch {}
            }
        };

        try {
            // 暂停持续语音检测，避免与一次性识别冲突
            try { stopVoiceDetection(); } catch {}
            sr.start();
        } catch (_) {
            if (input) {
                input.placeholder = '无法启动语音识别';
                input.disabled = false;
            }
            if (voiceBtn) voiceBtn.classList.remove('is-listening');
            setIsVoiceListening(false);
            setIsTeacherListening(false);
        }
    };

    return (
        <div className="flex items-center space-x-3">
            <button type="button" id="voice-input-btn" onClick={handleVoiceInput} disabled={isVoiceListening} className="interactive-btn flex-shrink-0 w-12 h-12 bg-white text-slate-500 hover:text-blue-600 transition-all duration-300 rounded-full flex items-center justify-center shadow-md">
                <svg id="voice-icon" className="w-6 h-6 transition-all" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M19 11a7 7 0 01-7 7m0 0a7 7 0 01-7-7m7 7v4m0 0H8m4 0h4m-4-8a3 3 0 01-3-3V5a3 3 0 116 0v6a3 3 0 01-3 3z"></path></svg>
            </button>
            <form onSubmit={handleSubmit} className="relative flex-1 flex items-center">
                <input id="chat-input" type="search" placeholder="输入您的问题..." value={inputValue} onChange={(e) => setInputValue(e.target.value)} onFocus={() => setIsTeacherListening(true)} onBlur={() => setIsTeacherListening(false)} className="w-full p-3 pl-4 pr-12 bg-white border-transparent rounded-full shadow-md focus:ring-2 focus:ring-blue-400 focus:border-transparent transition" />
                <button type="submit" className="absolute right-1 top-1/2 -translate-y-1/2 interactive-btn w-10 h-10 text-white bg-blue-500 rounded-full hover:bg-blue-600 transition-colors flex items-center justify-center">
                    <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M5 13l4 4L19 7"></path></svg>
                </button>
            </form>
        </div>
    );
};

export default ChatInput;