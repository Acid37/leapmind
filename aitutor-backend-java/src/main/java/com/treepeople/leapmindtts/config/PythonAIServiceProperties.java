package com.treepeople.leapmindtts.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Python AI 微服务配置属性
 * 用于 Java 后端调用 Python FastAPI 内部接口
 */
@Data
@Component
@ConfigurationProperties(prefix = "python.ai")
public class PythonAIServiceProperties {

    /** Python FastAPI 服务地址（内网） */
    private String baseUrl = "http://localhost:8001";

    /** 非流式 AI 调用路径 */
    private String generatePath = "/api/internal/ai/generate";

    /** 流式 AI 调用路径 */
    private String streamPath = "/api/internal/ai/generate/stream";

    /** 请求超时（秒） */
    private int connectTimeout = 10;

    /** 响应超时（秒），流式场景需要更长 */
    private int readTimeout = 120;
}
