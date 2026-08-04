package com.treepeople.leapmindtts.service.lesson;

import com.treepeople.leapmindtts.config.SegmentedSpeechProperties;
import com.treepeople.leapmindtts.pojo.dto.SegmentedSpeechResult;
import com.treepeople.leapmindtts.pojo.dto.SpeechSegment;
import com.treepeople.leapmindtts.util.SpeechSegmentConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 分段语音服务
 * 负责文本分句、逐句TTS合成和语音片段管理
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SegmentedSpeechService {

    private final SegmentedSpeechProperties properties;
    private final TextToSpeechService textToSpeechService;
    private final PlaybackStateManager playbackStateManager;
    private final SegmentPreloadService preloadService;
    private final VoiceDatabaseService voiceDatabaseService;

    /**
     * 生成会话ID
     */
    private String generateCourseId() {
        return "session_" + UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 估算音频时长
     */
    private long estimateAudioDuration(byte[] audioData) {
        if (audioData == null || audioData.length <= 44) {
            return 0;
        }
        int dataSize = audioData.length - 44;
        return (long) (dataSize / (16000.0 * 2)) * 1000;
    }

    /**
     * 生成音频数据校验和
     */
    private String generateChecksum(byte[] audioData) {
        if (audioData == null) return "";
        return String.valueOf(audioData.length);
    }

    /**
     * 按句号分割文本
     * 支持中英文句号、感叹号、问号等分句符号
     */
    public List<String> splitTextBySentence(String text) {
        log.info("开始文本分句处理，原文长度: {} 字符", text != null ? text.length() : 0);

        List<String> segments = new ArrayList<>();

        if (text == null || text.trim().isEmpty()) {
            log.warn("输入文本为空，返回空列表");
            return segments;
        }

        String cleanText = text.trim();

        // 获取配置参数
        List<String> separators = properties.getTextSplitting().getSentenceSeparators();
        int maxSegmentLength = properties.getTextSplitting().getMaxSegmentLength();
        int minSegmentLength = properties.getTextSplitting().getMinSegmentLength();

        // 简单分句实现
        List<String> rawSegments = splitBySeparators(cleanText, separators);

        // 处理片段长度限制
        for (String segment : rawSegments) {
            if (segment.length() <= maxSegmentLength && segment.length() >= minSegmentLength) {
                segments.add(segment);
            } else if (segment.length() > maxSegmentLength) {
                // 对过长片段进行二次分割
                segments.addAll(performSecondarySegmentation(segment, maxSegmentLength, minSegmentLength));
            }
        }

        log.info("文本分句完成，最终得到 {} 个有效片段", segments.size());
        return segments;
    }

    /**
     * 使用分隔符列表分割文本
     */
    private List<String> splitBySeparators(String text, List<String> separators) {
        List<String> result = new ArrayList<>();
        StringBuilder currentSegment = new StringBuilder();

        for (int i = 0; i < text.length(); i++) {
            char currentChar = text.charAt(i);
            currentSegment.append(currentChar);

            String currentCharStr = String.valueOf(currentChar);
            if (separators.contains(currentCharStr)) {
                String segment = currentSegment.toString().trim();
                if (!segment.isEmpty()) {
                    result.add(segment);
                }
                currentSegment = new StringBuilder();
            }
        }

        String lastSegment = currentSegment.toString().trim();
        if (!lastSegment.isEmpty()) {
            result.add(lastSegment);
        }

        return result;
    }

    /**
     * 对过长片段进行二次分割
     */
    private List<String> performSecondarySegmentation(String segment, int maxLength, int minLength) {
        List<String> subSegments = new ArrayList<>();

        if (segment.length() <= maxLength) {
            subSegments.add(segment);
            return subSegments;
        }

        // 简单的强制分割
        int start = 0;
        while (start < segment.length()) {
            int end = Math.min(start + maxLength, segment.length());
            String subSegment = segment.substring(start, end).trim();
            if (subSegment.length() >= minLength) {
                subSegments.add(subSegment);
            }
            start = end;
        }

        return subSegments;
    }

    /**
     * 创建分段语音
     */
    public Mono<SegmentedSpeechResult> createSegmentedSpeech(String text) {
        log.info("开始创建分段语音，文本长度: {} 字符", text != null ? text.length() : 0);

        if (!properties.isEnabled()) {
            log.warn("分段语音功能已禁用");
            return Mono.error(new IllegalStateException("分段语音功能已禁用"));
        }

        if (text == null || text.trim().isEmpty()) {
            log.warn("输入文本为空或null");
            return Mono.error(new IllegalArgumentException("输入文本不能为空"));
        }

        List<String> segments = splitTextBySentence(text);

        if (segments.isEmpty()) {
            log.warn("文本分句结果为空");
            return Mono.just(SegmentedSpeechResult.builder()
                    .courseId(generateCourseId())
                    .totalSegments(0)
                    .segmentTexts(new ArrayList<>())
                    .totalDuration(0L)
                    .createdAt(LocalDateTime.now())
                    .isComplete(true)
                    .status("EMPTY")
                    .originalText(text)
                    .completedSegments(0)
                    .failedSegments(0)
                    .synthesisStartTime(LocalDateTime.now())
                    .synthesisEndTime(LocalDateTime.now())
                    .build());
        }

        String courseId = generateCourseId();
        LocalDateTime startTime = LocalDateTime.now();

        log.info("会话 {} 开始分段语音合成，共 {} 个片段", courseId, segments.size());

        SegmentedSpeechResult initialResult = SegmentedSpeechResult.builder()
                .courseId(courseId)
                .totalSegments(segments.size())
                .segmentTexts(segments)
                .totalDuration(0L)
                .createdAt(LocalDateTime.now())
                .isComplete(false)
                .status("PROCESSING")
                .originalText(text)
                .completedSegments(0)
                .failedSegments(0)
                .synthesisStartTime(startTime)
                .build();

        // 异步执行TTS合成
        synthesizeAndStoreSegments(courseId, segments)
                .doOnSuccess(unused -> {
                    log.info("会话 {} 的所有语音片段合成完成", courseId);
                    if (properties.getCache().isEnablePreload()) {
                        preloadService.startPreloading(courseId, segments, 0)
                                .subscribe(null, error -> log.warn("预加载启动失败: {}", error.getMessage()));
                    }
                })
                .doOnError(error -> log.error("会话 {} 的语音片段合成过程中发生错误: {}", courseId, error.getMessage()))
                .subscribe();

        return Mono.just(initialResult);
    }

    /**
     * 合成并存储语音片段
     */
    private Mono<Void> synthesizeAndStoreSegments(String courseId, List<String> sentences) {
        log.info("开始为会话 {} 合成并存储语音片段，共 {} 个句子", courseId, sentences.size());

        return Flux.fromIterable(sentences)
                .index()
                .concatMap(indexedSentence -> {
                    int index = indexedSentence.getT1().intValue();
                    String sentence = indexedSentence.getT2();

                    // 添加延迟以避免请求过于频繁
                    return Mono.delay(java.time.Duration.ofMillis(index * 1000))
                            .then(textToSpeechService.synthesizeSpeech(sentence))
                            .map(audioData -> SpeechSegment.builder()
                                    .segmentIndex(index)
                                    .text(sentence)
                                    .audioData(audioData)
                                    .duration(estimateAudioDuration(audioData))
                                    .createdAt(LocalDateTime.now())
                                    .isPreloaded(false)
                                    .status("COMPLETED")
                                    .audioFormat("wav")
                                    .sampleRate(16000)
                                    .checksum(generateChecksum(audioData))
                                    .build())

                            // 保存音频数据到数据库
                            .doOnNext(segment -> {
                                // 异步保存到数据库，不阻塞主流程
                                Mono.fromRunnable(() -> {
                                    SpeechSegmentConverter.safelySaveToDatabase(
                                        voiceDatabaseService, courseId, segment, "SegmentedSpeechService");
                                })
                                .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
                                .subscribe();
                            })

                            .flatMap(segment -> {
                                // 存储所有片段，包括失败的片段，这样前端可以知道片段状态
                                return playbackStateManager.storeSpeechSegment(courseId, segment)
                                        .thenReturn(segment);
                            })
                            .onErrorResume(error -> {
                                log.error("片段 {} 合成失败 for session {}: {}", index, courseId, error.getMessage());
                                SpeechSegment failedSegment = SpeechSegment.builder()
                                        .segmentIndex(index)
                                        .text(sentence)
                                        .audioData(new byte[0])
                                        .duration(0L)
                                        .createdAt(LocalDateTime.now())
                                        .isPreloaded(false)
                                        .status("FAILED")
                                        .audioFormat("wav")
                                        .sampleRate(16000)
                                        .checksum("")
                                        .build();

                                // 对于失败的片段，我们仍然需要存储它以保持索引一致性
                                return playbackStateManager.storeSpeechSegment(courseId, failedSegment)
                                        .onErrorResume(storeError -> {
                                            log.warn("无法存储失败的片段 {}: {}", index, storeError.getMessage());
                                            return Mono.empty();
                                        })
                                        .thenReturn(failedSegment);
                            });
                })
                .then()
                .doOnSuccess(unused -> log.info("会话 {} 的所有语音片段合成和存储完成", courseId))
                .doOnError(error -> log.error("会话 {} 的语音片段合成过程发生错误: {}", courseId, error.getMessage()));
    }

}
