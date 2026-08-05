/**
 * 认证服务
 * 处理用户登录、注册、获取用户信息等认证相关操作
 */

import { get, post, put } from './api';
import { saveToken, saveUserInfo, clearAuth } from '../utils/tokenManager';

/**
 * 用户登录
 * @param {string} username - 用户名或邮箱
 * @param {string} password - 密码
 * @returns {Promise<{token: string, user: Object}>}
 */
export async function login(username, password) {
  try {
    const response = await post('/api/auth/login', {
      username: username.trim(),
      password: password,
    });

    if (response.code === 200 && response.data) {
      const { token, tokenType, expiresIn, user } = response.data;
      
      // 保存 token 和用户信息
      saveToken(token, expiresIn);
      saveUserInfo(user);

      return {
        token,
        tokenType,
        expiresIn,
        user,
      };
    }

    throw new Error(response.message || '登录失败');
  } catch (error) {
    console.error('登录失败:', error);
    throw error;
  }
}

/**
 * 用户注册
 * @param {Object} userData - 用户注册数据
 * @param {string} userData.username - 用户名
 * @param {string} userData.password - 密码
 * @param {string} userData.email - 邮箱
 * @param {string} [userData.phone] - 手机号
 * @param {string} [userData.realName] - 真实姓名
 * @param {string} [userData.stageCode] - 教育阶段代码
 * @param {string} [userData.gradeCode] - 年级代码
 * @returns {Promise<Object>} 注册的用户信息
 */
export async function register(userData) {
  try {
    const response = await post('/api/auth/register', userData);

    if (response.code === 200 && response.data) {
      return response.data;
    }

    throw new Error(response.message || '注册失败');
  } catch (error) {
    console.error('注册失败:', error);
    throw error;
  }
}

/**
 * 获取当前登录用户信息
 * @returns {Promise<Object>} 用户信息
 */
export async function getUserProfile() {
  try {
    const response = await get('/api/auth/profile');

    if (response.code === 200 && response.data) {
      // 更新本地缓存的用户信息
      saveUserInfo(response.data);
      return response.data;
    }

    throw new Error(response.message || '获取用户信息失败');
  } catch (error) {
    console.error('获取用户信息失败:', error);
    throw error;
  }
}

/**
 * 更新用户信息
 * @param {Object} userData - 要更新的用户数据
 * @param {string} [userData.email] - 邮箱
 * @param {string} [userData.phone] - 手机号
 * @param {string} [userData.realName] - 真实姓名
 * @param {string} [userData.stageCode] - 教育阶段代码
 * @param {string} [userData.gradeCode] - 年级代码
 * @returns {Promise<Object>} 更新后的用户信息
 */
export async function updateUserProfile(userData) {
  try {
    const response = await put('/api/auth/profile', userData);

    if (response.code === 200 && response.data) {
      // 更新本地缓存的用户信息
      saveUserInfo(response.data);
      return response.data;
    }

    throw new Error(response.message || '更新用户信息失败');
  } catch (error) {
    console.error('更新用户信息失败:', error);
    throw error;
  }
}

/**
 * 用户登出
 */
export function logout() {
  clearAuth();
}

/**
 * 检查用户是否已登录
 * @returns {Promise<boolean>}
 */
export async function checkAuth() {
  try {
    await getUserProfile();
    return true;
  } catch (error) {
    return false;
  }
}

export default {
  login,
  register,
  getUserProfile,
  updateUserProfile,
  logout,
  checkAuth,
};

