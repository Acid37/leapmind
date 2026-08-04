package com.treepeople.leapmindtts.service.lesson;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiTeacherBaiduAsrService {

    private final BaiduTokenService baiduTokenService;
    private final ObjectMapper objectMapper;
    private final WebClient webClient;

    @Value("${baidu.speech.cuid:ai-teacher-unique-id}") // 从配置读取，如果没有则使用默认值
    private String cuid;

    private static final String RECOGNITION_URL = "https://vop.baidu.com/server_api";
    private static final String DEV_PID = "1537"; // 使用普通话(支持简单的英文识别)，更适合日常对话

    public Mono<String> recognize(byte[] userAudioData) {
        if (userAudioData == null || userAudioData.length == 0) {
            log.warn("音频数据为空，跳过识别");
            return Mono.just("");
        }

        // 音频质量预检查
        if (userAudioData.length < 1024) { // 小于1KB的音频通常质量不佳
            log.warn("音频数据过小 ({} bytes)，可能质量不佳", userAudioData.length);
        }

        log.info("开始百度语音识别，音频大小: {} bytes ({:.1f}KB)",
                userAudioData.length, userAudioData.length / 1024.0);

        return baiduTokenService.getToken()
                .flatMap(token -> {
                    String speechBase64 = Base64.getEncoder().encodeToString(userAudioData);

                    Map<String, Object> requestBody = new HashMap<>();
                    requestBody.put("format", "wav");
                    requestBody.put("rate", 16000);
                    requestBody.put("channel", 1);
                    requestBody.put("cuid", cuid);
                    requestBody.put("token", token);
                    requestBody.put("dev_pid", DEV_PID);
                    requestBody.put("speech", speechBase64);
                    requestBody.put("len", userAudioData.length); // 添加音频长度参数

                    // 添加识别优化参数
                    Map<String, Object> params = new HashMap<>();
                    params.put("domain", "15362"); // 客服领域，提高对话识别准确性
                    params.put("ptt", 1); // 开启标点符号
                    params.put("pd", "talk"); // 对话场景
                    requestBody.put("params", params);
                    requestBody.put("len", userAudioData.length);

                    return webClient.post()
                            .uri(RECOGNITION_URL)
                            .header("Content-Type", "application/json")
                            .header("Accept", "application/json")
                            .bodyValue(requestBody)
                            .retrieve()
                            .bodyToMono(String.class)
                            .flatMap(responseBody -> {
                                try {
                                    JsonNode rootNode = objectMapper.readTree(responseBody);
                                    int errNo = rootNode.path("err_no").asInt();
                                    if (errNo == 0) {
                                        JsonNode resultNode = rootNode.path("result");
                                        if (resultNode.isArray() && resultNode.size() > 0) {
                                            String resultText = resultNode.get(0).asText();
                                            log.info("百度短语音识别成功: {}", resultText);
                                            return Mono.just(resultText);
                                        } else {
                                            log.warn("百度短语音识别结果为空，可能是音频质量不佳或无有效语音内容");
                                            log.debug("完整响应: {}", responseBody);
                                            return Mono.just(""); // 返回空字符串而不是错误
                                        }
                                    } else {
                                        String errMsg = rootNode.path("err_msg").asText();
                                        log.error("百度短语音识别API错误，错误码: {}, 错误信息: {}", errNo, errMsg);
                                        log.debug("完整响应: {}", responseBody);

                                        // 对于常见错误，返回友好提示而不是抛出异常
                                        switch (errNo) {
                                            case 3300:
                                                log.warn("输入参数不正确，可能是音频格式问题");
                                                return Mono.just("");
                                            case 3301:
                                                log.warn("音频质量过差，无法识别");
                                                return Mono.just("");
                                            case 3302:
                                                log.warn("鉴权失败，请检查API配置");
                                                return Mono.error(new RuntimeException("ASR认证失败: " + errMsg));
                                            case 3303:
                                                log.warn("语音服务器后端问题");
                                                return Mono.just("");
                                            default:
                                                return Mono.error(new RuntimeException("ASR API Error: " + errMsg));
                                        }
                                    }
                                } catch (JsonProcessingException e) {
                                    log.error("解析百度短语音识别响应失败, 响应体: {}", responseBody, e);
                                    return Mono.error(new RuntimeException("Failed to parse ASR response", e));
                                }
                            });
                })
                .doOnError(error -> log.error("百度短语音识别流程发生错误", error));
    }
}
