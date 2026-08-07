"""
薄弱点分析 API 端点

由 Java 后端通过 HTTP POST 调用，接收薄弱点数据，
调用 AI 生成综合分析报告和个性化学习建议。
"""
import logging
from typing import List, Optional

from fastapi import APIRouter, HTTPException
from pydantic import BaseModel, Field

from ..services.weak_points_service import WeakPointsAnalysisService

router = APIRouter()
logger = logging.getLogger(__name__)

# 懒加载服务实例
_analysis_service: Optional[WeakPointsAnalysisService] = None


def get_analysis_service() -> WeakPointsAnalysisService:
    """获取分析服务实例（懒加载）"""
    global _analysis_service
    if _analysis_service is None:
        try:
            _analysis_service = WeakPointsAnalysisService()
            logger.info("薄弱点分析服务初始化成功")
        except Exception as e:
            logger.error(f"薄弱点分析服务初始化失败: {e}")
            raise
    return _analysis_service


# ==================== 请求模型 ====================

class WeakPointItem(BaseModel):
    """薄弱点条目"""
    id: Optional[int] = None
    knowledgePoint: str = Field(..., description="知识点名称")
    subject: Optional[str] = Field(None, description="学科")
    weaknessLevel: Optional[str] = Field("MEDIUM", description="薄弱程度：HIGH/MEDIUM/LOW")
    errorCount: Optional[int] = Field(0, description="错误次数")
    totalCount: Optional[int] = Field(0, description="总答题次数")
    accuracyRate: Optional[float] = Field(None, description="正确率(%)")


class ExerciseRecordItem(BaseModel):
    """练习记录条目"""
    exerciseId: Optional[str] = Field(None, description="练习ID")
    knowledgePoint: Optional[str] = Field(None, description="知识点")
    subject: Optional[str] = Field(None, description="学科")
    isCorrect: Optional[int] = Field(0, description="是否正确：1-正确 0-错误")
    completedAt: Optional[str] = Field(None, description="完成时间")


class WeakPointsAnalysisRequest(BaseModel):
    """薄弱点分析请求"""
    userId: int = Field(..., description="用户ID")
    weakPoints: List[WeakPointItem] = Field(default_factory=list, description="薄弱点列表")
    recentExercises: List[ExerciseRecordItem] = Field(default_factory=list, description="近期练习记录")
    language: str = Field("zh", description="语言")


# ==================== 响应模型 ====================

class DetailAnalysis(BaseModel):
    """单个薄弱点详细分析"""
    knowledgePoint: str = Field(..., description="知识点名称")
    analysis: str = Field(..., description="薄弱原因分析")
    suggestion: str = Field(..., description="针对性学习建议")


class WeakPointsAnalysisResponse(BaseModel):
    """薄弱点分析响应"""
    comprehensiveAnalysis: str = Field("", description="综合薄弱点分析")
    learningSuggestions: str = Field("", description="个性化学习建议")
    detailAnalyses: List[DetailAnalysis] = Field(default_factory=list, description="详细分析列表")
    recommendedPriority: List[str] = Field(default_factory=list, description="推荐优先攻克的知识点")
    status: str = Field("success", description="状态：success/error")
    error: Optional[str] = Field(None, description="错误信息")


# ==================== API 端点 ====================

@router.post(
    "/api/weak-points/analyze",
    response_model=WeakPointsAnalysisResponse,
    summary="AI 薄弱点综合分析",
    description="接收用户薄弱点数据和练习记录，调用 AI 生成综合分析报告和个性化学习建议"
)
async def analyze_weak_points(request: WeakPointsAnalysisRequest):
    """
    AI 综合分析用户薄弱点

    - **userId**: 用户ID
    - **weakPoints**: 薄弱点列表（包含知识点、错误次数、正确率等）
    - **recentExercises**: 近期练习记录（用于上下文分析）
    - **language**: 输出语言，默认 zh

    由 Java 后端 WeakPointsController 通过 HTTP POST 调用。
    """
    try:
        service = get_analysis_service()
        result = await service.analyze_weak_points(request.model_dump(by_alias=True))

        if result.get("status") == "error":
            logger.error(f"AI分析返回错误: {result.get('error')}")
            # 返回错误响应但仍然用200，由status字段标识
            return WeakPointsAnalysisResponse(
                status="error",
                error=result.get("error", "未知错误")
            )

        return WeakPointsAnalysisResponse(
            comprehensiveAnalysis=result.get("comprehensive_analysis", ""),
            learningSuggestions=result.get("learning_suggestions", ""),
            detailAnalyses=[
                DetailAnalysis(
                    knowledgePoint=da.get("knowledge_point", ""),
                    analysis=da.get("analysis", ""),
                    suggestion=da.get("suggestion", "")
                )
                for da in result.get("detail_analyses", [])
            ],
            recommendedPriority=result.get("recommended_priority", []),
            status="success"
        )

    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"薄弱点分析端点异常: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail=f"分析服务异常: {str(e)}")
