import React from 'react';

/**
 * 场景标签映射
 */
const SCENE_LABELS = {
  doing_exercise: '📝 正在做题中',
  explaining: '🔍 正在讲题：',
  teaching: '📖 正在讲解：',
  lesson_prep: '📋 正在备课：',
  general_qa: '💬 自由问答',
};

/**
 * 上下文提示条 — 展示当前场景和关联上下文
 *
 * 让用户知道 AI "知道"当前在干什么（如"当前正在讲解：勾股定理"）
 *
 * @param {{
 *   sceneType: string,
 *   context: Object,
 *   className?: string,
 * }} props
 */
const ContextIndicator = ({ sceneType, context = {}, className = '' }) => {
  const label = SCENE_LABELS[sceneType] || SCENE_LABELS.general_qa;

  // 从 context 中提取可读的额外信息
  const detail = buildContextDetail(sceneType, context);

  if (!sceneType || sceneType === 'general_qa') {
    return (
      <div className={`flex items-center gap-2 px-3 py-2 text-xs text-slate-500 bg-slate-50/80 rounded-lg ${className}`}>
        <span>{label}</span>
      </div>
    );
  }

  return (
    <div className={`flex items-center gap-2 px-3 py-2 text-xs bg-indigo-50/80 text-indigo-700 rounded-lg border border-indigo-100 ${className}`}>
      <span>{label}</span>
      {detail && (
        <span className="font-medium text-indigo-800 truncate max-w-[180px]">{detail}</span>
      )}
    </div>
  );
};

/**
 * 从 context 中提取可读提示
 */
function buildContextDetail(sceneType, context) {
  switch (sceneType) {
    case 'doing_exercise':
      return context.questionId ? `题目 #${context.questionId}` : '';
    case 'explaining':
      return context.wrongQuestionId ? `错题 #${context.wrongQuestionId}` : '';
    case 'teaching':
      return context.slide != null ? `第 ${context.slide} 页` : '';
    case 'lesson_prep':
      return context.prepId ? `备课 #${context.prepId}` : '';
    default:
      return '';
  }
}

export default ContextIndicator;
