"""
FastAPI 入口 — 提供 M3 薄弱点分析 REST API

运行方式：
    cd backend
    uvicorn main:app --reload

Swagger 文档：http://127.0.0.1:8000/docs
"""
import logging
from datetime import datetime
from contextlib import asynccontextmanager

from fastapi import FastAPI, Query

from calculator.ai_service import generate_assessment
from calculator.knowledge_graph import (
    build_knowledge_graph,
    compute_node_importance,
    get_graph_data,
)
from calculator.kp_tree import build_kp_tree, get_sub_weak_points, aggregate_parent_weakness
from calculator.recommend import recommend_questions
from calculator.scheduler import init_scheduler, run_incremental_update
from calculator.trend_analysis import analyze_trends
from calculator.weakness_score import calculate_weakness_scores
from database.mysql_connector import Database
from models.schemas import (
    GraphEdge,
    GraphNode,
    IncrementalUpdateRequest,
    IncrementalUpdateResponse,
    KnowledgeGraphResponse,
    QuestionItem,
    RecommendQuestionsResponse,
    SubWeakPoint,
    WeakPoint,
    WeakPointsResponse,
)

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("m3")

db = Database()


@asynccontextmanager
async def lifespan(app: FastAPI):
    scheduler = init_scheduler(app)
    scheduler.start()
    logger.info("M3 薄弱点服务已启动，定时调度已开启")
    yield
    scheduler.shutdown(wait=False)
    logger.info("M3 薄弱点服务已关闭")


app = FastAPI(
    title="M3 薄弱点分析服务",
    version="1.0.0",
    docs_url="/docs",
    lifespan=lifespan,
)


# ──────────────────────────────────────────────
# 接口 1：获取薄弱点列表 — 提供给 M4/M5
# ──────────────────────────────────────────────
@app.get(
    "/api/weak-points",
    response_model=WeakPointsResponse,
    response_model_by_alias=True,
)
def get_weak_points(
    user_id: int = Query(..., alias="userId", description="用户ID"),
    top_n: int = Query(10, alias="topN", description="返回前N个薄弱点"),
):
    # 1. 拉取原始数据（文档 4.1.1 中列出的 4 个数据源）
    user_answers = db.fetch_user_answers(user_id)
    conversation_msgs = db.fetch_conversation_messages(user_id)
    knowledge_points = db.fetch_knowledge_points()
    wrong_book = db.fetch_wrong_question_book(user_id)
    user_profile = db.fetch_user_profile(user_id)

    if not user_answers:
        return WeakPointsResponse(
            user_id=user_id,
            weak_points=[],
            updated_at=datetime.now(),
            overall_assessment="暂无做题数据，请先完成练习。",
        )

    kp_map = {kp["id"]: kp["name"] for kp in knowledge_points}

    # 2. 计算薄弱度（多源融合：答题记录 + 对话困惑 + 错题本 + 用户画像）
    weak_points = calculate_weakness_scores(
        user_answers, conversation_msgs, knowledge_points,
        wrong_question_book=wrong_book, user_profile=user_profile,
    )

    # 3. 趋势分析
    weak_points = analyze_trends(user_answers, weak_points)

    # 4. 聚合父知识点薄弱度
    weak_points = aggregate_parent_weakness(weak_points, knowledge_points)

    # 5. 构建知识图谱（供前端可视化用，不影响分数）
    G = build_knowledge_graph(knowledge_points)
    importance = compute_node_importance(G)

    # 6. 构建知识点树
    kp_tree = build_kp_tree(knowledge_points)

    # 7. 取 Top N，补充子知识点 + 推荐题目
    top_points = weak_points[:top_n]
    result_points = []

    for wp in top_points:
        sub_weak = get_sub_weak_points(kp_tree, wp["kp_id"], weak_points, kp_map)
        sub_weak_models = [SubWeakPoint(**s) for s in sub_weak]

        _, recommended_qs = recommend_questions(user_id, wp["kp_id"], count=3, db=db)
        recommended_ids = [q["id"] for q in recommended_qs]

        # 持久化计算结果
        db.upsert_weak_point({
            "user_id": user_id,
            "kp_id": wp["kp_id"],
            "weakness_score": wp["weakness_score"],
            "error_count": wp["error_count"],
            "total_attempts": wp["total_attempts"],
            "error_rate": wp["error_rate"],
            "recent_correct_rate": wp["recent_correct_rate"],
            "confusion_count": wp["confusion_count"],
            "trend": wp["trend"],
            "last_error_at": wp.get("last_error_at"),
            "calculated_at": datetime.now(),
        })

        result_points.append(
            WeakPoint(
                kp_id=wp["kp_id"],
                kp_name=wp["kp_name"],
                weakness_score=wp["weakness_score"],
                error_count=wp["error_count"],
                total_attempts=wp["total_attempts"],
                error_rate=wp["error_rate"],
                trend=wp["trend"],
                last_error_at=wp.get("last_error_at"),
                recommended_questions=recommended_ids,
                sub_weak_points=sub_weak_models,
            )
        )

    # 8. 生成总体评估
    raw_weak_points = [
        {
            "kp_name": wp.kp_name,
            "weakness_score": wp.weakness_score,
            "error_rate": wp.error_rate,
            "trend": wp.trend,
        }
        for wp in result_points
    ]
    assessment = generate_assessment(raw_weak_points)

    return WeakPointsResponse(
        user_id=user_id,
        weak_points=result_points,
        updated_at=datetime.now(),
        overall_assessment=assessment,
    )


