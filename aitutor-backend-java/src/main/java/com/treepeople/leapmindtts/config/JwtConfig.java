package com.treepeople.leapmindtts.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * JWT配置类
 * 管理JWT相关的配置参数
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "jwt")
public class JwtConfig {
    
    /**
     * JWT密钥
     */
    private String secret = "leapmind-tts-secret-key-for-jwt-token-generation-and-validation-secure";
    
    /**
     * JWT过期时间（毫秒）
     * 默认24小时
     */
    private Long expiration = 24 * 60 * 60 * 1000L;
    
    /**
     * JWT Token前缀
     */
    private String tokenPrefix = "Bearer ";
    
    /**
     * JWT Header名称
     */
    private String headerName = "Authorization";
}