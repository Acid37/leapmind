"""M6 模块测试共享夹具：使用 mock 隔离真实 MySQL 数据库。

为 M6 的 profile_engine / review_scheduler / 事件处理测试提供：
- make_conn：构造带上下文管理协议的 mock 游标，并可按调用顺序预置 fetchone/fetchall 返回值。
- sql_asserts：断言 mock 游标上 execute 调用（SQL 片段、参数）。
"""

from unittest import mock

import pytest


@pytest.fixture
def make_conn():
    """构造模拟数据库连接与游标。

    返回工厂函数 make_conn(fetchone=None, fetchall=None) -> (conn, cursor)：
      - fetchone：单条查询返回值；传入列表时按调用顺序逐个返回（side_effect）。
      - fetchall：多条查询返回值；传入列表时按调用顺序逐个返回。
    返回的 cursor 供后续断言 execute 的 SQL 与参数。
    """

    def _factory(fetchone=None, fetchall=None):
        conn = mock.MagicMock()
        cursor = conn.cursor.return_value.__enter__.return_value
        if fetchone is not None:
            if isinstance(fetchone, list):
                cursor.fetchone.side_effect = fetchone
            else:
                cursor.fetchone.return_value = fetchone
        if fetchall is not None:
            cursor.fetchall.side_effect = fetchall
        return conn, cursor

    return _factory


@pytest.fixture
def sql_asserts():
    """提供对 mock 游标 execute 调用的断言辅助函数集合。"""

    def fragments(cursor):
        """返回每次 execute 的 SQL 文本列表。"""
        return [call.args[0] for call in cursor.execute.call_args_list]

    def contains(cursor, fragment):
        """断言至少一次 execute 的 SQL 包含指定片段。"""
        joined = "\n".join(fragments(cursor))
        assert fragment in joined, f"未找到 SQL 片段: {fragment}\n实际 SQL:\n{joined}"

    def params(cursor, index=-1):
        """返回指定位置（默认最后一次）execute 的参数。"""
        return cursor.execute.call_args_list[index][0][1]

    def params_for(cursor, fragment):
        """返回第一条包含指定 SQL 片段的 execute 的参数；找不到则返回 None。"""
        for call in cursor.execute.call_args_list:
            if fragment in call.args[0]:
                return call.args[1]
        return None

    def execute_count(cursor):
        """返回 execute 的调用次数。"""
        return len(cursor.execute.call_args_list)

    return {
        "fragments": fragments,
        "contains": contains,
        "params": params,
        "params_for": params_for,
        "execute_count": execute_count,
    }