# ──────────────────────────────────────────────
# 接口 2：推荐练习题目 — 提供给 M1
# ──────────────────────────────────────────────
@app.get(
    "/api/weak-points/recommend-questions",
    response_model=RecommendQuestionsResponse,
    response_model_by_alias=True,
)
def get_recommend_questions(
    user_id: int = Query(..., description="用户ID"),
    kp_id: int = Query(..., description="知识点ID"),
    count: int = Query(10, description="推荐题目数量"),
):
    kp_name, questions = recommend_questions(user_id, kp_id, count, db=db)
    return RecommendQuestionsResponse(
        kp_name=kp_name,
        questions=[
            QuestionItem(
                id=q["id"],
                kp_id=q["kp_id"],
                content=q["content"],
                difficulty=float(q["difficulty"]),
            )
            for q in questions
        ],
    )


# ──────────────────────────────────────────────
# 接口 3：获取知识图谱数据 — 提供前端可视化
# ──────────────────────────────────────────────
@app.get(
    "/api/weak-points/knowledge-graph",
    response_model=KnowledgeGraphResponse,
    response_model_by_alias=True,
)
def get_knowledge_graph(
    user_id: int = Query(..., description="用户ID"),
):
    """
    返回知识点关联图谱，包含每个知识点的薄弱度分数，
    供前端 ECharts 力导向图使用。
    """
    knowledge_points = db.fetch_knowledge_points()
    if not knowledge_points:
        return KnowledgeGraphResponse(nodes=[], edges=[])

    # 计算用户薄弱度，用于标注节点颜色/大小
    user_answers = db.fetch_user_answers(user_id)
    conversation_msgs = db.fetch_conversation_messages(user_id)
    wrong_book = db.fetch_wrong_question_book(user_id)
    user_profile = db.fetch_user_profile(user_id)

    if user_answers:
        weak_points = calculate_weakness_scores(
            user_answers, conversation_msgs, knowledge_points,
            wrong_question_book=wrong_book, user_profile=user_profile,
        )
        weak_points = analyze_trends(user_answers, weak_points)
    else:
        weak_points = []

    # 构建图谱
    G = build_knowledge_graph(knowledge_points)
    graph_data = get_graph_data(G, weak_points)

    return KnowledgeGraphResponse(
        nodes=[GraphNode(**n) for n in graph_data["nodes"]],
        edges=[GraphEdge(**e) for e in graph_data["edges"]],
    )


# ──────────────────────────────────────────────
# 接口 4：增量更新薄弱点 — 供 M1 调用
# ──────────────────────────────────────────────
@app.post(
    "/api/weak-points/incremental-update",
    response_model=IncrementalUpdateResponse,
    response_model_by_alias=True,
)
def incremental_update(body: IncrementalUpdateRequest):
    """
    增量更新薄弱点（事件驱动）。

    用户完成一组练习后，M1 调用此接口，仅对涉及的知识点重算薄弱度。

    请求示例：
        POST /api/weak-points/incremental-update
        {
            "userId": 1001,
            "kpIds": [10, 11]
        }
    """
    result = run_incremental_update(body.user_id, body.kp_ids, db=db)
    return IncrementalUpdateResponse(**result)
