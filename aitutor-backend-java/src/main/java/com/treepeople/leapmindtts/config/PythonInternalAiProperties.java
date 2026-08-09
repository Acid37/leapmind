package com.treepeople.leapmindtts.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * M6 画像引擎（Python build-profile 联调）配置。
 * 前缀 m6.profile-engine，避免与 python-service（复习计算，吴敏希）在宽松绑定下互相覆盖。
 */
@Data
@Component
@ConfigurationProperties(prefix = "m6.profile-engine")
public class PythonInternalAiProperties {
    /** 是否启用真实 HTTP 客户端；默认 false，使用 DisabledProfileEngineAdapter 兜底 */
    private boolean enabled = false;
    /** Python 画像引擎服务基础地址（不含尾部斜杠）；实际 Python 端口为 8000（main.py），契约 yaml 的 8001 仅为开发默认 */
    private String baseUrl = "http://localhost:8000";
    /** build-profile 接口路径 */
    private String buildProfilePath = "/api/internal/ai/build-profile";
}
