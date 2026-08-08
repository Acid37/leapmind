import { state } from './pptState';

function generateId() {
  if (typeof crypto !== 'undefined' && crypto.randomUUID) return crypto.randomUUID();
  return 'course_' + Math.random().toString(36).slice(2) + Date.now().toString(36);
}

export function getOrCreateCourseId() {
  if (state.currentCourseId) return state.currentCourseId;
  try {
    const stored = localStorage.getItem('currentCourseId');
    if (stored) {
      state.currentCourseId = stored;
      return stored;
    }
  } catch (_) {}
  const id = generateId();
  state.currentCourseId = id;
  try { localStorage.setItem('currentCourseId', id); } catch (_) {}
  return id;
}

export function setCourseId(courseId) {
  if (!courseId || typeof courseId !== 'string') return;
  state.currentCourseId = courseId;
  try { localStorage.setItem('currentCourseId', courseId); } catch (_) {}
}

// 向后兼容的别名
export const getOrCreateSessionId = getOrCreateCourseId;
export const setSessionId = setCourseId;



