import React from 'react';

/**
 * 流式文本打字机效果
 *
 * 逐字展示内容 + 末尾闪烁光标
 * 用于 AI 回复正在生成的实时展示
 *
 * @param {{ text: string, isActive?: boolean, speed?: number }} props
 */
const StreamingText = ({ text = '', isActive = false }) => {
  return (
    <span className="streaming-text">
      {text}
      {isActive && (
        <span
          className="inline-block w-0.5 h-[1em] bg-indigo-500 ml-0.5 align-middle animate-pulse"
          aria-hidden="true"
        />
      )}
    </span>
  );
};

export default StreamingText;
