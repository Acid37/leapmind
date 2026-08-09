package com.treepeople.leapmindtts.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * CORS跨域配置
 */
@Configuration
public class CorsConfig {

    private final List<String> allowedOrigins;

    public CorsConfig(@Value("${app.cors.allowed-origins:http://localhost:5173,http://127.0.0.1:5173}")
                      String configuredOrigins) {
        this.allowedOrigins = Arrays.stream(configuredOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .peek(origin -> {
                    if (origin.contains("*")) {
                        throw new IllegalArgumentException("CORS allowed origins must be exact and cannot contain wildcards");
                    }
                })
                .toList();

        if (allowedOrigins.isEmpty()) {
            throw new IllegalArgumentException("At least one exact CORS allowed origin must be configured");
        }
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // 允许来源（精确配置优先，localhost 通配作为开发环境兜底）
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedOriginPatterns(Arrays.asList(
                "http://localhost:*",
                "http://127.0.0.1:*",
                "http://[::1]:*"
        ));

        // 允许所有HTTP方法
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));

        // 允许所有请求头
        configuration.setAllowedHeaders(Arrays.asList("*"));

        // 暴露的响应头（前端可以访问的响应头）
        configuration.setExposedHeaders(Arrays.asList(
                "Authorization",
                "Content-Type",
                "X-Requested-With",
                "X-Request-Id",
                "Deprecation",
                "Link",
                "Access-Control-Allow-Origin",
                "Access-Control-Allow-Credentials"
        ));

        // 允许发送凭证
        configuration.setAllowCredentials(true);

        // 预检请求的缓存时间
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }
}
