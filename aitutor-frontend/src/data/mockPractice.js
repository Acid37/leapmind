/**
 * M1 做题板块 —— Mock 数据
 * 后续对接真实接口时替换
 */

// ---------- 题库 Mock ----------

export const mockQuestions = [
  {
    questionId: 1,
    type: "single_choice",
    difficulty: 2,
    subject: "math",
    grade: "grade_8",
    chapter: "勾股定理",
    content: {
      stem: "在直角三角形ABC中，∠C = 90°，AC = 3，BC = 4，则 AB 的长度是？",
      options: ["A. 5", "B. 6", "C. 7", "D. 8"],
      images: [],
    },
    correctAnswer: "A",
    explanation: "根据勾股定理：a² + b² = c²，即 3² + 4² = 25，所以 c = √25 = 5。故选 A。",
    knowledgePoints: [{ id: 10, name: "勾股定理" }],
    answerSteps: ["识别三角形为直角三角形", "确定直角边 AC=3, BC=4", "代入公式 a²+b²=c²", "计算得出斜边 AB=5"],
  },
  {
    questionId: 2,
    type: "single_choice",
    difficulty: 3,
    subject: "math",
    grade: "grade_8",
    chapter: "勾股定理",
    content: {
      stem: "一个直角三角形的两条直角边分别为 6cm 和 8cm，则斜边上的高为多少？",
      options: ["A. 4.8cm", "B. 5.0cm", "C. 4.5cm", "D. 5.2cm"],
      images: [],
    },
    correctAnswer: "A",
    explanation: "先求斜边：√(6²+8²)=10cm。斜边上的高 = (两直角边乘积) ÷ 斜边 = (6×8)÷10 = 4.8cm。",
    knowledgePoints: [{ id: 10, name: "勾股定理" }, { id: 11, name: "三角形面积" }],
    answerSteps: ["计算斜边长度 = 10cm", "利用面积相等：直角边乘积 = 斜边×高", "6×8 = 10×h", "解得 h = 4.8cm"],
  },
  {
    questionId: 3,
    type: "single_choice",
    difficulty: 1,
    subject: "math",
    grade: "grade_8",
    chapter: "勾股定理",
    content: {
      stem: "下列各组数中，能构成直角三角形的是？",
      options: ["A. 1, 2, 3", "B. 3, 4, 5", "C. 4, 5, 6", "D. 5, 6, 7"],
      images: [],
    },
    correctAnswer: "B",
    explanation: "根据勾股定理逆定理：若 a²+b²=c²，则为直角三角形。3²+4²=9+16=25=5²，故选 B。",
    knowledgePoints: [{ id: 10, name: "勾股定理逆定理" }],
    answerSteps: ["检验 A：1²+2²=5≠9", "检验 B：3²+4²=25=5² ✓", "检验 C：4²+5²=41≠36", "检验 D：5²+6²=61≠49"],
  },
  {
    questionId: 4,
    type: "fill_blank",
    difficulty: 2,
    subject: "math",
    grade: "grade_8",
    chapter: "勾股定理",
    content: {
      stem: "直角三角形的斜边长为 13，一条直角边长为 5，则另一条直角边长为____。",
      options: [],
      images: [],
    },
    correctAnswer: "12",
    explanation: "设另一条直角边为 x，则 5² + x² = 13²，x² = 169 - 25 = 144，x = 12。",
    knowledgePoints: [{ id: 10, name: "勾股定理" }],
    answerSteps: ["设未知直角边为 x", "代入公式 5²+x²=13²", "x²=169-25=144", "x=12"],
  },
  {
    questionId: 5,
    type: "single_choice",
    difficulty: 4,
    subject: "math",
    grade: "grade_8",
    chapter: "勾股定理",
    content: {
      stem: "如图，在△ABC中，AB=AC=5，BC=6，AD⊥BC于点D，则AD的长为？",
      options: ["A. 3", "B. 4", "C. 5", "D. √11"],
      images: [],
    },
    correctAnswer: "B",
    explanation: "等腰三角形底边上的高也是中线，所以BD=DC=3。在Rt△ABD中，AB=5，BD=3，AD=√(5²-3²)=√16=4。",
    knowledgePoints: [{ id: 10, name: "勾股定理" }, { id: 12, name: "等腰三角形性质" }],
    answerSteps: ["等腰三角形底边高=中线", "BD=DC=BC/2=3", "在Rt△ABD中使用勾股定理", "AD=√(5²-3²)=4"],
  },
  {
    questionId: 6,
    type: "single_choice",
    difficulty: 2,
    subject: "math",
    grade: "grade_8",
    chapter: "相似三角形",
    content: {
      stem: "两个相似三角形的相似比为 1:2，则它们的面积比为？",
      options: ["A. 1:2", "B. 1:3", "C. 1:4", "D. 2:3"],
      images: [],
    },
    correctAnswer: "C",
    explanation: "相似三角形的面积比等于相似比的平方。相似比 1:2，面积比 = 1²:2² = 1:4。",
    knowledgePoints: [{ id: 11, name: "相似三角形" }],
    answerSteps: ["相似三角形面积比 = 相似比的平方", "相似比为 1:2", "面积比 = 1²:2²", "结果为 1:4"],
  },
  {
    questionId: 7,
    type: "short_answer",
    difficulty: 3,
    subject: "math",
    grade: "grade_8",
    chapter: "勾股定理",
    content: {
      stem: "小明从家到学校要走 300m 向东，再走 400m 向北。如果他直接从家走直线到学校，可以少走多少米？请写出计算过程。",
      options: [],
      images: [],
    },
    correctAnswer: "200m",
    explanation: "家→学校直线距离 = √(300²+400²) = 500m。原路程 = 300+400 = 700m。少走 = 700-500 = 200m。",
    knowledgePoints: [{ id: 10, name: "勾股定理" }],
    answerSteps: ["原路程 = 300+400 = 700m", "直线距离 = √(300²+400²) = 500m", "少走路程 = 700-500 = 200m"],
  },
];

