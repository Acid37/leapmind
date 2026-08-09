import React from 'react';
import StreamingText from './StreamingText';

/**
 * 消息气泡组件
 *
 * 支持三种角色样式：
 *   - user:      蓝紫渐变，靠右
 *   - assistant: 白色卡片+左边框，靠左，带头像
 *   - system:    灰色提示条，居中
 *
 * 流式生成中的 assistant 消息使用 StreamingText 打字机效果
 *
 * @param {{ role: 'user'|'assistant'|'system', content: string, isStreaming?: boolean, error?: boolean }} props
 */
const MessageBubble = ({ role, content, isStreaming = false, error = false }) => {
  // ---- system 提示 -----
  if (role === 'system') {
    return (
      <div className="flex justify-center my-2">
        <span className="px-3 py-1 text-xs text-slate-500 bg-slate-100 rounded-full">
          {content}
        </span>
      </div>
    );
  }

  // ---- user 消息 -----
  if (role === 'user') {
    return (
      <div className="flex justify-end items-start gap-2 mb-3">
        <div className="max-w-[80%] px-4 py-2.5 rounded-2xl rounded-br-md bg-gradient-to-br from-blue-500 to-blue-600 text-white shadow-sm">
          <p className="text-sm leading-relaxed whitespace-pre-wrap break-words">{content}</p>
        </div>
      </div>
    );
  }

  // ---- assistant 消息 -----
  return (
    <div className="flex justify-start items-start gap-2 mb-3">
      {/* AI 头像 */}
      <div className="w-7 h-7 rounded-full bg-gradient-to-br from-indigo-500 to-indigo-600 flex-shrink-0 flex items-center justify-center shadow-sm ring-1 ring-indigo-200">
        <svg className="w-4 h-4 text-white" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
          <polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2" />
        </svg>
      </div>
      {/* 气泡 */}
      <div className={`max-w-[80%] px-4 py-2.5 rounded-2xl rounded-bl-md border shadow-sm ${
        error
          ? 'bg-red-50 border-red-200'
          : 'bg-white border-slate-200'
      }`}>
        {isStreaming ? (
          <StreamingText text={content} isActive={true} />
        ) : (
          <p className={`text-sm leading-relaxed whitespace-pre-wrap break-words ${
            error ? 'text-red-600' : 'text-slate-800'
          }`}>{content}</p>
        )}
      </div>
    </div>
  );
};

export default MessageBubble;
