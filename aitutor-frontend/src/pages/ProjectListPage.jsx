import React, { useEffect, useMemo, useState } from 'react';
import LevelNode from '../components/projects/LevelNode';
import { triggerBulkSynthesisFromOutline } from '../features/chat/pptApi';
import { setCourseId } from '../features/chat/pptSession';
import { ICONS, COLORS } from '../components/projects/Icons';

const ProjectListPage = ({ onEnterProject }) => {
  const [projects, setProjects] = useState([]);
  const [selectedProjectId, setSelectedProjectId] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    const fetchProjects = async () => {
      setError('');
      setLoading(true);
      try {
        const apiBaseRaw = import.meta.env.VITE_API_BASE || '';
        const apiBase = apiBaseRaw.endsWith('/') ? apiBaseRaw.slice(0, -1) : apiBaseRaw;
        const resp = await fetch(`${apiBase}/api/projects?page=1&page_size=20`, {
          credentials: 'include',
          headers: { Accept: 'application/json' },
        });
        if (!resp.ok) {
          throw new Error('获取项目列表失败');
        }
        const data = await resp.json();
        const list = Array.isArray(data?.projects) ? data.projects : [];
        setProjects(list);
        if (list.length > 0) {
          setSelectedProjectId(list[0]?.project_id || '');
        }
      } catch (err) {
        setError(err?.message || '获取项目列表失败');
      } finally {
        setLoading(false);
      }
    };
    fetchProjects();
  }, []);

  const getProjectTitle = (p) => p?.title || p?.name || p?.project_name || '未命名项目';

  const selectedProject = projects.find(p => p.project_id === selectedProjectId);
  const selectedTitle = selectedProject ? getProjectTitle(selectedProject) : '';

  const levelsWithStyles = useMemo(() => {
    // 根据后端字段 slides_html 是否为 null 决定关卡状态：null -> locked；非空 -> active
    return projects.map((p) => ({
      id: p.project_id,
      title: getProjectTitle(p),
      status: p?.slides_html ? 'active' : 'locked',
      icon: ICONS[Math.floor(Math.random() * ICONS.length)],
      color: COLORS[Math.floor(Math.random() * COLORS.length)],
    }));
  }, [projects]);

  return (
    <div className="w-full h-screen overflow-auto bg-gradient-to-b from-sky-50 via-white to-slate-100 p-6">
      <div className="max-w-4xl mx-auto">
        <div className="text-center mb-8">
          <h1 className="text-3xl sm:text-4xl font-extrabold tracking-wide text-transparent bg-clip-text bg-gradient-to-r from-pink-500 via-violet-500 to-blue-500 drop-shadow">
            学习之旅
          </h1>
          <p className="mt-2 text-slate-500 text-sm">
            选择一个项目开始你的冒险旅程 ✨
          </p>
        </div>
        {loading && <div className="text-slate-600">加载中…</div>}
        {error && !loading && (
          <div className="text-red-500 text-sm" role="alert">{error}</div>
        )}
        {!loading && !error && (
          (levelsWithStyles.length === 0 ? (
            <div className="text-slate-500 text-sm">暂无数据</div>
          ) : (
            <div className="flex flex-col items-center space-y-16">
              {levelsWithStyles.map((level, index) => (
                <LevelNode
                  key={level.id}
                  level={level}
                  isLast={index === levelsWithStyles.length - 1}
                  isSelected={selectedProjectId === level.id}
                  onSelect={async () => {
                    setSelectedProjectId(level.id);
                    // fire-and-forget：发送 outline/slides JSON 进行批量合成，不阻塞跳转
                    try {
                      const project = projects.find(p => p.project_id === level.id);
                      if (project) {
                        const outline = project?.outline && typeof project.outline === 'object' ? project.outline : null;
                        let payload = null;
                        if (outline) {
                          const title = outline.title || getProjectTitle(project);
                          const slides = Array.isArray(outline.slides) ? outline.slides : [];
                          const metadata = outline.metadata;
                          payload = metadata ? { title, slides, metadata } : { title, slides };
                        } else if (Array.isArray(project?.slides_data)) {
                          // 回退：从 slides_data 构造最小可用 payload
                          payload = { title: getProjectTitle(project), slides: project.slides_data };
                        }
                        if (payload && payload.title && Array.isArray(payload.slides) && payload.slides.length > 0) {
                          try {
                            const res = await triggerBulkSynthesisFromOutline(payload, {
                              enablePolishing: true,
                              saveOriginalText: true,
                            });
                            if (res && typeof res.course_id === 'string' && res.course_id) {
                              setCourseId(res.course_id);
                            } else if (res && typeof res.sessionId === 'string' && res.sessionId) {
                              // 向后兼容旧后端返回 sessionId
                              setCourseId(res.sessionId);
                            }
                          } catch (_) {
                            // 忽略触发失败，不阻塞进入
                          }
                        }
                      }
                    } catch {}
                    onEnterProject?.(level.id);
                  }}
                />
              ))}
            </div>
          ))
        )}
      </div>
    </div>
  );
};

export default ProjectListPage;


