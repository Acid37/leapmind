package com.treepeople.leapmindtts.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Python AI 服务配置属性
 * <p>
 * 映射 {@code application.yml} 中 {@code python-service} 配置段，
 * 用于配置 Java 后端调用 Python AI 服务的连接参数。
 * 所有属性均设有默认值，未配置时使用开发环境默认值（localhost:8000）。
 * <p>
 * 配置示例（application.yml）：
 * <pre>{@code
 * python-service:
 *   base-url: http://localhost:8000
 *   review-calculation-path: /api/review/calculate-all
 *   event-process-path: /api/events/process
 *   connect-timeout: 30
 *   read-timeout: 300
 * }</pre>
 * <p>
 * 生产环境建议通过环境变量覆盖：
 * <ul>
 *   <li>{@code PYTHON_SERVICE_BASE_URL} — Python 服务地址</li>
 *   <li>{@code PYTHON_SERVICE_REVIEW_CALCULATION_PATH} — 复习计算接口路径</li>
 *   <li>{@code PYTHON_SERVICE_EVENT_PROCESS_PATH} — 事件处理接口路径</li>
 * </ul>
 *
 * @author wuminxi
 * @date 2026-07-21
 */
@Data
@Component
@ConfigurationProperties(prefix = "python-service")
public class PythonServiceProperties {

    /**
     * Python 服务基础地址（不含尾部斜杠）
     * <p>默认值：{@code http://localhost:8000}</p>
     */
    private String baseUrl = "http://localhost:8000";

    /**
     * 全量复习计算接口路径
     * <p>最终请求 URL = baseUrl + reviewCalculationPath</p>
     * <p>Python 服务收到请求后基于遗忘曲线算法为所有用户重新计算复习计划</p>
     */
    private String reviewCalculationPath = "/api/review/calculate-all";

    /**
     * 事件采集处理接口路径
     * <p>最终请求 URL = baseUrl + eventProcessPath</p>
     * <p>Python 服务收到请求后对 M1/M2/M4/M7 模块事件进行汇总分析</p>
     */
    private String eventProcessPath = "/api/events/process";

    /**
     * 连接超时时间（秒）
     * <p>建立 TCP 连接的最大等待时间，默认 30 秒</p>
     */
    private int connectTimeout = 30;

    /**
     * 读取超时时间（秒）
     * <p>等待 Python 服务响应的最大时间，默认 300 秒（5 分钟）。
     * 复习计算涉及 AI 模型推理，耗时可长达数分钟，因此设置较长的超时</p>
     */
    private int readTimeout = 300;
}
