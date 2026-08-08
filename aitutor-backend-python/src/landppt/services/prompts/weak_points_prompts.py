"""
薄弱点分析 AI 提示词模板
"""

import json


class WeakPointsPrompts:
    """薄弱点分析提示词集合"""

    @staticmethod
    def get_system_prompt() -> str:
        """获取系统角色提示词"""
        return """你是一位经验丰富的教育分析专家，擅长诊断学生的学习薄弱点并提供个性化学习建议。

你的职责：
1. 分析学生的练习数据，识别知识薄弱点和错误模式
2. 生成综合性的学习状况分析报告
3. 提供具体、可操作的个性化学习建议
4. 按优先级排序薄弱点，给出攻克顺序建议

分析维度：
- **知识掌握程度**：正确率、错误频率、错误类型
- **薄弱点根源**：概念不清、计算失误、理解偏差、记忆不牢等
- **学习策略问题**：是否存在系统性的方法问题
- **提升路径**：短期目标和长期规划

输出要求：
1. 综合分析要全面但简洁，200-500字
2. 学习建议要具体可操作，每条建议有明确的目标
3. 优先级排序要合理，标注优先攻克的知识点
4. 语言要亲切鼓励，增强学生信心

请以 JSON 格式输出，使用```json```代码块包裹，格式如下：
```json
{
    "comprehensive_analysis": "综合分析文本...",
    "learning_suggestions": "个性化学习建议文本...",
    "detail_analyses": [
        {
            "knowledge_point": "知识点名称",
            "analysis": "该知识点的薄弱原因分析",
            "suggestion": "针对该知识点的学习建议"
        }
    ],
    "recommended_priority": ["知识点1", "知识点2", ...]
}
```"""

    @staticmethod
    def get_analysis_prompt(weak_points: list, recent_exercises: list, language: str = "zh") -> str:
        """构建薄弱点分析用户提示词

        Args:
            weak_points: 薄弱点列表
            recent_exercises: 最近的练习记录
            language: 语言，默认 zh

        Returns:
            格式化的提示词字符串
        """
        prompt_parts = []

        # 薄弱点数据
        if weak_points:
            prompt_parts.append("## 学生薄弱点数据\n")
            prompt_parts.append("| 知识点 | 学科 | 薄弱程度 | 错误次数 | 总次数 | 正确率 |")
            prompt_parts.append("|--------|------|----------|----------|--------|--------|")
            for wp in weak_points:
                # Java Jackson 序列化使用 camelCase 字段名
                kp = wp.get("knowledgePoint", wp.get("knowledge_point", "未知"))
                subject = wp.get("subject", "未知")
                level = wp.get("weaknessLevel", wp.get("weakness_level", "MEDIUM"))
                errors = wp.get("errorCount", wp.get("error_count", 0))
                total = wp.get("totalCount", wp.get("total_count", 0))
                rate = wp.get("accuracyRate", wp.get("accuracy_rate", 0))
                prompt_parts.append(f"| {kp} | {subject} | {level} | {errors} | {total} | {rate}% |")

        # 练习记录
        if recent_exercises:
            prompt_parts.append("\n## 近期练习记录\n")
            correct_count = sum(1 for e in recent_exercises
                              if e.get("isCorrect", e.get("is_correct", 0)) == 1)
            total_count = len(recent_exercises)
            prompt_parts.append(f"近30天完成 {total_count} 道练习，正确 {correct_count} 道，正确率 {correct_count*100//max(total_count,1)}%\n")

        prompt_parts.append("\n请根据以上数据分析学生的薄弱点，给出综合分析报告和个性化学习建议。")

        return "\n".join(prompt_parts)

    @staticmethod
    def parse_ai_response(content: str) -> dict:
        """解析 AI 返回的 JSON 响应

        Args:
            content: AI 返回的原始文本

        Returns:
            解析后的字典
        """
        try:
            # 尝试提取 JSON 代码块
            if "```json" in content:
                start = content.index("```json") + 7
                end = content.index("```", start)
                json_str = content[start:end].strip()
            elif "```" in content:
                start = content.index("```") + 3
                end = content.index("```", start)
                json_str = content[start:end].strip()
            else:
                json_str = content.strip()

            return json.loads(json_str)

        except (json.JSONDecodeError, ValueError) as e:
            # 解析失败，返回原始文本作为综合分析
            return {
                "comprehensive_analysis": content,
                "learning_suggestions": "AI分析结果解析失败，请稍后重试。",
                "detail_analyses": [],
                "recommended_priority": [],
                "parse_error": str(e)
            }
