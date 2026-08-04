package com.treepeople.leapmindtts.service.lesson;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * @ Package：com.treepeople.leapmindtts.service
 * @ Project：leapmind-tts - 语音对话
 * @ Description: 语音对话服务
 * @ Date：2025/8/8
 */

@Service
@Slf4j
@RequiredArgsConstructor
public class VoiceChatService {

    private final AIModelService aiModelService;
    private final TextToSpeechService ttsService;

    /**
     * 处理语音对话
     * 调用AI服务获取回答，不涉及讲课相关的上下文处理
     */
    public Mono<String> processVoiceChat(String courseId, String question) {
        log.info("语音对话服务：处理对话请求，会话ID: {}, 问题: {}", courseId, question);

        return aiModelService.getAIResponse(question)
                .doOnNext(answer -> log.info("AI回答: {}", answer))
                .doOnError(error -> log.error("处理语音对话失败，会话ID: {}, 问题: {}", courseId, question, error));
    }

    // 测试方法，不带提示词
    public Mono<String> processVoiceChatNoPrompt(String courseId, String question) {
        log.info("语音对话服务：处理对话请求，会话ID: {}, 问题: {}", courseId, question);

        return aiModelService.getAIResponseNoPrompt(question)
                .doOnNext(answer -> log.info("AI回答: {}", answer))
                .doOnError(error -> log.error("处理语音对话失败，会话ID: {}, 问题: {}", courseId, question, error));
    }

    /**
     * 合成语音音频
     * 将文本转换为语音
     */
    public Mono<byte[]> synthesizeVoiceAudio(String text) {
        log.info("语音对话服务：合成语音音频，文本长度: {} 字符", text.length());

        return ttsService.synthesizeSpeech(text)
                .doOnNext(audioData -> log.info("语音合成完成，音频大小: {} bytes", audioData.length))
                .doOnError(error -> log.error("语音合成失败，文本: {}", text, error));
    }
}
