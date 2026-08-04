/**
 * 错题本页（M1 · 5.2.3）
 *
 * 功能：
 * - 错题列表 + 错误原因标签 + 状态筛选
 * - 单题操作：查看解析、重做、删除、标记重点
 * - 批量操作：批量重做
 */
import { useState, useEffect } from "react";
import {
  Search,
  RotateCcw,
  Trash2,
  Star,
  BookOpen,
  AlertCircle,
  CheckCircle2,
  Clock,
  Calendar,
  Tag,
  ChevronDown,
} from "lucide-react";
import { getWrongQuestions, toggleFocus, deleteWrongQuestion } from "../services/practiceService";

export default function WrongQuestionBookPage({ onRedo, onExplain }) {
  const [questions, setQuestions] = useState([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [statusFilter, setStatusFilter] = useState(""); // unresolved / reviewing / resolved
  const [selectedIds, setSelectedIds] = useState([]);
  const [searchKeyword, setSearchKeyword] = useState("");
  const [subjectFilter, setSubjectFilter] = useState("");
  const [kpFilter, setKpFilter] = useState("");
  const [timeFilter, setTimeFilter] = useState("all");

  useEffect(() => {
    loadData();
  }, [statusFilter, subjectFilter, kpFilter, timeFilter]);

  const loadData = async () => {
    setLoading(true);
    try {
      const params = {};
      if (statusFilter) params.status = statusFilter;
      if (searchKeyword) params.keyword = searchKeyword;
      if (subjectFilter) params.subject = subjectFilter;
      if (kpFilter) params.kpName = kpFilter;
      if (timeFilter !== "all") params.timeRange = timeFilter;
      const data = await getWrongQuestions(params);
      setQuestions(data.items);
      setTotal(data.total);
    } catch (err) {
      console.error("加载错题失败:", err);
    } finally {
      setLoading(false);
    }
  };

  const handleToggleFocus = async (id) => {
    await toggleFocus(id);
    setQuestions((prev) =>
      prev.map((q) => (q.id === id ? { ...q, isKeyFocus: !q.isKeyFocus } : q))
    );
  };

  const handleDelete = async (id) => {
    await deleteWrongQuestion(id);
    setQuestions((prev) => prev.filter((q) => q.id !== id));
  };

  const handleBatchRedo = () => {
    onRedo?.(selectedIds);
  };

  const toggleSelect = (id) => {
    setSelectedIds((prev) =>
      prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id]
    );
  };

  const toggleSelectAll = () => {
    if (selectedIds.length === questions.length) {
      setSelectedIds([]);
    } else {
      setSelectedIds(questions.map((q) => q.id));
    }
  };

  const statusTabs = [
    { value: "", label: "全部", count: total },
    { value: "unresolved", label: "未解决", icon: AlertCircle },
    { value: "reviewing", label: "复习中", icon: Clock },
    { value: "resolved", label: "已解决", icon: CheckCircle2 },
  ];

  const reasonLabels = {
    concept_unclear: { label: "概念不清", color: "bg-red-50 text-red-600" },
    careless: { label: "粗心大意", color: "bg-amber-50 text-amber-600" },
    formula_wrong: { label: "公式记错", color: "bg-orange-50 text-orange-600" },
    method_wrong: { label: "方法错误", color: "bg-purple-50 text-purple-600" },
  };

  return (
    <div className="max-w-5xl mx-auto">
      {/* 标题 & 统计 */}
      <div className="flex items-center justify-between mb-5 flex-wrap gap-3">
        <div>
          <h1 className="text-xl font-bold text-slate-800 flex items-center gap-2">
            <BookOpen size={22} className="text-indigo-500" /> 错题本
          </h1>
          <p className="text-sm text-slate-500 mt-1">共 {total} 道错题</p>
        </div>

        {/* 批量操作 */}
        {selectedIds.length > 0 && (
          <button
            onClick={handleBatchRedo}
            className="flex items-center gap-1.5 px-4 py-2 bg-indigo-500 text-white rounded-xl text-sm font-medium hover:bg-indigo-600 cursor-pointer"
          >
            <RotateCcw size={15} /> 批量重做 ({selectedIds.length})
          </button>
        )}
      </div>

      {/* 状态筛选 Tabs */}
      <div className="flex items-center gap-1 mb-4 p-1 bg-slate-100 rounded-xl w-fit flex-wrap">
        {statusTabs.map((tab) => (
          <button
            key={tab.value}
            onClick={() => setStatusFilter(tab.value)}
            className={`px-4 py-2 rounded-lg text-sm font-medium transition-colors cursor-pointer flex items-center gap-1.5
              ${statusFilter === tab.value ? "bg-white text-indigo-600 shadow-sm" : "text-slate-500 hover:text-slate-700"}`}
          >
            {tab.icon && <tab.icon size={15} />}
            {tab.label}
          </button>
        ))}
      </div>

      {/* 扩展筛选：科目 / 知识点 / 时间 */}
      <div className="flex items-center gap-2 mb-3 flex-wrap">
        <select
          value={subjectFilter}
          onChange={(e) => setSubjectFilter(e.target.value)}
          className="px-3 py-1.5 rounded-lg border border-slate-200 bg-white text-xs text-slate-600 outline-none focus:border-indigo-300 cursor-pointer"
        >
          <option value="">全部科目</option>
          <option value="math">数学</option>
          <option value="chinese">语文</option>
          <option value="english">英语</option>
          <option value="physics">物理</option>
        </select>
        <select
          value={kpFilter}
          onChange={(e) => setKpFilter(e.target.value)}
          className="px-3 py-1.5 rounded-lg border border-slate-200 bg-white text-xs text-slate-600 outline-none focus:border-indigo-300 cursor-pointer"
        >
          <option value="">全部知识点</option>
          <option value="勾股定理">勾股定理</option>
          <option value="相似三角形">相似三角形</option>
          <option value="等腰三角形性质">等腰三角形性质</option>
        </select>
        <div className="flex items-center gap-0.5 p-0.5 bg-slate-100 rounded-lg">
          {[
            { value: "all", label: "全部时间" },
            { value: "week", label: "近一周" },
            { value: "month", label: "近一月" },
          ].map((t) => (
            <button
              key={t.value}
              onClick={() => setTimeFilter(t.value)}
              className={`px-3 py-1 rounded-md text-xs font-medium transition-colors cursor-pointer
                ${timeFilter === t.value ? "bg-white text-indigo-600 shadow-sm" : "text-slate-500 hover:text-slate-700"}`}
            >
              {t.label}
            </button>
          ))}
        </div>
      </div>

      {/* 搜索框 */}
      <div className="relative mb-4 max-w-sm">
        <Search size={17} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
        <input
          type="text"
          value={searchKeyword}
          onChange={(e) => setSearchKeyword(e.target.value)}
          placeholder="搜索错题..."
          className="w-full pl-9 pr-4 py-2 rounded-xl border border-slate-200 bg-white text-sm outline-none focus:border-indigo-400"
        />
      </div>

      {/* 列表 */}
      {loading ? (
        <div className="flex justify-center py-16">
          <div className="w-7 h-7 border-2 border-indigo-400 border-t-transparent rounded-full animate-spin" />
        </div>
      ) : questions.length === 0 ? (
        <div className="text-center py-16 text-slate-400">
          <BookOpen size={48} className="mx-auto mb-3 opacity-40" />
          <p className="text-lg font-medium">暂无错题</p>
          <p className="text-sm mt-1">去练习几道题吧！</p>
        </div>
      ) : (
        <div className="space-y-3">
          {/* 全选 */}
          <div className="flex items-center gap-2 px-1">
            <input
              type="checkbox"
              checked={selectedIds.length === questions.length}
              onChange={toggleSelectAll}
              className="w-4 h-4 rounded border-slate-300 text-indigo-500 focus:ring-indigo-400"
            />
            <span className="text-xs text-slate-400">
              {selectedIds.length > 0 ? `已选 ${selectedIds.length} 题` : "全选"}
            </span>
          </div>

          {questions.map((q) => (
            <div
              key={q.id}
              className="bg-white rounded-xl border border-slate-100 p-4 hover:border-indigo-200 hover:shadow-sm transition-all group"
            >
              <div className="flex items-start gap-3">
                {/* 复选框 */}
                <input
                  type="checkbox"
                  checked={selectedIds.includes(q.id)}
                  onChange={() => toggleSelect(q.id)}
                  className="mt-1.5 w-4 h-4 rounded border-slate-300 text-indigo-500 focus:ring-indigo-400"
                />

                {/* 内容 */}
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2 mb-1.5 flex-wrap">
                    {/* 错误原因 */}
                    {q.wrongReasonTag && reasonLabels[q.wrongReasonTag] && (
                      <span
                        className={`px-2 py-0.5 rounded-md text-xs font-medium ${reasonLabels[q.wrongReasonTag].color}`}
                      >
                        {reasonLabels[q.wrongReasonTag].label}
                      </span>
                    )}
                    {/* 状态 */}
                    <span
                      className={`px-2 py-0.5 rounded-md text-xs font-medium
                        ${q.status === "unresolved" ? "bg-red-50 text-red-500" : ""}
                        ${q.status === "reviewing" ? "bg-amber-50 text-amber-500" : ""}
                        ${q.status === "resolved" ? "bg-emerald-50 text-emerald-500" : ""}`}
                    >
                      {q.status === "unresolved" ? "未解决" : q.status === "reviewing" ? "复习中" : "已解决"}
                    </span>
                    {/* 时间 */}
                    <span className="text-xs text-slate-400">{q.createdAt?.slice(0, 10)}</span>
                  </div>

                  <p className="text-sm text-slate-700 leading-relaxed line-clamp-2">
                    {q.questionContent?.stem}
                  </p>

                  {/* 知识点 */}
                  <div className="flex items-center gap-1.5 mt-2 flex-wrap">
                    {q.knowledgePoints?.map((kp) => (
                      <span
                        key={kp.id}
                        className="px-2 py-0.5 rounded-md text-xs bg-indigo-50 text-indigo-500"
                      >
                        {kp.name}
                      </span>
                    ))}
                  </div>
                </div>

                {/* 操作按钮 */}
                <div className="flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity flex-shrink-0">
                  <button
                    onClick={() => handleToggleFocus(q.id)}
                    className={`p-2 rounded-lg cursor-pointer transition-colors
                      ${q.isKeyFocus ? "text-amber-500 bg-amber-50" : "text-slate-400 hover:text-amber-500 hover:bg-amber-50"}`}
                    title="标记重点"
                  >
                    <Star size={16} fill={q.isKeyFocus ? "currentColor" : "none"} />
                  </button>
                  <button
                    onClick={() => onRedo?.([q.id])}
                    className="p-2 text-slate-400 hover:text-indigo-500 hover:bg-indigo-50 rounded-lg cursor-pointer transition-colors"
                    title="重做"
                  >
                    <RotateCcw size={16} />
                  </button>
                  <button
                    onClick={() => onExplain?.(q)}
                    className="p-2 text-slate-400 hover:text-violet-500 hover:bg-violet-50 rounded-lg cursor-pointer transition-colors"
                    title="查看讲解"
                  >
                    <BookOpen size={16} />
                  </button>
                  <button
                    onClick={() => handleDelete(q.id)}
                    className="p-2 text-slate-400 hover:text-red-400 hover:bg-red-50 rounded-lg cursor-pointer transition-colors"
                    title="删除"
                  >
                    <Trash2 size={16} />
                  </button>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
