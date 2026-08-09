/**
 * M4 讲课创建页 (4.2.1)
 * 
 * 功能：
 *  - 文件上传（拖拽/点击，PDF/Word/PPT/图片/文本）
 *  - 文本输入（直接输入要讲解的内容）
 *  - 讲课风格选择（简洁/详细/互动/故事）
 *  - 期望时长滑块
 *  - 薄弱点关联选择
 *  - "开始生成"按钮 → 跳转等待页
 */

import React, { useState, useRef, useCallback } from 'react';
import { Upload, FileText, Clock, Palette, Target, ArrowRight, X, File, AlertCircle } from 'lucide-react';
import { parseLectureFile } from '../../services/lectureService';
import { lectureStyles, durationOptions, mockWeakPoints } from '../../data/mockLecture';

// ─── 子组件：上传区域 ──────────────────────────────

const UploadZone = ({ file, onFileSelect, onClear, parsing, parseError }) => {
  const inputRef = useRef(null);

  const handleDrop = useCallback((e) => {
    e.preventDefault();
    const f = e.dataTransfer?.files?.[0];
    if (f) onFileSelect(f);
  }, [onFileSelect]);

  const handleDragOver = (e) => e.preventDefault();

  if (parsing) {
    return (
      <div className="flex flex-col items-center justify-center h-40 sm:h-48 border-2 border-purple-300 rounded-xl bg-purple-50/50">
        <div className="w-7 h-7 sm:w-8 sm:h-8 border-2 border-purple-400 border-t-transparent rounded-full animate-spin mb-2 sm:mb-3" />
        <p className="text-purple-500 text-xs sm:text-sm font-medium">正在解析文件…</p>
      </div>
    );
  }

  if (file) {
    return (
      <div className="flex items-center justify-between p-3 sm:p-4 border-2 border-green-300 rounded-xl bg-green-50/50">
        <div className="flex items-center gap-2 sm:gap-3 min-w-0">
          <File className="w-6 h-6 sm:w-8 sm:h-8 text-green-500 flex-shrink-0" />
          <div className="min-w-0">
            <p className="text-xs sm:text-sm font-medium text-slate-700 truncate">{file.name}</p>
            <p className="text-[10px] sm:text-xs text-slate-400">{(file.size / 1024).toFixed(1)} KB</p>
          </div>
        </div>
        <button onClick={onClear} className="p-1 hover:bg-red-50 rounded-lg transition-colors flex-shrink-0">
          <X className="w-4 h-4 sm:w-5 sm:h-5 text-slate-400 hover:text-red-500" />
        </button>
      </div>
    );
  }

  return (
    <div
      onDrop={handleDrop}
      onDragOver={handleDragOver}
      onClick={() => inputRef.current?.click()}
      className="flex flex-col items-center justify-center h-24 sm:h-40 border-2 border-dashed border-slate-300 rounded-xl bg-slate-50/50 hover:border-purple-400 hover:bg-purple-50/30 transition-all cursor-pointer"
    >
      <Upload className="w-8 h-8 sm:w-10 sm:h-10 text-slate-300 mb-2 sm:mb-3" />
      <p className="text-xs sm:text-sm font-medium text-slate-500">拖拽文件到此处，或点击上传</p>
      <p className="text-[10px] sm:text-xs text-slate-400 mt-1 px-2 text-center">支持 PDF、Word、PPT、图片、TXT（≤50MB）</p>
      {parseError && (
        <p className="flex items-center gap-1 text-xs text-red-500 mt-2">
          <AlertCircle className="w-3 h-3" />{parseError}
        </p>
      )}
      <input
        ref={inputRef}
        type="file"
        className="hidden"
        accept=".pdf,.doc,.docx,.ppt,.pptx,.png,.jpg,.jpeg,.txt"
        onChange={(e) => e.target.files?.[0] && onFileSelect(e.target.files[0])}
      />
    </div>
  );
};

// ─── 子组件：风格选择器 ─────────────────────────────

const StyleSelector = ({ value, onChange }) => (
  <div className="grid grid-cols-2 sm:grid-cols-2 lg:grid-cols-4 gap-2 sm:gap-3 lg:gap-4">
    {lectureStyles.map((s) => (
      <button
        key={s.value}
        onClick={() => onChange(s.value)}
        className={`text-left p-2 sm:p-3 lg:p-4 rounded-lg lg:rounded-xl border-2 transition-all flex items-center gap-2 lg:block ${
          value === s.value
            ? 'border-purple-500 bg-purple-50 shadow-sm'
            : 'border-slate-200 bg-white hover:border-purple-200'
        }`}
      >
        <p className="text-xs sm:text-sm lg:text-base font-semibold text-slate-800 whitespace-nowrap lg:w-full">{s.label}</p>
        <p className="text-[10px] sm:text-xs lg:text-sm text-slate-400 truncate lg:w-full lg:mt-1">{s.desc}</p>
      </button>
    ))}
  </div>
);

