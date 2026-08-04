/**
 * 练习统计页（M1 · 5.2.4）
 *
 * 正确率趋势、知识点掌握度、错误原因分布
 */
import { useState, useEffect } from "react";
import {
  BarChart3,
  TrendingUp,
  Target,
  Clock,
  Zap,
} from "lucide-react";
import { getStatistics } from "../services/practiceService";

export default function StatisticsPage() {
  const [stats, setStats] = useState(null);
  const [loading, setLoading] = useState(true);
  const [period, setPeriod] = useState("week");

  useEffect(() => {
    loadStats();
  }, [period]);

  const loadStats = async () => {
    setLoading(true);
    try {
      const data = await getStatistics({ period });
      setStats(data);
    } catch (err) {
      console.error("加载统计数据失败:", err);
    } finally {
      setLoading(false);
    }
  };

  if (loading) {
    return (
      <div className="flex justify-center py-24">
        <div className="w-7 h-7 border-2 border-indigo-400 border-t-transparent rounded-full animate-spin" />
      </div>
    );
  }

  if (!stats) return null;

  const { summary, dailyTrend, byKnowledgePoint, wrongReasons } = stats;

  // 计算趋势图的最大值用于 SVG 缩放
  const chartW = 280;
  const chartH = 140;
  const padX = 10;
  const padY = 20;

  const pointsCorrect = dailyTrend.map((d, i) => {
    const x = padX + (i / (dailyTrend.length - 1)) * (chartW - padX * 2);
    const y = chartH - padY - (d.correctRate * (chartH - padY * 2));
    return `${x},${y}`;
  });

  // 错误原因颜色
  const reasonColors = ["#f43f5e", "#f59e0b", "#f97316", "#8b5cf6"];

  return (
    <div className="max-w-5xl mx-auto">
      <h1 className="text-xl font-bold text-slate-800 mb-5 flex items-center gap-2">
        <BarChart3 size={22} className="text-indigo-500" /> 练习统计
      </h1>

      {/* 时间切换 */}
      <div className="flex items-center gap-1 mb-6 p-1 bg-slate-100 rounded-xl w-fit">
        {[
          { value: "week", label: "近一周" },
          { value: "month", label: "近一月" },
          { value: "all", label: "全部" },
        ].map((p) => (
          <button
            key={p.value}
            onClick={() => setPeriod(p.value)}
            className={`px-4 py-2 rounded-lg text-sm font-medium transition-colors cursor-pointer
              ${period === p.value ? "bg-white text-indigo-600 shadow-sm" : "text-slate-500 hover:text-slate-700"}`}
          >
            {p.label}
          </button>
        ))}
      </div>

      {/* 摘要卡片 */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
        <SummaryCard
          icon={<Target size={20} />}
          label="总做题数"
          value={summary.totalQuestions}
          color="indigo"
        />
        <SummaryCard
          icon={<TrendingUp size={20} />}
          label="正确率"
          value={`${Math.round(summary.correctRate * 100)}%`}
          color="emerald"
        />
        <SummaryCard
          icon={<Clock size={20} />}
          label="平均用时"
          value={`${summary.avgTimeSpent}s`}
          color="amber"
        />
        <SummaryCard
          icon={<Zap size={20} />}
          label="连续天数"
          value={`${summary.streakDays} 天`}
          color="violet"
        />
      </div>

      {/* 趋势图 + 错误原因 */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-5 mb-6">
        {/* 趋势折线图（手写 SVG） */}
        <div className="bg-white rounded-2xl shadow-sm border border-slate-100 p-5">
          <h3 className="text-sm font-semibold text-slate-600 mb-3">做题趋势</h3>
          <svg viewBox={`0 0 ${chartW} ${chartH}`} className="w-full h-auto">
            {/* 网格线 */}
            {[0, 0.25, 0.5, 0.75, 1].map((r) => {
              const y = chartH - padY - r * (chartH - padY * 2);
              return (
                <g key={r}>
                  <line x1={padX} y1={y} x2={chartW - padX} y2={y} stroke="#f1f5f9" strokeWidth="1" />
                  <text x={padX - 5} y={y + 3} textAnchor="end" className="text-[8px] fill-slate-400">
                    {Math.round(r * 100)}%
                  </text>
                </g>
              );
            })}
            {/* 正确率线 */}
            <polyline
              points={pointsCorrect.join(" ")}
              fill="none"
              stroke="#10b981"
              strokeWidth="2"
              strokeLinecap="round"
              strokeLinejoin="round"
            />
            {/* 数据点 */}
            {dailyTrend.map((d, i) => {
              const x = padX + (i / (dailyTrend.length - 1)) * (chartW - padX * 2);
              const y = chartH - padY - d.correctRate * (chartH - padY * 2);
              return (
                <g key={i}>
                  <circle cx={x} cy={y} r="3" fill="#10b981" stroke="white" strokeWidth="1.5" />
                  <text x={x} y={chartH - 3} textAnchor="middle" className="text-[8px] fill-slate-400">
                    {d.date}
                  </text>
                </g>
              );
            })}
          </svg>
          <div className="flex items-center gap-3 mt-2 text-xs">
            <span className="flex items-center gap-1">
              <span className="w-3 h-0.5 bg-emerald-500 rounded" /> 正确率
            </span>
          </div>
        </div>

        {/* 错误原因饼图 */}
        <div className="bg-white rounded-2xl shadow-sm border border-slate-100 p-5">
          <h3 className="text-sm font-semibold text-slate-600 mb-4">错误原因分布</h3>
          {wrongReasons.length === 0 ? (
            <p className="text-sm text-slate-400 py-4">暂无数据</p>
          ) : (
            <div className="flex items-center gap-5">
              {/* 简易环形图 */}
              <div className="relative w-28 h-28 flex-shrink-0">
                <svg viewBox="0 0 100 100" className="w-full h-full -rotate-90">
                  {(() => {
                    const total = wrongReasons.reduce((s, r) => s + r.count, 0);
                    let cumulative = 0;
                    return wrongReasons.map((r, i) => {
                      const pct = r.count / total;
                      const dash = pct * 264; // 2*PI*42 ≈ 264
                      const offset = cumulative * 264;
                      cumulative += pct;
                      return (
                        <circle
                          key={i}
                          cx="50" cy="50" r="42" fill="none"
                          stroke={reasonColors[i]}
                          strokeWidth="10" strokeLinecap="round"
                          strokeDasharray={`${dash} ${264 - dash}`}
                          strokeDashoffset={-offset}
                        />
                      );
                    });
                  })()}
                </svg>
                <div className="absolute inset-0 flex items-center justify-center text-xs font-bold text-slate-600">
                  {wrongReasons.reduce((s, r) => s + r.count, 0)}次
                </div>
              </div>
              {/* 图例 */}
              <div className="space-y-2 flex-1">
                {wrongReasons.map((r, i) => (
                  <div key={i} className="flex items-center justify-between text-sm">
                    <span className="flex items-center gap-2 text-slate-600">
                      <span
                        className="w-2.5 h-2.5 rounded-full"
                        style={{ backgroundColor: reasonColors[i] }}
                      />
                      {r.label}
                    </span>
                    <span className="font-semibold text-slate-700">{r.count}</span>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>
      </div>

      {/* 知识点掌握度 — 雷达图 */}
      <div className="bg-white rounded-2xl shadow-sm border border-slate-100 p-5">
        <h3 className="text-sm font-semibold text-slate-600 mb-4">知识点掌握度</h3>
        <div className="flex flex-col lg:flex-row items-center gap-6">
          {/* 雷达图 */}
          <div className="flex-shrink-0">
            <RadarChart data={byKnowledgePoint} size={200} />
          </div>
          {/* 图例 */}
          <div className="flex-1 space-y-2 w-full">
            {byKnowledgePoint.map((kp) => (
              <div key={kp.kpId} className="flex items-center justify-between text-sm">
                <span className="flex items-center gap-2 text-slate-600">
                  <span
                    className={`w-2.5 h-2.5 rounded-full ${
                      kp.rate >= 0.8 ? "bg-emerald-400" : kp.rate >= 0.6 ? "bg-amber-400" : "bg-red-400"
                    }`}
                  />
                  {kp.kpName}
                </span>
                <span className="flex items-center gap-3">
                  <span className="font-semibold text-slate-700">{Math.round(kp.rate * 100)}%</span>
                  <span className="text-xs text-slate-400">{kp.correct}/{kp.total}</span>
                </span>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}

/** 雷达图 */
function RadarChart({ data, size = 200 }) {
  if (!data || data.length < 3) return null;
  const cx = size / 2;
  const cy = size / 2;
  const radius = size * 0.35;
  const levels = 4; // 网格圈数
  const n = data.length;
  const angleStep = (2 * Math.PI) / n;
  // 从顶部开始，所以旋转 -PI/2
  const getPoint = (i, r) => ({
    x: cx + r * Math.cos(i * angleStep - Math.PI / 2),
    y: cy + r * Math.sin(i * angleStep - Math.PI / 2),
  });

  const levelLines = [];
  for (let l = 1; l <= levels; l++) {
    const points = data.map((_, i) => {
      const p = getPoint(i, radius * (l / levels));
      return `${p.x},${p.y}`;
    });
    levelLines.push(points.join(" "));
  }

  // 轴线
  const axes = data.map((_, i) => {
    const outer = getPoint(i, radius);
    return { x1: cx, y1: cy, x2: outer.x, y2: outer.y };
  });

  // 数据多边形
  const dataPolygon = data.map((kp, i) => {
    const p = getPoint(i, radius * Math.max(kp.rate, 0.05));
    return `${p.x},${p.y}`;
  }).join(" ");

  return (
    <svg viewBox={`0 0 ${size} ${size}`} className="w-48 h-48">
      {/* 网格 */}
      {levelLines.map((pts, i) => (
        <polygon key={i} points={pts} fill="none" stroke="#e2e8f0" strokeWidth="1" />
      ))}
      {/* 轴线 */}
      {axes.map((a, i) => (
        <line key={i} {...a} stroke="#e2e8f0" strokeWidth="0.5" />
      ))}
      {/* 数据多边形 */}
      <polygon points={dataPolygon} fill="rgba(99, 102, 241, 0.2)" stroke="#6366f1" strokeWidth="2" strokeLinejoin="round" />
      {/* 数据点 */}
      {data.map((kp, i) => {
        const p = getPoint(i, radius * Math.max(kp.rate, 0.05));
        return <circle key={i} cx={p.x} cy={p.y} r="3" fill="#6366f1" stroke="white" strokeWidth="1.5" />;
      })}
      {/* 标签 */}
      {data.map((kp, i) => {
        const p = getPoint(i, radius + 20);
        return (
          <text
            key={i}
            x={p.x}
            y={p.y}
            textAnchor="middle"
            dominantBaseline="middle"
            className="text-[10px] fill-slate-500"
            style={{ fontWeight: 500 }}
          >
            {kp.kpName.length > 4 ? kp.kpName.slice(0, 4) + "…" : kp.kpName}
          </text>
        );
      })}
    </svg>
  );
}

/** 摘要卡片 */
function SummaryCard({ icon, label, value, color }) {
  const colorMap = {
    indigo: "bg-indigo-50 text-indigo-600",
    emerald: "bg-emerald-50 text-emerald-600",
    amber: "bg-amber-50 text-amber-600",
    violet: "bg-violet-50 text-violet-600",
  };

  return (
    <div className="bg-white rounded-2xl shadow-sm border border-slate-100 p-4">
      <div className="flex items-center gap-3">
        <div className={`w-10 h-10 rounded-xl flex items-center justify-center ${colorMap[color]}`}>
          {icon}
        </div>
        <div>
          <div className="text-2xl font-bold text-slate-800">{value}</div>
          <div className="text-xs text-slate-400">{label}</div>
        </div>
      </div>
    </div>
  );
}
