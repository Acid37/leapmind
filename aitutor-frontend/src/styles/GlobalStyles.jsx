
import React from "react";
const GlobalStyles = () => (
    <style>{`
        body { font-family: 'Inter', sans-serif; overflow: hidden; }
        .shadow-soft { box-shadow: 0 4px 6px -1px rgb(0 0 0 / 0.05), 0 2px 4px -2px rgb(0 0 0 / 0.05); }
        .shadow-soft-strong { box-shadow: 0 10px 15px -3px rgb(0 0 0 / 0.07), 0 4px 6px -4px rgb(0 0 0 / 0.07); }
        #chat-dialog { background-color: rgba(255, 255, 255, 0.6); backdrop-filter: blur(12px); -webkit-backdrop-filter: blur(12px); border: 1px solid rgba(255, 255, 255, 0.2); }
        @keyframes pulse { 0%, 100% { transform: scale(1); opacity: 0.1; } 50% { transform: scale(1.1); opacity: 0.2; } }
        .animate-pulse-soft { animation: pulse 2.5s cubic-bezier(0.4, 0, 0.6, 1) infinite; }
        #teacher-avatar-wrapper.is-listening .animate-pulse-soft { animation-duration: 1s; opacity: 0.3; }
        #teacher-avatar-wrapper.is-listening #teacher-avatar { transform: scale(1.05); }
        .interactive-btn { transition: transform 0.15s ease-out; }
        .interactive-btn:hover { transform: scale(1.05); }
        .interactive-btn:active { transform: scale(0.95); }
        input[type="search"]::-webkit-search-cancel-button { -webkit-appearance: none; }
        #subtitle-bar { transition: all 0.3s ease; overflow: hidden; }
        @keyframes slideUpFadeIn { from { opacity: 0; transform: translateY(100%); } to { opacity: 1; transform: translateY(0); } }
        .subtitle-line { animation: slideUpFadeIn 0.5s ease-out forwards; }
        @keyframes mic-pulse { 0% { box-shadow: 0 0 0 0 rgba(59, 130, 246, 0.7); } 70% { box-shadow: 0 0 0 10px rgba(59, 130, 246, 0); } 100% { box-shadow: 0 0 0 0 rgba(59, 130, 246, 0); }
        #voice-input-btn.is-listening { animation: mic-pulse 1.5s infinite; }
        @keyframes spin { to { transform: rotate(360deg); } }
        .is-processing { animation: spin 1s linear infinite; }
        @keyframes typing-dot { 0%, 80%, 100% { transform: scale(0); } 40% { transform: scale(1.0); } }
        .typing-dot { width: 8px; height: 8px; background-color: #94a3b8; border-radius: 50%; animation: typing-dot 1.4s infinite ease-in-out both; }
        .typing-dot:nth-child(1) { animation-delay: -0.32s; }
        .typing-dot:nth-child(2) { animation-delay: -0.16s; }
        @keyframes fadeIn { from { opacity: 0; transform: translateY(10px); } to { opacity: 1; transform: translateY(0); } }
        .animate-fadeIn { animation: fadeIn 0.3s ease-out forwards; }
    `}</style>
);

export default GlobalStyles;