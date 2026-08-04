package com.treepeople.leapmindtts.websocket;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 百度云实时语音识别WebSocket处理器
 */
@Slf4j
@Component
public class BaiduRealTimeASRWebSocketHandler extends AbstractWebSocketHandler {

    private static final String TOKEN_URL = "https://aip.baidubce.com/oauth/2.0/token";
    private static final String REAL_TIME_ASR_URL = "https://vop.baidu.com/pro_api";
    
    @Value("${baidu.asr.api-key:uILerVfsDTL7Qiu5kM118mHL}")
    private String apiKey;
    
    @Value("${baidu.asr.secret-key:GVoAUBS9IUioq6dp0P7AgijUfMV6vPGq}")
    private String secretKey;
    
    @Value("${baidu.asr.app-id:6986539}")
    private String appId;
    
    // 速率限制配置
    private static final long MIN_REQUEST_INTERVAL_MS = 1000; // 增加到1秒间隔
    private static final int MAX_CONCURRENT_REQUESTS = 1; // 降低到1个并发请求
    private final AtomicLong lastRequestTime = new AtomicLong(0);
    private final AtomicLong activeRequests = new AtomicLong(0);
    
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private String accessToken;
    private long tokenExpireTime = 0;
    
    // 存储每个会话的状态
    private final Map<String, SessionState> sessionStates = new ConcurrentHashMap<>();
    
    // 用于控制处理频率的变量
    private final Map<String, Long> lastProcessTimes = new ConcurrentHashMap<>();
    private static final long PROCESS_INTERVAL_MS = 1000; // 增加到1秒处理间隔
    
