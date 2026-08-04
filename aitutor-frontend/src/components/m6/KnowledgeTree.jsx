import { useState } from 'react';
import {
  BookOpen,
  CheckCircle2,
  CircleDashed,
  Clock3,
  LockKeyhole,
  Sparkles,
} from 'lucide-react';

const TONES = {
  emerald: {
    card: 'border-emerald-300/30 bg-emerald-300/10 hover:border-emerald-200/50 hover:bg-emerald-300/15',
    icon: 'bg-emerald-300/15 text-emerald-100',
    text: 'text-emerald-200',
    chip: 'bg-emerald-300/15 text-emerald-100',
    bar: 'bg-emerald-300',
    ring: 'ring-emerald-300/70',
    Icon: CheckCircle2,
  },
  blue: {
    card: 'border-cyan-300/30 bg-cyan-300/10 hover:border-cyan-200/50 hover:bg-cyan-300/15',
    icon: 'bg-cyan-300/15 text-cyan-100',
    text: 'text-cyan-200',
    chip: 'bg-cyan-300/15 text-cyan-100',
    bar: 'bg-cyan-300',
    ring: 'ring-cyan-300/70',
    Icon: BookOpen,
  },
  amber: {
    card: 'border-amber-300/30 bg-amber-300/10 hover:border-amber-200/50 hover:bg-amber-300/15',
    icon: 'bg-amber-300/15 text-amber-100',
    text: 'text-amber-200',
    chip: 'bg-amber-300/15 text-amber-100',
    bar: 'bg-amber-300',
    ring: 'ring-amber-300/70',
    Icon: Clock3,
  },
  rose: {
    card: 'border-rose-300/30 bg-rose-300/10 hover:border-rose-200/50 hover:bg-rose-300/15',
    icon: 'bg-rose-300/15 text-rose-100',
    text: 'text-rose-200',
    chip: 'bg-rose-300/15 text-rose-100',
    bar: 'bg-rose-300',
    ring: 'ring-rose-300/70',
    Icon: CircleDashed,
  },
  slate: {
    card: 'border-purple-200/20 bg-white/[0.06] hover:border-purple-200/35 hover:bg-white/10',
    icon: 'bg-white/10 text-purple-100/65',
    text: 'text-purple-100/65',
    chip: 'bg-white/10 text-purple-100/70',
    bar: 'bg-purple-200/50',
    ring: 'ring-purple-200/50',
    Icon: LockKeyhole,
  },
};

const STATUS_LABELS = {
  mastered: '已掌握',
  completed: '已掌握',
  done: '已掌握',
  learning: '学习中',
  in_progress: '学习中',
  reviewing: '待复习',
  review: '待复习',
  weak: '需巩固',
  at_risk: '需巩固',
  locked: '未解锁',
  not_started: '未开始',
};

const normalizeStatus = (status) =>
  String(status || '')
    .trim()
    .toLowerCase()
    .replace(/[\s-]+/g, '_');

const normalizeMastery = (value) => {
  if (value === null || value === undefined || value === '') {
    return null;
  }

  const parsed = Number.parseFloat(value);
  return Number.isFinite(parsed) ? Math.round(Math.min(100, Math.max(0, parsed))) : null;
};

const statusLabel = (status) => {
  const normalized = normalizeStatus(status);
  return STATUS_LABELS[normalized] || status || '尚未评估';
};

const getTone = (mastery, status) => {
  const normalizedStatus = normalizeStatus(status);
  const value = normalizeMastery(mastery);

  if (['locked', 'not_started'].includes(normalizedStatus)) return TONES.slate;
  if (['mastered', 'completed', 'done'].includes(normalizedStatus)) return TONES.emerald;
  if (['weak', 'at_risk'].includes(normalizedStatus)) return TONES.rose;
  if (['reviewing', 'review'].includes(normalizedStatus)) return TONES.amber;
  if (['learning', 'in_progress'].includes(normalizedStatus)) return TONES.blue;
  if (value === null) return TONES.slate;
  if (value >= 80) return TONES.emerald;
  if (value >= 60) return TONES.blue;
  if (value >= 40) return TONES.amber;
  return TONES.rose;
};