// ─── 子组件：时长选择器 ─────────────────────────────

const DurationPicker = ({ value, onChange }) => (
  <div className="grid grid-cols-5 sm:grid-cols-5 gap-2 sm:gap-3 lg:gap-4">
    {durationOptions.map((d) => (
      <button
        key={d.value}
        onClick={() => onChange(d.value)}
        className={`px-2 sm:px-4 lg:px-6 py-2 sm:py-2.5 lg:py-3 rounded-lg text-[11px] sm:text-sm lg:text-base font-medium transition-all text-center ${
          value === d.value
            ? 'bg-purple-500 text-white shadow-sm'
            : 'bg-slate-100 text-slate-600 hover:bg-purple-100'
        }`}
      >
        {d.label}
      </button>
    ))}
  </div>
);

// ─── 子组件：薄弱点选择器 ───────────────────────────
// TODO-REAL: 标签展示需后端协调修改
//   1) mockWeakPoints 当前来自 src/data/mockLecture.js，
//      上线时需替换为接口 GET /api/weak-points?userId=...&grade=...
//      字段约定：
//        - kpId         知识点 ID（数字）
//        - kpName       知识点名称（字符串）
//        - weaknessScore 薄弱度 0~1（数字）
//      返回后字段可能不同，需与后端对齐命名
//   2) 选中态 weakPointIds 会随 createLecture payload 传给后端
//      （lectureService.js handleGenerate），后端再据此
//      让 AI 在相关位置放慢节奏、增加互动
//   3) 标签上限：可能需要限制最多 N 个（如 3 个），
//      取决于后端模型能消化的上下文长度
const WeakPointSelector = ({ selected, onToggle }) => (
  <div className="grid grid-cols-1 sm:grid-cols-3 gap-2 sm:gap-3 lg:gap-4">
    {mockWeakPoints.map((wp) => {
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

// ─── 主页面 ─────────────────────────────────────────

const LectureCreatePage = ({ userId = 1, onStartGeneration, onViewHistory, onExit }) => {
  // 输入模式：file | text
  const [inputMode, setInputMode] = useState('file');

  // 文件上传状态
  const [file, setFile] = useState(null);
  const [parsing, setParsing] = useState(false);
  const [parseResult, setParseResult] = useState(null);
  const [parseError, setParseError] = useState('');

  // 文本输入
  const [textContent, setTextContent] = useState('');

  // 选项
  const [style, setStyle] = useState('interactive');
  const [duration, setDuration] = useState(15);
  // TODO-REAL: 关联薄弱知识点（标签展示需后端协调）
  //   当前使用 mock 数据 mockWeakPoints（kpId/kpName/weaknessScore），
  //   上线后应由后端根据用户学情动态返回。
  //   selectedWeakPoints 数组会在 handleGenerate 时随 payload 传
  //   POST /api/lecture/generate，AI 据此调节讲课节奏。
  const [selectedWeakPoints, setSelectedWeakPoints] = useState([]);

  // 处理文件选择 + 解析
  const handleFileSelect = useCallback(async (f) => {
    setFile(f);
    setParseError('');
    setParsing(true);
    try {
      const result = await parseLectureFile(f, userId);
      setParseResult(result);
    } catch (err) {
      setParseError(err?.message || '文件解析失败');
      setFile(null);
    } finally {
      setParsing(false);
    }
  }, [userId]);

  const handleClearFile = () => {
    setFile(null);
    setParseResult(null);
    setParseError('');
  };

  // TODO-REAL: 标签数据后端协调点
  //   - selectedWeakPoints 存的是 kpId（数字），后端应根据当前用户
  //     的年级/学科筛选合法的 kpId 集合，避免乱选
  //   - 后端可能要求按"学科"分组排序，而不是平铺
  //   - 用户每次进入页面的可选列表都不同（动态学情），不应缓存
  const toggleWeakPoint = (kpId) => {
    setSelectedWeakPoints(prev =>
      prev.includes(kpId) ? prev.filter(id => id !== kpId) : [...prev, kpId]
    );
  };

  // 是否可以生成
  const canGenerate = inputMode === 'file'
    ? !!parseResult
    : textContent.trim().length > 10;

  const handleGenerate = () => {
    if (!canGenerate) return;
    onStartGeneration?.({
      userId,
      sourceType: inputMode === 'file' ? 'file' : 'text',
      sourceId: parseResult?.fileId,
      textContent: inputMode === 'text' ? textContent : undefined,
      parseResult,
      style,
      duration,
      weakPointIds: selectedWeakPoints,
    });
  };

  return (
    <div className="w-full min-h-screen bg-gradient-to-br from-purple-50 via-white to-blue-50">
      <div className="w-full max-w-screen-2xl mx-auto px-4 sm:px-6 lg:px-10 py-3 sm:py-8 min-h-screen flex flex-col">
        {/* 顶部导航 */}
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2 mb-3 sm:mb-6">
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

        <div className="flex-1 space-y-3 sm:space-y-5 lg:space-y-7 flex flex-col justify-center lg:py-8">
          {/* 输入模式切换 */}
          <div className="flex bg-slate-100 rounded-xl p-1">
            {[
              { value: 'file', label: '上传文件', icon: Upload },
              { value: 'text', label: '输入文本', icon: FileText },
            ].map((m) => (
              <button
                key={m.value}
                onClick={() => setInputMode(m.value)}
                className={`flex-1 flex items-center justify-center gap-1.5 sm:gap-2 py-1.5 sm:py-2.5 rounded-lg text-xs sm:text-sm font-medium transition-all ${
                  inputMode === m.value
                    ? 'bg-white text-purple-600 shadow-sm'
                    : 'text-slate-500 hover:text-slate-700'
                }`}
              >
                <m.icon className="w-4 h-4" />
                {m.label}
              </button>
            ))}
          </div>

          {/* 文件上传 / 文本输入 */}
          {inputMode === 'file' ? (
            <div>
              <UploadZone
                file={file}
                onFileSelect={handleFileSelect}
                onClear={handleClearFile}
                parsing={parsing}
                parseError={parseError}
              />
              {parseResult && (
                <div className="mt-3 p-4 bg-green-50 border border-green-200 rounded-xl">
                  <p className="text-sm font-medium text-green-700">
                    ✅ 解析完成：「{parseResult.parsedContent.title}」
                  </p>
                  <p className="text-xs text-green-600 mt-1">
                    {parseResult.parsedContent.sections.length} 个章节 · 预计 {parseResult.parsedContent.estimatedDuration} 分钟
                  </p>
                </div>
              )}
            </div>
          ) : (
            <textarea
              value={textContent}
              onChange={(e) => setTextContent(e.target.value)}
              placeholder="输入你想讲解的内容，例如：&#10;&#10;勾股定理是初中数学的重要定理。直角三角形两直角边的平方和等于斜边的平方…"
              rows={1}
              className="w-full h-24 sm:h-40 p-3 border-2 border-slate-200 rounded-xl text-sm leading-6 resize-none focus:border-purple-400 focus:ring-0 transition-colors placeholder:text-slate-300"
            />
          )}

          {/* 讲课风格 */}
          <div>
            <div className="flex items-center gap-2 mb-2 lg:mb-3">
              <Palette className="w-4 h-4 text-purple-500" />
              <span className="text-sm lg:text-base font-semibold text-slate-700">讲课风格</span>
            </div>
            <StyleSelector value={style} onChange={setStyle} />
          </div>

          {/* 期望时长 */}
          <div>
            <div className="flex items-center gap-2 mb-2 lg:mb-3">
              <Clock className="w-4 h-4 text-purple-500" />
              <span className="text-sm lg:text-base font-semibold text-slate-700">期望时长</span>
            </div>
            <DurationPicker value={duration} onChange={setDuration} />
          </div>

          {/* 薄弱点关联 */}
          <div>
            <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-1 sm:gap-2 mb-2 lg:mb-3">
              <div className="flex items-center gap-2">
                <Target className="w-4 h-4 text-orange-500" />
                <span className="text-sm lg:text-base font-semibold text-slate-700">关联薄弱知识点（可选）</span>
              </div>
              <span className="text-[10px] sm:text-xs lg:text-sm text-slate-400 sm:text-right leading-tight">选中后 AI 会在这些地方放慢节奏、增加互动</span>
            </div>
            <WeakPointSelector selected={selectedWeakPoints} onToggle={toggleWeakPoint} />
          </div>

          {/* 生成按钮 */}
          <button
            onClick={handleGenerate}
            disabled={!canGenerate}
            className={`w-full flex items-center justify-center gap-2 py-2.5 sm:py-3 rounded-xl text-sm sm:text-base font-semibold transition-all ${
              canGenerate
                ? 'bg-purple-600 text-white hover:bg-purple-700 shadow-lg shadow-purple-200 active:scale-[0.98]'
                : 'bg-slate-200 text-slate-400 cursor-not-allowed'
            }`}
          >
            <ArrowRight className="w-5 h-5" />
            {canGenerate ? '开始生成讲课内容' : '请先上传文件或输入文本内容'}
          </button>
        </div>
      </div>
    </div>
  );
};

export default LectureCreatePage;