    public BaiduRealTimeASRWebSocketHandler() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();
        this.objectMapper = new ObjectMapper();
    }
    
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        log.info("WebSocket连接已建立: {}", session.getId());
        sessionStates.put(session.getId(), new SessionState());
        
        // 获取访问令牌
        getAccessToken(session);
    }
    
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        log.info("WebSocket连接已关闭: {}, 状态: {}", session.getId(), status);
        sessionStates.remove(session.getId());
        lastProcessTimes.remove(session.getId());
    }
    
    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws Exception {
        ByteBuffer buffer = message.getPayload();
        byte[] audioData = new byte[buffer.remaining()];
        buffer.get(audioData);
        
        // 记录接收到的音频数据大小
        log.debug("接收到音频数据: {} bytes", audioData.length);
        
        SessionState state = sessionStates.get(session.getId());
        if (state == null) {
            log.warn("未找到会话状态: {}", session.getId());
            return;
        }
        
        // 检查是否有访问令牌
        if (state.getAccessToken() == null) {
            log.warn("访问令牌未准备好，等待令牌...");
            session.sendMessage(new TextMessage("{\"status\":\"waiting_for_token\"}"));
            return;
        }
        
        // 检查音频数据是否为空
        if (audioData.length == 0) {
            log.warn("接收到空的音频数据");
            return;
        }
        
        // 速率限制 - 使用与BaiduSpeechClient相同的逻辑
        if (!shouldProcessRequest(session.getId())) {
            log.debug("Rate limiting: skipping request for session {}", session.getId());
            return;
        }
        
        // 处理音频数据
        processAudioData(session, audioData, state);
        lastProcessTimes.put(session.getId(), System.currentTimeMillis());
    }
    
    private boolean shouldProcessRequest(String courseId) {
        // 检查并发请求数量
        if (activeRequests.get() >= MAX_CONCURRENT_REQUESTS) {
            log.debug("Too many concurrent requests, skipping this audio chunk");
            return false;
        }
        
        // 检查全局请求间隔
        long currentTime = System.currentTimeMillis();
        long lastTime = lastRequestTime.get();
        long timeDiff = currentTime - lastTime;
        
        if (timeDiff < MIN_REQUEST_INTERVAL_MS) {
            return false;
        }
        
        // 检查会话特定的处理间隔
        Long lastProcessTime = lastProcessTimes.get(courseId);
        if (lastProcessTime != null && currentTime - lastProcessTime < PROCESS_INTERVAL_MS) {
            return false;
        }
        
        return true;
    }
    
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        log.debug("接收到文本消息: {}", payload);
        
        // 如果是停止信号
        if ("STOP".equals(payload)) {
            SessionState state = sessionStates.get(session.getId());
            if (state != null) {
                state.setLastFrame(true);
                log.info("收到停止信号，设置最后一帧标志");
            }
        }
    }
    
    /**
     * 获取百度云访问令牌
     */
    private void getAccessToken(WebSocketSession session) {
        // 检查缓存的令牌是否有效
        if (accessToken != null && System.currentTimeMillis() < tokenExpireTime) {
            SessionState state = sessionStates.get(session.getId());
            if (state != null) {
                state.setAccessToken(accessToken);
                try {
                    session.sendMessage(new TextMessage("{\"status\":\"token_ready\"}"));
                } catch (IOException e) {
                    log.error("发送令牌就绪消息失败", e);
                }
            }
            return;
        }
        
        log.info("获取百度云访问令牌");
        
        String url = TOKEN_URL + "?client_id=" + apiKey + 
                    "&client_secret=" + secretKey + 
                    "&grant_type=client_credentials";
        
        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(MediaType.parse("application/json"), ""))
                .build();
        
        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                log.error("获取访问令牌失败", e);
                try {
                    session.sendMessage(new TextMessage("{\"error\":\"获取访问令牌失败\"}"));
                } catch (IOException ex) {
                    log.error("发送错误消息失败", ex);
                }
            }
            
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try (response) {
                    if (!response.isSuccessful()) {
                        log.error("获取访问令牌失败: {}", response.code());
                        session.sendMessage(new TextMessage("{\"error\":\"获取访问令牌失败\"}"));
                        return;
                    }
                    
                    String responseBody = response.body().string();
                    log.debug("Token响应: {}", responseBody);
                    
                    TokenResponse tokenResponse = objectMapper.readValue(responseBody, TokenResponse.class);
                    
                    if (tokenResponse.getAccessToken() == null) {
                        log.error("获取访问令牌失败: {}", tokenResponse.getError());
                        session.sendMessage(new TextMessage("{\"error\":\"获取访问令牌失败\"}"));
                        return;
                    }
                    
                    // 设置访问令牌和过期时间
                    accessToken = tokenResponse.getAccessToken();
                    tokenExpireTime = System.currentTimeMillis() + (tokenResponse.getExpiresIn() - 300) * 1000L;
                    
                    SessionState state = sessionStates.get(session.getId());
                    if (state != null) {
                        state.setAccessToken(accessToken);
                    }
                    
                    log.info("成功获取访问令牌");
                    session.sendMessage(new TextMessage("{\"status\":\"token_ready\"}"));
                } catch (Exception e) {
                    log.error("处理访问令牌响应失败", e);
                }
            }
        });
    }
    
    /**
     * 处理音频数据
     */
    private void processAudioData(WebSocketSession session, byte[] audioData, SessionState state) {
        // 检查并发请求限制
        if (activeRequests.get() >= MAX_CONCURRENT_REQUESTS) {
            log.debug("Too many concurrent requests, skipping audio processing");
            return;
        }
        
        // 更新请求时间和计数
        if (!lastRequestTime.compareAndSet(lastRequestTime.get(), System.currentTimeMillis())) {
            log.debug("Failed to update request time, skipping this request");
            return;
        }
        
        activeRequests.incrementAndGet();
        
        try {
            // 验证音频数据
            if (audioData.length == 0) {
                log.warn("Audio data is empty, skipping request");
                return;
            }
            
            // 限制音频数据大小
            if (audioData.length > 1024 * 1024) { // 1MB限制
                log.warn("Audio data too large: {} bytes, truncating", audioData.length);
                byte[] truncatedData = new byte[1024 * 1024];
                System.arraycopy(audioData, 0, truncatedData, 0, truncatedData.length);
                audioData = truncatedData;
            }
            
            // 构建请求参数 - 统一参数格式
            Map<String, Object> params = new HashMap<>();
            params.put("format", "pcm");  // 统一使用PCM格式
            params.put("rate", 16000);    // 统一16kHz采样率
            params.put("channel", 1);     // 单声道
            params.put("cuid", generateUniqueCUID(session.getId())); // 使用唯一CUID
            params.put("token", state.getAccessToken());
            params.put("dev_pid", 1537);  // 普通话识别模型
            
            // 对音频数据进行Base64编码
            String audioBase64 = Base64.getEncoder().encodeToString(audioData);
            params.put("speech", audioBase64);
            params.put("len", audioData.length);
            
            // 记录音频数据大小
            log.debug("音频数据大小: {} bytes, Base64编码后大小: {} bytes", 
                    audioData.length, audioBase64.length());
            
            // 设置是否是第一帧和最后一帧
            if (state.isFirstFrame()) {
                params.put("first_frame", 1);
                state.setFirstFrame(false);
            } else {
                params.put("first_frame", 0);
            }
            
            if (state.isLastFrame()) {
                params.put("last_frame", 1);
            } else {
                params.put("last_frame", 0);
            }
            
            String requestJson = objectMapper.writeValueAsString(params);
            
            Request request = new Request.Builder()
                    .url(REAL_TIME_ASR_URL)
                    .post(RequestBody.create(MediaType.parse("application/json"), requestJson))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("Accept", "application/json")
                    .build();
            
            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    log.error("实时语音识别请求失败", e);
                    try {
                        session.sendMessage(new TextMessage("{\"error\":\"" + e.getMessage() + "\"}"));
                    } catch (IOException ex) {
                        log.error("发送WebSocket消息失败", ex);
                    }
                }
                
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try (response) {
                        String responseBody = response.body().string();
                        log.debug("百度云实时ASR响应: {}", responseBody);
                        
                        if (!response.isSuccessful()) {
                            log.error("实时语音识别请求失败: {}, 响应内容: {}", response.code(), responseBody);
                            session.sendMessage(new TextMessage("{\"error\":\"请求失败，状态码: " + response.code() + "\"}"));
                            return;
                        }
                        
                        AsrResponse asrResponse = objectMapper.readValue(responseBody, AsrResponse.class);
                        
                        if (asrResponse.getErrNo() == 0) {
                            // 成功识别
                            String result = "";
                            if (asrResponse.getResult() != null && !asrResponse.getResult().isEmpty()) {
                                result = asrResponse.getResult();
                            }
                            
                            log.info("实时语音识别结果: {}", result);
                            
                            // 构建响应JSON
                            Map<String, Object> responseMap = new HashMap<>();
                            responseMap.put("success", true);
                            responseMap.put("text", result);
                            responseMap.put("isComplete", state.isLastFrame());
                            
                            String responseJson = objectMapper.writeValueAsString(responseMap);
                            log.debug("发送WebSocket响应: {}", responseJson);
                            session.sendMessage(new TextMessage(responseJson));
                        } else {
                            // 处理错误
                            int errorCode = asrResponse.getErrNo();
                            String errorMsg = asrResponse.getErrMsg();
                            log.error("实时语音识别错误: {}: {}", errorCode, errorMsg);
                            
                            handleBaiduError(session, errorCode, errorMsg);
                        }
                        
                    } catch (Exception e) {
                        log.error("处理响应失败", e);
                        try {
                            session.sendMessage(new TextMessage("{\"error\":\"处理响应失败\"}"));
                        } catch (IOException ex) {
                            log.error("发送错误消息失败", ex);
                        }
                    }
                }
            });
        } catch (Exception e) {
            log.error("处理音频数据失败", e);
            try {
                session.sendMessage(new TextMessage("{\"error\":\"" + e.getMessage() + "\"}"));
            } catch (IOException ex) {
                log.error("发送WebSocket消息失败", ex);
            }
        } finally {
            activeRequests.decrementAndGet();
        }
    }
    
    private void handleBaiduError(WebSocketSession session, int errorCode, String errorMsg) {
        try {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("errorCode", errorCode);
            errorResponse.put("errorMsg", errorMsg);
            
            switch (errorCode) {
                case 3300:
                    log.warn("Speech format error: {}", errorMsg);
                    errorResponse.put("suggestion", "音频格式错误，请检查音频参数");
                    break;
                case 3304:
                    log.warn("QPS limit exceeded: {}", errorMsg);
                    errorResponse.put("suggestion", "请求过于频繁，请稍后再试");
                    break;
                case 6:
                    log.error("No permission: {}", errorMsg);
                    errorResponse.put("suggestion", "没有权限，请检查API密钥");
                    // 重新获取token
                    getAccessToken(session);
                    break;
                default:
                    log.error("Unhandled error: {}: {}", errorCode, errorMsg);
                    errorResponse.put("suggestion", "未知错误，请联系技术支持");
            }
            
            String responseJson = objectMapper.writeValueAsString(errorResponse);
            session.sendMessage(new TextMessage(responseJson));
        } catch (Exception e) {
            log.error("处理错误响应失败", e);
        }
    }
    
    private String generateUniqueCUID(String courseId) {
        return "websocket_" + courseId + "_" + System.currentTimeMillis();
    }
    
    /**
     * 会话状态类
     */
    @Data
    private static class SessionState {
        private String accessToken;
        private boolean firstFrame = true;
        private boolean lastFrame = false;
    }
    
    @Data
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    private static class TokenResponse {
        @JsonProperty("access_token")
        private String accessToken;
        @JsonProperty("expires_in")
        private Integer expiresIn;
        @JsonProperty("refresh_token")
        private String refreshToken;
        @JsonProperty("session_key")
        private String sessionKey;
        @JsonProperty("scope")
        private String scope;
        private String error;
    }
    
    @Data
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    private static class AsrResponse {
        @JsonProperty("err_no")
        private Integer errNo;
        @JsonProperty("err_msg")
        private String errMsg;
        private String result;
        private String sn;
    }
}