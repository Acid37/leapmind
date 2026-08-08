/**
 * @typedef {Object} StructuredQuestion
 * @property {string} stem - 题干
 * @property {string[]} [options] - 选项列表
 * @property {'single_choice'|'multi_choice'|'fill_blank'|'short_answer'|'essay'} type - 题型
 * @property {string} subject - 科目
 */

/**
 * @typedef {Object} OCRResult
 * @property {number} ocrRecordId
 * @property {string} imageUrl
 * @property {string} recognizedText
 * @property {StructuredQuestion} structuredQuestion
 * @property {number} confidence
 */

/**
 * @typedef {Object} QASSEChunk
 * @property {'answer'|'step'|'knowledge'|'done'} type
 * @property {string} content
 * @property {number} [contentId]
 */

export default {}
