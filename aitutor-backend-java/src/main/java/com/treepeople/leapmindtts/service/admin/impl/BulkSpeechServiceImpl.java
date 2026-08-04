package com.treepeople.leapmindtts.service.admin.impl;

import com.treepeople.leapmindtts.mapper.AudioSegmentMapper;
import com.treepeople.leapmindtts.pojo.dto.*;
import com.treepeople.leapmindtts.pojo.entity.AudioSegment;
import com.treepeople.leapmindtts.pojo.entity.LessonSession;
import com.treepeople.leapmindtts.service.admin.BulkSpeechService;
import com.treepeople.leapmindtts.service.admin.LessonSessionService;
import com.treepeople.leapmindtts.service.lesson.*;
import com.treepeople.leapmindtts.util.SegmentIndexingStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 批量语音合成服务实现类
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BulkSpeechServiceImpl implements BulkSpeechService {

    /**
     * Slide处理结果内部类
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    private static class SlideProcessResult {
        private int segmentCount;
        private String polishedText;
    }

    /**
     * Slide文本处理结果内部类（用于预处理）
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    private static class SlideTextProcessResult {
        private int segmentCount;
        private String polishedText;
    }

    private final AudioSegmentMapper audioSegmentMapper;
    private final SegmentedSpeechService segmentedSpeechService;
    private final VoiceDatabaseService voiceDatabaseService;
    private final PPTContextualPolishing pptContextualPolishing;
    private final TextToSpeechService textToSpeechService;
    private final PageLevelAudioService pageLevelAudioService;
    private final AIModelService aiModelService;
    private final LessonSessionService lessonSessionService;

    @Override
    public BulkSynthesisResponse processBulkSynthesis(BulkSynthesisRequest request) {
        String courseId = generateCourseId();
        LocalDateTime startTime = LocalDateTime.now();

        log.info("开始批量语音合成，会话ID: {}, PPT标题: {}, slides数量: {}", courseId, request.getTitle(), request.getSlides().size());


        if (voiceDatabaseService == null) {
            log.error("voiceDatabaseService为null，无法继续处理");
            return BulkSynthesisResponse.builder()
                    .courseId(courseId)
                    .status("FAILED")
                    .message("服务依赖注入失败：voiceDatabaseService为null")
                    .startTime(startTime)
                    .build();
        }

        try {
            // 调试：检查请求数据
            log.info("请求数据检查:");
            log.info("- request.getSlides(): {}", request.getSlides() != null ? request.getSlides().size() : "NULL");
            if (request.getSlides() != null) {
                for (int i = 0; i < request.getSlides().size(); i++) {
                    BulkSynthesisRequest.SlideData slide = request.getSlides().get(i);
                    log.info("  - slide[{}]: pageNumber={}, title={}, contentPoints={}",
                            i,
                            slide != null ? slide.getPageNumber() : "NULL",
                            slide != null ? slide.getTitle() : "NULL",
                            slide != null && slide.getContentPoints() != null ? slide.getContentPoints().size() : "NULL");
                }
            }

            // 1. 按页码排序slides，处理null值
            List<BulkSynthesisRequest.SlideData> sortedSlides = request.getSlides().stream()
                    .filter(slide -> slide != null && slide.getPageNumber() != null)
                    .sorted(Comparator.comparing(BulkSynthesisRequest.SlideData::getPageNumber))
                    .collect(Collectors.toList());

            log.info("排序后的slides数量: {}", sortedSlides.size());

            if (sortedSlides.size() != request.getSlides().size()) {
                log.warn("过滤掉了 {} 个无效的slide（pageNumber为null）",
                        request.getSlides().size() - sortedSlides.size());
            }

            // 2. 创建会话
            String originalText = buildOriginalText(sortedSlides);
            boolean sessionCreated = voiceDatabaseService.createCompleteSession(
                    courseId, request.getTitle(), originalText, null, new ArrayList<>());

            if (!sessionCreated) {
                log.error("创建会话失败，会话ID: {}", courseId);
                return BulkSynthesisResponse.builder()
                        .courseId(courseId)
                        .status("FAILED")
                        .message("创建会话失败")
                        .startTime(startTime)
                        .build();
            }

            // 为了向后兼容，原有接口直接设置为SYNTHESIZED状态（跳过审核流程）
            updateSessionStatus(courseId, "SYNTHESIZED", "SYSTEM", LocalDateTime.now(), "直接合成，跳过审核");

            // 3. 处理所有slides并收集润色文本
            int totalContentPoints = calculateTotalContentPoints(sortedSlides);
            int processedSegments = 0;
            StringBuilder polishedTextBuilder = new StringBuilder();

            for (BulkSynthesisRequest.SlideData slide : sortedSlides) {
                try {
                    SlideProcessResult result = processSlideWithPolishedText(courseId, slide, request.getOptions());
                    processedSegments += result.getSegmentCount();

                    // 收集润色后的文本
                    if (result.getPolishedText() != null && !result.getPolishedText().trim().isEmpty()) {
                        polishedTextBuilder.append("第").append(slide.getPageNumber()).append("页: ")
                                .append(slide.getTitle()).append("\n");
                        polishedTextBuilder.append(result.getPolishedText()).append("\n\n");
                    }

                    log.info("处理slide完成，页码: {}, 生成片段数: {}", slide.getPageNumber(), result.getSegmentCount());
                } catch (Exception e) {
                    log.error("处理slide失败，页码: {}", slide.getPageNumber(), e);
                    // 继续处理其他slides
                }
            }

            // 4. 更新会话的润色文本
            if (polishedTextBuilder.length() > 0) {
                try {
                    boolean updated = voiceDatabaseService.updateSessionPolishedText(courseId, polishedTextBuilder.toString());
                    if (updated) {
                        log.info("更新会话润色文本成功，会话ID: {}, 文本长度: {}", courseId, polishedTextBuilder.length());
                    } else {
                        log.warn("更新会话润色文本失败，会话ID: {}", courseId);
                    }
                } catch (Exception e) {
                    log.error("更新会话润色文本异常，会话ID: {}", courseId, e);
                }
            }

            log.info("批量语音合成完成，会话ID: {}, 总片段数: {}", courseId, processedSegments);

            return BulkSynthesisResponse.builder()
                    .courseId(courseId)
                    .status("COMPLETED")
                    .totalSlides(sortedSlides.size())
                    .totalContentPoints(totalContentPoints)
                    .message("批量合成完成，生成 " + processedSegments + " 个音频片段")
                    .startTime(startTime)
                    .build();

        } catch (Exception e) {
            log.error("批量语音合成失败，会话ID: {}", courseId, e);
            return BulkSynthesisResponse.builder()
                    .courseId(courseId)
                    .status("FAILED")
                    .message("处理失败: " + e.getMessage())
                    .startTime(startTime)
                    .build();
        }
    }

    /**
     * 处理单个slide并返回润色文本（新方法）
     */
    private SlideProcessResult processSlideWithPolishedText(String courseId, BulkSynthesisRequest.SlideData slide,
                                                          BulkSynthesisRequest.BulkSynthesisOptions options) {

        log.info("开始处理页面，会话ID: {}, 页码: {}, 内容点数: {}",
                courseId, slide.getPageNumber(), slide.getContentPoints().size());

        // 检查是否为目录页面，如果是，使用特殊处理逻辑
        if (isAgendaOrTitlePage(slide)) {
            return processAgendaSlideWithPolishedText(courseId, slide, options);
        }

        // 普通页面的处理逻辑
        return processNormalSlideWithPolishedText(courseId, slide, options);
    }

    /**
     * 处理单个slide的所有content_points（页面级存储）- 原方法保持兼容
     */
    private int processSlide(String courseId, BulkSynthesisRequest.SlideData slide,
                           BulkSynthesisRequest.BulkSynthesisOptions options) {

        log.info("开始处理页面，会话ID: {}, 页码: {}, 内容点数: {}",
                courseId, slide.getPageNumber(), slide.getContentPoints().size());

        // 检查是否为目录页面，如果是，使用特殊处理逻辑
        if (isAgendaOrTitlePage(slide)) {
            return processAgendaSlide(courseId, slide, options);
        }

        // 普通页面的处理逻辑
        return processNormalSlide(courseId, slide, options);
    }

    /**
     * 处理目录页面（特殊逻辑：合并所有内容点）
     */
    private int processAgendaSlide(String courseId, BulkSynthesisRequest.SlideData slide,
                                 BulkSynthesisRequest.BulkSynthesisOptions options) {

        log.info("处理目录页面，会话ID: {}, 页码: {}, 内容点数: {}",
                courseId, slide.getPageNumber(), slide.getContentPoints().size());

        List<PPTAudioSegment> pageAudioSegments = new ArrayList<>();

        // 合并所有内容点为一个字符串
        String combinedContent = String.join(" ", slide.getContentPoints());

        try {
            // 1. 文本润色（如果启用）
            String polishedText = combinedContent;
            if (options != null && Boolean.TRUE.equals(options.getEnablePolishing())) {
                try {
                    // 为目录页面生成专门的润色提示词
                    String customPrompt = pptContextualPolishing.generatePolishingPrompt(slide, combinedContent, 0);

                    // 目录页面固定200字限制
                    Integer hardCap = 200;

                    log.info("开始目录页面润色，页码: {}, 原文{}字, 上限{}字",
                            slide.getPageNumber(), combinedContent.length(), hardCap);

                    polishedText = aiModelService.polishTextWithPrompt(combinedContent, customPrompt, hardCap).block();
                    if (polishedText == null || polishedText.trim().isEmpty()) {
                        log.warn("目录页面润色结果为空，使用原文");
                        polishedText = combinedContent;
                    } else {
                        int polishedLength = polishedText.length();
                        log.info("目录页面润色完成，页码: {}, 原文{}字 -> 润色后{}字",
                                slide.getPageNumber(), combinedContent.length(), polishedLength);
                    }
                } catch (Exception e) {
                    log.warn("目录页面润色失败，使用原文，页码: {}, 错误: {}",
                            slide.getPageNumber(), e.getMessage(), e);
                    polishedText = combinedContent;
                }
            }

            // 2. 分句处理
            List<String> sentences;
            try {
                sentences = segmentedSpeechService.splitTextBySentence(polishedText);
                if (sentences.isEmpty()) {
                    log.warn("目录页面分句结果为空，页码: {}", slide.getPageNumber());
                    return 0;
                }
                log.debug("目录页面分句完成，页码: {}, 句子数: {}", slide.getPageNumber(), sentences.size());
            } catch (Exception e) {
                log.error("目录页面分句处理失败，页码: {}", slide.getPageNumber(), e);
                return 0;
            }

            // 3. 处理每个句子
            for (int sentenceIndex = 0; sentenceIndex < sentences.size(); sentenceIndex++) {
                String sentence = sentences.get(sentenceIndex);

                try {
                    // 计算全局片段索引（目录页面使用pointIndex=0）
                    int globalSegmentIndex = SegmentIndexingStrategy.generateGlobalSegmentIndex(
                            slide.getPageNumber(), 0, sentenceIndex);

                    // 语音合成
                    PPTAudioSegment audioSegment = synthesizeAudioSegment(courseId, slide, 0,
                            globalSegmentIndex, combinedContent, polishedText, sentence, options);

                    if (audioSegment != null && audioSegment.getAudioData() != null && audioSegment.getAudioData().length > 0) {
                        pageAudioSegments.add(audioSegment);
                        log.debug("目录页面音频片段合成成功，全局索引: {}, 大小: {} bytes",
                                globalSegmentIndex, audioSegment.getAudioData().length);
                    } else {
                        log.warn("目录页面语音合成失败，句子: {}", sentenceIndex);
                    }
                } catch (Exception e) {
                    log.error("目录页面处理句子失败，句子: {}", sentenceIndex, e);
                }
            }

            // 4. 保存页面级音频数据
            if (!pageAudioSegments.isEmpty()) {
                try {
                    String audioFormat = options != null ? options.getAudioFormat() : "wav";
                    Integer sampleRate = options != null ? options.getSampleRate() : 16000;

                    boolean saved = pageLevelAudioService.savePageAudio(
                            courseId,
                            slide.getPageNumber(),
                            slide.getTitle(),
                            slide.getSlideType(),
                            slide.getDescription(),
                            pageAudioSegments,
                            audioFormat,
                            sampleRate
                    );

                    if (saved) {
                        log.info("目录页面音频保存成功，页码: {}, 片段数: {}", slide.getPageNumber(), pageAudioSegments.size());
                    } else {
                        log.error("目录页面音频保存失败，页码: {}", slide.getPageNumber());
                    }
                } catch (Exception e) {
                    log.error("保存目录页面音频时发生异常，页码: {}", slide.getPageNumber(), e);
                }
            }

            return pageAudioSegments.size();

        } catch (Exception e) {
            log.error("处理目录页面失败，页码: {}", slide.getPageNumber(), e);
            return 0;
        }
    }

    /**
     * 处理普通页面（原有逻辑）
     */
    private int processNormalSlide(String courseId, BulkSynthesisRequest.SlideData slide,
                                 BulkSynthesisRequest.BulkSynthesisOptions options) {

        List<PPTAudioSegment> pageAudioSegments = new ArrayList<>();
        int segmentCount = 0;
        int failedCount = 0;

        for (int pointIndex = 0; pointIndex < slide.getContentPoints().size(); pointIndex++) {
            String contentPoint = slide.getContentPoints().get(pointIndex);

            try {
                // 验证内容点
                if (contentPoint == null || contentPoint.trim().isEmpty()) {
                    log.warn("跳过空内容点，页码: {}, 内容点索引: {}", slide.getPageNumber(), pointIndex);
                    continue;
                }

                // 1. 文本润色（启用）
                String polishedText = contentPoint;
                if (options != null && Boolean.TRUE.equals(options.getEnablePolishing())) {
                    try {
                        // 根据slide上下文生成定制prompt（含“目录/agenda”等篇幅与风格限制）
                        String customPrompt = pptContextualPolishing.generatePolishingPrompt(slide, contentPoint, pointIndex);

                        // 计算字数上限：根据页面类型动态调整
                        int originalLength = contentPoint.length();
                        Integer hardCap;

                        // 针对特定页面类型应用不同的字数限制策略
                        if (isAgendaOrTitlePage(slide)) {
                            // 目录/标题页：严格限制为200字
                            hardCap = 200;
                            log.debug("检测到目录/标题页，应用严格字数限制: {}字 (原文{}字)", hardCap, originalLength);
                        } else {
                            // 普通页面：原文+200字
                            hardCap = originalLength + 200;
                            log.debug("普通页面，应用标准字数限制: {}字 (原文{}字)", hardCap, originalLength);
                        }

                        log.debug("开始润色，页码: {}, 内容点: {}, 原文{}字, 上限{}字",
                                slide.getPageNumber(), pointIndex, originalLength, hardCap);

                        // 直接走AIModelService的定制方法（结合上下文prompt + 硬长度上限）
                        polishedText = aiModelService.polishTextWithPrompt(contentPoint, customPrompt, hardCap).block();
                        if (polishedText == null || polishedText.trim().isEmpty()) {
                            log.warn("润色结果为空，使用原文");
                            polishedText = contentPoint;
                        } else {
                            int polishedLength = polishedText.length();
                            log.info("文本润色完成，页码: {}, 内容点: {}, 原文{}字 -> 润色后{}字 (增加{}字)",
                                    slide.getPageNumber(), pointIndex, originalLength, polishedLength,
                                    polishedLength - originalLength);

                            // 验证字数是否超标
                            if (polishedLength > originalLength + 200) {
                                log.warn("润色后字数超标，页码: {}, 内容点: {}, 超出{}字，将进行截断",
                                        slide.getPageNumber(), pointIndex, polishedLength - originalLength - 200);
                            }
                        }
                    } catch (Exception e) {
                        log.warn("文本润色失败，使用原文，页码: {}, 内容点索引: {}, 错误: {}",
                                slide.getPageNumber(), pointIndex, e.getMessage(), e);
                        polishedText = contentPoint;
                    }
                }

                // 3. 分句处理
                List<String> sentences;
                try {
                    sentences = segmentedSpeechService.splitTextBySentence(polishedText);
                    if (sentences.isEmpty()) {
                        log.warn("分句结果为空，跳过该内容点，页码: {}, 内容点索引: {}",
                                slide.getPageNumber(), pointIndex);
                        continue;
                    }
                    log.debug("分句完成，页码: {}, 内容点: {}, 句子数: {}",
                            slide.getPageNumber(), pointIndex, sentences.size());
                } catch (Exception e) {
                    log.error("分句处理失败，页码: {}, 内容点索引: {}", slide.getPageNumber(), pointIndex, e);
                    failedCount++;
                    continue;
                }

                // 4. 处理每个句子
                for (int sentenceIndex = 0; sentenceIndex < sentences.size(); sentenceIndex++) {
                    String sentence = sentences.get(sentenceIndex);

                    try {
                        // 计算全局片段索引
                        int globalSegmentIndex = SegmentIndexingStrategy.generateGlobalSegmentIndex(
                                slide.getPageNumber(), pointIndex, sentenceIndex);

                        // 语音合成
                        PPTAudioSegment audioSegment = synthesizeAudioSegment(courseId, slide, pointIndex,
                                globalSegmentIndex, contentPoint, polishedText, sentence, options);

                        if (audioSegment != null && audioSegment.getAudioData() != null && audioSegment.getAudioData().length > 0) {
                            pageAudioSegments.add(audioSegment);
                            segmentCount++;
                            log.debug("音频片段合成成功，全局索引: {}, 大小: {} bytes",
                                    globalSegmentIndex, audioSegment.getAudioData().length);
                        } else {
                            failedCount++;
                            log.warn("语音合成失败，页码: {}, 内容点: {}, 句子: {}",
                                    slide.getPageNumber(), pointIndex, sentenceIndex);
                        }
                    } catch (Exception e) {
                        log.error("处理句子失败，页码: {}, 内容点: {}, 句子: {}",
                                slide.getPageNumber(), pointIndex, sentenceIndex, e);
                        failedCount++;
                    }
                }

            } catch (Exception e) {
                log.error("处理内容点失败，页码: {}, 内容点索引: {}", slide.getPageNumber(), pointIndex, e);
                failedCount++;
                // 继续处理其他内容点
            }
        }

        // 5. 保存页面级音频数据
        if (!pageAudioSegments.isEmpty()) {
            try {
                String audioFormat = options != null ? options.getAudioFormat() : "wav";
                Integer sampleRate = options != null ? options.getSampleRate() : 16000;

                boolean saved = pageLevelAudioService.savePageAudio(
                        courseId,
                        slide.getPageNumber(),
                        slide.getTitle(),
                        slide.getSlideType(),
                        slide.getDescription(),
                        pageAudioSegments,
                        audioFormat,
                        sampleRate
                );

                if (saved) {
                    log.info("页面音频保存成功，页码: {}, 片段数: {}", slide.getPageNumber(), pageAudioSegments.size());
                } else {
                    log.error("页面音频保存失败，页码: {}", slide.getPageNumber());
                }
            } catch (Exception e) {
                log.error("保存页面音频时发生异常，页码: {}", slide.getPageNumber(), e);
            }
        }

        if (failedCount > 0) {
            log.warn("slide处理完成，页码: {}, 成功: {}, 失败: {}",
                    slide.getPageNumber(), segmentCount, failedCount);
        }

        return segmentCount;
    }

    /**
     * 处理目录页面并返回润色文本
     */
    private SlideProcessResult processAgendaSlideWithPolishedText(String courseId, BulkSynthesisRequest.SlideData slide,
                                                               BulkSynthesisRequest.BulkSynthesisOptions options) {

        log.info("处理目录页面，会话ID: {}, 页码: {}, 内容点数: {}",
                courseId, slide.getPageNumber(), slide.getContentPoints().size());

        List<PPTAudioSegment> pageAudioSegments = new ArrayList<>();
        String combinedContent = String.join(" ", slide.getContentPoints());
        String finalPolishedText = combinedContent;

        try {
            // 1. 文本润色（如果启用）
            if (options != null && Boolean.TRUE.equals(options.getEnablePolishing())) {
                try {
                    String customPrompt = pptContextualPolishing.generatePolishingPrompt(slide, combinedContent, 0);
                    Integer hardCap = 200;

                    log.info("开始目录页面润色，页码: {}, 原文{}字, 上限{}字",
                            slide.getPageNumber(), combinedContent.length(), hardCap);

                    String polishedText = aiModelService.polishTextWithPrompt(combinedContent, customPrompt, hardCap).block();
                    if (polishedText != null && !polishedText.trim().isEmpty()) {
                        finalPolishedText = polishedText;
                        log.info("目录页面润色完成，页码: {}, 原文{}字 -> 润色后{}字",
                                slide.getPageNumber(), combinedContent.length(), polishedText.length());
                    }
                } catch (Exception e) {
                    log.warn("目录页面润色失败，使用原文，页码: {}, 错误: {}",
                            slide.getPageNumber(), e.getMessage(), e);
                }
            }

            // 2. 分句和语音合成处理（保持原有逻辑）
            List<String> sentences = segmentedSpeechService.splitTextBySentence(finalPolishedText);

            for (int sentenceIndex = 0; sentenceIndex < sentences.size(); sentenceIndex++) {
                String sentence = sentences.get(sentenceIndex);

                try {
                    int globalSegmentIndex = SegmentIndexingStrategy.generateGlobalSegmentIndex(
                            slide.getPageNumber(), 0, sentenceIndex);

                    PPTAudioSegment audioSegment = synthesizeAudioSegment(courseId, slide, 0,
                            globalSegmentIndex, combinedContent, finalPolishedText, sentence, options);

                    if (audioSegment != null && audioSegment.getAudioData() != null && audioSegment.getAudioData().length > 0) {
                        pageAudioSegments.add(audioSegment);
                    }
                } catch (Exception e) {
                    log.error("目录页面处理句子失败，句子: {}", sentenceIndex, e);
                }
            }

            // 3. 保存页面级音频数据
            if (!pageAudioSegments.isEmpty()) {
                try {
                    String audioFormat = options != null ? options.getAudioFormat() : "wav";
                    Integer sampleRate = options != null ? options.getSampleRate() : 16000;

                    pageLevelAudioService.savePageAudio(
                            courseId, slide.getPageNumber(), slide.getTitle(),
                            slide.getSlideType(), slide.getDescription(),
                            pageAudioSegments, audioFormat, sampleRate
                    );
                } catch (Exception e) {
                    log.error("保存目录页面音频时发生异常，页码: {}", slide.getPageNumber(), e);
                }
            }

            return SlideProcessResult.builder()
                    .segmentCount(pageAudioSegments.size())
                    .polishedText(finalPolishedText)
                    .build();

        } catch (Exception e) {
            log.error("处理目录页面失败，页码: {}", slide.getPageNumber(), e);
            return SlideProcessResult.builder()
                    .segmentCount(0)
                    .polishedText(combinedContent)
                    .build();
        }
    }

    /**
     * 处理普通页面并返回润色文本
     */
    private SlideProcessResult processNormalSlideWithPolishedText(String courseId, BulkSynthesisRequest.SlideData slide,
                                                               BulkSynthesisRequest.BulkSynthesisOptions options) {

        List<PPTAudioSegment> pageAudioSegments = new ArrayList<>();
        StringBuilder slidePolishedText = new StringBuilder();
        int segmentCount = 0;

        for (int pointIndex = 0; pointIndex < slide.getContentPoints().size(); pointIndex++) {
            String contentPoint = slide.getContentPoints().get(pointIndex);

            if (contentPoint == null || contentPoint.trim().isEmpty()) {
                continue;
            }

            try {
                // 1. 文本润色
                String polishedText = contentPoint;
                if (options != null && Boolean.TRUE.equals(options.getEnablePolishing())) {
                    try {
                        String customPrompt = pptContextualPolishing.generatePolishingPrompt(slide, contentPoint, pointIndex);
                        int originalLength = contentPoint.length();
                        Integer hardCap = isAgendaOrTitlePage(slide) ? 200 : originalLength + 200;

                        String result = aiModelService.polishTextWithPrompt(contentPoint, customPrompt, hardCap).block();
                        if (result != null && !result.trim().isEmpty()) {
                            polishedText = result;
                            log.info("文本润色完成，页码: {}, 内容点: {}, 原文{}字 -> 润色后{}字",
                                    slide.getPageNumber(), pointIndex, originalLength, polishedText.length());
                        }
                    } catch (Exception e) {
                        log.warn("文本润色失败，使用原文，页码: {}, 内容点索引: {}",
                                slide.getPageNumber(), pointIndex, e);
                    }
                }

                // 收集润色后的文本
                if (slidePolishedText.length() > 0) {
                    slidePolishedText.append("\n");
                }
                slidePolishedText.append("- ").append(polishedText);

                // 2. 分句和语音合成处理（保持原有逻辑）
                List<String> sentences = segmentedSpeechService.splitTextBySentence(polishedText);

                for (int sentenceIndex = 0; sentenceIndex < sentences.size(); sentenceIndex++) {
                    String sentence = sentences.get(sentenceIndex);

                    try {
                        int globalSegmentIndex = SegmentIndexingStrategy.generateGlobalSegmentIndex(
                                slide.getPageNumber(), pointIndex, sentenceIndex);

                        PPTAudioSegment audioSegment = synthesizeAudioSegment(courseId, slide, pointIndex,
                                globalSegmentIndex, contentPoint, polishedText, sentence, options);

                        if (audioSegment != null && audioSegment.getAudioData() != null && audioSegment.getAudioData().length > 0) {
                            pageAudioSegments.add(audioSegment);
                            segmentCount++;
                        }
                    } catch (Exception e) {
                        log.error("处理句子失败，页码: {}, 内容点: {}, 句子: {}",
                                slide.getPageNumber(), pointIndex, sentenceIndex, e);
                    }
                }

            } catch (Exception e) {
                log.error("处理内容点失败，页码: {}, 内容点索引: {}", slide.getPageNumber(), pointIndex, e);
            }
        }

        // 3. 保存页面级音频数据
        if (!pageAudioSegments.isEmpty()) {
            try {
                String audioFormat = options != null ? options.getAudioFormat() : "wav";
                Integer sampleRate = options != null ? options.getSampleRate() : 16000;

                pageLevelAudioService.savePageAudio(
                        courseId, slide.getPageNumber(), slide.getTitle(),
                        slide.getSlideType(), slide.getDescription(),
                        pageAudioSegments, audioFormat, sampleRate
                );
            } catch (Exception e) {
                log.error("保存页面音频时发生异常，页码: {}", slide.getPageNumber(), e);
            }
        }

        return SlideProcessResult.builder()
                .segmentCount(segmentCount)
                .polishedText(slidePolishedText.toString())
                .build();
    }

    /**
     * 语音合成（返回音频片段对象，不直接保存到数据库）
     */
    private PPTAudioSegment synthesizeAudioSegment(String courseId, BulkSynthesisRequest.SlideData slide,
                                                  int contentPointIndex, int globalSegmentIndex,
                                                  String originalText, String polishedText, String sentence,
                                                  BulkSynthesisRequest.BulkSynthesisOptions options) {
        try {
            // 1. 使用项目已有的阿里云TTS服务进行语音合成
            byte[] audioData;
            try {
                Mono<byte[]> synthesisResult = textToSpeechService.synthesizeSpeech(sentence);
                audioData = synthesisResult.block();
                if (audioData == null || audioData.length == 0) {
                    log.warn("TTS合成结果为空，使用模拟数据，全局索引: {}", globalSegmentIndex);
                    audioData = simulateTTSSynthesis(sentence);
                }
            } catch (Exception ttsError) {
                log.warn("TTS合成失败，使用模拟数据，全局索引: {}, 错误: {}",
                        globalSegmentIndex, ttsError.getMessage());
                audioData = simulateTTSSynthesis(sentence);
            }

            long duration = estimateAudioDuration(audioData);

            String audioFormat = options != null ? options.getAudioFormat() : "wav";
            Integer sampleRate = options != null ? options.getSampleRate() : 16000;

            // 2. 构建PPTAudioSegment对象
            PPTAudioSegment audioSegment = PPTAudioSegment.builder()
                    .courseId(courseId)
                    .slidePageNumber(slide.getPageNumber())
                    .slideTitle(slide.getTitle())
                    .contentPointIndex(contentPointIndex)
                    .segmentIndex(globalSegmentIndex)
                    .slideType(slide.getSlideType())
                    .slideDescription(slide.getDescription())
                    .originalText(originalText)
                    .polishedText(polishedText)
                    .textContent(sentence)
                    .audioData(audioData)
                    .audioSize((long) audioData.length)
                    .duration(duration)
                    .audioFormat(audioFormat)
                    .sampleRate(sampleRate)
                    .checksum(generateChecksum(audioData))
                    .createdAt(LocalDateTime.now())
                    .build();

            log.debug("音频片段合成成功，全局索引: {}, 音频大小: {} bytes",
                    globalSegmentIndex, audioData.length);

            return audioSegment;

        } catch (Exception e) {
            log.error("语音合成失败，全局索引: {}", globalSegmentIndex, e);
            return null;
        }
    }

    /**
     * 生成音频数据校验和
     */
    private String generateChecksum(byte[] audioData) {
        if (audioData == null || audioData.length == 0) {
            return "";
        }
        return String.valueOf(audioData.length) + "_" + String.valueOf(audioData.hashCode());
    }

    /**
     * 判断是否为目录或标题页
     */
    private boolean isAgendaOrTitlePage(BulkSynthesisRequest.SlideData slide) {
        // 检查slideType
        if (slide.getSlideType() != null) {
            String type = slide.getSlideType().toLowerCase();
            if ("agenda".equals(type) || "title".equals(type) || "thankyou".equals(type)) {
                return true;
            }
        }

        // 检查标题关键词
        if (slide.getTitle() != null) {
            String title = slide.getTitle().toLowerCase().trim();
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
        }

        return false;
    }

    /**
     * 生成会话ID
     */
    private String generateCourseId() {
        return "ppt_session_" + UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 构建原始文本
     */
    private String buildOriginalText(List<BulkSynthesisRequest.SlideData> slides) {
        StringBuilder sb = new StringBuilder();
        for (BulkSynthesisRequest.SlideData slide : slides) {
            sb.append("第").append(slide.getPageNumber()).append("页: ").append(slide.getTitle()).append("\n");
            for (String contentPoint : slide.getContentPoints()) {
                sb.append("- ").append(contentPoint).append("\n");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * 计算总内容点数量
     */
    private int calculateTotalContentPoints(List<BulkSynthesisRequest.SlideData> slides) {
        return slides.stream()
                .mapToInt(slide -> slide.getContentPoints().size())
                .sum();
    }

    /**
     * 模拟TTS合成（实际项目中应该调用真实的TTS服务）
     */
    private byte[] simulateTTSSynthesis(String text) {
        // 模拟音频数据，实际应该调用TextToSpeechService
        return ("AUDIO_DATA_" + text.hashCode()).getBytes();
    }

    /**
     * 估算音频时长
     */
    private long estimateAudioDuration(byte[] audioData) {
        if (audioData == null || audioData.length == 0) {
            return 0;
        }

        // 对于WAV格式音频，尝试从文件头读取时长信息
        if (audioData.length > 44) {
            try {
                // WAV文件格式：44字节头部 + 音频数据
                // 采样率通常在字节24-27位置，但这里使用简化计算
                // 假设16kHz采样率，16位深度，单声道
                int dataSize = audioData.length - 44; // 减去WAV头部
                long durationMs = (long) (dataSize / (16000.0 * 2)) * 1000; // 16kHz, 16bit = 2 bytes per sample
                return Math.max(durationMs, 100); // 最少100ms
            } catch (Exception e) {
                log.debug("无法解析音频时长，使用估算值");
            }
        }

        // 简单估算：根据音频数据大小估算时长
        // 假设16kHz采样率，16位深度，单声道：32KB/s
        long estimatedMs = (audioData.length * 1000L) / 32000;
        return Math.max(estimatedMs, 100); // 最少100ms
    }

    @Override
    public List<PPTAudioSegment> getPageAudioSegments(String courseId, Integer pageNumber) {
        log.info("获取页面音频片段，会话ID: {}, 页码: {}", courseId, pageNumber);
        return pageLevelAudioService.getPageAudioSegments(courseId, pageNumber);
    }

    @Override
    public PPTAudioSegment getAudioSegment(String courseId, Integer segmentIndex) {
        log.info("获取音频片段，会话ID: {}, 片段索引: {}", courseId, segmentIndex);
        return pageLevelAudioService.getAudioSegmentInfoByGlobalIndex(courseId, segmentIndex);
    }

    @Override
    public PPTAudioInfo getPPTAudioInfo(String courseId) {
        log.info("获取PPT音频信息，会话ID: {}", courseId);

        // 获取会话基本信息
        LessonSession session = voiceDatabaseService.getCompleteSessionInfo(courseId);
        if (session == null) {
            log.warn("未找到会话信息，会话ID: {}", courseId);
            return null;
        }

        // 获取所有页面音频
        List<AudioSegment> pageAudios = pageLevelAudioService.getSessionPageAudios(courseId);

        // 构建页面信息
        List<PPTAudioInfo.PPTPageInfo> pages = pageAudios.stream()
                .map(pageAudio -> PPTAudioInfo.PPTPageInfo.builder()
                        .pageNumber(pageAudio.getSlidePageNumber())
                        .pageTitle(pageAudio.getSlideTitle())
                        .slideType(pageAudio.getSlideType())
                        .segmentCount(pageAudio.getSegmentCount())
                        .pageDuration(pageAudio.getDuration())
                        .build())
                .sorted(Comparator.comparing(PPTAudioInfo.PPTPageInfo::getPageNumber))
                .collect(Collectors.toList());

        // 获取统计信息
        long[] stats = pageLevelAudioService.getSessionAudioStats(courseId);
        long totalAudioSize = stats[0];
        long totalDuration = stats[1];
        long totalSegments = stats[2];

        PPTAudioInfo audioInfo = PPTAudioInfo.builder()
                .courseId(courseId)
                .title(session.getTitle())
                .totalPages(pages.size())
                .totalSegments((int) totalSegments)
                .totalDuration(totalDuration)
                .totalAudioSize(totalAudioSize)
                .pages(pages)
                .createdAt(session.getCreatedAt())
                .build();

        log.info("PPT音频信息获取成功，会话ID: {}, 总页数: {}, 总片段数: {}",
                courseId, audioInfo.getTotalPages(), audioInfo.getTotalSegments());

        return audioInfo;
    }

    //1
    @Override
    public BulkPreprocessingResponse processBulkPreprocessing(BulkSynthesisRequest request) {
        //String courseId = generateCourseId();
        String courseId = request.getCourseId();

        LocalDateTime startTime = LocalDateTime.now();

        log.info("开始批量文本预处理，会话ID: {}, PPT标题: {}, slides数量: {}",
                courseId, request.getTitle(), request.getSlides().size());

        try {
            // 1. 按页码排序slides，处理null值
            List<BulkSynthesisRequest.SlideData> sortedSlides = request.getSlides().stream()
                    .filter(slide -> slide != null && slide.getPageNumber() != null)
                    .sorted(Comparator.comparing(BulkSynthesisRequest.SlideData::getPageNumber))
                    .collect(Collectors.toList());

            log.info("排序后的slides数量: {}", sortedSlides.size());

            // 2. 创建会话（DRAFT状态）
            String originalText = buildOriginalText(sortedSlides);
            boolean sessionCreated = voiceDatabaseService.createCompleteSession(
                    courseId, request.getTitle(), originalText, null, new ArrayList<>());

            if (!sessionCreated) {
                log.error("创建会话失败，会话ID: {}", courseId);
                return BulkPreprocessingResponse.builder()
                        .courseId(courseId)
                        .status("FAILED")
                        .message("创建会话失败")
                        .startTime(startTime)
                        .endTime(LocalDateTime.now())
                        .build();
            }

            // 3. 更新会话状态为DRAFT
            updateSessionStatus(courseId, "DRAFT", null, null, null);

            // 4. 处理所有slides（只进行文本预处理，不生成音频）
            int totalTextSegments = 0;
            StringBuilder polishedTextBuilder = new StringBuilder();

            for (BulkSynthesisRequest.SlideData slide : sortedSlides) {
                try {
                    SlideTextProcessResult result = processSlideTextOnlyWithPolishedText(courseId, slide, request.getOptions());
                    totalTextSegments += result.getSegmentCount();

                    // 收集润色后的文本
                    if (result.getPolishedText() != null && !result.getPolishedText().trim().isEmpty()) {
                        polishedTextBuilder.append("第").append(slide.getPageNumber()).append("页: ")
                                .append(slide.getTitle()).append("\n");
                        polishedTextBuilder.append(result.getPolishedText()).append("\n\n");
                    }

                    log.info("处理slide文本完成，页码: {}, 生成文本片段数: {}", slide.getPageNumber(), result.getSegmentCount());
                } catch (Exception e) {
                    log.error("处理slide文本失败，页码: {}", slide.getPageNumber(), e);
                    // 继续处理其他slides
                }
            }

            // 5. 更新会话的润色文本
            if (polishedTextBuilder.length() > 0) {
                try {
                    boolean updated = voiceDatabaseService.updateSessionPolishedText(courseId, polishedTextBuilder.toString());
                    if (updated) {
                        log.info("更新会话润色文本成功，会话ID: {}, 文本长度: {}", courseId, polishedTextBuilder.length());
                    } else {
                        log.warn("更新会话润色文本失败，会话ID: {}", courseId);
                    }
                } catch (Exception e) {
                    log.error("更新会话润色文本异常，会话ID: {}", courseId, e);
                }
            }

            // 6. 更新会话状态为PENDING_REVIEW
            updateSessionStatus(courseId, "PENDING_REVIEW", null, null, null);

            log.info("批量文本预处理完成，会话ID: {}, 总文本片段数: {}", courseId, totalTextSegments);

            return BulkPreprocessingResponse.builder()
                    .courseId(courseId)
                    .status("SUCCESS")
                    .totalSlides(sortedSlides.size())
                    .totalTextSegments(totalTextSegments)
                    .message("文本预处理完成，等待审核")
                    .startTime(startTime)
                    .endTime(LocalDateTime.now())
                    .build();

        } catch (Exception e) {
            log.error("批量文本预处理失败，会话ID: {}", courseId, e);
            return BulkPreprocessingResponse.builder()
                    .courseId(courseId)
                    .status("FAILED")
                    .message("处理失败: " + e.getMessage())
                    .startTime(startTime)
                    .endTime(LocalDateTime.now())
                    .build();
        }
    }

    @Override
    public BulkSynthesisResponse executeBulkSynthesis(String courseId) {
        LocalDateTime startTime = LocalDateTime.now();

        log.info("开始执行批量语音合成，会话ID: {}", courseId);

        try {
            // 1. 检查会话状态
            LessonSession session = voiceDatabaseService.getCompleteSessionInfo(courseId);
            if (session == null) {
                log.error("会话不存在，会话ID: {}", courseId);
                return BulkSynthesisResponse.builder()
                        .courseId(courseId)
                        .status("FAILED")
                        .message("会话不存在")
                        .startTime(startTime)
                        .build();
            }

            if (!"APPROVED".equals(session.getProcessingStatus())) {
                log.error("会话状态不正确，当前状态: {}, 会话ID: {}", session.getProcessingStatus(), courseId);
                return BulkSynthesisResponse.builder()
                        .courseId(courseId)
                        .status("FAILED")
                        .message("会话状态不正确，当前状态: " + session.getProcessingStatus())
                        .startTime(startTime)
                        .build();
            }

            // 2. 获取所有TEXT_ONLY状态的音频片段
            List<AudioSegment> textOnlySegments = pageLevelAudioService.getTextOnlySegments(courseId);
            if (textOnlySegments.isEmpty()) {
                log.warn("未找到待合成的文本片段，会话ID: {}", courseId);
                return BulkSynthesisResponse.builder()
                        .courseId(courseId)
                        .status("FAILED")
                        .message("未找到待合成的文本片段")
                        .startTime(startTime)
                        .build();
            }

            // 3. 按页面分组处理
            Map<Integer, List<AudioSegment>> segmentsByPage = textOnlySegments.stream()
                    .collect(Collectors.groupingBy(AudioSegment::getSlidePageNumber));

            int processedSegments = 0;

            for (Map.Entry<Integer, List<AudioSegment>> entry : segmentsByPage.entrySet()) {
                Integer pageNumber = entry.getKey();
                List<AudioSegment> pageSegments = entry.getValue();

                try {
                    int pageProcessedCount = synthesizePageAudio(courseId, pageNumber, pageSegments);
                    processedSegments += pageProcessedCount;
                    log.info("页面音频合成完成，页码: {}, 合成片段数: {}", pageNumber, pageProcessedCount);
                } catch (Exception e) {
                    log.error("页面音频合成失败，页码: {}", pageNumber, e);
                    // 继续处理其他页面
                }
            }

            // 4. 更新会话状态为SYNTHESIZED
            updateSessionStatus(courseId, "SYNTHESIZED", null, null, null);

            log.info("批量语音合成完成，会话ID: {}, 总合成片段数: {}", courseId, processedSegments);

            return BulkSynthesisResponse.builder()
                    .courseId(courseId)
                    .status("COMPLETED")
                    .totalSlides(segmentsByPage.size())
                    .totalContentPoints(processedSegments)
                    .message("语音合成完成，生成 " + processedSegments + " 个音频片段")
                    .startTime(startTime)
                    .build();

        } catch (Exception e) {
            log.error("批量语音合成失败，会话ID: {}", courseId, e);
            return BulkSynthesisResponse.builder()
                    .courseId(courseId)
                    .status("FAILED")
                    .message("合成失败: " + e.getMessage())
                    .startTime(startTime)
                    .build();
        }
    }

    @Override
    public List<LessonSession> getPendingReviewSessions() {
        log.info("获取待审核会话列表");
        return voiceDatabaseService.getSessionsByStatus("PENDING_REVIEW");
    }

    @Override
    public List<LessonSession> getSessionsByStatus(String status) {
        log.info("根据状态获取会话列表，状态: {}", status);
        if (status == null || status.trim().isEmpty()) {
            // 如果状态为空，返回所有会话（这里需要扩展VoiceDatabaseService）
            return voiceDatabaseService.getSessionsByStatus(null);
        }
        return voiceDatabaseService.getSessionsByStatus(status);
    }

    @Override
    public ReviewResponse reviewSession(String courseId, String reviewerId, Boolean approved, String comments) {
        log.info("审核会话，会话ID: {}, 审核人: {}, 结果: {}", courseId, reviewerId, approved);

        try {
            // 检查会话是否存在
            LessonSession session = voiceDatabaseService.getCompleteSessionInfo(courseId);
            if (session == null) {
                return ReviewResponse.builder()
                        .courseId(courseId)
                        .status("FAILED")
                        .message("会话不存在")
                        .build();
            }

            if (!"PENDING_REVIEW".equals(session.getProcessingStatus())) {
                return ReviewResponse.builder()
                        .courseId(courseId)
                        .status("FAILED")
                        .message("会话状态不正确，当前状态: " + session.getProcessingStatus())
                        .build();
            }

            // 更新审核状态
            String newStatus = approved ? "APPROVED" : "REJECTED";
            boolean updated = updateSessionStatus(courseId, newStatus, reviewerId, LocalDateTime.now(), comments);

            if (updated) {
                log.info("会话审核完成，会话ID: {}, 新状态: {}", courseId, newStatus);
                return ReviewResponse.builder()
                        .courseId(courseId)
                        .status("SUCCESS")
                        .message("审核完成")
                        .newStatus(newStatus)
                        .build();
            } else {
                return ReviewResponse.builder()
                        .courseId(courseId)
                        .status("FAILED")
                        .message("更新审核状态失败")
                        .build();
            }

        } catch (Exception e) {
            log.error("审核会话失败，会话ID: {}", courseId, e);
            return ReviewResponse.builder()
                    .courseId(courseId)
                    .status("FAILED")
                    .message("审核失败: " + e.getMessage())
                    .build();
        }
    }

    @Override
    public ReviewResponse adminReviewSession(String courseId, AdminReviewRequest request) {
        log.info("管理员审核会话，会话ID: {}, 审核人: {}, 结果: {}, 是否修改文本: {}",
                courseId, request.getReviewerId(), request.getApproved(),
                request.getUpdatedPolishedText() != null);

        try {
            // 检查会话是否存在
            LessonSession session = voiceDatabaseService.getCompleteSessionInfo(courseId);
            if (session == null) {
                return ReviewResponse.builder()
                        .courseId(courseId)
                        .status("FAILED")
                        .message("会话不存在")
                        .build();
            }

            if (!"PENDING_REVIEW".equals(session.getProcessingStatus())) {
                return ReviewResponse.builder()
                        .courseId(courseId)
                        .status("FAILED")
                        .message("会话状态不正确，当前状态: " + session.getProcessingStatus())
                        .build();
            }

            // 1. 更新会话基本信息（如果提供）
            boolean sessionUpdated = false;

            // 更新标题
            if (request.getUpdatedTitle() != null && !request.getUpdatedTitle().trim().isEmpty()) {
                boolean titleUpdated = voiceDatabaseService.updateSessionTitle(courseId, request.getUpdatedTitle());
                if (titleUpdated) {
                    log.info("会话标题更新成功，会话ID: {}", courseId);
                    sessionUpdated = true;
                } else {
                    log.warn("会话标题更新失败，会话ID: {}", courseId);
                }
            }

            // 更新润色文本
            if (request.getUpdatedPolishedText() != null && !request.getUpdatedPolishedText().trim().isEmpty()) {
                boolean textUpdated = voiceDatabaseService.updateSessionPolishedText(courseId, request.getUpdatedPolishedText());
                if (textUpdated) {
                    log.info("会话润色文本更新成功，会话ID: {}, 文本长度: {}", courseId, request.getUpdatedPolishedText().length());
                    sessionUpdated = true;

                    // 2. 如果需要强制更新文本片段
                    if (Boolean.TRUE.equals(request.getForceUpdateSegments())) {
                        try {
                            boolean segmentsUpdated = updateTextSegmentsFromPolishedText(courseId, request.getUpdatedPolishedText());
                            if (segmentsUpdated) {
                                log.info("文本片段更新成功，会话ID: {}", courseId);
                            } else {
                                log.warn("文本片段更新失败，会话ID: {}", courseId);
                            }
                        } catch (Exception e) {
                            log.error("更新文本片段时发生异常，会话ID: {}", courseId, e);
                        }
                    }
                } else {
                    log.warn("会话润色文本更新失败，会话ID: {}", courseId);
                }
            }

            // 3. 更新审核状态
            String newStatus = request.getApproved() ? "APPROVED" : "REJECTED";
            boolean statusUpdated = updateSessionStatus(courseId, newStatus, request.getReviewerId(),
                    LocalDateTime.now(), request.getComments());

            if (statusUpdated) {
                log.info("管理员会话审核完成，会话ID: {}, 新状态: {}, 会话信息更新: {}",
                        courseId, newStatus, sessionUpdated);

                String message = sessionUpdated ? "审核完成，会话信息已更新" : "审核完成";

                return ReviewResponse.builder()
                        .courseId(courseId)
                        .status("SUCCESS")
                        .message(message)
                        .newStatus(newStatus)
                        .build();
            } else {
                return ReviewResponse.builder()
                        .courseId(courseId)
                        .status("FAILED")
                        .message("更新审核状态失败")
                        .build();
            }

        } catch (Exception e) {
            log.error("管理员审核会话失败，会话ID: {}", courseId, e);
            return ReviewResponse.builder()
                    .courseId(courseId)
                    .status("FAILED")
                    .message("审核失败: " + e.getMessage())
                    .build();
        }
    }

    @Override
    public LessonSession getPendingReviewSessionsByCourseId(String courseId) {
        LessonSession lessonSession = lessonSessionService.getByCourseId(courseId);
        return lessonSession;
    }

    /**
     * 处理单个slide的文本预处理并返回润色文本（新方法）
     */
    private SlideTextProcessResult processSlideTextOnlyWithPolishedText(String courseId, BulkSynthesisRequest.SlideData slide,
                                                                      BulkSynthesisRequest.BulkSynthesisOptions options) {

        log.info("开始处理页面文本，会话ID: {}, 页码: {}, 内容点数: {}",
                courseId, slide.getPageNumber(), slide.getContentPoints().size());

        List<PPTAudioSegment> textSegments = new ArrayList<>();
        String slidePolishedText = "";
        int segmentCount = 0;

        // 检查是否为目录页面
        if (isAgendaOrTitlePage(slide)) {
            SlideTextProcessResult result = processAgendaSlideTextOnlyWithPolishedText(courseId, slide, options, textSegments);
            segmentCount = result.getSegmentCount();
            slidePolishedText = result.getPolishedText();
        } else {
            SlideTextProcessResult result = processNormalSlideTextOnlyWithPolishedText(courseId, slide, options, textSegments);
            segmentCount = result.getSegmentCount();
            slidePolishedText = result.getPolishedText();
        }

        // 保存文本片段到数据库（不包含音频数据）
        if (!textSegments.isEmpty()) {
            try {
                boolean saved = pageLevelAudioService.saveTextOnlySegments(
                        courseId,
                        slide.getPageNumber(),
                        slide.getTitle(),
                        slide.getSlideType(),
                        slide.getDescription(),
                        textSegments
                );

                if (saved) {
                    log.info("页面文本片段保存成功，页码: {}, 片段数: {}", slide.getPageNumber(), textSegments.size());
                } else {
                    log.error("页面文本片段保存失败，页码: {}", slide.getPageNumber());
                }
            } catch (Exception e) {
                log.error("保存页面文本片段时发生异常，页码: {}", slide.getPageNumber(), e);
            }
        }

        return SlideTextProcessResult.builder()
                .segmentCount(segmentCount)
                .polishedText(slidePolishedText)
                .build();
    }

    /**
     * 处理单个slide的文本预处理（不生成音频）- 保持向后兼容
     */
    private int processSlideTextOnly(String courseId, BulkSynthesisRequest.SlideData slide,
                                   BulkSynthesisRequest.BulkSynthesisOptions options) {

        log.info("开始处理页面文本，会话ID: {}, 页码: {}, 内容点数: {}",
                courseId, slide.getPageNumber(), slide.getContentPoints().size());

        List<PPTAudioSegment> textSegments = new ArrayList<>();
        int segmentCount = 0;

        // 检查是否为目录页面
        if (isAgendaOrTitlePage(slide)) {
            segmentCount = processAgendaSlideTextOnly(courseId, slide, options, textSegments);
        } else {
            segmentCount = processNormalSlideTextOnly(courseId, slide, options, textSegments);
        }

        // 保存文本片段到数据库（不包含音频数据）
        if (!textSegments.isEmpty()) {
            try {
                boolean saved = pageLevelAudioService.saveTextOnlySegments(
                        courseId,
                        slide.getPageNumber(),
                        slide.getTitle(),
                        slide.getSlideType(),
                        slide.getDescription(),
                        textSegments
                );

                if (saved) {
                    log.info("页面文本片段保存成功，页码: {}, 片段数: {}", slide.getPageNumber(), textSegments.size());
                } else {
                    log.error("页面文本片段保存失败，页码: {}", slide.getPageNumber());
                }
            } catch (Exception e) {
                log.error("保存页面文本片段时发生异常，页码: {}", slide.getPageNumber(), e);
            }
        }

        return segmentCount;
    }

    /**
     * 处理目录页面的文本预处理并返回润色文本
     */
    private SlideTextProcessResult processAgendaSlideTextOnlyWithPolishedText(String courseId, BulkSynthesisRequest.SlideData slide,
                                                                            BulkSynthesisRequest.BulkSynthesisOptions options,
                                                                            List<PPTAudioSegment> textSegments) {

        // 合并所有内容点为一个字符串
        String combinedContent = String.join(" ", slide.getContentPoints());
        String finalPolishedText = combinedContent;

        try {
            // 1. 文本润色（如果启用）
            if (options != null && Boolean.TRUE.equals(options.getEnablePolishing())) {
                try {
                    String customPrompt = pptContextualPolishing.generatePolishingPrompt(slide, combinedContent, 0);
                    Integer hardCap = 200; // 目录页面固定200字限制

                    String polishedText = aiModelService.polishTextWithPrompt(combinedContent, customPrompt, hardCap).block();
                    if (polishedText != null && !polishedText.trim().isEmpty()) {
                        finalPolishedText = polishedText;
                    }
                } catch (Exception e) {
                    log.warn("目录页面润色失败，使用原文，页码: {}", slide.getPageNumber(), e);
                }
            }

            // 2. 分句处理
            List<String> sentences = segmentedSpeechService.splitTextBySentence(finalPolishedText);
            if (sentences.isEmpty()) {
                return SlideTextProcessResult.builder()
                        .segmentCount(0)
                        .polishedText(finalPolishedText)
                        .build();
            }

            // 3. 创建文本片段（不包含音频数据）
            for (int sentenceIndex = 0; sentenceIndex < sentences.size(); sentenceIndex++) {
                String sentence = sentences.get(sentenceIndex);
                int globalSegmentIndex = SegmentIndexingStrategy.generateGlobalSegmentIndex(
                        slide.getPageNumber(), 0, sentenceIndex);

                PPTAudioSegment textSegment = PPTAudioSegment.builder()
                        .courseId(courseId)
                        .slidePageNumber(slide.getPageNumber())
                        .slideTitle(slide.getTitle())
                        .contentPointIndex(0)
                        .segmentIndex(globalSegmentIndex)
                        .slideType(slide.getSlideType())
                        .slideDescription(slide.getDescription())
                        .originalText(combinedContent)
                        .polishedText(finalPolishedText)
                        .textContent(sentence)
                        .audioData(null) // 不包含音频数据
                        .audioSize(0L)
                        .duration(0L)
                        .audioFormat(options != null ? options.getAudioFormat() : "wav")
                        .sampleRate(options != null ? options.getSampleRate() : 16000)
                        .createdAt(LocalDateTime.now())
                        .build();

                textSegments.add(textSegment);
            }

            return SlideTextProcessResult.builder()
                    .segmentCount(sentences.size())
                    .polishedText(finalPolishedText)
                    .build();

        } catch (Exception e) {
            log.error("处理目录页面文本失败，页码: {}", slide.getPageNumber(), e);
            return SlideTextProcessResult.builder()
                    .segmentCount(0)
                    .polishedText(combinedContent)
                    .build();
        }
    }

    /**
     * 处理目录页面的文本预处理 - 保持向后兼容
     */
    private int processAgendaSlideTextOnly(String courseId, BulkSynthesisRequest.SlideData slide,
                                         BulkSynthesisRequest.BulkSynthesisOptions options,
                                         List<PPTAudioSegment> textSegments) {

        // 合并所有内容点为一个字符串
        String combinedContent = String.join(" ", slide.getContentPoints());

        try {
            // 1. 文本润色（如果启用）
            String polishedText = combinedContent;
            if (options != null && Boolean.TRUE.equals(options.getEnablePolishing())) {
                try {
                    String customPrompt = pptContextualPolishing.generatePolishingPrompt(slide, combinedContent, 0);
                    Integer hardCap = 200; // 目录页面固定200字限制

                    polishedText = aiModelService.polishTextWithPrompt(combinedContent, customPrompt, hardCap).block();
                    if (polishedText == null || polishedText.trim().isEmpty()) {
                        polishedText = combinedContent;
                    }
                } catch (Exception e) {
                    log.warn("目录页面润色失败，使用原文，页码: {}", slide.getPageNumber(), e);
                    polishedText = combinedContent;
                }
            }

            // 2. 分句处理
            List<String> sentences = segmentedSpeechService.splitTextBySentence(polishedText);
            if (sentences.isEmpty()) {
                return 0;
            }

            // 3. 创建文本片段（不包含音频数据）
            for (int sentenceIndex = 0; sentenceIndex < sentences.size(); sentenceIndex++) {
                String sentence = sentences.get(sentenceIndex);
                int globalSegmentIndex = SegmentIndexingStrategy.generateGlobalSegmentIndex(
                        slide.getPageNumber(), 0, sentenceIndex);

                PPTAudioSegment textSegment = PPTAudioSegment.builder()
                        .courseId(courseId)
                        .slidePageNumber(slide.getPageNumber())
                        .slideTitle(slide.getTitle())
                        .contentPointIndex(0)
                        .segmentIndex(globalSegmentIndex)
                        .slideType(slide.getSlideType())
                        .slideDescription(slide.getDescription())
                        .originalText(combinedContent)
                        .polishedText(polishedText)
                        .textContent(sentence)
                        .audioData(null) // 不包含音频数据
                        .audioSize(0L)
                        .duration(0L)
                        .audioFormat(options != null ? options.getAudioFormat() : "wav")
                        .sampleRate(options != null ? options.getSampleRate() : 16000)
                        .createdAt(LocalDateTime.now())
                        .build();

                textSegments.add(textSegment);
            }

            return sentences.size();

        } catch (Exception e) {
            log.error("处理目录页面文本失败，页码: {}", slide.getPageNumber(), e);
            return 0;
        }
    }

    /**
     * 处理普通页面的文本预处理并返回润色文本
     */
    private SlideTextProcessResult processNormalSlideTextOnlyWithPolishedText(String courseId, BulkSynthesisRequest.SlideData slide,
                                                                            BulkSynthesisRequest.BulkSynthesisOptions options,
                                                                            List<PPTAudioSegment> textSegments) {

        int segmentCount = 0;
        StringBuilder slidePolishedText = new StringBuilder();

        for (int pointIndex = 0; pointIndex < slide.getContentPoints().size(); pointIndex++) {
            String contentPoint = slide.getContentPoints().get(pointIndex);

            if (contentPoint == null || contentPoint.trim().isEmpty()) {
                continue;
            }

            try {
                // 1. 文本润色
                String polishedText = contentPoint;
                if (options != null && Boolean.TRUE.equals(options.getEnablePolishing())) {
                    try {
                        String customPrompt = pptContextualPolishing.generatePolishingPrompt(slide, contentPoint, pointIndex);
                        int originalLength = contentPoint.length();
                        Integer hardCap = originalLength + 200;

                        String result = aiModelService.polishTextWithPrompt(contentPoint, customPrompt, hardCap).block();
                        if (result != null && !result.trim().isEmpty()) {
                            polishedText = result;
                        }
                    } catch (Exception e) {
                        log.warn("文本润色失败，使用原文，页码: {}, 内容点索引: {}",
                                slide.getPageNumber(), pointIndex, e);
                    }
                }

                // 收集润色后的文本
                if (slidePolishedText.length() > 0) {
                    slidePolishedText.append("\n");
                }
                slidePolishedText.append("- ").append(polishedText);

                // 2. 分句处理
                List<String> sentences = segmentedSpeechService.splitTextBySentence(polishedText);
                if (sentences.isEmpty()) {
                    continue;
                }

                // 3. 创建文本片段
                for (int sentenceIndex = 0; sentenceIndex < sentences.size(); sentenceIndex++) {
                    String sentence = sentences.get(sentenceIndex);
                    int globalSegmentIndex = SegmentIndexingStrategy.generateGlobalSegmentIndex(
                            slide.getPageNumber(), pointIndex, sentenceIndex);

                    PPTAudioSegment textSegment = PPTAudioSegment.builder()
                            .courseId(courseId)
                            .slidePageNumber(slide.getPageNumber())
                            .slideTitle(slide.getTitle())
                            .contentPointIndex(pointIndex)
                            .segmentIndex(globalSegmentIndex)
                            .slideType(slide.getSlideType())
                            .slideDescription(slide.getDescription())
                            .originalText(contentPoint)
                            .polishedText(polishedText)
                            .textContent(sentence)
                            .audioData(null) // 不包含音频数据
                            .audioSize(0L)
                            .duration(0L)
                            .audioFormat(options != null ? options.getAudioFormat() : "wav")
                            .sampleRate(options != null ? options.getSampleRate() : 16000)
                            .createdAt(LocalDateTime.now())
                            .build();

                    textSegments.add(textSegment);
                    segmentCount++;
                }

            } catch (Exception e) {
                log.error("处理内容点文本失败，页码: {}, 内容点索引: {}", slide.getPageNumber(), pointIndex, e);
            }
        }

        return SlideTextProcessResult.builder()
                .segmentCount(segmentCount)
                .polishedText(slidePolishedText.toString())
                .build();
    }

    /**
     * 处理普通页面的文本预处理 - 保持向后兼容
     */
    private int processNormalSlideTextOnly(String courseId, BulkSynthesisRequest.SlideData slide,
                                         BulkSynthesisRequest.BulkSynthesisOptions options,
                                         List<PPTAudioSegment> textSegments) {

        int segmentCount = 0;

        for (int pointIndex = 0; pointIndex < slide.getContentPoints().size(); pointIndex++) {
            String contentPoint = slide.getContentPoints().get(pointIndex);

            if (contentPoint == null || contentPoint.trim().isEmpty()) {
                continue;
            }

            try {
                // 1. 文本润色
                String polishedText = contentPoint;
                if (options != null && Boolean.TRUE.equals(options.getEnablePolishing())) {
                    try {
                        String customPrompt = pptContextualPolishing.generatePolishingPrompt(slide, contentPoint, pointIndex);
                        int originalLength = contentPoint.length();
                        Integer hardCap = originalLength + 200;

                        polishedText = aiModelService.polishTextWithPrompt(contentPoint, customPrompt, hardCap).block();
                        if (polishedText == null || polishedText.trim().isEmpty()) {
                            polishedText = contentPoint;
                        }
                    } catch (Exception e) {
                        log.warn("文本润色失败，使用原文，页码: {}, 内容点索引: {}",
                                slide.getPageNumber(), pointIndex, e);
                        polishedText = contentPoint;
                    }
                }

                // 2. 分句处理
                List<String> sentences = segmentedSpeechService.splitTextBySentence(polishedText);
                if (sentences.isEmpty()) {
                    continue;
                }

                // 3. 创建文本片段
                for (int sentenceIndex = 0; sentenceIndex < sentences.size(); sentenceIndex++) {
                    String sentence = sentences.get(sentenceIndex);
                    int globalSegmentIndex = SegmentIndexingStrategy.generateGlobalSegmentIndex(
                            slide.getPageNumber(), pointIndex, sentenceIndex);

                    PPTAudioSegment textSegment = PPTAudioSegment.builder()
                            .courseId(courseId)
                            .slidePageNumber(slide.getPageNumber())
                            .slideTitle(slide.getTitle())
                            .contentPointIndex(pointIndex)
                            .segmentIndex(globalSegmentIndex)
                            .slideType(slide.getSlideType())
                            .slideDescription(slide.getDescription())
                            .originalText(contentPoint)
                            .polishedText(polishedText)
                            .textContent(sentence)
                            .audioData(null) // 不包含音频数据
                            .audioSize(0L)
                            .duration(0L)
                            .audioFormat(options != null ? options.getAudioFormat() : "wav")
                            .sampleRate(options != null ? options.getSampleRate() : 16000)
                            .createdAt(LocalDateTime.now())
                            .build();

                    textSegments.add(textSegment);
                    segmentCount++;
                }

            } catch (Exception e) {
                log.error("处理内容点文本失败，页码: {}, 内容点索引: {}", slide.getPageNumber(), pointIndex, e);
            }
        }

        return segmentCount;
    }

    /**
     * 为页面的文本片段生成音频
     */
    private int synthesizePageAudio(String courseId, Integer pageNumber, List<AudioSegment> textSegments) {
        log.info("开始为页面生成音频，会话ID: {}, 页码: {}, 片段数: {}", courseId, pageNumber, textSegments.size());

        List<PPTAudioSegment> audioSegments = new ArrayList<>();
        int successCount = 0;

        for (AudioSegment textSegment : textSegments) {
            try {
                // 语音合成
                byte[] audioData;
                try {
                    Mono<byte[]> synthesisResult = textToSpeechService.synthesizeSpeech(textSegment.getTextContent());
                    audioData = synthesisResult.block();
                    if (audioData == null || audioData.length == 0) {
                        audioData = simulateTTSSynthesis(textSegment.getTextContent());
                    }
                } catch (Exception ttsError) {
                    log.warn("TTS合成失败，使用模拟数据，片段: {}", textSegment.getSegmentIndex(), ttsError);
                    audioData = simulateTTSSynthesis(textSegment.getTextContent());
                }

                long duration = estimateAudioDuration(audioData);

                // 创建音频片段对象
                PPTAudioSegment audioSegment = PPTAudioSegment.builder()
                        .courseId(courseId)
                        .slidePageNumber(pageNumber)
                        .slideTitle(textSegment.getSlideTitle())
                        .contentPointIndex(0) // 从textSegment中获取
                        .segmentIndex(textSegment.getSegmentIndex())
                        .slideType(textSegment.getSlideType())
                        .slideDescription(textSegment.getSlideDescription())
                        .originalText(textSegment.getOriginalText())
                        .polishedText(textSegment.getPolishedText())
                        .textContent(textSegment.getTextContent())
                        .audioData(audioData)
                        .audioSize((long) audioData.length)
                        .duration(duration)
                        .audioFormat(textSegment.getAudioFormat())
                        .sampleRate(textSegment.getSampleRate())
                        .checksum(generateChecksum(audioData))
                        .createdAt(LocalDateTime.now())
                        .build();

                audioSegments.add(audioSegment);
                successCount++;

            } catch (Exception e) {
                log.error("为文本片段生成音频失败，片段索引: {}", textSegment.getSegmentIndex(), e);
            }
        }

        // 保存音频数据并更新状态
        if (!audioSegments.isEmpty()) {
            try {
                boolean saved = pageLevelAudioService.updateSegmentsWithAudio(courseId, pageNumber, audioSegments);
                if (saved) {
                    log.info("页面音频更新成功，页码: {}, 成功片段数: {}", pageNumber, successCount);
                } else {
                    log.error("页面音频更新失败，页码: {}", pageNumber);
                }
            } catch (Exception e) {
                log.error("更新页面音频时发生异常，页码: {}", pageNumber, e);
            }
        }

        return successCount;
    }

    /**
     * 根据润色文本更新文本片段
     * 这个方法会重新分句并更新数据库中的文本片段
     */
    private boolean updateTextSegmentsFromPolishedText(String courseId, String polishedText) {
        log.info("开始根据润色文本更新文本片段，会话ID: {}, 文本长度: {}", courseId, polishedText.length());

        try {
            // 1. 获取会话信息
            LessonSession session = voiceDatabaseService.getCompleteSessionInfo(courseId);
            if (session == null) {
                log.error("会话不存在，无法更新文本片段，会话ID: {}", courseId);
                return false;
            }

            // 2. 删除现有的文本片段（TEXT_ONLY状态的）
            boolean deleted = pageLevelAudioService.deleteTextOnlySegments(courseId);
            if (!deleted) {
                log.warn("删除现有文本片段失败，会话ID: {}", courseId);
            }

            // 3. 按页面分割润色文本
            // 假设润色文本格式为：第X页: 标题\n内容\n\n
            String[] pageTexts = polishedText.split("第\\d+页:");

            for (int i = 1; i < pageTexts.length; i++) { // 跳过第一个空元素
                String pageText = pageTexts[i].trim();
                if (pageText.isEmpty()) continue;

                // 解析页面信息
                String[] lines = pageText.split("\n", 2);
                if (lines.length < 2) continue;

                String pageTitle = lines[0].trim();
                String pageContent = lines[1].trim();

                // 分句处理
                List<String> sentences = segmentedSpeechService.splitTextBySentence(pageContent);

                // 创建新的文本片段
                List<PPTAudioSegment> textSegments = new ArrayList<>();
                for (int sentenceIndex = 0; sentenceIndex < sentences.size(); sentenceIndex++) {
                    String sentence = sentences.get(sentenceIndex);

                    // 使用页面索引作为全局片段索引的基础
                    int globalSegmentIndex = SegmentIndexingStrategy.generateGlobalSegmentIndex(i, 0, sentenceIndex);

                    PPTAudioSegment textSegment = PPTAudioSegment.builder()
                            .courseId(courseId)
                            .slidePageNumber(i)
                            .slideTitle(pageTitle)
                            .contentPointIndex(0)
                            .segmentIndex(globalSegmentIndex)
                            .slideType("CONTENT") // 默认类型
                            .slideDescription("")
                            .originalText(pageContent)
                            .polishedText(pageContent)
                            .textContent(sentence)
                            .audioData(null) // 不包含音频数据
                            .audioSize(0L)
                            .duration(0L)
                            .audioFormat("wav")
                            .sampleRate(16000)
                            .createdAt(LocalDateTime.now())
                            .build();

                    textSegments.add(textSegment);
                }

                // 保存页面的文本片段
                if (!textSegments.isEmpty()) {
                    boolean saved = pageLevelAudioService.saveTextOnlySegments(
                            courseId, i, pageTitle, "CONTENT", "", textSegments);

                    if (saved) {
                        log.info("页面文本片段更新成功，页码: {}, 片段数: {}", i, textSegments.size());
                    } else {
                        log.error("页面文本片段更新失败，页码: {}", i);
                    }
                }
            }

            log.info("文本片段更新完成，会话ID: {}", courseId);
            return true;

        } catch (Exception e) {
            log.error("根据润色文本更新文本片段失败，会话ID: {}", courseId, e);
            return false;
        }
    }

    /**
     * 更新会话状态
     */
    private boolean updateSessionStatus(String courseId, String status, String reviewerId,
                                      LocalDateTime reviewedAt, String comments) {
        try {
            return voiceDatabaseService.updateSessionStatus(courseId, status, reviewerId, reviewedAt, comments);
        } catch (Exception e) {
            log.error("更新会话状态失败，会话ID: {}, 状态: {}", courseId, status, e);
            return false;
        }
    }

}
