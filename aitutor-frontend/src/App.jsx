import React, { useEffect, useState } from 'react';  
import GlobalStyles from './styles/GlobalStyles.jsx';
import LoginPage from './pages/LoginPage';
import LoginPage2 from './pages/LoginPage2.jsx';
import ProjectListPage from './pages/ProjectListPage';
import LecturePage from './pages/LecturePage';
import LecturePage2 from './pages/LecturePage2';
import TemHomePage from './pages/TemHomePage';
import ProfilePage from './pages/ProfilePage.jsx';
import TeacherAvatarPage from './pages/TeacherAvatarPage.jsx';
import PhotoQAPage from './pages/m2/PhotoQAPage';
import ExplainPage from './pages/m2/ExplainPage';
import ExplainHistoryPage from './pages/m2/ExplainHistoryPage';
import LearningProfilePage from './pages/LearningProfilePage.jsx';
import KnowledgePointDetailPage from './pages/KnowledgePointDetailPage.jsx';
// M4 讲课流程 - 独立容器，通过 m4Page state 触发，与 M2 路由风格一致
import M4LectureContainer from './pages/lecture/M4LectureContainer';
// M1 做题页
import PracticePage from './pages/PracticePage.jsx';
import QuestionBankPage from './pages/QuestionBankPage.jsx';
import WrongQuestionBookPage from './pages/WrongQuestionBookPage.jsx';
import StatisticsPage from './pages/StatisticsPage.jsx';
import RankingPage from './pages/RankingPage.jsx';
import { hasValidToken } from './utils/tokenManager';
import { checkAuth } from './services/authService';

