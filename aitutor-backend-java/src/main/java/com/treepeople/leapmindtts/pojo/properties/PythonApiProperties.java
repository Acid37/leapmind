package com.treepeople.leapmindtts.pojo.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "python.api")
public class PythonApiProperties {
    private String baseUrl;
    private String compressContextUri;

    public String getCompressContextUri() {
        if (compressContextUri != null && !compressContextUri.isEmpty()) {
            return compressContextUri;
        }
        return (baseUrl != null ? baseUrl : "") + "/internal/ai/compress-context";
    }
}
