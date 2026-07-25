package com.treepeople.leapmindtts.config;

import com.treepeople.leapmindtts.config.PythonAIServiceProperties;
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
 * WebClient 配置类
 * 超时值取自 PythonAIServiceProperties，支持通过 application.yml 覆盖
 */
@Configuration
public class WebClientConfig {

    @Bean
    public WebClient.Builder webClientBuilder(PythonAIServiceProperties pythonProps) {
        int connectTimeout = Math.max(pythonProps.getConnectTimeout(), 5);
        int readTimeout = Math.max(pythonProps.getReadTimeout(), 30);

        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeout * 1000)
                .responseTimeout(Duration.ofSeconds(readTimeout))
                .keepAlive(true)
                .doOnConnected(conn ->
                        conn.addHandlerLast(new ReadTimeoutHandler(readTimeout, TimeUnit.SECONDS))
                                .addHandlerLast(new WriteTimeoutHandler(60, TimeUnit.SECONDS)));

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(20 * 1024 * 1024)); // 20MB
    }
    @Bean
    public WebClient webClient(WebClient.Builder webClientBuilder) {
        return webClientBuilder.build();
    }
}