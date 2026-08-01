/**
 * 题库浏览与筛选页（M1 · 5.2.1）
 *
 * 左侧筛选面板 + 右侧题目列表
 * 筛选条件：科目 → 年级 → 章节 → 题型 → 难度
 */
import { useCallback, useState, useEffect, useRef } from "react";
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
  Plus,
  Pencil,
  Trash2,
} from "lucide-react";
import {
  createQuestion,
  deleteQuestion,
  getFilterOptions,
  getQuestionForEditing,
  getQuestions,
  importQuestions,
  updateQuestion,
} from "../services/practiceService";

const createEmptyQuestionForm = (subject = "数学", gradeLevel = "大学") => ({
  subject,
  gradeLevel,
  track: "",
  chapter: "",
  knowledgePoint: "",
  questionType: "SINGLE_CHOICE",
  difficulty: "BASIC",
  title: "",
  content: "",
  optionA: "",
  optionB: "",
  optionC: "",
  optionD: "",
  correctAnswer: "",
  answerKeywords: "",
  analysis: "",
  lessonId: "",
  status: "ENABLED",
});

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
  const [showEditor, setShowEditor] = useState(false);
  const [editingQuestionId, setEditingQuestionId] = useState(null);
  const [questionForm, setQuestionForm] = useState(createEmptyQuestionForm());
  const [editorLoading, setEditorLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [formError, setFormError] = useState("");
  const [deleteTarget, setDeleteTarget] = useState(null);
  const [deleting, setDeleting] = useState(false);
  const [actionNotice, setActionNotice] = useState("");

  // 导入状态
  const [importing, setImporting] = useState(false);
  const [importResult, setImportResult] = useState(null);

  // 文件输入 ref
  const fileInputRef = useRef(null);

  const loadQuestions = useCallback(async (targetPage = page) => {
    setLoading(true);
    try {
      const params = { page: targetPage, size: 20 };
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
  }, [filters, page, searchKeyword]);

  // 加载筛选选项
  useEffect(() => {
    getFilterOptions().then(setFilterOptions);
  }, []);

  // 加载题目
  useEffect(() => {
    loadQuestions();
  }, [loadQuestions]);

  const updateFilter = (key, value) => {
    setFilters((prev) => ({ ...prev, [key]: value }));
    setPage(1);
  };

  const clearFilters = () => {
    setFilters({ subject: "", grade: "", chapter: "", type: "", difficulty: "", lessonId: "" });
    setSearchKeyword("");
    setPage(1);
  };

  // 导入 Excel / Word / PDF
  const handleImportFile = async (e) => {
    const file = e.target.files?.[0];
    if (!file) return;
    setImporting(true);
    setImportResult(null);
    try {
      const result = await importQuestions(file);
      setImportResult(result);
      setPage(1);
      // 导入后的新题按创建时间排在最前面
      await loadQuestions(1);
    } catch (err) {
      setImportResult({ inserted: 0, failed: 1, errors: [err.message] });
    } finally {
      setImporting(false);
      // 重置 input 以便再次选择同一文件
      if (fileInputRef.current) fileInputRef.current.value = '';
    }
  };

  const refreshFilterOptions = async () => {
    try {
      setFilterOptions(await getFilterOptions());
    } catch (err) {
      console.warn("刷新筛选项失败:", err);
    }
  };

  const openCreateEditor = () => {
    const selectedSubject = filterOptions?.subjects?.find((item) => item.value === filters.subject)?.label || "数学";
    const selectedGrade = filters.grade || filterOptions?.grades?.[0]?.label || "大学";
    setEditingQuestionId(null);
    setQuestionForm(createEmptyQuestionForm(selectedSubject, selectedGrade));
    setFormError("");
    setShowEditor(true);
  };

  const openEditEditor = async (questionId) => {
    setEditingQuestionId(questionId);
    setQuestionForm(createEmptyQuestionForm());
    setFormError("");
    setEditorLoading(true);
    setShowEditor(true);
    try {
      setQuestionForm(await getQuestionForEditing(questionId));
    } catch (err) {
      setFormError(err.message || "读取题目详情失败");
    } finally {
      setEditorLoading(false);
    }
  };

  const validateQuestionForm = (form) => {
    const requiredFields = [
      ["title", "请填写题目标题"],
      ["content", "请填写题干"],
      ["subject", "请填写科目"],
      ["gradeLevel", "请填写年级或学段"],
      ["chapter", "请填写章节"],
      ["knowledgePoint", "请填写知识点"],
      ["correctAnswer", "请填写标准答案"],
    ];
    for (const [key, message] of requiredFields) {
      if (!form[key]?.trim()) return message;
    }
    const isChoice = form.questionType === "SINGLE_CHOICE" || form.questionType === "MULTIPLE_CHOICE";
    if (isChoice && (!form.optionA.trim() || !form.optionB.trim())) return "选择题至少需要填写 A、B 两个选项";
    const normalizedAnswer = form.correctAnswer.trim().toUpperCase().replaceAll("，", ",").replaceAll(" ", "");
    if (form.questionType === "SINGLE_CHOICE" && !/^[A-D]$/.test(normalizedAnswer)) return "单选题答案请填写 A、B、C 或 D";
    if (form.questionType === "MULTIPLE_CHOICE" && !/^[A-D](,[A-D])+$/.test(normalizedAnswer)) return "多选题答案请按 A,B 的格式填写";
    return "";
  };

  const handleSaveQuestion = async (event) => {
    event.preventDefault();
    const validationMessage = validateQuestionForm(questionForm);
    if (validationMessage) {
      setFormError(validationMessage);
      return;
    }
    setSaving(true);
    setFormError("");
    const isChoice = questionForm.questionType === "SINGLE_CHOICE" || questionForm.questionType === "MULTIPLE_CHOICE";
    const payload = {
      ...questionForm,
      track: questionForm.track.trim() || questionForm.subject.trim(),
      optionA: isChoice ? questionForm.optionA.trim() : "",
      optionB: isChoice ? questionForm.optionB.trim() : "",
      optionC: isChoice ? questionForm.optionC.trim() : "",
      optionD: isChoice ? questionForm.optionD.trim() : "",
      correctAnswer: isChoice
        ? questionForm.correctAnswer.trim().toUpperCase().replaceAll("，", ",").replaceAll(" ", "")
        : questionForm.correctAnswer.trim(),
    };
    try {
      if (editingQuestionId) {
        await updateQuestion(editingQuestionId, payload);
        setActionNotice("题目修改成功");
        await loadQuestions(page);
      } else {
        await createQuestion(payload);
        setActionNotice("题目新增成功");
        setPage(1);
        await loadQuestions(1);
      }
      await refreshFilterOptions();
      setShowEditor(false);
    } catch (err) {
      setFormError(err.message || "保存题目失败");
    } finally {
      setSaving(false);
    }
  };

  const handleDeleteQuestion = async () => {
    if (!deleteTarget) return;
    setDeleting(true);
    try {
      await deleteQuestion(deleteTarget.questionId);
      const targetPage = questions.length === 1 && page > 1 ? page - 1 : page;
      setPage(targetPage);
      await loadQuestions(targetPage);
      await refreshFilterOptions();
      setActionNotice("题目已删除");
      setDeleteTarget(null);
    } catch (err) {
      setActionNotice(err.message || "删除题目失败");
    } finally {
      setDeleting(false);
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

        {/* 新增题目 */}
        <button
          onClick={openCreateEditor}
          className="flex items-center gap-1.5 px-4 py-2.5 bg-white border border-indigo-200 rounded-xl text-sm font-medium text-indigo-600 hover:bg-indigo-50 transition-colors cursor-pointer"
        >
          <Plus size={16} /> 新增题目
        </button>

        {/* 导入题目 */}
        <input
          ref={fileInputRef}
          type="file"
          accept=".xlsx,.xls,.csv,.docx,.doc,.pdf,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document,application/msword"
          onChange={handleImportFile}
          className="hidden"
        />
        <button
          onClick={() => fileInputRef.current?.click()}
          disabled={importing}
          className="flex items-center gap-1.5 px-4 py-2.5 bg-white border border-slate-200 rounded-xl text-sm text-slate-600 hover:bg-slate-50 disabled:opacity-50 transition-colors cursor-pointer"
          title="批量导入题目（支持 Excel / Word / PDF）"
        >
          {importing ? (
            <><div className="w-4 h-4 border-2 border-slate-400 border-t-transparent rounded-full animate-spin" /> 导入中...</>
          ) : (
            <><Upload size={16} /> 导入</>
          )}
        </button>
      </div>

      {actionNotice && (
        <div className="mb-4 flex items-center justify-between rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-700">
          <span>{actionNotice}</span>
          <button onClick={() => setActionNotice("")} className="text-xs underline cursor-pointer">关闭</button>
        </div>
      )}

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
          {importResult.pendingReview > 0 && (
            <span className="ml-3">📝 {importResult.pendingReview} 题缺少答案，已设为待完善</span>
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
                    onEdit={() => openEditEditor(q.questionId)}
                    onDelete={() => setDeleteTarget(q)}
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

      {showEditor && (
        <QuestionEditorModal
          form={questionForm}
          setForm={setQuestionForm}
          editing={Boolean(editingQuestionId)}
          loading={editorLoading}
          saving={saving}
          error={formError}
          subjects={filterOptions?.subjects || []}
          grades={filterOptions?.grades || []}
          onClose={() => !saving && setShowEditor(false)}
          onSubmit={handleSaveQuestion}
        />
      )}

      {deleteTarget && (
        <DeleteQuestionDialog
          question={deleteTarget}
          deleting={deleting}
          onCancel={() => !deleting && setDeleteTarget(null)}
          onConfirm={handleDeleteQuestion}
        />
      )}
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
function QuestionListItem({ question, onStart, onEdit, onDelete }) {
  const typeLabels = {
    single_choice: "单选",
    multi_choice: "多选",
    fill_blank: "填空",
    short_answer: "简答",
  };
  const knowledgeTag = question.knowledgePoints?.[0]?.name || question.chapter;
  const difficulty = Math.max(1, Math.min(5, Number(question.difficulty) || 1));

  return (
    <div className="group rounded-2xl border border-slate-100 bg-white px-5 py-5 shadow-sm transition-all hover:-translate-y-0.5 hover:border-indigo-200 hover:shadow-md">
      <div className="flex items-start gap-3">
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 mb-1.5 flex-wrap">
            <span className="px-2 py-0.5 rounded-md text-xs font-medium bg-indigo-50 text-indigo-600">
              {typeLabels[question.type] || question.type}
            </span>
            <span className="text-sm tracking-tight text-amber-500" aria-label={`难度 ${difficulty} 星`}>
              {"★".repeat(difficulty)}
            </span>
            {knowledgeTag && (
              <span className="rounded-md bg-slate-50 px-2 py-0.5 text-xs text-slate-500">
                {knowledgeTag}
              </span>
            )}
            {question.status === "DISABLED" && (
              <span className="rounded-md bg-amber-50 px-2 py-0.5 text-xs font-medium text-amber-600">待完善</span>
            )}
          </div>
          <p className="line-clamp-2 text-[15px] leading-7 text-slate-700">
            {question.content.stem}
          </p>
        </div>
        <div className="flex flex-shrink-0 items-center gap-1.5">
          <button
            onClick={onEdit}
            className="flex items-center gap-1 rounded-lg px-2.5 py-1.5 text-xs font-medium text-slate-500 hover:bg-slate-100 hover:text-indigo-600 cursor-pointer"
            title="编辑题目"
          >
            <Pencil size={14} /> 编辑
          </button>
          <button
            onClick={onDelete}
            className="flex items-center gap-1 rounded-lg px-2.5 py-1.5 text-xs font-medium text-slate-500 hover:bg-red-50 hover:text-red-500 cursor-pointer"
            title="删除题目"
          >
            <Trash2 size={14} /> 删除
          </button>
          <button
            onClick={onStart}
            disabled={question.status === "DISABLED"}
            className="px-3 py-1.5 bg-indigo-50 text-indigo-600 rounded-lg text-xs font-medium transition-colors hover:bg-indigo-100 disabled:cursor-not-allowed disabled:opacity-50 cursor-pointer"
          >
            {question.status === "DISABLED" ? "待完善" : "去练习"}
          </button>
        </div>
      </div>
    </div>
  );
}

function QuestionEditorModal({ form, setForm, editing, loading, saving, error, subjects, grades, onClose, onSubmit }) {
  const updateField = (field, value) => setForm((prev) => ({ ...prev, [field]: value }));
  const isChoice = form.questionType === "SINGLE_CHOICE" || form.questionType === "MULTIPLE_CHOICE";
  const inputClass = "w-full rounded-xl border border-slate-200 bg-white px-3 py-2 text-sm text-slate-700 outline-none transition-colors focus:border-indigo-400";

  return (
    <div className="fixed inset-0 z-[70] flex items-center justify-center p-4">
      <div className="absolute inset-0 bg-slate-900/45" onClick={onClose} />
      <form
        role="dialog"
        aria-modal="true"
        aria-labelledby="question-editor-title"
        onSubmit={onSubmit}
        className="relative max-h-[92vh] w-full max-w-4xl overflow-y-auto rounded-2xl bg-white shadow-2xl"
      >
        <div className="sticky top-0 z-10 flex items-center justify-between border-b border-slate-100 bg-white px-6 py-4">
          <div>
            <h2 id="question-editor-title" className="text-lg font-bold text-slate-800">
              {editing ? "编辑题目" : "新增题目"}
            </h2>
            <p className="mt-0.5 text-xs text-slate-400">带 * 的字段为必填项</p>
          </div>
          <button type="button" onClick={onClose} disabled={saving} className="rounded-lg p-2 text-slate-400 hover:bg-slate-100 cursor-pointer">
            <X size={18} />
          </button>
        </div>

        {loading ? (
          <div className="flex h-64 items-center justify-center">
            <div className="h-8 w-8 animate-spin rounded-full border-2 border-indigo-400 border-t-transparent" />
          </div>
        ) : (
          <div className="space-y-5 px-6 py-5">
            {error && (
              <div className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-600">{error}</div>
            )}

            <div className="grid gap-4 md:grid-cols-2">
              <EditorField label="题目标题" required>
                <input className={inputClass} value={form.title} onChange={(e) => updateField("title", e.target.value)} placeholder="例如：函数值计算" />
              </EditorField>
              <EditorField label="科目" required>
                <input className={inputClass} list="question-subject-list" value={form.subject} onChange={(e) => updateField("subject", e.target.value)} placeholder="例如：数学" />
                <datalist id="question-subject-list">
                  {subjects.map((item) => <option key={item.value} value={item.label} />)}
                </datalist>
              </EditorField>
              <EditorField label="年级 / 学段" required>
                <input className={inputClass} list="question-grade-list" value={form.gradeLevel} onChange={(e) => updateField("gradeLevel", e.target.value)} placeholder="例如：高中" />
                <datalist id="question-grade-list">
                  {grades.map((item) => <option key={item.value} value={item.label} />)}
                </datalist>
              </EditorField>
              <EditorField label="题库 / 方向">
                <input className={inputClass} value={form.track} onChange={(e) => updateField("track", e.target.value)} placeholder="留空时默认使用科目" />
              </EditorField>
              <EditorField label="章节" required>
                <input className={inputClass} value={form.chapter} onChange={(e) => updateField("chapter", e.target.value)} placeholder="例如：导数与微分" />
              </EditorField>
              <EditorField label="知识点" required>
                <input className={inputClass} value={form.knowledgePoint} onChange={(e) => updateField("knowledgePoint", e.target.value)} placeholder="例如：导数计算" />
              </EditorField>
              <EditorField label="题型" required>
                <select className={inputClass} value={form.questionType} onChange={(e) => updateField("questionType", e.target.value)}>
                  <option value="SINGLE_CHOICE">单选题</option>
                  <option value="MULTIPLE_CHOICE">多选题</option>
                  <option value="FILL_BLANK">填空题</option>
                  <option value="SHORT_ANSWER">简答题</option>
                </select>
              </EditorField>
              <EditorField label="难度" required>
                <select className={inputClass} value={form.difficulty} onChange={(e) => updateField("difficulty", e.target.value)}>
                  <option value="BASIC">基础（★）</option>
                  <option value="ADVANCED">进阶（★★★）</option>
                  <option value="HARD">困难（★★★★★）</option>
                </select>
              </EditorField>
            </div>

            <EditorField label="题干" required>
              <textarea className={`${inputClass} min-h-24 resize-y`} value={form.content} onChange={(e) => updateField("content", e.target.value)} placeholder="请输入完整题目内容" />
            </EditorField>

            {isChoice && (
              <div className="rounded-2xl border border-slate-100 bg-slate-50/70 p-4">
                <h3 className="mb-3 text-sm font-semibold text-slate-700">选项设置</h3>
                <div className="grid gap-3 md:grid-cols-2">
                  {["A", "B", "C", "D"].map((key) => (
                    <EditorField key={key} label={`选项 ${key}`} required={key === "A" || key === "B"}>
                      <input className={inputClass} value={form[`option${key}`]} onChange={(e) => updateField(`option${key}`, e.target.value)} placeholder={`请输入选项 ${key}`} />
                    </EditorField>
                  ))}
                </div>
              </div>
            )}

            <div className="grid gap-4 md:grid-cols-2">
              <EditorField label="标准答案" required>
                <input
                  className={inputClass}
                  value={form.correctAnswer}
                  onChange={(e) => updateField("correctAnswer", e.target.value)}
                  placeholder={form.questionType === "MULTIPLE_CHOICE" ? "例如：A,B" : form.questionType === "SINGLE_CHOICE" ? "例如：A" : "请输入参考答案"}
                />
              </EditorField>
              <EditorField label="答案关键词">
                <input className={inputClass} value={form.answerKeywords} onChange={(e) => updateField("answerKeywords", e.target.value)} placeholder="多个关键词用逗号分隔" />
              </EditorField>
            </div>

            <EditorField label="答案解析">
              <textarea className={`${inputClass} min-h-24 resize-y`} value={form.analysis} onChange={(e) => updateField("analysis", e.target.value)} placeholder="请输入解题步骤或知识点说明" />
            </EditorField>

            <EditorField label="题目状态">
              <select className={`${inputClass} md:w-56`} value={form.status} onChange={(e) => updateField("status", e.target.value)}>
                <option value="ENABLED">启用</option>
                <option value="DISABLED">停用 / 待完善</option>
              </select>
            </EditorField>
          </div>
        )}

        {!loading && (
          <div className="sticky bottom-0 flex items-center justify-end gap-3 border-t border-slate-100 bg-white px-6 py-4">
            <button type="button" onClick={onClose} disabled={saving} className="rounded-xl border border-slate-200 px-4 py-2 text-sm text-slate-600 hover:bg-slate-50 disabled:opacity-50 cursor-pointer">取消</button>
            <button type="submit" disabled={saving} className="rounded-xl bg-indigo-500 px-5 py-2 text-sm font-semibold text-white hover:bg-indigo-600 disabled:opacity-50 cursor-pointer">
              {saving ? "保存中..." : editing ? "保存修改" : "创建题目"}
            </button>
          </div>
        )}
      </form>
    </div>
  );
}

function EditorField({ label, required = false, children }) {
  return (
    <label className="block text-sm text-slate-600">
      <span className="mb-1.5 block font-medium text-slate-600">
        {label}{required && <span className="ml-0.5 text-red-400">*</span>}
      </span>
      {children}
    </label>
  );
}

function DeleteQuestionDialog({ question, deleting, onCancel, onConfirm }) {
  return (
    <div className="fixed inset-0 z-[80] flex items-center justify-center p-4">
      <div className="absolute inset-0 bg-slate-900/45" onClick={onCancel} />
      <div role="alertdialog" aria-modal="true" aria-labelledby="delete-question-title" className="relative w-full max-w-md rounded-2xl bg-white p-6 shadow-2xl">
        <div className="mb-4 flex h-11 w-11 items-center justify-center rounded-full bg-red-50 text-red-500">
          <Trash2 size={20} />
        </div>
        <h2 id="delete-question-title" className="text-lg font-bold text-slate-800">确认删除这道题？</h2>
        <p className="mt-2 text-sm leading-6 text-slate-500">
          {question.title || question.content?.stem}
        </p>
        <p className="mt-2 text-xs text-slate-400">如果已有答题记录，系统会归档题目并保留历史统计。</p>
        <div className="mt-6 flex justify-end gap-3">
          <button onClick={onCancel} disabled={deleting} className="rounded-xl border border-slate-200 px-4 py-2 text-sm text-slate-600 hover:bg-slate-50 disabled:opacity-50 cursor-pointer">取消</button>
          <button onClick={onConfirm} disabled={deleting} className="rounded-xl bg-red-500 px-4 py-2 text-sm font-semibold text-white hover:bg-red-600 disabled:opacity-50 cursor-pointer">
            {deleting ? "删除中..." : "确认删除"}
          </button>
        </div>
      </div>
    </div>
  );
}
