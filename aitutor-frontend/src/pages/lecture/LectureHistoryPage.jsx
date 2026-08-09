/**
 * M4 讲课历史管理页 (4.2.4)
 * 
 * 功能：
 *  - 讲课内容列表（卡片网格）
 *  - 筛选：按科目、状态
 *  - 操作：开始讲课、删除、查看详情
 */

import React, { useEffect, useState } from 'react';
import { getLectureList, deleteLecture } from '../../services/lectureService';
import { mockHistoryList } from '../../data/mockLecture';
import { Clock, BookOpen, Trash2, Play, MoreHorizontal, FileText, Filter, X } from 'lucide-react';

// ─── 状态标签 ──────────────────────────────────────

const StatusBadge = ({ status }) => {
  const config = {
    draft: { bg: 'bg-yellow-100', text: 'text-yellow-700', label: '草稿' },
    published: { bg: 'bg-green-100', text: 'text-green-700', label: '已发布' },
    archived: { bg: 'bg-slate-100', text: 'text-slate-500', label: '已归档' },
  };
  const c = config[status] || config.draft;
  return (
    <span className={`px-2 py-0.5 rounded-full text-xs font-medium ${c.bg} ${c.text}`}>
      {c.label}
    </span>
  );
};

// ─── 风格标签 ──────────────────────────────────────

const styleLabels = {
  concise: '简洁', detailed: '详细', interactive: '互动', storytelling: '故事',
};

// ─── 主页面 ─────────────────────────────────────────

