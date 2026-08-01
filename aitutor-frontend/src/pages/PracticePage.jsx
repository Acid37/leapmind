/**
 * 做题页（M1 核心页面 · 5.2.2）
 *
 * 功能：
 * - 题目展示（单选/多选/填空/简答）
 * - 选项选择 → 提交 → 即时评判
 * - 进度条 + 正确/错误/未答统计
 * - 单题计时
 * - 侧边题目导航（可跳转）
 * - 练习结束总结面板
 * - 预留 ChatPanel 嵌入位（TODO: 对接 M7 ChatPanel）
 */
import { useState, useEffect, useCallback } from "react";
import {
  ArrowLeft,
  ArrowRight,
  Check,
  Send,
  MessageCircle,
  Flag,
  RotateCcw,
  Trophy,
  BarChart3,
  Play,
  Gift,
  CalendarCheck,
  ListChecks,
  Shuffle,
} from "lucide-react";
import QuestionCard from "../components/practice/QuestionCard";
import ProgressBar from "../components/practice/ProgressBar";
import Timer from "../components/practice/Timer";
import QuestionNav from "../components/practice/QuestionNav";
import { generateSession, submitAnswer, dailyCheckin, getCheckinStatus, getFilterOptions } from "../services/practiceService";
import { ChatPanel } from "../components/chat";
import { getUserInfo } from "../utils/tokenManager";

const SESSION_KEY = "m1_practice_session";
const QUICK_COUNTS = [5, 10, 15, 20];

