package com.treepeople.leapmindtts.pojo.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "python.api")
public class PythonApiProperties {
    private String baseUrl = "http://127.0.0.1:8000";
    private String compressContextUri;
}
