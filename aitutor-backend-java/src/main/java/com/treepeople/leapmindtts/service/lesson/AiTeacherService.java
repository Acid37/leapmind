package com.treepeople.leapmindtts.service.lesson;

import com.treepeople.leapmindtts.config.TextPolishingProperties;
import com.treepeople.leapmindtts.pojo.dto.PlaybackProgress;
import com.treepeople.leapmindtts.pojo.dto.SegmentedLessonResult;
import com.treepeople.leapmindtts.pojo.dto.SpeechSegment;
import com.treepeople.leapmindtts.pojo.enums.PlaybackStatus;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Base64;

/**
 * AI 教师核心交互服务
 * - 协调 百度ASR -> 通义千问 -> 阿里云TTS 的完整流程
 * - 集成文本润色功能，在TTS之前对文本进行优化
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AiTeacherService {

    private final AiTeacherBaiduAsrService baiduRecognitionService;
    private final AIModelService aiModelService;
    private final TextToSpeechService ttsService;
    private final TextPolishingService textPolishingService;
    private final TextPolishingProperties textPolishingProperties;
    private final SegmentedSpeechService segmentedSpeechService;
    private final PlaybackStateManager playbackStateManager;
    private final StudentQuestionService studentQuestionService;

    /**
     * 开始讲课：将课程文本转换为语音
     * 集成文本润色功能：原始文本 -> 文本润色 -> TTS语音合成
     */
    public Mono<byte[]> startLesson(String lessonContent) {
        log.info("AI教师服务：开始合成课程语音, 内容长度: {} 字符", lessonContent.length());

        // 检查文本润色功能是否启用
        if (textPolishingProperties.isEnabled()) {
            log.info("文本润色功能已启用，开始润色处理");

            // 建立响应式流处理链：文本润色 -> TTS合成
            return textPolishingService.polishTextWithFallback(lessonContent)
                    .doOnNext(polishedText -> {
                        log.info("文本润色完成，原始长度: {} 字符，润色后长度: {} 字符",
                                lessonContent.length(), polishedText.length());
                        log.debug("润色后文本: {}", polishedText);
                    })
                    .flatMap(polishedText -> ttsService.synthesizeSpeech(polishedText))
                    .doOnSuccess(audioData -> log.info("课程语音合成完成，音频大小: {} bytes", audioData.length))
                    .doOnError(error -> log.error("课程语音合成失败", error));
        } else {
            log.info("文本润色功能已禁用，直接进行TTS合成");

            // 直接进行TTS合成
            return ttsService.synthesizeSpeech(lessonContent)
                    .doOnSuccess(audioData -> log.info("课程语音合成完成，音频大小: {} bytes", audioData.length))
                    .doOnError(error -> log.error("课程语音合成失败", error));
        }
    }

    /**
     * 开始分段讲课
     * 支持文本润色和分段语音合成
     */
    public Mono<SegmentedLessonResult> startSegmentedLesson(String lessonContent) {
        log.info("AI教师服务：开始分段讲课，内容长度: {} 字符", lessonContent.length());

        // 检查文本润色功能是否启用
        if (textPolishingProperties.isEnabled()) {
            log.info("文本润色功能已启用，开始润色处理");

            return textPolishingService.polishTextWithFallback(lessonContent)
                    .doOnNext(polishedText -> {
                        log.info("文本润色完成，原始长度: {} 字符，润色后长度: {} 字符",
                                lessonContent.length(), polishedText.length());
                    })
                    .flatMap(polishedText -> createSegmentedLessonFromText(lessonContent, polishedText));
        } else {
            log.info("文本润色功能已禁用，直接进行分段处理");
            return createSegmentedLessonFromText(lessonContent, lessonContent);
        }
    }

    /**
     * 处理学生打断（语音识别 + AI回答 + 语音合成）
     */
    private String SESSION__ID = "";
    public Mono<InteractionResult> handleStudentInterruption(byte[] audioData) {
        log.info("AI教师服务：处理学生打断，音频数据大小: {} bytes", audioData.length);

        return baiduRecognitionService.recognize(audioData)
                .doOnNext(recognizedText -> log.info("语音识别结果: {}", recognizedText))
                .flatMap(recognizedText -> {
                    // 调用AI模型生成回答
                    return aiModelService.getAIResponse(recognizedText)
                            .doOnNext(aiResponse -> log.info("AI回答: {}", aiResponse))
                            .doOnNext(aiResponse -> {
                                // 异步保存用户语音提问和AI回答到数据库
                                Mono.fromRunnable(() -> {
                                    try {
                                        boolean saved = studentQuestionService.saveVoiceQuestion(
                                            "GLOBAL_INTERRUPTION", // 全局打断的特殊会话ID
                                            null, // segmentIndex - 语音打断时可能没有特定片段
                                            recognizedText, // 用户提问文本
                                            aiResponse, // AI回答文本
                                            audioData, // 用户语音数据
                                            null // AI回答音频将在后续生成
                                        );
                                        if (saved) {
                                            log.debug("语音提问已保存到数据库，问题: {}", recognizedText);
                                        } else {
                                            log.warn("语音提问保存到数据库失败，问题: {}", recognizedText);
                                        }
                                    } catch (Exception e) {
                                        log.error("保存语音提问到数据库时发生异常，问题: {}", recognizedText, e);
                                    }
                                })
                                .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
                                .subscribe();
                            })
                            .flatMap(aiResponse -> {
                                // 将AI回答转换为语音
                                return ttsService.synthesizeSpeech(aiResponse)
                                        .doOnNext(answerAudioBytes -> {
                                            // 异步更新数据库记录，添加AI回答音频
                                            Mono.fromRunnable(() -> {
                                                try {
                                                    // 重新保存完整的语音提问记录（包含AI回答音频）
                                                    boolean saved = studentQuestionService.saveVoiceQuestion(
                                                        SESSION__ID, // courseId
                                                        null, // segmentIndex
                                                        recognizedText, // 用户提问文本
                                                        aiResponse, // AI回答文本
                                                        audioData, // 用户语音数据
                                                        answerAudioBytes // AI回答音频数据
                                                    );
                                                    if (saved) {
                                                        log.debug("完整语音交互已保存到数据库，问题: {}", recognizedText);
                                                    }
                                                } catch (Exception e) {
                                                    log.error("更新语音提问记录时发生异常，问题: {}", recognizedText, e);
                                                }
                                            })
                                            .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
                                            .subscribe();
                                        })
                                        .map(audioBytes -> new InteractionResult(
                                                recognizedText,
                                                aiResponse,
                                                Base64.getEncoder().encodeToString(audioBytes)
                                        ));
                            });
                })
                .doOnSuccess(result -> log.info("学生打断处理完成"))
                .doOnError(error -> log.error("处理学生打断时发生错误", error));
    }

    /**
     * 处理用户打断（兼容旧接口）
     */
    public Mono<InteractionResult> handleUserInterruption(byte[] audioData) {
        return handleStudentInterruption(audioData);
    }

    /**
     * 处理分段打断
     */
    public Mono<InteractionResult> handleSegmentedInterruption(byte[] audioData) {
        return handleStudentInterruption(audioData);
    }

    /**
     * 处理文本打断
     */
    public Mono<String> handleTextInterruption(String courseId, String question, int currentSegment, String segmentText) {
        log.info("AI教师服务：处理文本打断，会话ID: {}, 问题: {}", courseId, question);
        SESSION__ID = courseId;
        return aiModelService.getAIResponse(question)
                .doOnNext(answer -> log.info("AI回答: {}", answer))
                .doOnNext(answer -> {
                    // 异步保存用户文本提问和AI回答到数据库
                    Mono.fromRunnable(() -> {
                        try {
                            boolean saved = studentQuestionService.saveTextQuestion(
                                courseId, // 会话ID
                                currentSegment, // 当前片段索引
                                question, // 用户提问文本
                                answer // AI回答文本
                            );
                            if (saved) {
                                log.debug("文本提问已保存到数据库，会话ID: {}, 片段: {}, 问题: {}",
                                        courseId, currentSegment, question);
                            } else {
                                log.warn("文本提问保存到数据库失败，会话ID: {}, 问题: {}", courseId, question);
                            }
                        } catch (Exception e) {
                            log.error("保存文本提问到数据库时发生异常，会话ID: {}, 问题: {}", courseId, question, e);
                        }
                    })
                    .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
                    .subscribe();
                })
                .doOnError(error -> log.error("处理文本打断失败", error));
    }

    /**
     * 合成回答音频
     */
    public Mono<byte[]> synthesizeAnswerAudio(String answerText) {
        log.info("AI教师服务：合成回答音频，文本长度: {} 字符", answerText.length());

        return ttsService.synthesizeSpeech(answerText)
                .doOnSuccess(audioData -> log.info("回答音频合成完成，大小: {} bytes", audioData.length))
                .doOnError(error -> log.error("合成回答音频失败", error));
    }

    /**
     * 获取片段音频
     */
    public Mono<byte[]> getSegmentAudio(String courseId, int segmentIndex) {
        log.info("AI教师服务：获取片段音频，会话ID: {}, 片段索引: {}", courseId, segmentIndex);
        SESSION__ID = courseId;

        return playbackStateManager.getSegment(courseId, segmentIndex)
                .map(SpeechSegment::getAudioData)
                .doOnSuccess(audioData -> log.debug("获取片段音频成功，大小: {} bytes", audioData.length))
                .doOnError(error -> log.error("获取片段音频失败，会话ID: {}, 片段: {}", courseId, segmentIndex, error));
    }

    /**
     * 停止课程
     */
    public Mono<Void> stopLesson() {
        log.info("AI教师服务：停止课程");
        return Mono.empty();
    }

    /**
     * 从文本创建分段课程
     */
    private Mono<SegmentedLessonResult> createSegmentedLessonFromText(String originalText, String processedText) {
        log.info("开始创建分段语音课程，原始文本长度: {} 字符，处理后文本长度: {} 字符",
                originalText.length(), processedText.length());

        return segmentedSpeechService.createSegmentedSpeech(processedText)
                .map(speechResult -> {
                    // 转换为SegmentedLessonResult
                    return SegmentedLessonResult.builder()
                            .courseId(speechResult.getCourseId())
                            .totalSegments(speechResult.getTotalSegments())
                            .segmentTexts(speechResult.getSegmentTexts())
                            .firstSegmentAudio("")
                            .isReady(speechResult.isComplete())
                            .createdAt(speechResult.getCreatedAt())
                            .originalText(originalText)
                            .polishedText(processedText)
                            .estimatedDuration(speechResult.getTotalDuration())
                            .status(speechResult.getStatus())
                            .completedSegments(speechResult.getCompletedSegments())
                            .lessonTitle(generateLessonTitle(originalText))
                            .build();
                })
                .doOnSuccess(result -> log.info("分段语音课程创建完成，会话ID: {}, 总片段数: {}",
                        result.getCourseId(), result.getTotalSegments()))
                .doOnError(error -> log.error("创建分段课程失败", error));
    }

    /**
     * 生成课程标题
     */
    private String generateLessonTitle(String originalText) {
        if (originalText.length() > 20) {
            return originalText.substring(0, 20) + "...";
        }
        return originalText;
    }

    /**
     * 从指定片段开始播放
     */
    public Flux<byte[]> playFromSegment(String courseId, int segmentIndex) {
        log.info("AI教师服务：从片段 {} 开始播放，会话ID: {}", segmentIndex, courseId);

        return playbackStateManager.savePlaybackPosition(courseId, segmentIndex)
                .then(updatePlaybackStatus(courseId, PlaybackStatus.PLAYING))
                .thenMany(playbackStateManager.getSegmentsFrom(courseId, segmentIndex))
                .map(SpeechSegment::getAudioData)
                .doOnNext(audioData -> log.debug("播放片段音频，大小: {} bytes", audioData.length))
                .doOnComplete(() -> {
                    log.info("会话 {} 播放完成", courseId);
                    updatePlaybackStatus(courseId, PlaybackStatus.COMPLETED).subscribe();
                })
                .doOnError(error -> {
                    log.error("播放过程中发生错误，会话ID: {}", courseId, error);
                    updatePlaybackStatus(courseId, PlaybackStatus.ERROR).subscribe();
                });
    }

    /**
     * 暂停播放
     */
    public Mono<Void> pausePlayback(String courseId, int currentSegmentIndex) {
        log.info("AI教师服务：暂停播放，会话ID: {}, 当前片段: {}", courseId, currentSegmentIndex);

        return playbackStateManager.savePlaybackPosition(courseId, currentSegmentIndex)
                .then(updatePlaybackStatus(courseId, PlaybackStatus.PAUSED))
                .doOnSuccess(unused -> log.info("播放已暂停，会话ID: {}", courseId))
                .doOnError(error -> log.error("暂停播放失败，会话ID: {}", courseId, error));
    }

    /**
     * 恢复播放
     */
    public Mono<Void> resumePlayback(String courseId) {
        log.info("AI教师服务：恢复播放，会话ID: {}", courseId);

        return updatePlaybackStatus(courseId, PlaybackStatus.PLAYING)
                .doOnSuccess(unused -> log.info("播放已恢复，会话ID: {}", courseId))
                .doOnError(error -> log.error("恢复播放失败，会话ID: {}", courseId, error));
    }

    /**
     * 停止播放
     */
    public Mono<Void> stopPlayback(String courseId) {
        log.info("AI教师服务：停止播放，会话ID: {}", courseId);

        return updatePlaybackStatus(courseId, PlaybackStatus.STOPPED)
                .doOnSuccess(unused -> log.info("播放已停止，会话ID: {}", courseId))
                .doOnError(error -> log.error("停止播放失败，会话ID: {}", courseId, error));
    }

    /**
     * 跳转到指定片段
     */
    public Mono<Void> seekToSegment(String courseId, int segmentIndex) {
        log.info("AI教师服务：跳转到片段 {}，会话ID: {}", segmentIndex, courseId);

        return playbackStateManager.savePlaybackPosition(courseId, segmentIndex)
                .doOnSuccess(unused -> log.info("已跳转到片段 {}，会话ID: {}", segmentIndex, courseId))
                .doOnError(error -> log.error("跳转片段失败，会话ID: {}, 片段: {}", courseId, segmentIndex, error));
    }

    /**
     * 获取播放进度
     */
    public Mono<PlaybackProgress> getPlaybackProgress(String courseId) {
        return playbackStateManager.getPlaybackProgress(courseId)
                .doOnSuccess(progress -> log.debug("获取播放进度成功，会话ID: {}, 当前片段: {}",
                        courseId, progress.getCurrentSegment()))
                .doOnError(error -> log.error("获取播放进度失败，会话ID: {}", courseId, error));
    }

    /**
     * 更新播放状态
     */
    private Mono<Void> updatePlaybackStatus(String courseId, PlaybackStatus status) {
        return playbackStateManager.getPlaybackState(courseId)
                .flatMap(playbackState -> {
                    playbackState.setStatus(status);
                    playbackState.updateLastUpdated();
                    return playbackStateManager.savePlaybackState(playbackState);
                })
                .doOnSuccess(unused -> log.debug("播放状态已更新，会话ID: {}, 状态: {}", courseId, status))
                .doOnError(error -> log.error("更新播放状态失败，会话ID: {}, 状态: {}", courseId, status, error));
    }

    /**
     * 交互结果数据类
     */
    @Data
    public static class InteractionResult {
        private final String recognizedText;
        private final String aiResponse;
        private final String aiAudioData;
    }
}
