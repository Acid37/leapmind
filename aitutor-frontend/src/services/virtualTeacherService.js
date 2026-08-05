import { ApiError, get, post, put } from './api';
import { getToken } from '@/utils/tokenManager.js';
import {
  normalizeAnimationPayload,
  normalizeExpression,
} from '@/features/virtualTeacher/animationCue.js';

const PREFERENCE_KEY = 'leapmind.virtualTeacher.preference';

export const DEFAULT_TEACHER_AVATARS = [
  {
    id: 'teacher-001',
    name: '小跃',
    description: '亲切活泼，适合语言与通识课程',
    modelUrl: '/vrm/teacher001_girl.vrm',
    voiceType: 'young-female-warm',
    accent: '普通话',
    color: 'from-fuchsia-400 to-violet-500',
  },
  {
    id: 'teacher-002',
    name: '知夏',
    description: '沉稳清晰，适合理工科讲解',
    modelUrl: '/vrm/teacher002_with_glasses_girl.vrm',
    voiceType: 'young-female-clear',
    accent: '普通话',
    color: 'from-cyan-400 to-blue-500',
  },
  {
    id: 'teacher-003',
    name: '星澜',
    description: '自然耐心，适合互动答疑',
    modelUrl: '/vrm/teacher003_girl.vrm',
    voiceType: 'young-female-natural',
    accent: '普通话',
    color: 'from-violet-400 to-indigo-500',
  },
];

function unwrap(response) {
  return response?.data ?? response;
}

function normalizeAvatar(avatar, index) {
  return {
    id: String(avatar.id ?? avatar.avatarId ?? `teacher-${index + 1}`),
    name: avatar.name ?? avatar.avatarName ?? `虚拟教师 ${index + 1}`,
    description: avatar.description ?? avatar.introduction ?? 'LeapMind 虚拟教师',
    modelUrl: avatar.modelUrl ?? avatar.vrmUrl ?? avatar.avatarUrl,
    voiceType: avatar.voiceType ?? avatar.voice ?? 'default',
    speed: Number(avatar.speed ?? 1),
    accent: avatar.accent ?? '普通话',
    color: avatar.color ?? DEFAULT_TEACHER_AVATARS[index % DEFAULT_TEACHER_AVATARS.length].color,
  };
}

export function getLocalTeacherPreference() {
  try {
    return JSON.parse(localStorage.getItem(PREFERENCE_KEY) || 'null');
  } catch {
    return null;
  }
}

export function normalizeTeacherExpression(value) {
  return normalizeExpression(value);
}

export async function fetchTeacherAvatars() {
  try {
    const data = unwrap(await get('/api/virtual-teacher/avatars'));
    const avatars = Array.isArray(data) ? data : data?.items ?? data?.records;
    const normalized = avatars
      ?.map(normalizeAvatar)
      .filter((avatar) => avatar.modelUrl);
    return normalized?.length ? normalized : DEFAULT_TEACHER_AVATARS;
  } catch (error) {
    if (error instanceof ApiError && error.code === 401) throw error;
    return DEFAULT_TEACHER_AVATARS;
  }
}

export async function fetchTeacherPreference() {
  try {
    const data = unwrap(await get('/api/virtual-teacher/preference'));
    return data ? normalizeAvatar(data, 0) : getLocalTeacherPreference();
  } catch (error) {
    if (error instanceof ApiError && error.code === 401) throw error;
    return getLocalTeacherPreference();
  }
}

export async function saveTeacherPreference(avatar) {
  const preference = normalizeAvatar(avatar, 0);
  localStorage.setItem(PREFERENCE_KEY, JSON.stringify(preference));

  try {
    await put('/api/virtual-teacher/preference', {
      avatarId: preference.id,
      voiceType: preference.voiceType,
      speed: preference.speed,
    });
    return { preference, synced: true };
  } catch (error) {
    if (error instanceof ApiError && error.code === 401) throw error;
    return { preference, synced: false };
  }
}

function getApiBase() {
  const value = import.meta.env.VITE_API_BASE || '';
  return value.endsWith('/') ? value.slice(0, -1) : value;
}

