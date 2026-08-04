package com.treepeople.leapmindtts.service.lesson;

import com.treepeople.leapmindtts.config.TextPolishingProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @ Package：com.treepeople.leapmindtts.service.Impl
 * @ Project：leapmind-tts
 * @ Description:
 * @ Date：2025/7/14  22:33
 */
@Service
@Slf4j
public class AIModelService {
    // 阿里云百炼平台API端点
    private static final String AI_API_URL = "https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation";

    @Value("${ai.api.key}")
    private String apiKey;

    @Value("${ai.prompt}")
    private String aiPrompt;

    private final WebClient webClient;
    private final TextPolishingProperties textPolishingProperties;

    public AIModelService(WebClient.Builder webClientBuilder, TextPolishingProperties textPolishingProperties) {
        this.webClient = webClientBuilder.build();
        this.textPolishingProperties = textPolishingProperties;
    }

    public Mono<String> getAIResponse(String userInput) {
        // 检查输入参数
        if (userInput == null || userInput.trim().isEmpty()) {
            log.warn("用户输入为空，返回默认回复");
            return Mono.just("抱歉，我没有听清楚您的问题，请再说一遍。");
        }

        // 检查API Key配置
        if (apiKey == null || apiKey.trim().isEmpty()) {
            log.error("AI API Key未配置");
            return Mono.just("抱歉，AI服务暂时不可用。");
        }

        log.info("发送AI请求: userInput={}", userInput);

        // 构建阿里云百炼平台的请求格式
        DashScopeRequest request = new DashScopeRequest();
        request.setModel("qwen-turbo");


        DashScopeRequest.Input input = new DashScopeRequest.Input();
        input.addMessage("user", userInput, aiPrompt);
        request.setInput(input);

        DashScopeRequest.Parameters parameters = new DashScopeRequest.Parameters();
        parameters.setResult_format("text");
        request.setParameters(parameters);

        log.debug("AI请求JSON: {}", request);

        return webClient.post()
                .uri(AI_API_URL)
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(DashScopeResponse.class)
                .map(response -> {
                    if (response != null && response.getOutput() != null && response.getOutput().getText() != null) {
                        String content = response.getOutput().getText();
                        log.info("AI响应成功: {}", content.substring(0, Math.min(50, content.length())) + "...");
                        return content;
                    } else {
                        log.warn("AI响应格式异常: {}", response);
                        return "抱歉，我现在无法回答您的问题。";
                    }
                })
                .doOnError(error -> {
                    log.error("AI请求失败: {}", error.getMessage());
                    if (error instanceof org.springframework.web.reactive.function.client.WebClientResponseException) {
                        var webError = (org.springframework.web.reactive.function.client.WebClientResponseException) error;
                        log.error("HTTP状态码: {}, 响应体: {}",
                                webError.getStatusCode(),
                                webError.getResponseBodyAsString());
                    }
                })
                .onErrorReturn("抱歉，AI服务暂时不可用，请稍后再试。");
    }

    // 测试方法，不是有讲课提示词
    public Mono<String> getAIResponseNoPrompt(String userInput) {
        // 检查输入参数
        if (userInput == null || userInput.trim().isEmpty()) {
            log.warn("用户输入为空，返回默认回复");
            return Mono.just("抱歉，我没有听清楚您的问题，请再说一遍。");
        }

        // 检查API Key配置
        if (apiKey == null || apiKey.trim().isEmpty()) {
            log.error("AI API Key未配置");
            return Mono.just("抱歉，AI服务暂时不可用。");
        }

        log.info("发送AI请求: userInput={}", userInput);

        // 构建阿里云百炼平台的请求格式
        DashScopeRequest request = new DashScopeRequest();
        request.setModel("qwen-turbo");


        DashScopeRequest.Input input = new DashScopeRequest.Input();
        input.addMessage("user", userInput);
        request.setInput(input);

        DashScopeRequest.Parameters parameters = new DashScopeRequest.Parameters();
        parameters.setResult_format("text");
        request.setParameters(parameters);

        log.debug("AI请求JSON: {}", request);

        return webClient.post()
                .uri(AI_API_URL)
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(DashScopeResponse.class)
                .map(response -> {
                    if (response != null && response.getOutput() != null && response.getOutput().getText() != null) {
                        String content = response.getOutput().getText();
                        log.info("AI响应成功: {}", content.substring(0, Math.min(50, content.length())) + "...");
                        return content;
                    } else {
                        log.warn("AI响应格式异常: {}", response);
                        return "抱歉，我现在无法回答您的问题。";
                    }
                })
                .doOnError(error -> {
                    log.error("AI请求失败: {}", error.getMessage());
                    if (error instanceof org.springframework.web.reactive.function.client.WebClientResponseException) {
                        var webError = (org.springframework.web.reactive.function.client.WebClientResponseException) error;
                        log.error("HTTP状态码: {}, 响应体: {}",
                                webError.getStatusCode(),
                                webError.getResponseBodyAsString());
                    }
                })
                .onErrorReturn("抱歉，AI服务暂时不可用，请稍后再试。");
    }

