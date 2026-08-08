ALTER TABLE practice_questions
    ADD COLUMN subject VARCHAR(80) NOT NULL DEFAULT '通用' AFTER id,
    ADD COLUMN grade_level VARCHAR(40) NOT NULL DEFAULT '大学' AFTER subject,
    ADD COLUMN question_type VARCHAR(30) NOT NULL DEFAULT 'SINGLE_CHOICE' AFTER grade_level,
    ADD COLUMN answer_keywords VARCHAR(1000) DEFAULT NULL AFTER correct_answer,
    ADD COLUMN lesson_id VARCHAR(80) DEFAULT NULL AFTER track,
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ENABLED' AFTER lesson_id,
    ADD COLUMN updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON
UPDATE CURRENT_TIMESTAMP AFTER created_at,
    ADD KEY idx_practice_question_full_filter (subject, grade_level, chapter, question_type, difficulty, status),
    ADD KEY idx_practice_question_lesson (lesson_id);

ALTER TABLE practice_questions
    MODIFY option_a VARCHAR (500) NULL,
    MODIFY option_b VARCHAR (500) NULL,
    MODIFY option_c VARCHAR (500) NULL,
    MODIFY option_d VARCHAR (500) NULL,
    MODIFY correct_answer VARCHAR (1000) NOT NULL;

UPDATE practice_questions
SET subject       = CASE
                        WHEN track IN ('高数期末', '考研数学') THEN '数学'
                        WHEN track = '计算机二级' THEN '计算机'
                        WHEN track = '四六级' THEN '英语'
                        ELSE '通用'
    END,
    grade_level   = '大学',
    question_type = 'SINGLE_CHOICE',
    status        = 'ENABLED'
WHERE subject = '通用';

ALTER TABLE practice_answer_records
    ADD COLUMN question_type VARCHAR(30) NOT NULL DEFAULT 'SINGLE_CHOICE' AFTER track,
    ADD COLUMN judge_score DECIMAL(5,2) DEFAULT NULL AFTER correct,
    ADD COLUMN judge_feedback VARCHAR(1000) DEFAULT NULL AFTER judge_score,
    ADD KEY idx_practice_record_knowledge (user_id, knowledge_point, answered_at),
    ADD KEY idx_practice_record_duration (user_id, duration_seconds);

CREATE TABLE practice_mistakes
(
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    user_id        BIGINT      NOT NULL,
    question_id    BIGINT      NOT NULL,
    status         VARCHAR(20) NOT NULL DEFAULT 'UNRESOLVED',
    wrong_count    INT         NOT NULL DEFAULT 1,
    review_count   INT         NOT NULL DEFAULT 0,
    doubtful       TINYINT     NOT NULL DEFAULT 0,
    review_note    TEXT,
    last_wrong_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_review_at DATETIME             DEFAULT NULL,
    resolved_at    DATETIME             DEFAULT NULL,
    created_at     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_practice_mistake_user_question (user_id, question_id),
    KEY            idx_practice_mistake_user_status (user_id, status),
    CONSTRAINT fk_practice_mistake_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_practice_mistake_question FOREIGN KEY (question_id) REFERENCES practice_questions (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='practice mistake book with lifecycle status';

CREATE TABLE practice_checkins
(
    id           BIGINT   NOT NULL AUTO_INCREMENT,
    user_id      BIGINT   NOT NULL,
    checkin_date DATE     NOT NULL,
    points       INT      NOT NULL DEFAULT 2,
    created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_practice_checkin_user_date (user_id, checkin_date),
    CONSTRAINT fk_practice_checkin_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='practice daily check-in points';

INSERT INTO practice_mistakes
(user_id, question_id, status, wrong_count, doubtful, review_note, last_wrong_at, created_at, updated_at)
SELECT user_id,
       question_id,
       'UNRESOLVED',
       COUNT(*),
       MAX(doubtful),
       MAX(review_note),
       MAX(answered_at),
       MIN(answered_at),
       MAX(answered_at)
FROM practice_answer_records
WHERE correct = 0
GROUP BY user_id, question_id ON DUPLICATE KEY
UPDATE
    wrong_count =
VALUES (wrong_count), doubtful =
VALUES (doubtful), review_note =
VALUES (review_note), last_wrong_at =
VALUES (last_wrong_at), updated_at =
VALUES (updated_at);

INSERT INTO practice_questions
(subject, grade_level, question_type, title, content, option_a, option_b, option_c, option_d, correct_answer,
 answer_keywords, analysis, chapter, knowledge_point, difficulty, track, status)
VALUES ('数学', '大学', 'FILL_BLANK', '导数填空', '函数 f(x)=x^3 在 x=2 处的导数是 ____ 。', NULL, NULL, NULL, NULL,
        '12', '12;十二', 'f''(x)=3x^2，代入 x=2 得 12。', '导数与微分', '导数计算', 'ADVANCED', '高数期末', 'ENABLED'),
       ('英语', '大学', 'FILL_BLANK', '词义填空', '单词 evaluate 的常见中文含义是 ____ 。', NULL, NULL, NULL, NULL,
        '评估', '评估;评价;评定', 'evaluate 常表示评估、评价。', '核心词汇', '词义辨析', 'BASIC', '四六级', 'ENABLED'),
       ('计算机', '大学', 'SHORT_ANSWER', '简答：动态规划', '请简要说明动态规划适合解决哪类问题。', NULL, NULL, NULL,
        NULL, '最优子结构和重叠子问题', '最优子结构;重叠子问题;保存子问题结果;状态转移',
        '简答题会根据关键词覆盖和语义相似度给出基础判分，后续可接 Python AI 进一步增强。', '算法基础', '动态规划', 'HARD',
        '计算机二级', 'ENABLED');
