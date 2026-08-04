package com.treepeople.leapmindtts.service.lesson;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @ Package：com.treepeople.leapmindtts.service.Impl
 * @ Project：leapmind-tts
 * @ Description:
 * @ Date：2025/7/14  22:33
 */
@Service
@Slf4j
public class TextToSpeechService {
    // 阿里云实时语音合成API - 使用正确的端点
    private static final String TTS_API_URL = "https://nls-gateway-cn-shanghai.aliyuncs.com/stream/v1/tts";

    @Value("${tts.app.key:}")
    private String appKey;

    @Value("${tts.voice:xiaoyun}")
    private String voiceName;

    private final WebClient webClient;
    private final AliyunTokenService tokenService;

    public TextToSpeechService(WebClient.Builder webClientBuilder, AliyunTokenService tokenService) {
        this.webClient = webClientBuilder.build();
        this.tokenService = tokenService;
    }

    public Mono<byte[]> synthesizeSpeech(String text) {
        // 检查输入文本是否为空
        if (text == null || text.trim().isEmpty()) {
            log.warn("文本为空，跳过语音合成");
            return Mono.just(new byte[0]); // 返回空音频数据
        }

        // 阿里云TTS短文本合成有300字符的限制，我们需要智能处理长文本
        final String originalText = text;

        // 如果文本长度在限制内，直接合成
        if (originalText.length() <= 290) {
            return synthesizeSingleSegment(originalText);
        }


        // 长文本截断
        final String processedText = smartTruncateText(originalText, 290);
        log.warn("文本过长（{} > 290），已智能截断. 原长度: {}, 截断后长度: {}",
                originalText.length(), processedText.length());
        log.debug("截断后文本: {}", processedText);

        return synthesizeSingleSegment(processedText);
    }

    /**
     * 合成单个文本段落
     *
     * @param text 要合成的文本（长度应在限制内）
     * @return 音频数据
     */
    private Mono<byte[]> synthesizeSingleSegment(String text) {
        log.info("发送TTS请求: text={}, voice={}, format={}", text, voiceName, "wav");

        // 动态获取Token并发送请求
        return tokenService.getToken()
                .flatMap(token -> {
                    // 根据阿里云TTS API官方文档构建请求格式
                    // 使用最简单的JSON格式，确保兼容性
                    Map<String, Object> request = new LinkedHashMap<>();

                    // 必需字段
                    request.put("text", text);
                    request.put("voice", voiceName);
                    request.put("format", "wav");

                    // 添加一些可选参数来提高兼容性
                    request.put("sample_rate", 16000);
                    request.put("volume", 50);

                    if (appKey != null && !appKey.trim().isEmpty()) {
                        request.put("appkey", appKey);
                        log.debug("使用appkey: {}", appKey);
                    }

                    log.debug("最终TTS请求JSON: {}", request);

                    return webClient.post()
                            .uri(TTS_API_URL)
                            .header("X-NLS-Token", token)
                            .header("Content-Type", "application/json")
                            .header("Accept", "audio/wav")
                            .bodyValue(request)
                            .retrieve()
                            .bodyToMono(byte[].class)
                            // 增加重试机制，应对网络不稳定和临时服务问题
                            .retry(3) // 最多重试3次
                            .timeout(java.time.Duration.ofSeconds(120)) // 单次请求最大等待120秒
                            .doOnSuccess(audioData -> {
                                log.info("TTS语音合成成功，音频数据大小: {} bytes", audioData.length);
                            })
                            .onErrorMap(error -> {
                                log.error("TTS请求失败", error);

                                // 处理不同类型的错误，提供更详细的错误信息
                                if (error instanceof org.springframework.web.reactive.function.client.WebClientResponseException) {
                                    var webError = (org.springframework.web.reactive.function.client.WebClientResponseException) error;
                                    String errorBody = webError.getResponseBodyAsString();
                                    log.error("TTS API错误响应 [{}]: {}", webError.getStatusCode(), errorBody);

                                    // 根据不同的HTTP状态码提供更具体的错误信息
                                    if (webError.getStatusCode().value() == 429) {
                                        return new RuntimeException("TTS服务请求过于频繁，请稍后重试");
                                    } else if (webError.getStatusCode().value() == 401) {
                                        return new RuntimeException("TTS服务认证失败，请检查Token配置");
                                    } else if (webError.getStatusCode().value() >= 500) {
                                        return new RuntimeException("TTS服务暂时不可用，请稍后重试");
                                    } else {
                                        return new RuntimeException("TTS合成失败: " + webError.getStatusCode() + " - " + errorBody);
                                    }
                                } else if (error instanceof java.util.concurrent.TimeoutException ||
                                          error.getMessage().contains("timeout") ||
                                          error.getMessage().contains("handshake timed out")) {
                                    log.error("TTS请求超时: {}", error.getMessage());
                                    return new RuntimeException("TTS服务连接超时，可能是网络问题，请检查网络连接后重试");
                                } else if (error.getMessage().contains("Connection refused") ||
                                          error.getMessage().contains("UnknownHostException")) {
                                    log.error("TTS服务连接失败: {}", error.getMessage());
                                    return new RuntimeException("无法连接到TTS服务，请检查网络连接");
                                } else {
                                    return new RuntimeException("TTS合成失败: " + error.getMessage());
                                }
                            });
                });
    }


    /**
     * 智能截断文本
     * 尽量在句子结束符处截断，保持文本的完整性和可读性
     *
     * @param text 原始文本
     * @param maxLength 最大长度
     * @return 截断后的文本
     */
    private String smartTruncateText(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }

        // 在最大长度范围内查找最后一个句子结束符
        String truncated = text.substring(0, maxLength);

        // 按优先级查找句子结束符：句号 > 感叹号 > 问号 > 分号 > 逗号
        int[] endPositions = {
            truncated.lastIndexOf('。'),
            truncated.lastIndexOf('！'),
            truncated.lastIndexOf('？'),
            truncated.lastIndexOf(';'),
            truncated.lastIndexOf('；'),
            truncated.lastIndexOf(','),
            truncated.lastIndexOf('，')
        };

        // 找到最靠后的句子结束符
        int bestEndPosition = -1;
        for (int pos : endPositions) {
            if (pos > bestEndPosition) {
                bestEndPosition = pos;
            }
        }

        // 如果找到句子结束符，并且位置不太靠前（至少保留一半长度）
        if (bestEndPosition > maxLength / 2) {
            return text.substring(0, bestEndPosition + 1);
        }

        // 如果没有找到合适的句子结束符，尝试在空格处截断
        int lastSpacePosition = truncated.lastIndexOf(' ');
        if (lastSpacePosition > maxLength * 2 / 3) {
            return text.substring(0, lastSpacePosition);
        }

        // 最后的选择：直接截断并添加省略号
        return truncated.trim() + "...";
    }

}
