// API requests related to PPT interactive player
import { getOrCreateCourseId } from './pptSession';
import { get, post } from '../../services/api';
import { getToken } from '../../utils/tokenManager';

/**
 * 获取 API 基础 URL（与 api.js 中的逻辑一致）
 * @returns {string} API 基础 URL
 */
function getApiBase() {
  const apiBaseRaw = import.meta.env.VITE_API_BASE || '';
  if (!apiBaseRaw) {
    return '';
  }
  return apiBaseRaw.endsWith('/') ? apiBaseRaw.slice(0, -1) : apiBaseRaw;
}

/**
 * 获取配置好的请求选项（用于需要特殊响应类型的请求）
 * @param {Object} options - 额外的 fetch 选项
 * @returns {Object} 配置好的 fetch 选项
 */
function getRequestConfig(options = {}) {
  const headers = {
    ...options.headers,
  };

  // 如果有 token，自动添加到请求头
  const token = getToken();
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  return {
    credentials: 'include',
    ...options,
    headers,
  };
}

/**
 * 获取音频片段信息
 * @param {string} courseId - 课程ID
 * @param {number} pageNumber - 页码
 * @returns {Promise<Object>} 片段信息
 */
export async function fetchSegments(courseId, pageNumber) {
  try {
    const response = await get(`/api/speech/ppt/${courseId}/page/${pageNumber}`);
    // 如果后端返回统一格式 {code, message, data}，提取 data
    return response.data || response;
  } catch (error) {
    throw new Error(`获取片段信息失败: ${error.message}`);
  }
}

/**
 * 获取合并后的音频（ArrayBuffer）
 * @param {string} courseId - 课程ID
 * @param {number} pageNumber - 页码
 * @returns {Promise<ArrayBuffer>} 音频数据
 */
export async function fetchMergedAudio(courseId, pageNumber) {
  const apiBase = getApiBase();
  const url = `${apiBase}/api/speech/ppt/${courseId}/page/${pageNumber}/audio`;
  const config = getRequestConfig({
    headers: {
      'Accept': 'audio/*',
    },
  });

  const res = await fetch(url, config);
  if (!res.ok) {
    throw new Error(`获取页面音频失败: ${res.status}`);
  }
  return res.arrayBuffer();
}

/**
 * 提问
 * @param {string} courseId - 课程ID
 * @param {string} question - 问题
 * @returns {Promise<Object>} 回答数据
 */
export async function askQuestion(courseId, question) {
  if (!courseId) courseId = getOrCreateCourseId();
  try {
    const response = await post('/api/voice-chat/ask', {
      courseId,
      question,
    });
    // 如果后端返回统一格式 {code, message, data}，提取 data
    const data = response.data || response;
    try {
      console.debug('[askQuestion] response:', data);
    } catch {}
    return data;
  } catch (error) {
    throw new Error(`提问失败: ${error.message}`);
  }
}

/**
 * 合成语音（返回 Blob）
 * @param {string} courseId - 课程ID
 * @param {string} text - 要合成的文本
 * @returns {Promise<Blob|null>} 音频 Blob，失败时返回 null
 */
export async function synthesizeSpeech(courseId, text) {
  if (!courseId) courseId = getOrCreateCourseId();
  const apiBase = getApiBase();
  const url = `${apiBase}/api/voice-chat/synthesize`;
  const config = getRequestConfig({
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Accept': 'audio/*',
    },
    body: JSON.stringify({ courseId, text }),
  });

  try {
    const res = await fetch(url, config);
    if (!res.ok) return null;
    return res.blob();
  } catch (error) {
    console.error('合成语音失败:', error);
    return null;
  }
}

/**
 * 触发批量合成（基于大纲）
 * @param {Object} outlineJson - 大纲 JSON 对象
 * @param {Object} optionsOverride - 可选的配置覆盖
 * @returns {Promise<Object>} 响应数据
 */
export async function triggerBulkSynthesisFromOutline(outlineJson, optionsOverride) {
  if (!outlineJson || typeof outlineJson !== 'object') {
    throw new Error('outlineJson 必须为对象');
  }

  const defaultOptions = {
    enablePolishing: true,
    saveOriginalText: true,
    audioFormat: 'wav',
    sampleRate: 16000,
  };

  const payload = {
    ...outlineJson,
    options: { ...defaultOptions, ...(optionsOverride || {}), ...(outlineJson.options || {}) },
  };

  try {
    const response = await post('/api/speech/bulk-synthesis', payload);
    // 如果后端返回统一格式 {code, message, data}，提取 data
    return response.data || response;
  } catch (error) {
    throw new Error(`批量合成请求失败: ${error.message}`);
  }
}


