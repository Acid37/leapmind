import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  ArrowLeft,
  Brain,
  Check,
  CloudOff,
  Glasses,
  Megaphone,
  Smile,
  Sparkles,
  Volume2,
} from 'lucide-react';
import VirtualTeacherViewer from '@/components/virtualTeacher/VirtualTeacherViewer.jsx';
import {
  DEFAULT_TEACHER_AVATARS,
  fetchTeacherAvatars,
  fetchTeacherPreference,
  saveTeacherPreference,
} from '@/services/virtualTeacherService.js';

export default function TeacherAvatarPage({ onBack }) {
  const [avatars, setAvatars] = useState(DEFAULT_TEACHER_AVATARS);
  const [selectedId, setSelectedId] = useState(DEFAULT_TEACHER_AVATARS[0].id);
  const [savedId, setSavedId] = useState(null);
  const [saveState, setSaveState] = useState('idle');
  const [viewer, setViewer] = useState(null);
  const [demoState, setDemoState] = useState('');

  useEffect(() => {
    let active = true;
    Promise.all([fetchTeacherAvatars(), fetchTeacherPreference()])
      .then(([items, preference]) => {
        if (!active) return;
        setAvatars(items);
        const preferredId = preference?.id;
        const matched = items.find((item) => item.id === preferredId);
        const initialId = matched?.id ?? items[0]?.id;
        setSelectedId(initialId);
        setSavedId(matched?.id ?? null);
      })
      .catch(() => {});
    return () => {
      active = false;
    };
  }, []);

  const selected = useMemo(
    () => avatars.find((avatar) => avatar.id === selectedId) ?? avatars[0],
    [avatars, selectedId],
  );

  const handleViewerReady = useCallback((readyViewer) => {
    setViewer(readyViewer);
  }, []);

  const playDemo = (expression, motion, label) => {
    if (!viewer?.model?.emoteController) return;
    viewer.model.emoteController.playEmotion(expression);
    viewer.model.emoteController.playHeadMotion(motion);
    setDemoState(label);
    window.setTimeout(() => setDemoState(''), 1600);
  };

  const handleSave = async () => {
    if (!selected) return;
    setSaveState('saving');
    try {
      const result = await saveTeacherPreference(selected);
      setSavedId(selected.id);
      setSaveState(result.synced ? 'saved' : 'local');
    } catch {
      setSaveState('error');
    }
  };

  return (
    <main
      className="min-h-screen w-full overflow-auto bg-[#35117f] px-5 py-6 text-white sm:px-8"
      style={{ backgroundImage: 'radial-gradient(circle at 70% 10%, rgba(69, 160, 255, .48), transparent 30%), linear-gradient(135deg, #861FCE 0%, #5D24CE 48%, #21066D 100%)' }}
    >
      <div className="mx-auto max-w-7xl">
        <header className="mb-6 flex flex-wrap items-center justify-between gap-4">
          <div className="flex items-center gap-4">
            <button
              type="button"
              onClick={onBack}
              className="grid h-11 w-11 place-items-center rounded-full border border-white/20 bg-white/10 transition hover:bg-white/20"
              aria-label="返回首页"
            >
              <ArrowLeft size={21} />
            </button>
            <div>
              <p className="text-sm font-semibold tracking-wider text-purple-200">M8 · 虚拟 AI 教师</p>
              <h1 className="text-2xl font-black sm:text-3xl">选择你的大学生助教</h1>
            </div>
          </div>
          <div className="rounded-full border border-white/15 bg-white/10 px-4 py-2 text-sm text-purple-100 backdrop-blur-xl">
            VRM 3D · 口型同步 · 表情动画
          </div>
        </header>

        <section className="grid min-h-[620px] gap-6 lg:grid-cols-[minmax(0,1.25fr)_minmax(360px,.75fr)]">
          <div className="relative min-h-[500px] overflow-hidden rounded-[32px] border border-white/20 bg-gradient-to-b from-white/15 to-indigo-950/25 shadow-2xl backdrop-blur-xl">
            <div className="absolute left-6 top-6 z-10 max-w-xs rounded-2xl border border-white/15 bg-indigo-950/35 p-4 backdrop-blur-xl">
              <div className="mb-1 flex items-center gap-2 text-xl font-black">
                <Sparkles size={18} className="text-amber-300" />
                {selected?.name}
              </div>
              <p className="text-sm leading-6 text-purple-100/80">{selected?.description}</p>
            </div>
            {selected?.modelUrl && (
              <VirtualTeacherViewer
                key={selected.id}
                modelUrl={selected.modelUrl}
                onReady={handleViewerReady}
              />
            )}
            <div className="pointer-events-none absolute inset-x-0 bottom-0 h-32 bg-gradient-to-t from-indigo-950/70 to-transparent" />
            <div className="absolute bottom-6 left-6 right-6 flex flex-wrap gap-3 text-xs text-white/85">
              <span className="rounded-full bg-white/10 px-3 py-2 backdrop-blur-md"><Volume2 size={14} className="mr-1 inline" />{selected?.voiceType}</span>
              <span className="rounded-full bg-white/10 px-3 py-2 backdrop-blur-md"><Glasses size={14} className="mr-1 inline" />支持课堂互动</span>
            </div>
          </div>

          <div className="flex flex-col rounded-[32px] border border-white/20 bg-indigo-950/25 p-5 shadow-2xl backdrop-blur-xl sm:p-6">
            <div className="mb-5">
              <h2 className="text-xl font-black">教师形象</h2>
              <p className="mt-1 text-sm text-purple-100/65">选择后会同步到讲课页和互动答疑组件。</p>
            </div>
            <div className="space-y-3">
              {avatars.map((avatar) => {
                const selectedNow = avatar.id === selectedId;
                return (
                  <button
                    key={avatar.id}
                    type="button"
                    onClick={() => {
                      setViewer(null);
                      setSelectedId(avatar.id);
                      setSaveState('idle');
                    }}
                    className={`flex w-full items-center gap-4 rounded-2xl border p-3 text-left transition ${
                      selectedNow
                        ? 'border-cyan-300 bg-white/20 shadow-lg shadow-cyan-500/10'
                        : 'border-white/10 bg-white/[.07] hover:border-white/25 hover:bg-white/10'
                    }`}
                  >
                    <span className={`grid h-14 w-14 shrink-0 place-items-center rounded-2xl bg-gradient-to-br ${avatar.color} text-xl font-black shadow-lg`}>
                      {avatar.name.slice(0, 1)}
                    </span>
                    <span className="min-w-0 flex-1">
                      <span className="flex items-center gap-2 font-bold">
                        {avatar.name}
                        {savedId === avatar.id && <Check size={16} className="text-cyan-300" />}
                      </span>
                      <span className="mt-1 block truncate text-xs text-purple-100/60">{avatar.description}</span>
                    </span>
                    <span className={`h-4 w-4 rounded-full border-2 ${selectedNow ? 'border-cyan-300 bg-cyan-300 shadow-[0_0_12px_#67e8f9]' : 'border-white/35'}`} />
                  </button>
                );
              })}
            </div>

            <div className="mt-5 rounded-2xl border border-white/10 bg-white/[.06] p-4">
              <div className="mb-3 flex items-center justify-between">
                <div>
                  <h3 className="text-sm font-bold">互动表现预览</h3>
                  <p className="mt-0.5 text-xs text-purple-100/55">
                    {demoState || (viewer ? '点击体验表情与教学动作' : '模型加载完成后可体验')}
                  </p>
                </div>
                <span className={`h-2.5 w-2.5 rounded-full ${viewer ? 'bg-emerald-300 shadow-[0_0_10px_#6ee7b7]' : 'bg-white/25'}`} />
              </div>
              <div className="grid grid-cols-3 gap-2">
                <button
                  type="button"
                  disabled={!viewer}
                  onClick={() => playDemo('happy', 'smallNod', '正在展示：微笑鼓励')}
                  className="flex flex-col items-center gap-1.5 rounded-xl border border-white/10 bg-white/[.07] px-2 py-3 text-xs font-semibold transition hover:border-fuchsia-300/60 hover:bg-white/15 disabled:cursor-wait disabled:opacity-40"
                >
                  <Smile size={19} className="text-fuchsia-200" />
                  微笑
                </button>
                <button
                  type="button"
                  disabled={!viewer}
                  onClick={() => playDemo('relaxed', 'tiltHead', '正在展示：好奇思考')}
                  className="flex flex-col items-center gap-1.5 rounded-xl border border-white/10 bg-white/[.07] px-2 py-3 text-xs font-semibold transition hover:border-cyan-300/60 hover:bg-white/15 disabled:cursor-wait disabled:opacity-40"
                >
                  <Brain size={19} className="text-cyan-200" />
                  思考
                </button>
                <button
                  type="button"
                  disabled={!viewer}
                  onClick={() => playDemo('happy', 'bigNod', '正在展示：重点强调')}
                  className="flex flex-col items-center gap-1.5 rounded-xl border border-white/10 bg-white/[.07] px-2 py-3 text-xs font-semibold transition hover:border-amber-300/60 hover:bg-white/15 disabled:cursor-wait disabled:opacity-40"
                >
                  <Megaphone size={19} className="text-amber-200" />
                  强调
                </button>
              </div>
            </div>

            <div className="mt-auto pt-6">
              {saveState === 'local' && (
                <p className="mb-3 flex items-center gap-2 text-xs text-amber-200">
                  <CloudOff size={15} /> 后端接口尚未连通，选择已保存在当前浏览器。
                </p>
              )}
              {saveState === 'error' && <p className="mb-3 text-xs text-rose-200">保存失败，请重新登录后再试。</p>}
              <button
                type="button"
                onClick={handleSave}
                disabled={!selected || saveState === 'saving'}
                className="w-full rounded-2xl bg-gradient-to-r from-cyan-400 to-blue-500 px-5 py-3.5 font-black text-indigo-950 shadow-xl transition hover:-translate-y-0.5 hover:shadow-cyan-400/25 disabled:cursor-wait disabled:opacity-60"
              >
                {saveState === 'saving' ? '正在保存…' : savedId === selected?.id ? '已设为我的虚拟教师' : '使用这个形象'}
              </button>
            </div>
          </div>
        </section>
      </div>
    </main>
  );
}
