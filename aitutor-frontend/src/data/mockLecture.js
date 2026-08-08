/**
 * M4 讲课模块 —— Mock 数据
 * 
 * 对应接口契约（来自《LeapMind教育网站.md》M4 章节）：
 *   POST /api/lecture/parse-file   → parseResult
 *   POST /api/lecture/generate     → SSE 流式（此处 mock 为同步返回）
 *   GET  /api/lecture/contents     → historyList
 */

// ─── 文件解析 Mock ──────────────────────────────────

export const mockParseResult = {
  fileId: 'file_mock_001',
  fileUrl: 'https://placehold.co/600x400?text=Uploaded+File',
  parsedContent: {
    title: '勾股定理',
    sections: [
      { heading: '一、勾股定理的定义', level: 1, content: '直角三角形两直角边的平方和等于斜边的平方。即：a² + b² = c²，其中 c 为斜边，a、b 为直角边。' },
      { heading: '二、勾股定理的证明', level: 1, content: '面积法证明：构造四个全等的直角三角形拼成大正方形，通过面积关系推导。' },
      { heading: '2.1 面积法证明', level: 2, content: '大正方形面积 = (a+b)² = 4×(½ab) + c²，化简得 a²+b²=c²。' },
      { heading: '三、勾股定理的应用', level: 1, content: '已知直角三角形任意两边，可求第三边；判断三角形是否为直角三角形。' },
      { heading: '3.1 求边长', level: 2, content: '已知直角边 a=3, b=4，则斜边 c=√(3²+4²)=5。' },
      { heading: '3.2 判断直角三角形', level: 2, content: '若三角形三边满足 a²+b²=c²，则该三角形为直角三角形。' },
      { heading: '四、常见题型', level: 1, content: '直接计算、逆定理判断、实际应用题（梯子靠墙、最短路径等）。' },
      { heading: '五、易错点总结', level: 1, content: '注意区分斜边和直角边；斜边一定是最长边；不要忘记开方。' },
    ],
    estimatedDuration: 15,
  },
};

// ─── PPT 结构 Mock（讲课生成结果）──────────────────

