CREATE TABLE practice_questions
(
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    title           VARCHAR(255) NOT NULL,
    content         TEXT         NOT NULL,
    option_a        VARCHAR(500) NOT NULL,
    option_b        VARCHAR(500) NOT NULL,
    option_c        VARCHAR(500) NOT NULL,
    option_d        VARCHAR(500) NOT NULL,
    correct_answer  VARCHAR(10)  NOT NULL,
    analysis        TEXT,
    chapter         VARCHAR(120) NOT NULL,
    knowledge_point VARCHAR(120) NOT NULL,
    difficulty      VARCHAR(20)  NOT NULL,
    track           VARCHAR(120) NOT NULL,
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY             idx_practice_question_filter (track, chapter, knowledge_point, difficulty)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='practice question bank';

CREATE TABLE practice_answer_records
(
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    user_id          BIGINT       NOT NULL,
    question_id      BIGINT       NOT NULL,
    answered_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    duration_seconds INT          NOT NULL DEFAULT 0,
    user_answer      VARCHAR(10)  NOT NULL,
    correct_answer   VARCHAR(10)  NOT NULL,
    correct          TINYINT      NOT NULL DEFAULT 0,
    points           INT          NOT NULL DEFAULT 0,
    chapter          VARCHAR(120) NOT NULL,
    knowledge_point  VARCHAR(120) NOT NULL,
    difficulty       VARCHAR(20)  NOT NULL,
    track            VARCHAR(120) NOT NULL,
    attempt_number   INT          NOT NULL DEFAULT 1,
    source_mode      VARCHAR(30)  NOT NULL DEFAULT 'SEQUENTIAL',
    doubtful         TINYINT      NOT NULL DEFAULT 0,
    review_note      TEXT,
    PRIMARY KEY (id),
    KEY              idx_practice_record_user_time (user_id, answered_at),
    KEY              idx_practice_record_user_question (user_id, question_id),
    KEY              idx_practice_record_user_correct (user_id, correct),
    KEY              idx_practice_record_track_points (track, points),
    CONSTRAINT fk_practice_record_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_practice_record_question FOREIGN KEY (question_id) REFERENCES practice_questions (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='practice answer records';

CREATE TABLE practice_user_stats
(
    user_id            BIGINT  NOT NULL,
    total_points       INT     NOT NULL DEFAULT 0,
    total_answers      INT     NOT NULL DEFAULT 0,
    correct_answers    INT     NOT NULL DEFAULT 0,
    conquered_mistakes INT     NOT NULL DEFAULT 0,
    current_streak     INT     NOT NULL DEFAULT 0,
    last_practice_date DATE             DEFAULT NULL,
    daily_bonus_date   DATE             DEFAULT NULL,
    leaderboard_hidden TINYINT NOT NULL DEFAULT 0,
    preferred_track    VARCHAR(120)     DEFAULT '高数期末',
    updated_at         DATETIME         DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id),
    CONSTRAINT fk_practice_stats_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='practice user statistics';

CREATE TABLE practice_teams
(
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    name          VARCHAR(80) NOT NULL,
    owner_user_id BIGINT      NOT NULL,
    invite_code   VARCHAR(20) NOT NULL,
    created_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_practice_team_invite (invite_code),
    CONSTRAINT fk_practice_team_owner FOREIGN KEY (owner_user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='voluntary practice teams';

CREATE TABLE practice_team_members
(
    id        BIGINT NOT NULL AUTO_INCREMENT,
    team_id   BIGINT NOT NULL,
    user_id   BIGINT NOT NULL,
    joined_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_practice_team_member (team_id, user_id),
    CONSTRAINT fk_practice_team_member_team FOREIGN KEY (team_id) REFERENCES practice_teams (id),
    CONSTRAINT fk_practice_team_member_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='practice team members';

INSERT INTO practice_questions
(title, content, option_a, option_b, option_c, option_d, correct_answer, analysis, chapter, knowledge_point, difficulty,
 track)
VALUES ('导数定义辨析', '函数 f(x)=x^2 在 x=2 处的导数是多少？', '2', '4', '6', '8', 'B', 'f''(x)=2x，因此 f''(2)=4。',
        '导数与微分', '导数定义', 'BASIC', '高数期末'),
       ('极限计算', 'lim x->0 sin(x)/x 的值是？', '0', '1', '不存在', '无穷大', 'B', '这是重要基本极限，值为 1。',
        '函数极限', '重要极限', 'BASIC', '高数期末'),
       ('定积分含义', '∫0到1 2x dx 的值是？', '0', '1', '2', '1/2', 'B', '原函数为 x^2，代入 1 和 0 得 1。', '积分学',
        '定积分', 'BASIC', '高数期末'),
       ('矩阵秩判断', '3x3 单位矩阵的秩是？', '1', '2', '3', '0', 'C', '单位矩阵三行线性无关，秩为 3。', '线性代数基础',
        '矩阵秩', 'BASIC', '考研数学'),
       ('概率基础', '随机事件 A 的概率 P(A) 不可能是？', '-0.2', '0', '0.6', '1', 'A', '概率取值范围是 0 到 1。',
        '概率论', '概率公理', 'BASIC', '考研数学'),
       ('复杂度判断', '二分查找在有序数组中的时间复杂度是？', 'O(1)', 'O(log n)', 'O(n)', 'O(n log n)', 'B',
        '每次查找都把搜索区间缩小一半。', '算法基础', '时间复杂度', 'BASIC', '计算机二级'),
       ('SQL 查询', '查询表 users 中所有记录通常使用？', 'SELECT * FROM users', 'DELETE FROM users', 'DROP TABLE users',
        'UPDATE users', 'A', 'SELECT 用于查询数据。', '数据库基础', 'SQL 查询', 'BASIC', '计算机二级'),
       ('英语词义', '“evaluate” 最接近的中文含义是？', '逃避', '评估', '重复', '删除', 'B', 'evaluate 表示评估、评价。',
        '核心词汇', '词义辨析', 'BASIC', '四六级'),
       ('阅读推断', '阅读题中 inference question 主要考查？', '字面抄写', '推断能力', '拼写能力', '发音能力', 'B',
        'inference question 要根据上下文推断。', '阅读理解', '推理判断', 'ADVANCED', '四六级'),
       ('洛必达条件', '使用洛必达法则前，通常需要先确认极限形式是？', '0/0 或 ∞/∞', '1+1', '常数', '负数', 'A',
        '洛必达法则适用于未定式 0/0 或 ∞/∞ 等情形。', '函数极限', '洛必达法则', 'ADVANCED', '高数期末'),
       ('特征值概念', '若 Ax=λx 且 x 非零，则 λ 称为 A 的？', '行列式', '特征值', '秩', '逆矩阵', 'B',
        '这是特征值与特征向量的定义。', '线性代数基础', '特征值', 'ADVANCED', '考研数学'),
       ('动态规划识别', '动态规划最常利用的问题性质是？', '完全随机', '最优子结构和重叠子问题', '无输入', '只适合排序',
        'B', '动态规划通过保存子问题结果避免重复计算。', '算法基础', '动态规划', 'HARD', '计算机二级');
