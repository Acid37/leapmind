/**
 * 服务层统一导出
 * 方便在组件中使用
 */

// API 基础服务
export { default as api, ApiError } from './api';

// 认证服务
export {
  login,
  register,
  getUserProfile,
  updateUserProfile,
  logout,
  checkAuth,
} from './authService';

// 教育阶段服务
export {
  getAllStages,
  getGradesByStage,
} from './educationService';

// 课程服务
export {
  getSections,
  SEMESTER,
} from './courseService';

// M1 做题板块服务
export {
  getQuestions,
  getFilterOptions,
  getQuestionDetail,
  generateSession,
  submitAnswer,
  getWrongQuestions,
  toggleFocus,
  deleteWrongQuestion,
  getStatistics,
  getRanking,
  getPointsHistory,
  dailyCheckin,
} from './practiceService';

// 对话服务（M7 ChatPanel）
export {
  default as chatService,
} from './chatService';

// M6 学习画像服务
export {
  getLearningProfile,
  getKnowledgePointDetail,
} from './learningProfileService';

// Token 管理
export {
  saveToken,
  getToken,
  removeToken,
  hasValidToken,
  saveUserInfo,
  getUserInfo,
  clearAuth,
} from '../utils/tokenManager';