export const mockPPTStructure = {
  lectureId: 999,
  title: '勾股定理',
  totalPages: 10,
  estimatedDuration: 15,
  slides: [
    {
      pageNum: 1,
      type: 'cover',
      content: {
        title: '勾股定理',
        subtitle: '初中数学·八年级下册',
        imageSuggestion: '直角三角形示意图',
      },
    },
    {
      pageNum: 2,
      type: 'content',
      content: {
        title: '一、什么是勾股定理',
        body: [
          '直角三角形两直角边的平方和等于斜边的平方',
          '公式：a² + b² = c²',
          '其中 c 是斜边（最长边），a 和 b 是直角边',
        ],
        formula: 'a^2 + b^2 = c^2',
        imageSuggestion: '标注了 a、b、c 的直角三角形',
        highlightPoints: ['c 是斜边，是最长边', 'a 和 b 是直角边，顺序无关'],
      },
      interaction: null,
    },
    {
      pageNum: 3,
      type: 'content',
      content: {
        title: '二、勾股定理的证明——面积法',
        body: [
          '构造 4 个全等的直角三角形，拼成一个大正方形',
          '大正方形边长 = a + b',
          '中间小正方形边长 = c',
          '通过面积关系：大正方形 = 4 个三角形 + 小正方形',
        ],
        formula: '(a+b)^2 = 4 \\times \\frac{1}{2}ab + c^2',
        imageSuggestion: '面积法证明的几何图示',
        highlightPoints: ['等面积法是关键', '化简后即得 a²+b²=c²'],
      },
      interaction: null,
    },
    {
      pageNum: 4,
      type: 'example',
      content: {
        title: '例题 1：直接计算',
        body: [
          '在 Rt△ABC 中，∠C=90°，AC=3，BC=4，求 AB。',
          '解：由勾股定理，AB² = AC² + BC² = 3² + 4² = 25',
          '∴ AB = 5',
        ],
        formula: 'c = \\sqrt{a^2 + b^2} = \\sqrt{25} = 5',
        imageSuggestion: '3-4-5 直角三角形',
        highlightPoints: ['常见勾股数：(3,4,5), (5,12,13), (6,8,10)'],
      },
      interaction: {
        type: 'question',
        text: '如果直角边分别是 6 和 8，斜边是多少？',
      },
    },
    {
      pageNum: 5,
      type: 'example',
      content: {
        title: '例题 2：逆定理判断',
        body: [
          '判断以 5、12、13 为边长的三角形是否为直角三角形。',
          '解：5²+12² = 25+144 = 169 = 13²',
          '满足 a²+b²=c²，所以是直角三角形。',
        ],
        formula: '5^2 + 12^2 = 169 = 13^2',
        imageSuggestion: null,
        highlightPoints: ['逆定理：若三边满足勾股关系，则为直角三角形'],
      },
      interaction: {
        type: 'question',
        text: '边长 7、24、25 能构成直角三角形吗？',
      },
    },
    {
      pageNum: 6,
      type: 'content',
      content: {
        title: '三、实际应用——梯子靠墙问题',
        body: [
          '一把长 5m 的梯子斜靠在墙上，底部离墙 3m，求梯子顶端离地面多高？',
          '问题建模：梯子 = 斜边 c=5，底部离墙 = 直角边 a=3',
          '求：顶端高度 b = ?',
        ],
        formula: 'b = \\sqrt{c^2 - a^2} = \\sqrt{25-9} = 4',
        imageSuggestion: '梯子靠墙示意图',
        highlightPoints: ['实际问题先建模为几何图形', '注意区分已知量和未知量'],
      },
      interaction: {
        type: 'question',
        text: '如果梯子底部离墙 2m，顶端高度是多少？',
      },
    },
    {
      pageNum: 7,
      type: 'content',
      content: {
        title: '四、最短路径问题',
        body: [
          '在平面直角坐标系中，求两点间最短距离。',
          'A(1,2) 到 B(4,6) 的距离 = √[(4-1)²+(6-2)²] = √(9+16) = 5',
          '核心思想：构造直角三角形，两点连线为斜边。',
        ],
        formula: 'd = \\sqrt{(x_2-x_1)^2 + (y_2-y_1)^2}',
        imageSuggestion: '坐标系中两点的直角三角形构造',
        highlightPoints: ['这是勾股定理在坐标系中的推广'],
      },
      interaction: null,
    },
    {
      pageNum: 8,
      type: 'content',
      content: {
        title: '五、易错点提醒 ⚠️',
        body: [
          '① 斜边和直角边不要搞反——c 一定是最长边',
          '② 计算时别忘了开方——c = √(a²+b²)，不是 a²+b²',
          '③ 勾股定理只适用于直角三角形——先用角判断再用定理',
          '④ 逆定理用最大边判断——检查"较小两边平方和 = 最大边平方"',
        ],
        imageSuggestion: null,
        highlightPoints: ['易错点 ① 和 ② 是最常犯的错误'],
      },
      interaction: null,
    },
    {
      pageNum: 9,
      type: 'content',
      content: {
        title: '六、知识总结',
        body: [
          '勾股定理：a² + b² = c²（c 为斜边）',
          '勾股定理逆定理：若三边满足 a²+b²=c²，则为 Rt△',
          '常见勾股数：(3,4,5)(5,12,13)(6,8,10)(7,24,25)(8,15,17)',
          '应用场景：求边长、判断直角、最短路径、实际建模',
        ],
        imageSuggestion: '知识思维导图',
        highlightPoints: [],
      },
      interaction: null,
    },
    {
      pageNum: 10,
      type: 'ending',
      content: {
        title: '课后练习',
        subtitle: '巩固今天所学的勾股定理',
        body: [
          '1. 直角边分别为 9 和 12，求斜边。',
          '2. 判断 8、15、17 能否构成直角三角形。',
          '3. 一根电线杆高 8m，在离杆底 6m 处拉一条钢索到杆顶，钢索多长？',
        ],
      },
      interaction: null,
    },
  ],
  knowledgePoints: [
    { id: 10, name: '勾股定理' },
    { id: 11, name: '勾股定理逆定理' },
    { id: 12, name: '勾股数' },
  ],
};

// ─── 讲课风格选项 ──────────────────────────────────

export const lectureStyles = [
  { value: 'concise', label: '简洁风', desc: '直奔主题，适合复习' },
  { value: 'detailed', label: '详细风', desc: '步步深入，适合新课' },
  { value: 'interactive', label: '互动风', desc: '穿插提问，适合课堂' },
  { value: 'storytelling', label: '故事风', desc: '情境引入，适合启蒙' },
];