// ---------- 题库筛选条件 Mock ----------

export const mockFilterOptions = {
  subjects: [
    { value: "math", label: "数学" },
    { value: "chinese", label: "语文" },
    { value: "english", label: "英语" },
    { value: "physics", label: "物理" },
    { value: "chemistry", label: "化学" },
    { value: "biology", label: "生物" },
  ],
  grades: [
    { value: "grade_7", label: "七年级" },
    { value: "grade_8", label: "八年级" },
    { value: "grade_9", label: "九年级" },
    { value: "grade_10", label: "高一" },
    { value: "grade_11", label: "高二" },
    { value: "grade_12", label: "高三" },
  ],
  chapters: {
    math: [
      { value: "勾股定理", label: "勾股定理" },
      { value: "相似三角形", label: "相似三角形" },
      { value: "一元二次方程", label: "一元二次方程" },
    ],
  },
  types: [
    { value: "single_choice", label: "单选题" },
    { value: "multi_choice", label: "多选题" },
    { value: "fill_blank", label: "填空题" },
    { value: "short_answer", label: "简答题" },
  ],
  difficulties: [
    { value: 1, label: "★" },
    { value: 2, label: "★★" },
    { value: 3, label: "★★★" },
    { value: 4, label: "★★★★" },
    { value: 5, label: "★★★★★" },
  ],
};

// ---------- 错题本 Mock ----------

export const mockWrongQuestions = [
  {
    id: 101,
    questionId: 2,
    questionContent: mockQuestions[1].content,
    correctAnswer: mockQuestions[1].correctAnswer,
    userAnswer: { selected: "C" },
    wrongReasonTag: "careless",
    knowledgePoints: mockQuestions[1].knowledgePoints,
    status: "unresolved",
    isKeyFocus: false,
    subject: "math",
    createdAt: "2026-07-18T10:30:00",
    reviewCount: 1,
    lastReviewAt: "2026-07-19T14:00:00",
  },
  {
    id: 102,
    questionId: 5,
    questionContent: mockQuestions[4].content,
    correctAnswer: mockQuestions[4].correctAnswer,
    userAnswer: { selected: "A" },
    wrongReasonTag: "concept_unclear",
    knowledgePoints: mockQuestions[4].knowledgePoints,
    status: "reviewing",
    isKeyFocus: true,
    subject: "math",
    createdAt: "2026-07-15T08:00:00",
    reviewCount: 3,
    lastReviewAt: "2026-07-20T09:00:00",
  },
  {
    id: 103,
    questionId: 3,
    questionContent: mockQuestions[2].content,
    correctAnswer: mockQuestions[2].correctAnswer,
    userAnswer: { selected: "C" },
    wrongReasonTag: "concept_unclear",
    knowledgePoints: mockQuestions[2].knowledgePoints,
    status: "unresolved",
    isKeyFocus: false,
    subject: "math",
    createdAt: "2026-07-17T16:00:00",
    reviewCount: 1,
    lastReviewAt: null,
  },
];

