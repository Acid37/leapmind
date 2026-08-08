/**
 * M3 薄弱点模块 类型定义
 */

/** @typedef {'HIGH'|'MEDIUM'|'LOW'|'MASTERED'} WeaknessLevel */

/**
 * @typedef {Object} WeakPoint
 * @property {number} id              薄弱点ID
 * @property {string} knowledgePoint  知识点名称（如"圆的面积"）
 * @property {string} subject         科目
 * @property {WeaknessLevel} weaknessLevel  薄弱等级 HIGH/MEDIUM/LOW
 * @property {number} errorCount      错题数
 * @property {number} totalCount      总答题数
 * @property {number} accuracyRate    正确率（百分比 0-100）
 * @property {string} status          状态 ACTIVE/RESOLVED/IMPROVING
 * @property {'improving'|'stable'|'declining'} [trend]  趋势
 * @property {string} [lastErrorTime] 最近一次错误时间
 * @property {string|null} aiAnalysis  AI分析（可空）
 * @property {string|null} aiSuggestion AI建议（可空）
 */

/**
 * @typedef {Object} WeakPointListResponse
 * @property {WeakPoint[]} data
 */

export {}
