/**
 * API 服务基础配置
 * 统一处理 HTTP 请求和响应
 */

import { getToken, removeToken } from '../utils/tokenManager';

// 获取 API 基础 URL
// 如果设置了 VITE_API_BASE，使用它；否则使用空字符串（依赖 Vite 代理）
const getApiBase = () => {
  const apiBaseRaw = import.meta.env.VITE_API_BASE || '';
  // 如果为空，返回空字符串，使用相对路径（由 Vite 代理处理）
  if (!apiBaseRaw) {
    return '';
  }
  return apiBaseRaw.endsWith('/') ? apiBaseRaw.slice(0, -1) : apiBaseRaw;
};

/**
 * 统一响应格式
 * @typedef {Object} ApiResponse
 * @property {number} code - 状态码
 * @property {string} message - 响应消息
 * @property {*} data - 响应数据
 * @property {string} timestamp - 响应时间戳
 */

/**
 * API 错误类
 */
export class ApiError extends Error {
  constructor(message, code, data = null) {
    super(message);
    this.name = 'ApiError';
    this.code = code;
    this.data = data;
  }
}

/**
 * 发送 HTTP 请求
 * @param {string} endpoint - API 端点（相对路径）
 * @param {Object} options - fetch 选项
 * @returns {Promise<ApiResponse>}
 */
export async function request(endpoint, options = {}) {
  const apiBase = getApiBase();
  const url = `${apiBase}${endpoint}`;

  // 默认请求头
  const headers = {
    'Content-Type': 'application/json',
    'Accept': 'application/json',
    ...options.headers,
  };

  // 如果有 token，自动添加到请求头
  const token = getToken();
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  // 合并选项
  const config = {
    ...options,
    headers,
  };

  // 调试信息：记录请求详情（仅在开发环境）
  if (import.meta.env.DEV) {
    console.log('API 请求:', {
      url,
      method: config.method || 'GET',
      headers: config.headers,
      body: config.body,
    });
  }

  try {
    const response = await fetch(url, config);
    
    // 解析响应
    let result;
    const contentType = response.headers.get('content-type') || '';
    
    if (contentType.includes('application/json')) {
      try {
        result = await response.json();
      } catch (parseError) {
        // JSON 解析失败，尝试获取文本
        const text = await response.text();
        console.error('JSON 解析失败，响应内容:', text);
        result = {
          code: response.status,
          message: text || response.statusText || '服务器返回了无效的响应格式',
          data: null,
          timestamp: new Date().toISOString(),
        };
      }
    } else {
      // 如果不是 JSON，尝试获取文本
      const text = await response.text();
      result = {
        code: response.status,
        message: text || response.statusText,
        data: null,
        timestamp: new Date().toISOString(),
      };
    }

    // 检查响应状态
    if (response.ok) {
      // HTTP 状态码 2xx 表示成功
      if (result.code === 200 || result.status === 'SUCCESS' || !result.code) {
        return result;
      }
    }

    // 调试信息：记录真正的错误响应（只在失败时打印）
    console.error('API 错误响应:', {
      url,
      status: response.status,
      statusText: response.statusText,
      result,
    });
    
    // 如果是 500 错误，尝试获取更详细的错误信息
    if (response.status === 500 || result.code === 500) {
      // 检查响应体是否包含更多信息
      if (result.data && typeof result.data === 'object') {
        console.error('服务器错误详情:', result.data);
      }
    }

    // 处理 401 未授权（token 过期或无效）
    if (response.status === 401 || result.code === 401) {
      removeToken(); // 清除无效 token
      throw new ApiError(result.message || '未授权访问，请重新登录', result.code, result.data);
    }

    // 处理 500 服务器错误
    if (response.status === 500 || result.code === 500) {
      // 尝试从错误数据中获取更详细的错误信息
      const errorMessage = result.data?.message || result.message || '服务器内部错误，请稍后重试';
      throw new ApiError(errorMessage, 500, result.data);
    }

    // 其他错误
    const errorCode = result.code || response.status;
    const errorMessage = result.message || `请求失败 (${errorCode})`;
    throw new ApiError(errorMessage, errorCode, result.data);
  } catch (error) {
    // 网络错误或其他异常
    if (error instanceof ApiError) {
      throw error;
    }
    
    throw new ApiError(
      error.message || '网络请求失败，请检查网络连接',
      0,
      null
    );
  }
}

/**
 * GET 请求
 * @param {string} endpoint - API 端点
 * @param {Object} params - 查询参数
 * @param {Object} options - fetch 选项
 * @returns {Promise<ApiResponse>}
 */
export async function get(endpoint, params = {}, options = {}) {
  // 构建查询字符串
  const queryString = new URLSearchParams(params).toString();
  const url = queryString ? `${endpoint}?${queryString}` : endpoint;

  return request(url, {
    method: 'GET',
    ...options,
  });
}

/**
 * POST 请求
 * @param {string} endpoint - API 端点
 * @param {Object} data - 请求体数据
 * @param {Object} options - fetch 选项
 * @returns {Promise<ApiResponse>}
 */
export async function post(endpoint, data = {}, options = {}) {
  return request(endpoint, {
    method: 'POST',
    body: JSON.stringify(data),
    ...options,
  });
}

/**
 * PUT 请求
 * @param {string} endpoint - API 端点
 * @param {Object} data - 请求体数据
 * @param {Object} options - fetch 选项
 * @returns {Promise<ApiResponse>}
 */
export async function put(endpoint, data = {}, options = {}) {
  return request(endpoint, {
    method: 'PUT',
    body: JSON.stringify(data),
    ...options,
  });
}

/**
 * DELETE 请求
 * @param {string} endpoint - API 端点
 * @param {Object} options - fetch 选项
 * @returns {Promise<ApiResponse>}
 */
export async function del(endpoint, options = {}) {
  return request(endpoint, {
    method: 'DELETE',
    ...options,
  });
}

export default {
  get,
  post,
  put,
  delete: del,
  request,
};