// ---------- 统计 Mock ----------

export const mockStatistics = {
  summary: {
    totalQuestions: 256,
    correctRate: 0.72,
    avgTimeSpent: 45,
    streakDays: 7,
  },
  dailyTrend: [
    { date: "07-14", count: 18, correctRate: 0.67 },
    { date: "07-15", count: 22, correctRate: 0.73 },
    { date: "07-16", count: 20, correctRate: 0.70 },
    { date: "07-17", count: 25, correctRate: 0.76 },
    { date: "07-18", count: 30, correctRate: 0.80 },
    { date: "07-19", count: 28, correctRate: 0.75 },
    { date: "07-20", count: 15, correctRate: 0.87 },
  ],
  byKnowledgePoint: [
    { kpId: 10, kpName: "勾股定理", total: 45, correct: 32, rate: 0.71 },
    { kpId: 11, kpName: "相似三角形", total: 38, correct: 28, rate: 0.74 },
    { kpId: 12, kpName: "等腰三角形性质", total: 25, correct: 18, rate: 0.72 },
    { kpId: 13, kpName: "一元一次方程", total: 30, correct: 27, rate: 0.90 },
  ],
  wrongReasons: [
    { reason: "concept_unclear", label: "概念不清", count: 25 },
    { reason: "careless", label: "粗心大意", count: 18 },
    { reason: "formula_wrong", label: "公式记错", count: 10 },
    { reason: "method_wrong", label: "方法错误", count: 8 },
  ],
};

// ---------- 排行榜 Mock ----------

export const mockRanking = {
  daily: [
    { rank: 1, userId: 101, nickname: "数学小天才", avatar: "", points: 320, streakDays: 12 },
    { rank: 2, userId: 102, nickname: "勤学苦练", avatar: "", points: 285, streakDays: 8 },
    { rank: 3, userId: 103, nickname: "学海无涯", avatar: "", points: 260, streakDays: 15 },
    { rank: 4, userId: 1001, nickname: "我", avatar: "", points: 245, streakDays: 7 },
    { rank: 5, userId: 104, nickname: "天天向上", avatar: "", points: 230, streakDays: 5 },
    { rank: 6, userId: 105, nickname: "思维导图", avatar: "", points: 210, streakDays: 6 },
    { rank: 7, userId: 106, nickname: "几何之王", avatar: "", points: 195, streakDays: 4 },
    { rank: 8, userId: 107, nickname: "公式达人", avatar: "", points: 180, streakDays: 9 },
    { rank: 9, userId: 108, nickname: "努力ing", avatar: "", points: 165, streakDays: 3 },
    { rank: 10, userId: 109, nickname: "小太阳", avatar: "", points: 150, streakDays: 2 },
  ],
};

// ---------- 用户积分 Mock ----------

export const mockPoints = {
  totalPoints: 1245,
  dailyPoints: 45,
  streakDays: 7,
  history: [
    { id: 1, actionType: "answer_correct", points: 10, description: "答对题目", createdAt: "2026-07-20 14:30" },
    { id: 2, actionType: "answer_correct", points: 10, description: "答对题目", createdAt: "2026-07-20 14:15" },
    { id: 3, actionType: "daily_checkin", points: 5, description: "每日签到", createdAt: "2026-07-20 08:00" },
    { id: 4, actionType: "streak_bonus", points: 20, description: "连续7天奖励", createdAt: "2026-07-20 08:00" },
  ],
};
