package com.treepeople.leapmindtts.service.admin.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.treepeople.leapmindtts.mapper.AudioSegmentMapper;
import com.treepeople.leapmindtts.mapper.TeachingContentMapper;
import com.treepeople.leapmindtts.pojo.dto.*;
import com.treepeople.leapmindtts.pojo.entity.AudioSegment;
import com.treepeople.leapmindtts.pojo.entity.LessonSession;
import com.treepeople.leapmindtts.pojo.entity.TeachingContent;
import com.treepeople.leapmindtts.service.admin.BulkSpeechService;
import com.treepeople.leapmindtts.service.admin.LessonSessionService;
import com.treepeople.leapmindtts.service.impl.TtsBatchServiceImpl;
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
    private final NarrationBridgeService narrationBridgeService;
    private final TtsBatchServiceImpl ttsBatchService;
    private final TeachingContentMapper teachingContentMapper;
    private final ObjectMapper objectMapper;

    @Override
    public BulkSynthesisResponse processBulkSynthesis(BulkSynthesisRequest request, String userJwt) {
        LocalDateTime startTime = LocalDateTime.now();
        String courseId = Optional.ofNullable(request.getCourseId())
                .filter(s -> !s.isBlank()).orElse(generateCourseId());
        long prepIdN = courseIdToPrepId(courseId);
        log.info("[新体系] 批量语音合成，courseId={} prepId={} title={} slides={} userJwt 存在={}",
                courseId, prepIdN, request.getTitle(),
                request.getSlides() != null ? request.getSlides().size() : 0,
                userJwt != null && !userJwt.isBlank());

        try {
            List<BulkSynthesisRequest.SlideData> sorted = request.getSlides() == null ? List.of()
                    : request.getSlides().stream()
                    .filter(s -> s != null && s.getPageNumber() != null)
                    .sorted(Comparator.comparing(BulkSynthesisRequest.SlideData::getPageNumber))
                    .collect(Collectors.toList());
            if (sorted.isEmpty()) {
                return BulkSynthesisResponse.builder()
                        .courseId(courseId).status("FAILED").message("slides 为空")
                        .startTime(startTime).build();
            }
            int totalContentPoints = sorted.stream()
                    .mapToInt(s -> s.getContentPoints() != null ? s.getContentPoints().size() : 0).sum();

            // 1) 老请求 → 新结构，首写 DB
            PptStructureDTO structure = convertBulkRequestToStructure(request, sorted);
            saveOrUpdateTeachingContent(prepIdN, request.getTitle(),
                    objectMapper.writeValueAsString(structure), courseId);

            // 2) 公共一键 TTS：收集 narration → 3 并发 M8 → [AUDIO_URL:] 回填 → 回写 DB
            int[] success = {0};
            success[0] = ttsBatchService.generateAndBackfill(structure, prepIdN, userJwt, p -> {
                log.debug("[新体系] TTS 进度 {}/{} slide={} status={}",
                        p.getCurrentIndex(), p.getTotalCount(), p.getCurrentTitle(), p.getStatus());
            });

            // 3) lesson_session 状态兼容现有管理后台展示
            try {
                if (voiceDatabaseService.getCompleteSessionInfo(courseId) == null) {
                    voiceDatabaseService.createCompleteSession(
                            courseId, request.getTitle(), originalTextFromStructure(structure), null, new ArrayList<>());
                }
                updateSessionStatus(courseId, "SYNTHESIZED", "SYSTEM",
                        LocalDateTime.now(), "新体系合成，跳过审核");
            } catch (Exception e) {
                log.warn("创建 lesson_session 展示记录失败(不影响主链路)，courseId={} err={}",
                        courseId, e.getMessage());
            }

            return BulkSynthesisResponse.builder()
                    .courseId(courseId)
                    .status("COMPLETED")
                    .totalSlides(sorted.size())
                    .totalContentPoints(totalContentPoints)
                    .message(String.format("新体系合成完成 %d 页，成功生成 %d 条旁白音频", sorted.size(), success[0]))
                    .startTime(startTime)
                    .build();

        } catch (Exception e) {
            log.error("[新体系] 批量合成失败 courseId={}", courseId, e);
            return BulkSynthesisResponse.builder()
                    .courseId(courseId).status("FAILED")
                    .message("处理失败: " + e.getMessage())
                    .startTime(startTime).build();
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

    // ================================================================
    //  新体系辅助方法
    // ================================================================

    /** courseId → prepId：数字型直接转；否则取 hashCode 绝对值作为稳定伪 ID */
    private long courseIdToPrepId(String courseId) {
        if (courseId == null || courseId.isBlank()) return 0L;
        try { return Long.parseLong(courseId.trim()); }
        catch (NumberFormatException e) {
            return Math.abs((long) courseId.hashCode()) | 0x10000000L; // 高位打标，避免与真实 ID 撞
        }
    }

    /**
     * 将老 BulkSynthesisRequest（slides:[{page_number,title,content_points[]}]）
     * 转成新体系 PptStructureDTO。
     *  notes = 标题 + 要点拼接（模拟 Python narration_text：整页一段话）
     *  bulletPoints = content_points
     *  imageSuggestion / highlightPoints = 尽量从 options 取，没有就留空
     */
    private PptStructureDTO convertBulkRequestToStructure(
            BulkSynthesisRequest req, List<BulkSynthesisRequest.SlideData> sorted) {

        PptStructureDTO structure = new PptStructureDTO();
        structure.setTitle(req.getTitle() != null ? req.getTitle() : "未命名PPT");

        List<PptStructureDTO.SlideDTO> slides = new ArrayList<>(sorted.size());
        for (BulkSynthesisRequest.SlideData s : sorted) {
            PptStructureDTO.SlideDTO sd = new PptStructureDTO.SlideDTO();
            sd.setPageNum(s.getPageNumber());
            sd.setTitle(s.getTitle() != null ? s.getTitle() : "");
            sd.setType(s.getType() != null ? s.getType() : "content");
            sd.setBulletPoints(s.getContentPoints() != null ? s.getContentPoints() : List.of());

            // 页面 narration：标题 + 要点 拼起来 → notes
            StringBuilder notes = new StringBuilder();
            if (s.getTitle() != null && !s.getTitle().isBlank()) notes.append(s.getTitle()).append("。\n");
            if (s.getContentPoints() != null && !s.getContentPoints().isEmpty()) {
                for (int i = 0; i < s.getContentPoints().size(); i++) {
                    String pt = s.getContentPoints().get(i);
                    if (pt == null || pt.isBlank()) continue;
                    notes.append(i + 1).append("、").append(pt);
                    if (!pt.endsWith("。") && !pt.endsWith("！") && !pt.endsWith("？")
                            && !pt.endsWith(".") && !pt.endsWith("!") && !pt.endsWith("?")) {
                        notes.append("。");
                    }
                    notes.append("\n");
                }
            }
            sd.setNotes(notes.toString().trim());

            // 可选字段：如果 SlideData 提供了对应字段则填充（新 BulkSynthesisRequest 暂时没有，留空）
            sd.setImageSuggestion("");
            sd.setFormula("");
            sd.setHighlightPoints(List.of());
            sd.setInteraction(null);
            slides.add(sd);
        }
        structure.setSlides(slides);
        return structure;
    }

    /** 同 prep_id 幂等：有则 update，无则 insert。courseId 写入 userId 占位（避免非空约束） */
    private void saveOrUpdateTeachingContent(long prepId, String title, String pptJson, String courseId) {
        TeachingContent exist = null;
        try { exist = teachingContentMapper.selectByPrepId(prepId); } catch (Exception ignore) {}
        if (exist != null) {
            exist.setPptStructure(pptJson);
            if (title != null) exist.setTitle(title);
            // createdAt 自动填充，不覆盖
            teachingContentMapper.updateById(exist);
            log.debug("[新体系] 更新 teaching_contents id={} prepId={}", exist.getId(), prepId);
        } else {
            TeachingContent nc = TeachingContent.builder()
                    .prepId(prepId)
                    .userId(courseIdToUserId(courseId))
                    .title(title != null ? title : "未命名PPT")
                    .status("published")
                    .pptStructure(pptJson)
                    .build();
            teachingContentMapper.insert(nc);
            log.debug("[新体系] 插入 teaching_contents id={} prepId={}", nc.getId(), prepId);
        }
    }

    private Long courseIdToUserId(String courseId) {
        // userId 占位：如果课程里不带用户，用 0 或取 courseId 的 hash 做稳定值；
        // 项目有全局默认用户 ID 时可在这里换成配置值
        if (courseId == null) return 0L;
        return Math.abs((long) courseId.hashCode()) % 9_999_999L + 1L;
    }

    /** 从 PptStructureDTO 收集 NarrationTask（跳过空 notes、跳过已有 AUDIO_URL 前缀） */
    private List<TtsBatchServiceImpl.NarrationTask> collectNarrationTasks(PptStructureDTO structure) {
        if (structure.getSlides() == null) return List.of();
        List<TtsBatchServiceImpl.NarrationTask> tasks = new ArrayList<>();
        for (int i = 0; i < structure.getSlides().size(); i++) {
            PptStructureDTO.SlideDTO s = structure.getSlides().get(i);
            String n = s.getNotes();
            if (n == null || n.isBlank()) continue;
            if (n.startsWith("[AUDIO_URL:")) continue;
            tasks.add(new TtsBatchServiceImpl.NarrationTask(i, n, s.getTitle()));
        }
        return tasks;
    }

    /** 将生成的 {pageIndex, audioUrl} 回填到 structure.slides[i].notes（前缀 + 原文） */
    private void applyAudioUrlsToStructure(PptStructureDTO structure, Map<Integer, String> audioUrls) {
        if (structure.getSlides() == null || audioUrls == null || audioUrls.isEmpty()) return;
        for (Map.Entry<Integer, String> e : audioUrls.entrySet()) {
            int idx = e.getKey();
            String url = e.getValue();
            if (idx < 0 || idx >= structure.getSlides().size()) continue;
            PptStructureDTO.SlideDTO s = structure.getSlides().get(idx);
            String notes = s.getNotes() == null ? "" : s.getNotes();
            String stripped;
            if (notes.startsWith("[AUDIO_URL:")) {
                int newLine = notes.indexOf('\n');
                stripped = newLine < 0 ? "" : notes.substring(newLine + 1).trim();
            } else {
                stripped = notes;
            }
            s.setNotes("[AUDIO_URL:" + url + "]\n" + stripped);
        }
    }

    /** 给 lesson_session.original_text 生成全文（保持原有展示列表的搜索能力） */
    private String originalTextFromStructure(PptStructureDTO s) {
        if (s.getSlides() == null) return "";
        StringBuilder sb = new StringBuilder();
        for (PptStructureDTO.SlideDTO sd : s.getSlides()) {
            int pn = sd.getPageNum() != null ? sd.getPageNum() : sb.toString().split("\n第").length;
            sb.append("第").append(pn).append("页: ");
            if (sd.getTitle() != null) sb.append(sd.getTitle());
            sb.append("\n");
            if (sd.getBulletPoints() != null) {
                for (String p : sd.getBulletPoints()) sb.append("- ").append(p).append("\n");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    @Override
    public List<PPTAudioSegment> getPageAudioSegments(String courseId, Integer pageNumber) {
        log.info("[新体系] 获取页面音频片段，会话ID: {}, 页码: {}", courseId, pageNumber);
        // 统一走新体系 NarrationBridge（内部读 teaching_contents.slides[].notes [AUDIO_URL:]）
        return narrationBridgeService.getSegments(courseId, pageNumber);
    }

    @Override
    public PPTAudioSegment getAudioSegment(String courseId, Integer segmentIndex) {
        log.warn("[新体系] 不支持按全局片段索引取单条（1页=1段，索引=pageNumber-1），courseId={} segmentIndex={}",
                courseId, segmentIndex);
        // 兼容：1段=1页 → 把 segmentIndex 当 pageNumber-1 查，没找到返回 null
        List<PPTAudioSegment> page = getPageAudioSegments(courseId, segmentIndex + 1);
        return (page != null && !page.isEmpty()) ? page.get(0) : null;
    }

    @Override
    public PPTAudioInfo getPPTAudioInfo(String courseId) {
        log.info("[新体系] 获取PPT音频信息，会话ID: {}", courseId);
        // 统一走新体系 NarrationBridge 统计
        return narrationBridgeService.getInfo(courseId);
    }

    //1
    @Override
    public BulkPreprocessingResponse processBulkPreprocessing(BulkSynthesisRequest request) {
        // 【新体系 · 预处理】
        // 只把 PPT 内容转 PptStructureDTO → 写入 teaching_contents(prep_id=courseId)，不调 TTS。
        // 仍然把 lesson_session 置为 PENDING_REVIEW，给现有管理后台的"待审核列表"保持一致展示。
        String courseId = Optional.ofNullable(request.getCourseId())
                .filter(s -> !s.isBlank()).orElse(generateCourseId());
        long prepIdN = courseIdToPrepId(courseId);
        LocalDateTime startTime = LocalDateTime.now();
        log.info("[新体系] 批量文本预处理，courseId={} prepId={} title={} slides={}",
                courseId, prepIdN, request.getTitle(),
                request.getSlides() != null ? request.getSlides().size() : 0);

        try {
            List<BulkSynthesisRequest.SlideData> sorted = request.getSlides() == null ? List.of()
                    : request.getSlides().stream()
                    .filter(s -> s != null && s.getPageNumber() != null)
                    .sorted(Comparator.comparing(BulkSynthesisRequest.SlideData::getPageNumber))
                    .collect(Collectors.toList());
            if (sorted.isEmpty()) {
                return BulkPreprocessingResponse.builder()
                        .courseId(courseId).status("FAILED").message("slides 为空")
                        .startTime(startTime).endTime(LocalDateTime.now()).build();
            }

            PptStructureDTO structure = convertBulkRequestToStructure(request, sorted);
            String json = objectMapper.writeValueAsString(structure);
            saveOrUpdateTeachingContent(prepIdN, request.getTitle(), json, courseId);

            int totalTextSegments = sorted.size();   // 1 页 = 1 段 narration

            // 保留 lesson_session 展示记录
            try {
                if (voiceDatabaseService.getCompleteSessionInfo(courseId) == null) {
                    String original = originalTextFromStructure(structure);
                    voiceDatabaseService.createCompleteSession(
                            courseId, request.getTitle(), original, null, new ArrayList<>());
                }
                updateSessionStatus(courseId, "DRAFT", null, null, null);
                updateSessionStatus(courseId, "PENDING_REVIEW", null, null, null);
            } catch (Exception e) {
                log.warn("创建 lesson_session 展示记录失败(不影响新体系主链路) courseId={} err={}",
                        courseId, e.getMessage());
            }

            return BulkPreprocessingResponse.builder()
                    .courseId(courseId)
                    .status("SUCCESS")
                    .totalSlides(sorted.size())
                    .totalTextSegments(totalTextSegments)
                    .message("新体系文本预处理完成，等待审核后执行合成")
                    .startTime(startTime)
                    .endTime(LocalDateTime.now())
                    .build();

        } catch (Exception e) {
            log.error("[新体系] 批量预处理失败 courseId={}", courseId, e);
            return BulkPreprocessingResponse.builder()
                    .courseId(courseId).status("FAILED")
                    .message("处理失败: " + e.getMessage())
                    .startTime(startTime).endTime(LocalDateTime.now())
                    .build();
        }
    }

    @Override
    public BulkSynthesisResponse executeBulkSynthesis(String courseId, String userJwt) {
        LocalDateTime startTime = LocalDateTime.now();
        long prepIdN = courseIdToPrepId(courseId);
        log.info("[新体系] 审核后执行合成，courseId={} prepId={} userJwt 存在={}",
                courseId, prepIdN, userJwt != null && !userJwt.isBlank());

        try {
            LessonSession session = voiceDatabaseService.getCompleteSessionInfo(courseId);
            if (session != null && !"APPROVED".equals(session.getProcessingStatus())) {
                log.warn("[新体系] lesson_session 状态未 APPROVED，仍执行合成（新体系不依赖老状态机），status={}",
                        session.getProcessingStatus());
            }

            TeachingContent tc = teachingContentMapper.selectByPrepId(prepIdN);
            if (tc == null || tc.getPptStructure() == null || tc.getPptStructure().isBlank()) {
                return BulkSynthesisResponse.builder()
                        .courseId(courseId).status("FAILED")
                        .message("未找到 PPT 结构数据，请先执行预处理").startTime(startTime).build();
            }
            PptStructureDTO structure = PptStructureDTO.parse(objectMapper, tc.getPptStructure());
            if (structure.getSlides() == null || structure.getSlides().isEmpty()) {
                return BulkSynthesisResponse.builder()
                        .courseId(courseId).status("FAILED")
                        .message("PPT 结构 slides 为空").startTime(startTime).build();
            }

            int success = ttsBatchService.generateAndBackfill(structure, prepIdN, userJwt, p -> {
                log.debug("[新体系] 执行合成进度 {}/{} slide={} status={}",
                        p.getCurrentIndex(), p.getTotalCount(), p.getCurrentTitle(), p.getStatus());
            });

            try { updateSessionStatus(courseId, "SYNTHESIZED", null, null, null); } catch (Exception ignore) {}

            return BulkSynthesisResponse.builder()
                    .courseId(courseId)
                    .status("COMPLETED")
                    .totalSlides(structure.getSlides().size())
                    .totalContentPoints(success)
                    .message(success == 0
                            ? "无待合成片段（已全部生成过或原文为空）"
                            : String.format("执行合成完成，成功 %d 条", success))
                    .startTime(startTime)
                    .build();

        } catch (Exception e) {
            log.error("[新体系] 执行合成失败 courseId={}", courseId, e);
            return BulkSynthesisResponse.builder()
                    .courseId(courseId).status("FAILED")
                    .message("执行合成失败: " + e.getMessage())
                    .startTime(startTime).build();
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
