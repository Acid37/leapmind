import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  ArrowLeft,
  Brain,
  Check,
  ChevronLeft,
  ChevronRight,
  CloudOff,
  Glasses,
  Loader2,
  Megaphone,
  Play,
  Send,
  Smile,
  Sparkles,
  UserRound,
  Volume2,
  X,
} from 'lucide-react';
import VirtualTeacherViewer from '@/components/virtualTeacher/VirtualTeacherViewer.jsx';
import {
  DEFAULT_TEACHER_AVATARS,
  askVirtualTeacherQuestion,
  fetchVirtualTeacherLesson,
  fetchTeacherAvatars,
  fetchTeacherPreference,
  saveTeacherPreference,
  synthesizeVirtualTeacherSpeech,
} from '@/services/virtualTeacherService.js';
import { recordQuestionContext } from '@/services/learningProfileService.js';
import { getUserInfo } from '@/utils/tokenManager.js';

const DEMO_LESSONS = [
  {
    id: 'limit',
    title: '函数极限',
    tag: '高等数学',
    content: '今天我们先抓住极限的直觉：当自变量无限靠近某个点时，函数值会不会稳定靠近一个确定的数。先看图像趋势，再回到定义。',
    prompt: '为什么极限存在不要求函数在该点一定有定义？',
    slides: [
      {
        title: '函数极限的直觉',
        subtitle: '当 x 靠近 a 时，f(x) 靠近谁',
        points: ['关注“靠近”的趋势', '不先要求 x=a 处有值', '用图像观察左右两侧'],
        script: '这一页我们先建立极限的直觉。极限不是问函数在某一点实际等于多少，而是问当自变量越来越靠近这个点时，函数值会不会稳定靠近一个确定的数。',
      },
      {
        title: '判断极限是否存在',
        subtitle: '左极限和右极限必须一致',
        points: ['左侧靠近得到一个趋势', '右侧靠近得到一个趋势', '两个趋势相同，极限才存在'],
        script: '判断极限是否存在时，核心是比较左右两边的趋势。如果从左边靠近和从右边靠近得到的是同一个数，我们才说这个极限存在。',
      },
    ],
  },
  {
    id: 'physics',
    title: '牛顿第二定律',
    tag: '大学物理',
    content: '力不是让物体保持运动的原因，而是改变运动状态的原因。合外力越大，加速度越大；质量越大，同样的力产生的加速度越小。',
    prompt: '如果物体速度恒定，合外力一定是多少？',
    slides: [
      {
        title: 'F = ma',
        subtitle: '合外力决定加速度',
        points: ['力改变运动状态', '质量体现惯性大小', '方向与合外力方向一致'],
        script: '这一页我们看牛顿第二定律。公式 F 等于 m 乘 a，真正表达的是合外力、质量和加速度之间的关系。',
      },
      {
        title: '速度恒定意味着什么',
        subtitle: '加速度为零，合外力为零',
        points: ['速度大小不变', '速度方向不变', '物体处于平衡状态'],
        script: '如果一个物体速度恒定，说明速度大小和方向都没有变化，因此加速度为零。根据牛顿第二定律，合外力也应该为零。',
      },
    ],
  },
  {
    id: 'english',
    title: 'Academic Writing',
    tag: '大学英语',
    content: '一段好的学术表达通常先给出主题句，再用证据支撑观点，最后解释证据和观点之间的关系。不要只堆例子，要写出推理链。',
    prompt: '主题句和结论句有什么区别？',
    slides: [
      {
        title: 'Paragraph Structure',
        subtitle: 'Claim, evidence, explanation',
        points: ['Topic sentence gives the claim', 'Evidence supports the claim', 'Explanation connects them'],
        script: 'A good academic paragraph usually starts with a clear topic sentence, then gives evidence, and finally explains why the evidence supports the claim.',
      },
      {
        title: 'Avoid Evidence Dumping',
        subtitle: 'Examples are not enough',
        points: ['Do not list examples only', 'Explain the reasoning chain', 'End with a focused conclusion'],
        script: 'Examples alone are not enough in academic writing. You need to explain the reasoning chain, so readers understand how your evidence proves your point.',
      },
    ],
  },
];