    /**
     * 文本润色方法
     * 使用通义千问API对文本进行润色处理，使其更适合教学场景
     *
     * @param originalText 原始文本
     * @return 润色后的文本，如果润色失败则返回原始文本
     */
    public Mono<String> polishText(String originalText) {
        // 检查润色功能是否启用
        if (!textPolishingProperties.isEnabled()) {
            log.debug("文本润色功能已禁用，返回原始文本");
            return Mono.just(originalText);
        }

        // 检查输入参数
        if (originalText == null) {
            log.warn("原始文本为null，返回空字符串");
            return Mono.just("");
        }

        if (originalText.trim().isEmpty()) {
            log.warn("原始文本为空，返回原文");
            return Mono.just(originalText);
        }

        // 检查文本长度
        if (originalText.length() < textPolishingProperties.getValidation().getMinTextLength()) {
            log.debug("文本长度小于最小限制，返回原文: length={}", originalText.length());
            return Mono.just(originalText);
        }

        // 截断过长的文本
        final String textToPolish;
        if (originalText.length() > textPolishingProperties.getValidation().getMaxTextLength()) {
            textToPolish = originalText.substring(0, textPolishingProperties.getValidation().getMaxTextLength());
            log.warn("文本长度超过最大限制，已截断: originalLength={}, truncatedLength={}",
                    originalText.length(), textToPolish.length());
        } else {
            textToPolish = originalText;
        }

        // 检查API Key配置
        if (apiKey == null || apiKey.trim().isEmpty()) {
            log.error("AI API Key未配置，返回原文");
            return Mono.just(originalText);
        }

        log.info("开始文本润色: originalLength={}", textToPolish.length());
        final long startTime = System.currentTimeMillis();

        return performPolishingRequest(textToPolish, textPolishingProperties.getPrompt())
                .timeout(Duration.ofSeconds(textPolishingProperties.getTimeout()))
                .retryWhen(Retry.backoff(textPolishingProperties.getMaxRetries(), Duration.ofMillis(1000))
                        .maxBackoff(Duration.ofSeconds(5))
                        .filter(this::isRetryableError))
                .map(polishedText -> {
                    // 强制长度约束：最多原文长度 + maxExtraChars
                    int maxPolishedLength = Math.max(0, originalText.length() + textPolishingProperties.getValidation().getMaxExtraChars());
                    if (polishedText != null && polishedText.length() > maxPolishedLength) {
                        String truncated = polishedText.substring(0, maxPolishedLength).trim();
                        // 尽量在句号或换行处收尾
                        int lastBreak = Math.max(Math.max(truncated.lastIndexOf('。'), truncated.lastIndexOf('\n')), Math.max(truncated.lastIndexOf('！'), truncated.lastIndexOf('？')));
                        if (lastBreak > maxPolishedLength / 2) {
                            truncated = truncated.substring(0, lastBreak + 1).trim();
                        }
                        return truncated;
                    }
                    return polishedText;
                })
                .doOnSuccess(polishedText -> {
                    long duration = System.currentTimeMillis() - startTime;
                    log.info("文本润色成功: originalLength={}, polishedLength={}, duration={}ms",
                            textToPolish.length(), polishedText.length(), duration);
                })
                .doOnError(error -> {
                    long duration = System.currentTimeMillis() - startTime;
                    log.warn("文本润色失败，使用原文: error={}, duration={}ms", error.getMessage(), duration);
                })
                .onErrorReturn(originalText);
    }

