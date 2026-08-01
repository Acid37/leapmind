/**
 * 练习统计页：趋势、题型/难度/章节拆分、知识点掌握度和复习建议。
 */
import { useEffect, useState } from "react";
import {
  Activity,
  BarChart3,
  CalendarDays,
  CheckCircle2,
  Clock,
  Lightbulb,
  Target,
  TrendingDown,
  TrendingUp,
  XCircle,
} from "lucide-react";
import { getStatistics } from "../services/practiceService";

const PERIODS = [
  { value: "week", label: "近一周" },
  { value: "month", label: "近一月" },
  { value: "all", label: "全部" },
];

const TYPE_LABELS = {
  SINGLE_CHOICE: "单选题",
  MULTIPLE_CHOICE: "多选题",
  FILL_BLANK: "填空题",
  SHORT_ANSWER: "简答题",
  single_choice: "单选题",
  multi_choice: "多选题",
  fill_blank: "填空题",
  short_answer: "简答题",
};

const DIFFICULTY_LABELS = {
  BASIC: "基础",
  ADVANCED: "进阶",
  HARD: "困难",
};

export default function StatisticsPage() {
  const [stats, setStats] = useState(null);
  const [loading, setLoading] = useState(true);
  const [period, setPeriod] = useState("week");

  useEffect(() => {
    let active = true;
    setLoading(true);
    getStatistics({ period })
      .then((data) => active && setStats(data))
      .catch((error) => console.error("加载统计数据失败:", error))
      .finally(() => active && setLoading(false));
    return () => { active = false; };
  }, [period]);

  if (loading) {
    return (
      <div className="flex justify-center py-24">
        <div className="h-7 w-7 animate-spin rounded-full border-2 border-indigo-400 border-t-transparent" />
      </div>
    );
  }

  if (!stats) return null;

  const summary = stats.summary || {};
  const dailyTrend = stats.dailyTrend || [];
  const byKnowledgePoint = stats.byKnowledgePoint || [];
  const byQuestionType = stats.byQuestionType || [];
  const byDifficulty = stats.byDifficulty || [];
  const byChapter = stats.byChapter || [];
  const wrongReasons = stats.wrongReasons || [];
  const recommendations = stats.recommendations?.length
    ? stats.recommendations
    : ["继续完成练习，系统会根据新增答题记录生成更具体的复习建议。"];
  const total = summary.totalQuestions || 0;
  const correct = summary.correctQuestions ?? Math.round(total * (summary.correctRate || 0));
  const incorrect = summary.incorrectQuestions ?? Math.max(0, total - correct);

  return (
    <div className="mx-auto max-w-6xl space-y-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="flex items-center gap-2 text-xl font-bold text-slate-800">
            <BarChart3 size={22} className="text-indigo-500" /> 刷题分析
          </h1>
          <p className="mt-1 text-sm text-slate-500">从正确率、用时、题型和知识点定位刷题后的具体问题。</p>
        </div>
        <div className="flex w-fit items-center gap-1 rounded-xl bg-slate-100 p-1">
          {PERIODS.map((item) => (
            <button
              key={item.value}
              onClick={() => setPeriod(item.value)}
              className={`cursor-pointer rounded-lg px-4 py-2 text-sm font-medium transition-colors ${
                period === item.value ? "bg-white text-indigo-600 shadow-sm" : "text-slate-500 hover:text-slate-700"
              }`}
            >
              {item.label}
            </button>
          ))}
        </div>
      </div>

      <div className="grid grid-cols-2 gap-3 md:grid-cols-3 xl:grid-cols-6">
        <SummaryCard icon={<Target size={19} />} label="完成题数" value={total} tone="indigo" />
        <SummaryCard icon={<CheckCircle2 size={19} />} label="答对" value={correct} tone="emerald" />
        <SummaryCard icon={<XCircle size={19} />} label="答错" value={incorrect} tone="rose" />
        <SummaryCard icon={<TrendingUp size={19} />} label="正确率" value={`${Math.round((summary.correctRate || 0) * 100)}%`} tone="cyan" />
        <SummaryCard icon={<Clock size={19} />} label="平均每题" value={formatDuration(summary.avgTimeSpent || 0)} tone="amber" />
        <SummaryCard icon={<CalendarDays size={19} />} label="活跃天数" value={`${summary.activeDays || 0} 天`} tone="violet" />
      </div>

      <div className="grid grid-cols-1 gap-5 lg:grid-cols-3">
        <section className="rounded-2xl border border-slate-100 bg-white p-5 shadow-sm lg:col-span-2">
          <div className="mb-4 flex flex-wrap items-start justify-between gap-2">
            <div>
              <h2 className="font-semibold text-slate-700">正确率与做题量趋势</h2>
              <p className="mt-1 text-xs text-slate-400">折线为正确率，柱形为当天完成题数</p>
            </div>
            <TrendBadge summary={summary} />
          </div>
          <TrendChart data={dailyTrend} />
        </section>

        <section className="rounded-2xl border border-indigo-100 bg-gradient-to-br from-indigo-50 to-white p-5 shadow-sm">
          <h2 className="flex items-center gap-2 font-semibold text-slate-700">
            <Lightbulb size={18} className="text-indigo-500" /> 本周期建议
          </h2>
          <div className="mt-4 space-y-3">
            {recommendations.map((item, index) => (
              <div key={`${item}-${index}`} className="flex gap-3 rounded-xl bg-white/80 p-3 text-sm leading-6 text-slate-600 ring-1 ring-indigo-100">
                <span className="flex h-6 w-6 flex-shrink-0 items-center justify-center rounded-full bg-indigo-100 text-xs font-bold text-indigo-600">{index + 1}</span>
                <span>{item}</span>
              </div>
            ))}
          </div>
        </section>
      </div>

      <section className="rounded-2xl border border-slate-100 bg-white p-5 shadow-sm">
        <div className="border-b border-slate-100 pb-4">
          <div>
            <h2 className="flex items-center gap-2 font-semibold text-slate-700">
              <Activity size={18} className="text-indigo-500" /> 知识点掌握度
            </h2>
            <p className="mt-1 text-xs text-slate-400">薄弱项优先排列，避免一次答对就被判断为“已掌握”。</p>
          </div>
        </div>

        {byKnowledgePoint.length === 0 ? (
          <EmptyState text="暂无知识点答题数据" />
        ) : (
          <div className="mt-4 space-y-3">
            {byKnowledgePoint.map((point) => {
              const mastery = masteryOf(point);
              const accuracy = accuracyOf(point);
              const recentAccuracy = recentAccuracyOf(point);
              const level = point.masteryLevel || levelOf(mastery, point.total);
              return (
                <article key={point.kpId || point.kpName} className="rounded-xl border border-slate-100 p-4 transition-colors hover:border-indigo-100 hover:bg-indigo-50/20">
                  <div className="flex flex-col gap-3 lg:flex-row lg:items-center">
                    <div className="min-w-0 lg:w-48">
                      <div className="flex items-center gap-2">
                        <span className="truncate font-medium text-slate-700">{point.kpName || "未分类"}</span>
                        <MasteryBadge level={level} />
                      </div>
                      <p className="mt-1 truncate text-xs text-slate-400">{point.recommendation || "继续练习以完善判断"}</p>
                    </div>
                    <div className="flex-1">
                      <div className="mb-1 flex items-center justify-between text-xs text-slate-500">
                        <span>综合掌握度</span>
                        <span className="font-semibold text-slate-700">{Math.round(mastery)}%</span>
                      </div>
                      <div className="h-2 overflow-hidden rounded-full bg-slate-100">
                        <div className={`h-full rounded-full ${masteryBarColor(level)}`} style={{ width: `${Math.max(2, mastery)}%` }} />
                      </div>
                    </div>
                    <div className="grid grid-cols-4 gap-3 text-center text-xs lg:w-[360px]">
                      <Metric label="历史正确率" value={`${Math.round(accuracy * 100)}%`} />
                      <Metric label="最近表现" value={`${Math.round(recentAccuracy * 100)}%`} />
                      <Metric label="样本数" value={`${point.total || 0} 题`} />
                      <Metric
                        label="近期变化"
                        value={`${point.recentTrend > 0 ? "+" : ""}${Math.round(point.recentTrend || 0)}%`}
                        valueClass={point.recentTrend > 0 ? "text-emerald-600" : point.recentTrend < 0 ? "text-rose-600" : "text-slate-700"}
                      />
                    </div>
                  </div>
                </article>
              );
            })}
          </div>
        )}
      </section>

      <div className="grid grid-cols-1 gap-5 lg:grid-cols-3">
        <BreakdownCard title="按题型分析" items={byQuestionType} labelMap={TYPE_LABELS} />
        <BreakdownCard title="按难度分析" items={byDifficulty} labelMap={DIFFICULTY_LABELS} />
        <BreakdownCard title="按章节分析" items={byChapter.slice(0, 6)} />
      </div>

      <section className="rounded-2xl border border-slate-100 bg-white p-5 shadow-sm">
        <div className="flex flex-wrap items-start justify-between gap-2">
          <div>
            <h2 className="font-semibold text-slate-700">错误原因线索</h2>
            <p className="mt-1 text-xs text-slate-400">根据题型、答题用时、重复作答和判题反馈推断，用于辅助复盘。</p>
          </div>
          <span className="rounded-full bg-amber-50 px-3 py-1 text-xs text-amber-700">推断结果，不等同于人工诊断</span>
        </div>
        {wrongReasons.length === 0 ? (
          <EmptyState text="当前周期暂无错题" />
        ) : (
          <WrongReasonBars items={wrongReasons} />
        )}
      </section>
    </div>
  );
}