export default function PracticePage({ onBack, embedded = false, mode = "FREE_PRACTICE", lessonId = "", initialParams = {} }) {
  // --- ChatPanel 状态 ---
  const [chatPanelOpen, setChatPanelOpen] = useState(false);
  const userInfo = getUserInfo();

  // --- 签到状态 ---
  const [checkinStatus, setCheckinStatus] = useState(null);
  const [checkinLoading, setCheckinLoading] = useState(false);

  // --- 会话状态 ---
  const [session, setSession] = useState(null);
  const [currentIndex, setCurrentIndex] = useState(0);
  const [answers, setAnswers] = useState({});
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [hasSavedSession, setHasSavedSession] = useState(false);
  const [showSetup, setShowSetup] = useState(false);
  const [setupError, setSetupError] = useState("");
  const [availableSubjects, setAvailableSubjects] = useState([]);
  const [setup, setSetup] = useState({
    questionCount: Math.max(1, Math.min(50, Number(initialParams.questionCount) || 10)),
    subject: initialParams.subject || "mixed",
  });

  // --- 持久化：每次状态变化写入 localStorage ---
  useEffect(() => {
    if (session && Object.keys(answers).length > 0) {
      try {
        localStorage.setItem(SESSION_KEY, JSON.stringify({
          session,
          currentIndex,
          answers,
        }));
      } catch { /* quota exceeded, ignore */ }
    }
  }, [session, currentIndex, answers]);

  // --- 初始化会话 ---
  useEffect(() => {
    // 检查是否有未完成的会话
    try {
      const saved = localStorage.getItem(SESSION_KEY);
      if (saved) {
        const parsed = JSON.parse(saved);
        if (parsed.session?.questions?.length > 0) {
          setHasSavedSession(true);
          setSession(parsed.session);
          setCurrentIndex(parsed.currentIndex || 0);
          setAnswers(parsed.answers || {});
          setLoading(false);
          return;
        }
      }
    } catch { /* corrupted, ignore */ }
    // 没有保存的会话，先由用户选择题量和科目
    setShowSetup(true);
    setLoading(false);
  }, []);

  useEffect(() => {
    getFilterOptions()
      .then((options) => setAvailableSubjects(options.subjects || []))
      .catch((err) => console.warn("加载科目失败:", err));
  }, []);

  const initSession = async () => {
    const questionCount = Number(setup.questionCount);
    if (!Number.isInteger(questionCount) || questionCount < 1 || questionCount > 50) {
      setSetupError("题目数量需要在 1 到 50 之间");
      return;
    }
    setLoading(true);
    setHasSavedSession(false);
    setShowSetup(false);
    setSetupError("");
    localStorage.removeItem(SESSION_KEY);
    try {
      const data = await generateSession({
        ...initialParams,
        sceneType: "free_practice",
        questionCount,
        subject: setup.subject === "mixed" ? undefined : setup.subject,
        mode,
        lessonId: lessonId || undefined,
      });
      const subjectLabel = setup.subject === "mixed"
        ? "混合科目"
        : availableSubjects.find((item) => item.value === setup.subject)?.label || setup.subject;
      setSession({ ...data, setup: { questionCount: data.questions.length, requestedCount: questionCount, subject: setup.subject, subjectLabel } });
      setCurrentIndex(0);
      const initial = {};
      data.questions.forEach((q) => {
        initial[q.questionId] = {
          selectedAnswer: null,
          isCorrect: null,
          timeSpent: 0,
          submitted: false,
        };
      });
      setAnswers(initial);
    } catch (err) {
      console.error("生成会话失败:", err);
      setSetupError(err.message || "生成练习失败，请调整条件后重试");
      setShowSetup(true);
    } finally {
      setLoading(false);
    }
  };

  const openSessionSetup = () => {
    localStorage.removeItem(SESSION_KEY);
    setHasSavedSession(false);
    setSession(null);
    setCurrentIndex(0);
    setAnswers({});
    setSetupError("");
    setShowSetup(true);
    setLoading(false);
  };

  // 继续上次会话
  const handleContinueSession = () => {
    setHasSavedSession(false);
    setLoading(false);
  };

  // --- 签到 ---
  useEffect(() => {
    getCheckinStatus().then(status => setCheckinStatus(status));
  }, []);

  const handleCheckin = async () => {
    setCheckinLoading(true);
    try {
      const result = await dailyCheckin();
      if (result.alreadyChecked) {
        setCheckinStatus(prev => ({ ...prev, checkedToday: true, streakDays: result.streakDays || prev?.streakDays || 0 }));
      } else {
        setCheckinStatus(prev => ({
          checkedToday: true,
          streakDays: (prev?.streakDays || 0) + 1,
          totalPoints: (prev?.totalPoints || 0) + (result.pointsEarned || 0),
        }));
      }
    } catch (err) {
      console.warn('签到失败:', err);
    } finally {
      setCheckinLoading(false);
    }
  };

  const currentQuestion = session?.questions?.[currentIndex];
  const currentAnswer = currentQuestion
    ? answers[currentQuestion.questionId]
    : null;

  // --- 选择答案 ---
  const handleSelectAnswer = useCallback(
    (answer) => {
      if (!currentQuestion || currentAnswer?.submitted) return;
      setAnswers((prev) => ({
        ...prev,
        [currentQuestion.questionId]: {
          ...prev[currentQuestion.questionId],
          selectedAnswer: answer,
        },
      }));
    },
    [currentQuestion, currentAnswer]
  );

  // --- 提交答案 ---
  const handleSubmit = async () => {
    if (!currentQuestion || !currentAnswer?.selectedAnswer || submitting) return;
    setSubmitting(true);
    try {
      const result = await submitAnswer({
        questionId: currentQuestion.questionId,
        _originalId: currentQuestion._originalId,
        answer: currentAnswer.selectedAnswer,
        timeSpent: currentAnswer.timeSpent,
        mode,
      });
      setAnswers((prev) => ({
        ...prev,
        [currentQuestion.questionId]: {
          ...prev[currentQuestion.questionId],
          isCorrect: result.isCorrect,
          submitted: true,
          result,
        },
      }));
    } catch (err) {
      console.error("提交失败:", err);
    } finally {
      setSubmitting(false);
    }
  };

  // --- 下一题 ---
  const handleNext = () => {
    if (currentIndex < session.questions.length - 1) {
      setCurrentIndex((i) => i + 1);
    }
  };

  // --- 跳转题目 ---
  const handleJump = (index) => {
    setCurrentIndex(index);
  };

  // --- 计时回调 ---
  const handleTick = (s) => {
    if (!currentQuestion) return;
    setAnswers((prev) => ({
      ...prev,
      [currentQuestion.questionId]: {
        ...prev[currentQuestion.questionId],
        timeSpent: s,
      },
    }));
  };

  // --- 计算统计 ---
  const answerStatuses = session?.questions?.map(
    (q) => {
      const a = answers[q.questionId];
      if (!a?.submitted) return null;
      return a.isCorrect ? "correct" : "wrong";
    }
  ) || [];
  const totalCorrect = answerStatuses.filter((s) => s === "correct").length;
  const totalWrong = answerStatuses.filter((s) => s === "wrong").length;
  const allDone = answerStatuses.every((s) => s !== null);
  const score = session ? Math.round((totalCorrect / session.questions.length) * 100) : 0;

  // --- 加载中 ---
  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="flex flex-col items-center gap-3">
          <div className="w-8 h-8 border-2 border-indigo-400 border-t-transparent rounded-full animate-spin" />
          <span className="text-sm text-slate-500">正在生成题目...</span>
        </div>
      </div>
    );
  }

  // --- 新练习配置 ---
  if (showSetup || !session) {
    return (
      <PracticeSetup
        setup={setup}
        setSetup={setSetup}
        subjects={availableSubjects}
        error={setupError}
        onStart={initSession}
      />
    );
  }

  // --- 恢复会话提示 ---
  if (hasSavedSession) {
    const doneCount = Object.values(answers).filter((a) => a?.submitted).length;
    return (
      <div className="max-w-md mx-auto py-16 px-4">
        <div className="bg-white rounded-2xl shadow-sm border border-slate-100 p-8 text-center">
          <div className="w-16 h-16 mx-auto mb-4 rounded-full bg-amber-50 flex items-center justify-center">
            <RotateCcw size={30} className="text-amber-500" />
          </div>
          <h2 className="text-xl font-bold text-slate-800 mb-2">发现未完成的练习</h2>
          <p className="text-sm text-slate-500 mb-6">
            你上次做到了第 {currentIndex + 1} 题，已完成 {doneCount}/{session.questions.length} 题
          </p>
          <div className="flex gap-3 justify-center">
            <button
              onClick={handleContinueSession}
              className="px-5 py-2.5 bg-indigo-500 text-white rounded-xl font-medium hover:bg-indigo-600 transition-colors flex items-center gap-2 cursor-pointer"
            >
              <Play size={16} /> 继续做题
            </button>
            <button
              onClick={openSessionSetup}
              className="px-5 py-2.5 bg-white border border-slate-200 text-slate-600 rounded-xl font-medium hover:bg-slate-50 transition-colors flex items-center gap-2 cursor-pointer"
            >
              <Check size={16} /> 重新开始
            </button>
          </div>
        </div>
      </div>
    );
  }

  // --- 完成页 ---
  if (allDone) {
    localStorage.removeItem(SESSION_KEY);
    return (
      <div className="max-w-lg mx-auto py-10 px-4">
        <div className="bg-white rounded-2xl shadow-sm border border-slate-100 p-8 text-center">
          {/* 大图标 */}
          <div className="w-20 h-20 mx-auto mb-4 rounded-full bg-indigo-50 flex items-center justify-center">
            <Trophy size={36} className="text-indigo-500" />
          </div>

          <h2 className="text-2xl font-bold text-slate-800 mb-1">练习完成！</h2>
          <p className="text-slate-500 mb-6">你已完成全部 {session.questions.length} 道题</p>

          {/* 成绩环形 */}
          <div className="w-32 h-32 mx-auto mb-6 relative">
            <svg viewBox="0 0 100 100" className="w-full h-full -rotate-90">
              <circle cx="50" cy="50" r="42" fill="none" stroke="#e2e8f0" strokeWidth="8" />
              <circle
                cx="50" cy="50" r="42" fill="none" stroke={score >= 60 ? "#10b981" : "#f43f5e"}
                strokeWidth="8" strokeLinecap="round"
                strokeDasharray={`${score * 2.64} 264`}
              />
            </svg>
            <div className="absolute inset-0 flex items-center justify-center">
              <span className="text-2xl font-bold text-slate-800">{score}%</span>
            </div>
          </div>

          {/* 统计卡片 */}
          <div className="grid grid-cols-3 gap-3 mb-6">
            <div className="bg-emerald-50 rounded-xl p-3">
              <div className="text-2xl font-bold text-emerald-600">{totalCorrect}</div>
              <div className="text-xs text-emerald-500">正确</div>
            </div>
            <div className="bg-red-50 rounded-xl p-3">
              <div className="text-2xl font-bold text-red-400">{totalWrong}</div>
              <div className="text-xs text-red-400">错误</div>
            </div>
            <div className="bg-indigo-50 rounded-xl p-3">
              <div className="text-2xl font-bold text-indigo-500">
                {session.questions.length}
              </div>
              <div className="text-xs text-indigo-400">总题数</div>
            </div>
          </div>

          {/* 操作按钮 */}
          <div className="flex gap-3 justify-center flex-wrap">
            <button
              onClick={openSessionSetup}
              className="px-5 py-2.5 bg-indigo-500 text-white rounded-xl font-medium hover:bg-indigo-600 transition-colors flex items-center gap-2 cursor-pointer"
            >
              <RotateCcw size={16} /> 再练一组
            </button>
            <button
              onClick={onBack}
              className="px-5 py-2.5 bg-white border border-slate-200 text-slate-600 rounded-xl font-medium hover:bg-slate-50 transition-colors flex items-center gap-2 cursor-pointer"
            >
              <BarChart3 size={16} /> 查看统计
            </button>
          </div>
        </div>
      </div>
    );
  }

  // --- 做题中 ---
  return (
    <div className="flex gap-5 h-full max-w-5xl mx-auto">
      {/* 主区域 */}
      <div className="flex-1 min-w-0 flex flex-col gap-4">
        {/* 顶部栏 */}
        <div className="flex items-center justify-between flex-wrap gap-2">
          {!embedded && onBack && (
            <button
              onClick={onBack}
              className="flex items-center gap-1.5 text-sm text-slate-500 hover:text-slate-700 transition-colors cursor-pointer"
            >
              <ArrowLeft size={16} /> 退出练习
            </button>
          )}
          <div className="flex items-center gap-3">
            {/* 签到按钮 */}
            {!checkinStatus?.checkedToday && (
              <button
                onClick={handleCheckin}
                disabled={checkinLoading}
                className="flex items-center gap-1 px-3 py-1.5 bg-amber-50 text-amber-600 rounded-lg text-xs font-medium hover:bg-amber-100 transition-colors disabled:opacity-50 cursor-pointer"
              >
                <Gift size={14} />
                {checkinLoading ? '签到中...' : '签到领积分'}
              </button>
            )}
            {checkinStatus?.checkedToday && (
              <span className="flex items-center gap-1 px-3 py-1.5 bg-emerald-50 text-emerald-600 rounded-lg text-xs font-medium">
                <CalendarCheck size={14} />
                已签到{checkinStatus.streakDays > 0 ? ` · 连签${checkinStatus.streakDays}天` : ''}
              </span>
            )}
            <Timer
              isRunning={!currentAnswer?.submitted}
              onTick={handleTick}
              resetKey={currentQuestion?.questionId}
            />
          </div>
        </div>

        {/* 进度条 */}
        <ProgressBar
          current={currentIndex + 1}
          total={session.questions.length}
          answerStatuses={answerStatuses}
        />

        {session.availabilityNotice && (
          <div className="rounded-xl border border-amber-200 bg-amber-50 px-4 py-2.5 text-sm text-amber-700">
            {session.availabilityNotice}
          </div>
        )}

        {/* 题目卡片 */}
        <QuestionCard
          question={currentQuestion}
          selectedAnswer={currentAnswer?.selectedAnswer}
          onSelectAnswer={handleSelectAnswer}
          isSubmitted={currentAnswer?.submitted}
          isCorrect={currentAnswer?.isCorrect}
        />

        {/* 底部操作栏 */}
        <div className="flex items-center justify-between gap-3 flex-wrap">
          {/* 左侧：上一题 */}
          <button
            disabled={currentIndex === 0}
            onClick={() => setCurrentIndex((i) => i - 1)}
            className="flex items-center gap-1 px-3 py-2 text-sm text-slate-500 hover:text-slate-700 disabled:opacity-30 transition-colors cursor-pointer"
          >
            <ArrowLeft size={16} /> 上一题
          </button>

          <div className="flex items-center gap-2">
            {/* 问 AI（ChatPanel） */}
            {currentAnswer?.submitted && (
              <button
                onClick={() => setChatPanelOpen((v) => !v)}
                className="flex items-center gap-1.5 px-4 py-2.5 bg-violet-50 text-violet-600 rounded-xl text-sm font-medium hover:bg-violet-100 transition-colors cursor-pointer"
              >
                <MessageCircle size={16} /> 问 AI
              </button>
            )}

            {/* 标记按钮 */}
            {!currentAnswer?.submitted && (
              <button className="p-2.5 text-slate-400 hover:text-amber-500 rounded-xl hover:bg-amber-50 transition-colors cursor-pointer">
                <Flag size={18} />
              </button>
            )}

            {/* 提交 / 下一题 按钮 */}
            {!currentAnswer?.submitted ? (
              <button
                disabled={!currentAnswer?.selectedAnswer || submitting}
                onClick={handleSubmit}
                className="flex items-center gap-1.5 px-5 py-2.5 bg-indigo-500 text-white rounded-xl text-sm font-semibold hover:bg-indigo-600 disabled:opacity-40 disabled:cursor-not-allowed transition-all cursor-pointer"
              >
                {submitting ? (
                  <>
                    <div className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin" />
                    提交中...
                  </>
                ) : (
                  <>
                    <Send size={16} /> 提交答案
                  </>
                )}
              </button>
            ) : (
              currentIndex < session.questions.length - 1 && (
                <button
                  onClick={handleNext}
                  className="flex items-center gap-1.5 px-5 py-2.5 bg-indigo-500 text-white rounded-xl text-sm font-semibold hover:bg-indigo-600 transition-colors cursor-pointer"
                >
                  下一题 <ArrowRight size={16} />
                </button>
              )
            )}

            {/* 最后一题答完 -> 再来一组 */}
            {currentAnswer?.submitted && currentIndex === session.questions.length - 1 && (
              <button
                onClick={openSessionSetup}
                className="flex items-center gap-1.5 px-5 py-2.5 bg-emerald-500 text-white rounded-xl text-sm font-semibold hover:bg-emerald-600 transition-colors cursor-pointer"
              >
                <Check size={16} /> 再来一组
              </button>
            )}
          </div>
        </div>
      </div>

      {/* 侧边栏：题目导航 */}
      <div className="hidden lg:block w-48 flex-shrink-0">
        <div className="sticky top-4 space-y-4">
          <QuestionNav
            total={session.questions.length}
            currentIndex={currentIndex}
            answerStatuses={answerStatuses}
            onJump={handleJump}
          />

          {/* 统计小结 */}
          <div className="bg-white rounded-2xl shadow-sm border border-slate-100 p-4">
            <h3 className="text-sm font-semibold text-slate-500 mb-3">答题统计</h3>
            <div className="space-y-2 text-sm">
              <div className="flex justify-between">
                <span className="text-slate-500">正确</span>
                <span className="font-semibold text-emerald-600">{totalCorrect}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-slate-500">错误</span>
                <span className="font-semibold text-red-400">{totalWrong}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-slate-500">未答</span>
                <span className="font-semibold text-slate-400">
                  {session.questions.length - totalCorrect - totalWrong}
                </span>
              </div>
              <hr className="border-slate-100" />
              <div className="flex justify-between">
                <span className="text-slate-500">正确率</span>
                <span className="font-semibold text-slate-700">
                  {session.questions.length > 0
                    ? Math.round((totalCorrect / (totalCorrect + totalWrong || 1)) * 100)
                    : 0}
                  %
                </span>
              </div>
            </div>
          </div>
        </div>
      </div>
      {/* ChatPanel 弹出层 */}
      {chatPanelOpen && (
        <div className="fixed inset-0 z-50 flex justify-end pointer-events-none">
          <div className="pointer-events-auto w-96 h-full shadow-2xl">
            <ChatPanel
              sceneType="doing_exercise"
              context={{ questionId: currentQuestion?.questionId }}
              userId={userInfo?.id}
              visible={chatPanelOpen}
              onClose={() => setChatPanelOpen(false)}
            />
          </div>
        </div>
      )}
    </div>
  );
}

