package com.treepeople.leapmindtts.config;

import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class OkHttpConfig {

    /**
     * 配置OKHttp客户端
     */
    @Bean
    public OkHttpClient okHttpClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS) // 连接超时
                .readTimeout(15, TimeUnit.SECONDS)    // 读取超时
                .writeTimeout(15, TimeUnit.SECONDS)   // 写入超时
                .connectionPool(new ConnectionPool(10, 30, TimeUnit.SECONDS)) // 连接池：5个空闲连接，30秒超时
                .retryOnConnectionFailure(false) // 连接失败是否重试（短信发送不建议重试，避免重复发送）
                .build();
    }
}