function TrendChart({ data }) {
  if (!data.length) return <EmptyState text="暂无趋势数据" />;
  const width = 720;
  const height = 230;
  const left = 38;
  const right = 16;
  const top = 18;
  const bottom = 34;
  const innerW = width - left - right;
  const innerH = height - top - bottom;
  const denominator = Math.max(1, data.length - 1);
  const maxCount = Math.max(1, ...data.map((item) => item.count || 0));
  const labelStep = Math.max(1, Math.ceil(data.length / 7));
  const xAt = (index) => left + (index / denominator) * innerW;
  const yAt = (rate) => top + (1 - Math.max(0, Math.min(1, rate || 0))) * innerH;
  const points = data.map((item, index) => `${xAt(index)},${yAt(item.correctRate)}`).join(" ");
  const barWidth = Math.max(3, Math.min(20, innerW / data.length * 0.55));

  return (
    <div className="overflow-x-auto">
      <svg viewBox={`0 0 ${width} ${height}`} className="min-w-[620px]">
        {[0, 0.25, 0.5, 0.75, 1].map((rate) => {
          const y = yAt(rate);
          return (
            <g key={rate}>
              <line x1={left} y1={y} x2={width - right} y2={y} stroke="#eef2f7" />
              <text x={left - 8} y={y + 3} textAnchor="end" className="fill-slate-400 text-[9px]">{Math.round(rate * 100)}%</text>
            </g>
          );
        })}
        {data.map((item, index) => {
          const barHeight = ((item.count || 0) / maxCount) * innerH * 0.72;
          return (
            <rect
              key={`bar-${item.date}-${index}`}
              x={xAt(index) - barWidth / 2}
              y={top + innerH - barHeight}
              width={barWidth}
              height={barHeight}
              rx="3"
              fill="#c7d2fe"
              opacity="0.7"
            />
          );
        })}
        <polyline points={points} fill="none" stroke="#10b981" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round" />
        {data.map((item, index) => (
          <g key={`${item.date}-${index}`}>
            <circle cx={xAt(index)} cy={yAt(item.correctRate)} r="3.5" fill="#10b981" stroke="white" strokeWidth="2" />
            {(index % labelStep === 0 || index === data.length - 1) && (
              <text x={xAt(index)} y={height - 9} textAnchor="middle" className="fill-slate-400 text-[9px]">{item.date}</text>
            )}
          </g>
        ))}
      </svg>
      <div className="mt-1 flex items-center justify-center gap-5 text-xs text-slate-500">
        <span className="flex items-center gap-1.5"><span className="h-0.5 w-4 bg-emerald-500" />正确率</span>
        <span className="flex items-center gap-1.5"><span className="h-2.5 w-4 rounded bg-indigo-200" />做题量</span>
      </div>
    </div>
  );
}

