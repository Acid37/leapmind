package com.treepeople.leapmindtts.interceptor;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

@Slf4j
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimiterRegistry rateLimiterRegistry;
    private final RateLimiterConfig rateLimiterConfig;

    public RateLimitInterceptor(RateLimiterRegistry rateLimiterRegistry) {
        this.rateLimiterRegistry = rateLimiterRegistry;
        // 修改为：如果找不到 userQuestionLimiter，就优雅降级使用默认配置 (getDefaultConfig)
        this.rateLimiterConfig = rateLimiterRegistry.getConfiguration("userQuestionLimiter")
                .orElseGet(rateLimiterRegistry::getDefaultConfig);
    }

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
                             @NonNull HttpServletResponse response,
                             @NonNull Object handler) throws Exception {
        String key = resolveKey(request);

        // 核心改造：直接委托 RateLimiterRegistry 创建或获取缓存中的限流器，无需自己维护 ConcurrentHashMap
        RateLimiter rateLimiter = rateLimiterRegistry.rateLimiter(key, rateLimiterConfig);

        if (rateLimiter.acquirePermission()) {
            return true;
        } else {
            log.warn("Rate limit exceeded for key: {}", key);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.getWriter().write("You have made too many requests in a short period. Please try again later.");
            return false;
        }
    }

    private String resolveKey(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            return auth.getName();
        }

        // 针对未登录用户解析代理真实 IP
        String forwardedFor = request.getHeader("X-Forwarded-For");
        // 使用 Spring 自带的 StringUtils.hasText 替代 isBlank()，彻底解决 Java 编译版本报错
        if (StringUtils.hasText(forwardedFor)) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
