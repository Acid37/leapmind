package com.treepeople.leapmindtts.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * WebClient配置类
 */
@Configuration
public class WebClientConfig {

    @Bean
    public WebClient.Builder webClientBuilder() {
        // 针对TTS服务的网络延迟和处理时间，增加超时配置
        // 阿里云TTS服务在网络不稳定时可能需要更长的连接和处理时间
        HttpClient httpClient = HttpClient.create()
                // 连接超时：从10秒增加到20秒，应对网络握手延迟
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 20000)
                // 响应超时：从30秒增加到90秒，应对TTS合成处理时间
                .responseTimeout(Duration.ofSeconds(90))
                // 启用连接池以提高性能和稳定性
                .keepAlive(true)
                .doOnConnected(conn ->
                        // 读超时：增加到90秒，等待TTS服务响应
                        conn.addHandlerLast(new ReadTimeoutHandler(90, TimeUnit.SECONDS))
                                // 写超时：增加到60秒，应对大文本上传
                                .addHandlerLast(new WriteTimeoutHandler(60, TimeUnit.SECONDS)));

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                // 增加内存缓冲区大小，支持更大的音频文件
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(20 * 1024 * 1024)); // 20MB
    }
    @Bean
    public WebClient webClient(WebClient.Builder webClientBuilder) {
        return webClientBuilder.build();
    }
}