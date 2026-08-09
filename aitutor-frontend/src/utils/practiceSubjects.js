export const SUBJECT_MAP = {
  '数学': 'math',
  '英语': 'english',
  '计算机': 'computer',
  '语文': 'chinese',
  '物理': 'physics',
  '化学': 'chemistry',
  '生物': 'biology',
  '通用': 'general',
};

export const SUBJECT_REVERSE = Object.fromEntries(
  Object.entries(SUBJECT_MAP).map(([label, value]) => [value, label])
);

/**
 * 把后端题库中的实际科目转换为筛选选项。
 * 预置科目继续使用原有英文 value；新建的自定义科目直接使用科目名称，
 * 避免所有未知科目都被错误合并成 general。
 */
export function transformSubjectOptions(subjects = []) {
  const seen = new Set();
  const options = [];

  subjects.forEach((rawSubject) => {
    const label = String(rawSubject ?? '').trim();
    if (!label || label === '通用' || label === 'general') return;
    const value = SUBJECT_MAP[label] || label;
    if (seen.has(value)) return;
    seen.add(value);
    options.push({ value, label });
  });

  return options;
}

/**
 * 把知识点字符串转换为统一的下拉/标签选项，并去除空值和重复值。
 */
export function transformKnowledgePointOptions(knowledgePoints = []) {
  const seen = new Set();
  const options = [];

  knowledgePoints.forEach((rawKnowledgePoint) => {
    const value = String(rawKnowledgePoint ?? '').trim();
    if (!value || seen.has(value)) return;
    seen.add(value);
    options.push({ value, label: value });
  });

  return options;
}

/**
 * 后端按中文科目返回知识点分组，前端需要把科目键转换为页面使用的 value。
 * 自定义科目继续保留原名称，不会被合并为 general。
 */
export function transformKnowledgePointsBySubject(groups = {}) {
  const result = {};

  Object.entries(groups || {}).forEach(([rawSubject, knowledgePoints]) => {
    const subjectLabel = String(rawSubject ?? '').trim();
    if (!subjectLabel || subjectLabel === '通用' || subjectLabel === 'general') return;
    const subjectValue = SUBJECT_MAP[subjectLabel] || subjectLabel;
    const existing = result[subjectValue]?.map((item) => item.value) || [];
    result[subjectValue] = transformKnowledgePointOptions([
      ...existing,
      ...(Array.isArray(knowledgePoints) ? knowledgePoints : []),
    ]);
  });

  return result;
}
