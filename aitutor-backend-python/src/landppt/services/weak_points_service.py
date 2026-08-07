"""
薄弱点 AI 综合分析服务
"""
import logging
from typing import Optional

from ..ai import get_ai_provider, AIMessage, MessageRole
from ..core.config import ai_config
from .prompts.weak_points_prompts import WeakPointsPrompts

logger = logging.getLogger(__name__)


class WeakPointsAnalysisService:
    """薄弱点 AI 分析服务

    调用 AI Provider（OpenAI/Claude 等）生成：
    - 薄弱点综合分析
    - 个性化学习建议
    - 每个薄弱点的详细分析和改进方案
    - 优先级排序
    """

    def __init__(self, provider_name: Optional[str] = None):
        self.provider_name = provider_name
        self.prompts = WeakPointsPrompts()

    @property
    def ai_provider(self):
        """动态获取 AI provider，确保使用最新配置"""
        provider_name = self.provider_name or ai_config.default_ai_provider
        return get_ai_provider(provider_name)

    async def analyze_weak_points(self, request_data: dict) -> dict:
        """分析薄弱点并生成学习建议

        Args:
            request_data: 包含 weak_points、recent_exercises、user_id、language

        Returns:
            包含 comprehensive_analysis、learning_suggestions、
            detail_analyses、recommended_priority 的字典
        """
        try:
            weak_points = request_data.get("weakPoints", [])
            recent_exercises = request_data.get("recentExercises", [])
            language = request_data.get("language", "zh")

            logger.info(f"开始薄弱点分析: userId={request_data.get('userId')}, "
                        f"薄弱点数={len(weak_points)}, 练习记录数={len(recent_exercises)}")

            # 构建提示词
            system_prompt = self.prompts.get_system_prompt()
            user_prompt = self.prompts.get_analysis_prompt(
                weak_points=weak_points,
                recent_exercises=recent_exercises,
                language=language
            )

            # 调用 AI
            messages = [
                AIMessage(role=MessageRole.SYSTEM, content=system_prompt),
                AIMessage(role=MessageRole.USER, content=user_prompt)
            ]

            response = await self.ai_provider.chat_completion(
                messages=messages,
                max_tokens=ai_config.max_tokens,
                temperature=0.7
            )

            # 解析响应
            result = self.prompts.parse_ai_response(response.content)

            if "parse_error" in result:
                logger.warning(f"AI 响应解析部分失败: {result['parse_error']}")

            logger.info(f"薄弱点分析完成，分析长度: {len(result.get('comprehensive_analysis', ''))} 字符")
            result["status"] = "success"
            return result

        except Exception as e:
            logger.error(f"薄弱点 AI 分析失败: {e}", exc_info=True)
            return {
                "comprehensive_analysis": "",
                "learning_suggestions": "",
                "detail_analyses": [],
                "recommended_priority": [],
                "status": "error",
                "error": str(e)
            }