function PracticeSetup({ setup, setSetup, subjects, error, onStart }) {
  const selectedSubjectLabel = setup.subject === "mixed"
    ? "混合科目"
    : subjects.find((item) => item.value === setup.subject)?.label || setup.subject;

  return (
    <div className="mx-auto max-w-2xl px-4 py-10">
      <div className="rounded-3xl border border-slate-100 bg-white p-7 shadow-sm md:p-9">
        <div className="mb-7 flex items-start gap-4">
          <div className="flex h-14 w-14 flex-shrink-0 items-center justify-center rounded-2xl bg-indigo-50 text-indigo-500">
            <ListChecks size={28} />
          </div>
          <div>
            <h2 className="text-2xl font-bold text-slate-800">设置本次练习</h2>
            <p className="mt-1 text-sm leading-6 text-slate-500">选择题目数量和科目范围，再开始做题。</p>
          </div>
        </div>

        {error && (
          <div className="mb-5 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-600">{error}</div>
        )}

        <section className="mb-7">
          <div className="mb-3 flex items-center justify-between gap-3">
            <div>
              <h3 className="font-semibold text-slate-700">题目数量</h3>
              <p className="mt-0.5 text-xs text-slate-400">可以输入 1—50 之间的任意整数</p>
            </div>
            <label className="flex items-center gap-2 text-sm text-slate-500">
              <input
                type="number"
                min="1"
                max="50"
                step="1"
                aria-label="题目数量"
                value={setup.questionCount}
                onChange={(event) => setSetup((prev) => ({ ...prev, questionCount: event.target.value }))}
                className="w-20 rounded-xl border border-slate-200 px-3 py-2 text-center font-semibold text-slate-700 outline-none focus:border-indigo-400"
              />
              题
            </label>
          </div>
          <div className="grid grid-cols-4 gap-2">
            {QUICK_COUNTS.map((count) => (
              <button
                key={count}
                type="button"
                aria-pressed={Number(setup.questionCount) === count}
                onClick={() => setSetup((prev) => ({ ...prev, questionCount: count }))}
                className={`rounded-xl border px-3 py-2 text-sm font-medium transition-colors cursor-pointer ${
                  Number(setup.questionCount) === count
                    ? "border-indigo-300 bg-indigo-50 text-indigo-600"
                    : "border-slate-200 text-slate-500 hover:border-indigo-200 hover:bg-slate-50"
                }`}
              >
                {count} 题
              </button>
            ))}
          </div>
        </section>

        <section className="mb-7">
          <div className="mb-3">
            <h3 className="font-semibold text-slate-700">科目范围</h3>
            <p className="mt-0.5 text-xs text-slate-400">混合科目会尽量从不同科目中均衡抽题</p>
          </div>
          <div className="grid gap-2 sm:grid-cols-2 md:grid-cols-3">
            <button
              type="button"
              aria-pressed={setup.subject === "mixed"}
              onClick={() => setSetup((prev) => ({ ...prev, subject: "mixed" }))}
              className={`flex items-center justify-center gap-2 rounded-xl border px-4 py-3 text-sm font-medium transition-colors cursor-pointer ${
                setup.subject === "mixed"
                  ? "border-indigo-300 bg-indigo-50 text-indigo-600"
                  : "border-slate-200 text-slate-500 hover:border-indigo-200 hover:bg-slate-50"
              }`}
            >
              <Shuffle size={15} /> 混合科目
            </button>
            {subjects.map((subject) => (
              <button
                key={subject.value}
                type="button"
                aria-pressed={setup.subject === subject.value}
                onClick={() => setSetup((prev) => ({ ...prev, subject: subject.value }))}
                className={`rounded-xl border px-4 py-3 text-sm font-medium transition-colors cursor-pointer ${
                  setup.subject === subject.value
                    ? "border-indigo-300 bg-indigo-50 text-indigo-600"
                    : "border-slate-200 text-slate-500 hover:border-indigo-200 hover:bg-slate-50"
                }`}
              >
                {subject.label}
              </button>
            ))}
          </div>
        </section>

        <div className="flex flex-col gap-4 rounded-2xl bg-slate-50 px-5 py-4 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <div className="text-xs text-slate-400">本次练习</div>
            <div className="mt-1 font-semibold text-slate-700">{selectedSubjectLabel} · {setup.questionCount || 0} 题</div>
          </div>
          <button
            type="button"
            onClick={onStart}
            className="flex items-center justify-center gap-2 rounded-xl bg-indigo-500 px-6 py-3 text-sm font-semibold text-white shadow-sm transition-colors hover:bg-indigo-600 cursor-pointer"
          >
            <Play size={17} /> 开始练习
          </button>
        </div>
      </div>
    </div>
  );
}
