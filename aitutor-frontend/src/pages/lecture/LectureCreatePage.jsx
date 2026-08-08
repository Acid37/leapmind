/**
 * M4 讲课创建页 (4.2.1)
 * 
 * 功能：
 *  - DeepSeek 风格对话输入框：支持同时上传文件 + 输入提示词
 *  - 文件上传（拖拽/点击，PDF/Word/PPT/图片/文本）
 *  - 薄弱点关联选择
 *  - "生成"按钮 → 跳转等待页
 */

import React, { useState, useRef, useCallback, useEffect } from 'react';
import { Paperclip, FileText, Target, X, File, AlertCircle, ArrowLeft, Send, Loader2 } from 'lucide-react';
import { parseLectureFile, getWeakPoints } from '../../services/lectureService';
import { getLearningProfile } from '../../services/learningProfileService';
import { getOrCreateCourseId } from '../../features/chat/pptSession';

function buildProfileSummary(profile) {
  if (!profile) return '';
  const { summary = {}, preferences = {}, user = {} } = profile;
  const details = [
    user.grade && `学习阶段：${user.grade}`,
    Number.isFinite(summary.overallMastery) && `综合掌握度：${summary.overallMastery}%`,
    Number.isFinite(summary.weeklyStudyMinutes) && `本周学习：${summary.weeklyStudyMinutes} 分钟`,
    preferences.learningStyle && `学习偏好：${preferences.learningStyle}`,
    summary.message && `学习建议：${summary.message}`,
  ].filter(Boolean);
  return details.join('；');
}

// ─── 子组件：薄弱点选择器 ───────────────────────────
const WeakPointSelector = ({ items, selected, onToggle, loading }) => {
  if (loading) {
    return (
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-2 sm:gap-3 lg:gap-4">
        {[1, 2, 3].map((i) => (
          <div key={i} className="px-3 lg:px-5 py-2 lg:py-3.5 rounded-xl bg-slate-100 animate-pulse">
            <div className="h-4 bg-slate-200 rounded w-3/4" />
          </div>
        ))}
      </div>
    );
  }
  if (!items || items.length === 0) {
    return (
      <div className="text-center py-4 text-sm text-slate-400">
        暂无薄弱知识点，多做练习题后会自动分析
      </div>
    );
  }
  return (
    <div className="grid grid-cols-1 sm:grid-cols-3 gap-2 sm:gap-3 lg:gap-4">
      {items.map((wp) => {
        const isSelected = selected.includes(wp.kpId);
        return (
          <button
            key={wp.kpId}
            onClick={() => onToggle(wp.kpId)}
            className={`px-3 lg:px-5 py-2 lg:py-3.5 rounded-xl text-sm lg:text-base font-medium transition-all flex items-center justify-between gap-2 ${
              isSelected
                ? 'bg-orange-100 text-orange-700 border-2 border-orange-400'
                : 'bg-slate-50 text-slate-600 border-2 border-slate-200 hover:border-orange-200'
            }`}
          >
            <span className="truncate">{wp.kpName}</span>
            <span className={`text-xs lg:text-sm font-bold ${isSelected ? 'text-orange-500' : 'text-slate-400'}`}>
              {Math.round(wp.weaknessScore * 100)}%
            </span>
          </button>
        );
      })}
    </div>
  );
};

// ─── 主页面 ─────────────────────────────────────────

