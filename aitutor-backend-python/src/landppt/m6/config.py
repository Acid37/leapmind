# MySQL 连接配置（与 Java 后端共享 leapmind-voice 数据库）
# Python M6 模块通过此连接直接读写 MySQL
MYSQL = {
    "host": "localhost",
    "port": 3306,
    "user": "root",
    "password": "1234",
    "database": "leapmind-voice",
    "charset": "utf8mb4",
}

# 艾宾浩斯遗忘曲线复习间隔（单位：天）
# stage 0→1 天, 1→3 天, 2→7 天, 3→30 天（含以上）
REVIEW_INTERVALS = [1, 3, 7, 30]
MAX_STAGE = len(REVIEW_INTERVALS)

# 学习风格推断阈值配置
# 各阈值用于判断用户属于 reading/visual/practitioner/balanced 哪种风格
LEARNING_STYLE_CONFIG = {
    "correct_fast_threshold": 0.8,   # 快速答题正确率 ≥80%
    "fast_time_threshold": 30,        # 快速答题时间 ＜30秒
    "confused_frequency": 3,          # 概念模糊标记 ≥3次
    "retry_threshold": 2,             # 答错后立即重做 ≥2次
}