const asArray = (value) => (Array.isArray(value) ? value : []);

const subjectMatches = (point, subject) => {
  const reference = point?.subject;

  if (reference && typeof reference === 'object') {
    return reference.id === subject.id || reference.name === subject.name;
  }

  return String(reference ?? '') === String(subject.id) || String(reference ?? '') === String(subject.name);
};

const averageMastery = (points) => {
  const values = points
    .map((point) => normalizeMastery(point.mastery))
    .filter((value) => value !== null);

  if (values.length === 0) return null;
  return Math.round(values.reduce((sum, value) => sum + value, 0) / values.length);
};

const normalizeSubjects = (subjects, knowledgeTree) => {
  const providedSubjects = asArray(subjects);
  const flatTree = asArray(knowledgeTree);

  if (providedSubjects.length > 0) {
    return providedSubjects.map((subject, index) => {
      const normalizedSubject =
        typeof subject === 'string' ? { id: subject, name: subject } : { ...subject };
      const id = normalizedSubject.id ?? normalizedSubject.name ?? `subject-${index}`;
      const name = normalizedSubject.name ?? normalizedSubject.label ?? `科目 ${index + 1}`;
      let children = asArray(normalizedSubject.children);

      if (children.length === 0 && knowledgeTree && !Array.isArray(knowledgeTree)) {
        children = asArray(knowledgeTree[id] ?? knowledgeTree[name]);
      }

      if (children.length === 0) {
        children = flatTree.filter((point) => subjectMatches(point, { id, name }));
      }

      return {
        ...normalizedSubject,
        id,
        name,
        children,
        mastery: normalizeMastery(normalizedSubject.mastery) ?? averageMastery(children),
      };
    });
  }

  if (knowledgeTree && !Array.isArray(knowledgeTree)) {
    return Object.entries(knowledgeTree).map(([name, points], index) => ({
      id: `subject-${index}-${name}`,
      name,
      children: asArray(points),
      mastery: averageMastery(asArray(points)),
    }));
  }

  if (flatTree.length === 0) return [];

  if (flatTree.some((point) => asArray(point.children).length > 0)) {
    return flatTree.map((point, index) => ({
      ...point,
      id: point.id ?? `subject-${index}`,
      name: point.name ?? `科目 ${index + 1}`,
      children: asArray(point.children),
    }));
  }

  const grouped = new Map();
  flatTree.forEach((point) => {
    const reference = point.subject;
    const id = typeof reference === 'object' ? reference.id ?? reference.name : reference;
    const name = typeof reference === 'object' ? reference.name ?? reference.id : reference;
    const key = String(id ?? name ?? '全部知识点');

    if (!grouped.has(key)) {
      grouped.set(key, { id: key, name: name || '全部知识点', children: [] });
    }
    grouped.get(key).children.push(point);
  });

  return Array.from(grouped.values()).map((subject) => ({
    ...subject,
    mastery: averageMastery(subject.children),
  }));
};

const Progress = ({ value, tone, label }) => {
  if (value === null) {
    return <span className="text-xs text-purple-100/50">尚未评估</span>;
  }

  return (
    <div className="flex items-center gap-2">
      <div
        className="h-1.5 flex-1 overflow-hidden rounded-full bg-white/10"
        role="progressbar"
        aria-label={`${label}掌握度`}
        aria-valuemin="0"
        aria-valuemax="100"
        aria-valuenow={value}
      >
        <div className={`h-full rounded-full ${tone.bar}`} style={{ width: `${value}%` }} />
      </div>
      <span className={`min-w-9 text-right text-xs font-bold ${tone.text}`}>{value}%</span>
    </div>
  );
};