function TrendBadge({ summary }) {
  if (!summary.hasPreviousPeriod) {
    return <span className="rounded-full bg-slate-100 px-3 py-1 text-xs text-slate-500">暂无上一周期对比</span>;
  }
  const change = Number(summary.accuracyChange || 0);
  const positive = change >= 0;
  return (
    <span className={`flex items-center gap-1 rounded-full px-3 py-1 text-xs font-medium ${positive ? "bg-emerald-50 text-emerald-700" : "bg-rose-50 text-rose-700"}`}>
      {positive ? <TrendingUp size={14} /> : <TrendingDown size={14} />}
      较上期 {positive ? "+" : ""}{Math.round(change)} 个百分点
    </span>
  );
}

function BreakdownCard({ title, items = [], labelMap = {} }) {
  return (
    <section className="rounded-2xl border border-slate-100 bg-white p-5 shadow-sm">
      <h2 className="font-semibold text-slate-700">{title}</h2>
      {items.length === 0 ? (
        <EmptyState text="暂无数据" compact />
      ) : (
        <div className="mt-4 space-y-4">
          {items.map((item) => {
            const accuracy = Math.max(0, Math.min(1, item.accuracy || 0));
            return (
              <div key={item.name}>
                <div className="mb-1.5 flex items-center justify-between text-sm">
                  <span className="truncate text-slate-600">{labelMap[item.name] || item.name}</span>
                  <span className="ml-3 flex-shrink-0 font-semibold text-slate-700">{Math.round(accuracy * 100)}%</span>
                </div>
                <div className="h-1.5 overflow-hidden rounded-full bg-slate-100">
                  <div className={accuracy >= 0.8 ? "h-full bg-emerald-400" : accuracy >= 0.6 ? "h-full bg-amber-400" : "h-full bg-rose-400"} style={{ width: `${accuracy * 100}%` }} />
                </div>
                <div className="mt-1 flex justify-between text-[11px] text-slate-400">
                  <span>{item.correct || 0}/{item.total || 0} 题</span>
                  <span>平均 {formatDuration(item.avgTimeSpent || 0)}</span>
                </div>
              </div>
            );
          })}
        </div>
      )}
    </section>
  );
}