    /**
     * 执行润色请求
     */
    private Mono<String> performPolishingRequest(String textToPolish, String prompt) {
        // 构建润色请求
        DashScopeRequest request = buildPolishingRequest(textToPolish, prompt);

        log.debug("发送润色请求: textLength={}", textToPolish.length());

        return webClient.post()
                .uri(AI_API_URL)
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(DashScopeResponse.class)
                .map(response -> {
                    if (response != null && response.getOutput() != null && response.getOutput().getText() != null) {
                        String rawText = response.getOutput().getText().trim();
                        String polishedText = cleanPolishedText(rawText);
                        log.debug("润色响应成功: rawLength={}, cleanedLength={}", rawText.length(), polishedText.length());
                        return polishedText;
                    } else {
                        log.warn("润色响应格式异常: {}", response);
                        throw new RuntimeException("润色响应格式异常");
                    }
                })
                .doOnError(error -> {
                    if (error instanceof WebClientResponseException) {
                        var webError = (WebClientResponseException) error;
                        log.error("润色请求失败: statusCode={}, responseBody={}",
                                webError.getStatusCode(), webError.getResponseBodyAsString());
                    } else {
                        log.error("润色请求失败: {}", error.getMessage());
                    }
                });
    }

    /**
     * 构建润色请求
     */
    private DashScopeRequest buildPolishingRequest(String textToPolish, String prompt) {
        DashScopeRequest request = new DashScopeRequest();
        request.setModel("qwen-turbo");

        DashScopeRequest.Input input = new DashScopeRequest.Input();
        // 构建完整的润色请求内容（允许自定义prompt）
        String effectivePrompt = (prompt != null && !prompt.trim().isEmpty()) ? prompt : textPolishingProperties.getPrompt();
        String fullPrompt = effectivePrompt + "\n\n" + textToPolish;
        input.addMessage("user", fullPrompt);
        request.setInput(input);

        DashScopeRequest.Parameters parameters = new DashScopeRequest.Parameters();
        parameters.setResult_format("text");
        request.setParameters(parameters);

        return request;
    }

    /**
     * 使用自定义提示词的润色方法，可选指定最大输出字符数
     */
    public Mono<String> polishTextWithPrompt(String originalText, String customPrompt, Integer maxOutputChars) {
        if (!textPolishingProperties.isEnabled()) {
            log.debug("文本润色功能已禁用，返回原始文本");
            return Mono.just(originalText);
        }

        if (originalText == null) {
            log.warn("原始文本为null，返回空字符串");
            return Mono.just("");
        }
        if (originalText.trim().isEmpty()) {
            log.warn("原始文本为空，返回原文");
            return Mono.just(originalText);
        }

        //降级策略
        // 截断过长的文本（按配置）
        final String textToPolish;
        if (originalText.length() > textPolishingProperties.getValidation().getMaxTextLength()) {
            textToPolish = originalText.substring(0, textPolishingProperties.getValidation().getMaxTextLength());
            log.warn("文本长度超过最大限制，已截断: originalLength={}, truncatedLength={}",
                    originalText.length(), textToPolish.length());
        } else {
            textToPolish = originalText;
        }

        if (apiKey == null || apiKey.trim().isEmpty()) {
            log.error("AI API Key未配置，返回原文");
            return Mono.just(originalText);
        }

        log.info("开始文本润色(自定义提示): originalLength={}", textToPolish.length());
        final long startTime = System.currentTimeMillis();

        return performPolishingRequest(textToPolish, customPrompt)
                .timeout(Duration.ofSeconds(textPolishingProperties.getTimeout()))
                .retryWhen(Retry.backoff(textPolishingProperties.getMaxRetries(), Duration.ofMillis(1000))
                        .maxBackoff(Duration.ofSeconds(5))
                        .filter(this::isRetryableError))
                .map(polishedText -> {
                    // 先应用全局“原文+maxExtraChars”的保护
                    int defaultMax = Math.max(0, originalText.length() + textPolishingProperties.getValidation().getMaxExtraChars());
                    int cap = defaultMax;
                    // 若指定了绝对上限，取两者中更小的
                    if (maxOutputChars != null && maxOutputChars > 0) {
                        cap = Math.min(cap, maxOutputChars);
                    }
                    if (polishedText != null && polishedText.length() > cap) {
                        log.warn("润色文本超出字数限制，原文{}字，润色后{}字，上限{}字，将进行截断",
                                originalText.length(), polishedText.length(), cap);

                        String truncated = polishedText.substring(0, cap).trim();
                        int lastBreak = Math.max(Math.max(truncated.lastIndexOf('。'), truncated.lastIndexOf('\n')),
                                Math.max(truncated.lastIndexOf('！'), truncated.lastIndexOf('？')));
                        if (lastBreak > cap / 2) {
                            truncated = truncated.substring(0, lastBreak + 1).trim();
                            log.info("在句子结束符处截断，最终长度: {}字", truncated.length());
                        } else {
                            log.info("直接截断，最终长度: {}字", truncated.length());
                        }
                        return truncated;
                    }
                    return polishedText;
                })
                .doOnSuccess(polishedText -> {
                    long duration = System.currentTimeMillis() - startTime;
                    log.info("文本润色成功(自定义提示): originalLength={}, polishedLength={}, duration={}ms",
                            textToPolish.length(), polishedText.length(), duration);
                })
                .doOnError(error -> {
                    long duration = System.currentTimeMillis() - startTime;
                    log.warn("文本润色失败(自定义提示)，使用原文: error={}, duration={}ms", error.getMessage(), duration);
                })
                .onErrorReturn(originalText);
    }

