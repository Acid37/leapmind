import { showSuccess, showError } from './pptUi';

export async function startBulkSynthesis() {
  const jsonInput = document.getElementById('bulkSynthesisJson').value.trim();
  if (!jsonInput) {
    showError('请输入PPT数据的JSON格式');
    return;
  }
  let requestData;
  try {
    requestData = JSON.parse(jsonInput);
  } catch (error) {
    showError('JSON格式错误: ' + error.message);
    return;
  }
  if (!requestData.title || !requestData.slides || !Array.isArray(requestData.slides)) {
    showError('JSON数据缺少必需字段: title 和 slides');
    return;
  }
  const options = {
    enablePolishing: document.getElementById('enablePolishing').checked,
    saveOriginalText: document.getElementById('saveOriginalText').checked,
    audioFormat: document.getElementById('audioFormat').value,
    sampleRate: parseInt(document.getElementById('sampleRate').value),
  };
  const fullRequestData = { ...requestData, options };
  showSynthesisStatus('正在提交批量合成请求...', true);
  try {
    const response = await fetch(`/api/speech/bulk-synthesis`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(fullRequestData),
    });
    if (!response.ok) throw new Error(`HTTP ${response.status}: ${response.statusText}`);
    const result = await response.json();
    showSynthesisStatus(`批量合成已启动！状态: ${result.status}`, false);
    updateSynthesisDetails(result);
    const courseId = result.course_id || result.sessionId;
    if (courseId) {
      showCourseId(courseId);
      showSuccess(`批量合成已启动，课程ID: ${courseId}`);
    }
  } catch (error) {
    showSynthesisStatus('批量合成请求失败: ' + error.message, false);
    showError('批量合成失败: ' + error.message);
  }
}

export function showSynthesisStatus(message, isLoading) {
  const statusDiv = document.getElementById('synthesisStatus');
  const statusText = document.getElementById('synthesisStatusText');
  const loadingDiv = document.getElementById('synthesisLoading');
  if (!statusDiv || !statusText || !loadingDiv) return;
  statusText.textContent = message;
  loadingDiv.style.display = isLoading ? 'inline-block' : 'none';
  statusDiv.style.display = 'block';
}

export function updateSynthesisDetails(result) {
  const detailsDiv = document.getElementById('synthesisDetails');
  if (!detailsDiv) return;
  let details = '';
  if (result.totalSlides) details += `总页数: ${result.totalSlides}`;
  if (result.totalContentPoints) details += ` | 总内容点: ${result.totalContentPoints}`;
  if (result.startTime) details += ` | 开始时间: ${new Date(result.startTime).toLocaleString()}`;
  if (result.message) details += ` | ${result.message}`;
  detailsDiv.textContent = details;
}

export function showCourseId(courseId) {
  const courseIdDiv = document.getElementById('synthesisSessionId') || document.getElementById('synthesisCourseId');
  const courseIdValue = document.getElementById('sessionIdValue') || document.getElementById('courseIdValue');
  if (!courseIdDiv || !courseIdValue) return;
  courseIdValue.textContent = courseId;
  courseIdDiv.style.display = 'block';
}

// 向后兼容的别名
export const showSessionId = showCourseId;

export function copyCourseId() {
  const courseId = document.getElementById('sessionIdValue')?.textContent || document.getElementById('courseIdValue')?.textContent || '';
  navigator.clipboard.writeText(courseId)
    .then(() => showSuccess('课程ID已复制到剪贴板'))
    .catch((err) => {
      console.error('复制失败:', err);
      showError('复制失败，请手动复制');
    });
}

// 向后兼容的别名
export const copySessionId = copyCourseId;

export function loadSampleData() {
  const sampleData = {
    title: '人工智能基础知识',
    slides: [
      {
        page_number: 1,
        title: '什么是人工智能',
        content_points: [
          '人工智能是计算机科学的一个分支',
          '它致力于创建能够执行通常需要人类智能的任务的系统',
          '包括学习、推理、感知和语言理解等能力',
        ],
        slide_type: 'introduction',
        type: 'concept',
        description: '介绍人工智能的基本概念',
      },
      {
        page_number: 2,
        title: '机器学习的类型',
        content_points: [
          '监督学习：使用标记数据进行训练',
          '无监督学习：从未标记数据中发现模式',
          '强化学习：通过与环境交互来学习最优策略',
        ],
        slide_type: 'content',
        type: 'classification',
        description: '介绍机器学习的主要类型',
      },
      {
        page_number: 3,
        title: '深度学习应用',
        content_points: [
          '图像识别：在医疗诊断和自动驾驶中的应用',
          '自然语言处理：机器翻译和智能客服',
          '语音识别：语音助手和语音转文字技术',
        ],
        slide_type: 'application',
        type: 'examples',
        description: '展示深度学习的实际应用场景',
      },
    ],
  };
  const el = document.getElementById('bulkSynthesisJson');
  if (el) el.value = JSON.stringify(sampleData, null, 2);
  showSuccess('示例数据已加载');
}

export function validateJsonFormat() {
  const jsonInput = document.getElementById('bulkSynthesisJson').value.trim();
  if (!jsonInput) {
    showError('请先输入JSON数据');
    return;
  }
  try {
    const data = JSON.parse(jsonInput);
    const errors = [];
    if (!data.title || typeof data.title !== 'string') errors.push('缺少或无效的 title 字段');
    if (!data.slides || !Array.isArray(data.slides)) errors.push('缺少或无效的 slides 字段（应为数组）');
    else {
      data.slides.forEach((slide, index) => {
        if (!slide.page_number || typeof slide.page_number !== 'number') errors.push(`第${index + 1}个slide缺少或无效的 page_number 字段`);
        if (!slide.title || typeof slide.title !== 'string') errors.push(`第${index + 1}个slide缺少或无效的 title 字段`);
        if (!slide.content_points || !Array.isArray(slide.content_points)) errors.push(`第${index + 1}个slide缺少或无效的 content_points 字段（应为数组）`);
      });
    }
    if (errors.length > 0) {
      showError('JSON验证失败:\n' + errors.join('\n'));
    } else {
      showSuccess(`JSON格式验证通过！包含 ${data.slides.length} 个幻灯片`);
    }
  } catch (error) {
    showError('JSON格式错误: ' + error.message);
  }
}

export function clearJsonInput() {
  const el = document.getElementById('bulkSynthesisJson');
  const status = document.getElementById('synthesisStatus');
  if (el) el.value = '';
  if (status) status.style.display = 'none';
  showSuccess('输入已清空');
}