export default function App() {
    const [isChecking, setIsChecking] = useState(true);
    const [isAuthed, setIsAuthed] = useState(false);
    const [currentCourseId, setCurrentCourseId] = useState('');
    const [guestRoute, setGuestRoute] = useState('home'); // home | profile
    const [showProfile, setShowProfile] = useState(false);
    const [showTeacherAvatar, setShowTeacherAvatar] = useState(false);
    const [m2Page, setM2Page] = useState(null); // null | 'photo-qa' | 'explain' | 'explain-history'
    const [m2Params, setM2Params] = useState({}); // 传递给 M2 页面的参数
    const [learningProfileView, setLearningProfileView] = useState(null); // null | overview | detail
    const [selectedKnowledgePointId, setSelectedKnowledgePointId] = useState(null);
    const [m4Page, setM4Page] = useState(null); // null | 'active' — M4 讲课全屏容器
    const [m1Page, setM1Page] = useState(null); // null | practice | question-bank | mistakes | statistics | ranking
    const [m1PracticeParams, setM1PracticeParams] = useState({}); // M1 做题页参数（mode/lessonId 等）

    useEffect(() => {
        const checkSession = async () => {
            try {
                // 先检查本地是否有有效的 token
                if (!hasValidToken()) {
                    setIsAuthed(false);
                    setIsChecking(false);
                    return;
                }

                // 验证 token 是否真的有效（调用后端接口）
                const isValid = await checkAuth();
                setIsAuthed(isValid);
            } catch (error) {
                console.error('会话检查失败:', error);
                setIsAuthed(false);
            } finally {
                setIsChecking(false);
            }
        };
        checkSession();
    }, []);

    const handleLoginSuccess = (user) => {
        console.log('登录成功，用户信息:', user);
        setIsAuthed(true);
    };

    const handleOpenProfile = () => {
        setShowProfile(true);
    };

    const handleOpenLearningProfile = () => {
        setSelectedKnowledgePointId(null);
        setLearningProfileView('overview');
    };

    const handleOpenKnowledgePoint = (knowledgePoint) => {
        const id = typeof knowledgePoint === 'object' ? knowledgePoint?.id : knowledgePoint;
        if (!id) return;
        setSelectedKnowledgePointId(id);
        setLearningProfileView('detail');
    };

    // M4 入口/出口
    const handleLaunchM4 = () => setM4Page('active');
    const handleExitM4 = () => setM4Page(null);
    const handleLaunchM1 = (params) => {
      if (params && typeof params === 'object') {
        setM1PracticeParams(params);
      }
      setM1Page('practice');
    };

    return (
        <div className={isAuthed ? "flex h-screen bg-slate-100 text-slate-800" : "w-full h-screen"}>
            <GlobalStyles />
            {m4Page === 'active' ? (
                <M4LectureContainer onExit={handleExitM4} />
            ) : m1Page ? (
                <div className="flex flex-col w-full h-full bg-slate-50">
                    <header className="shrink-0 bg-white border-b border-slate-200 px-4 py-3 flex items-center gap-4 flex-wrap shadow-sm">
                        <button
                            type="button"
                            onClick={() => { setM1Page(null); setM1PracticeParams({}); }}
                            className="px-3 py-2 text-sm text-slate-600 hover:text-indigo-600 hover:bg-indigo-50 rounded-lg transition-colors cursor-pointer"
                        >
                            ← 返回首页
                        </button>
                        <nav className="flex items-center gap-1 flex-wrap" aria-label="做题模块导航">
                            {[
                                ['practice', '开始做题'],
                                ['question-bank', '题库'],
                                ['mistakes', '错题本'],
                                ['statistics', '统计'],
                                ['ranking', '排行榜'],
                            ].map(([page, label]) => (
                                <button
                                    type="button"
                                    key={page}
                                    onClick={() => setM1Page(page)}
                                    className={`px-4 py-2 text-sm font-medium rounded-lg transition-colors cursor-pointer ${m1Page === page ? 'bg-indigo-600 text-white' : 'text-slate-600 hover:bg-slate-100'}`}
                                >
                                    {label}
                                </button>
                            ))}
                        </nav>
                    </header>
                    <main className="flex-1 min-h-0 overflow-auto p-4">
                        {m1Page === 'practice' ? (
                            <PracticePage
                                embedded
                                mode={m1PracticeParams.mode}
                                lessonId={m1PracticeParams.lessonId || ""}
                                initialParams={m1PracticeParams}
                                onViewStatistics={() => setM1Page('statistics')}
                            />
                        ) : m1Page === 'question-bank' ? (
                            <QuestionBankPage onStartPractice={handleLaunchM1} lessonId={m1PracticeParams.lessonId || ""} />
                        ) : m1Page === 'mistakes' ? (
                            <WrongQuestionBookPage onRedo={() => handleLaunchM1({ mode: 'MISTAKE_REDO' })} />
                        ) : m1Page === 'statistics' ? (
                            <StatisticsPage />
                        ) : (
                            <RankingPage />
                        )}
                    </main>
                </div>
            ) : isChecking ? (
                <div className="m-auto text-slate-600">检查会话中…</div>
            ) : !isAuthed ? (
                guestRoute === 'profile' ? (
                    <ProfilePage onBack={() => setGuestRoute('home')} />
                ) : (
                    <div className="relative w-full h-full">
                        <LoginPage2 onLoginSuccess={handleLoginSuccess} />
                        {import.meta.env.DEV && (
                            <button
                                onClick={() => { setIsAuthed(true); console.log('开发模式：已跳过登录') }}
                                className="fixed top-4 right-4 z-50 px-4 py-2 bg-gray-800 text-white text-sm rounded-lg hover:bg-gray-700 shadow"
                            >
                                🚀 跳过登录（开发模式）
                            </button>
                        )}
                    </div>
                )
            ) : m2Page === 'photo-qa' ? (
                <PhotoQAPage onBack={() => setM2Page(null)} onExplain={(params) => { setM2Params(params); setM2Page('explain'); }} />
            ) : m2Page === 'explain' ? (
                <ExplainPage onBack={() => { const from = m2Params.from; setM2Params(from === 'explain-history' ? { from: 'explain' } : {}); setM2Page(from === 'explain-history' ? 'explain-history' : null); }} wrongQuestionId={m2Params.wrongQuestionId} replayId={m2Params.replayId} onExplainHistory={() => { setM2Params({ from: 'explain' }); setM2Page('explain-history'); }} />
            ) : m2Page === 'explain-history' ? (
                <ExplainHistoryPage onBack={m2Params.from === 'explain' ? () => { setM2Params({}); setM2Page('explain'); } : () => setM2Page(null)} onReplay={(id) => { setM2Params({ replayId: id, from: 'explain-history' }); setM2Page('explain'); }} />
            ) : currentCourseId ? (
                      <LecturePage2 courseId={currentCourseId} onBack={() => setCurrentCourseId('')} />
            ) : learningProfileView === 'detail' ? (
                <KnowledgePointDetailPage
                    knowledgePointId={selectedKnowledgePointId}
                    onBack={() => setLearningProfileView('overview')}
                    onHome={() => setLearningProfileView('overview')}
                />
            ) : learningProfileView === 'overview' ? (
                <LearningProfilePage
                    onBack={() => setLearningProfileView(null)}
                    onOpenKnowledgePoint={handleOpenKnowledgePoint}
                />
            ) : (
                showTeacherAvatar ? (
                    <TeacherAvatarPage onBack={() => setShowTeacherAvatar(false)} />
                ) : showProfile ? (
                    <ProfilePage onBack={() => setShowProfile(false)} />
                ) : (
                    <div className="relative w-full h-full">
                    <TemHomePage 
                        onEnterProject={(courseId) => setCurrentCourseId(courseId)}
                        onOpenProfile={handleOpenProfile}
                        onOpenTeacherAvatar={() => setShowTeacherAvatar(true)}
                        onM2PhotoQa={() => setM2Page('photo-qa')}
                        onM2Explain={() => { setM2Params({}); setM2Page('explain'); }}
                        onOpenLearningProfile={handleOpenLearningProfile}
                        onM1Practice={handleLaunchM1}
                    />
                    {/* M4 讲课入口（浮动按钮，联调后可移除或整合到首页） */}
                    <button
                        onClick={handleLaunchM4}
                        className="absolute bottom-6 right-6 flex items-center gap-2 px-4 py-2.5 bg-purple-600 text-white rounded-full shadow-lg hover:bg-purple-700 active:scale-95 transition-all text-sm font-medium z-40"
                        title="AI 即时讲课"
                    >
                        <span className="text-lg">🎓</span>
                        AI 讲课
                    </button>
                    </div>
                )
            )}
        </div>
    );
}
