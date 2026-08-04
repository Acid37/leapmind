import { fetchSegments, fetchMergedAudio } from './pptApi';
import { splitAudioByDelimiter, createAudioBlob } from './pptAudioSplit';
import { state } from './pptState';
import { renderSegments } from './pptPlayback';
import { showError, showSuccess } from './pptUi';
import { getOrCreateCourseId } from './pptSession';

export async function loadPageAudio() {
  let courseId = document.getElementById('courseId')?.value.trim() || document.getElementById('sessionId')?.value.trim();
  if (!courseId) courseId = getOrCreateCourseId();
  const pageNumber = document.getElementById('pageNumber').value.trim();
  if (!courseId || !pageNumber) {
    showError('请输入课程ID和页码');
    return;
  }
  try {
    showSuccess('正在加载页面音频数据...');
    const segments = await fetchSegments(courseId, pageNumber);
    if (!segments || segments.length === 0) {
      showError('未找到该页面的音频数据');
      return;
    }
    showSuccess('正在下载并拆分页面音频...');
    const audioBuffer = await fetchMergedAudio(courseId, pageNumber);
    const splitAudioData = splitAudioByDelimiter(audioBuffer);
    if (splitAudioData.length === 0) throw new Error('音频拆分失败，未找到有效的音频片段');
    state.splitAudioBlobs = splitAudioData.map((data) => createAudioBlob(data));
    const validBlobs = state.splitAudioBlobs.filter((b) => b.size > 0);
    if (validBlobs.length !== state.splitAudioBlobs.length) {
      console.warn(`⚠️ 发现 ${state.splitAudioBlobs.length - validBlobs.length} 个空音频片段`);
    }
    state.currentCourseId = courseId;
    state.currentPageNumber = parseInt(pageNumber);
    state.audioSegments = segments;
    state.currentSegmentIndex = 0;

    const container = document.getElementById('segmentItems');
    renderSegments(container, state.audioSegments, state.splitAudioBlobs, (idx) => playSegmentExternal(idx));
    updatePlaybackStatusExternal();
    enablePlaybackControlsExternal();

    showSuccess(`成功加载并拆分 ${segments.length} 个音频片段`);
  } catch (error) {
    console.error('加载音频数据失败:', error);
    showError('加载音频数据失败: ' + error.message);
  }
}

// 直接使用指定的 courseId 与 pageNumber 加载页面音频数据
export async function loadPageAudioWith(courseId, pageNumber) {
  if (!courseId || !pageNumber) {
    throw new Error('缺少 course_id 或 pageNumber');
  }
  try {
    showSuccess('正在加载页面音频数据...');
    const segments = await fetchSegments(courseId, pageNumber);
    if (!segments || segments.length === 0) {
      showError('未找到该页面的音频数据');
      return;
    }
    showSuccess('正在下载并拆分页面音频...');
    const audioBuffer = await fetchMergedAudio(courseId, pageNumber);
    const splitAudioData = splitAudioByDelimiter(audioBuffer);
    if (splitAudioData.length === 0) throw new Error('音频拆分失败，未找到有效的音频片段');
    state.splitAudioBlobs = splitAudioData.map((data) => createAudioBlob(data));
    const validBlobs = state.splitAudioBlobs.filter((b) => b.size > 0);
    if (validBlobs.length !== state.splitAudioBlobs.length) {
      console.warn(`⚠️ 发现 ${state.splitAudioBlobs.length - validBlobs.length} 个空音频片段`);
    }
    state.currentCourseId = courseId;
    state.currentPageNumber = parseInt(pageNumber);
    state.audioSegments = segments;
    state.currentSegmentIndex = 0;

    const container = document.getElementById('segmentItems');
    renderSegments(container, state.audioSegments, state.splitAudioBlobs, (idx) => playSegmentExternal(idx));
    updatePlaybackStatusExternal();
    enablePlaybackControlsExternal();

    showSuccess(`成功加载并拆分 ${segments.length} 个音频片段`);
  } catch (error) {
    console.error('加载音频数据失败:', error);
    showError('加载音频数据失败: ' + error.message);
    throw error;
  }
}

// Hooks for cross-module calls provided by main file or controller
let playSegmentExternal = () => {};
let updatePlaybackStatusExternal = () => {};
let enablePlaybackControlsExternal = () => {};

export function bindLoaderHooks({ playSegment, updatePlaybackStatus, enablePlaybackControls }) {
  playSegmentExternal = playSegment || (() => {});
  updatePlaybackStatusExternal = updatePlaybackStatus || (() => {});
  enablePlaybackControlsExternal = enablePlaybackControls || (() => {});
}



