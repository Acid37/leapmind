-- M1 题库扩充：数学、英语、计算机、物理、化学各新增 12 道启用题目。
-- lesson_id 使用固定标记，使脚本可重复执行而不会重复插入。

-- “通用”是历史导入产生的占位分类，不属于真实科目；当前无答题或错题引用时可直接清理。
DELETE FROM practice_questions
WHERE subject = '通用';

UPDATE practice_questions
SET grade_level = '大学'
WHERE grade_level = '八升九';

INSERT INTO practice_questions
(subject, grade_level, question_type, title, content, option_a, option_b, option_c, option_d, correct_answer,
 answer_keywords, analysis, chapter, knowledge_point, difficulty, track, lesson_id, status)
SELECT seed.subject, seed.grade_level, seed.question_type, seed.title, seed.content,
       seed.option_a, seed.option_b, seed.option_c, seed.option_d, seed.correct_answer,
       seed.answer_keywords, seed.analysis, seed.chapter, seed.knowledge_point, seed.difficulty,
       seed.track, seed.lesson_id, seed.status
FROM (
    SELECT '数学' AS subject, '大学' AS grade_level, 'SINGLE_CHOICE' AS question_type,
           'M1扩充-数学01-基本极限' AS title, '极限 lim(x→0) sin(x)/x 的值是？' AS content,
           '0' AS option_a, '1' AS option_b, '无穷大' AS option_c, '不存在' AS option_d,
           'B' AS correct_answer, NULL AS answer_keywords, '这是重要基本极限，结果为 1。' AS analysis,
           '函数极限' AS chapter, '重要极限' AS knowledge_point, 'BASIC' AS difficulty,
           '高等数学' AS track, 'M1-BANK-20260809-MATH-01' AS lesson_id, 'ENABLED' AS status
    UNION ALL
    SELECT '数学', '大学', 'SINGLE_CHOICE', 'M1扩充-数学02-乘积求导',
           '函数 f(x)=x²eˣ，则 f''(x) 等于？', 'eˣ(x²+2x)', 'eˣ(x²+x)', '2xeˣ', 'x²eˣ',
           'A', NULL, '使用乘积求导法则，f''(x)=2xeˣ+x²eˣ=eˣ(x²+2x)。',
           '导数与微分', '乘积求导', 'ADVANCED', '高等数学', 'M1-BANK-20260809-MATH-02', 'ENABLED'
    UNION ALL
    SELECT '数学', '大学', 'SINGLE_CHOICE', 'M1扩充-数学03-不定积分',
           '不定积分 ∫2x dx 的结果是？', '2x²+C', 'x²+C', 'x+C', '2+C',
           'B', NULL, '因为 d(x²)/dx=2x，所以 ∫2x dx=x²+C。',
           '积分学', '不定积分', 'BASIC', '高等数学', 'M1-BANK-20260809-MATH-03', 'ENABLED'
    UNION ALL
    SELECT '数学', '大学', 'SINGLE_CHOICE', 'M1扩充-数学04-二阶行列式',
           '矩阵 [[1,2],[3,4]] 的行列式等于？', '-2', '2', '10', '14',
           'A', NULL, '二阶行列式为 1×4-2×3=-2。',
           '线性代数', '行列式', 'BASIC', '高等数学', 'M1-BANK-20260809-MATH-04', 'ENABLED'
    UNION ALL
    SELECT '数学', '大学', 'SINGLE_CHOICE', 'M1扩充-数学05-补事件概率',
           '若事件 A 的概率 P(A)=0.3，则 P(A 的对立事件) 等于？', '0.3', '0.7', '1.3', '无法确定',
           'B', NULL, '对立事件概率为 1-P(A)=0.7。',
           '概率论', '补事件', 'BASIC', '高等数学', 'M1-BANK-20260809-MATH-05', 'ENABLED'
    UNION ALL
    SELECT '数学', '大学', 'SINGLE_CHOICE', 'M1扩充-数学06-等比数列',
           '等比数列首项为 2、公比为 3，则第 4 项是？', '18', '27', '54', '81',
           'C', NULL, '第 4 项 a₄=2×3³=54。',
           '数列', '等比数列', 'BASIC', '高等数学', 'M1-BANK-20260809-MATH-06', 'ENABLED'
    UNION ALL
    SELECT '数学', '大学', 'FILL_BLANK', 'M1扩充-数学07-偏导数',
           '函数 f(x,y)=x²y，则 ∂f/∂x 在点 (1,3) 处的值是 ____。', NULL, NULL, NULL, NULL,
           '6', '6;六', '∂f/∂x=2xy，代入 (1,3) 得 6。',
           '多元函数微分', '偏导数', 'ADVANCED', '高等数学', 'M1-BANK-20260809-MATH-07', 'ENABLED'
    UNION ALL
    SELECT '数学', '大学', 'FILL_BLANK', 'M1扩充-数学08-定积分',
           '定积分 ∫₀¹3x² dx 的值是 ____。', NULL, NULL, NULL, NULL,
           '1', '1;一', '原函数为 x³，在 0 到 1 上的增量为 1。',
           '积分学', '定积分', 'ADVANCED', '高等数学', 'M1-BANK-20260809-MATH-08', 'ENABLED'
    UNION ALL
    SELECT '数学', '大学', 'SINGLE_CHOICE', 'M1扩充-数学09-特征值之和',
           '一个 2×2 矩阵的迹为 5，则两个特征值之和为？', '0', '2', '5', '25',
           'C', NULL, '矩阵特征值之和等于矩阵的迹。',
           '线性代数', '特征值', 'ADVANCED', '高等数学', 'M1-BANK-20260809-MATH-09', 'ENABLED'
    UNION ALL
    SELECT '数学', '大学', 'MULTIPLE_CHOICE', 'M1扩充-数学10-连续与可导',
           '关于函数在一点处连续与可导的关系，正确的是哪些？',
           '可导一定连续', '连续一定可导', '|x| 在 x=0 处连续但不可导', '不连续函数也可能在该点可导',
           'A,C', '可导;连续;反例', '可导蕴含连续，但连续不一定可导，|x| 是经典反例。',
           '导数与微分', '连续与可导', 'ADVANCED', '高等数学', 'M1-BANK-20260809-MATH-10', 'ENABLED'
    UNION ALL
    SELECT '数学', '大学', 'SHORT_ANSWER', 'M1扩充-数学11-中值定理',
           '请写出拉格朗日中值定理的条件和结论。', NULL, NULL, NULL, NULL,
           '函数在闭区间连续、开区间可导，则存在一点使导数等于区间端点割线斜率',
           '闭区间连续;开区间可导;存在;导数;割线斜率',
           '条件是闭区间连续、开区间可导，结论是至少存在一点满足导数等于平均变化率。',
           '微分中值定理', '拉格朗日中值定理', 'HARD', '高等数学', 'M1-BANK-20260809-MATH-11', 'ENABLED'
    UNION ALL
    SELECT '数学', '大学', 'SINGLE_CHOICE', 'M1扩充-数学12-方差性质',
           '随机变量 X 的方差为 Var(X)，则 Var(2X) 等于？', 'Var(X)', '2Var(X)', '3Var(X)', '4Var(X)',
           'D', NULL, '方差满足 Var(aX)=a²Var(X)，因此 Var(2X)=4Var(X)。',
           '概率论', '方差', 'ADVANCED', '高等数学', 'M1-BANK-20260809-MATH-12', 'ENABLED'

    UNION ALL
    SELECT '英语', '大学', 'SINGLE_CHOICE', 'M1扩充-英语01-词义辨析',
           'Which word is closest in meaning to abundant?', 'scarce', 'plentiful', 'temporary', 'ordinary',
           'B', NULL, 'Abundant means existing in large quantities, so plentiful is the closest synonym.',
           '核心词汇', '同义词', 'BASIC', '大学英语', 'M1-BANK-20260809-ENGLISH-01', 'ENABLED'
    UNION ALL
    SELECT '英语', '大学', 'SINGLE_CHOICE', 'M1扩充-英语02-虚拟语气',
           'If I ____ you, I would accept the offer.', 'am', 'were', 'was being', 'will be',
           'B', NULL, 'In a present unreal condition, the verb be commonly takes were for all persons.',
           '英语语法', '虚拟语气', 'ADVANCED', '大学英语', 'M1-BANK-20260809-ENGLISH-02', 'ENABLED'
    UNION ALL
    SELECT '英语', '大学', 'SINGLE_CHOICE', 'M1扩充-英语03-过去完成时',
           'By the time we arrived, the meeting ____.', 'starts', 'has started', 'had started', 'will start',
           'C', NULL, 'The meeting happened before the past action arrived, so the past perfect is required.',
           '英语语法', '过去完成时', 'ADVANCED', '大学英语', 'M1-BANK-20260809-ENGLISH-03', 'ENABLED'
    UNION ALL
    SELECT '英语', '大学', 'SINGLE_CHOICE', 'M1扩充-英语04-固定搭配',
           'She is responsible ____ preparing the final report.', 'at', 'for', 'with', 'by',
           'B', NULL, 'The fixed expression is be responsible for doing something.',
           '英语语法', '介词搭配', 'BASIC', '大学英语', 'M1-BANK-20260809-ENGLISH-04', 'ENABLED'
    UNION ALL
    SELECT '英语', '大学', 'SINGLE_CHOICE', 'M1扩充-英语05-让步关系',
           '____ the heavy rain, the match continued.', 'Despite', 'Because', 'Unless', 'Therefore',
           'A', NULL, 'Despite is followed by a noun phrase and expresses concession.',
           '英语语法', '逻辑连接', 'BASIC', '大学英语', 'M1-BANK-20260809-ENGLISH-05', 'ENABLED'
    UNION ALL
    SELECT '英语', '大学', 'FILL_BLANK', 'M1扩充-英语06-名词复数',
           'The plural form of analysis is ____.', NULL, NULL, NULL, NULL,
           'analyses', 'analyses', 'Analysis changes to analyses in the plural form.',
           '核心词汇', '名词复数', 'BASIC', '大学英语', 'M1-BANK-20260809-ENGLISH-06', 'ENABLED'
    UNION ALL
    SELECT '英语', '大学', 'FILL_BLANK', 'M1扩充-英语07-时态填空',
           'Complete the sentence: She ____ (study) for three hours and is still working.', NULL, NULL, NULL, NULL,
           'has been studying', 'has been studying', 'The action began in the past and continues now, so use the present perfect continuous.',
           '英语语法', '现在完成进行时', 'ADVANCED', '大学英语', 'M1-BANK-20260809-ENGLISH-07', 'ENABLED'
    UNION ALL
    SELECT '英语', '大学', 'MULTIPLE_CHOICE', 'M1扩充-英语08-significant同义词',
           'Which words can be synonyms of significant in an academic context?',
           'important', 'tiny', 'substantial', 'irrelevant', 'A,C', 'important;substantial',
           'Significant can mean important or substantial depending on context.',
           '核心词汇', '同义词', 'ADVANCED', '大学英语', 'M1-BANK-20260809-ENGLISH-08', 'ENABLED'
    UNION ALL
    SELECT '英语', '大学', 'MULTIPLE_CHOICE', 'M1扩充-英语09-学术写作',
           'Which features are appropriate for academic writing?',
           'Claims supported by evidence', 'Frequent internet slang', 'Unsupported personal guesses', 'Clear logical organization',
           'A,D', 'evidence;logical organization;clarity', 'Academic writing values evidence, clarity and logical organization.',
           '学术写作', '写作规范', 'ADVANCED', '大学英语', 'M1-BANK-20260809-ENGLISH-09', 'ENABLED'
    UNION ALL
    SELECT '英语', '大学', 'SHORT_ANSWER', 'M1扩充-英语10-句意改写',
           'Paraphrase the sentence: The results indicate that regular practice improves performance.', NULL, NULL, NULL, NULL,
           'The findings suggest that consistent practice leads to better performance.',
           'results;findings;indicate;suggest;regular practice;improves performance',
           'A good paraphrase changes wording and structure while preserving the original meaning.',
           '学术写作', '同义改写', 'HARD', '大学英语', 'M1-BANK-20260809-ENGLISH-10', 'ENABLED'
    UNION ALL
    SELECT '英语', '大学', 'SINGLE_CHOICE', 'M1扩充-英语11-阅读推断',
           'Solar panels have high initial costs, but they can greatly reduce electricity bills over time. What can be inferred?',
           'They never require investment', 'They may provide long-term savings', 'They increase every electricity bill', 'They work only at night',
           'B', NULL, 'Lower bills over time imply that the initial investment may lead to long-term savings.',
           '阅读理解', '推理判断', 'ADVANCED', '大学英语', 'M1-BANK-20260809-ENGLISH-11', 'ENABLED'
    UNION ALL
    SELECT '英语', '大学', 'SINGLE_CHOICE', 'M1扩充-英语12-contribute搭配',
           'Regular exercise contributes ____ better physical and mental health.', 'for', 'with', 'to', 'on',
           'C', NULL, 'The correct collocation is contribute to.',
           '英语语法', '介词搭配', 'BASIC', '大学英语', 'M1-BANK-20260809-ENGLISH-12', 'ENABLED'

    UNION ALL
    SELECT '计算机', '大学', 'SINGLE_CHOICE', 'M1扩充-计算机01-二分查找',
           '在有序数组中，二分查找的平均时间复杂度是？', 'O(1)', 'O(log n)', 'O(n)', 'O(n²)',
           'B', NULL, '二分查找每次把搜索区间缩小一半，因此时间复杂度为 O(log n)。',
           '算法基础', '时间复杂度', 'BASIC', '计算机基础', 'M1-BANK-20260809-COMPUTER-01', 'ENABLED'
    UNION ALL
    SELECT '计算机', '大学', 'SINGLE_CHOICE', 'M1扩充-计算机02-栈结构',
           '栈的典型访问原则是？', '后进先出', '先进先出', '随机访问', '按关键字排序',
           'A', NULL, '栈遵循 LIFO，即后进先出。',
           '数据结构', '栈', 'BASIC', '计算机基础', 'M1-BANK-20260809-COMPUTER-02', 'ENABLED'
    UNION ALL
    SELECT '计算机', '大学', 'SINGLE_CHOICE', 'M1扩充-计算机03-主键',
           '关系数据库中，主键的主要作用是？', '保存重复数据', '压缩整张表', '唯一标识一条记录', '自动删除空值',
           'C', NULL, '主键值必须唯一且非空，用于唯一标识表中的记录。',
           '数据库基础', '主键', 'BASIC', '计算机基础', 'M1-BANK-20260809-COMPUTER-03', 'ENABLED'
    UNION ALL
    SELECT '计算机', '大学', 'SINGLE_CHOICE', 'M1扩充-计算机04-线程资源',
           '同一进程中的多个线程通常共享什么？', '各自独立的进程地址空间', '进程的地址空间和打开文件', '各自独立的操作系统内核', '各自独立的磁盘分区',
           'B', NULL, '同一进程的线程共享进程资源，但拥有各自的栈和执行上下文。',
           '操作系统', '进程与线程', 'ADVANCED', '计算机基础', 'M1-BANK-20260809-COMPUTER-04', 'ENABLED'
    UNION ALL
    SELECT '计算机', '大学', 'SINGLE_CHOICE', 'M1扩充-计算机05-TCP协议',
           '关于 TCP 协议，正确的描述是？', '无连接且不保证可靠', '只用于局域网', '面向连接并提供可靠传输', '不进行流量控制',
           'C', NULL, 'TCP 是面向连接的可靠传输协议，并提供流量控制和拥塞控制。',
           '计算机网络', 'TCP', 'BASIC', '计算机基础', 'M1-BANK-20260809-COMPUTER-05', 'ENABLED'
    UNION ALL
    SELECT '计算机', '大学', 'FILL_BLANK', 'M1扩充-计算机06-队列原则',
           '队列通常遵循的访问原则用四个英文字母表示为 ____。', NULL, NULL, NULL, NULL,
           'FIFO', 'FIFO;先进先出', '队列遵循 First In First Out，即先进先出。',
           '数据结构', '队列', 'BASIC', '计算机基础', 'M1-BANK-20260809-COMPUTER-06', 'ENABLED'
    UNION ALL
    SELECT '计算机', '大学', 'FILL_BLANK', 'M1扩充-计算机07-HTTP状态码',
           'HTTP 中表示“资源未找到”的状态码是 ____。', NULL, NULL, NULL, NULL,
           '404', '404', 'HTTP 404 表示服务器找不到请求的资源。',
           '计算机网络', 'HTTP状态码', 'BASIC', '计算机基础', 'M1-BANK-20260809-COMPUTER-07', 'ENABLED'
    UNION ALL
    SELECT '计算机', '大学', 'MULTIPLE_CHOICE', 'M1扩充-计算机08-事务ACID',
           '数据库事务的 ACID 特性包括哪些？', '原子性', '一致性', '隔离性', '持久性',
           'A,B,C,D', '原子性;一致性;隔离性;持久性', 'ACID 分别表示 Atomicity、Consistency、Isolation 和 Durability。',
           '数据库基础', '事务', 'ADVANCED', '计算机基础', 'M1-BANK-20260809-COMPUTER-08', 'ENABLED'
    UNION ALL
    SELECT '计算机', '大学', 'MULTIPLE_CHOICE', 'M1扩充-计算机09-面向对象特征',
           '面向对象程序设计的典型特征包括哪些？', '封装', '继承', '顺序执行', '多态',
           'A,B,D', '封装;继承;多态', '封装、继承和多态是面向对象程序设计的三个典型特征。',
           '程序设计', '面向对象', 'ADVANCED', '计算机基础', 'M1-BANK-20260809-COMPUTER-09', 'ENABLED'
    UNION ALL
    SELECT '计算机', '大学', 'SHORT_ANSWER', 'M1扩充-计算机10-死锁条件',
           '请写出操作系统产生死锁的四个必要条件。', NULL, NULL, NULL, NULL,
           '互斥、请求并保持、不可剥夺、循环等待',
           '互斥;请求并保持;不可剥夺;循环等待', '四个必要条件同时成立时才可能发生死锁。',
           '操作系统', '死锁', 'HARD', '计算机基础', 'M1-BANK-20260809-COMPUTER-10', 'ENABLED'
    UNION ALL
    SELECT '计算机', '大学', 'SINGLE_CHOICE', 'M1扩充-计算机11-Git分支',
           '在 Git 中创建并立即切换到 feature 分支，推荐使用哪个命令？',
           'git branch feature', 'git switch -c feature', 'git merge feature', 'git delete feature',
           'B', NULL, 'git switch -c feature 会创建新分支并立即切换过去。',
           '软件工程', '版本控制', 'BASIC', '计算机基础', 'M1-BANK-20260809-COMPUTER-11', 'ENABLED'
    UNION ALL
    SELECT '计算机', '大学', 'SHORT_ANSWER', 'M1扩充-计算机12-递归终止',
           '为什么递归函数必须设置终止条件？', NULL, NULL, NULL, NULL,
           '终止条件使递归在有限步骤后停止，避免无限递归和栈溢出',
           '终止条件;停止;无限递归;栈溢出', '没有终止条件，递归调用会持续压栈并最终导致栈溢出。',
           '程序设计', '递归', 'ADVANCED', '计算机基础', 'M1-BANK-20260809-COMPUTER-12', 'ENABLED'

    UNION ALL
    SELECT '物理', '大学', 'SINGLE_CHOICE', 'M1扩充-物理01-牛顿第二定律',
           '质量为 m 的物体受到合外力 F 时，其加速度大小为？', 'm/F', 'F/m', 'Fm', 'F+m',
           'B', NULL, '由牛顿第二定律 F=ma，可得 a=F/m。',
           '力学', '牛顿第二定律', 'BASIC', '大学物理', 'M1-BANK-20260809-PHYSICS-01', 'ENABLED'
    UNION ALL
    SELECT '物理', '大学', 'SINGLE_CHOICE', 'M1扩充-物理02-动能公式',
           '质量为 m、速度为 v 的质点，其平动动能为？', 'mv', 'mv²', '1/2 mv²', '2mv²',
           'C', NULL, '经典力学中平动动能 Ek=1/2 mv²。',
           '力学', '动能', 'BASIC', '大学物理', 'M1-BANK-20260809-PHYSICS-02', 'ENABLED'
    UNION ALL
    SELECT '物理', '大学', 'SINGLE_CHOICE', 'M1扩充-物理03-动量守恒',
           '一个系统在什么条件下总动量保持不变？', '合外力为零', '内力为零', '速度都相等', '质量都相等',
           'A', NULL, '系统所受合外力为零时，总动量守恒。',
           '力学', '动量守恒', 'ADVANCED', '大学物理', 'M1-BANK-20260809-PHYSICS-03', 'ENABLED'
    UNION ALL
    SELECT '物理', '大学', 'SINGLE_CHOICE', 'M1扩充-物理04-电场强度单位',
           '电场强度的 SI 单位可以表示为？', 'N/C', 'J·s', 'Wb', 'Ω·m',
           'A', NULL, '由 E=F/q 可知电场强度单位为 N/C。',
           '电磁学', '电场强度', 'BASIC', '大学物理', 'M1-BANK-20260809-PHYSICS-04', 'ENABLED'
    UNION ALL
    SELECT '物理', '大学', 'SINGLE_CHOICE', 'M1扩充-物理05-串联电路',
           '理想串联电路中，各元件的哪个物理量相同？', '电压', '电流', '电阻', '功率',
           'B', NULL, '串联电路只有一条电流路径，因此各处电流相同。',
           '电磁学', '串联电路', 'BASIC', '大学物理', 'M1-BANK-20260809-PHYSICS-05', 'ENABLED'
    UNION ALL
    SELECT '物理', '大学', 'FILL_BLANK', 'M1扩充-物理06-波长公式',
           '波速为 v、频率为 f，则波长 λ=____。', NULL, NULL, NULL, NULL,
           'v/f', 'v/f;v÷f', '由 v=λf 可得 λ=v/f。',
           '振动与波', '波长', 'BASIC', '大学物理', 'M1-BANK-20260809-PHYSICS-06', 'ENABLED'
    UNION ALL
    SELECT '物理', '大学', 'FILL_BLANK', 'M1扩充-物理07-重力加速度',
           '地球表面附近的重力加速度通常近似取 ____ m/s²。', NULL, NULL, NULL, NULL,
           '9.8', '9.8;9.80', '在常规计算中，地球表面重力加速度常取 9.8 m/s²。',
           '力学', '重力加速度', 'BASIC', '大学物理', 'M1-BANK-20260809-PHYSICS-07', 'ENABLED'
    UNION ALL
    SELECT '物理', '大学', 'MULTIPLE_CHOICE', 'M1扩充-物理08-机械能守恒',
           '关于机械能守恒，正确的是哪些？',
           '只有保守力做功时机械能守恒', '存在摩擦耗散时机械能仍必然守恒', '动能和势能可以相互转化', '机械能守恒要求动能始终不变',
           'A,C', '保守力;机械能守恒;动能;势能;相互转化', '只有保守力做功时机械能守恒，动能与势能可以相互转化。',
           '力学', '机械能守恒', 'ADVANCED', '大学物理', 'M1-BANK-20260809-PHYSICS-08', 'ENABLED'
    UNION ALL
    SELECT '物理', '大学', 'MULTIPLE_CHOICE', 'M1扩充-物理09-电磁感应',
           '哪些变化可能引起穿过闭合回路的磁通量变化？',
           '磁感应强度变化', '回路面积变化', '回路与磁场夹角变化', '保持所有条件不变',
           'A,B,C', '磁感应强度;面积;夹角;磁通量', '磁通量与磁感应强度、有效面积和夹角有关。',
           '电磁学', '电磁感应', 'ADVANCED', '大学物理', 'M1-BANK-20260809-PHYSICS-09', 'ENABLED'
    UNION ALL
    SELECT '物理', '大学', 'SHORT_ANSWER', 'M1扩充-物理10-作用力反作用力',
           '简述牛顿第三定律，并说明作用力和反作用力为什么不能相互抵消。', NULL, NULL, NULL, NULL,
           '作用力和反作用力大小相等、方向相反、作用在不同物体上，因此不能在同一物体上抵消',
           '大小相等;方向相反;不同物体;不能抵消', '两个力作用在不同物体上，所以不能作为同一物体上的平衡力相互抵消。',
           '力学', '牛顿第三定律', 'HARD', '大学物理', 'M1-BANK-20260809-PHYSICS-10', 'ENABLED'
    UNION ALL
    SELECT '物理', '大学', 'SINGLE_CHOICE', 'M1扩充-物理11-凸透镜成像',
           '物体位于凸透镜焦点以外时，通常可以形成怎样的像？', '正立实像', '倒立实像', '只能形成虚像', '永远不成像',
           'B', NULL, '物距大于焦距时，凸透镜可形成倒立实像。',
           '光学', '凸透镜成像', 'ADVANCED', '大学物理', 'M1-BANK-20260809-PHYSICS-11', 'ENABLED'
    UNION ALL
    SELECT '物理', '大学', 'SINGLE_CHOICE', 'M1扩充-物理12-理想气体内能',
           '一定量理想气体的内能主要取决于？', '体积', '压强', '温度', '容器形状',
           'C', NULL, '理想气体分子间作用能忽略，内能是温度的函数。',
           '热学', '理想气体内能', 'ADVANCED', '大学物理', 'M1-BANK-20260809-PHYSICS-12', 'ENABLED'

    UNION ALL
    SELECT '化学', '大学', 'SINGLE_CHOICE', 'M1扩充-化学01-原子序数',
           '元素的原子序数等于其原子核中的什么数量？', '中子数', '质子数', '电子层数', '核外轨道数',
           'B', NULL, '原子序数定义为原子核中的质子数。',
           '无机化学', '原子结构', 'BASIC', '大学化学', 'M1-BANK-20260809-CHEMISTRY-01', 'ENABLED'
    UNION ALL
    SELECT '化学', '大学', 'SINGLE_CHOICE', 'M1扩充-化学02-阿伏伽德罗常数',
           '1 mol 微粒约含有多少个基本粒子？', '6.02×10²³', '3.01×10⁸', '9.8×10²', '1.00×10⁻³',
           'A', NULL, '1 mol 物质所含微粒数约为 6.02×10²³。',
           '基础化学', '物质的量', 'BASIC', '大学化学', 'M1-BANK-20260809-CHEMISTRY-02', 'ENABLED'
    UNION ALL
    SELECT '化学', '大学', 'SINGLE_CHOICE', 'M1扩充-化学03-中性溶液pH',
           '25℃ 时，中性水溶液的 pH 通常为？', '0', '1', '7', '14',
           'C', NULL, '25℃ 时中性溶液中氢离子和氢氧根离子浓度相等，pH=7。',
           '分析化学', '酸碱平衡', 'BASIC', '大学化学', 'M1-BANK-20260809-CHEMISTRY-03', 'ENABLED'
    UNION ALL
    SELECT '化学', '大学', 'SINGLE_CHOICE', 'M1扩充-化学04-氧化数',
           '在大多数化合物中，氧元素的常见氧化数是？', '+2', '+1', '-1', '-2',
           'D', NULL, '除过氧化物等少数特殊情况外，氧通常显 -2 价。',
           '无机化学', '氧化数', 'BASIC', '大学化学', 'M1-BANK-20260809-CHEMISTRY-04', 'ENABLED'
    UNION ALL
    SELECT '化学', '大学', 'SINGLE_CHOICE', 'M1扩充-化学05-催化剂',
           '催化剂加快化学反应速率的主要原因是？', '提高反应物总能量', '降低反应活化能', '改变反应平衡常数', '增加生成物质量',
           'B', NULL, '催化剂提供活化能更低的反应途径，但不改变平衡常数。',
           '物理化学', '反应速率', 'ADVANCED', '大学化学', 'M1-BANK-20260809-CHEMISTRY-05', 'ENABLED'
    UNION ALL
    SELECT '化学', '大学', 'FILL_BLANK', 'M1扩充-化学06-水的摩尔质量',
           'H₂O 的摩尔质量约为 ____ g/mol。', NULL, NULL, NULL, NULL,
           '18', '18;18.0', '水由两个氢原子和一个氧原子组成，摩尔质量约为 2×1+16=18 g/mol。',
           '基础化学', '摩尔质量', 'BASIC', '大学化学', 'M1-BANK-20260809-CHEMISTRY-06', 'ENABLED'
    UNION ALL
    SELECT '化学', '大学', 'FILL_BLANK', 'M1扩充-化学07-氯化钠化学键',
           'NaCl 晶体中主要存在的化学键类型是 ____。', NULL, NULL, NULL, NULL,
           '离子键', '离子键', 'Na⁺ 与 Cl⁻ 之间通过静电作用形成离子键。',
           '结构化学', '化学键', 'BASIC', '大学化学', 'M1-BANK-20260809-CHEMISTRY-07', 'ENABLED'
    UNION ALL
    SELECT '化学', '大学', 'MULTIPLE_CHOICE', 'M1扩充-化学08-酸的性质',
           '关于常见酸性水溶液，正确的是哪些？',
           '能够提供 H⁺', '一定含有大量 OH⁻', '可使蓝色石蕊试纸变红', 'pH 一定大于 7',
           'A,C', 'H+;氢离子;蓝色石蕊;红色', '酸性溶液可提供 H⁺，并能使蓝色石蕊试纸变红。',
           '分析化学', '酸碱性质', 'ADVANCED', '大学化学', 'M1-BANK-20260809-CHEMISTRY-08', 'ENABLED'
    UNION ALL
    SELECT '化学', '大学', 'MULTIPLE_CHOICE', 'M1扩充-化学09-平衡移动',
           '对有气体参加的平衡体系，哪些操作可能改变平衡状态？',
           '改变温度', '改变有关气体分压', '加入催化剂只改变平衡常数', '改变反应物浓度',
           'A,B,D', '温度;分压;浓度;平衡移动', '温度、分压或浓度变化可能使平衡移动，催化剂不改变平衡常数。',
           '物理化学', '化学平衡', 'HARD', '大学化学', 'M1-BANK-20260809-CHEMISTRY-09', 'ENABLED'
    UNION ALL
    SELECT '化学', '大学', 'SHORT_ANSWER', 'M1扩充-化学10-质量守恒',
           '请简述化学反应中的质量守恒定律。', NULL, NULL, NULL, NULL,
           '在封闭体系中，参加反应的各物质总质量等于反应后生成物的总质量',
           '封闭体系;反应物;生成物;总质量;相等', '化学反应只改变原子的组合方式，不改变原子的种类和总数。',
           '基础化学', '质量守恒定律', 'BASIC', '大学化学', 'M1-BANK-20260809-CHEMISTRY-10', 'ENABLED'
    UNION ALL
    SELECT '化学', '大学', 'SINGLE_CHOICE', 'M1扩充-化学11-甲烷分类',
           '甲烷 CH₄ 属于哪一类有机化合物？', '烷烃', '烯烃', '醇', '羧酸',
           'A', NULL, '甲烷是结构最简单的饱和烃，属于烷烃。',
           '有机化学', '烷烃', 'BASIC', '大学化学', 'M1-BANK-20260809-CHEMISTRY-11', 'ENABLED'
    UNION ALL
    SELECT '化学', '大学', 'SINGLE_CHOICE', 'M1扩充-化学12-电极反应',
           '原电池中发生氧化反应的电极称为？', '负极（阳极）', '正极（阴极）', '盐桥', '电解质',
           'A', NULL, '原电池的负极也称阳极，在该处发生氧化反应。',
           '电化学', '原电池', 'ADVANCED', '大学化学', 'M1-BANK-20260809-CHEMISTRY-12', 'ENABLED'

) AS seed
LEFT JOIN practice_questions existing ON existing.lesson_id = seed.lesson_id
WHERE existing.id IS NULL;
