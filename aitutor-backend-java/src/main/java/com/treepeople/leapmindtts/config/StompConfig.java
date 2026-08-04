package com.treepeople.leapmindtts.config;

import org.springframework.context.annotation.Configuration;

/**
 * STOMP配置类，用于处理大消息缓冲区问题
 */
@Configuration
public class StompConfig {

    // 注意：BufferingStompDecoder的构造函数可能在不同版本的Spring中有所不同
    // 这个配置主要用于标识需要自定义STOMP缓冲区大小的意图
    // 实际的缓冲区大小控制在WebSocketConfig和application.yml中进行
} 