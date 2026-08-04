package com.treepeople.leapmindtts.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");
        config.setApplicationDestinationPrefixes("/app");
        
        // 设置消息缓存限制
        config.setCacheLimit(1024);
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 使用setAllowedOriginPatterns代替setAllowedOrigins
        registry.addEndpoint("/speech-websocket")
                .setAllowedOriginPatterns("*")
                .withSockJS()
                .setStreamBytesLimit(512 * 1024) // 设置流字节限制为512KB
                .setHttpMessageCacheSize(1000)   // 设置HTTP消息缓存大小
                .setDisconnectDelay(30 * 1000)   // 设置断开连接延迟为30秒
                .setClientLibraryUrl("https://cdn.jsdelivr.net/npm/sockjs-client@1.6.1/dist/sockjs.min.js");
    }
    
    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registry) {
        // 设置消息大小限制 - 增加到512KB以处理较大的音频数据
        registry.setMessageSizeLimit(512 * 1024); // 512KB
        
        // 设置发送缓冲区大小限制 - 增加到1MB
        registry.setSendBufferSizeLimit(1024 * 1024); // 1MB
        
        // 设置发送时间限制 - 增加到30秒
        registry.setSendTimeLimit(30 * 1000); // 30 seconds
        
        // 设置WebSocket会话的时间限制
        registry.setTimeToFirstMessage(30 * 1000); // 30 seconds
    }
    
    // STOMP缓冲区大小通过WebSocket传输配置和Tomcat配置来控制
}