    /**
     * 清理润色后的文本，去除不需要的引导语和说明文字
     */
    private String cleanPolishedText(String rawText) {
        if (rawText == null || rawText.trim().isEmpty()) {
            return rawText;
        }

        String cleanedText = rawText.trim();

        // 去除常见的引导语模式
        String[] unwantedPrefixes = {
            "当然可以，下面是",
            "好的，下面是",
            "以下是",
            "这是",
            "润色后的内容如下",
            "润色后的文本",
            "适合老师讲课的内容",
            "将这段文字润色后",
            "润色为适合老师讲课的内容"
        };

        // 检查并移除引导语
        for (String prefix : unwantedPrefixes) {
            if (cleanedText.toLowerCase().contains(prefix.toLowerCase())) {
                // 找到引导语后的冒号或换行符，从那里开始截取
                int colonIndex = cleanedText.indexOf("：");
                int colonIndex2 = cleanedText.indexOf(":");
                int newlineIndex = cleanedText.indexOf("\n");

                int startIndex = -1;
                if (colonIndex > 0) startIndex = colonIndex + 1;
                else if (colonIndex2 > 0) startIndex = colonIndex2 + 1;
                else if (newlineIndex > 0) startIndex = newlineIndex + 1;

                if (startIndex > 0 && startIndex < cleanedText.length()) {
                    cleanedText = cleanedText.substring(startIndex).trim();
                    break;
                }
            }
        }

        // 去除结尾的说明文字
        String[] unwantedSuffixes = {
            "如果你有更多内容需要润色，也可以继续发给我",
            "还有其他问题欢迎继续询问",
            "希望这个润色版本对你有帮助",
            "这样的表达更适合课堂教学",
            "需要我继续润色后续内容吗",
            "这样的表达方式更贴近课堂讲解",
            "语言也更生动易懂",
            "同时保留了专业术语的准确性",
            "并加入了适当的解释和过渡",
            "有助于学生更好地理解和吸收知识点"
        };

        for (String suffix : unwantedSuffixes) {
            if (cleanedText.toLowerCase().contains(suffix.toLowerCase())) {
                int index = cleanedText.toLowerCase().indexOf(suffix.toLowerCase());
                if (index > 0) {
                    // 查找前面的分隔符
                    String beforeSuffix = cleanedText.substring(0, index);
                    if (beforeSuffix.endsWith("---") || beforeSuffix.endsWith("。") || beforeSuffix.endsWith("！")) {
                        cleanedText = beforeSuffix.trim();
                        if (cleanedText.endsWith("---")) {
                            cleanedText = cleanedText.substring(0, cleanedText.length() - 3).trim();
                        }
                        break;
                    }
                }
            }
        }

        // 去除开头和结尾的分隔符
        cleanedText = cleanedText.replaceAll("^---\\s*\\n*", ""); // 去除开头的---
        cleanedText = cleanedText.replaceAll("\\n*\\s*---\\s*$", ""); // 去除结尾的---
        cleanedText = cleanedText.replaceAll("\\n*\\s*---\\s*\\n+", "\n\n"); // 去除中间的---分隔符

        // 去除markdown格式标记
        cleanedText = cleanedText.replaceAll("\\*\\*(.*?)\\*\\*", "$1"); // 去除加粗
        cleanedText = cleanedText.replaceAll("\\*(.*?)\\*", "$1"); // 去除斜体

        // 去除多余的换行符
        cleanedText = cleanedText.replaceAll("\\n{3,}", "\n\n"); // 将3个或更多换行符替换为2个

        // 去除包含特定模式的整个段落或句子
        String[] unwantedParagraphs = {
            "这样的表达方式更贴近课堂讲解",
            "需要我继续润色后续内容吗",
            "希望这个润色版本对你有帮助",
            "如果你有更多段落需要润色",
            "也可以继续发给我",
            "大家有没有兴趣继续了解呢",
            "我们可以从它和传统单体架构的区别开始讲起"
        };

        for (String unwantedParagraph : unwantedParagraphs) {
            // 使用正则表达式匹配包含这些内容的整个段落
            String pattern = ".*" + java.util.regex.Pattern.quote(unwantedParagraph) + ".*?(?=\\n\\n|$)";
            cleanedText = cleanedText.replaceAll(pattern, "");
        }

        // 去除结尾的问号句子（通常是AI添加的互动问题）
        cleanedText = cleanedText.replaceAll("，[^。！？]*？[？！]?$", "。");
        cleanedText = cleanedText.replaceAll("[^。！]*？[？！]?$", "");

        // 最终清理：去除首尾空白和多余换行
        cleanedText = cleanedText.trim();
        cleanedText = cleanedText.replaceAll("^\\n+", ""); // 去除开头的换行
        cleanedText = cleanedText.replaceAll("\\n+$", ""); // 去除结尾的换行

        log.debug("文本清理: 原长度={}, 清理后长度={}", rawText.length(), cleanedText.length());

        return cleanedText;
    }