// ─── 讲课时长选项 ──────────────────────────────────

export const durationOptions = [
  { value: 5, label: '5 分钟' },
  { value: 10, label: '10 分钟' },
  { value: 15, label: '15 分钟' },
  { value: 20, label: '20 分钟' },
  { value: 30, label: '30 分钟' },
];

// ─── 模拟薄弱点 ────────────────────────────────────

export const mockWeakPoints = [
  { kpId: 10, kpName: '勾股定理', weaknessScore: 0.72 },
  { kpId: 11, kpName: '相似三角形', weaknessScore: 0.60 },
  { kpId: 20, kpName: '一元二次方程', weaknessScore: 0.45 },
];

// ─── 讲课历史 Mock ─────────────────────────────────

export const mockHistoryList = [
  {
    lectureId: 1,
    title: '勾股定理',
    subject: 'math',
    grade: 'grade_8',
    slideCount: 10,
    duration: 15,
    style: 'interactive',
    sourceType: 'file',
    status: 'published',
    createdAt: '2026-07-18T10:30:00',
    thumbnail: 'https://placehold.co/320x180/6366f1/white?text=勾股定理',
    knowledgePoints: [{ id: 10, name: '勾股定理' }],
  },
  {
    lectureId: 2,
    title: '相似三角形',
    subject: 'math',
    grade: 'grade_9',
    slideCount: 12,
    duration: 20,
    style: 'detailed',
    sourceType: 'text',
    status: 'draft',
    createdAt: '2026-07-16T14:00:00',
    thumbnail: 'https://placehold.co/320x180/8b5cf6/white?text=相似三角形',
    knowledgePoints: [{ id: 11, name: '相似三角形' }],
  },
  {
    lectureId: 3,
    title: '牛顿第一定律',
    subject: 'physics',
    grade: 'grade_8',
    slideCount: 8,
    duration: 10,
    style: 'storytelling',
    sourceType: 'file',
    status: 'published',
    createdAt: '2026-07-15T09:00:00',
    thumbnail: 'https://placehold.co/320x180/ec4899/white?text=牛顿第一定律',
    knowledgePoints: [{ id: 30, name: '牛顿第一定律' }],
  },
  {
    lectureId: 4,
    title: '一元二次方程',
    subject: 'math',
    grade: 'grade_9',
    slideCount: 15,
    duration: 25,
    style: 'concise',
    sourceType: 'from_weakpoint',
    status: 'archived',
    createdAt: '2026-07-10T16:20:00',
    thumbnail: 'https://placehold.co/320x180/f59e0b/white?text=一元二次方程',
    knowledgePoints: [{ id: 20, name: '一元二次方程' }],
  },
];

// ─── SSE 生成模拟事件流 ────────────────────────────

/**
 * 模拟 SSE 生成过程的事件序列
 * 对应接口：POST /api/lecture/generate → SSE 流式
 */
export const mockGenerationEvents = [
  { delay: 500, type: 'outline', content: '已为你规划 10 页讲课内容，预计 15 分钟…' },
  { delay: 1500, type: 'slide', pageNum: 1, slide: mockPPTStructure.slides[0] },
  { delay: 2500, type: 'slide', pageNum: 2, slide: mockPPTStructure.slides[1] },
  { delay: 3500, type: 'slide', pageNum: 3, slide: mockPPTStructure.slides[2] },
  { delay: 4500, type: 'slide', pageNum: 4, slide: mockPPTStructure.slides[3] },
  { delay: 5500, type: 'slide', pageNum: 5, slide: mockPPTStructure.slides[4] },
  { delay: 6500, type: 'slide', pageNum: 6, slide: mockPPTStructure.slides[5] },
  { delay: 7500, type: 'slide', pageNum: 7, slide: mockPPTStructure.slides[6] },
  { delay: 8500, type: 'slide', pageNum: 8, slide: mockPPTStructure.slides[7] },
  { delay: 9500, type: 'slide', pageNum: 9, slide: mockPPTStructure.slides[8] },
  { delay: 10500, type: 'slide', pageNum: 10, slide: mockPPTStructure.slides[9] },
  { delay: 11500, type: 'done', lectureId: 999, totalPages: 10 },
];