const LectureHistoryPage = ({ userId = 1, onSelectLecture, onBack }) => {
  const [list, setList] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [filterStatus, setFilterStatus] = useState('all'); // all | draft | published | archived
  const [filterSubject, setFilterSubject] = useState('all');
  const [menuOpenId, setMenuOpenId] = useState(null);

  useEffect(() => {
    const load = async () => {
      setLoading(true);
      setError('');
      try {
        const result = await getLectureList(userId);
        setList(result.items || []);
      } catch (err) {
        setError(err?.message || '加载失败');
        // 降级到静态 mock
        setList(mockHistoryList);
      } finally {
        setLoading(false);
      }
    };
    load();
  }, [userId]);

  const handleDelete = async (lectureId) => {
    try {
      await deleteLecture(lectureId);
      setList(prev => prev.filter(l => l.lectureId !== lectureId));
    } catch {
      // ignore
    }
    setMenuOpenId(null);
  };

  const handleStart = (item) => {
    onSelectLecture?.(item);
  };

  // 筛选
  const subjects = ['all', ...new Set(list.map(l => l.subject).filter(Boolean))];
  const filtered = list.filter(l => {
    if (filterStatus !== 'all' && l.status !== filterStatus) return false;
    if (filterSubject !== 'all' && l.subject !== filterSubject) return false;
    return true;
  });

  return (
    <div className="w-full min-h-screen bg-gradient-to-br from-purple-50 via-white to-blue-50">
      <div className="w-full max-w-screen-2xl mx-auto px-4 sm:px-6 lg:px-10 py-6 sm:py-10 min-h-screen flex flex-col">
        {/* 顶部 */}
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2 sm:gap-0 mb-4 sm:mb-8">
          <div>
            <h1 className="text-xl sm:text-2xl font-bold text-slate-800">讲课历史</h1>
            <p className="text-xs sm:text-sm text-slate-500 mt-0.5 sm:mt-1">管理和回放已生成的讲课内容</p>
          </div>
          {onBack && (
            <button
              onClick={onBack}
              className="self-start sm:self-auto px-3 sm:px-4 py-1.5 sm:py-2 text-xs sm:text-sm font-medium text-slate-600 bg-slate-100 rounded-lg hover:bg-slate-200 transition-colors"
            >
              ← 返回
            </button>
          )}
        </div>

        {/* 筛选栏 */}
        <div className="flex items-center gap-2 sm:gap-3 mb-4 sm:mb-6 overflow-x-auto pb-1">
          <Filter className="w-3.5 h-3.5 sm:w-4 sm:h-4 text-slate-400 flex-shrink-0" />
          {/* 状态筛选 */}
          <div className="flex gap-1 bg-slate-100 rounded-lg p-1 flex-shrink-0">
            {[
              { value: 'all', label: '全部' },
              { value: 'published', label: '已发布' },
              { value: 'draft', label: '草稿' },
              { value: 'archived', label: '已归档' },
            ].map((f) => (
              <button
                key={f.value}
                onClick={() => setFilterStatus(f.value)}
                className={`px-3 py-1.5 rounded-md text-xs font-medium transition-all ${
                  filterStatus === f.value
                    ? 'bg-white text-purple-600 shadow-sm'
                    : 'text-slate-500 hover:text-slate-700'
                }`}
              >
                {f.label}
              </button>
            ))}
          </div>
          {/* 科目筛选 */}
          <div className="flex gap-1 bg-slate-100 rounded-lg p-1 flex-shrink-0">
            {subjects.map((s) => (
              <button
                key={s}
                onClick={() => setFilterSubject(s)}
                className={`px-3 py-1.5 rounded-md text-xs font-medium transition-all ${
                  filterSubject === s
                    ? 'bg-white text-purple-600 shadow-sm'
                    : 'text-slate-500 hover:text-slate-700'
                }`}
              >
                {s === 'all' ? '全部科目' :
                 s === 'math' ? '数学' :
                 s === 'physics' ? '物理' : s}
              </button>
            ))}
          </div>
          {(filterStatus !== 'all' || filterSubject !== 'all') && (
            <button
              onClick={() => { setFilterStatus('all'); setFilterSubject('all'); }}
              className="flex items-center gap-1 text-xs text-slate-400 hover:text-slate-600 flex-shrink-0"
            >
              <X className="w-3 h-3" />清除
            </button>
          )}
        </div>

        {/* 加载 / 错误 */}
        {loading && (
          <div className="flex flex-col items-center py-20">
            <div className="w-8 h-8 border-2 border-purple-400 border-t-transparent rounded-full animate-spin mb-4" />
            <p className="text-sm text-slate-400">加载中…</p>
          </div>
        )}
        {error && !loading && (
          <div className="text-center py-20">
            <p className="text-red-500 text-sm">{error}</p>
          </div>
        )}

        {/* 卡片网格 */}
        {!loading && !error && (
          <>
            {filtered.length === 0 ? (
              <div className="text-center py-20">
                <FileText className="w-12 h-12 text-slate-200 mx-auto mb-3" />
                <p className="text-slate-400 text-sm">暂无讲课记录</p>
                <p className="text-slate-300 text-xs mt-1">
                  {filterStatus !== 'all' || filterSubject !== 'all' ? '换个筛选条件试试' : '去创建你的第一堂 AI 讲课吧'}
                </p>
              </div>
            ) : (
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4 sm:gap-5">
                {filtered.map((item) => (
                  <div
                    key={item.lectureId}
                    className="bg-white border border-slate-200 rounded-xl overflow-hidden hover:shadow-lg hover:border-purple-200 transition-all group"
                  >
                    {/* 缩略图 */}
                    <div className="aspect-video bg-slate-100 relative overflow-hidden">
                      {item.thumbnail ? (
                        <img src={item.thumbnail} alt={item.title} className="w-full h-full object-cover" />
                      ) : (
                        <div className="w-full h-full flex items-center justify-center">
                          <BookOpen className="w-10 h-10 text-slate-300" />
                        </div>
                      )}
                      {/* 悬停播放 overlay */}
                      <div className="absolute inset-0 bg-black/0 group-hover:bg-black/30 transition-all flex items-center justify-center">
                        <button
                          onClick={() => handleStart(item)}
                          className="opacity-0 group-hover:opacity-100 w-12 h-12 bg-white/90 rounded-full flex items-center justify-center shadow-lg transition-all hover:scale-110"
                        >
                          <Play className="w-5 h-5 text-purple-600 ml-0.5" />
                        </button>
                      </div>
                    </div>

                    {/* 信息 */}
                    <div className="p-4">
                      <div className="flex items-start justify-between mb-2">
                        <h3 className="text-sm font-semibold text-slate-800 line-clamp-1 flex-1 mr-2">
                          {item.title}
                        </h3>
                        <StatusBadge status={item.status} />
                      </div>

                      <div className="flex items-center gap-3 text-xs text-slate-400 mb-3">
                        <span className="flex items-center gap-1">
                          <BookOpen className="w-3 h-3" />
                          {item.slideCount} 页
                        </span>
                        <span className="flex items-center gap-1">
                          <Clock className="w-3 h-3" />
                          {item.duration} min
                        </span>
                        {styleLabels[item.style] && (
                          <span className="px-1.5 py-0.5 bg-slate-100 rounded text-slate-500">
                            {styleLabels[item.style]}
                          </span>
                        )}
                      </div>

                      {/* 知识点标签 */}
                      {item.knowledgePoints && item.knowledgePoints.length > 0 && (
                        <div className="flex flex-wrap gap-1 mb-3">
                          {item.knowledgePoints.map((kp) => (
                            <span key={kp.id} className="px-1.5 py-0.5 bg-purple-50 text-purple-600 rounded text-xs">
                              {kp.name}
                            </span>
                          ))}
                        </div>
                      )}

                      {/* 底部操作 */}
                      <div className="flex items-center justify-between pt-2 border-t border-slate-100">
                        <span className="text-xs text-slate-400">
                          {new Date(item.createdAt).toLocaleDateString('zh-CN')}
                        </span>
                        <div className="flex items-center gap-1 relative">
                          <button
                            onClick={() => handleStart(item)}
                            className="px-3 py-1 text-xs font-medium text-purple-600 bg-purple-50 rounded-lg hover:bg-purple-100 transition-colors"
                          >
                            开始讲课
                          </button>
                          <button
                            onClick={() => setMenuOpenId(menuOpenId === item.lectureId ? null : item.lectureId)}
                            className="p-1 hover:bg-slate-100 rounded-lg transition-colors"
                          >
                            <MoreHorizontal className="w-4 h-4 text-slate-400" />
                          </button>
                          {menuOpenId === item.lectureId && (
                            <div className="absolute bottom-full right-0 mb-1 bg-white border border-slate-200 rounded-lg shadow-lg py-1 z-10">
                              <button
                                onClick={() => handleDelete(item.lectureId)}
                                className="w-full flex items-center gap-2 px-3 py-1.5 text-xs text-red-500 hover:bg-red-50 transition-colors"
                              >
                                <Trash2 className="w-3 h-3" />删除
                              </button>
                            </div>
                          )}
                        </div>
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </>
        )}
      </div>
    </div>
  );
};

export default LectureHistoryPage;
