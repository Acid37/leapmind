package com.treepeople.leapmindtts.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 事件采集服务配置属性
 * <p>
 * 用于 M3 薄弱点模块向 M6 画像引擎上报 {@code weak_point_changed} 事件。
 */
@ConfigurationProperties(prefix = "event-collection")
@Validated
public class EventCollectionProperties {

    /** 是否启用事件上报（默认 true，便于本地调试时关闭） */
    private boolean enabled = true;

    /** 事件采集服务地址 */
    private String baseUrl = "http://192.168.1.19:8080";

    /** API 路径 */
    private String collectEndpoint = "/api/events/collect";

    /** HTTP 超时（秒） */
    private int timeout = 10;

    /** 最大重试次数（0 表示不重试） */
    private int maxRetries = 1;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getCollectEndpoint() {
        return collectEndpoint;
    }

    public void setCollectEndpoint(String collectEndpoint) {
        this.collectEndpoint = collectEndpoint;
    }

    public int getTimeout() {
        return timeout;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }
}
