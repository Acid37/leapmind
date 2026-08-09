package com.treepeople.leapmindtts.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 事件采集配置类
 * <p>
 * 启用 {@link EventCollectionProperties} 配置属性绑定。
 */
@Configuration
@EnableConfigurationProperties(EventCollectionProperties.class)
public class EventCollectionConfig {
}
