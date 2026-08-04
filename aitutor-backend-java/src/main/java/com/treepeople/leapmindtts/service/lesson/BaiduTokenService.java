package com.treepeople.leapmindtts.service.lesson;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class BaiduTokenService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${baidu.speech.api.key}")
    private String apiKey;

    @Value("${baidu.speech.secret.key}")
    private String secretKey;

    private static final String TOKEN_URL = "https://aip.baidubce.com/oauth/2.0/token";

    private Mono<String> cachedTokenMono;

    public Mono<String> getToken() {
        if (this.cachedTokenMono == null) {
            this.cachedTokenMono = fetchNewToken()
                    .cache(
                            token -> Duration.ofHours(1), // Cache duration
                            error -> Duration.ZERO,      // No cache on error
                            () -> Duration.ZERO          // No cache on empty
                    );
        }
        return this.cachedTokenMono;
    }

    private Mono<String> fetchNewToken() {
        log.info("正在获取新的百度访问令牌...");

        URI uri = UriComponentsBuilder.fromHttpUrl(TOKEN_URL)
                .queryParam("grant_type", "client_credentials")
                .queryParam("client_id", apiKey)
                .queryParam("client_secret", secretKey)
                .build()
                .toUri();

        return webClient.post()
                .uri(uri)
                .retrieve()
                .bodyToMono(String.class)
                .flatMap(responseBody -> {
                    try {
                        JsonNode rootNode = objectMapper.readTree(responseBody);
                        if (rootNode.has("access_token")) {
                            String accessToken = rootNode.get("access_token").asText();
                            log.info("成功获取新的百度访问令牌。");
                            return Mono.just(accessToken);
                        } else {
                            String error = rootNode.path("error_description").asText("Unknown error");
                            log.error("获取百度令牌失败: {}", error);
                            return Mono.error(new RuntimeException("Failed to get Baidu token: " + error));
                        }
                    } catch (JsonProcessingException e) {
                        log.error("解析百度令牌响应失败", e);
                        return Mono.error(new RuntimeException("Failed to parse Baidu token response", e));
                    }
                });
    }
} 