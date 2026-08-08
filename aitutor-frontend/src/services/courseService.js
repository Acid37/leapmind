/**
 * 课程服务
 * 处理课程、章节等相关操作
 */

import { post } from './api';

/**
 * 获取课本章节
 * 使用POST请求，参数在请求体中（JSON格式）
 * @param {Object} params - 请求参数
 * @param {string} params.subject - 课程学科名（数学、语文）
 * @param {string} params.stageName - 教育阶段名称（如：小学、初中、高中）
 * @param {string} params.gradeName - 年级名称（如：一年级、二年级等）
 * @param {string} params.semester - 学期枚举值（SEMESTER_1、SEMESTER_2）
 * @returns {Promise<Array>} 课本章节列表
 */
export async function getSections(params) {
  try {
    const { subject, stageName, gradeName, semester } = params;

    // 验证必填参数
    if (!subject || !stageName || !gradeName || !semester) {
      throw new Error('缺少必填参数：subject, stageName, gradeName, semester');
    }

    // 使用POST请求，参数在请求体中（JSON格式）
    const response = await post('/api/courses/section', {
      subject,
      stageName,
      gradeName,
      semester,
    });

    if (response.code === 200 && response.data) {
      return response.data;
    }

    throw new Error(response.message || '获取课本章节失败');
  } catch (error) {
    console.error('获取课本章节失败:', error);
    throw error;
  }
}

/**
 * 学期枚举
 */
export const SEMESTER = {
  FIRST: 'SEMESTER_1',   // 第一学期
  SECOND: 'SEMESTER_2',  // 第二学期
};

export default {
  getSections,
  SEMESTER,
};

