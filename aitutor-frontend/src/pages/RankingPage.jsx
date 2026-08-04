/**
 * 排行榜页（M1 · 5.2.5）
 *
 * Tab 切换：日榜 / 周榜 / 月榜
 * Top 3 特殊样式，当前用户高亮
 */
import { useState, useEffect } from "react";
import { Trophy, Medal, Crown, TrendingUp, User } from "lucide-react";
import { getRanking } from "../services/practiceService";

const CURRENT_USER_ID = 1001; // Mock 当前用户

export default function RankingPage() {
  const [type, setType] = useState("daily");
  const [ranking, setRanking] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    loadRanking();
  }, [type]);

  const loadRanking = async () => {
    setLoading(true);
    try {
      const data = await getRanking(type);
      setRanking(data);
    } catch (err) {
      console.error("加载排行榜失败:", err);
    } finally {
      setLoading(false);
    }
  };

  const tabs = [
    { value: "daily", label: "日榜" },
    { value: "weekly", label: "周榜" },
    { value: "monthly", label: "月榜" },
  ];

  // 当前用户排名
  const currentUserRank = ranking.findIndex((r) => r.userId === CURRENT_USER_ID) + 1;

  return (
    <div className="max-w-2xl mx-auto">
      {/* 标题 */}
      <div className="text-center mb-6">
        <h1 className="text-xl font-bold text-slate-800 flex items-center justify-center gap-2">
          <Trophy size={22} className="text-amber-500" /> 排行榜
        </h1>
        <p className="text-sm text-slate-500 mt-1">努力学习，争当第一！</p>
      </div>

      {/* Tab 切换 */}
      <div className="flex justify-center mb-6">
        <div className="flex items-center gap-1 p-1 bg-slate-100 rounded-xl">
          {tabs.map((tab) => (
            <button
              key={tab.value}
              onClick={() => setType(tab.value)}
              className={`px-6 py-2 rounded-lg text-sm font-semibold transition-colors cursor-pointer
                ${type === tab.value ? "bg-white text-indigo-600 shadow-sm" : "text-slate-500 hover:text-slate-700"}`}
            >
              {tab.label}
            </button>
          ))}
        </div>
      </div>

      {/* 当前用户排名条 */}
      {currentUserRank > 0 && (
        <div className="bg-indigo-50 rounded-2xl border border-indigo-100 p-4 mb-5 flex items-center gap-4">
          <div className="w-10 h-10 rounded-full bg-indigo-500 flex items-center justify-center text-white font-bold text-sm">
            {currentUserRank}
          </div>
          <div className="flex-1">
            <div className="font-semibold text-slate-700">我的排名</div>
            <div className="text-xs text-slate-500">
              {currentUserRank <= 10 ? "继续保持！" : "再努力一把就能上榜了！"}
            </div>
          </div>
          <div className="text-right">
            <div className="font-bold text-indigo-600 text-lg">
              {ranking.find((r) => r.userId === CURRENT_USER_ID)?.points || 0}
            </div>
            <div className="text-xs text-slate-400">积分</div>
          </div>
        </div>
      )}

      {/* 排行榜列表 */}
      {loading ? (
        <div className="flex justify-center py-16">
          <div className="w-7 h-7 border-2 border-indigo-400 border-t-transparent rounded-full animate-spin" />
        </div>
      ) : (
        <div className="bg-white rounded-2xl shadow-sm border border-slate-100 overflow-hidden">
          {/* Top 3 特殊展示 */}
          {ranking.length >= 3 && (
            <div className="flex items-end justify-center gap-3 p-6 bg-gradient-to-b from-amber-50 to-white">
              {/* 第2名 */}
              <RankPodium
                rank={2}
                nickname={ranking[1].nickname}
                points={ranking[1].points}
                height="h-20"
                color="bg-slate-300"
                icon={<Medal size={20} className="text-slate-500" />}
              />
              {/* 第1名 */}
              <RankPodium
                rank={1}
                nickname={ranking[0].nickname}
                points={ranking[0].points}
                height="h-28"
                color="bg-amber-400"
                icon={<Crown size={24} className="text-amber-600" />}
              />
              {/* 第3名 */}
              <RankPodium
                rank={3}
                nickname={ranking[2].nickname}
                points={ranking[2].points}
                height="h-16"
                color="bg-orange-300"
                icon={<Medal size={18} className="text-orange-600" />}
              />
            </div>
          )}

          {/* 4-N 名列表 */}
          <div className="divide-y divide-slate-50">
            {ranking.slice(3).map((item) => (
              <div
                key={item.userId}
                className={`flex items-center gap-4 px-5 py-3.5 transition-colors
                  ${item.userId === CURRENT_USER_ID ? "bg-indigo-50" : "hover:bg-slate-50"}`}
              >
                <span className="w-8 text-center font-bold text-sm text-slate-400">
                  {item.rank}
                </span>
                <div className="w-8 h-8 rounded-full bg-slate-100 flex items-center justify-center text-slate-500 font-bold text-sm">
                  {item.nickname?.charAt(0) || <User size={15} />}
                </div>
                <div className="flex-1">
                  <div className="font-medium text-sm text-slate-700 flex items-center gap-1.5">
                    {item.nickname}
                    {item.userId === CURRENT_USER_ID && (
                      <span className="px-1.5 py-0.5 rounded text-[10px] bg-indigo-500 text-white font-medium">
                        我
                      </span>
                    )}
                  </div>
                  <div className="text-xs text-slate-400">连续 {item.streakDays} 天</div>
                </div>
                <div className="text-right">
                  <div className="font-bold text-slate-700">{item.points}</div>
                  <div className="text-[10px] text-slate-400">积分</div>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

/** 领奖台单项 */
function RankPodium({ rank, nickname, points, height, color, icon }) {
  const medalColors = {
    1: "text-amber-500",
    2: "text-slate-400",
    3: "text-orange-400",
  };

  return (
    <div className="flex flex-col items-center gap-2 w-24">
      {/* 头像 */}
      <div className={`w-12 h-12 rounded-full ${color} flex items-center justify-center`}>
        {icon}
      </div>
      <span className={`text-lg ${medalColors[rank]}`}>{icon}</span>
      <div className="text-center">
        <div className="font-bold text-sm text-slate-700 truncate w-full">{nickname}</div>
        <div className="text-xs text-slate-500">{points} 分</div>
      </div>
      {/* 底座 */}
      <div className={`w-full ${height} rounded-t-lg ${color} opacity-30`} />
    </div>
  );
}
