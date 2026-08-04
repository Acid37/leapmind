/**
 * Token 管理工具
 * 用于存储、获取和管理 JWT Token
 */

const TOKEN_KEY = 'auth_token';
const TOKEN_EXPIRY_KEY = 'auth_token_expiry';
const USER_KEY = 'user_info';

/**
 * 保存 Token
 * @param {string} token - JWT token
 * @param {number} expiresIn - 过期时间（秒）
 */
export function saveToken(token, expiresIn) {
  try {
    localStorage.setItem(TOKEN_KEY, token);
    
    // 计算过期时间戳
    if (expiresIn) {
      const expiryTime = Date.now() + expiresIn * 1000;
      localStorage.setItem(TOKEN_EXPIRY_KEY, expiryTime.toString());
    }
  } catch (error) {
    console.error('保存 token 失败:', error);
  }
}

/**
 * 获取 Token
 * @returns {string|null} token 或 null
 */
export function getToken() {
  try {
    const token = localStorage.getItem(TOKEN_KEY);
    
    if (!token) {
      return null;
    }

    // 检查是否过期
    const expiryTime = localStorage.getItem(TOKEN_EXPIRY_KEY);
    if (expiryTime && Date.now() > parseInt(expiryTime)) {
      // Token 已过期，清除
      removeToken();
      return null;
    }

    return token;
  } catch (error) {
    console.error('获取 token 失败:', error);
    return null;
  }
}

/**
 * 移除 Token
 */
export function removeToken() {
  try {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(TOKEN_EXPIRY_KEY);
    localStorage.removeItem(USER_KEY);
  } catch (error) {
    console.error('移除 token 失败:', error);
  }
}

/**
 * 检查 Token 是否存在且有效
 * @returns {boolean}
 */
export function hasValidToken() {
  return getToken() !== null;
}

/**
 * 保存用户信息
 * @param {Object} user - 用户信息对象
 */
export function saveUserInfo(user) {
  try {
    // 如果 user 是 null 或 undefined，清除存储
    if (!user) {
      localStorage.removeItem(USER_KEY);
      return;
    }
    localStorage.setItem(USER_KEY, JSON.stringify(user));
  } catch (error) {
    console.error('保存用户信息失败:', error);
  }
}

/**
 * 获取用户信息
 * @returns {Object|null} 用户信息或 null
 */
export function getUserInfo() {
  try {
    const userStr = localStorage.getItem(USER_KEY);
    
    // 如果不存在或者是无效的字符串，返回 null
    if (!userStr || userStr === 'undefined' || userStr === 'null') {
      // 如果是无效字符串，清除它
      if (userStr === 'undefined' || userStr === 'null') {
        localStorage.removeItem(USER_KEY);
      }
      return null;
    }
    
    // 尝试解析 JSON
    const parsed = JSON.parse(userStr);
    
    // 如果解析结果是 null 或 undefined，也返回 null
    if (parsed === null || parsed === undefined) {
      localStorage.removeItem(USER_KEY);
      return null;
    }
    
    return parsed;
  } catch (error) {
    console.error('获取用户信息失败:', error);
    // 如果解析失败，清除无效数据
    try {
      localStorage.removeItem(USER_KEY);
    } catch (e) {
      // 忽略清除失败的错误
    }
    return null;
  }
}

/**
 * 清除所有认证信息
 */
export function clearAuth() {
  removeToken();
}

/**
 * 根据年级代码推断教育阶段代码
 * @param {string} gradeCode - 年级代码（如：GRADE_1, GRADE_7, GRADE_10）
 * @returns {string|null} 教育阶段代码（PRIMARY, JUNIOR, SENIOR）或 null
 */
export function inferStageCodeFromGrade(gradeCode) {
  if (!gradeCode || typeof gradeCode !== 'string') return null
  
  // 提取年级数字
  const match = gradeCode.match(/GRADE_(\d+)/)
  if (!match) return null
  
  const gradeNum = parseInt(match[1], 10)
  
  // 根据年级数字推断阶段
  // GRADE_1-GRADE_6: 小学 (PRIMARY)
  // GRADE_7-GRADE_9: 初中 (JUNIOR)
  // GRADE_10-GRADE_12: 高中 (SENIOR)
  if (gradeNum >= 1 && gradeNum <= 6) {
    return 'PRIMARY'
  } else if (gradeNum >= 7 && gradeNum <= 9) {
    return 'JUNIOR'
  } else if (gradeNum >= 10 && gradeNum <= 12) {
    return 'SENIOR'
  }
  
  return null
}

export default {
  saveToken,
  getToken,
  removeToken,
  hasValidToken,
  saveUserInfo,
  getUserInfo,
  clearAuth,
  inferStageCodeFromGrade,
};