export default function TeacherAvatarPage({ courseId = '', onBack }) {
  const [avatars, setAvatars] = useState(DEFAULT_TEACHER_AVATARS);
  const [selectedId, setSelectedId] = useState(DEFAULT_TEACHER_AVATARS[0].id);
  const [savedId, setSavedId] = useState(null);
  const [saveState, setSaveState] = useState('idle');
  const [viewer, setViewer] = useState(null);
  const [demoState, setDemoState] = useState('');
  const [activeLessonId, setActiveLessonId] = useState(DEMO_LESSONS[0].id);
  const [lessons, setLessons] = useState(DEMO_LESSONS);
  const [lessonSource, setLessonSource] = useState(courseId ? 'loading' : 'demo');
  const [question, setQuestion] = useState('');
  const [messages, setMessages] = useState([
    { role: 'teacher', text: '我已经准备好讲解了。你可以先点“开始讲解”，也可以直接问一个问题。' },
  ]);
  const [speechState, setSpeechState] = useState('idle');
  const [askState, setAskState] = useState('idle');
  const [currentSlideIndex, setCurrentSlideIndex] = useState(0);
  const [showAvatarPanel, setShowAvatarPanel] = useState(false);

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

  useEffect(() => {
    let active = true;
    if (!courseId) {
      setLessons(DEMO_LESSONS);
      setActiveLessonId(DEMO_LESSONS[0].id);
      setLessonSource('demo');
      return () => {
        active = false;
      };
    }

    setLessonSource('loading');
    fetchVirtualTeacherLesson(courseId)
      .then((lesson) => {
        if (!active) return;
        if (lesson) {
          setLessons([lesson]);
          setActiveLessonId(lesson.id);
          setLessonSource('api');
        } else {
          setLessons(DEMO_LESSONS);
          setActiveLessonId(DEMO_LESSONS[0].id);
          setLessonSource('empty');
        }
      })
      .catch(() => {
        if (!active) return;
        setLessons(DEMO_LESSONS);
        setActiveLessonId(DEMO_LESSONS[0].id);
        setLessonSource('error');
      });
    return () => {
      active = false;
    };
  }, [courseId]);

  const selected = useMemo(
    () => avatars.find((avatar) => avatar.id === selectedId) ?? avatars[0],
    [avatars, selectedId],
  );

  const activeLesson = useMemo(
    () => lessons.find((lesson) => lesson.id === activeLessonId) ?? lessons[0] ?? DEMO_LESSONS[0],
    [activeLessonId, lessons],
  );

  const currentSlide = activeLesson.slides[currentSlideIndex] ?? activeLesson.slides[0];
  const teachingSlide = useMemo(
    () => ({
      ...currentSlide,
      tag: activeLesson.tag,
      page: currentSlideIndex + 1,
      totalPages: activeLesson.slides.length,
    }),
    [activeLesson, currentSlide, currentSlideIndex],
  );

  useEffect(() => {
    setCurrentSlideIndex(0);
  }, [activeLessonId]);

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

  const speakText = async (text, label = '正在讲解') => {
    if (!text) return;
    setSpeechState('speaking');
    setDemoState(label);
    viewer?.model?.emoteController?.playEmotion('happy');
    viewer?.model?.emoteController?.playHeadMotion('smallNod');

    try {
      const result = await synthesizeVirtualTeacherSpeech({
        courseId: activeLesson.id,
        text,
        voiceType: selected?.voiceType,
      });
      if (result?.audioBlob) {
        const audio = new Audio(URL.createObjectURL(result.audioBlob));
        audio.onended = () => setSpeechState('idle');
        audio.onerror = () => setSpeechState('idle');
        await audio.play();
      } else {
        window.setTimeout(() => setSpeechState('idle'), 1400);
      }
    } catch {
      setMessages((prev) => [
        ...prev,
        { role: 'system', text: '语音接口暂时不可用，已保留 3D 教师动作演示。' },
      ]);
      window.setTimeout(() => setSpeechState('idle'), 1400);
    } finally {
      window.setTimeout(() => setDemoState(''), 1600);
    }
  };

  const handleTeach = () => {
    const script = currentSlide?.script || activeLesson.content;
    setMessages((prev) => [
      ...prev,
      { role: 'teacher', text: script },
    ]);
    speakText(script, '正在讲解：' + currentSlide.title);
  };

  const handleAsk = async () => {
    if (askState === 'asking') return;
    const text = question.trim() || activeLesson.prompt;
    const user = getUserInfo();
    setQuestion('');
    setAskState('asking');
    setMessages((prev) => [
      ...prev,
      { role: 'student', text },
    ]);
    void recordQuestionContext({
      userId: user?.id ?? user?.userId ?? user?.uid,
      courseId: activeLesson.id,
      chapterId: currentSlide.id || `slide-${currentSlideIndex + 1}`,
    });

    try {
      const response = await askVirtualTeacherQuestion({
        courseId: activeLesson.id,
        question: text,
      });
      setMessages((prev) => [
        ...prev,
        { role: 'teacher', text: response.answer },
      ]);
      void speakText(response.answer, '正在答疑');
    } catch (error) {
      setMessages((prev) => [
        ...prev,
        { role: 'system', text: error?.message || 'AI 教师暂时无法回答，请稍后重试。' },
      ]);
    } finally {
      setAskState('idle');
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

        <section className="relative left-1/2 w-[calc(100vw-2.5rem)] max-w-[1500px] -translate-x-1/2 sm:w-[calc(100vw-4rem)]">
          <div className="relative h-[76vh] min-h-[620px] max-h-[840px] overflow-hidden rounded-[32px] border border-white/20 bg-gradient-to-b from-white/15 to-indigo-950/25 shadow-2xl backdrop-blur-xl">
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
                teachingSlide={teachingSlide}
                onReady={handleViewerReady}
              />
            )}
            <button
              type="button"
              onClick={() => setShowAvatarPanel(true)}
              className="absolute right-6 top-6 z-10 inline-flex items-center gap-2 rounded-2xl border border-white/20 bg-indigo-950/45 px-4 py-2.5 text-sm font-bold text-white shadow-lg backdrop-blur-xl transition hover:bg-indigo-900/70"
            >
              <UserRound size={17} />
              教师形象
            </button>
            <div className="absolute bottom-20 right-5 z-10 rounded-2xl border border-white/15 bg-indigo-950/45 px-3 py-2 backdrop-blur-xl">
              <div className="flex items-center gap-3">
                <button
                  type="button"
                  onClick={() => setCurrentSlideIndex((prev) => Math.max(prev - 1, 0))}
                  disabled={currentSlideIndex === 0}
                  className="grid h-9 w-9 place-items-center rounded-full border border-white/15 bg-white/10 transition hover:bg-white/20 disabled:opacity-35"
                  aria-label="上一页 PPT"
                >
                  <ChevronLeft size={18} />
                </button>
                <span className="min-w-20 text-center text-xs font-bold text-purple-50">
                  3D 课件 {currentSlideIndex + 1}/{activeLesson.slides.length}
                </span>
                <button
                  type="button"
                  onClick={() => setCurrentSlideIndex((prev) => Math.min(prev + 1, activeLesson.slides.length - 1))}
                  disabled={currentSlideIndex === activeLesson.slides.length - 1}
                  className="grid h-9 w-9 place-items-center rounded-full border border-white/15 bg-white/10 transition hover:bg-white/20 disabled:opacity-35"
                  aria-label="下一页 PPT"
                >
                  <ChevronRight size={18} />
                </button>
              </div>
            </div>
            <div className="pointer-events-none absolute inset-x-0 bottom-0 h-32 bg-gradient-to-t from-indigo-950/70 to-transparent" />
            <div className="absolute bottom-6 left-6 right-6 flex flex-wrap gap-3 text-xs text-white/85">
              <span className="rounded-full bg-white/10 px-3 py-2 backdrop-blur-md"><Volume2 size={14} className="mr-1 inline" />{selected?.voiceType}</span>
              <span className="rounded-full bg-white/10 px-3 py-2 backdrop-blur-md"><Glasses size={14} className="mr-1 inline" />支持课堂互动</span>
            </div>
          </div>

          {showAvatarPanel && (
            <div
              className="fixed inset-0 z-50 flex justify-end bg-indigo-950/60 p-3 backdrop-blur-sm sm:p-6"
              role="presentation"
              onMouseDown={(event) => {
                if (event.target === event.currentTarget) setShowAvatarPanel(false);
              }}
            >
              <aside
                className="flex h-full w-full max-w-md flex-col overflow-y-auto rounded-[32px] border border-white/20 bg-[#2b1175]/95 p-5 text-white shadow-2xl sm:p-6"
                aria-label="教师形象设置"
              >
                <div className="mb-5 flex items-start justify-between gap-4">
                  <div>
                    <h2 className="text-xl font-black">教师形象</h2>
                    <p className="mt-1 text-sm text-purple-100/65">选择后会同步到讲课页和互动答疑组件。</p>
                  </div>
                  <button
                    type="button"
                    onClick={() => setShowAvatarPanel(false)}
                    className="grid h-10 w-10 shrink-0 place-items-center rounded-full border border-white/15 bg-white/10 transition hover:bg-white/20"
                    aria-label="关闭教师形象设置"
                  >
                    <X size={19} />
                  </button>
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
                onClick={async () => {
                  await handleSave();
                  setShowAvatarPanel(false);
                }}
                disabled={!selected || saveState === 'saving'}
                className="w-full rounded-2xl bg-gradient-to-r from-cyan-400 to-blue-500 px-5 py-3.5 font-black text-indigo-950 shadow-xl transition hover:-translate-y-0.5 hover:shadow-cyan-400/25 disabled:cursor-wait disabled:opacity-60"
              >
                {saveState === 'saving' ? '正在保存…' : savedId === selected?.id ? '已设为我的虚拟教师' : '使用这个形象'}
              </button>
            </div>
              </aside>
            </div>
          )}
        </section>

        <section className="mt-6 flex flex-col gap-6">
          <div className="order-2 rounded-[28px] border border-white/20 bg-white/[.09] p-5 shadow-2xl backdrop-blur-xl sm:p-6">
            <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
              <div>
                <p className="text-sm font-semibold text-cyan-100/70">教师讲课</p>
                <h2 className="text-2xl font-black">{currentSlide.title}</h2>
              </div>
              <button
                type="button"
                onClick={handleTeach}
                disabled={speechState === 'speaking'}
                className="inline-flex items-center gap-2 rounded-2xl bg-amber-300 px-5 py-3 font-black text-indigo-950 shadow-lg transition hover:-translate-y-0.5 disabled:cursor-wait disabled:opacity-70"
              >
                {speechState === 'speaking' ? <Loader2 size={18} className="animate-spin" /> : <Play size={18} />}
                开始讲解
              </button>
            </div>
            <div className="mb-5 flex flex-wrap gap-2">
              {lessons.map((lesson) => (
                <button
                  key={lesson.id}
                  type="button"
                  onClick={() => setActiveLessonId(lesson.id)}
                  className={`rounded-full border px-4 py-2 text-sm font-bold transition ${
                    lesson.id === activeLesson.id
                      ? 'border-cyan-300 bg-cyan-300 text-indigo-950'
                      : 'border-white/15 bg-white/[.06] text-purple-50 hover:bg-white/15'
                  }`}
                >
                  {lesson.tag}
                </button>
              ))}
              <span className={`rounded-full border px-3 py-2 text-xs font-bold ${
                lessonSource === 'api'
                  ? 'border-emerald-300/40 bg-emerald-300/15 text-emerald-100'
                  : lessonSource === 'loading'
                    ? 'border-cyan-300/40 bg-cyan-300/15 text-cyan-100'
                    : 'border-amber-300/40 bg-amber-300/15 text-amber-100'
              }`}>
                {lessonSource === 'api'
                  ? `正式课程 · ${courseId}`
                  : lessonSource === 'loading'
                    ? '正在加载正式课件'
                    : lessonSource === 'empty'
                      ? '该课程暂无课件 · 当前为演示'
                      : lessonSource === 'error'
                        ? '课件接口不可用 · 当前为演示'
                        : '演示课件'}
              </span>
            </div>
            <p className="rounded-3xl border border-white/10 bg-indigo-950/25 p-5 text-base leading-8 text-purple-50/90">
              {currentSlide.script}
            </p>
            <div className="mt-4 rounded-2xl border border-cyan-200/20 bg-cyan-300/10 p-4 text-sm text-cyan-50">
              课堂引导：{activeLesson.prompt}
            </div>
          </div>

          <div className="order-1 min-h-[300px] rounded-[28px] border border-white/20 bg-indigo-950/25 p-5 shadow-2xl backdrop-blur-xl sm:p-6">
            <div className="mb-4">
              <p className="text-sm font-semibold text-cyan-100/70">互动提问</p>
              <h2 className="text-2xl font-black">提问与反馈</h2>
            </div>
            <div className="max-h-72 space-y-3 overflow-y-auto pr-1">
              {messages.map((message, index) => (
                <div
                  key={`${message.role}-${index}`}
                  className={`rounded-2xl border p-3 text-sm leading-6 ${
                    message.role === 'student'
                      ? 'ml-8 border-cyan-200/20 bg-cyan-300/15 text-cyan-50'
                      : message.role === 'system'
                        ? 'border-amber-200/20 bg-amber-300/10 text-amber-100'
                        : 'mr-8 border-white/10 bg-white/[.07] text-purple-50'
                  }`}
                >
                  {message.text}
                </div>
              ))}
            </div>
            <div className="mt-4 flex gap-2">
              <input
                value={question}
                onChange={(event) => setQuestion(event.target.value)}
                onKeyDown={(event) => {
                  if (event.key === 'Enter' && !event.nativeEvent.isComposing) handleAsk();
                }}
                placeholder={activeLesson.prompt}
                className="min-w-0 flex-1 rounded-2xl border border-white/15 bg-white/[.08] px-4 py-3 text-sm text-white outline-none placeholder:text-purple-100/45 focus:border-cyan-300"
              />
              <button
                type="button"
                onClick={handleAsk}
                disabled={askState === 'asking'}
                className="grid h-12 w-12 place-items-center rounded-2xl bg-cyan-300 text-indigo-950 transition hover:-translate-y-0.5 disabled:cursor-wait disabled:opacity-70"
                aria-label="发送问题"
              >
                {askState === 'asking' ? <Loader2 size={18} className="animate-spin" /> : <Send size={18} />}
              </button>
            </div>
          </div>
        </section>
      </div>
    </main>
  );
}