    /**
     * 判断是否为可重试的错误
     */
    private boolean isRetryableError(Throwable error) {
        if (error instanceof WebClientResponseException) {
            WebClientResponseException webError = (WebClientResponseException) error;
            int statusCode = webError.getStatusCode().value();
            // 5xx服务器错误和429限流错误可以重试
            return statusCode >= 500 || statusCode == 429;
        }
        // 网络连接异常可以重试
        return error instanceof java.net.ConnectException ||
               error instanceof java.util.concurrent.TimeoutException;
    }

    // 阿里云百炼平台请求DTO
    @Data
    private static class DashScopeRequest {
        private String model;
        private Input input;
        private Parameters parameters;



        @Data
        public static class Input {
            private List<Message> messages = new ArrayList<>();

            public void addMessage(String role, String content, String prompt) {
                // 将提示词与用户输入内容拼接
                String fullContent = (prompt != null && !prompt.trim().isEmpty()) ? prompt + content : content;
                messages.add(new Message(role, fullContent));
            }

            public void addMessage(String role, String content) {
                messages.add(new Message(role, content));
            }

            @Data
            @AllArgsConstructor
            public static class Message {
                private String role;
                private String content;

            }
        }

        @Data
        public static class Parameters {
            private String result_format;
        }
    }

    // 阿里云百炼平台响应DTO
    @Data
    private static class DashScopeResponse {
        private Output output;
        private Usage usage;

        @Data
        public static class Output {
            private String text;
        }

        @Data
        public static class Usage {
            private Integer output_tokens;
            private Integer input_tokens;
        }
    }
}
