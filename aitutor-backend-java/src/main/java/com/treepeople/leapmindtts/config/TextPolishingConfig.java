package com.treepeople.leapmindtts.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * 文本润色配置类
 * 启用TextPolishingProperties配置属性并进行初始化验证
 */
@Configuration
@EnableConfigurationProperties(TextPolishingProperties.class)
public class TextPolishingConfig {

    private final TextPolishingProperties textPolishingProperties;

    public TextPolishingConfig(TextPolishingProperties textPolishingProperties) {
        this.textPolishingProperties = textPolishingProperties;
    }

    /**
     * 应用启动后验证配置参数
     */
    @PostConstruct
    public void validateConfiguration() {
        textPolishingProperties.validateConfiguration();
    }
}