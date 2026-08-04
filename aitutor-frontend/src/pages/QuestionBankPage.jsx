/**
 * 题库浏览与筛选页（M1 · 5.2.1）
 *
 * 左侧筛选面板 + 右侧题目列表
 * 筛选条件：科目 → 年级 → 章节 → 题型 → 难度
 */
import { useState, useEffect, useRef } from "react";
import {
  Search,
  Filter,
  ChevronDown,
  ChevronUp,
  Play,
  BookOpen,
  SlidersHorizontal,
  X,
  Upload,
  Download,
  FileSpreadsheet,
} from "lucide-react";
import { getQuestions, getFilterOptions, importQuestions, getImportTemplate } from "../services/practiceService";

export default function QuestionBankPage({ onStartPractice, lessonId = "" }) {
  // 筛选条件
  const [filterOptions, setFilterOptions] = useState(null);
  const [filters, setFilters] = useState({
    subject: "",
    grade: "",
    chapter: "",
    type: "",
    difficulty: "",
    lessonId: lessonId || "",
  });
  const [searchKeyword, setSearchKeyword] = useState("");

  // 数据
  const [questions, setQuestions] = useState([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [loading, setLoading] = useState(true);

  // UI
  const [showMobileFilter, setShowMobileFilter] = useState(false);
  const [expandedSection, setExpandedSection] = useState("subject");

  // 导入状态
  const [importing, setImporting] = useState(false);
  const [importResult, setImportResult] = useState(null);

  // 文件输入 ref
  const fileInputRef = useRef(null);

  // 加载筛选选项
  useEffect(() => {
    getFilterOptions().then(setFilterOptions);
  }, []);

  // 加载题目
  useEffect(() => {
    loadQuestions();
  }, [filters, page]);

  const loadQuestions = async () => {
    setLoading(true);
    try {
      const params = { page, size: 20 };
      if (filters.subject) params.subject = filters.subject;
      if (filters.grade) params.grade = filters.grade;
      if (filters.chapter) params.chapter = filters.chapter;
      if (filters.type) params.type = filters.type;
      if (filters.difficulty) params.difficulty = filters.difficulty;
      if (searchKeyword) params.keyword = searchKeyword;
      const data = await getQuestions(params);
      setQuestions(data.items);
      setTotal(data.total);
    } catch (err) {
      console.error("加载题目失败:", err);
    } finally {
      setLoading(false);
    }
  };

  const updateFilter = (key, value) => {
    setFilters((prev) => ({ ...prev, [key]: value }));
    setPage(1);
  };

  const clearFilters = () => {
    setFilters({ subject: "", grade: "", chapter: "", type: "", difficulty: "", lessonId: "" });
    setSearchKeyword("");
    setPage(1);
  };

  // 导入 Excel/CSV
  const handleImportFile = async (e) => {
    const file = e.target.files?.[0];
    if (!file) return;
    setImporting(true);
    setImportResult(null);
    try {
      const result = await importQuestions(file);
      setImportResult(result);
      // 刷新列表
      loadQuestions();
    } catch (err) {
      setImportResult({ inserted: 0, failed: 1, errors: [err.message] });
    } finally {
      setImporting(false);
      // 重置 input 以便再次选择同一文件
      if (fileInputRef.current) fileInputRef.current.value = '';
    }
  };

  // 下载导入模板
  const handleDownloadTemplate = async () => {
    try {
      await getImportTemplate();
    } catch (err) {
      console.warn('下载模板失败:', err);
    }
  };

  const hasActiveFilters = Object.values(filters).some(Boolean);

  // 筛选面板
  const FilterPanel = () => (
    <div className="space-y-5">
      {/* 标题 */}
      <div className="flex items-center justify-between">
        <h2 className="font-bold text-slate-700 flex items-center gap-2">
          <SlidersHorizontal size={18} /> 筛选条件
        </h2>
        {hasActiveFilters && (
          <button
            onClick={clearFilters}
            className="text-xs text-indigo-500 hover:text-indigo-600 flex items-center gap-1 cursor-pointer"
          >
            <X size={14} /> 清除
          </button>
        )}
      </div>

      {/* 科目 */}
      <FilterSection
        title="科目"
        expanded={expandedSection === "subject"}
        onToggle={() => setExpandedSection(expandedSection === "subject" ? "" : "subject")}
      >
        <div className="flex flex-wrap gap-2">
          {filterOptions?.subjects.map((s) => (
            <FilterChip
              key={s.value}
              active={filters.subject === s.value}
              onClick={() => updateFilter("subject", filters.subject === s.value ? "" : s.value)}
            >
              {s.label}
            </FilterChip>
          ))}
        </div>
      </FilterSection>

      {/* 年级 */}
      <FilterSection
        title="年级"
        expanded={expandedSection === "grade"}
        onToggle={() => setExpandedSection(expandedSection === "grade" ? "" : "grade")}
      >
        <div className="flex flex-wrap gap-2">
          {filterOptions?.grades.map((g) => (
            <FilterChip
              key={g.value}
              active={filters.grade === g.value}
              onClick={() => updateFilter("grade", filters.grade === g.value ? "" : g.value)}
            >
              {g.label}
            </FilterChip>
          ))}
        </div>
      </FilterSection>

      {/* 章节（依赖科目） */}
      {filters.subject && filterOptions?.chapters?.[filters.subject] && (
        <FilterSection
          title="章节"
          expanded={expandedSection === "chapter"}
          onToggle={() => setExpandedSection(expandedSection === "chapter" ? "" : "chapter")}
        >
          <div className="flex flex-wrap gap-2">
            {filterOptions.chapters[filters.subject].map((c) => (
              <FilterChip
                key={c.value}
                active={filters.chapter === c.value}
                onClick={() => updateFilter("chapter", filters.chapter === c.value ? "" : c.value)}
              >
                {c.label}
              </FilterChip>
            ))}
          </div>
        </FilterSection>
      )}

      {/* 题型 */}
      <FilterSection
        title="题型"
        expanded={expandedSection === "type"}
        onToggle={() => setExpandedSection(expandedSection === "type" ? "" : "type")}
      >
        <div className="flex flex-wrap gap-2">
          {filterOptions?.types.map((t) => (
            <FilterChip
              key={t.value}
              active={filters.type === t.value}
              onClick={() => updateFilter("type", filters.type === t.value ? "" : t.value)}
            >
              {t.label}
            </FilterChip>
          ))}
        </div>
      </FilterSection>

      {/* 难度 */}
      <FilterSection
        title="难度"
        expanded={expandedSection === "difficulty"}
        onToggle={() => setExpandedSection(expandedSection === "difficulty" ? "" : "difficulty")}
      >
        <div className="flex gap-1">
          {filterOptions?.difficulties.map((d) => (
            <button
              key={d.value}
              onClick={() =>
                updateFilter("difficulty", filters.difficulty === String(d.value) ? "" : String(d.value))
              }
              className={`px-3 py-1.5 rounded-lg text-xs font-medium transition-colors cursor-pointer
                ${filters.difficulty === String(d.value) ? "bg-amber-100 text-amber-700 border border-amber-300" : "bg-slate-50 text-slate-500 border border-slate-100 hover:bg-slate-100"}`}
            >
              {"★".repeat(d.value)}
            </button>
          ))}
        </div>
      </FilterSection>
    </div>
  );

  return (
    <div className="max-w-6xl mx-auto">
      {/* 顶部操作栏 */}
      <div className="flex items-center gap-3 mb-5 flex-wrap">
        {/* 搜索框 */}
        <div className="flex-1 min-w-[200px] relative">
          <Search
            size={18}
            className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400"
          />
          <input
            type="text"
            value={searchKeyword}
            onChange={(e) => setSearchKeyword(e.target.value)}
            placeholder="搜索题目关键词..."
            className="w-full pl-10 pr-4 py-2.5 rounded-xl border border-slate-200 bg-white text-sm outline-none focus:border-indigo-400 transition-colors"
          />
        </div>

        {/* 移动端筛选按钮 */}
        <button
          onClick={() => setShowMobileFilter(!showMobileFilter)}
          className="lg:hidden flex items-center gap-1.5 px-4 py-2.5 bg-white border border-slate-200 rounded-xl text-sm text-slate-600 hover:bg-slate-50 cursor-pointer"
        >
          <Filter size={16} /> 筛选
          {hasActiveFilters && (
            <span className="w-2 h-2 rounded-full bg-indigo-500" />
          )}
        </button>

        {/* 开始练习 */}
        <button
          onClick={() => onStartPractice?.({
            subject: filters.subject || undefined,
            grade: filters.grade || undefined,
            chapter: filters.chapter || undefined,
            type: filters.type || undefined,
            difficulty: filters.difficulty || undefined,
            lessonId: filters.lessonId || undefined,
          })}
          className="flex items-center gap-1.5 px-5 py-2.5 bg-indigo-500 text-white rounded-xl text-sm font-semibold hover:bg-indigo-600 transition-colors cursor-pointer"
        >
          <Play size={16} /> 开始练习
        </button>

        {/* 导入题目 */}
        <input
          ref={fileInputRef}
          type="file"
          accept=".xlsx,.csv"
          onChange={handleImportFile}
          className="hidden"
        />
        <button
          onClick={() => fileInputRef.current?.click()}
          disabled={importing}
          className="flex items-center gap-1.5 px-4 py-2.5 bg-white border border-slate-200 rounded-xl text-sm text-slate-600 hover:bg-slate-50 disabled:opacity-50 transition-colors cursor-pointer"
          title="批量导入题目（支持 .xlsx / .csv）"
        >
          {importing ? (
            <><div className="w-4 h-4 border-2 border-slate-400 border-t-transparent rounded-full animate-spin" /> 导入中...</>
          ) : (
            <><Upload size={16} /> 导入</>
          )}
        </button>
        {/* 下载模板 */}
        <button
          onClick={handleDownloadTemplate}
          className="flex items-center gap-1.5 px-3 py-2.5 bg-white border border-slate-200 rounded-xl text-sm text-slate-500 hover:bg-slate-50 transition-colors cursor-pointer"
          title="下载 CSV 导入模板"
        >
          <Download size={16} />
        </button>
      </div>

      {/* 导入结果提示 */}
      {importResult && (
        <div className={`mb-4 px-4 py-3 rounded-xl text-sm ${
          importResult.failed === 0 && importResult.inserted > 0
            ? 'bg-emerald-50 text-emerald-700 border border-emerald-200'
            : importResult.errors?.length > 0
              ? 'bg-amber-50 text-amber-700 border border-amber-200'
              : ''
        }`}>
          {importResult.inserted > 0 && (
            <span>✅ 成功导入 {importResult.inserted} 题</span>
          )}
          {importResult.failed > 0 && (
            <span className="ml-3">⚠️ {importResult.failed} 条失败</span>
          )}
          {importResult.errors?.map((err, i) => (
            <div key={i} className="text-xs mt-1 opacity-75">{err}</div>
          ))}
          <button
            onClick={() => setImportResult(null)}
            className="ml-3 text-xs underline cursor-pointer"
          >关闭</button>
        </div>
      )}

      <div className="flex gap-6">
        {/* 左侧筛选面板 - 桌面端 */}
        <div className="hidden lg:block w-56 flex-shrink-0">
          <div className="sticky top-4 bg-white rounded-2xl shadow-sm border border-slate-100 p-5">
            <FilterPanel />
          </div>
        </div>

        {/* 移动端筛选面板 */}
        {showMobileFilter && (
          <div className="fixed inset-0 z-50 lg:hidden">
            <div className="absolute inset-0 bg-black/40" onClick={() => setShowMobileFilter(false)} />
            <div className="absolute right-0 top-0 h-full w-72 bg-white shadow-xl p-5 overflow-y-auto">
              <FilterPanel />
            </div>
          </div>
        )}

        {/* 右侧题目列表 */}
        <div className="flex-1 min-w-0">
          {loading ? (
            <div className="flex items-center justify-center h-48">
              <div className="w-7 h-7 border-2 border-indigo-400 border-t-transparent rounded-full animate-spin" />
            </div>
          ) : questions.length === 0 ? (
            <div className="text-center py-16 text-slate-400">
              <BookOpen size={40} className="mx-auto mb-3 opacity-50" />
              <p>暂无符合条件的题目</p>
            </div>
          ) : (
            <>
              <div className="text-sm text-slate-500 mb-3">
                共 {total} 道题目
              </div>
              <div className="space-y-3">
                {questions.map((q) => (
                  <QuestionListItem
                    key={q.questionId}
                    question={q}
                    onStart={() => onStartPractice?.(q)}
                  />
                ))}
              </div>
              {/* 分页 */}
              {total > 20 && (
                <div className="flex items-center justify-center gap-2 mt-6">
                  <button
                    disabled={page === 1}
                    onClick={() => setPage((p) => p - 1)}
                    className="px-3 py-1.5 text-sm rounded-lg border border-slate-200 disabled:opacity-30 hover:bg-slate-50 cursor-pointer"
                  >
                    上一页
                  </button>
                  <span className="text-sm text-slate-500">
                    第 {page} / {Math.ceil(total / 20)} 页
                  </span>
                  <button
                    disabled={page >= Math.ceil(total / 20)}
                    onClick={() => setPage((p) => p + 1)}
                    className="px-3 py-1.5 text-sm rounded-lg border border-slate-200 disabled:opacity-30 hover:bg-slate-50 cursor-pointer"
                  >
                    下一页
                  </button>
                </div>
              )}
            </>
          )}
        </div>
      </div>
    </div>
  );
}

// ---- 子组件 ----

/** 折叠筛选组 */
function FilterSection({ title, expanded, onToggle, children }) {
  return (
    <div>
      <button
        onClick={onToggle}
        className="flex items-center justify-between w-full text-sm font-semibold text-slate-600 mb-2 cursor-pointer hover:text-slate-800"
      >
        {title}
        {expanded ? <ChevronUp size={16} /> : <ChevronDown size={16} />}
      </button>
      {expanded && <div>{children}</div>}
    </div>
  );
}

/** 筛选 Chip */
function FilterChip({ active, onClick, children }) {
  return (
    <button
      onClick={onClick}
      className={`px-3 py-1.5 rounded-lg text-xs font-medium transition-colors cursor-pointer
        ${active ? "bg-indigo-50 text-indigo-600 border border-indigo-200" : "bg-slate-50 text-slate-500 border border-slate-100 hover:bg-slate-100"}`}
    >
      {children}
    </button>
  );
}

/** 题目列表项 */
function QuestionListItem({ question, onStart }) {
  const typeLabels = {
    single_choice: "单选",
    multi_choice: "多选",
    fill_blank: "填空",
    short_answer: "简答",
  };

  return (
    <div className="bg-white rounded-xl border border-slate-100 p-4 hover:border-indigo-200 hover:shadow-sm transition-all group">
      <div className="flex items-start gap-3">
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 mb-1.5 flex-wrap">
            <span className="px-2 py-0.5 rounded-md text-xs font-medium bg-indigo-50 text-indigo-600">
              {typeLabels[question.type] || question.type}
            </span>
            <span className="text-amber-500 text-xs">
              {"★".repeat(question.difficulty)}
            </span>
            {question.chapter && (
              <span className="px-2 py-0.5 rounded-md text-xs bg-slate-50 text-slate-500">
                {question.chapter}
              </span>
            )}
          </div>
          <p className="text-sm text-slate-700 leading-relaxed line-clamp-2">
            {question.content.stem}
          </p>
        </div>
        <button
          onClick={onStart}
          className="flex-shrink-0 px-3 py-1.5 bg-indigo-50 text-indigo-600 rounded-lg text-xs font-medium opacity-0 group-hover:opacity-100 transition-opacity hover:bg-indigo-100 cursor-pointer"
        >
          去练习
        </button>
      </div>
    </div>
  );
}