function WrongReasonBars({ items }) {
  const max = Math.max(1, ...items.map((item) => item.count || 0));
  const colors = ["bg-rose-400", "bg-amber-400", "bg-orange-400", "bg-violet-400", "bg-cyan-400", "bg-indigo-400"];
  return (
    <div className="mt-5 grid grid-cols-1 gap-4 md:grid-cols-2">
      {items.map((item, index) => (
        <div key={item.reason || item.label} className="rounded-xl bg-slate-50 p-4">
          <div className="flex items-center justify-between gap-3">
            <span className="font-medium text-slate-700">{item.label}</span>
            <span className="text-sm font-bold text-slate-700">{item.count} 次</span>
          </div>
          <div className="mt-2 h-2 overflow-hidden rounded-full bg-white">
            <div className={`h-full rounded-full ${colors[index % colors.length]}`} style={{ width: `${(item.count / max) * 100}%` }} />
          </div>
          <p className="mt-2 text-xs leading-5 text-slate-400">{item.description}</p>
        </div>
      ))}
    </div>
  );
}

function SummaryCard({ icon, label, value, tone }) {
  const tones = {
    indigo: "bg-indigo-50 text-indigo-600",
    emerald: "bg-emerald-50 text-emerald-600",
    rose: "bg-rose-50 text-rose-600",
    cyan: "bg-cyan-50 text-cyan-600",
    amber: "bg-amber-50 text-amber-600",
    violet: "bg-violet-50 text-violet-600",
  };
  return (
    <div className="rounded-2xl border border-slate-100 bg-white p-4 shadow-sm">
      <div className={`mb-3 flex h-9 w-9 items-center justify-center rounded-xl ${tones[tone]}`}>{icon}</div>
      <div className="text-xl font-bold text-slate-800">{value}</div>
      <div className="mt-1 text-xs text-slate-400">{label}</div>
    </div>
  );
}

function MasteryBadge({ level }) {
  const tones = {
    已掌握: "bg-emerald-50 text-emerald-700",
    较熟练: "bg-indigo-50 text-indigo-700",
    待巩固: "bg-amber-50 text-amber-700",
    薄弱: "bg-rose-50 text-rose-700",
    数据不足: "bg-slate-100 text-slate-500",
  };
  return <span className={`flex-shrink-0 rounded-md px-2 py-0.5 text-[11px] font-medium ${tones[level] || tones.数据不足}`}>{level}</span>;
}

function Metric({ label, value, valueClass = "text-slate-700" }) {
  return (
    <div>
      <div className={`font-semibold ${valueClass}`}>{value}</div>
      <div className="mt-1 text-[11px] text-slate-400">{label}</div>
    </div>
  );
}

function EmptyState({ text, compact = false }) {
  return <div className={`${compact ? "py-8" : "py-12"} text-center text-sm text-slate-400`}>{text}</div>;
}

function masteryOf(point) {
  if (Number.isFinite(Number(point.masteryScore))) return Math.max(0, Math.min(100, Number(point.masteryScore)));
  return Math.max(0, Math.min(100, Number(point.rate || 0) * 100));
}

function accuracyOf(point) {
  if (point.accuracy !== undefined) return Math.max(0, Math.min(1, Number(point.accuracy) || 0));
  return Math.max(0, Math.min(1, Number(point.rate || 0)));
}

function recentAccuracyOf(point) {
  return point.recentAccuracy !== undefined ? Math.max(0, Math.min(1, Number(point.recentAccuracy) || 0)) : accuracyOf(point);
}

function levelOf(mastery, total) {
  if ((total || 0) < 3) return "数据不足";
  if (mastery >= 85) return "已掌握";
  if (mastery >= 70) return "较熟练";
  if (mastery >= 55) return "待巩固";
  return "薄弱";
}

function masteryBarColor(level) {
  if (level === "已掌握") return "bg-emerald-400";
  if (level === "较熟练") return "bg-indigo-400";
  if (level === "待巩固") return "bg-amber-400";
  if (level === "薄弱") return "bg-rose-400";
  return "bg-slate-300";
}

function formatDuration(seconds) {
  const value = Math.round(Number(seconds) || 0);
  if (value < 60) return `${value} 秒`;
  const minutes = Math.floor(value / 60);
  const remain = value % 60;
  return remain ? `${minutes}分${remain}秒` : `${minutes} 分`;
}
