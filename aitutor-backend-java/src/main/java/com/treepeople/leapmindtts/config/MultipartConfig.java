package com.treepeople.leapmindtts.config;

import org.springframework.boot.web.servlet.MultipartConfigFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

import jakarta.servlet.MultipartConfigElement;

/**
 * 文件上传配置
 */
@Configuration
public class MultipartConfig {

    /**
     * 配置文件上传参数
     */
    @Bean
    public MultipartConfigElement multipartConfigElement() {
        MultipartConfigFactory factory = new MultipartConfigFactory();
        // 设置文件大小限制，超出设置页面会抛出异常信息
        factory.setMaxFileSize(DataSize.ofMegabytes(10)); // 限制上传文件大小为10MB
        factory.setMaxRequestSize(DataSize.ofMegabytes(10)); // 设置总上传数据总大小
        return factory.createMultipartConfig();
    }
}