# MySQL 连接配置（与 Java 后端共享数据库）
# 生产环境通过环境变量注入，开发环境可在 .env 中配置
import os
MYSQL = {
    "host": os.getenv("MYSQL_HOST", "localhost"),
    "port": int(os.getenv("MYSQL_PORT", "3306")),
    "user": os.getenv("MYSQL_USER", "root"),
    "password": os.getenv("MYSQL_PASSWORD", ""),
    "database": os.getenv("MYSQL_DATABASE", "leapmind"),
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