const LectureCreatePage = ({ userId = 1, initialText = '', onStartGeneration, onViewHistory, onExit }) => {
  // 文件上传状态
  const [file, setFile] = useState(null);
  const [parsing, setParsing] = useState(false);
  const [parseResult, setParseResult] = useState(null);
  const [parseError, setParseError] = useState('');

  // 文本输入（提示词）——支持从首页搜索栏带入初始内容
  const [textContent, setTextContent] = useState(initialText);

  // 拖拽高亮状态
  const [dragOver, setDragOver] = useState(false);

  // 薄弱知识点（从后端实时获取）
  const [selectedWeakPoints, setSelectedWeakPoints] = useState([]);
  const [weakPoints, setWeakPoints] = useState([]);
  const [weakPointsLoading, setWeakPointsLoading] = useState(true);
  const [learningProfile, setLearningProfile] = useState(null);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      setWeakPointsLoading(true);
      const data = await getWeakPoints(userId);
      if (!cancelled) {
        setWeakPoints(data);
        setWeakPointsLoading(false);
      }
    })();
    return () => { cancelled = true; };
  }, [userId]);

  // M6 上下文：画像读取失败时降级为空，不影响用户继续创建讲课。
  useEffect(() => {
    let cancelled = false;
    getLearningProfile(userId)
      .then((profile) => {
        if (!cancelled) setLearningProfile(profile);
      })
      .catch(() => {
        if (!cancelled) setLearningProfile(null);
      });
    return () => { cancelled = true; };
  }, [userId]);

  const fileInputRef = useRef(null);

  // 处理文件选择 + 解析
  const handleFileSelect = useCallback(async (f) => {
    if (!f) return;
    setFile(f);
    setParseError('');
    setParsing(true);
    try {
      const courseId = getOrCreateCourseId();
      const result = await parseLectureFile(f, courseId);
      setParseResult(result);
    } catch (err) {
      setParseError(err?.message || '文件解析失败');
      setFile(null);
    } finally {
      setParsing(false);
    }
  }, []);

  const handleClearFile = () => {
    setFile(null);
    setParseResult(null);
    setParseError('');
  };

  // 拖拽上传
  const handleDrop = useCallback((e) => {
    e.preventDefault();
    setDragOver(false);
    const f = e.dataTransfer?.files?.[0];
    if (f) handleFileSelect(f);
  }, [handleFileSelect]);

  // 薄弱知识点已对接 GET /api/weak-points（M3 曾俊桥 / develop 分支）
  // kpId←UserWeakPointVO.id, kpName←knowledgePoint, weaknessScore←1-accuracyRate
  const toggleWeakPoint = (kpId) => {
    setSelectedWeakPoints(prev =>
      prev.includes(kpId) ? prev.filter(id => id !== kpId) : [...prev, kpId]
    );
  };

  // 是否可以生成：有解析成功的文件 或 有足够文本
  const canGenerate = !!parseResult || textContent.trim().length > 0;

  const handleGenerate = () => {
    if (!canGenerate || parsing) return;
    const selectedWeakPointDetails = weakPoints
      .filter((wp) => selectedWeakPoints.includes(wp.kpId))
      .map((wp) => ({ id: wp.kpId, name: wp.kpName, weaknessScore: wp.weaknessScore }));
    onStartGeneration?.({
      userId,
      courseId: getOrCreateCourseId(),
      sourceType: file ? 'file' : 'text',
      sourceId: parseResult?.fileId,
      textContent: textContent.trim() || undefined,
      parseResult,
      weakPointIds: selectedWeakPoints,
      selectedWeakPoints: selectedWeakPointDetails,
      userProfile: learningProfile ? {
        summary: learningProfile.summary,
        preferences: learningProfile.preferences,
      } : {},
      userProfileSummary: buildProfileSummary(learningProfile),
    });
  };

  // Enter 发送 / Shift+Enter 换行
  const handleKeyDown = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleGenerate();
    }
  };

  return (
    <div className="w-full min-h-screen bg-gradient-to-br from-purple-50 via-white to-blue-50">
      <div className="w-full max-w-screen-2xl mx-auto px-4 sm:px-6 lg:px-10 py-3 sm:py-8 min-h-screen flex flex-col">
        {/* 顶部导航 */}
        <div className="flex items-center gap-3 sm:gap-4 mb-3 sm:mb-6">
          <button
            onClick={onExit}
            className="flex items-center gap-1.5 px-3 sm:px-4 py-1.5 sm:py-2 text-xs sm:text-sm font-medium text-slate-600 bg-white border border-slate-200 rounded-lg hover:bg-slate-50 hover:border-slate-300 transition-colors shrink-0"
            title="返回首页"
          >
            <ArrowLeft className="w-3.5 h-3.5 sm:w-4 sm:h-4" />
            返回
          </button>
          <div className="flex-1 flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2 min-w-0">
            <div>
              <h1 className="text-xl sm:text-2xl font-bold text-slate-800">创建讲课</h1>
              <p className="text-xs sm:text-sm text-slate-500 mt-0.5 sm:mt-1">上传文件或输入内容，AI 为你生成讲课 PPT</p>
            </div>
            <button
              onClick={onViewHistory}
              className="flex items-center gap-1.5 px-3 sm:px-4 py-1.5 sm:py-2 text-xs sm:text-sm font-medium text-purple-600 bg-purple-50 rounded-lg hover:bg-purple-100 transition-colors self-start sm:self-auto"
            >
              <FileText className="w-3.5 h-3.5 sm:w-4 sm:h-4" />
              历史记录
            </button>
          </div>
        </div>

        <div className="flex-1 flex flex-col justify-center gap-4 sm:gap-5 lg:gap-6 lg:py-8 max-w-4xl mx-auto w-full">
          {/* 薄弱点关联 */}
          <div>
            <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-1 sm:gap-2 mb-2 lg:mb-3">
              <div className="flex items-center gap-2">
                <Target className="w-4 h-4 text-orange-500" />
                <span className="text-sm lg:text-base font-semibold text-slate-700">关联薄弱知识点（可选）</span>
              </div>
              <span className="text-[10px] sm:text-xs lg:text-sm text-slate-400 sm:text-right leading-tight">选中后 AI 会在这些地方放慢节奏、增加互动</span>
            </div>
            <WeakPointSelector items={weakPoints} selected={selectedWeakPoints} onToggle={toggleWeakPoint} loading={weakPointsLoading} />
          </div>

          {/* DeepSeek 风格对话输入框：文件 + 提示词 二合一 */}
          <div
            onDrop={handleDrop}
            onDragOver={(e) => { e.preventDefault(); setDragOver(true); }}
            onDragLeave={() => setDragOver(false)}
            className={`relative rounded-2xl border-2 bg-white shadow-xl transition-all duration-200 ${
              dragOver
                ? 'border-purple-400 bg-purple-50/50 ring-4 ring-purple-100'
                : 'border-slate-200 focus-within:border-purple-400 focus-within:ring-4 focus-within:ring-purple-100'
            }`}
          >
            {/* 已上传文件 chips */}
            {(file || parsing) && (
              <div className="flex flex-wrap gap-2 pt-3 px-3">
                <div className={`flex items-center gap-2 rounded-lg border px-2.5 py-1.5 text-xs max-w-full ${
                  parseError
                    ? 'bg-red-50 border-red-200'
                    : 'bg-purple-50 border-purple-200'
                }`}>
                  {parsing ? (
                    <Loader2 className="w-3.5 h-3.5 text-purple-400 animate-spin flex-shrink-0" />
                  ) : parseError ? (
                    <AlertCircle className="w-3.5 h-3.5 text-red-500 flex-shrink-0" />
                  ) : (
                    <File className="w-3.5 h-3.5 text-purple-500 flex-shrink-0" />
                  )}
                  <span className="text-slate-700 font-medium truncate max-w-[140px] sm:max-w-[220px]">{file?.name}</span>
                  {parsing ? (
                    <span className="text-purple-400 text-[10px] shrink-0">解析中…</span>
                  ) : parseError ? (
                    <span className="text-red-500 text-[10px] shrink-0">解析失败</span>
                  ) : (
                    <span className="text-green-500 text-[10px] shrink-0">✓ 已解析</span>
                  )}
                  {!parsing && (
                    <button onClick={handleClearFile} className="p-0.5 hover:bg-slate-200 rounded transition-colors flex-shrink-0">
                      <X className="w-3.5 h-3.5 text-slate-400 hover:text-red-500" />
                    </button>
                  )}
                </div>
              </div>
            )}

            {/* 提示词输入 */}
            <textarea
              value={textContent}
              onChange={(e) => setTextContent(e.target.value)}
              onKeyDown={handleKeyDown}
              placeholder="描述你想讲解的内容，例如「勾股定理是初中数学的重要定理…」&#10;&#10;也可以直接拖入 PDF / Word / PPT / 图片 / TXT 文件，并附上提示词一起生成"
              rows={1}
              className="w-full min-h-[120px] sm:min-h-[140px] px-4 pt-3 pb-2 text-sm sm:text-base leading-6 resize-none bg-transparent focus:outline-none placeholder:text-slate-300"
            />

            {/* 底部工具栏 */}
            <div className="flex items-center justify-between gap-2 px-2.5 pb-2.5">
              {/* 左侧：上传按钮 + 格式提示 */}
              <div className="flex items-center gap-1.5 min-w-0">
                <button
                  type="button"
                  onClick={() => fileInputRef.current?.click()}
                  disabled={parsing}
                  className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg text-slate-500 hover:text-purple-600 hover:bg-purple-50 transition-colors disabled:opacity-50"
                  title="上传文件"
                >
                  <Paperclip className="w-5 h-5" />
                  <span className="text-xs font-medium hidden sm:inline">上传文件</span>
                </button>
                <span className="text-[10px] sm:text-xs text-slate-400 truncate hidden md:inline">支持 PDF、Word、PPT、图片、TXT（≤50MB）</span>
              </div>

              {/* 右侧：生成按钮 */}
              <button
                type="button"
                onClick={handleGenerate}
                disabled={!canGenerate || parsing}
                className={`flex items-center gap-1.5 px-4 sm:px-5 py-2 rounded-xl text-sm font-semibold transition-all shrink-0 ${
                  canGenerate && !parsing
                    ? 'bg-purple-600 text-white hover:bg-purple-700 shadow-lg shadow-purple-200 active:scale-95'
                    : 'bg-slate-200 text-slate-400 cursor-not-allowed'
                }`}
              >
                {parsing ? (
                  <Loader2 className="w-4 h-4 animate-spin" />
                ) : (
                  <Send className="w-4 h-4" />
                )}
                {canGenerate ? '生成讲课' : '请上传文件或输入内容'}
              </button>
            </div>

            <input
              ref={fileInputRef}
              type="file"
              className="hidden"
              accept=".pdf,.doc,.docx,.ppt,.pptx,.png,.jpg,.jpeg,.txt"
              onChange={(e) => e.target.files?.[0] && handleFileSelect(e.target.files[0])}
            />
          </div>

          {/* 解析结果提示 */}
          {parseResult && (
            <div className="p-3 sm:p-4 bg-green-50 border border-green-200 rounded-xl animate-fadeIn">
              <p className="text-sm font-medium text-green-700">
                ✅ 解析完成：「{parseResult.parsedContent.title}」
              </p>
              <p className="text-xs text-green-600 mt-1">
                {parseResult.parsedContent.sections.length} 个章节 · 预计 {parseResult.parsedContent.estimatedDuration} 分钟
              </p>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default LectureCreatePage;
