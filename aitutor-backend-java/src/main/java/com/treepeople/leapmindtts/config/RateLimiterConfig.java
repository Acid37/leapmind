package com.treepeople.leapmindtts.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.function.Function;

@Configuration
public class RateLimiterConfig {

    /**
     * 定义一个 KeyResolver，用于从请求中提取用于限流的 key。
     * 实现了多种策略来识别用户：
     * 1. Spring Security 的 Authentication
     * 2. 'userId' 请求参数
     * 3. 'X-User-Id' 请求头
     * 4. 客户端 IP 地址作为最终兜底
     */
    @Bean(name = "userKeyResolver")
    public Function<HttpServletRequest, String> userKeyResolver() {
        return request -> {
            // 优先解析 Spring Security Token
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
                return auth.getName();
            }

            // 兜底解析：请求参数 -> Request Header -> 客户端 IP
            return Optional.ofNullable(request.getParameter("userId"))
                    .filter(userId -> !userId.isEmpty())
                    .or(() -> Optional.ofNullable(request.getHeader("X-User-Id")))
                    .filter(userId -> !userId.isEmpty())
                    .orElse(request.getRemoteAddr());
        };
    }
}
