package com.treepeople.leapmindtts.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * 安全配置类
 * 配置密码加密和基础安全设置
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CorsConfig corsConfig;

    /**
     * 密码编码器Bean
     * 使用BCrypt算法进行密码加密
     *
     * @return BCrypt密码编码器
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 安全过滤器链配置
     * 配置HTTP安全策略，禁用默认的Spring Security登录页面
     *
     * @param http HttpSecurity对象
     * @return SecurityFilterChain
     * @throws Exception 配置异常
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // 禁用CSRF保护，因为我们使用JWT
                .csrf(AbstractHttpConfigurer::disable)
                // 启用CORS配置
                .cors(cors -> cors.configurationSource(corsConfig.corsConfigurationSource()))
               // 配置会话管理为无状态
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 配置授权规则
                .authorizeHttpRequests(auth -> auth
                        // 允许OPTIONS预检请求（CORS需要）
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // 允许所有用户访问认证相关接口
                        .requestMatchers("/api/auth/**").permitAll()
                        // 允许所有用户访问教育阶段查询接口
                        .requestMatchers("/api/education/**").permitAll()
                        // 允许访问 API 文档
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/doc.html", "/webjars/**").permitAll()
                        // 允许访问静态资源
                        .requestMatchers("/static/**", "/*.html", "/*.js", "/*.css", "/admin/**", "/css/**", "/js/**", "/image/**").permitAll()
                        // 允许访问短信测试接口
                        .requestMatchers("/api/test/**").permitAll()
                        .requestMatchers("/api/admin/**").permitAll()
                        // 允许访问管理后台审核接口
                        .requestMatchers("/admin/review/**").permitAll()
                        // 语音合成和音频相关接口需要认证
                        .requestMatchers("/api/speech/**").authenticated()
                        // 语音对话接口需要认证
                        .requestMatchers("/api/voice-chat/**").authenticated()
                        // 课程相关接口需要认证
                        .requestMatchers("/api/courses/**").authenticated()
                        // 管理员接口需要认证（具体权限由@AdminRequired注解控制）
                        // 其他请求需要认证
                        .anyRequest().authenticated()
                )
                // 添加JWT认证过滤器
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}