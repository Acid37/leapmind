package com.treepeople.leapmindtts.service.lesson;

import com.treepeople.leapmindtts.pojo.dto.BulkSynthesisRequest.SlideData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * PPT上下文感知润色服务
 * 根据PPT的结构化信息生成上下文感知的润色提示词
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PPTContextualPolishing {

    /**
     * 根据PPT上下文信息生成润色提示词
     *
     * @param slide 当前slide信息
     * @param contentPoint 需要润色的内容点
     * @param pointIndex 内容点在slide中的索引
     * @return 上下文感知的润色提示词
     */
    public String generatePolishingPrompt(SlideData slide, String contentPoint, int pointIndex) {
        // 计算原文字数，用于字数控制
        int originalLength = contentPoint != null ? contentPoint.length() : 0;

        // 检查是否为目录页，如果是，使用专门的简化提示词
        if (isAgendaPage(slide.getTitle()) || "agenda".equals(slide.getSlideType())) {
            return generateAgendaPrompt(slide, contentPoint, originalLength);
        }

        // 普通页面的润色提示词
        return generateNormalPrompt(slide, contentPoint, pointIndex, originalLength);
    }

    /**
     * 为目录页面生成专门的简化提示词
     */
    private String generateAgendaPrompt(SlideData slide, String contentPoint, int originalLength) {
        StringBuilder prompt = new StringBuilder();

        // 目录页面字数控制：最多200字
        int maxAllowedLength = 200;

        prompt.append("我希望你能够给出一篇字数不超过200字，而且是作为目录的讲稿。\n\n");
        prompt.append("要求：\n");
        prompt.append("1. 以\"各位同学好\"或类似的问候语开始\n");
        prompt.append("2. 简要介绍今天要学习的主题\n");
        prompt.append("3. 用\"首先...再...接着...还会...最后...\"等过渡词串联各个章节\n");
        prompt.append("4. 语言自然流畅，适合老师口语化讲课\n");
        prompt.append("5. 不要展开详细讲解，只需概述学习路径\n");
        prompt.append("6. 【严格限制】总字数不得超过200字\n");
        prompt.append("7. 只返回讲稿内容，不要任何说明文字\n\n");

        prompt.append("参考示例风格：\n");
        prompt.append("\"各位同学好，今天我们学习SOA相关知识。首先明确什么是SOA，再了解其核心组件。接着比较SOA与微服务，探讨Spring Cloud在SOA中的应用及最佳实践。还会学习服务治理与监控、安全策略及最佳实践。分析企业级SOA实施的成败案例，探讨其挑战与未来趋势，以及接口规范与设计。最后总结、问答，提供参考资料和联系方式。\"\n\n");

        prompt.append("课程标题：").append(slide.getTitle()).append("\n");
        prompt.append("目录内容：\n").append(contentPoint);

        log.debug("为目录页面 {} 生成讲稿风格润色提示词，原文{}字，上限{}字",
                slide.getTitle(), originalLength, maxAllowedLength);

        return prompt.toString();
    }

    /**
     * 为普通页面生成润色提示词
     */
    private String generateNormalPrompt(SlideData slide, String contentPoint, int pointIndex, int originalLength) {
        StringBuilder prompt = new StringBuilder();

        int maxAllowedLength = originalLength + 200; // 最多增加200字

        // 基础润色要求
        prompt.append("请将以下PPT内容润色为适合老师讲课的内容，要求：\n");
        prompt.append("1. 保持原意不变，确保信息准确性\n");
        prompt.append("2. 语言更加生动、易懂，适合学生理解\n");
        prompt.append("3. 适合口语化表达，增强互动感\n");
        prompt.append("4. 增加适当的过渡词和解释，提升连贯性\n");
        prompt.append("5. 根据PPT页面类型调整讲解风格\n");
        prompt.append("6. 切勿加入与本页无关的大段拓展说明，避免过度发挥\n");
        prompt.append("7. 【重要】润色后的内容字数不得超过").append(maxAllowedLength).append("字（原文").append(originalLength).append("字+200字）\n");
        prompt.append("8. 只返回润色后的教学内容，不要包含任何说明、引导语、分隔符或额外的话语\n\n");

        // 添加PPT上下文信息
        prompt.append("PPT上下文信息：\n");
        prompt.append("- 当前页面标题：").append(slide.getTitle()).append("\n");
        prompt.append("- 页面类型：").append(slide.getSlideType()).append("\n");
        if (slide.getDescription() != null && !slide.getDescription().trim().isEmpty()) {
            prompt.append("- 页面描述：").append(slide.getDescription()).append("\n");
        }
        prompt.append("- 当前是第").append(pointIndex + 1).append("个内容点\n");

        // 根据slide类型和标题添加特定指导
        addTypeSpecificGuidance(prompt, slide.getSlideType(), slide.getTitle(), originalLength);

        prompt.append("\n需要润色的内容：\n").append(contentPoint);

        log.debug("为页面 {} 的第 {} 个内容点生成润色提示词，原文{}字，最大允许{}字",
                slide.getTitle(), pointIndex + 1, originalLength, maxAllowedLength);

        return prompt.toString();
    }

    /**
     * 根据slide类型和标题添加特定的讲解风格指导
     */
    private void addTypeSpecificGuidance(StringBuilder prompt, String slideType, String slideTitle, int originalLength) {
        // 根据标题判断是否为目录页
        boolean isAgendaPage = isAgendaPage(slideTitle);

        if (slideType == null && !isAgendaPage) {
            prompt.append("- 讲解风格：根据内容特点灵活调整\n");
            prompt.append("- 篇幅控制：适度精炼，避免冗长；不超过原文+200字\n");
            return;
        }

        // 如果标题表明是目录页，优先按目录页处理
        if (isAgendaPage) {
            prompt.append("- 讲解风格：【目录页】简要介绍课程结构，逐项概述要点，引导学习路径\n");
            prompt.append("- 语言特点：条理清晰，承上启下，简洁明了\n");
            prompt.append("- 篇幅控制：【严格限制】这是目录页，只需简述各章节要点，不要展开详细讲解，严格控制在原文+200字以内\n");
            return;
        }

        String type = slideType != null ? slideType.toLowerCase() : "";
        switch (type) {
            case "title":
                prompt.append("- 讲解风格：开场介绍，热情欢迎，概括主题，激发学习兴趣\n");
                prompt.append("- 语言特点：简洁有力，富有感染力，设置悬念\n");
                prompt.append("- 篇幅控制：简要引入与主题概述，不要展开详细讲解；严格控制在原文+200字以内\n");
                break;

            case "agenda":
                prompt.append("- 讲解风格：清晰列举，逻辑顺序，引导期待，建立学习路径\n");
                prompt.append("- 语言特点：条理清晰，承上启下，预告重点\n");
                prompt.append("- 篇幅控制：【严格限制】这是目录/大纲页，仅简述结构或章节安排，不要展开细节，严格控制在原文+200字以内\n");
                break;

            case "content":
                prompt.append("- 讲解风格：详细解释，举例说明，互动提问，深入浅出\n");
                prompt.append("- 语言特点：通俗易懂，生动形象，适当停顿\n");
                prompt.append("- 篇幅控制：围绕关键点讲解，避免无关扩展；严格控制在原文+200字以内\n");
                break;

            case "thankyou":
                prompt.append("- 讲解风格：总结回顾，感谢聆听，鼓励提问，留下深刻印象\n");
                prompt.append("- 语言特点：温暖真诚，回味无穷，开放互动\n");
                prompt.append("- 篇幅控制：简要收束全篇内容，避免重复大段阐述；严格控制在原文+200字以内\n");
                break;

            default:
                prompt.append("- 讲解风格：根据内容特点灵活调整，保持专业性和亲和力\n");
                prompt.append("- 语言特点：自然流畅，重点突出，易于理解\n");
                prompt.append("- 篇幅控制：适度精炼，避免冗长；严格控制在原文+200字以内\n");
        }
    }

    /**
     * 根据标题判断是否为目录页
     */
    private boolean isAgendaPage(String slideTitle) {
        if (slideTitle == null) {
            return false;
        }

        String title = slideTitle.toLowerCase().trim();

        // 常见的目录页标题关键词
        String[] agendaKeywords = {
            "目录", "大纲", "内容", "章节", "agenda", "contents", "outline",
            "课程安排", "学习内容", "主要内容", "课程大纲", "课程目录",
            "今日内容", "本次课程", "课程结构", "学习路径"
        };

        for (String keyword : agendaKeywords) {
            if (title.contains(keyword)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 验证润色提示词是否合理
     */
    public boolean validatePrompt(String prompt) {
        if (prompt == null || prompt.trim().isEmpty()) {
            log.warn("润色提示词为空");
            return false;
        }

        if (prompt.length() > 2000) {
            log.warn("润色提示词过长，长度: {}", prompt.length());
            return false;
        }

        return true;
    }
}
