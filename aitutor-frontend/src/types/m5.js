/**
 * M5 AI备课模块 类型定义
 */

/** @typedef {'standard'|'detailed'|'interactive'} PrepStyle */

/** @typedef {'math'|'chinese'|'english'|'physics'|'chemistry'|'biology'} Subject */

/** @typedef {'grade_7'|'grade_8'|'grade_9'|'grade_10'|'grade_11'|'grade_12'} Grade */

/**
 * @typedef {Object} LessonPrepCreateRequest
 * @property {number} userId
 * @property {string} title
 * @property {Subject} subject
 * @property {Grade} grade
 * @property {number[]} knowledgePointIds
 * @property {string[]} teachingGoals
 * @property {number} totalHours
 * @property {number[]} [weakPointIds]
 * @property {PrepStyle} style
 * @property {string} [userProfileSummary]
 */

/**
 * @typedef {Object} GenerateSSEChunk
 * @property {'syllabus_chunk'|'syllabus_done'|'slide'|'slides_done'|'narration'|'warn'|'final_check'|'done'|'error'} event
 * @property {*} data
 */

/**
 * @typedef {Object} SyllabusDone
 * @property {string} title
 * @property {number} totalHours
 * @property {Section[]} sections
 */

/**
 * @typedef {Object} Section
 * @property {number} hourIndex
 * @property {string} title
 * @property {string} coreContent
 * @property {string[]} teachingGoals
 * @property {string[]} keyPoints
 * @property {string[]} difficultPoints
 * @property {TeachingStep[]} teachingProcess
 * @property {Object} homework
 */

/**
 * @typedef {Object} TeachingStep
 * @property {string} step
 * @property {string} duration
 * @property {string} teacherActivity
 * @property {string} studentActivity
 * @property {string} designIntent
 */

/**
 * @typedef {Object} SlideData
 * @property {number} pageNum
 * @property {'cover'|'content'|'interactive'|'summary'|'homework'} type
 * @property {string} title
 * @property {string[]} bulletPoints
 * @property {string} [imageSuggestion]
 * @property {string} [formula]
 * @property {Object} [interaction]
 */

/**
 * @typedef {Object} WeakPointItem
 * @property {number} id
 * @property {string} knowledgePoint
 * @property {string} subject
 * @property {'HIGH'|'MEDIUM'|'LOW'} weaknessLevel
 * @property {number} errorCount
 * @property {number} totalCount
 * @property {number} accuracyRate
 * @property {string} status
 */

/**
 * @typedef {Object} KnowledgePointItem
 * @property {number} id
 * @property {string} name
 * @property {string} subject
 * @property {string} grade
 */

export {}
