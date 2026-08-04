package com.treepeople.leapmindtts.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * SpringDoc + Knife4j 配置
 * <p>
 * 文档访问地址：
 * <ul>
 *   <li>Knife4j 增强页面：<a href="http://localhost:8080/doc.html">http://localhost:8080/doc.html</a></li>
 *   <li>原生 Swagger UI：<a href="http://localhost:8080/swagger-ui.html">http://localhost:8080/swagger-ui.html</a></li>
 *   <li>OpenAPI JSON：<a href="http://localhost:8080/v3/api-docs">http://localhost:8080/v3/api-docs</a></li>
 * </ul>
 */
@Configuration
public class SpringDocConfig {

    /**
     * API 基本信息
     */
    @Bean
    public OpenAPI leapMindOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("LeapMind 教育平台 API")
                        .description("AI 互动教育平台后端接口文档")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("LeapMind Team")
                                .email("team@leapmind.com"))
                        .license(new License()
                                .name("私有协议")
                                .url("https://leapmind.com")));
    }

    // ==================== 接口分组 ====================

    /**
     * 公开接口 — 用户认证、教育阶段等无需登录的接口
     */
    @Bean
    public GroupedOpenApi publicApi() {
        return GroupedOpenApi.builder()
                .group("1-公开接口")
                .displayName("公开接口")
                .pathsToMatch("/api/auth/**", "/api/education/**")
                .build();
    }

    /**
     * 课程接口 — 需要登录后访问
     */
    @Bean
    public GroupedOpenApi courseApi() {
        return GroupedOpenApi.builder()
                .group("2-课程接口")
                .displayName("课程接口")
                .pathsToMatch("/api/courses/**")
                .build();
    }

    /**
     * 语音接口 — 语音合成、问答等
     */
    @Bean
    public GroupedOpenApi speechApi() {
        return GroupedOpenApi.builder()
                .group("3-语音接口")
                .displayName("语音接口")
                .pathsToMatch("/api/speech/**", "/api/voice-chat/**")
                .build();
    }

    /**
     * 管理后台接口 — 管理员功能
     */
    @Bean
    public GroupedOpenApi adminApi() {
        return GroupedOpenApi.builder()
                .group("4-管理后台")
                .displayName("管理后台接口")
                .pathsToMatch("/api/admin/**", "/admin/**", "/api/admin/review/**")
                .build();
    }
}
