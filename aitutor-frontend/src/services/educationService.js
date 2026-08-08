/**
 * 教育阶段服务
 * 处理教育阶段、年级等相关操作
 */

import { get } from './api';

/**
 * 获取所有教育阶段
 * @returns {Promise<Array>} 教育阶段列表
 */
export async function getAllStages() {
  try {
    const response = await get('/api/education/stages');

    if (response.code === 200 && response.data) {
      return response.data;
    }

    throw new Error(response.message || '获取教育阶段失败');
  } catch (error) {
    console.error('获取教育阶段失败:', error);
    throw error;
  }
}

/**
 * 根据阶段代码获取年级列表
 * @param {string} stageCode - 教育阶段代码（如：PRIMARY、JUNIOR、SENIOR）
 * @returns {Promise<Array>} 年级列表
 */
export async function getGradesByStage(stageCode) {
  try {
    const response = await get(`/api/education/stages/${stageCode}/grades`);

    if (response.code === 200 && response.data) {
      return response.data;
    }

    throw new Error(response.message || '获取年级列表失败');
  } catch (error) {
    console.error('获取年级列表失败:', error);
    throw error;
  }
}

export default {
  getAllStages,
  getGradesByStage,
};

