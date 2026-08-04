import React from 'react';

/**
 * 打断按钮 — 生成中可见，点击立刻停止 AI
 *
 * @param {{ onClick: () => void, className?: string }} props
 */
const InterruptButton = ({ onClick, className = '' }) => {
  return (
    <button
      onClick={onClick}
      className={`flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium text-red-600 bg-red-50 border border-red-200 rounded-full hover:bg-red-100 transition-colors shadow-sm ${className}`}
      title="停止生成"
    >
      <svg className="w-3.5 h-3.5" viewBox="0 0 24 24" fill="currentColor">
        <rect x="6" y="6" width="12" height="12" rx="1" />
      </svg>
      停止生成
    </button>
  );
};

export default InterruptButton;