const KnowledgeNode = ({ node, path, onSelect, selectedPointId }) => {
  const nodeId = String(node.id ?? path);
  const children = asArray(node.children);
  const mastery = normalizeMastery(node.mastery);
  const tone = getTone(mastery, node.status);
  const Icon = tone.Icon;
  const isSelected = selectedPointId === nodeId;

  return (
    <li className="relative">
      <button
        type="button"
        onClick={() => onSelect(node, nodeId)}
        className={`w-full rounded-2xl border p-3 text-left shadow-lg transition duration-200 hover:-translate-y-0.5 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-offset-2 focus-visible:ring-offset-[#4210A5] ${tone.card} ${isSelected ? `ring-2 ring-offset-2 ring-offset-[#4210A5] ${tone.ring}` : ''}`}
        aria-current={isSelected ? 'true' : undefined}
        aria-label={`${node.name || '未命名知识点'}，${statusLabel(node.status)}${mastery === null ? '' : `，掌握度 ${mastery}%`}`}
      >
        <span className="flex items-start gap-3">
          <span className={`mt-0.5 grid h-9 w-9 shrink-0 place-items-center rounded-xl ${tone.icon}`}>
            <Icon aria-hidden="true" size={18} strokeWidth={2.2} />
          </span>
          <span className="min-w-0 flex-1">
            <span className="flex items-start justify-between gap-2">
              <span className="break-words text-sm font-semibold leading-5 text-white">
                {node.name || '未命名知识点'}
              </span>
              <span className={`shrink-0 rounded-full px-2 py-0.5 text-[10px] font-semibold ${tone.chip}`}>
                {statusLabel(node.status)}
              </span>
            </span>
            <span className="mt-2 block">
              <Progress value={mastery} tone={tone} label={node.name || '知识点'} />
            </span>
          </span>
        </span>
      </button>

      {children.length > 0 ? (
        <ul className="ml-5 mt-2 space-y-2 border-l-2 border-purple-200/20 pl-4" aria-label={`${node.name}的子知识点`}>
          {children.map((child, index) => (
            <KnowledgeNode
              key={child.id ?? `${path}-${index}`}
              node={child}
              path={`${path}-${index}`}
              onSelect={onSelect}
              selectedPointId={selectedPointId}
            />
          ))}
        </ul>
      ) : null}
    </li>
  );
};

/**
 * Subject selector and accessible knowledge-point tree.
 *
 * @param {{
 *   subjects?: Array<{ id?: string|number, name?: string, mastery?: number, status?: string, children?: Array }>,
 *   knowledgeTree?: Array|Record<string, Array>,
 *   onSelect?: (point: object) => void,
 *   className?: string
 * }} props
 */
const KnowledgeTree = ({ subjects = [], knowledgeTree = [], onSelect, className = '' }) => {
  const normalizedSubjects = normalizeSubjects(subjects, knowledgeTree);
  const [requestedSubjectId, setRequestedSubjectId] = useState(null);
  const [selectedPointId, setSelectedPointId] = useState(null);
  const requestedSubject = normalizedSubjects.find(
    (subject) => String(subject.id) === requestedSubjectId,
  );
  const selectedSubject = requestedSubject || normalizedSubjects[0] || null;

  const selectSubject = (subject) => {
    setRequestedSubjectId(String(subject.id));
    setSelectedPointId(null);
  };

  const selectPoint = (point, pointId) => {
    setSelectedPointId(pointId);
    onSelect?.(point);
  };

  if (normalizedSubjects.length === 0) {
    return (
      <section
        className={`rounded-2xl border border-dashed border-purple-200/25 bg-[#4210A5]/45 px-6 py-12 text-center ${className}`}
        aria-label="知识树"
      >
        <CircleDashed className="mx-auto text-purple-200/55" size={32} aria-hidden="true" />
        <p className="mt-3 text-sm font-semibold text-white/80">暂无知识树数据</p>
        <p className="mt-1 text-xs text-purple-100/50">开始学习后，知识点会在这里逐步点亮</p>
      </section>
    );
  }

  const selectedMastery = normalizeMastery(selectedSubject.mastery);
  const selectedTone = getTone(selectedMastery, selectedSubject.status);
  const points = asArray(selectedSubject.children);

  return (
    <section className={`w-full ${className}`} aria-label="学科知识树">
      <div className="mb-5 flex items-center gap-2">
        <span className="grid h-9 w-9 place-items-center rounded-xl bg-[#A286FF]/25 text-purple-100">
          <Sparkles size={18} aria-hidden="true" />
        </span>
        <div>
          <h2 className="text-base font-bold text-white">知识掌握图谱</h2>
          <p className="text-xs text-purple-100/55">选择科目，查看各知识点的掌握情况</p>
        </div>
      </div>

      <nav className="grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-3" aria-label="科目列表">
        {normalizedSubjects.map((subject) => {
          const mastery = normalizeMastery(subject.mastery);
          const tone = getTone(mastery, subject.status);
          const Icon = tone.Icon;
          const isSelected = String(subject.id) === String(selectedSubject.id);

          return (
            <button
              key={subject.id}
              type="button"
              onClick={() => selectSubject(subject)}
              aria-pressed={isSelected}
              className={`group rounded-2xl border p-4 text-left shadow-lg transition duration-200 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-cyan-200 focus-visible:ring-offset-2 focus-visible:ring-offset-[#4210A5] ${
                isSelected
                  ? 'border-cyan-200/60 bg-gradient-to-br from-[#638AFF]/45 to-[#A286FF]/35 ring-1 ring-cyan-200/40'
                  : 'border-purple-300/25 bg-[#4210A5]/55 hover:-translate-y-0.5 hover:border-purple-200/45 hover:bg-[#5120B6]/65'
              }`}
            >
              <span className="flex items-center gap-3">
                <span className={`grid h-10 w-10 shrink-0 place-items-center rounded-xl ${tone.icon}`}>
                  <Icon size={20} aria-hidden="true" />
                </span>
                <span className="min-w-0 flex-1">
                  <span className="block truncate text-sm font-bold text-white">{subject.name}</span>
                  <span className="mt-0.5 block text-xs text-purple-100/55">
                    {asArray(subject.children).length} 个知识点
                  </span>
                </span>
                <span className={`text-sm font-bold ${tone.text}`}>
                  {mastery === null ? '--' : `${mastery}%`}
                </span>
              </span>
            </button>
          );
        })}
      </nav>

      <div className="my-4 flex justify-center" aria-hidden="true">
        <span className="h-7 w-px bg-gradient-to-b from-cyan-200/80 to-purple-200/20" />
      </div>

      <div className="rounded-3xl border border-purple-300/30 bg-[#4210A5]/60 p-4 shadow-xl backdrop-blur-lg sm:p-5">
        <div className="mb-4 flex flex-wrap items-center justify-between gap-3 border-b border-white/10 pb-4">
          <div>
            <p className="text-xs font-medium uppercase tracking-wider text-purple-100/50">当前科目</p>
            <h3 className="mt-1 text-lg font-bold text-white">{selectedSubject.name}</h3>
          </div>
          <div className={`rounded-full px-3 py-1.5 text-xs font-bold ${selectedTone.chip}`}>
            总体掌握度 {selectedMastery === null ? '待评估' : `${selectedMastery}%`}
          </div>
        </div>

        {points.length > 0 ? (
          <ul className="grid grid-cols-1 gap-4 md:grid-cols-2 2xl:grid-cols-3" aria-label={`${selectedSubject.name}知识点`}>
            {points.map((point, index) => (
              <KnowledgeNode
                key={point.id ?? `root-${index}`}
                node={point}
                path={`root-${index}`}
                onSelect={selectPoint}
                selectedPointId={selectedPointId}
              />
            ))}
          </ul>
        ) : (
          <div className="rounded-2xl bg-white/[0.06] px-5 py-10 text-center">
            <p className="text-sm font-semibold text-white/70">该科目暂无知识点</p>
            <p className="mt-1 text-xs text-purple-100/45">知识点数据同步后会显示在这里</p>
          </div>
        )}
      </div>
    </section>
  );
};

export default KnowledgeTree;