function resolveApiUrl(value) {
  if (!value || /^(?:https?:|blob:|data:)/i.test(value)) return value;
  const base = getApiBase();
  const path = value.startsWith('/') ? value : `/${value}`;
  return `${base}${path}`;
}

async function readAudioResponse(response) {
  const contentType = response.headers.get('content-type') || '';
  if (contentType.includes('application/json')) {
    const body = await response.json();
    const data = unwrap(body);
    const audioUrl = data?.audioUrl ?? data?.url;
    if (!audioUrl) return null;
    const audioResponse = await fetch(resolveApiUrl(audioUrl));
    if (!audioResponse.ok) return null;
    return {
      audioBlob: await audioResponse.blob(),
      animation: normalizeAnimationPayload(data),
      durationMs: data?.durationMs,
    };
  }
  return {
    audioBlob: await response.blob(),
    animation: null,
    durationMs: Number(response.headers.get('x-audio-duration-ms')) || undefined,
  };
}

export async function askVirtualTeacherQuestion({ courseId, question }) {
  const normalizedQuestion = String(question || '').trim();
  if (!normalizedQuestion) {
    throw new ApiError('请输入问题', 400);
  }

  const response = await post('/api/voice-chat/ask', {
    courseId: String(courseId || 'virtual-teacher'),
    question: normalizedQuestion,
  });
  const data = unwrap(response);
  if (!data?.answer || String(data.status || 'SUCCESS').toUpperCase() !== 'SUCCESS') {
    throw new ApiError(data?.answer || 'AI 教师暂时无法回答', 502, data);
  }
  return data;
}

function htmlToText(value) {
  const html = String(value || '').trim();
  if (!html) return '';
  if (typeof DOMParser === 'undefined') {
    return html.replace(/<[^>]+>/g, ' ').replace(/\s+/g, ' ').trim();
  }
  const documentNode = new DOMParser().parseFromString(html, 'text/html');
  return (documentNode.body?.textContent || '').replace(/\s+/g, ' ').trim();
}

function normalizeCourseSlide(slide, index) {
  const text = htmlToText(slide?.htmlContent ?? slide?.content);
  const title = slide?.title || `第 ${index + 1} 页`;
  return {
    id: String(slide?.slideId ?? slide?.id ?? `slide-${index + 1}`),
    index: Number(slide?.slideIndex ?? index),
    title,
    subtitle: slide?.contentType || '课程课件',
    points: text ? [text] : ['本页暂无文字内容'],
    script: text || `现在讲解${title}。`,
    htmlContent: slide?.htmlContent || '',
  };
}

export async function fetchVirtualTeacherLesson(courseId) {
  const normalizedCourseId = String(courseId || '').trim();
  if (!normalizedCourseId) return null;

  const response = await get(`/api/courses/${encodeURIComponent(normalizedCourseId)}/slides-data`);
  const data = unwrap(response);
  const items = Array.isArray(data) ? data : data?.items ?? data?.records ?? [];
  const slides = items
    .map(normalizeCourseSlide)
    .sort((left, right) => left.index - right.index);
  if (!slides.length) return null;

  return {
    id: normalizedCourseId,
    title: slides[0]?.title || '课程讲解',
    tag: '正式课件',
    content: slides[0]?.script || '',
    prompt: `关于“${slides[0]?.title || '本节内容'}”，你最想进一步理解什么？`,
    slides,
    source: 'api',
  };
}

export async function synthesizeVirtualTeacherSpeech({
  courseId,
  text,
  voiceType,
  speed = 1,
}) {
  const token = getToken();
  const preference = getLocalTeacherPreference();
  const response = await fetch(`${getApiBase()}/api/virtual-teacher/tts`, {
    method: 'POST',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'audio/*, application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify({
      courseId,
      text,
      voiceType: voiceType ?? preference?.voiceType ?? 'default',
      speed,
    }),
  });

  if (!response.ok) {
    throw new ApiError('虚拟教师语音合成暂不可用', response.status);
  }
  return readAudioResponse(response);
}
