package com.treepeople.leapmindtts.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.client.RestTemplate;
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
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    /**
     * 通用 WebClient.Builder（TTS、普通 HTTP 调用适用，超时中等）。
     * 标记 @Primary 避免与 streamingWebClientBuilder 注入歧义。
     */
    @Bean
    @Primary
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

    @Bean
    @Qualifier("contextCompressWebClient")
    public WebClient contextCompressWebClient() {
        // As per user spec: connect 2s, response 10s. The total timeout is controlled by .timeout() in the service.
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 2000) // 2 seconds
                .responseTimeout(Duration.ofSeconds(10)) // 10 seconds
                .doOnConnected(conn ->
                        conn.addHandlerLast(new ReadTimeoutHandler(10, TimeUnit.SECONDS))
                                .addHandlerLast(new WriteTimeoutHandler(10, TimeUnit.SECONDS)));

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    /**
     * [SSE流式] 备课生成专用 WebClient.Builder。
     * 设计要点：
     *   - 连接超时 30s（跨服务调用可接受）
     *   - 读超时 30 分钟：M5 备课三段管线（大纲+PPT+讲解词）极端情况下可超过 10 分钟
     *   - 禁用代理级缓冲（X-Accel-Buffering=no）由 HTTP 头在请求侧设置
     *   - 响应超时 Duration 级限制放宽，让应用层靠心跳/阶段事件感知存活
     */
    @Bean("streamingWebClientBuilder")
    public WebClient.Builder streamingWebClientBuilder() {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 30_000)
                // 不做整体 HTTP 响应超时（SSE 流本身是长连接），由应用层 SseEmitter 超时兜底
                .keepAlive(true)
                .compress(false)
                .doOnConnected(conn ->
                        // 读空闲 30 分钟：若 Python 30 分钟内连一条事件都没推，视为异常断开
                        conn.addHandlerLast(new ReadTimeoutHandler(30 * 60, TimeUnit.SECONDS))
                                .addHandlerLast(new WriteTimeoutHandler(120, TimeUnit.SECONDS)));

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                // SSE 流本身不需要太大的内存缓冲，但保留 4MB 以防单次大纲 JSON 较大
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(4 * 1024 * 1024));
    }

    @Bean("streamingWebClient")
    public WebClient streamingWebClient(@Qualifier("streamingWebClientBuilder") WebClient.Builder builder) {
        return builder.build();
    }
}
